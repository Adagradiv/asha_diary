package com.upgovt.ashadiary.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * EDIT_REQUEST Entity
 * Field staff submit edit requests for beneficiary critical fields (name/age/gender)
 * Workflow: Field Staff → Supervisor → Block Nodal (final authority)
 */
@Entity(
    tableName = "edit_requests",
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
            childColumns = ["requested_by_user_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["reviewed_by_user_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["beneficiary_id"]),
        Index(value = ["requested_by_user_id"]),
        Index(value = ["status"]),
        Index(value = ["requested_at"])
    ]
)
@Parcelize
data class EditRequestEntity(
    @PrimaryKey
    val id: String,  // UUID
    
    val beneficiary_id: String,  // FK to beneficiaries
    
    // Request details
    val field_name: String,  // "name_hindi", "date_of_birth", "gender", etc.
    val current_value: String,  // Current value in beneficiary record
    val requested_value: String,  // New value requested
    
    // Justification
    val reason_hindi: String,  // Voice input: why this edit is needed
    
    // Workflow status
    val status: String,  // "PENDING", "APPROVED", "REJECTED"
    
    // Request metadata
    val requested_by_user_id: String,  // FK to users (must be Field Staff)
    val requested_at: Long = System.currentTimeMillis(),
    val request_gps_lat: Double,
    val request_gps_lng: Double,
    
    // Review metadata
    val reviewed_by_user_id: String? = null,  // FK to users (Supervisor or Block Nodal)
    val reviewed_at: Long? = null,
    val review_notes_hindi: String? = null,
    
    val created_at: Long = System.currentTimeMillis(),
    val updated_at: Long = System.currentTimeMillis(),
    
    // Sync metadata
    val is_synced: Boolean = false,
    val last_synced_at: Long? = null,
    val server_id: String? = null
) : Parcelable
