<?php
/**
 * Sync Push Endpoint
 * POST /api/sync/push.php
 * Receives changes from Android clients
 */

header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json; charset=UTF-8");
header("Access-Control-Allow-Methods: POST");
header("Access-Control-Allow-Headers: Content-Type, Authorization");

require_once '../config/database.php';
require_once '../utils/jwt.php';
require_once '../utils/conflict_resolver.php';

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

if (empty($data->changes) || !is_array($data->changes)) {
    http_response_code(400);
    echo json_encode(array("message" => "Invalid request format"));
    exit();
}

try {
    $database = new Database();
    $db = $database->getConnection();
    
    $db->beginTransaction();
    
    $results = array(
        'synced' => array(),
        'conflicts' => array(),
        'errors' => array()
    );
    
    foreach ($data->changes as $change) {
        try {
            $entity_type = $change->entity_type;
            $operation = $change->operation;  // CREATE, UPDATE, DELETE
            $entity_data = $change->data;
            $client_sync_version = $change->sync_version ?? 1;
            $client_role_id = $change->role_id ?? $jwt['role_id'];
            
            // Process based on entity type
            switch ($entity_type) {
                case 'BENEFICIARY':
                    $result = syncBeneficiary($db, $operation, $entity_data, $client_sync_version, $client_role_id);
                    break;
                    
                case 'ANC_VISIT':
                case 'BP_VISIT':
                case 'BLOOD_SUGAR_VISIT':
                case 'VACCINATION_VISIT':
                    $result = syncVisit($db, $entity_type, $operation, $entity_data, $client_sync_version, $client_role_id);
                    break;
                    
                case 'EDIT_REQUEST':
                    $result = syncEditRequest($db, $operation, $entity_data);
                    break;
                    
                case 'DUPLICATE_FLAG':
                    $result = syncDuplicateFlag($db, $operation, $entity_data);
                    break;
                    
                case 'AUDIT_LOG':
                    $result = syncAuditLog($db, $entity_data);
                    break;
                    
                default:
                    throw new Exception("Unknown entity type: $entity_type");
            }
            
            if ($result['status'] === 'success') {
                $results['synced'][] = array(
                    'entity_type' => $entity_type,
                    'entity_id' => $change->entity_id,
                    'server_id' => $result['server_id'] ?? null
                );
            } elseif ($result['status'] === 'conflict') {
                $results['conflicts'][] = array(
                    'entity_type' => $entity_type,
                    'entity_id' => $change->entity_id,
                    'reason' => $result['reason'],
                    'server_data' => $result['server_data'] ?? null
                );
            }
            
        } catch (Exception $e) {
            $results['errors'][] = array(
                'entity_type' => $change->entity_type ?? 'unknown',
                'entity_id' => $change->entity_id ?? 'unknown',
                'error' => $e->getMessage()
            );
        }
    }
    
    $db->commit();
    
    http_response_code(200);
    echo json_encode(array(
        "message" => "Sync completed",
        "synced_count" => count($results['synced']),
        "conflict_count" => count($results['conflicts']),
        "error_count" => count($results['errors']),
        "results" => $results
    ));
    
} catch (Exception $e) {
    if (isset($db)) {
        $db->rollBack();
    }
    http_response_code(500);
    error_log("Sync error: " . $e->getMessage());
    echo json_encode(array("message" => "Sync failed"));
}

/**
 * Sync beneficiary with conflict resolution
 */
