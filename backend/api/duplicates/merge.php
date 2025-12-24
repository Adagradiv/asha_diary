<?php
/**
 * Duplicate Merge Endpoint
 * POST /api/duplicates/merge.php
 * Block Nodal merges duplicate beneficiary into master
 */

header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json; charset=UTF-8");
header("Access-Control-Allow-Methods: POST");
header("Access-Control-Allow-Headers: Content-Type, Authorization");

require_once '../config/database.php';
require_once '../utils/jwt.php';

$jwt = JWTHelper::validateRequest();
if (!$jwt) {
    http_response_code(401);
    echo json_encode(array("message" => "Unauthorized"));
    exit();
}

$data = json_decode(file_get_contents("php://input"));

if (empty($data->duplicate_beneficiary_id) || empty($data->master_beneficiary_id)) {
    http_response_code(400);
    echo json_encode(array("message" => "duplicate_beneficiary_id and master_beneficiary_id required"));
    exit();
}

try {
    $database = new Database();
    $db = $database->getConnection();
    
    $user_id = $jwt['id'];
    $user_role_id = $jwt['role_id'];
    
    // Only Block Nodal and above can merge
    if ($user_role_id < 3) {
        http_response_code(403);
        echo json_encode(array("message" => "Only Block Nodal and above can merge duplicates"));
        exit();
    }
    
    $db->beginTransaction();
    
    // Get both beneficiaries
    $query = "SELECT * FROM beneficiaries WHERE id = :id AND is_active = 1";
    
    $stmt1 = $db->prepare($query);
    $stmt1->bindParam(":id", $data->duplicate_beneficiary_id);
    $stmt1->execute();
    $duplicate = $stmt1->fetch();
    
    $stmt2 = $db->prepare($query);
    $stmt2->bindParam(":id", $data->master_beneficiary_id);
    $stmt2->execute();
    $master = $stmt2->fetch();
    
    if (!$duplicate || !$master) {
        $db->rollBack();
        http_response_code(404);
        echo json_encode(array("message" => "One or both beneficiaries not found"));
        exit();
    }
    
    // Transfer all visits from duplicate to master
    $visit_counts = array(
        'anc_visits' => 0,
        'bp_visits' => 0,
        'blood_sugar_visits' => 0,
        'vaccination_visits' => 0
    );
    
    // Transfer ANC visits
    $transfer_query = "UPDATE anc_visits SET 
                       beneficiary_id = :master_id,
                       sync_version = sync_version + 1,
                       updated_at = NOW()
                       WHERE beneficiary_id = :duplicate_id";
    $stmt = $db->prepare($transfer_query);
    $stmt->bindParam(":master_id", $data->master_beneficiary_id);
    $stmt->bindParam(":duplicate_id", $data->duplicate_beneficiary_id);
    $stmt->execute();
    $visit_counts['anc_visits'] = $stmt->rowCount();
    
    // Transfer BP visits
    $transfer_query = str_replace('anc_visits', 'bp_visits', $transfer_query);
    $stmt = $db->prepare($transfer_query);
    $stmt->bindParam(":master_id", $data->master_beneficiary_id);
    $stmt->bindParam(":duplicate_id", $data->duplicate_beneficiary_id);
    $stmt->execute();
    $visit_counts['bp_visits'] = $stmt->rowCount();
    
    // Transfer Blood Sugar visits
    $transfer_query = str_replace('bp_visits', 'blood_sugar_visits', $transfer_query);
    $stmt = $db->prepare($transfer_query);
    $stmt->bindParam(":master_id", $data->master_beneficiary_id);
    $stmt->bindParam(":duplicate_id", $data->duplicate_beneficiary_id);
    $stmt->execute();
    $visit_counts['blood_sugar_visits'] = $stmt->rowCount();
    
    // Transfer Vaccination visits
    $transfer_query = str_replace('blood_sugar_visits', 'vaccination_visits', $transfer_query);
    $stmt = $db->prepare($transfer_query);
    $stmt->bindParam(":master_id", $data->master_beneficiary_id);
    $stmt->bindParam(":duplicate_id", $data->duplicate_beneficiary_id);
    $stmt->execute();
    $visit_counts['vaccination_visits'] = $stmt->rowCount();
    
    $total_visits = array_sum($visit_counts);
    
    // Mark duplicate as inactive and link to master
    $inactivate_query = "UPDATE beneficiaries SET
                         is_active = 0,
                         duplicate_of_beneficiary_id = :master_beneficiary_id,
                         last_modified_by_user_id = :user_id,
                         last_modified_by_role_id = :role_id,
                         sync_version = sync_version + 1,
                         updated_at = NOW()
                         WHERE id = :duplicate_id";
    
    $inactivate_stmt = $db->prepare($inactivate_query);
    $inactivate_stmt->bindParam(":master_beneficiary_id", $master['beneficiary_id']);
    $inactivate_stmt->bindParam(":user_id", $user_id);
    $inactivate_stmt->bindParam(":role_id", $user_role_id);
    $inactivate_stmt->bindParam(":duplicate_id", $data->duplicate_beneficiary_id);
    $inactivate_stmt->execute();
    
    // Update duplicate flag status
    $flag_query = "UPDATE duplicate_flags SET
                   status = 'MERGED',
                   resolution_action = 'MERGED_INTO_MASTER',
                   master_beneficiary_id = :master_beneficiary_id,
                   resolved_by_user_id = :user_id,
                   resolved_at = NOW(),
                   resolution_notes_hindi = :notes,
                   updated_at = NOW()
                   WHERE (beneficiary_id_1 = :dup_id OR beneficiary_id_2 = :dup_id)
                   AND status = 'PENDING'";
    
    $flag_stmt = $db->prepare($flag_query);
    $flag_stmt->bindParam(":master_beneficiary_id", $master['beneficiary_id']);
    $flag_stmt->bindParam(":user_id", $user_id);
    $flag_stmt->bindParam(":notes", $data->notes);
    $flag_stmt->bindParam(":dup_id", $data->duplicate_beneficiary_id);
    $flag_stmt->execute();
    
    // Create audit log
    $audit_description = "डुप्लिकेट लाभार्थी मर्ज: {$duplicate['beneficiary_id']} → {$master['beneficiary_id']}\n";
    $audit_description .= "$total_visits विज़िट ट्रांसफर की गई";
    if (!empty($data->notes)) {
        $audit_description .= "\nटिप्पणी: {$data->notes}";
    }
    
    $audit_query = "INSERT INTO audit_logs 
                    (id, user_id, user_role_id, action_type, entity_type, entity_id,
                     action_description_hindi, gps_lat, gps_lng, gps_accuracy_meters)
                    VALUES (UUID(), :user_id, :role_id, 'MERGE', 'BENEFICIARY', :duplicate_id,
                     :description, 0, 0, 0)";
    
    $audit_stmt = $db->prepare($audit_query);
    $audit_stmt->bindParam(":user_id", $user_id);
    $audit_stmt->bindParam(":role_id", $user_role_id);
    $audit_stmt->bindParam(":duplicate_id", $data->duplicate_beneficiary_id);
    $audit_stmt->bindParam(":description", $audit_description);
    $audit_stmt->execute();
    
    $db->commit();
    
    http_response_code(200);
    echo json_encode(array(
        "message" => "Merge successful",
        "master_beneficiary_id" => $master['beneficiary_id'],
        "duplicate_beneficiary_id" => $duplicate['beneficiary_id'],
        "visits_transferred" => $visit_counts,
        "total_visits_transferred" => $total_visits
    ));
    
} catch (Exception $e) {
    if (isset($db)) {
        $db->rollBack();
    }
    http_response_code(500);
    error_log("Merge error: " . $e->getMessage());
    echo json_encode(array("message" => "Merge failed: " . $e->getMessage()));
}
?>
