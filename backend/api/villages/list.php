<?php
/**
 * Villages List Endpoint
 * GET /api/villages/list.php
 * Returns villages for spinner/dropdown (filtered by user's area)
 */

header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json; charset=UTF-8");
header("Access-Control-Allow-Methods: GET");
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

try {
    $database = new Database();
    $db = $database->getConnection();
    
    $user_id = $jwt['id'];
    $user_role_id = $jwt['role_id'];
    
    // Get filter parameters
    $district_code = $_GET['district_code'] ?? '';
    $block_code = $_GET['block_code'] ?? '';
    
    // Build query based on user role
    if ($user_role_id == 1 || $user_role_id == 2) {
        // Field Staff and Supervisors - only assigned villages
        $query = "SELECT DISTINCT v.*
                  FROM villages v
                  INNER JOIN user_area_mappings uam ON v.id = uam.village_id
                  WHERE uam.user_id = :user_id
                  AND uam.is_active = 1
                  AND v.is_active = 1";
        
        if (!empty($district_code)) {
            $query .= " AND v.district_code = :district_code";
        }
        if (!empty($block_code)) {
            $query .= " AND v.block_code = :block_code";
        }
        
        $query .= " ORDER BY v.village_name_hindi ASC";
        
        $stmt = $db->prepare($query);
        $stmt->bindParam(":user_id", $user_id);
        if (!empty($district_code)) {
            $stmt->bindParam(":district_code", $district_code);
        }
        if (!empty($block_code)) {
            $stmt->bindParam(":block_code", $block_code);
        }
        
    } else {
        // Block Nodal and above - all villages in their jurisdiction
        $query = "SELECT * FROM villages WHERE is_active = 1";
        
        if (!empty($district_code)) {
            $query .= " AND district_code = :district_code";
        }
        if (!empty($block_code)) {
            $query .= " AND block_code = :block_code";
        }
        
        $query .= " ORDER BY district_name_hindi, block_name_hindi, village_name_hindi ASC";
        
        $stmt = $db->prepare($query);
        if (!empty($district_code)) {
            $stmt->bindParam(":district_code", $district_code);
        }
        if (!empty($block_code)) {
            $stmt->bindParam(":block_code", $block_code);
        }
    }
    
    $stmt->execute();
    $villages = $stmt->fetchAll();
    
    // Format for spinner (simplified)
    $formatted_villages = array();
    foreach ($villages as $village) {
        $formatted_villages[] = array(
            'id' => $village['id'],
            'village_code' => $village['village_code'],
            'village_name' => $village['village_name_hindi'],
            'block_name' => $village['block_name_hindi'],
            'district_name' => $village['district_name_hindi'],
            'state_code' => $village['state_code'],
            'district_code' => $village['district_code'],
            'block_code' => $village['block_code'],
            'display_name' => $village['village_name_hindi'] . ' (' . $village['block_name_hindi'] . ')'
        );
    }
    
    http_response_code(200);
    echo json_encode(array(
        "message" => "Villages retrieved successfully",
        "total" => count($formatted_villages),
        "villages" => $formatted_villages
    ));
    
} catch (Exception $e) {
    http_response_code(500);
    error_log("Villages list error: " . $e->getMessage());
    echo json_encode(array("message" => "Failed to retrieve villages"));
}
?>