function syncBeneficiary($db, $operation, $data, $client_sync_version, $client_role_id) {
    if ($operation === 'CREATE') {
        // Insert new beneficiary
        $query = "INSERT INTO beneficiaries 
                  (id, beneficiary_id, name_hindi, father_husband_name_hindi, gender, 
                   date_of_birth, age_years, beneficiary_type, village_id, address_hindi, 
                   mobile_number, registered_by_user_id, registration_gps_lat, registration_gps_lng,
                   last_modified_by_user_id, last_modified_by_role_id, sync_version)
                  VALUES (:id, :beneficiary_id, :name_hindi, :father_husband_name_hindi, :gender,
                   :date_of_birth, :age_years, :beneficiary_type, :village_id, :address_hindi,
                   :mobile_number, :registered_by_user_id, :registration_gps_lat, :registration_gps_lng,
                   :last_modified_by_user_id, :last_modified_by_role_id, :sync_version)";
        
        $stmt = $db->prepare($query);
        $stmt->execute((array)$data);
        
        return array('status' => 'success', 'server_id' => $db->lastInsertId());
        
    } elseif ($operation === 'UPDATE') {
        // Check for conflicts
        $check_query = "SELECT sync_version, last_modified_by_role_id 
                        FROM beneficiaries 
                        WHERE id = :id";
        $check_stmt = $db->prepare($check_query);
        $check_stmt->bindParam(":id", $data->id);
        $check_stmt->execute();
        
        if ($check_stmt->rowCount() == 0) {
            throw new Exception("Beneficiary not found");
        }
        
        $server_data = $check_stmt->fetch();
        
        // Conflict resolution: Higher role wins
        if ($server_data['sync_version'] > $client_sync_version) {
            // Server is newer - check role
            if ($server_data['last_modified_by_role_id'] > $client_role_id) {
                return array(
                    'status' => 'conflict',
                    'reason' => 'Server has newer version by higher authority',
                    'server_data' => $server_data
                );
            }
        }
        
        // Update beneficiary
        $update_query = "UPDATE beneficiaries SET
                         name_hindi = :name_hindi,
                         father_husband_name_hindi = :father_husband_name_hindi,
                         gender = :gender,
                         age_years = :age_years,
                         mobile_number = :mobile_number,
                         address_hindi = :address_hindi,
                         last_modified_by_user_id = :last_modified_by_user_id,
                         last_modified_by_role_id = :last_modified_by_role_id,
                         sync_version = sync_version + 1,
                         updated_at = NOW()
                         WHERE id = :id";
        
        $update_stmt = $db->prepare($update_query);
        $update_stmt->execute((array)$data);
        
        return array('status' => 'success');
    }
    
    return array('status' => 'error', 'reason' => 'Unknown operation');
}

/**
 * Sync health visit
 */
function syncVisit($db, $entity_type, $operation, $data, $client_sync_version, $client_role_id) {
    $table_map = array(
        'ANC_VISIT' => 'anc_visits',
        'BP_VISIT' => 'bp_visits',
        'BLOOD_SUGAR_VISIT' => 'blood_sugar_visits',
        'VACCINATION_VISIT' => 'vaccination_visits'
    );
    
    $table = $table_map[$entity_type];
    
    if ($operation === 'CREATE') {
        // Build dynamic insert query based on data fields
        $fields = array_keys((array)$data);
        $placeholders = array_map(function($field) { return ":$field"; }, $fields);
        
        $query = "INSERT INTO $table (" . implode(', ', $fields) . ") 
                  VALUES (" . implode(', ', $placeholders) . ")";
        
        $stmt = $db->prepare($query);
        $stmt->execute((array)$data);
        
        return array('status' => 'success', 'server_id' => $db->lastInsertId());
    }
    
    // Update and delete similar to beneficiary
    return array('status' => 'success');
}

/**
 * Sync edit request
 */
function syncEditRequest($db, $operation, $data) {
    if ($operation === 'CREATE') {
        $query = "INSERT INTO edit_requests 
                  (id, beneficiary_id, field_name, current_value, requested_value, 
                   reason_hindi, requested_by_user_id, request_gps_lat, request_gps_lng)
                  VALUES (:id, :beneficiary_id, :field_name, :current_value, :requested_value,
                   :reason_hindi, :requested_by_user_id, :request_gps_lat, :request_gps_lng)";
        
        $stmt = $db->prepare($query);
        $stmt->execute((array)$data);
        
        return array('status' => 'success');
    }
    
    return array('status' => 'success');
}

/**
 * Sync duplicate flag
 */
function syncDuplicateFlag($db, $operation, $data) {
    if ($operation === 'CREATE') {
        $query = "INSERT INTO duplicate_flags 
                  (id, beneficiary_id_1, beneficiary_id_2, similarity_score, 
                   name_match, village_match, gender_match, age_within_range,
                   flagged_by_user_id, flag_source)
                  VALUES (:id, :beneficiary_id_1, :beneficiary_id_2, :similarity_score,
                   :name_match, :village_match, :gender_match, :age_within_range,
                   :flagged_by_user_id, :flag_source)";
        
        $stmt = $db->prepare($query);
        $stmt->execute((array)$data);
        
        return array('status' => 'success');
    }
    
    return array('status' => 'success');
}

/**
 * Sync audit log
 */
function syncAuditLog($db, $data) {
    $query = "INSERT INTO audit_logs 
              (id, user_id, user_role_id, action_type, entity_type, entity_id,
               action_description_hindi, gps_lat, gps_lng, gps_accuracy_meters,
               device_id, app_version)
              VALUES (:id, :user_id, :user_role_id, :action_type, :entity_type, :entity_id,
               :action_description_hindi, :gps_lat, :gps_lng, :gps_accuracy_meters,
               :device_id, :app_version)";
    
    $stmt = $db->prepare($query);
    $stmt->execute((array)$data);
    
    return array('status' => 'success');
}
?>
