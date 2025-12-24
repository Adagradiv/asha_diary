package com.upgovt.ashadiary.data.local.dao

import androidx.room.*
import com.upgovt.ashadiary.data.local.entities.UserEntity
import kotlinx.coroutines.flow.Flow

/**
 * UserDao - Data Access Object for USER entity
 * All operations return Flow for reactive UI updates
 */
@Dao
interface UserDao {
    
    // CREATE
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<UserEntity>): List<Long>
    
    // READ
    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun getUserById(userId: String): UserEntity?
    
    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    fun observeUserById(userId: String): Flow<UserEntity?>
    
    @Query("SELECT * FROM users WHERE username = :username AND is_active = 1 LIMIT 1")
    suspend fun getUserByUsername(username: String): UserEntity?
    
    @Query("SELECT * FROM users WHERE phone_number = :phoneNumber AND is_active = 1 LIMIT 1")
    suspend fun getUserByPhone(phoneNumber: String): UserEntity?
    
    @Query("SELECT * FROM users WHERE role_id = :roleId AND is_active = 1")
    suspend fun getUsersByRole(roleId: Int): List<UserEntity>
    
    @Query("SELECT * FROM users WHERE role_id = :roleId AND is_active = 1")
    fun observeUsersByRole(roleId: Int): Flow<List<UserEntity>>
    
    @Query("SELECT * FROM users WHERE is_active = 1 ORDER BY full_name ASC")
    suspend fun getAllActiveUsers(): List<UserEntity>
    
    @Query("SELECT * FROM users WHERE is_active = 1 ORDER BY full_name ASC")
    fun observeAllActiveUsers(): Flow<List<UserEntity>>
    
    // Authentication
    @Query("""
        SELECT * FROM users 
        WHERE username = :username 
        AND password_hash = :passwordHash 
        AND is_active = 1 
        LIMIT 1
    """)
    suspend fun authenticateUser(username: String, passwordHash: String): UserEntity?
    
    @Query("""
        SELECT * FROM users 
        WHERE offline_token = :token 
        AND is_active = 1 
        LIMIT 1
    """)
    suspend fun getUserByOfflineToken(token: String): UserEntity?
    
    // UPDATE
    @Update
    suspend fun updateUser(user: UserEntity): Int
    
    @Query("UPDATE users SET is_active = 0, updated_at = :timestamp WHERE id = :userId")
    suspend fun deactivateUser(userId: String, timestamp: Long = System.currentTimeMillis()): Int
    
    @Query("""
        UPDATE users 
        SET is_synced = :isSynced, 
            last_synced_at = :syncedAt, 
            server_id = :serverId 
        WHERE id = :userId
    """)
    suspend fun updateSyncStatus(
        userId: String,
        isSynced: Boolean,
        syncedAt: Long,
        serverId: String?
    ): Int
    
    // DELETE (Soft delete preferred)
    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteUser(userId: String): Int
    
    // SYNC-RELATED
    @Query("SELECT * FROM users WHERE is_synced = 0")
    suspend fun getUnsyncedUsers(): List<UserEntity>
    
    @Query("SELECT COUNT(*) FROM users WHERE is_synced = 0")
    suspend fun getUnsyncedCount(): Int
    
    @Query("SELECT COUNT(*) FROM users WHERE is_synced = 0")
    fun observeUnsyncedCount(): Flow<Int>
}
