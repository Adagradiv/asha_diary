<?php
/**
 * Edit Request Rejection Endpoint
 * POST /api/edit_requests/reject.php
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

if (empty($data->request_id) || empty($data->notes)) {
    http_response_code(400);
    echo json_encode(array("message" => "request_id and notes required"));
    exit();
}

try {
    $database = new Database();
    $db = $database->getConnection();
    
    $user_id = $jwt['id'];
    $user_role_id = $jwt['role_id'];
    
    if ($user_role_id < 2) {
        http_response_code(403);
        echo json_encode(array("message" => "Insufficient permissions"));
        exit();
    }
    
    $db->beginTransaction();
    
    // Check request exists
    $query = "SELECT * FROM edit_requests WHERE id = :request_id AND status = 'PENDING'";
    $stmt = $db->prepare($query);
    $stmt->bindParam(":request_id", $data->request_id);
    $stmt->execute();
    
    if ($stmt->rowCount() == 0) {
        $db->rollBack();
        http_response_code(404);
        echo json_encode(array("message" => "Request not found"));
        exit();
    }
    
    // Mark as rejected
    $reject_query = "UPDATE edit_requests SET
                     status = 'REJECTED',
                     reviewed_by_user_id = :user_id,
                     reviewed_at = NOW(),
                     review_notes_hindi = :notes,
                     updated_at = NOW()
                     WHERE id = :request_id";
    
    $reject_stmt = $db->prepare($reject_query);
    $reject_stmt->bindParam(":user_id", $user_id);
    $reject_stmt->bindParam(":notes", $data->notes);
    $reject_stmt->bindParam(":request_id", $data->request_id);
    $reject_stmt->execute();
    
    // Audit log
    $audit_query = "INSERT INTO audit_logs 
                    (id, user_id, user_role_id, action_type, entity_type, entity_id,
                     action_description_hindi, gps_lat, gps_lng, gps_accuracy_meters)
                    VALUES (UUID(), :user_id, :role_id, 'REJECT', 'EDIT_REQUEST', :request_id,
                     'संपादन अनुरोध अस्वीकृत', 0, 0, 0)";
    
    $audit_stmt = $db->prepare($audit_query);
    $audit_stmt->bindParam(":user_id", $user_id);
    $audit_stmt->bindParam(":role_id", $user_role_id);
    $audit_stmt->bindParam(":request_id", $data->request_id);
    $audit_stmt->execute();
    
    $db->commit();
    
    http_response_code(200);
    echo json_encode(array("message" => "Edit request rejected"));
    
} catch (Exception $e) {
    if (isset($db)) {
        $db->rollBack();
    }
    http_response_code(500);
    error_log("Reject error: " . $e->getMessage());
    echo json_encode(array("message" => "Rejection failed"));
}
?>
