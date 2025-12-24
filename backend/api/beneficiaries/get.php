<?php
/**
 * Get Single Beneficiary Endpoint
 * GET /api/beneficiaries/get.php
 * Retrieve beneficiary details by ID
 */

header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json; charset=UTF-8");
header("Access-Control-Allow-Methods: GET");
header("Access-Control-Allow-Headers: Content-Type, Authorization");

require_once '../config/database.php';
require_once '../utils/jwt.php';

$jwt = JWTHelper::validateRequest();
if (!$jwt) {
    http_response_code(401);
    echo json_encode(array("message" => "Unauthorized"));
    exit();
}

$beneficiary_id = $_GET['id'] ?? '';

if (empty($beneficiary_id)) {
    http_response_code(400);
    echo json_encode(array("message" => "Beneficiary ID required"));
    exit();
}

try {
    $database = new Database();
    $db = $database->getConnection();
    
    $query = "SELECT b.*, v.village_name_hindi, v.block_name_hindi, v.district_name_hindi
              FROM beneficiaries b
              LEFT JOIN villages v ON b.village_id = v.id
              WHERE b.id = :id AND b.is_active = 1";
    
    $stmt = $db->prepare($query);
    $stmt->bindParam(":id", $beneficiary_id);
    $stmt->execute();
    
    if ($stmt->rowCount() == 0) {
        http_response_code(404);
        echo json_encode(array("message" => "Beneficiary not found"));
        exit();
    }
    
    $beneficiary = $stmt->fetch();
    
    http_response_code(200);
    echo json_encode(array(
        "message" => "Beneficiary retrieved successfully",
        "beneficiary" => $beneficiary
    ));
    
} catch (Exception $e) {
    http_response_code(500);
    error_log("Get beneficiary error: " . $e->getMessage());
    echo json_encode(array("message" => "Failed to retrieve beneficiary"));
}
?>
