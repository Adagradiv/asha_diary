package com.upgovt.ashadiary.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * BENEFICIARY Entity
 * Core entity for tracking pregnant women and children (25 lakh beneficiaries)
 * Beneficiary ID Format: SS-DDD-BBB-VVVV-W-NNNN
 * Example: 09-146-137-4462-A-0001
 */
@Entity(
    tableName = "beneficiaries",
    foreignKeys = [
        ForeignKey(
            entity = VillageEntity::class,
            parentColumns = ["id"],
            childColumns = ["village_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["registered_by_user_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["beneficiary_id"], unique = true),
        Index(value = ["village_id"]),
        Index(value = ["registered_by_user_id"]),
        Index(value = ["name_hindi", "village_id"]),  // For duplicate detection
        Index(value = ["is_active"]),
        Index(value = ["is_duplicate_flagged"])
    ]
)
@Parcelize
data class BeneficiaryEntity(
    @PrimaryKey
    val id: String,  // UUID (local primary key)
    
    // Unique Beneficiary ID (NEVER changes after generation)
    // Format: SS-DDD-BBB-VVVV-W-NNNN
    val beneficiary_id: String,
    
    // Personal Information
    val name_hindi: String,
    val father_husband_name_hindi: String,
    
    val gender: String,  // "M", "F", "O" (Male/Female/Other)
    val date_of_birth: Long,  // Timestamp
    val age_years: Int,
    
    // Category
    val beneficiary_type: String,  // "PREGNANT_WOMAN", "CHILD", "BOTH"
    
    // Address
    val village_id: String,  // FK to villages
    val address_hindi: String,
    
    // Contact
    val mobile_number: String?,
    
    // Registration metadata
    val registered_by_user_id: String,  // FK to users
    val registration_date: Long = System.currentTimeMillis(),
    val registration_gps_lat: Double,
    val registration_gps_lng: Double,
    
    // Status flags
    val is_active: Boolean = true,
    val is_duplicate_flagged: Boolean = false,
    val duplicate_of_beneficiary_id: String? = null,  // If merged, points to master
    
    // Timestamps
    val created_at: Long = System.currentTimeMillis(),
    val updated_at: Long = System.currentTimeMillis(),
    val last_modified_by_user_id: String,
    
    // Sync metadata
    val is_synced: Boolean = false,
    val last_synced_at: Long? = null,
    val server_id: String? = null,
    
    // Conflict resolution
    val last_modified_by_role_id: Int,  // For conflict resolution (higher role wins)
    val sync_version: Int = 1  // Incremented on each update
) : Parcelable
