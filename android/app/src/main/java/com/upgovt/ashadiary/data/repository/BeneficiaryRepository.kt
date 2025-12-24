package com.upgovt.ashadiary.data.repository

import com.upgovt.ashadiary.data.local.dao.*
import com.upgovt.ashadiary.data.local.entities.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * BeneficiaryRepository - Repository pattern for beneficiary data
 * Handles offline-first logic, duplicate detection, and sync queueing
 */
class BeneficiaryRepository(
    private val beneficiaryDao: BeneficiaryDao,
    private val duplicateFlagDao: DuplicateFlagDao,
    private val syncQueueDao: SyncQueueDao,
    private val auditLogDao: AuditLogDao
) {
    
    // ==================== CREATE ====================
    
    /**
     * Register new beneficiary with duplicate detection
     * Returns Pair<BeneficiaryEntity, List<DuplicateFlagEntity>>
     */
    suspend fun registerBeneficiary(
        beneficiary: BeneficiaryEntity,
        userId: String,
        userRoleId: Int
    ): Pair<BeneficiaryEntity, List<DuplicateFlagEntity>> = withContext(Dispatchers.IO) {
        
        // 1. Check for potential duplicates
        val potentialDuplicates = beneficiaryDao.findPotentialDuplicates(
            villageId = beneficiary.village_id,
            gender = beneficiary.gender,
            ageYears = beneficiary.age_years,
            excludeId = beneficiary.id
        )
        
        val exactNameMatches = beneficiaryDao.findExactNameMatch(
            nameHindi = beneficiary.name_hindi,
            villageId = beneficiary.village_id,
            gender = beneficiary.gender,
            excludeId = beneficiary.id
        )
        
        // 2. Insert beneficiary
        beneficiaryDao.insertBeneficiary(beneficiary)
        
        // 3. Create duplicate flags if matches found
        val duplicateFlags = mutableListOf<DuplicateFlagEntity>()
        
        exactNameMatches.forEach { existing ->
            val flag = DuplicateFlagEntity(
                id = UUID.randomUUID().toString(),
                beneficiary_id_1 = existing.id,
                beneficiary_id_2 = beneficiary.id,
                similarity_score = 95, // High score for exact name match
                name_match = true,
                village_match = true,
                gender_match = true,
                age_within_range = kotlin.math.abs(existing.age_years - beneficiary.age_years) <= 2,
                status = "PENDING",
                flagged_by_user_id = userId,
                flag_source = "AUTO_DETECTION"
            )
            
            duplicateFlagDao.insert(flag)
            duplicateFlags.add(flag)
            
            // Mark both beneficiaries as flagged
            beneficiaryDao.setDuplicateFlagged(existing.id, true)
            beneficiaryDao.setDuplicateFlagged(beneficiary.id, true)
        }
        
        // 4. Add to sync queue
        addToSyncQueue(beneficiary, "CREATE", userRoleId)
        
        // 5. Create audit log
        logAudit(
            userId = userId,
            userRoleId = userRoleId,
            actionType = "CREATE",
            entityType = "BENEFICIARY",
            entityId = beneficiary.id,
            description = "नया लाभार्थी पंजीकृत: ${beneficiary.name_hindi}",
            gpsLat = beneficiary.registration_gps_lat,
            gpsLng = beneficiary.registration_gps_lng
        )
        
        Pair(beneficiary, duplicateFlags)
    }
    
    // ==================== READ ====================
    
    fun getBeneficiaryById(beneficiaryId: String): Flow<BeneficiaryEntity?> {
        return beneficiaryDao.observeBeneficiaryById(beneficiaryId)
    }
    
    fun getBeneficiariesByVillage(villageId: String): Flow<List<BeneficiaryEntity>> {
        return beneficiaryDao.observeBeneficiariesByVillage(villageId)
    }
    
    fun searchBeneficiaries(query: String, limit: Int = 50): Flow<List<BeneficiaryEntity>> {
        return beneficiaryDao.observeSearchBeneficiaries(query, limit)
    }
    
    suspend fun getBeneficiariesPaginated(limit: Int, offset: Int): List<BeneficiaryEntity> {
        return withContext(Dispatchers.IO) {
            beneficiaryDao.getBeneficiariesPaginated(limit, offset)
        }
    }
    
    // ==================== UPDATE ====================
    
    /**
     * Update beneficiary (only allowed for Supervisor and above)
     */
    suspend fun updateBeneficiary(
        beneficiary: BeneficiaryEntity,
        userId: String,
        userRoleId: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            beneficiaryDao.updateBeneficiary(beneficiary)
            
            // Add to sync queue
            addToSyncQueue(beneficiary, "UPDATE", userRoleId)
            
            // Audit log
            logAudit(
                userId = userId,
                userRoleId = userRoleId,
                actionType = "UPDATE",
                entityType = "BENEFICIARY",
                entityId = beneficiary.id,
                description = "लाभार्थी अपडेट: ${beneficiary.name_hindi}",
                gpsLat = beneficiary.registration_gps_lat,
                gpsLng = beneficiary.registration_gps_lng
            )
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Merge duplicate beneficiary
     * Only Block Nodal can perform this
     */
    suspend fun mergeBeneficiaryInto(
        duplicateBeneficiaryId: String,
        masterBeneficiaryId: String,
        userId: String,
        userRoleId: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            beneficiaryDao.mergeBeneficiaryInto(
                beneficiaryId = duplicateBeneficiaryId,
                masterBeneficiaryId = masterBeneficiaryId,
                modifiedByUserId = userId,
                modifiedByRoleId = userRoleId
            )
            
            // Audit log
            logAudit(
                userId = userId,
                userRoleId = userRoleId,
                actionType = "MERGE",
                entityType = "BENEFICIARY",
                entityId = duplicateBeneficiaryId,
                description = "डुप्लिकेट लाभार्थी मर्ज किया गया",
                gpsLat = 0.0,
                gpsLng = 0.0
            )
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ==================== STATISTICS ====================
    
    fun getTotalActiveBeneficiaries(): Flow<Int> {
        return beneficiaryDao.observeTotalActiveBeneficiaries()
    }
    
    fun getDuplicateFlaggedBeneficiaries(): Flow<List<BeneficiaryEntity>> {
        return beneficiaryDao.observeDuplicateFlaggedBeneficiaries()
    }
    
    // ==================== HELPER FUNCTIONS ====================
    
    private suspend fun addToSyncQueue(
        beneficiary: BeneficiaryEntity,
        operation: String,
        userRoleId: Int
    ) {
        val syncItem = SyncQueueEntity(
            id = UUID.randomUUID().toString(),
            entity_type = "BENEFICIARY",
            entity_id = beneficiary.id,
            operation = operation,
            payload_json = beneficiaryToJson(beneficiary),
            priority = SyncPriority.BENEFICIARY,
            sync_status = "PENDING",
            user_role_id = userRoleId,
            sync_version = beneficiary.sync_version
        )
        
        syncQueueDao.insert(syncItem)
    }
    
    private suspend fun logAudit(
        userId: String,
        userRoleId: Int,
        actionType: String,
        entityType: String,
        entityId: String,
        description: String,
        gpsLat: Double,
        gpsLng: Double
    ) {
        val auditLog = AuditLogEntity(
            id = UUID.randomUUID().toString(),
            user_id = userId,
            user_role_id = userRoleId,
            action_type = actionType,
            entity_type = entityType,
            entity_id = entityId,
            action_description_hindi = description,
            gps_lat = gpsLat,
            gps_lng = gpsLng,
            gps_accuracy_meters = 0f,
            device_id = "",  // To be populated from device info
            app_version = ""  // To be populated from app info
        )
        
        auditLogDao.insert(auditLog)
        
        // Add audit log to sync queue (highest priority)
        val syncItem = SyncQueueEntity(
            id = UUID.randomUUID().toString(),
            entity_type = "AUDIT_LOG",
            entity_id = auditLog.id,
            operation = "CREATE",
            payload_json = "",  // To be serialized
            priority = SyncPriority.AUDIT_LOG,
            sync_status = "PENDING",
            user_role_id = userRoleId,
            sync_version = 1
        )
        
        syncQueueDao.insert(syncItem)
    }
    
    private fun beneficiaryToJson(beneficiary: BeneficiaryEntity): String {
        // TODO: Implement JSON serialization using Gson
        return ""
    }
}
