<?php
/**
 * Dashboard Statistics Endpoint
 * GET /api/dashboard/stats.php
 * Returns real-time statistics for dashboard cards
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
    
    $stats = array();
    
    // Total beneficiaries (filtered by user's area)
    if ($user_role_id <= 2) {
        // Field Staff and Supervisors - their assigned areas only
        $ben_query = "SELECT COUNT(DISTINCT b.id) as total
                      FROM beneficiaries b
                      INNER JOIN user_area_mappings uam ON b.village_id = uam.village_id
                      WHERE uam.user_id = :user_id
                      AND uam.is_active = 1
                      AND b.is_active = 1";
        
        $stmt = $db->prepare($ben_query);
        $stmt->bindParam(":user_id", $user_id);
        $stmt->execute();
        $stats['total_beneficiaries'] = $stmt->fetch()['total'];
        
    } else {
        // Block Nodal and above - all active beneficiaries
        $ben_query = "SELECT COUNT(*) as total FROM beneficiaries WHERE is_active = 1";
        $stmt = $db->prepare($ben_query);
        $stmt->execute();
        $stats['total_beneficiaries'] = $stmt->fetch()['total'];
    }
    
    // Pending duplicates (for Block Nodal and above)
    if ($user_role_id >= 3) {
        $dup_query = "SELECT COUNT(*) as total FROM duplicate_flags WHERE status = 'PENDING'";
        $stmt = $db->prepare($dup_query);
        $stmt->execute();
        $stats['pending_duplicates'] = $stmt->fetch()['total'];
    } else {
        $stats['pending_duplicates'] = 0;
    }
    
    // Pending edit requests (for Supervisors and above)
    if ($user_role_id >= 2) {
        $edit_query = "SELECT COUNT(*) as total FROM edit_requests WHERE status = 'PENDING'";
        $stmt = $db->prepare($edit_query);
        $stmt->execute();
        $stats['pending_edit_requests'] = $stmt->fetch()['total'];
    } else {
        // For Field Staff - show their submitted pending requests
        $edit_query = "SELECT COUNT(*) as total FROM edit_requests 
                       WHERE requested_by_user_id = :user_id AND status = 'PENDING'";
        $stmt = $db->prepare($edit_query);
        $stmt->bindParam(":user_id", $user_id);
        $stmt->execute();
        $stats['pending_edit_requests'] = $stmt->fetch()['total'];
    }
    
    // Recent visits (last 7 days)
    $visit_query = "SELECT 
                    (SELECT COUNT(*) FROM anc_visits WHERE recorded_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)) as anc,
                    (SELECT COUNT(*) FROM bp_visits WHERE recorded_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)) as bp,
                    (SELECT COUNT(*) FROM blood_sugar_visits WHERE recorded_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)) as blood_sugar,
                    (SELECT COUNT(*) FROM vaccination_visits WHERE recorded_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)) as vaccination";
    
    $stmt = $db->prepare($visit_query);
    $stmt->execute();
    $visit_counts = $stmt->fetch();
    
    $stats['recent_visits'] = array(
        'anc' => $visit_counts['anc'],
        'bp' => $visit_counts['bp'],
        'blood_sugar' => $visit_counts['blood_sugar'],
        'vaccination' => $visit_counts['vaccination'],
        'total' => $visit_counts['anc'] + $visit_counts['bp'] + $visit_counts['blood_sugar'] + $visit_counts['vaccination']
    );
    
    // Beneficiaries registered this month
    $month_query = "SELECT COUNT(*) as total FROM beneficiaries 
                    WHERE MONTH(registration_date) = MONTH(NOW()) 
                    AND YEAR(registration_date) = YEAR(NOW())
                    AND is_active = 1";
    
    if ($user_role_id <= 2) {
        $month_query .= " AND EXISTS (
            SELECT 1 FROM user_area_mappings uam 
            WHERE uam.user_id = :user_id AND uam.village_id = beneficiaries.village_id
        )";
        $stmt = $db->prepare($month_query);
        $stmt->bindParam(":user_id", $user_id);
    } else {
        $stmt = $db->prepare($month_query);
    }
    
    $stmt->execute();
    $stats['registered_this_month'] = $stmt->fetch()['total'];
    
    // Flagged for duplicate review
    $flag_query = "SELECT COUNT(*) as total FROM beneficiaries WHERE is_duplicate_flagged = 1 AND is_active = 1";
    $stmt = $db->prepare($flag_query);
    $stmt->execute();
    $stats['duplicate_flagged_count'] = $stmt->fetch()['total'];
    
    http_response_code(200);
    echo json_encode(array(
        "message" => "Statistics retrieved successfully",
        "stats" => $stats,
        "user_role_id" => $user_role_id,
        "timestamp" => time()
    ));
    
} catch (Exception $e) {
    http_response_code(500);
    error_log("Dashboard stats error: " . $e->getMessage());
    echo json_encode(array("message" => "Failed to retrieve statistics"));
}
?>
