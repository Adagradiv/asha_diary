package com.upgovt.ashadiary.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * BLOOD_SUGAR_VISIT Entity
 * Blood Sugar/Diabetes monitoring visits
 */
@Entity(
    tableName = "blood_sugar_visits",
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
            childColumns = ["recorded_by_user_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["beneficiary_id"]),
        Index(value = ["visit_date"]),
        Index(value = ["recorded_by_user_id"])
    ]
)
@Parcelize
data class BloodSugarVisitEntity(
    @PrimaryKey
    val id: String,  // UUID
    
    val beneficiary_id: String,  // FK to beneficiaries
    
    // Visit details
    val visit_date: Long,  // Timestamp
    
    // Blood sugar readings
    val test_type: String,  // "FASTING", "RANDOM", "POST_PRANDIAL"
    val blood_sugar_mg_dl: Int,
    
    // Risk classification
    val diabetes_risk: String,  // "NORMAL", "PRE_DIABETIC", "DIABETIC"
    
    // Follow-up
    val is_referred: Boolean = false,
    val referral_facility_hindi: String?,
    val follow_up_date: Long?,
    
    // Medications prescribed
    val medications_prescribed_hindi: String?,
    
    // Lifestyle advice (voice input)
    val diet_advice_hindi: String?,
    val exercise_advice_hindi: String?,
    
    // Metadata
    val recorded_by_user_id: String,  // FK to users
    val recorded_at: Long = System.currentTimeMillis(),
    val gps_lat: Double,
    val gps_lng: Double,
    
    val created_at: Long = System.currentTimeMillis(),
    val updated_at: Long = System.currentTimeMillis(),
    
    // Sync metadata
    val is_synced: Boolean = false,
    val last_synced_at: Long? = null,
    val server_id: String? = null,
    
    val last_modified_by_role_id: Int,
    val sync_version: Int = 1
) : Parcelable
