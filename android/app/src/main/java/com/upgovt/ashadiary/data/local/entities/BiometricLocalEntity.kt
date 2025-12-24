package com.upgovt.ashadiary.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * BIOMETRIC_LOCAL Entity
 * CRITICAL: Stores fingerprint templates LOCALLY ONLY
 * NEVER synced to server
 * Encrypted at rest using SQLCipher
 * Used for duplicate beneficiary detection via ISO template matching
 */
@Entity(
    tableName = "biometrics_local",
    foreignKeys = [
        ForeignKey(
            entity = BeneficiaryEntity::class,
            parentColumns = ["id"],
            childColumns = ["beneficiary_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["captured_by_user_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["beneficiary_id"]),
        Index(value = ["finger_position"]),
        Index(value = ["captured_at"])
    ]
)
@Parcelize
data class BiometricLocalEntity(
    @PrimaryKey
    val id: String,  // UUID
    
    val beneficiary_id: String,  // FK to beneficiaries
    
    // Fingerprint metadata
    val finger_position: String,  // "LEFT_THUMB", "RIGHT_THUMB", "LEFT_INDEX", etc.
    val quality_score: Int,  // 0-100 (from Mantra device)
    
    // ISO template (ENCRYPTED at database level via SQLCipher)
    // This is the Mantra RD Service output
    val iso_template: ByteArray,  // ISO 19794-2 format
    
    // Capture metadata
    val captured_by_user_id: String,  // FK to users
    val captured_at: Long = System.currentTimeMillis(),
    val device_serial_number: String,  // Mantra device serial
    
    // CRITICAL: This entity is NEVER synced
    val is_synced: Boolean = false,  // Always false
    val last_synced_at: Long? = null  // Always null
) : Parcelable {
    // Override equals and hashCode to handle ByteArray comparison
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BiometricLocalEntity

        if (id != other.id) return false
        if (beneficiary_id != other.beneficiary_id) return false
        if (finger_position != other.finger_position) return false
        if (quality_score != other.quality_score) return false
        if (!iso_template.contentEquals(other.iso_template)) return false
        if (captured_by_user_id != other.captured_by_user_id) return false
        if (captured_at != other.captured_at) return false
        if (device_serial_number != other.device_serial_number) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + beneficiary_id.hashCode()
        result = 31 * result + finger_position.hashCode()
        result = 31 * result + quality_score
        result = 31 * result + iso_template.contentHashCode()
        result = 31 * result + captured_by_user_id.hashCode()
        result = 31 * result + captured_at.hashCode()
        result = 31 * result + device_serial_number.hashCode()
        return result
    }
}
