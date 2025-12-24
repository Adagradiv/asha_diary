package com.upgovt.ashadiary.domain.duplicate

import com.upgovt.ashadiary.data.local.dao.*
import com.upgovt.ashadiary.data.local.entities.*
import com.upgovt.ashadiary.domain.duplicate.DuplicateDetectionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * DuplicateMergeManager - Handles duplicate beneficiary merge operations
 * Only Block Nodal and above can perform merges
 */
class DuplicateMergeManager(
    private val beneficiaryDao: BeneficiaryDao,
    private val duplicateFlagDao: DuplicateFlagDao,
    private val biometricDao: BiometricLocalDao,
    private val ancVisitDao: ANCVisitDao,
    private val bpVisitDao: BPVisitDao,
    private val bloodSugarVisitDao: BloodSugarVisitDao,
    private val vaccinationVisitDao: VaccinationVisitDao,
    private val auditLogDao: AuditLogDao
) {
    
    /**
     * Merge duplicate beneficiary into master
     * - Transfers all visit records to master
     * - Keeps biometric data from master only
     * - Marks duplicate as inactive
     * - Creates audit trail
     */
    suspend fun mergeDuplicateIntoMaster(
        duplicateBeneficiaryId: String,
        masterBeneficiaryId: String,
        userId: String,
        userRoleId: Int,
        notes: String?
    ): Result<MergeResult> = withContext(Dispatchers.IO) {
        try {
            // Validate user role (must be Block Nodal or above)
            if (userRoleId < 3) {
                return@withContext Result.failure(
                    IllegalAccessException("केवल ब्लॉक नोडल या उच्च अधिकारी डुप्लिकेट मर्ज कर सकते हैं")
                )
            }
            
            // Get beneficiaries
            val duplicate = beneficiaryDao.getBeneficiaryById(duplicateBeneficiaryId)
                ?: return@withContext Result.failure(IllegalArgumentException("डुप्लिकेट लाभार्थी नहीं मिला"))
            
            val master = beneficiaryDao.getBeneficiaryById(masterBeneficiaryId)
                ?: return@withContext Result.failure(IllegalArgumentException("मास्टर लाभार्थी नहीं मिला"))
            
            // Transfer visit records
            val transferredVisits = transferVisitRecords(duplicateBeneficiaryId, masterBeneficiaryId)
            
            // Delete duplicate's biometric data (keep master's only)
            biometricDao.deleteByBeneficiary(duplicateBeneficiaryId)
            
            // Mark duplicate as inactive and link to master
            beneficiaryDao.mergeBeneficiaryInto(
                beneficiaryId = duplicateBeneficiaryId,
                masterBeneficiaryId = masterBeneficiaryId,
                modifiedByUserId = userId,
                modifiedByRoleId = userRoleId
            )
            
            // Create audit log
            createMergeAuditLog(
                duplicateId = duplicate.beneficiary_id,
                masterId = master.beneficiary_id,
                userId = userId,
                userRoleId = userRoleId,
                notes = notes,
                transferredVisits = transferredVisits
            )
            
            Result.success(MergeResult(
                masterBeneficiaryId = masterBeneficiaryId,
                duplicateBeneficiaryId = duplicateBeneficiaryId,
                transferredVisits = transferredVisits,
                message = "डुप्लिकेट सफलतापूर्वक मर्ज हो गया"
            ))
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Transfer all visit records from duplicate to master
     */
    private suspend fun transferVisitRecords(
        fromBeneficiaryId: String,
        toBeneficiaryId: String
    ): VisitTransferReport {
        var ancCount = 0
        var bpCount = 0
        var bloodSugarCount = 0
        var vaccinationCount = 0
        
        // Transfer ANC visits
        val ancVisits = ancVisitDao.getByBeneficiary(fromBeneficiaryId)
        ancVisits.forEach { visit ->
            val transferred = visit.copy(
                id = UUID.randomUUID().toString(),
                beneficiary_id = toBeneficiaryId,
                is_synced = false  // Need to re-sync
            )
            ancVisitDao.insert(transferred)
            ancVisitDao.delete(visit.id)
            ancCount++
        }
        
        // Transfer BP visits
        val bpVisits = bpVisitDao.getByBeneficiary(fromBeneficiaryId)
        bpVisits.forEach { visit ->
            val transferred = visit.copy(
                id = UUID.randomUUID().toString(),
                beneficiary_id = toBeneficiaryId,
                is_synced = false
            )
            bpVisitDao.insert(transferred)
            bpVisitDao.delete(visit.id)
            bpCount++
        }
        
        // Transfer Blood Sugar visits
        val bloodSugarVisits = bloodSugarVisitDao.getByBeneficiary(fromBeneficiaryId)
        bloodSugarVisits.forEach { visit ->
            val transferred = visit.copy(
                id = UUID.randomUUID().toString(),
                beneficiary_id = toBeneficiaryId,
                is_synced = false
            )
            bloodSugarVisitDao.insert(transferred)
            bloodSugarVisitDao.delete(visit.id)
            bloodSugarCount++
        }
        
        // Transfer Vaccination visits
        val vaccinationVisits = vaccinationVisitDao.getByBeneficiary(fromBeneficiaryId)
        vaccinationVisits.forEach { visit ->
            val transferred = visit.copy(
                id = UUID.randomUUID().toString(),
                beneficiary_id = toBeneficiaryId,
                is_synced = false
            )
            vaccinationVisitDao.insert(transferred)
            vaccinationVisitDao.delete(visit.id)
            vaccinationCount++
        }
        
        return VisitTransferReport(
            ancVisits = ancCount,
            bpVisits = bpCount,
            bloodSugarVisits = bloodSugarCount,
            vaccinationVisits = vaccinationCount,
            totalVisits = ancCount + bpCount + bloodSugarCount + vaccinationCount
        )
    }
    
    /**
     * Create audit log for merge operation
     */
    private suspend fun createMergeAuditLog(
        duplicateId: String,
        masterId: String,
        userId: String,
        userRoleId: Int,
        notes: String?,
        transferredVisits: VisitTransferReport
    ) {
        val description = buildString {
            append("डुप्लिकेट लाभार्थी मर्ज: ")
            append("$duplicateId → $masterId\n")
            append("${transferredVisits.totalVisits} विज़िट ट्रांसफर की गई")
            if (notes != null) {
                append("\nटिप्पणी: $notes")
            }
        }
        
        val auditLog = AuditLogEntity(
            id = UUID.randomUUID().toString(),
            user_id = userId,
            user_role_id = userRoleId,
            action_type = "MERGE",
            entity_type = "BENEFICIARY",
            entity_id = duplicateId,
            action_description_hindi = description,
            gps_lat = 0.0,  // Not location-specific
            gps_lng = 0.0,
            gps_accuracy_meters = 0f,
            device_id = "",
            app_version = ""
        )
        
        auditLogDao.insert(auditLog)
    }
    
    /**
     * Inactivate duplicate without merging
     * (When beneficiaries are similar but not same person)
     */
    suspend fun inactivateDuplicate(
        beneficiaryId: String,
        userId: String,
        userRoleId: Int,
        reason: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (userRoleId < 3) {
                return@withContext Result.failure(
                    IllegalAccessException("केवल ब्लॉक नोडल या उच्च अधिकारी निष्क्रिय कर सकते हैं")
                )
            }
            
            beneficiaryDao.deactivateBeneficiary(
                beneficiaryId = beneficiaryId,
                modifiedByUserId = userId,
                modifiedByRoleId = userRoleId
            )
            
            // Audit log
            val auditLog = AuditLogEntity(
                id = UUID.randomUUID().toString(),
                user_id = userId,
                user_role_id = userRoleId,
                action_type = "DELETE",
                entity_type = "BENEFICIARY",
                entity_id = beneficiaryId,
                action_description_hindi = "लाभार्थी निष्क्रिय: $reason",
                gps_lat = 0.0,
                gps_lng = 0.0,
                gps_accuracy_meters = 0f,
                device_id = "",
                app_version = ""
            )
            auditLogDao.insert(auditLog)
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Merge result
 */
data class MergeResult(
    val masterBeneficiaryId: String,
    val duplicateBeneficiaryId: String,
    val transferredVisits: VisitTransferReport,
    val message: String
)

/**
 * Visit transfer report
 */
data class VisitTransferReport(
    val ancVisits: Int,
    val bpVisits: Int,
    val bloodSugarVisits: Int,
    val vaccinationVisits: Int,
    val totalVisits: Int
)
