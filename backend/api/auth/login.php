<?php
/**
 * Authentication Endpoint
 * POST /api/auth/login.php
 */

header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json; charset=UTF-8");
header("Access-Control-Allow-Methods: POST");
header("Access-Control-Max-Age: 3600");
header("Access-Control-Allow-Headers: Content-Type, Authorization");

require_once '../config/database.php';
require_once '../utils/jwt.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    http_response_code(405);
    echo json_encode(array("message" => "Method not allowed"));
    exit();
}

$data = json_decode(file_get_contents("php://input"));

if (empty($data->username) || empty($data->password)) {
    http_response_code(400);
    echo json_encode(array("message" => "Username and password required"));
    exit();
}

try {
    $database = new Database();
    $db = $database->getConnection();
    
    // Get user
    $query = "SELECT u.id, u.username, u.password_hash, u.full_name, u.phone_number, 
                     u.role_id, u.worker_code, u.is_active,
                     r.name_hindi as role_name, r.authority_level,
                     r.can_edit_beneficiary, r.can_approve_edits, r.can_merge_duplicates
              FROM users u
              INNER JOIN roles r ON u.role_id = r.id
              WHERE u.username = :username AND u.is_active = 1
              LIMIT 1";
    
    $stmt = $db->prepare($query);
    $stmt->bindParam(":username", $data->username);
    $stmt->execute();
    
    if ($stmt->rowCount() == 0) {
        http_response_code(401);
        echo json_encode(array("message" => "Invalid credentials"));
        exit();
    }
    
    $user = $stmt->fetch();
    
    // Verify password
    if (!password_verify($data->password, $user['password_hash'])) {
        http_response_code(401);
        echo json_encode(array("message" => "Invalid credentials"));
        exit();
    }
    
    // Update last login
    $update_query = "UPDATE users SET last_login_at = NOW() WHERE id = :id";
    $update_stmt = $db->prepare($update_query);
    $update_stmt->bindParam(":id", $user['id']);
    $update_stmt->execute();
    
    // Generate JWT token
    $jwt = JWTHelper::generateToken(array(
        "id" => $user['id'],
        "username" => $user['username'],
        "role_id" => $user['role_id'],
        "authority_level" => $user['authority_level']
    ));
    
    http_response_code(200);
    echo json_encode(array(
        "message" => "Login successful",
        "token" => $jwt,
        "user" => array(
            "id" => $user['id'],
            "username" => $user['username'],
            "full_name" => $user['full_name'],
            "phone_number" => $user['phone_number'],
            "role_id" => $user['role_id'],
            "role_name" => $user['role_name'],
            "worker_code" => $user['worker_code'],
            "authority_level" => $user['authority_level'],
            "permissions" => array(
                "can_edit_beneficiary" => (bool)$user['can_edit_beneficiary'],
                "can_approve_edits" => (bool)$user['can_approve_edits'],
                "can_merge_duplicates" => (bool)$user['can_merge_duplicates']
            )
        )
    ));
    
} catch (Exception $e) {
    http_response_code(500);
    error_log("Login error: " . $e->getMessage());
    echo json_encode(array("message" => "Login failed"));
}
?>
