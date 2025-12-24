<?php
/**
 * Visit Creation Endpoint
 * POST /api/visits/create.php
 * Create new health visit (ANC/BP/BloodSugar/Vaccination)
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

if (empty($data->visit_type) || empty($data->beneficiary_id) || empty($data->visit_data)) {
    http_response_code(400);
    echo json_encode(array("message" => "visit_type, beneficiary_id, and visit_data required"));
    exit();
}

try {
    $database = new Database();
    $db = $database->getConnection();
    
    $user_id = $jwt['id'];
    $user_role_id = $jwt['role_id'];
    
    $db->beginTransaction();
    
    // Verify beneficiary exists and is active
    $ben_query = "SELECT id FROM beneficiaries WHERE id = :id AND is_active = 1";
    $ben_stmt = $db->prepare($ben_query);
    $ben_stmt->bindParam(":id", $data->beneficiary_id);
    $ben_stmt->execute();
    
    if ($ben_stmt->rowCount() == 0) {
        $db->rollBack();
        http_response_code(404);
        echo json_encode(array("message" => "Beneficiary not found or inactive"));
        exit();
    }
    
    // Determine table based on visit type
    $table_map = array(
        'ANC' => 'anc_visits',
        'BP' => 'bp_visits',
        'BLOOD_SUGAR' => 'blood_sugar_visits',
        'VACCINATION' => 'vaccination_visits'
    );
    
    $visit_type = strtoupper($data->visit_type);
    if (!isset($table_map[$visit_type])) {
        $db->rollBack();
        http_response_code(400);
        echo json_encode(array("message" => "Invalid visit_type"));
        exit();
    }
    
    $table = $table_map[$visit_type];
    $visit_data = (array)$data->visit_data;
    
    // Add common fields
    $visit_data['id'] = $visit_data['id'] ?? generateUUID();
    $visit_data['beneficiary_id'] = $data->beneficiary_id;
    $visit_data['recorded_by_user_id'] = $user_id;
    $visit_data['last_modified_by_role_id'] = $user_role_id;
    $visit_data['sync_version'] = 1;
    
    // Build insert query
    $fields = array_keys($visit_data);
    $placeholders = array_map(function($field) { return ":$field"; }, $fields);
    
    $insert_query = "INSERT INTO $table (" . implode(', ', $fields) . ") 
                     VALUES (" . implode(', ', $placeholders) . ")";
    
    $stmt = $db->prepare($insert_query);
    foreach ($visit_data as $key => $value) {
        $stmt->bindValue(":$key", $value);
    }
    $stmt->execute();
    
    $server_id = $db->lastInsertId();
    
    // Create audit log
    $audit_description = "विज़िट दर्ज: $visit_type\nलाभार्थी: {$data->beneficiary_id}";
    
    $audit_query = "INSERT INTO audit_logs 
                    (id, user_id, user_role_id, action_type, entity_type, entity_id,
                     action_description_hindi, gps_lat, gps_lng, gps_accuracy_meters)
                    VALUES (UUID(), :user_id, :role_id, 'CREATE', :entity_type, :visit_id,
                     :description, :gps_lat, :gps_lng, :gps_accuracy)";
    
    $audit_stmt = $db->prepare($audit_query);
    $audit_stmt->bindParam(":user_id", $user_id);
    $audit_stmt->bindParam(":role_id", $user_role_id);
    $audit_stmt->bindParam(":entity_type", $visit_type);
    $audit_stmt->bindParam(":visit_id", $visit_data['id']);
    $audit_stmt->bindParam(":description", $audit_description);
    $audit_stmt->bindParam(":gps_lat", $visit_data['gps_lat']);
    $audit_stmt->bindParam(":gps_lng", $visit_data['gps_lng']);
    $audit_stmt->bindParam(":gps_accuracy", $visit_data['gps_accuracy_meters']);
    $audit_stmt->execute();
    
    $db->commit();
    
    http_response_code(201);
    echo json_encode(array(
        "message" => "Visit created successfully",
        "visit_id" => $visit_data['id'],
        "server_id" => $server_id,
        "visit_type" => $visit_type
    ));
    
} catch (Exception $e) {
    if (isset($db)) {
        $db->rollBack();
    }
    http_response_code(500);
    error_log("Visit creation error: " . $e->getMessage());
    echo json_encode(array("message" => "Visit creation failed"));
}

/**
 * Generate UUID (simple implementation)
 */
function generateUUID() {
    return sprintf('%04x%04x-%04x-%04x-%04x-%04x%04x%04x',
        mt_rand(0, 0xffff), mt_rand(0, 0xffff),
        mt_rand(0, 0xffff),
        mt_rand(0, 0x0fff) | 0x4000,
        mt_rand(0, 0x3fff) | 0x8000,
        mt_rand(0, 0xffff), mt_rand(0, 0xffff), mt_rand(0, 0xffff)
    );
}
?>
