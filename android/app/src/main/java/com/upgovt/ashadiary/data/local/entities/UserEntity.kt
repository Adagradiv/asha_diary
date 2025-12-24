package com.upgovt.ashadiary.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * USER Entity
 * Stores field staff, supervisors, block/district/state nodal officers
 * Role hierarchy: 1=Field Staff, 2=Supervisor, 3=Block Nodal, 4=District Nodal, 5=State Admin
 */
@Entity(
    tableName = "users",
    indices = [
        Index(value = ["username"], unique = true),
        Index(value = ["phone_number"], unique = true),
        Index(value = ["role_id"]),
        Index(value = ["is_active"])
    ]
)
@Parcelize
data class UserEntity(
    @PrimaryKey
    val id: String,  // UUID generated client-side
    
    val username: String,
    val password_hash: String,  // SHA-256 hashed
    val full_name: String,
    val phone_number: String,
    val role_id: Int,  // 1-5 (see Role entity)
    
    // Offline token for authentication
    val offline_token: String,
    
    // Worker ID for beneficiary range allocation
    val worker_code: String,  // e.g., "A", "B", "C" for Field Staff
    
    val is_active: Boolean = true,
    
    // Timestamps
    val created_at: Long = System.currentTimeMillis(),
    val updated_at: Long = System.currentTimeMillis(),
    
    // Sync metadata
    val is_synced: Boolean = false,
    val last_synced_at: Long? = null,
    val server_id: String? = null  // ID from backend after sync
) : Parcelable
