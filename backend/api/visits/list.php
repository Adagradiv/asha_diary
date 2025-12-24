<?php
/**
 * Visits List Endpoint
 * GET /api/visits/list.php
 * Get all visits for a beneficiary (all types)
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

$beneficiary_id = $_GET['beneficiary_id'] ?? '';

if (empty($beneficiary_id)) {
    http_response_code(400);
    echo json_encode(array("message" => "beneficiary_id required"));
    exit();
}

try {
    $database = new Database();
    $db = $database->getConnection();
    
    // Verify beneficiary exists
    $ben_query = "SELECT id FROM beneficiaries WHERE id = :id AND is_active = 1";
    $ben_stmt = $db->prepare($ben_query);
    $ben_stmt->bindParam(":id", $beneficiary_id);
    $ben_stmt->execute();
    
    if ($ben_stmt->rowCount() == 0) {
        http_response_code(404);
        echo json_encode(array("message" => "Beneficiary not found"));
        exit();
    }
    
    $visits = array(
        'anc_visits' => array(),
        'bp_visits' => array(),
        'blood_sugar_visits' => array(),
        'vaccination_visits' => array()
    );
    
    // Get ANC visits
    $anc_query = "SELECT 
                    id, visit_number, visit_date, weight_kg, bp_systolic, bp_diastolic,
                    hemoglobin_gm_dl, ifa_tablets_given, advice_notes_hindi,
                    recorded_at, gps_lat, gps_lng
                  FROM anc_visits
                  WHERE beneficiary_id = :beneficiary_id
                  ORDER BY visit_date DESC, recorded_at DESC";
    
    $stmt = $db->prepare($anc_query);
    $stmt->bindParam(":beneficiary_id", $beneficiary_id);
    $stmt->execute();
    $visits['anc_visits'] = $stmt->fetchAll();
    
    // Get BP visits
    $bp_query = "SELECT 
                    id, visit_date, bp_systolic, bp_diastolic, pulse_rate,
                    bp_category, is_referred, medications_prescribed_hindi,
                    advice_notes_hindi, recorded_at, gps_lat, gps_lng
                 FROM bp_visits
                 WHERE beneficiary_id = :beneficiary_id
                 ORDER BY visit_date DESC, recorded_at DESC";
    
    $stmt = $db->prepare($bp_query);
    $stmt->bindParam(":beneficiary_id", $beneficiary_id);
    $stmt->execute();
    $visits['bp_visits'] = $stmt->fetchAll();
    
    // Get Blood Sugar visits
    $bs_query = "SELECT 
                    id, visit_date, test_type, blood_sugar_mg_dl, diabetes_risk,
                    is_referred, diet_advice_hindi, exercise_advice_hindi,
                    recorded_at, gps_lat, gps_lng
                 FROM blood_sugar_visits
                 WHERE beneficiary_id = :beneficiary_id
                 ORDER BY visit_date DESC, recorded_at DESC";
    
    $stmt = $db->prepare($bs_query);
    $stmt->bindParam(":beneficiary_id", $beneficiary_id);
    $stmt->execute();
    $visits['blood_sugar_visits'] = $stmt->fetchAll();
    
    // Get Vaccination visits
    $vac_query = "SELECT 
                    id, visit_date, vaccine_type, vaccine_name_hindi, dose_number,
                    vaccine_batch_number, has_adverse_reaction, adverse_reaction_notes_hindi,
                    next_dose_due_date, recorded_at, gps_lat, gps_lng
                  FROM vaccination_visits
                  WHERE beneficiary_id = :beneficiary_id
                  ORDER BY visit_date DESC, recorded_at DESC";
    
    $stmt = $db->prepare($vac_query);
    $stmt->bindParam(":beneficiary_id", $beneficiary_id);
    $stmt->execute();
    $visits['vaccination_visits'] = $stmt->fetchAll();
    
    // Calculate totals
    $totals = array(
        'anc' => count($visits['anc_visits']),
        'bp' => count($visits['bp_visits']),
        'blood_sugar' => count($visits['blood_sugar_visits']),
        'vaccination' => count($visits['vaccination_visits']),
        'total' => count($visits['anc_visits']) + count($visits['bp_visits']) + 
                   count($visits['blood_sugar_visits']) + count($visits['vaccination_visits'])
    );
    
    http_response_code(200);
    echo json_encode(array(
        "message" => "Visits retrieved successfully",
        "beneficiary_id" => $beneficiary_id,
        "totals" => $totals,
        "visits" => $visits
    ));
    
} catch (Exception $e) {
    http_response_code(500);
    error_log("Visits list error: " . $e->getMessage());
    echo json_encode(array("message" => "Failed to retrieve visits"));
}
?>
