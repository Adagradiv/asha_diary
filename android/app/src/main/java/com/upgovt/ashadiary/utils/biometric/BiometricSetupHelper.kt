package com.upgovt.ashadiary.utils.biometric

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * BiometricSetupHelper - Helps with Mantra RD Service installation
 */
object BiometricSetupHelper {
    
    /**
     * Download URLs for Mantra RD Service
     */
    private const val MANTRA_DOWNLOAD_URL = "https://www.mantratec.com/downloads"
    private const val MANTRA_RD_SERVICE_APK = "MantraRDService.apk"
    
    /**
     * Check if Mantra RD Service needs update
     * Based on version comparison
     */
    fun checkForUpdates(context: Context): BiometricUpdateInfo {
        val helper = MantraRDServiceHelper(context)
        
        if (!helper.isMantraServiceInstalled()) {
            return BiometricUpdateInfo(
                needsUpdate = true,
                isInstalled = false,
                currentVersion = null,
                recommendedVersion = "Latest",
                updateMessage = "Mantra RD Service इंस्टॉल नहीं है। कृपया इसे इंस्टॉल करें।"
            )
        }
        
        // TODO: Implement version checking logic
        // For now, assume installed version is fine
        return BiometricUpdateInfo(
            needsUpdate = false,
            isInstalled = true,
            currentVersion = "Unknown",
            recommendedVersion = "Latest",
            updateMessage = null
        )
    }
    
    /**
     * Open Mantra download page in browser
     */
    fun openDownloadPage(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(MANTRA_DOWNLOAD_URL)
        }
        context.startActivity(intent)
    }
    
    /**
     * Get installation instructions in Hindi
     */
    fun getInstallationInstructions(): List<String> {
        return listOf(
            "1. Mantra MFS100 डिवाइस को USB से कनेक्ट करें",
            "2. Mantra RD Service APK डाउनलोड करें",
            "3. APK इंस्टॉल करें और सभी permissions दें",
            "4. Mantra RD Service ऐप खोलें",
            "5. 'Service Started' मैसेज देखें",
            "6. ASHA Diary ऐप में वापस आएं"
        )
    }
    
    /**
     * Troubleshooting tips
     */
    fun getTroubleshootingTips(): List<String> {
        return listOf(
            "• USB cable सही से कनेक्टेड है?",
            "• Mantra RD Service चल रही है?",
            "• Device में USB debugging enabled है?",
            "• Mantra device की LED जल रही है?",
            "• फोन को रीस्टार्ट करके देखें",
            "• Mantra RD Service को reinstall करें"
        )
    }
}

/**
 * Biometric update information
 */
data class BiometricUpdateInfo(
    val needsUpdate: Boolean,
    val isInstalled: Boolean,
    val currentVersion: String?,
    val recommendedVersion: String,
    val updateMessage: String?
)
