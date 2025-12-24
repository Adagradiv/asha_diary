<?php
/**
 * JWT Helper - JSON Web Token utilities
 */

class JWTHelper {
    private static $secret_key = "CHANGE_THIS_SECRET_KEY_IN_PRODUCTION";  // TODO: Use environment variable
    private static $algorithm = 'HS256';
    private static $expiration_time = 86400;  // 24 hours
    
    /**
     * Generate JWT token
     */
    public static function generateToken($payload) {
        $header = array(
            "alg" => self::$algorithm,
            "typ" => "JWT"
        );
        
        $payload['iat'] = time();
        $payload['exp'] = time() + self::$expiration_time;
        
        $base64_header = self::base64UrlEncode(json_encode($header));
        $base64_payload = self::base64UrlEncode(json_encode($payload));
        
        $signature = hash_hmac('sha256', $base64_header . "." . $base64_payload, self::$secret_key, true);
        $base64_signature = self::base64UrlEncode($signature);
        
        return $base64_header . '.' . $base64_payload . '.' . $base64_signature;
    }
    
    /**
     * Validate JWT token from request
     */
    public static function validateRequest() {
        $headers = getallheaders();
        $authHeader = $headers['Authorization'] ?? '';
        
        if (empty($authHeader)) {
            return false;
        }
        
        $token = str_replace('Bearer ', '', $authHeader);
        return self::validateToken($token);
    }
    
    /**
     * Validate JWT token
     */
    public static function validateToken($token) {
        try {
            $parts = explode('.', $token);
            
            if (count($parts) !== 3) {
                return false;
            }
            
            list($base64_header, $base64_payload, $base64_signature) = $parts;
            
            // Verify signature
            $signature = hash_hmac('sha256', $base64_header . "." . $base64_payload, self::$secret_key, true);
            $base64_signature_check = self::base64UrlEncode($signature);
            
            if ($base64_signature_check !== $base64_signature) {
                return false;
            }
            
            // Decode payload
            $payload = json_decode(self::base64UrlDecode($base64_payload), true);
            
            // Check expiration
            if (isset($payload['exp']) && $payload['exp'] < time()) {
                return false;
            }
            
            return $payload;
            
        } catch (Exception $e) {
            return false;
        }
    }
    
    /**
     * Base64 URL encode
     */
    private static function base64UrlEncode($data) {
        return rtrim(strtr(base64_encode($data), '+/', '-_'), '=');
    }
    
    /**
     * Base64 URL decode
     */
    private static function base64UrlDecode($data) {
        return base64_decode(strtr($data, '-_', '+/'));
    }
}
?>
