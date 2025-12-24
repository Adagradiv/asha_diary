<?php
/**
 * Beneficiary Create Endpoint
 * POST /api/beneficiaries/create.php
 * Direct beneficiary creation (alternative to sync queue)
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

// Validate required fields
$required = ['beneficiary_id', 'name_hindi', 'father_husband_name_hindi', 'gender', 
             'date_of_birth', 'age_years', 'beneficiary_type', 'village_id', 'address_hindi'];

foreach ($required as $field) {
    if (empty($data->{$field})) {
        http_response_code(400);
        echo json_encode(array("message" => "$field is required"));
        exit();
    }
}

try {
    $database = new Database();
    $db = $database->getConnection();
    
    $user_id = $jwt['id'];
    $user_role_id = $jwt['role_id'];
    
    $db->beginTransaction();
    
    // Check if beneficiary_id already exists
    $check_query = "SELECT id FROM beneficiaries WHERE beneficiary_id = :beneficiary_id";
    $check_stmt = $db->prepare($check_query);
    $check_stmt->bindParam(":beneficiary_id", $data->beneficiary_id);
    $check_stmt->execute();
    
    if ($check_stmt->rowCount() > 0) {
        $db->rollBack();
        http_response_code(409);
        echo json_encode(array("message" => "Beneficiary ID already exists"));
        exit();
    }
    
    // Insert beneficiary
    $insert_query = "INSERT INTO beneficiaries 
                     (id, beneficiary_id, name_hindi, father_husband_name_hindi, gender,
                      date_of_birth, age_years, beneficiary_type, village_id, address_hindi,
                      mobile_number, registered_by_user_id, registration_gps_lat, registration_gps_lng,
                      last_modified_by_user_id, last_modified_by_role_id, sync_version)
                     VALUES 
                     (:id, :beneficiary_id, :name_hindi, :father_husband_name_hindi, :gender,
                      :date_of_birth, :age_years, :beneficiary_type, :village_id, :address_hindi,
                      :mobile_number, :registered_by_user_id, :registration_gps_lat, :registration_gps_lng,
                      :last_modified_by_user_id, :last_modified_by_role_id, 1)";
    
    $stmt = $db->prepare($insert_query);
    
    // Generate UUID if not provided
    $id = $data->id ?? generateUUID();
    $stmt->bindParam(":id", $id);
    $stmt->bindParam(":beneficiary_id", $data->beneficiary_id);
    $stmt->bindParam(":name_hindi", $data->name_hindi);
    $stmt->bindParam(":father_husband_name_hindi", $data->father_husband_name_hindi);
    $stmt->bindParam(":gender", $data->gender);
    $stmt->bindParam(":date_of_birth", $data->date_of_birth);
    $stmt->bindParam(":age_years", $data->age_years);
    $stmt->bindParam(":beneficiary_type", $data->beneficiary_type);
    $stmt->bindParam(":village_id", $data->village_id);
    $stmt->bindParam(":address_hindi", $data->address_hindi);
    $stmt->bindParam(":mobile_number", $data->mobile_number);
    $stmt->bindParam(":registered_by_user_id", $user_id);
    
    $gps_lat = $data->registration_gps_lat ?? 0;
    $gps_lng = $data->registration_gps_lng ?? 0;
    $stmt->bindParam(":registration_gps_lat", $gps_lat);
    $stmt->bindParam(":registration_gps_lng", $gps_lng);
    $stmt->bindParam(":last_modified_by_user_id", $user_id);
    $stmt->bindParam(":last_modified_by_role_id", $user_role_id);
    
    $stmt->execute();
    $server_id = $db->lastInsertId();
    
    // Create audit log
    $audit_description = "लाभार्थी पंजीकृत: {$data->name_hindi}\nID: {$data->beneficiary_id}";
    
    $audit_query = "INSERT INTO audit_logs 
                    (id, user_id, user_role_id, action_type, entity_type, entity_id,
                     action_description_hindi, gps_lat, gps_lng, gps_accuracy_meters)
                    VALUES (UUID(), :user_id, :role_id, 'CREATE', 'BENEFICIARY', :entity_id,
                     :description, :gps_lat, :gps_lng, 0)";
    
    $audit_stmt = $db->prepare($audit_query);
    $audit_stmt->bindParam(":user_id", $user_id);
    $audit_stmt->bindParam(":role_id", $user_role_id);
    $audit_stmt->bindParam(":entity_id", $id);
    $audit_stmt->bindParam(":description", $audit_description);
    $audit_stmt->bindParam(":gps_lat", $gps_lat);
    $audit_stmt->bindParam(":gps_lng", $gps_lng);
    $audit_stmt->execute();
    
    $db->commit();
    
    http_response_code(201);
    echo json_encode(array(
        "message" => "Beneficiary created successfully",
        "id" => $id,
        "server_id" => $server_id,
        "beneficiary_id" => $data->beneficiary_id
    ));
    
} catch (Exception $e) {
    if (isset($db)) {
        $db->rollBack();
    }
    http_response_code(500);
    error_log("Beneficiary creation error: " . $e->getMessage());
    echo json_encode(array("message" => "Creation failed"));
}

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
