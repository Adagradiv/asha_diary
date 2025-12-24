<?php
/**
 * Sync Pull Endpoint
 * POST /api/sync/pull.php
 * Returns server changes since last sync timestamp
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

if (empty($data->last_sync_timestamp)) {
    http_response_code(400);
    echo json_encode(array("message" => "last_sync_timestamp required"));
    exit();
}

try {
    $database = new Database();
    $db = $database->getConnection();
    
    $last_sync = date('Y-m-d H:i:s', $data->last_sync_timestamp);
    $user_id = $jwt['id'];
    $user_role_id = $jwt['role_id'];
    
    $changes = array(
        'beneficiaries' => array(),
        'anc_visits' => array(),
        'bp_visits' => array(),
        'blood_sugar_visits' => array(),
        'vaccination_visits' => array(),
        'edit_requests' => array(),
        'duplicate_flags' => array()
    );
    
    // Pull beneficiaries updated since last sync
    // Only beneficiaries in user's assigned villages or created by user
    $query = "SELECT b.* FROM beneficiaries b
              LEFT JOIN user_area_mappings uam ON b.village_id = uam.village_id
              WHERE (uam.user_id = :user_id OR b.registered_by_user_id = :user_id)
              AND b.updated_at > :last_sync
              AND b.is_active = 1
              ORDER BY b.updated_at ASC
              LIMIT 100";
    
    $stmt = $db->prepare($query);
    $stmt->bindParam(":user_id", $user_id);
    $stmt->bindParam(":last_sync", $last_sync);
    $stmt->execute();
    
    $changes['beneficiaries'] = $stmt->fetchAll();
    
    // Pull visits for beneficiaries in user's area
    $visit_query = "SELECT v.* FROM anc_visits v
                    INNER JOIN beneficiaries b ON v.beneficiary_id = b.id
                    LEFT JOIN user_area_mappings uam ON b.village_id = uam.village_id
                    WHERE (uam.user_id = :user_id OR v.recorded_by_user_id = :user_id)
                    AND v.updated_at > :last_sync
                    ORDER BY v.updated_at ASC
                    LIMIT 100";
    
    $stmt = $db->prepare($visit_query);
    $stmt->bindParam(":user_id", $user_id);
    $stmt->bindParam(":last_sync", $last_sync);
    $stmt->execute();
    $changes['anc_visits'] = $stmt->fetchAll();
    
    // Similar queries for other visit types
    // BP visits
    $visit_query = str_replace('anc_visits', 'bp_visits', $visit_query);
    $stmt = $db->prepare($visit_query);
    $stmt->bindParam(":user_id", $user_id);
    $stmt->bindParam(":last_sync", $last_sync);
    $stmt->execute();
    $changes['bp_visits'] = $stmt->fetchAll();
    
    // Blood sugar visits
    $visit_query = str_replace('bp_visits', 'blood_sugar_visits', $visit_query);
    $stmt = $db->prepare($visit_query);
    $stmt->bindParam(":user_id", $user_id);
    $stmt->bindParam(":last_sync", $last_sync);
    $stmt->execute();
    $changes['blood_sugar_visits'] = $stmt->fetchAll();
    
    // Vaccination visits
    $visit_query = str_replace('blood_sugar_visits', 'vaccination_visits', $visit_query);
    $stmt = $db->prepare($visit_query);
    $stmt->bindParam(":user_id", $user_id);
    $stmt->bindParam(":last_sync", $last_sync);
    $stmt->execute();
    $changes['vaccination_visits'] = $stmt->fetchAll();
    
    // Pull edit requests (for Supervisors and above)
    if ($user_role_id >= 2) {
        $edit_query = "SELECT * FROM edit_requests
                       WHERE updated_at > :last_sync
                       AND status = 'PENDING'
                       ORDER BY requested_at ASC
                       LIMIT 50";
        
        $stmt = $db->prepare($edit_query);
        $stmt->bindParam(":last_sync", $last_sync);
        $stmt->execute();
        $changes['edit_requests'] = $stmt->fetchAll();
    }
    
    // Pull duplicate flags (for Block Nodal and above)
    if ($user_role_id >= 3) {
        $dup_query = "SELECT * FROM duplicate_flags
                      WHERE updated_at > :last_sync
                      AND status = 'PENDING'
                      ORDER BY flagged_at ASC
                      LIMIT 50";
        
        $stmt = $db->prepare($dup_query);
        $stmt->bindParam(":last_sync", $last_sync);
        $stmt->execute();
        $changes['duplicate_flags'] = $stmt->fetchAll();
    }
    
    http_response_code(200);
    echo json_encode(array(
        "message" => "Sync pull successful",
        "server_timestamp" => time(),
        "changes" => $changes,
        "total_changes" => array_sum(array_map('count', $changes))
    ));
    
} catch (Exception $e) {
    http_response_code(500);
    error_log("Sync pull error: " . $e->getMessage());
    echo json_encode(array("message" => "Sync pull failed"));
}
?>
