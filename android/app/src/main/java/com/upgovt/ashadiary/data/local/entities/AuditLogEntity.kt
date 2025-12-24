package com.upgovt.ashadiary.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * AUDIT_LOG Entity
 * Government audit trail - tracks ALL data modifications
 * GPS stamped, immutable, exportable to PDF
 */
@Entity(
    tableName = "audit_logs",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["action_timestamp"]),
        Index(value = ["entity_type"]),
        Index(value = ["entity_id"]),
        Index(value = ["action_type"])
    ]
)
@Parcelize
data class AuditLogEntity(
    @PrimaryKey
    val id: String,  // UUID
    
    // Who performed the action
    val user_id: String,  // FK to users
    val user_role_id: Int,  // Role at time of action (for forensics)
    
    // What was done
    val action_type: String,  // "CREATE", "UPDATE", "DELETE", "MERGE", "APPROVE", "REJECT"
    val entity_type: String,  // "BENEFICIARY", "ANC_VISIT", "BP_VISIT", "EDIT_REQUEST", etc.
    val entity_id: String,  // ID of the affected record
    
    // Details
    val field_name: String? = null,  // Which field was changed (for UPDATE)
    val old_value: String? = null,
    val new_value: String? = null,
    
    // Description for complex actions
    val action_description_hindi: String,
    
    // Timestamp (immutable)
    val action_timestamp: Long = System.currentTimeMillis(),
    
    // GPS location (MANDATORY)
    val gps_lat: Double,
    val gps_lng: Double,
    val gps_accuracy_meters: Float,
    
    // Device info
    val device_id: String,
    val app_version: String,
    
    // Sync metadata (audit logs MUST be synced)
    val is_synced: Boolean = false,
    val last_synced_at: Long? = null,
    val server_id: String? = null
) : Parcelable
