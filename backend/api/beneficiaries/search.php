<?php
/**
 * Beneficiary Search Endpoint
 * GET /api/beneficiaries/search.php
 * Search beneficiaries by name (Hindi), village, mobile
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

if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    http_response_code(405);
    echo json_encode(array("message" => "Method not allowed"));
    exit();
}

try {
    $database = new Database();
    $db = $database->getConnection();
    
    $user_id = $jwt['id'];
    
    // Search parameters
    $search_query = $_GET['q'] ?? '';
    $village_id = $_GET['village_id'] ?? '';
    $mobile = $_GET['mobile'] ?? '';
    $page = isset($_GET['page']) ? (int)$_GET['page'] : 1;
    $limit = 20;
    $offset = ($page - 1) * $limit;
    
    // Build query
    $where_conditions = array("b.is_active = 1");
    $params = array();
    
    if (!empty($search_query)) {
        // FULLTEXT search for Hindi names
        $where_conditions[] = "MATCH(b.name_hindi, b.father_husband_name_hindi) AGAINST (:search_query IN NATURAL LANGUAGE MODE)";
        $params[':search_query'] = $search_query;
    }
    
    if (!empty($village_id)) {
        $where_conditions[] = "b.village_id = :village_id";
        $params[':village_id'] = $village_id;
    }
    
    if (!empty($mobile)) {
        $where_conditions[] = "b.mobile_number LIKE :mobile";
        $params[':mobile'] = "%$mobile%";
    }
    
    // Restrict to user's assigned areas
    $where_conditions[] = "(EXISTS (
        SELECT 1 FROM user_area_mappings uam 
        WHERE uam.user_id = :user_id AND uam.village_id = b.village_id
    ) OR b.registered_by_user_id = :user_id)";
    $params[':user_id'] = $user_id;
    
    $where_clause = implode(' AND ', $where_conditions);
    
    // Count total results
    $count_query = "SELECT COUNT(*) as total FROM beneficiaries b WHERE $where_clause";
    $count_stmt = $db->prepare($count_query);
    foreach ($params as $key => $value) {
        $count_stmt->bindValue($key, $value);
    }
    $count_stmt->execute();
    $total = $count_stmt->fetch()['total'];
    
    // Get paginated results
    $query = "SELECT b.*, v.village_name_hindi, v.block_name_hindi
              FROM beneficiaries b
              INNER JOIN villages v ON b.village_id = v.id
              WHERE $where_clause
              ORDER BY b.created_at DESC
              LIMIT :limit OFFSET :offset";
    
    $stmt = $db->prepare($query);
    foreach ($params as $key => $value) {
        $stmt->bindValue($key, $value);
    }
    $stmt->bindValue(':limit', $limit, PDO::PARAM_INT);
    $stmt->bindValue(':offset', $offset, PDO::PARAM_INT);
    $stmt->execute();
    
    $beneficiaries = $stmt->fetchAll();
    
    http_response_code(200);
    echo json_encode(array(
        "message" => "Search successful",
        "total" => $total,
        "page" => $page,
        "per_page" => $limit,
        "total_pages" => ceil($total / $limit),
        "beneficiaries" => $beneficiaries
    ));
    
} catch (Exception $e) {
    http_response_code(500);
    error_log("Search error: " . $e->getMessage());
    echo json_encode(array("message" => "Search failed"));
}
?>
