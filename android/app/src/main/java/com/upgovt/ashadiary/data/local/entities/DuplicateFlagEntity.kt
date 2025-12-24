package com.upgovt.ashadiary.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * DUPLICATE_FLAG Entity
 * Tracks potential duplicate beneficiaries
 * Detection: Same name + same village + same gender + age ±2 years
 * Workflow: Field → Warning → Supervisor → Block Nodal → Merge/Inactivate/Keep Both
 */
@Entity(
    tableName = "duplicate_flags",
    foreignKeys = [
        ForeignKey(
            entity = BeneficiaryEntity::class,
            parentColumns = ["id"],
            childColumns = ["beneficiary_id_1"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = BeneficiaryEntity::class,
            parentColumns = ["id"],
            childColumns = ["beneficiary_id_2"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["flagged_by_user_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["resolved_by_user_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["beneficiary_id_1"]),
        Index(value = ["beneficiary_id_2"]),
        Index(value = ["beneficiary_id_1", "beneficiary_id_2"], unique = true),
        Index(value = ["status"]),
        Index(value = ["flagged_at"])
    ]
)
@Parcelize
data class DuplicateFlagEntity(
    @PrimaryKey
    val id: String,  // UUID
    
    // Beneficiaries in question
    val beneficiary_id_1: String,  // FK to beneficiaries (existing)
    val beneficiary_id_2: String,  // FK to beneficiaries (newly added)
    
    // Similarity score (0-100)
    val similarity_score: Int,  // Algorithm-calculated
    
    // Match criteria
    val name_match: Boolean,
    val village_match: Boolean,
    val gender_match: Boolean,
    val age_within_range: Boolean,  // ±2 years
    
    // Status
    val status: String,  // "PENDING", "MERGED", "INACTIVATED", "KEPT_BOTH"
    
    // Flag metadata
    val flagged_by_user_id: String,  // FK to users (system or user)
    val flagged_at: Long = System.currentTimeMillis(),
    val flag_source: String,  // "AUTO_DETECTION", "USER_REPORT"
    
    // Resolution metadata
    val resolved_by_user_id: String? = null,  // FK to users (must be Block Nodal)
    val resolved_at: Long? = null,
    val resolution_action: String? = null,  // "MERGE", "INACTIVATE", "KEEP_BOTH"
    val resolution_notes_hindi: String? = null,
    
    // If merged, which beneficiary is the master
    val master_beneficiary_id: String? = null,
    
    val created_at: Long = System.currentTimeMillis(),
    val updated_at: Long = System.currentTimeMillis(),
    
    // Sync metadata
    val is_synced: Boolean = false,
    val last_synced_at: Long? = null,
    val server_id: String? = null
) : Parcelable
