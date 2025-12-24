package com.upgovt.ashadiary.domain.editrequest

import com.upgovt.ashadiary.data.local.dao.EditRequestDao
import com.upgovt.ashadiary.data.local.dao.BeneficiaryDao
import com.upgovt.ashadiary.data.local.dao.AuditLogDao
import com.upgovt.ashadiary.data.local.entities.EditRequestEntity
import com.upgovt.ashadiary.data.local.entities.BeneficiaryEntity
import com.upgovt.ashadiary.data.local.entities.AuditLogEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * EditRequestWorkflowManager - Manages edit request workflow
 * 
 * Workflow: Field Staff → Supervisor → Block Nodal
 * 
 * Rules:
 * - Field Staff: Can only submit requests, cannot edit directly
 * - Supervisor: Can approve/reject requests
 * - Block Nodal: Final authority, can approve/reject
 */
class EditRequestWorkflowManager(
    private val editRequestDao: EditRequestDao,
    private val beneficiaryDao: BeneficiaryDao,
    private val auditLogDao: AuditLogDao
) {
    
    /**
     * Submit edit request (Field Staff only)
     */
    suspend fun submitEditRequest(
        beneficiaryId: String,
        fieldName: String,
        currentValue: String,
        requestedValue: String,
        reason: String,
        requestedByUserId: String,
        requestedByRoleId: Int,
        gpsLat: Double,
        gpsLng: Double
    ): Result<EditRequestEntity> = withContext(Dispatchers.IO) {
        try {
            // Validate user can submit (typically Field Staff)
            if (requestedByRoleId > 2) {
                return@withContext Result.failure(
                    IllegalAccessException("उच्च अधिकारी सीधे संपादित कर सकते हैं, अनुरोध की आवश्यकता नहीं")
                )
            }
            
            // Validate beneficiary exists
            val beneficiary = beneficiaryDao.getBeneficiaryById(beneficiaryId)
                ?: return@withContext Result.failure(IllegalArgumentException("लाभार्थी नहीं मिला"))
            
            // Create edit request
            val editRequest = EditRequestEntity(
                id = UUID.randomUUID().toString(),
                beneficiary_id = beneficiaryId,
                field_name = fieldName,
                current_value = currentValue,
                requested_value = requestedValue,
                reason_hindi = reason,
                status = "PENDING",
                requested_by_user_id = requestedByUserId,
                request_gps_lat = gpsLat,
                request_gps_lng = gpsLng
            )
            
            editRequestDao.insert(editRequest)
            
            // Audit log
            createAuditLog(
                userId = requestedByUserId,
                userRoleId = requestedByRoleId,
                action = "CREATE",
                beneficiaryId = beneficiaryId,
                description = "संपादन अनुरोध सबमिट: $fieldName"
            )
            
            Result.success(editRequest)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Approve edit request (Supervisor or Block Nodal)
     */
    suspend fun approveEditRequest(
        requestId: String,
        reviewedByUserId: String,
        reviewedByRoleId: Int,
        reviewNotes: String? = null
    ): Result<BeneficiaryEntity> = withContext(Dispatchers.IO) {
        try {
            // Validate user can approve (Supervisor or above)
            if (reviewedByRoleId < 2) {
                return@withContext Result.failure(
                    IllegalAccessException("केवल सुपरवाइजर या उच्च अधिकारी स्वीकृत कर सकते हैं")
                )
            }
            
            // Get edit request
            val editRequest = editRequestDao.getById(requestId)
                ?: return@withContext Result.failure(IllegalArgumentException("अनुरोध नहीं मिला"))
            
            if (editRequest.status != "PENDING") {
                return@withContext Result.failure(
                    IllegalStateException("अनुरोध पहले से ${editRequest.status} है")
                )
            }
            
            // Get beneficiary
            val beneficiary = beneficiaryDao.getBeneficiaryById(editRequest.beneficiary_id)
                ?: return@withContext Result.failure(IllegalArgumentException("लाभार्थी नहीं मिला"))
            
            // Apply the edit
            val updatedBeneficiary = applyEdit(beneficiary, editRequest, reviewedByUserId, reviewedByRoleId)
            beneficiaryDao.updateBeneficiary(updatedBeneficiary)
            
            // Mark request as approved
            editRequestDao.approveRequest(
                requestId = requestId,
                reviewedByUserId = reviewedByUserId,
                reviewNotes = reviewNotes
            )
            
            // Audit log
            createAuditLog(
                userId = reviewedByUserId,
                userRoleId = reviewedByRoleId,
                action = "APPROVE",
                beneficiaryId = beneficiary.id,
                description = "संपादन अनुरोध स्वीकृत: ${editRequest.field_name} अपडेट किया गया"
            )
            
            Result.success(updatedBeneficiary)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Reject edit request (Supervisor or Block Nodal)
     */
    suspend fun rejectEditRequest(
        requestId: String,
        reviewedByUserId: String,
        reviewedByRoleId: Int,
        reviewNotes: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Validate user can reject
            if (reviewedByRoleId < 2) {
                return@withContext Result.failure(
                    IllegalAccessException("केवल सुपरवाइजर या उच्च अधिकारी अस्वीकार कर सकते हैं")
                )
            }
            
            // Get edit request
            val editRequest = editRequestDao.getById(requestId)
                ?: return@withContext Result.failure(IllegalArgumentException("अनुरोध नहीं मिला"))
            
            if (editRequest.status != "PENDING") {
                return@withContext Result.failure(
                    IllegalStateException("अनुरोध पहले से ${editRequest.status} है")
                )
            }
            
            // Mark as rejected
            editRequestDao.rejectRequest(
                requestId = requestId,
                reviewedByUserId = reviewedByUserId,
                reviewNotes = reviewNotes
            )
            
            // Audit log
            createAuditLog(
                userId = reviewedByUserId,
                userRoleId = reviewedByRoleId,
                action = "REJECT",
                beneficiaryId = editRequest.beneficiary_id,
                description = "संपादन अनुरोध अस्वीकृत: ${editRequest.field_name}"
            )
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get pending requests for review (for Supervisor/Block Nodal)
     */
    fun getPendingRequests(): Flow<List<EditRequestEntity>> {
        return editRequestDao.observePendingRequests()
    }
    
    /**
     * Get requests submitted by user (for Field Staff to track their requests)
     */
    fun getRequestsByUser(userId: String): Flow<List<EditRequestEntity>> {
        return editRequestDao.observeByRequestedUser(userId)
    }
    
    /**
     * Get requests for a specific beneficiary
     */
    fun getRequestsForBeneficiary(beneficiaryId: String): Flow<List<EditRequestEntity>> {
        return editRequestDao.observeByBeneficiary(beneficiaryId)
    }
    
    /**
     * Apply edit to beneficiary
     */
    private fun applyEdit(
        beneficiary: BeneficiaryEntity,
        editRequest: EditRequestEntity,
        modifiedByUserId: String,
        modifiedByRoleId: Int
    ): BeneficiaryEntity {
        return when (editRequest.field_name) {
            "name_hindi" -> beneficiary.copy(
                name_hindi = editRequest.requested_value,
                last_modified_by_user_id = modifiedByUserId,
                last_modified_by_role_id = modifiedByRoleId,
                updated_at = System.currentTimeMillis(),
                sync_version = beneficiary.sync_version + 1,
                is_synced = false
            )
            "father_husband_name_hindi" -> beneficiary.copy(
                father_husband_name_hindi = editRequest.requested_value,
                last_modified_by_user_id = modifiedByUserId,
                last_modified_by_role_id = modifiedByRoleId,
                updated_at = System.currentTimeMillis(),
                sync_version = beneficiary.sync_version + 1,
                is_synced = false
            )
            "gender" -> beneficiary.copy(
                gender = editRequest.requested_value,
                last_modified_by_user_id = modifiedByUserId,
                last_modified_by_role_id = modifiedByRoleId,
                updated_at = System.currentTimeMillis(),
                sync_version = beneficiary.sync_version + 1,
                is_synced = false
            )
            "age_years" -> beneficiary.copy(
                age_years = editRequest.requested_value.toIntOrNull() ?: beneficiary.age_years,
                last_modified_by_user_id = modifiedByUserId,
                last_modified_by_role_id = modifiedByRoleId,
                updated_at = System.currentTimeMillis(),
                sync_version = beneficiary.sync_version + 1,
                is_synced = false
            )
            "mobile_number" -> beneficiary.copy(
                mobile_number = editRequest.requested_value,
                last_modified_by_user_id = modifiedByUserId,
                last_modified_by_role_id = modifiedByRoleId,
                updated_at = System.currentTimeMillis(),
                sync_version = beneficiary.sync_version + 1,
                is_synced = false
            )
            else -> beneficiary  // Unknown field, return unchanged
        }
    }
    
    /**
     * Create audit log
     */
    private suspend fun createAuditLog(
        userId: String,
        userRoleId: Int,
        action: String,
        beneficiaryId: String,
        description: String
    ) {
        val auditLog = AuditLogEntity(
            id = UUID.randomUUID().toString(),
            user_id = userId,
            user_role_id = userRoleId,
            action_type = action,
            entity_type = "EDIT_REQUEST",
            entity_id = beneficiaryId,
            action_description_hindi = description,
            gps_lat = 0.0,
            gps_lng = 0.0,
            gps_accuracy_meters = 0f,
            device_id = "",
            app_version = ""
        )
        
        auditLogDao.insert(auditLog)
    }
}
