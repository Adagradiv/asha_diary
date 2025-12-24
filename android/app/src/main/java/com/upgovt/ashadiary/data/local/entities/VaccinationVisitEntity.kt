package com.upgovt.ashadiary.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * VACCINATION_VISIT Entity
 * Child immunization tracking
 */
@Entity(
    tableName = "vaccination_visits",
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
        Index(value = ["recorded_by_user_id"]),
        Index(value = ["vaccine_type"])
    ]
)
@Parcelize
data class VaccinationVisitEntity(
    @PrimaryKey
    val id: String,  // UUID
    
    val beneficiary_id: String,  // FK to beneficiaries
    
    // Visit details
    val visit_date: Long,  // Timestamp
    
    // Vaccine information
    val vaccine_type: String,  // "BCG", "OPV", "DPT", "MEASLES", "HEPATITIS_B", etc.
    val vaccine_name_hindi: String,
    val dose_number: Int,  // 1, 2, 3 for multi-dose vaccines
    
    // Batch information
    val vaccine_batch_number: String?,
    val vaccine_expiry_date: Long?,
    
    // Adverse reactions
    val has_adverse_reaction: Boolean = false,
    val adverse_reaction_notes_hindi: String?,
    
    // Next dose
    val next_dose_due_date: Long?,
    
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
