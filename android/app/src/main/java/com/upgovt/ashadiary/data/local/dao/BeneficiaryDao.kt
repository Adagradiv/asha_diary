package com.upgovt.ashadiary.data.local.dao

import androidx.room.*
import com.upgovt.ashadiary.data.local.entities.BeneficiaryEntity
import kotlinx.coroutines.flow.Flow

/**
 * BeneficiaryDao - Core DAO for beneficiary management
 * Includes duplicate detection queries
 */
@Dao
interface BeneficiaryDao {
    
    // CREATE
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBeneficiary(beneficiary: BeneficiaryEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBeneficiaries(beneficiaries: List<BeneficiaryEntity>): List<Long>
    
    // READ
    @Query("SELECT * FROM beneficiaries WHERE id = :beneficiaryId LIMIT 1")
    suspend fun getBeneficiaryById(beneficiaryId: String): BeneficiaryEntity?
    
    @Query("SELECT * FROM beneficiaries WHERE id = :beneficiaryId LIMIT 1")
    fun observeBeneficiaryById(beneficiaryId: String): Flow<BeneficiaryEntity?>
    
    @Query("SELECT * FROM beneficiaries WHERE beneficiary_id = :beneficiaryId LIMIT 1")
    suspend fun getBeneficiaryByUniqueId(beneficiaryId: String): BeneficiaryEntity?
    
    @Query("SELECT * FROM beneficiaries WHERE village_id = :villageId AND is_active = 1 ORDER BY name_hindi ASC")
    suspend fun getBeneficiariesByVillage(villageId: String): List<BeneficiaryEntity>
    
    @Query("SELECT * FROM beneficiaries WHERE village_id = :villageId AND is_active = 1 ORDER BY name_hindi ASC")
    fun observeBeneficiariesByVillage(villageId: String): Flow<List<BeneficiaryEntity>>
    
    @Query("SELECT * FROM beneficiaries WHERE registered_by_user_id = :userId AND is_active = 1 ORDER BY registration_date DESC")
    suspend fun getBeneficiariesByUser(userId: String): List<BeneficiaryEntity>
    
    @Query("SELECT * FROM beneficiaries WHERE registered_by_user_id = :userId AND is_active = 1 ORDER BY registration_date DESC")
    fun observeBeneficiariesByUser(userId: String): Flow<List<BeneficiaryEntity>>
    
    @Query("SELECT * FROM beneficiaries WHERE is_active = 1 ORDER BY name_hindi ASC LIMIT :limit OFFSET :offset")
    suspend fun getBeneficiariesPaginated(limit: Int, offset: Int): List<BeneficiaryEntity>
    
    // SEARCH
    @Query("""
        SELECT * FROM beneficiaries 
        WHERE is_active = 1 
        AND (
            name_hindi LIKE '%' || :query || '%' 
            OR beneficiary_id LIKE '%' || :query || '%'
            OR mobile_number LIKE '%' || :query || '%'
        )
        ORDER BY name_hindi ASC
        LIMIT :limit
    """)
    suspend fun searchBeneficiaries(query: String, limit: Int = 50): List<BeneficiaryEntity>
    
    @Query("""
        SELECT * FROM beneficiaries 
        WHERE is_active = 1 
        AND (
            name_hindi LIKE '%' || :query || '%' 
            OR beneficiary_id LIKE '%' || :query || '%'
            OR mobile_number LIKE '%' || :query || '%'
        )
        ORDER BY name_hindi ASC
        LIMIT :limit
    """)
    fun observeSearchBeneficiaries(query: String, limit: Int = 50): Flow<List<BeneficiaryEntity>>
    
    // DUPLICATE DETECTION
    @Query("""
        SELECT * FROM beneficiaries 
        WHERE village_id = :villageId 
        AND gender = :gender 
        AND is_active = 1
        AND ABS(age_years - :ageYears) <= 2
        AND id != :excludeId
    """)
    suspend fun findPotentialDuplicates(
        villageId: String,
        gender: String,
        ageYears: Int,
        excludeId: String
    ): List<BeneficiaryEntity>
    
    @Query("""
        SELECT * FROM beneficiaries 
        WHERE name_hindi = :nameHindi 
        AND village_id = :villageId 
        AND gender = :gender 
        AND is_active = 1
        AND id != :excludeId
    """)
    suspend fun findExactNameMatch(
        nameHindi: String,
        villageId: String,
        gender: String,
        excludeId: String
    ): List<BeneficiaryEntity>
    
    @Query("SELECT * FROM beneficiaries WHERE is_duplicate_flagged = 1 AND is_active = 1")
    suspend fun getDuplicateFlaggedBeneficiaries(): List<BeneficiaryEntity>
    
    @Query("SELECT * FROM beneficiaries WHERE is_duplicate_flagged = 1 AND is_active = 1")
    fun observeDuplicateFlaggedBeneficiaries(): Flow<List<BeneficiaryEntity>>
    
    // UPDATE
    @Update
    suspend fun updateBeneficiary(beneficiary: BeneficiaryEntity): Int
    
    @Query("""
        UPDATE beneficiaries 
        SET is_active = 0, 
            updated_at = :timestamp,
            last_modified_by_user_id = :modifiedByUserId,
            last_modified_by_role_id = :modifiedByRoleId,
            sync_version = sync_version + 1
        WHERE id = :beneficiaryId
    """)
    suspend fun deactivateBeneficiary(
        beneficiaryId: String,
        modifiedByUserId: String,
        modifiedByRoleId: Int,
        timestamp: Long = System.currentTimeMillis()
    ): Int
    
    @Query("""
        UPDATE beneficiaries 
        SET is_duplicate_flagged = :isFlagged,
            updated_at = :timestamp,
            sync_version = sync_version + 1
        WHERE id = :beneficiaryId
    """)
    suspend fun setDuplicateFlagged(
        beneficiaryId: String,
        isFlagged: Boolean,
        timestamp: Long = System.currentTimeMillis()
    ): Int
    
    @Query("""
        UPDATE beneficiaries 
        SET is_active = 0,
            duplicate_of_beneficiary_id = :masterBeneficiaryId,
            updated_at = :timestamp,
            last_modified_by_user_id = :modifiedByUserId,
            last_modified_by_role_id = :modifiedByRoleId,
            sync_version = sync_version + 1
        WHERE id = :beneficiaryId
    """)
    suspend fun mergeBeneficiaryInto(
        beneficiaryId: String,
        masterBeneficiaryId: String,
        modifiedByUserId: String,
        modifiedByRoleId: Int,
        timestamp: Long = System.currentTimeMillis()
    ): Int
    
    @Query("""
        UPDATE beneficiaries 
        SET is_synced = :isSynced, 
            last_synced_at = :syncedAt, 
            server_id = :serverId 
        WHERE id = :beneficiaryId
    """)
    suspend fun updateSyncStatus(
        beneficiaryId: String,
        isSynced: Boolean,
        syncedAt: Long,
        serverId: String?
    ): Int
    
    // DELETE (Hard delete - use with caution)
    @Query("DELETE FROM beneficiaries WHERE id = :beneficiaryId")
    suspend fun deleteBeneficiary(beneficiaryId: String): Int
    
    // SYNC-RELATED
    @Query("SELECT * FROM beneficiaries WHERE is_synced = 0 ORDER BY created_at ASC")
    suspend fun getUnsyncedBeneficiaries(): List<BeneficiaryEntity>
    
    @Query("SELECT COUNT(*) FROM beneficiaries WHERE is_synced = 0")
    suspend fun getUnsyncedCount(): Int
    
    @Query("SELECT COUNT(*) FROM beneficiaries WHERE is_synced = 0")
    fun observeUnsyncedCount(): Flow<Int>
    
    // STATISTICS
    @Query("SELECT COUNT(*) FROM beneficiaries WHERE is_active = 1")
    suspend fun getTotalActiveBeneficiaries(): Int
    
    @Query("SELECT COUNT(*) FROM beneficiaries WHERE is_active = 1")
    fun observeTotalActiveBeneficiaries(): Flow<Int>
    
    @Query("SELECT COUNT(*) FROM beneficiaries WHERE village_id = :villageId AND is_active = 1")
    suspend fun getCountByVillage(villageId: String): Int
    
    @Query("SELECT COUNT(*) FROM beneficiaries WHERE beneficiary_type = :type AND is_active = 1")
    suspend fun getCountByType(type: String): Int
}
