package com.upgovt.ashadiary.data.local.database

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * DatabaseEncryption - Manages database passphrase securely
 * Uses Android Keystore for secure key storage
 * Passphrase never stored in plain text
 */
object DatabaseEncryption {
    
    private const val KEYSTORE_ALIAS = "asha_diary_db_key"
    private const val PREF_FILE_NAME = "asha_diary_secure_prefs"
    private const val PREF_KEY_PASSPHRASE = "db_passphrase"
    
    /**
     * Get or generate database passphrase
     * Uses Android Keystore for secure storage
     */
    fun getOrCreatePassphrase(context: Context): CharArray {
        val encryptedPrefs = getEncryptedPreferences(context)
        
        // Check if passphrase already exists
        val existingPassphrase = encryptedPrefs.getString(PREF_KEY_PASSPHRASE, null)
        
        return if (existingPassphrase != null) {
            existingPassphrase.toCharArray()
        } else {
            // Generate new passphrase
            val newPassphrase = generateSecurePassphrase()
            
            // Save encrypted passphrase
            encryptedPrefs.edit()
                .putString(PREF_KEY_PASSPHRASE, String(newPassphrase))
                .apply()
            
            newPassphrase
        }
    }
    
    /**
     * Get EncryptedSharedPreferences for secure storage
     */
    private fun getEncryptedPreferences(context: Context): android.content.SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        
        return EncryptedSharedPreferences.create(
            PREF_FILE_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    /**
     * Generate cryptographically secure passphrase
     */
    private fun generateSecurePassphrase(): CharArray {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setKeySize(256)
            .build()
        
        keyGenerator.init(keyGenParameterSpec)
        val secretKey = keyGenerator.generateKey()
        
        // Generate 32-character passphrase for SQLCipher
        val passphrase = java.util.UUID.randomUUID().toString().replace("-", "")
        return passphrase.toCharArray()
    }
    
    /**
     * Clear passphrase (for logout/reset)
     */
    fun clearPassphrase(context: Context) {
        val encryptedPrefs = getEncryptedPreferences(context)
        encryptedPrefs.edit()
            .remove(PREF_KEY_PASSPHRASE)
            .apply()
        
        // Clear database instance
        ASHADiaryDatabase.clearInstance()
    }
}
