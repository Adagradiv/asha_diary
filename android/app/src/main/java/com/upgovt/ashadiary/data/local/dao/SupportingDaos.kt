package com.upgovt.ashadiary.data.local.dao

import androidx.room.*
import com.upgovt.ashadiary.data.local.entities.*
import kotlinx.coroutines.flow.Flow

/**
 * Supporting DAOs - Role, Village, UserAreaMapping, DuplicateFlag, AuditLog, BiometricLocal, SyncQueue
 */

// ==================== ROLE DAO ====================
@Dao
interface RoleDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(role: RoleEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(roles: List<RoleEntity>): List<Long>
    
    @Query("SELECT * FROM roles WHERE id = :roleId LIMIT 1")
    suspend fun getById(roleId: Int): RoleEntity?
    
    @Query("SELECT * FROM roles ORDER BY authority_level ASC")
    suspend fun getAllRoles(): List<RoleEntity>
    
    @Query("SELECT * FROM roles ORDER BY authority_level ASC")
    fun observeAllRoles(): Flow<List<RoleEntity>>
}

// ==================== VILLAGE DAO ====================
@Dao
interface VillageDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(village: VillageEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(villages: List<VillageEntity>): List<Long>
    
    @Query("SELECT * FROM villages WHERE id = :villageId LIMIT 1")
    suspend fun getById(villageId: String): VillageEntity?
    
    @Query("SELECT * FROM villages WHERE block_code = :blockCode AND is_active = 1 ORDER BY village_name_hindi ASC")
    suspend fun getByBlock(blockCode: String): List<VillageEntity>
    
    @Query("SELECT * FROM villages WHERE district_code = :districtCode AND is_active = 1 ORDER BY village_name_hindi ASC")
    suspend fun getByDistrict(districtCode: String): List<VillageEntity>
    
    @Query("SELECT * FROM villages WHERE is_active = 1 ORDER BY village_name_hindi ASC")
    suspend fun getAllActive(): List<VillageEntity>
    
    @Query("SELECT * FROM villages WHERE is_active = 1 ORDER BY village_name_hindi ASC")
    fun observeAllActive(): Flow<List<VillageEntity>>
    
    @Query("SELECT * FROM villages WHERE village_name_hindi LIKE '%' || :query || '%' AND is_active = 1 LIMIT :limit")
    suspend fun searchVillages(query: String, limit: Int = 50): List<VillageEntity>
    
    @Update
    suspend fun update(village: VillageEntity): Int
    
    @Query("SELECT * FROM villages WHERE is_synced = 0")
    suspend fun getUnsynced(): List<VillageEntity>
}

// ==================== USER AREA MAPPING DAO ====================
@Dao
interface UserAreaMappingDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mapping: UserAreaMappingEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(mappings: List<UserAreaMappingEntity>): List<Long>
    
    @Query("SELECT * FROM user_area_mappings WHERE user_id = :userId AND is_active = 1")
    suspend fun getByUser(userId: String): List<UserAreaMappingEntity>
    
    @Query("SELECT * FROM user_area_mappings WHERE user_id = :userId AND is_active = 1")
    fun observeByUser(userId: String): Flow<List<UserAreaMappingEntity>>
    
    @Query("SELECT * FROM user_area_mappings WHERE village_id = :villageId AND is_active = 1")
    suspend fun getByVillage(villageId: String): List<UserAreaMappingEntity>
    
    @Query("""
        SELECT v.* FROM villages v
        INNER JOIN user_area_mappings uam ON v.id = uam.village_id
        WHERE uam.user_id = :userId AND uam.is_active = 1 AND v.is_active = 1
        ORDER BY v.village_name_hindi ASC
    """)
    suspend fun getAssignedVillagesForUser(userId: String): List<VillageEntity>
    
    @Query("""
        SELECT v.* FROM villages v
        INNER JOIN user_area_mappings uam ON v.id = uam.village_id
        WHERE uam.user_id = :userId AND uam.is_active = 1 AND v.is_active = 1
        ORDER BY v.village_name_hindi ASC
    """)
    fun observeAssignedVillagesForUser(userId: String): Flow<List<VillageEntity>>
    
    @Update
    suspend fun update(mapping: UserAreaMappingEntity): Int
    
    @Query("UPDATE user_area_mappings SET is_active = 0 WHERE id = :mappingId")
    suspend fun deactivate(mappingId: String): Int
    
    @Query("SELECT * FROM user_area_mappings WHERE is_synced = 0")
    suspend fun getUnsynced(): List<UserAreaMappingEntity>
}

