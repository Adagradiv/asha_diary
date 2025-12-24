package com.upgovt.ashadiary.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * ROLE Entity
 * Fixed hierarchy for government health workers
 */
@Entity(tableName = "roles")
@Parcelize
data class RoleEntity(
    @PrimaryKey
    val id: Int,
    
    val name_hindi: String,  // e.g., "फील्ड स्टाफ", "सुपरवाइजर"
    val name_english: String,  // For backend only
    
    val authority_level: Int,  // 1-5, higher number = higher authority
    
    val can_edit_beneficiary: Boolean,  // Can directly edit beneficiary data
    val can_approve_edits: Boolean,     // Can approve edit requests
    val can_merge_duplicates: Boolean,  // Can merge duplicate beneficiaries
    
    val created_at: Long = System.currentTimeMillis()
) : Parcelable

/**
 * Pre-populated roles (insert on first app launch)
 */
object RoleDefaults {
    val FIELD_STAFF = RoleEntity(
        id = 1,
        name_hindi = "फील्ड स्टाफ",
        name_english = "Field Staff",
        authority_level = 1,
        can_edit_beneficiary = false,
        can_approve_edits = false,
        can_merge_duplicates = false
    )
    
    val SUPERVISOR = RoleEntity(
        id = 2,
        name_hindi = "सुपरवाइजर",
        name_english = "Supervisor",
        authority_level = 2,
        can_edit_beneficiary = true,
        can_approve_edits = true,
        can_merge_duplicates = false
    )
    
    val BLOCK_NODAL = RoleEntity(
        id = 3,
        name_hindi = "ब्लॉक नोडल",
        name_english = "Block Nodal",
        authority_level = 3,
        can_edit_beneficiary = true,
        can_approve_edits = true,
        can_merge_duplicates = true
    )
    
    val DISTRICT_NODAL = RoleEntity(
        id = 4,
        name_hindi = "जिला नोडल",
        name_english = "District Nodal",
        authority_level = 4,
        can_edit_beneficiary = true,
        can_approve_edits = true,
        can_merge_duplicates = true
    )
    
    val STATE_ADMIN = RoleEntity(
        id = 5,
        name_hindi = "राज्य प्रशासक",
        name_english = "State Admin",
        authority_level = 5,
        can_edit_beneficiary = true,
        can_approve_edits = true,
        can_merge_duplicates = true
    )
    
    fun getAll() = listOf(
        FIELD_STAFF,
        SUPERVISOR,
        BLOCK_NODAL,
        DISTRICT_NODAL,
        STATE_ADMIN
    )
}
