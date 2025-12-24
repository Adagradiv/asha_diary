package com.upgovt.ashadiary.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * SYNC_QUEUE Entity
 * Manages offline sync queue with retry logic
 * WorkManager polls this queue every 2 hours
 */
@Entity(
    tableName = "sync_queue",
    indices = [
        Index(value = ["entity_type"]),
        Index(value = ["sync_status"]),
        Index(value = ["priority"], order = Index.Order.DESC),
        Index(value = ["created_at"]),
        Index(value = ["retry_count"])
    ]
)
@Parcelize
data class SyncQueueEntity(
    @PrimaryKey
    val id: String,  // UUID
    
    // What entity needs to be synced
    val entity_type: String,  // "BENEFICIARY", "ANC_VISIT", "BP_VISIT", etc.
    val entity_id: String,  // ID of the record to sync
    
    // Operation type
    val operation: String,  // "CREATE", "UPDATE", "DELETE"
    
    // Serialized JSON payload
    val payload_json: String,  // Full entity data as JSON
    
    // Priority (higher number = higher priority)
    val priority: Int,  // 1-10 (10 = highest, e.g., AUDIT_LOG)
    
    // Sync status
    val sync_status: String,  // "PENDING", "IN_PROGRESS", "SUCCESS", "FAILED"
    
    // Retry logic
    val retry_count: Int = 0,
    val max_retries: Int = 5,
    val last_retry_at: Long? = null,
    val next_retry_at: Long? = null,
    
    // Error tracking
    val last_error_message: String? = null,
    val last_error_code: Int? = null,
    
    // Conflict resolution metadata
    val user_role_id: Int,  // Role of user who made the change (higher role wins)
    val sync_version: Int,  // Entity version for optimistic locking
    
    // Timestamps
    val created_at: Long = System.currentTimeMillis(),
    val updated_at: Long = System.currentTimeMillis(),
    val synced_at: Long? = null
) : Parcelable

/**
 * Priority levels for sync queue
 */
object SyncPriority {
    const val AUDIT_LOG = 10       // Highest priority
    const val BENEFICIARY = 8      // Critical
    const val HEALTH_VISIT = 7     // ANC, BP, Blood Sugar, Vaccination
    const val EDIT_REQUEST = 6     // Workflow items
    const val DUPLICATE_FLAG = 5   // Duplicate resolution
    const val USER_DATA = 4        // User, Role, Area Mapping
    const val VILLAGE_DATA = 3     // Location hierarchy
    const val DEFAULT = 1          // Lowest priority
}
