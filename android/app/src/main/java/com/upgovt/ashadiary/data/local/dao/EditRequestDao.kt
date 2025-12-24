package com.upgovt.ashadiary.data.local.dao

import androidx.room.*
import com.upgovt.ashadiary.data.local.entities.EditRequestEntity
import kotlinx.coroutines.flow.Flow

/**
 * EditRequestDao - Handles edit request workflow
 * Field Staff → Supervisor → Block Nodal
 */
@Dao
interface EditRequestDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(request: EditRequestEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(requests: List<EditRequestEntity>): List<Long>
    
    @Query("SELECT * FROM edit_requests WHERE id = :requestId LIMIT 1")
    suspend fun getById(requestId: String): EditRequestEntity?
    
    @Query("SELECT * FROM edit_requests WHERE id = :requestId LIMIT 1")
    fun observeById(requestId: String): Flow<EditRequestEntity?>
    
    // Get requests by beneficiary
    @Query("SELECT * FROM edit_requests WHERE beneficiary_id = :beneficiaryId ORDER BY requested_at DESC")
    suspend fun getByBeneficiary(beneficiaryId: String): List<EditRequestEntity>
    
    @Query("SELECT * FROM edit_requests WHERE beneficiary_id = :beneficiaryId ORDER BY requested_at DESC")
    fun observeByBeneficiary(beneficiaryId: String): Flow<List<EditRequestEntity>>
    
    // Get requests by status
    @Query("SELECT * FROM edit_requests WHERE status = :status ORDER BY requested_at DESC")
    suspend fun getByStatus(status: String): List<EditRequestEntity>
    
    @Query("SELECT * FROM edit_requests WHERE status = :status ORDER BY requested_at DESC")
    fun observeByStatus(status: String): Flow<List<EditRequestEntity>>
    
    // Pending requests for review
    @Query("SELECT * FROM edit_requests WHERE status = 'PENDING' ORDER BY requested_at ASC")
    suspend fun getPendingRequests(): List<EditRequestEntity>
    
    @Query("SELECT * FROM edit_requests WHERE status = 'PENDING' ORDER BY requested_at ASC")
    fun observePendingRequests(): Flow<List<EditRequestEntity>>
    
    // Requests submitted by user
    @Query("SELECT * FROM edit_requests WHERE requested_by_user_id = :userId ORDER BY requested_at DESC")
    suspend fun getByRequestedUser(userId: String): List<EditRequestEntity>
    
    @Query("SELECT * FROM edit_requests WHERE requested_by_user_id = :userId ORDER BY requested_at DESC")
    fun observeByRequestedUser(userId: String): Flow<List<EditRequestEntity>>
    
    // Approve request
    @Query("""
        UPDATE edit_requests 
        SET status = 'APPROVED',
            reviewed_by_user_id = :reviewedByUserId,
            reviewed_at = :reviewedAt,
            review_notes_hindi = :reviewNotes,
            updated_at = :reviewedAt
        WHERE id = :requestId
    """)
    suspend fun approveRequest(
        requestId: String,
        reviewedByUserId: String,
        reviewedAt: Long = System.currentTimeMillis(),
        reviewNotes: String? = null
    ): Int
    
    // Reject request
    @Query("""
        UPDATE edit_requests 
        SET status = 'REJECTED',
            reviewed_by_user_id = :reviewedByUserId,
            reviewed_at = :reviewedAt,
            review_notes_hindi = :reviewNotes,
            updated_at = :reviewedAt
        WHERE id = :requestId
    """)
    suspend fun rejectRequest(
        requestId: String,
        reviewedByUserId: String,
        reviewedAt: Long = System.currentTimeMillis(),
        reviewNotes: String? = null
    ): Int
    
    @Update
    suspend fun update(request: EditRequestEntity): Int
    
    @Query("DELETE FROM edit_requests WHERE id = :requestId")
    suspend fun delete(requestId: String): Int
    
    // SYNC
    @Query("SELECT * FROM edit_requests WHERE is_synced = 0 ORDER BY requested_at ASC")
    suspend fun getUnsynced(): List<EditRequestEntity>
    
    @Query("SELECT COUNT(*) FROM edit_requests WHERE status = 'PENDING'")
    suspend fun getPendingCount(): Int
    
    @Query("SELECT COUNT(*) FROM edit_requests WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>
    
    @Query("SELECT COUNT(*) FROM edit_requests WHERE is_synced = 0")
    fun observeUnsyncedCount(): Flow<Int>
}
