package com.upgovt.ashadiary.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * ANC_VISIT Entity
 * Tracks Antenatal Care visits for pregnant women
 */
@Entity(
    tableName = "anc_visits",
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
data class ANCVisitEntity(
    @PrimaryKey
    val id: String,  // UUID
    
    val beneficiary_id: String,  // FK to beneficiaries
    
    // Visit details
    val visit_number: Int,  // 1-9 (ANC 1, ANC 2, ...)
    val visit_date: Long,  // Timestamp
    
    // Clinical measurements
    val weight_kg: Float?,
    val blood_pressure_systolic: Int?,
    val blood_pressure_diastolic: Int?,
    val hemoglobin_gm_dl: Float?,
    
    // Risk assessment
    val risk_factors_hindi: String?,  // Comma-separated
    val complications_hindi: String?,
    
    // Medications/Supplements
    val ifa_tablets_given: Int?,  // Iron-Folic Acid tablets count
    val calcium_tablets_given: Int?,
    val other_medicines_hindi: String?,
    
    // Advice given (voice input)
    val advice_notes_hindi: String?,
    
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
