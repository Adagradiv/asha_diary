<?php
/**
 * Beneficiary Update Endpoint
 * PUT /api/beneficiaries/update.php
 * Update beneficiary details (with role validation)
 */

header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json; charset=UTF-8");
header("Access-Control-Allow-Methods: PUT");
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

if (empty($data->id)) {
    http_response_code(400);
    echo json_encode(array("message" => "Beneficiary ID required"));
    exit();
}

try {
    $database = new Database();
    $db = $database->getConnection();
    
    $user_id = $jwt['id'];
    $user_role_id = $jwt['role_id'];
    
    $db->beginTransaction();
    
    // Get current beneficiary
    $query = "SELECT * FROM beneficiaries WHERE id = :id AND is_active = 1";
    $stmt = $db->prepare($query);
    $stmt->bindParam(":id", $data->id);
    $stmt->execute();
    
    if ($stmt->rowCount() == 0) {
        $db->rollBack();
        http_response_code(404);
        echo json_encode(array("message" => "Beneficiary not found"));
        exit();
    }
    
    $current = $stmt->fetch();
    
    // Role check: Only Supervisor and above can directly edit
    if ($user_role_id < 2) {
        $db->rollBack();
        http_response_code(403);
        echo json_encode(array("message" => "Insufficient permissions. Submit edit request instead."));
        exit();
    }
    
    // Build update query dynamically based on provided fields
    $updateFields = array();
    $params = array(':id' => $data->id);
    
    // Allowed fields to update
    $allowedFields = array(
        'name_hindi', 'father_husband_name_hindi', 'age_years',
        'mobile_number', 'address_hindi', 'beneficiary_type'
    );
    
    foreach ($allowedFields as $field) {
        if (isset($data->{$field}) && $data->{$field} !== $current[$field]) {
            $updateFields[] = "$field = :$field";
            $params[":$field"] = $data->{$field};
        }
    }
    
    if (empty($updateFields)) {
        $db->rollBack();
        http_response_code(400);
        echo json_encode(array("message" => "No fields to update"));
        exit();
    }
    
    // Add tracking fields
    $updateFields[] = "last_modified_by_user_id = :user_id";
    $updateFields[] = "last_modified_by_role_id = :role_id";
    $updateFields[] = "sync_version = sync_version + 1";
    $updateFields[] = "updated_at = NOW()";
    
    $params[':user_id'] = $user_id;
    $params[':role_id'] = $user_role_id;
    
    $update_query = "UPDATE beneficiaries SET " . implode(', ', $updateFields) . " WHERE id = :id";
    
    $update_stmt = $db->prepare($update_query);
    foreach ($params as $key => $value) {
        $update_stmt->bindValue($key, $value);
    }
    $update_stmt->execute();
    
    // Create audit log
    $changed_fields = array_keys(array_filter($params, function($key) {
        return !in_array($key, [':id', ':user_id', ':role_id']);
    }, ARRAY_FILTER_USE_KEY));
    
    $audit_description = "लाभार्थी अपडेट: {$current['name_hindi']}\n";
    $audit_description .= "अपडेट फील्ड: " . implode(', ', $changed_fields);
    
    $audit_query = "INSERT INTO audit_logs 
                    (id, user_id, user_role_id, action_type, entity_type, entity_id,
                     action_description_hindi, gps_lat, gps_lng, gps_accuracy_meters)
                    VALUES (UUID(), :user_id, :role_id, 'UPDATE', 'BENEFICIARY', :entity_id,
                     :description, 0, 0, 0)";
    
    $audit_stmt = $db->prepare($audit_query);
    $audit_stmt->bindParam(":user_id", $user_id);
    $audit_stmt->bindParam(":role_id", $user_role_id);
    $audit_stmt->bindParam(":entity_id", $data->id);
    $audit_stmt->bindParam(":description", $audit_description);
    $audit_stmt->execute();
    
    $db->commit();
    
    http_response_code(200);
    echo json_encode(array(
        "message" => "Beneficiary updated successfully",
        "id" => $data->id,
        "updated_fields" => count($updateFields) - 4  // Exclude tracking fields
    ));
    
} catch (Exception $e) {
    if (isset($db)) {
        $db->rollBack();
    }
    http_response_code(500);
    error_log("Beneficiary update error: " . $e->getMessage());
    echo json_encode(array("message" => "Update failed"));
}
?>