// ==================== DUPLICATE FLAG DAO ====================
@Dao
interface DuplicateFlagDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(flag: DuplicateFlagEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(flags: List<DuplicateFlagEntity>): List<Long>
    
    @Query("SELECT * FROM duplicate_flags WHERE id = :flagId LIMIT 1")
    suspend fun getById(flagId: String): DuplicateFlagEntity?
    
    @Query("SELECT * FROM duplicate_flags WHERE status = 'PENDING' ORDER BY similarity_score DESC")
    suspend fun getPendingFlags(): List<DuplicateFlagEntity>
    
    @Query("SELECT * FROM duplicate_flags WHERE status = 'PENDING' ORDER BY similarity_score DESC")
    fun observePendingFlags(): Flow<List<DuplicateFlagEntity>>
    
    @Query("""
        SELECT * FROM duplicate_flags 
        WHERE (beneficiary_id_1 = :beneficiaryId OR beneficiary_id_2 = :beneficiaryId)
        ORDER BY flagged_at DESC
    """)
    suspend fun getByBeneficiary(beneficiaryId: String): List<DuplicateFlagEntity>
    
    @Query("""
        UPDATE duplicate_flags 
        SET status = :status,
            resolution_action = :action,
            resolution_notes_hindi = :notes,
            resolved_by_user_id = :resolvedByUserId,
            resolved_at = :resolvedAt,
            master_beneficiary_id = :masterBeneficiaryId,
            updated_at = :resolvedAt
        WHERE id = :flagId
    """)
    suspend fun resolveFlag(
        flagId: String,
        status: String,
        action: String,
        notes: String?,
        resolvedByUserId: String,
        resolvedAt: Long = System.currentTimeMillis(),
        masterBeneficiaryId: String? = null
    ): Int
    
    @Update
    suspend fun update(flag: DuplicateFlagEntity): Int
    
    @Query("SELECT * FROM duplicate_flags WHERE is_synced = 0")
    suspend fun getUnsynced(): List<DuplicateFlagEntity>
    
    @Query("SELECT COUNT(*) FROM duplicate_flags WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>
}

// ==================== AUDIT LOG DAO ====================
@Dao
interface AuditLogDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: AuditLogEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<AuditLogEntity>): List<Long>
    
    @Query("SELECT * FROM audit_logs WHERE entity_type = :entityType AND entity_id = :entityId ORDER BY action_timestamp DESC")
    suspend fun getByEntity(entityType: String, entityId: String): List<AuditLogEntity>
    
    @Query("SELECT * FROM audit_logs WHERE user_id = :userId ORDER BY action_timestamp DESC LIMIT :limit")
    suspend fun getByUser(userId: String, limit: Int = 100): List<AuditLogEntity>
    
    @Query("SELECT * FROM audit_logs ORDER BY action_timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 100): List<AuditLogEntity>
    
    @Query("SELECT * FROM audit_logs ORDER BY action_timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<AuditLogEntity>>
    
    @Query("SELECT * FROM audit_logs WHERE is_synced = 0 ORDER BY action_timestamp ASC")
    suspend fun getUnsynced(): List<AuditLogEntity>
    
    @Query("SELECT COUNT(*) FROM audit_logs WHERE is_synced = 0")
    fun observeUnsyncedCount(): Flow<Int>
}

// ==================== BIOMETRIC LOCAL DAO ====================
@Dao
interface BiometricLocalDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(biometric: BiometricLocalEntity): Long
    
    @Query("SELECT * FROM biometrics_local WHERE beneficiary_id = :beneficiaryId")
    suspend fun getByBeneficiary(beneficiaryId: String): List<BiometricLocalEntity>
    
    @Query("SELECT * FROM biometrics_local WHERE beneficiary_id = :beneficiaryId AND finger_position = :fingerPosition LIMIT 1")
    suspend fun getByBeneficiaryAndFinger(beneficiaryId: String, fingerPosition: String): BiometricLocalEntity?
    
    @Query("SELECT * FROM biometrics_local")
    suspend fun getAllBiometrics(): List<BiometricLocalEntity>
    
    @Query("DELETE FROM biometrics_local WHERE beneficiary_id = :beneficiaryId")
    suspend fun deleteByBeneficiary(beneficiaryId: String): Int
    
    @Query("DELETE FROM biometrics_local WHERE id = :id")
    suspend fun delete(id: String): Int
}

// ==================== SYNC QUEUE DAO ====================
@Dao
interface SyncQueueDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: SyncQueueEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SyncQueueEntity>): List<Long>
    
    @Query("SELECT * FROM sync_queue WHERE sync_status = 'PENDING' ORDER BY priority DESC, created_at ASC LIMIT :limit")
    suspend fun getPendingItems(limit: Int = 50): List<SyncQueueEntity>
    
    @Query("SELECT * FROM sync_queue WHERE sync_status = 'FAILED' AND retry_count < max_retries ORDER BY next_retry_at ASC LIMIT :limit")
    suspend fun getRetryableItems(limit: Int = 50): List<SyncQueueEntity>
    
    @Query("SELECT * FROM sync_queue WHERE entity_type = :entityType AND entity_id = :entityId LIMIT 1")
    suspend fun getByEntity(entityType: String, entityId: String): SyncQueueEntity?
    
    @Query("""
        UPDATE sync_queue 
        SET sync_status = 'SUCCESS',
            synced_at = :syncedAt,
            updated_at = :syncedAt
        WHERE id = :queueId
    """)
    suspend fun markAsSuccess(queueId: String, syncedAt: Long = System.currentTimeMillis()): Int
    
    @Query("""
        UPDATE sync_queue 
        SET sync_status = 'FAILED',
            retry_count = retry_count + 1,
            last_retry_at = :attemptedAt,
            next_retry_at = :nextRetryAt,
            last_error_message = :errorMessage,
            last_error_code = :errorCode,
            updated_at = :attemptedAt
        WHERE id = :queueId
    """)
    suspend fun markAsFailed(
        queueId: String,
        errorMessage: String,
        errorCode: Int?,
        attemptedAt: Long = System.currentTimeMillis(),
        nextRetryAt: Long = System.currentTimeMillis() + 300000 // 5 minutes
    ): Int
    
    @Update
    suspend fun update(item: SyncQueueEntity): Int
    
    @Query("DELETE FROM sync_queue WHERE sync_status = 'SUCCESS' AND synced_at < :olderThan")
    suspend fun deleteOldSuccessful(olderThan: Long): Int
    
    @Query("SELECT COUNT(*) FROM sync_queue WHERE sync_status = 'PENDING' OR sync_status = 'FAILED'")
    suspend fun getPendingCount(): Int
    
    @Query("SELECT COUNT(*) FROM sync_queue WHERE sync_status = 'PENDING' OR sync_status = 'FAILED'")
    fun observePendingCount(): Flow<Int>
}
