<?php
/**
 * Edit Request Approval Endpoint
 * POST /api/edit_requests/approve.php
 * Supervisor/Block Nodal approves edit request
 */

header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json; charset=UTF-8");
header("Access-Control-Allow-Methods: POST");
header("Access-Control-Allow-Headers: Content-Type, Authorization");

require_once '../config/database.php';
require_once '../utils/jwt.php';

// Validate JWT
$jwt = JWTHelper::validateRequest();
if (!$jwt) {
    http_response_code(401);
    echo json_encode(array("message" => "Unauthorized"));
    exit();
}

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    http_response_code(405);
    echo json_encode(array("message" => "Method not allowed"));
    exit();
}

$data = json_decode(file_get_contents("php://input"));

if (empty($data->request_id)) {
    http_response_code(400);
    echo json_encode(array("message" => "request_id required"));
    exit();
}

try {
    $database = new Database();
    $db = $database->getConnection();
    
    $user_id = $jwt['id'];
    $user_role_id = $jwt['role_id'];
    
    // Check user has permission (Supervisor or above)
    if ($user_role_id < 2) {
        http_response_code(403);
        echo json_encode(array("message" => "Insufficient permissions"));
        exit();
    }
    
    $db->beginTransaction();
    
    // Get edit request
    $query = "SELECT * FROM edit_requests WHERE id = :request_id AND status = 'PENDING'";
    $stmt = $db->prepare($query);
    $stmt->bindParam(":request_id", $data->request_id);
    $stmt->execute();
    
    if ($stmt->rowCount() == 0) {
        $db->rollBack();
        http_response_code(404);
        echo json_encode(array("message" => "Edit request not found or already processed"));
        exit();
    }
    
    $request = $stmt->fetch();
    
    // Get beneficiary
    $ben_query = "SELECT * FROM beneficiaries WHERE id = :beneficiary_id";
    $ben_stmt = $db->prepare($ben_query);
    $ben_stmt->bindParam(":beneficiary_id", $request['beneficiary_id']);
    $ben_stmt->execute();
    
    if ($ben_stmt->rowCount() == 0) {
        $db->rollBack();
        http_response_code(404);
        echo json_encode(array("message" => "Beneficiary not found"));
        exit();
    }
    
    $beneficiary = $ben_stmt->fetch();
    
    // Apply the edit to beneficiary
    $field_name = $request['field_name'];
    $new_value = $request['requested_value'];
    
    $update_query = "UPDATE beneficiaries SET 
                     $field_name = :new_value,
                     last_modified_by_user_id = :user_id,
                     last_modified_by_role_id = :role_id,
                     sync_version = sync_version + 1,
                     updated_at = NOW()
                     WHERE id = :beneficiary_id";
    
    $update_stmt = $db->prepare($update_query);
    $update_stmt->bindParam(":new_value", $new_value);
    $update_stmt->bindParam(":user_id", $user_id);
    $update_stmt->bindParam(":role_id", $user_role_id);
    $update_stmt->bindParam(":beneficiary_id", $request['beneficiary_id']);
    $update_stmt->execute();
    
    // Mark request as approved
    $approve_query = "UPDATE edit_requests SET
                      status = 'APPROVED',
                      reviewed_by_user_id = :user_id,
                      reviewed_at = NOW(),
                      review_notes_hindi = :notes,
                      updated_at = NOW()
                      WHERE id = :request_id";
    
    $approve_stmt = $db->prepare($approve_query);
    $approve_stmt->bindParam(":user_id", $user_id);
    $approve_stmt->bindParam(":notes", $data->notes);
    $approve_stmt->bindParam(":request_id", $data->request_id);
    $approve_stmt->execute();
    
    // Create audit log
    $audit_query = "INSERT INTO audit_logs 
                    (id, user_id, user_role_id, action_type, entity_type, entity_id,
                     action_description_hindi, gps_lat, gps_lng, gps_accuracy_meters)
                    VALUES (UUID(), :user_id, :role_id, 'APPROVE', 'EDIT_REQUEST', :request_id,
                     :description, 0, 0, 0)";
    
    $description = "संपादन अनुरोध स्वीकृत: {$field_name} अपडेट किया गया";
    $audit_stmt = $db->prepare($audit_query);
    $audit_stmt->bindParam(":user_id", $user_id);
    $audit_stmt->bindParam(":role_id", $user_role_id);
    $audit_stmt->bindParam(":request_id", $data->request_id);
    $audit_stmt->bindParam(":description", $description);
    $audit_stmt->execute();
    
    $db->commit();
    
    http_response_code(200);
    echo json_encode(array(
        "message" => "Edit request approved successfully",
        "beneficiary_id" => $request['beneficiary_id']
    ));
    
} catch (Exception $e) {
    if (isset($db)) {
        $db->rollBack();
    }
    http_response_code(500);
    error_log("Approve error: " . $e->getMessage());
    echo json_encode(array("message" => "Approval failed"));
}
?>
