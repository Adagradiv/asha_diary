package com.upgovt.ashadiary.utils.biometric

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Base64
import android.util.Log
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/**
 * MantraRDServiceHelper - Helper class for Mantra MFS100 biometric device integration
 * 
 * Handles:
 * - Device availability check
 * - Fingerprint capture via Intent
 * - PID data parsing
 * - ISO template extraction
 */
class MantraRDServiceHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "MantraRDService"
    }
    
    /**
     * Check if Mantra RD Service is installed
     */
    fun isMantraServiceInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(
                MantraRDServiceConstants.MANTRA_PACKAGE,
                PackageManager.GET_ACTIVITIES
            )
            true
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Mantra RD Service not installed")
            false
        }
    }
    
    /**
     * Launch Mantra RD Service to capture fingerprint
     * 
     * @param activity Activity to launch intent from
     * @param requestCode Request code for result callback
     */
    fun captureFingerprint(activity: Activity, requestCode: Int = MantraRDServiceConstants.REQUEST_CODE_CAPTURE_FINGERPRINT) {
        if (!isMantraServiceInstalled()) {
            throw IllegalStateException("Mantra RD Service not installed. Please install Mantra MFS100 driver.")
        }
        
        val pidOptions = MantraRDServiceConstants.getPIDOptionsXml()
        
        val intent = Intent(MantraRDServiceConstants.ACTION_CAPTURE).apply {
            setPackage(MantraRDServiceConstants.MANTRA_PACKAGE)
            putExtra(MantraRDServiceConstants.EXTRA_PID_OPTIONS, pidOptions)
        }
        
        activity.startActivityForResult(intent, requestCode)
    }
    
    /**
     * Get device information
     */
    fun getDeviceInfo(activity: Activity, requestCode: Int = MantraRDServiceConstants.REQUEST_CODE_DEVICE_INFO) {
        if (!isMantraServiceInstalled()) {
            throw IllegalStateException("Mantra RD Service not installed")
        }
        
        val intent = Intent(MantraRDServiceConstants.ACTION_DEVICE_INFO).apply {
            setPackage(MantraRDServiceConstants.MANTRA_PACKAGE)
        }
        
        activity.startActivityForResult(intent, requestCode)
    }
    
    /**
     * Parse PID data from Mantra RD Service response
     * Extracts ISO template, quality score, and device info
     */
    fun parsePIDData(pidDataXml: String): BiometricCaptureResult {
        return try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(pidDataXml))
            
            var isoTemplateBase64: String? = null
            var qualityScore = 0
            var deviceSerialNumber = ""
            var errorCode = 0
            var errorMessage: String? = null
            
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "Resp" -> {
                                errorCode = parser.getAttributeValue(null, "errCode")?.toIntOrNull() ?: 0
                                errorMessage = parser.getAttributeValue(null, "errInfo")
                            }
                            "DeviceInfo" -> {
                                deviceSerialNumber = parser.getAttributeValue(null, "dc") ?: ""
                            }
                            "Skey" -> {
                                // Skip for now - used for Aadhaar authentication
                            }
                            "Data" -> {
                                // ISO template is in the text content
                                parser.next()
                                if (parser.eventType == XmlPullParser.TEXT) {
                                    isoTemplateBase64 = parser.text
                                }
                            }
                            "Param" -> {
                                val paramName = parser.getAttributeValue(null, "name")
                                if (paramName == "qScore") {
                                    qualityScore = parser.getAttributeValue(null, "value")?.toIntOrNull() ?: 0
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
            
            // Check if capture was successful
            if (errorCode != 0) {
                return BiometricCaptureResult(
                    success = false,
                    isoTemplate = null,
                    qualityScore = 0,
                    fingerPosition = null,
                    deviceSerialNumber = deviceSerialNumber,
                    errorMessage = errorMessage ?: "Unknown error (code: $errorCode)",
                    pidData = pidDataXml
                )
            }
            
            // Decode ISO template
            val isoTemplate = if (isoTemplateBase64 != null) {
                try {
                    Base64.decode(isoTemplateBase64, Base64.DEFAULT)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to decode ISO template", e)
                    null
                }
            } else {
                null
            }
            
            BiometricCaptureResult(
                success = isoTemplate != null && qualityScore >= MantraRDServiceConstants.MIN_QUALITY_SCORE,
                isoTemplate = isoTemplate,
                qualityScore = qualityScore,
                fingerPosition = null,  // Will be set by caller
                deviceSerialNumber = deviceSerialNumber,
                errorMessage = if (qualityScore < MantraRDServiceConstants.MIN_QUALITY_SCORE) {
                    "गुणवत्ता कम है (Quality score: $qualityScore). कृपया फिर से प्रयास करें।"
                } else null,
                pidData = pidDataXml
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse PID data", e)
            BiometricCaptureResult(
                success = false,
                isoTemplate = null,
                qualityScore = 0,
                fingerPosition = null,
                deviceSerialNumber = "",
                errorMessage = "PID data parsing failed: ${e.message}",
                pidData = pidDataXml
            )
        }
    }
    
    /**
     * Handle activity result from Mantra RD Service
     * Call this from Activity.onActivityResult()
     */
    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): BiometricCaptureResult? {
        if (requestCode != MantraRDServiceConstants.REQUEST_CODE_CAPTURE_FINGERPRINT) {
            return null
        }
        
        if (resultCode != Activity.RESULT_OK || data == null) {
            return BiometricCaptureResult(
                success = false,
                isoTemplate = null,
                qualityScore = 0,
                fingerPosition = null,
                deviceSerialNumber = "",
                errorMessage = "फिंगरप्रिंट कैप्चर रद्द किया गया या विफल रहा"
            )
        }
        
        val pidData = data.getStringExtra(MantraRDServiceConstants.EXTRA_BIOMETRIC_DATA)
        
        if (pidData.isNullOrEmpty()) {
            return BiometricCaptureResult(
                success = false,
                isoTemplate = null,
                qualityScore = 0,
                fingerPosition = null,
                deviceSerialNumber = "",
                errorMessage = "कोई बायोमेट्रिक डेटा प्राप्त नहीं हुआ"
            )
        }
        
        return parsePIDData(pidData)
    }
}
