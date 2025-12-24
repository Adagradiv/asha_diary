package com.upgovt.ashadiary.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * VILLAGE Entity
 * Geographic hierarchy for Uttar Pradesh - Agra District
 */
@Entity(
    tableName = "villages",
    indices = [
        Index(value = ["state_code", "district_code", "block_code", "village_code"], unique = true),
        Index(value = ["block_code"])
    ]
)
@Parcelize
data class VillageEntity(
    @PrimaryKey
    val id: String,  // UUID
    
    // Hierarchical codes (for beneficiary ID generation)
    val state_code: String,    // SS: "09" for Uttar Pradesh
    val district_code: String, // DDD: "146" for Agra
    val block_code: String,    // BBB: e.g., "137"
    val village_code: String,  // VVVV: e.g., "4462"
    
    // Name in Hindi
    val village_name_hindi: String,
    val block_name_hindi: String,
    val district_name_hindi: String,
    val state_name_hindi: String,
    
    // Name in English (for backend only)
    val village_name_english: String,
    val block_name_english: String,
    val district_name_english: String,
    val state_name_english: String,
    
    val is_active: Boolean = true,
    
    val created_at: Long = System.currentTimeMillis(),
    val updated_at: Long = System.currentTimeMillis(),
    
    // Sync metadata
    val is_synced: Boolean = false,
    val last_synced_at: Long? = null,
    val server_id: String? = null
) : Parcelable
