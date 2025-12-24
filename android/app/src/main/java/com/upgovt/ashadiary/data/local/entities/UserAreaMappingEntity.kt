package com.upgovt.ashadiary.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * USER_AREA_MAPPING Entity
 * Maps field workers and supervisors to their assigned villages/blocks/districts
 */
@Entity(
    tableName = "user_area_mappings",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = VillageEntity::class,
            parentColumns = ["id"],
            childColumns = ["village_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["village_id"]),
        Index(value = ["user_id", "village_id"], unique = true)
    ]
)
@Parcelize
data class UserAreaMappingEntity(
    @PrimaryKey
    val id: String,  // UUID
    
    val user_id: String,     // Reference to users table
    val village_id: String,  // Reference to villages table
    
    val assigned_by_user_id: String,  // Who assigned this mapping
    
    val is_active: Boolean = true,
    
    val assigned_at: Long = System.currentTimeMillis(),
    val updated_at: Long = System.currentTimeMillis(),
    
    // Sync metadata
    val is_synced: Boolean = false,
    val last_synced_at: Long? = null,
    val server_id: String? = null
) : Parcelable
