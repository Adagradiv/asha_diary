package com.upgovt.ashadiary.data.api

import retrofit2.Response
import retrofit2.http.*

/**
 * ASHADiaryApiService - REST API interface for backend communication
 * 
 * Base URL: To be configured (e.g., https://ashadiary.up.gov.in/api/)
 */
interface ASHADiaryApiService {
    
    // ==================== AUTHENTICATION ====================
    
    @POST("auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<LoginResponse>
    
    @POST("auth/logout")
    suspend fun logout(
        @Header("Authorization") token: String
    ): Response<Unit>
    
    @GET("auth/validate")
    suspend fun validateToken(
        @Header("Authorization") token: String
    ): Response<UserResponse>
    
    // ==================== SYNC ====================
    
    /**
     * Push local changes to server
     */
    @POST("sync/push")
    suspend fun pushChanges(
        @Header("Authorization") token: String,
        @Body request: SyncPushRequest
    ): Response<SyncPushResponse>
    
    /**
     * Pull server changes
     */
    @POST("sync/pull")
    suspend fun pullChanges(
        @Header("Authorization") token: String,
        @Body request: SyncPullRequest
    ): Response<SyncPullResponse>
    
    // ==================== BENEFICIARIES ====================
    
    @GET("beneficiaries")
    suspend fun getBeneficiaries(
        @Header("Authorization") token: String,
        @Query("village_id") villageId: String?,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0
    ): Response<BeneficiaryListResponse>
    
    @GET("beneficiaries/{id}")
    suspend fun getBeneficiary(
        @Header("Authorization") token: String,
        @Path("id") beneficiaryId: String
    ): Response<BeneficiaryResponse>
    
    @POST("beneficiaries")
    suspend fun createBeneficiary(
        @Header("Authorization") token: String,
        @Body request: BeneficiaryCreateRequest
    ): Response<BeneficiaryResponse>
    
    @PUT("beneficiaries/{id}")
    suspend fun updateBeneficiary(
        @Header("Authorization") token: String,
        @Path("id") beneficiaryId: String,
        @Body request: BeneficiaryUpdateRequest
    ): Response<BeneficiaryResponse>
    
    // ==================== VISITS ====================
    
    @POST("visits/anc")
    suspend fun createANCVisit(
        @Header("Authorization") token: String,
        @Body request: ANCVisitRequest
    ): Response<VisitResponse>
    
    @POST("visits/bp")
    suspend fun createBPVisit(
        @Header("Authorization") token: String,
        @Body request: BPVisitRequest
    ): Response<VisitResponse>
    
    @POST("visits/blood-sugar")
    suspend fun createBloodSugarVisit(
        @Header("Authorization") token: String,
        @Body request: BloodSugarVisitRequest
    ): Response<VisitResponse>
    
    @POST("visits/vaccination")
    suspend fun createVaccinationVisit(
        @Header("Authorization") token: String,
        @Body request: VaccinationVisitRequest
    ): Response<VisitResponse>
    
    // ==================== EDIT REQUESTS ====================
    
    @GET("edit-requests")
    suspend fun getEditRequests(
        @Header("Authorization") token: String,
        @Query("status") status: String? = null
    ): Response<EditRequestListResponse>
    
    @POST("edit-requests")
    suspend fun createEditRequest(
        @Header("Authorization") token: String,
        @Body request: EditRequestCreateRequest
    ): Response<EditRequestResponse>
    
    @PUT("edit-requests/{id}/approve")
    suspend fun approveEditRequest(
        @Header("Authorization") token: String,
        @Path("id") requestId: String,
        @Body notes: EditRequestReviewRequest
    ): Response<EditRequestResponse>
    
    @PUT("edit-requests/{id}/reject")
    suspend fun rejectEditRequest(
        @Header("Authorization") token: String,
        @Path("id") requestId: String,
        @Body notes: EditRequestReviewRequest
    ): Response<EditRequestResponse>
    
    // ==================== DUPLICATES ====================
    
    @GET("duplicates")
    suspend fun getDuplicateFlags(
        @Header("Authorization") token: String,
        @Query("status") status: String? = "PENDING"
    ): Response<DuplicateFlagListResponse>
    
    @PUT("duplicates/{id}/resolve")
    suspend fun resolveDuplicate(
        @Header("Authorization") token: String,
        @Path("id") flagId: String,
        @Body request: DuplicateResolveRequest
    ): Response<DuplicateFlagResponse>
    
    // ==================== AUDIT LOGS ====================
    
    @POST("audit-logs/batch")
    suspend fun uploadAuditLogs(
        @Header("Authorization") token: String,
        @Body logs: List<AuditLogRequest>
    ): Response<BatchUploadResponse>
    
    // ==================== VILLAGES ====================
    
    @GET("villages")
    suspend fun getVillages(
        @Header("Authorization") token: String,
        @Query("block_code") blockCode: String? = null
    ): Response<VillageListResponse>
}

// ==================== REQUEST/RESPONSE MODELS ====================

// Authentication
data class LoginRequest(val username: String, val password: String)
data class LoginResponse(val token: String, val user: UserResponse)
data class UserResponse(
    val id: String,
    val username: String,
    val full_name: String,
    val role_id: Int,
    val role_name: String
)

// Sync
data class SyncPushRequest(val changes: List<SyncChangeItem>)
data class SyncChangeItem(
    val entity_type: String,
    val entity_id: String,
    val operation: String,
    val data: Map<String, Any>,
    val sync_version: Int,
    val role_id: Int
)
data class SyncPushResponse(
    val accepted: Int,
    val rejected: Int,
    val conflicts: List<SyncConflict>
)
data class SyncConflict(
    val entity_type: String,
    val entity_id: String,
    val reason: String,
    val server_version: Int
)

data class SyncPullRequest(val last_sync_timestamp: Long)
data class SyncPullResponse(
    val beneficiaries: List<Map<String, Any>>,
    val visits: List<Map<String, Any>>,
    val edit_requests: List<Map<String, Any>>,
    val timestamp: Long
)

// Beneficiaries
data class BeneficiaryListResponse(val data: List<Map<String, Any>>, val total: Int)
data class BeneficiaryResponse(val data: Map<String, Any>)
data class BeneficiaryCreateRequest(val beneficiary: Map<String, Any>)
data class BeneficiaryUpdateRequest(val beneficiary: Map<String, Any>, val sync_version: Int)

// Visits
data class ANCVisitRequest(val visit: Map<String, Any>)
data class BPVisitRequest(val visit: Map<String, Any>)
data class BloodSugarVisitRequest(val visit: Map<String, Any>)
data class VaccinationVisitRequest(val visit: Map<String, Any>)
data class VisitResponse(val data: Map<String, Any>)

// Edit Requests
data class EditRequestListResponse(val data: List<Map<String, Any>>, val total: Int)
data class EditRequestResponse(val data: Map<String, Any>)
data class EditRequestCreateRequest(val request: Map<String, Any>)
data class EditRequestReviewRequest(val notes: String?)

// Duplicates
data class DuplicateFlagListResponse(val data: List<Map<String, Any>>, val total: Int)
data class DuplicateFlagResponse(val data: Map<String, Any>)
data class DuplicateResolveRequest(
    val action: String,
    val notes: String?,
    val master_beneficiary_id: String?
)

// Audit Logs
data class AuditLogRequest(val log: Map<String, Any>)
data class BatchUploadResponse(val uploaded: Int, val failed: Int)

// Villages
data class VillageListResponse(val data: List<Map<String, Any>>, val total: Int)
