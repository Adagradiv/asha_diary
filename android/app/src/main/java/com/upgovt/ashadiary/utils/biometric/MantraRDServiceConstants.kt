package com.upgovt.ashadiary.utils.biometric

import android.content.Intent

/**
 * MantraRDServiceConstants - Constants for Mantra MFS100 RD Service integration
 * 
 * Mantra RD Service is used by government of India for biometric authentication
 * across Aadhaar, NPCI, and other public services
 */
object MantraRDServiceConstants {
    
    // Intent Actions
    const val ACTION_CAPTURE = "in.gov.uidai.rdservice.fp.CAPTURE"
    const val ACTION_DEVICE_INFO = "in.gov.uidai.rdservice.fp.INFO"
    
    // Package name for Mantra RD Service
    const val MANTRA_PACKAGE = "com.mantra.rdservice"
    
    // Request codes
    const val REQUEST_CODE_CAPTURE_FINGERPRINT = 1001
    const val REQUEST_CODE_DEVICE_INFO = 1002
    
    // Finger positions (as per ISO 19794-2)
    object FingerPosition {
        const val RIGHT_THUMB = "RIGHT_THUMB"
        const val RIGHT_INDEX = "RIGHT_INDEX"
        const val RIGHT_MIDDLE = "RIGHT_MIDDLE"
        const val RIGHT_RING = "RIGHT_RING"
        const val RIGHT_LITTLE = "RIGHT_LITTLE"
        
        const val LEFT_THUMB = "LEFT_THUMB"
        const val LEFT_INDEX = "LEFT_INDEX"
        const val LEFT_MIDDLE = "LEFT_MIDDLE"
        const val LEFT_RING = "LEFT_RING"
        const val LEFT_LITTLE = "LEFT_LITTLE"
        
        fun getAllPositions() = listOf(
            RIGHT_THUMB, RIGHT_INDEX, RIGHT_MIDDLE, RIGHT_RING, RIGHT_LITTLE,
            LEFT_THUMB, LEFT_INDEX, LEFT_MIDDLE, LEFT_RING, LEFT_LITTLE
        )
    }
    
    // Quality thresholds
    const val MIN_QUALITY_SCORE = 60  // NFIQ score (0-100, higher is better)
    const val RECOMMENDED_QUALITY_SCORE = 70
    
    // Timeout
    const val CAPTURE_TIMEOUT_MS = 10000  // 10 seconds
    
    // Intent Extra Keys
    const val EXTRA_PID_OPTIONS = "PID_OPTIONS"
    const val EXTRA_BIOMETRIC_DATA = "BIOMETRIC_DATA"
    
    // PID Options XML for capture request
    fun getPIDOptionsXml(timeout: Int = CAPTURE_TIMEOUT_MS): String {
        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <PidOptions ver="1.0">
                <Opts fCount="1" fType="2" iCount="0" pCount="0" format="0" pidVer="2.0" timeout="$timeout" otp="" wadh="" posh="UNKNOWN" env="P"/>
                <CustOpts>
                    <Param name="mantrakey" value=""/>
                </CustOpts>
            </PidOptions>
        """.trimIndent()
    }
}

/**
 * Biometric capture result
 */
data class BiometricCaptureResult(
    val success: Boolean,
    val isoTemplate: ByteArray?,
    val qualityScore: Int,
    val fingerPosition: String?,
    val deviceSerialNumber: String,
    val errorMessage: String? = null,
    val pidData: String? = null  // Full PID XML response
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BiometricCaptureResult

        if (success != other.success) return false
        if (isoTemplate != null) {
            if (other.isoTemplate == null) return false
            if (!isoTemplate.contentEquals(other.isoTemplate)) return false
        } else if (other.isoTemplate != null) return false
        if (qualityScore != other.qualityScore) return false
        if (fingerPosition != other.fingerPosition) return false
        if (deviceSerialNumber != other.deviceSerialNumber) return false
        if (errorMessage != other.errorMessage) return false

        return true
    }

    override fun hashCode(): Int {
        var result = success.hashCode()
        result = 31 * result + (isoTemplate?.contentHashCode() ?: 0)
        result = 31 * result + qualityScore
        result = 31 * result + (fingerPosition?.hashCode() ?: 0)
        result = 31 * result + deviceSerialNumber.hashCode()
        result = 31 * result + (errorMessage?.hashCode() ?: 0)
        return result
    }
}
