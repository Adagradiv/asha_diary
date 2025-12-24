package com.upgovt.ashadiary.data.sync

import com.upgovt.ashadiary.data.local.entities.BeneficiaryEntity
import android.util.Log

/**
 * ConflictResolver - Resolves sync conflicts using role-based authority
 * 
 * RULE: Higher role ALWAYS wins
 * Role hierarchy: 1=Field Staff < 2=Supervisor < 3=Block Nodal < 4=District Nodal < 5=State Admin
 */
object ConflictResolver {
    
    private const val TAG = "ConflictResolver"
    
    /**
     * Resolve conflict between local and server data
     * 
     * @param local Local entity with metadata
     * @param server Server entity with metadata
     * @return Winning entity and reason for audit log
     */
    fun <T : Any> resolveConflict(
        local: SyncableEntity<T>,
        server: SyncableEntity<T>
    ): ConflictResolution<T> {
        
        // Rule 1: Higher role wins
        if (local.lastModifiedByRoleId != server.lastModifiedByRoleId) {
            return if (local.lastModifiedByRoleId > server.lastModifiedByRoleId) {
                Log.d(TAG, "Local wins: Higher role (${local.lastModifiedByRoleId} > ${server.lastModifiedByRoleId})")
                ConflictResolution(
                    winner = ConflictWinner.LOCAL,
                    winningEntity = local,
                    reason = "स्थानीय परिवर्तन उच्च अधिकार द्वारा (Role ${local.lastModifiedByRoleId})"
                )
            } else {
                Log.d(TAG, "Server wins: Higher role (${server.lastModifiedByRoleId} > ${local.lastModifiedByRoleId})")
                ConflictResolution(
                    winner = ConflictWinner.SERVER,
                    winningEntity = server,
                    reason = "सर्वर परिवर्तन उच्च अधिकार द्वारा (Role ${server.lastModifiedByRoleId})"
                )
            }
        }
        
        // Rule 2: If same role, use version number (optimistic locking)
        if (local.syncVersion != server.syncVersion) {
            return if (local.syncVersion > server.syncVersion) {
                Log.d(TAG, "Local wins: Higher version (${local.syncVersion} > ${server.syncVersion})")
                ConflictResolution(
                    winner = ConflictWinner.LOCAL,
                    winningEntity = local,
                    reason = "स्थानीय परिवर्तन नया (Version ${local.syncVersion})"
                )
            } else {
                Log.d(TAG, "Server wins: Higher version (${server.syncVersion} > ${local.syncVersion})")
                ConflictResolution(
                    winner = ConflictWinner.SERVER,
                    winningEntity = server,
                    reason = "सर्वर परिवर्तन नया (Version ${server.syncVersion})"
                )
            }
        }
        
        // Rule 3: If same role and version, use timestamp (last write wins)
        return if (local.updatedAt >= server.updatedAt) {
            Log.d(TAG, "Local wins: Latest timestamp")
            ConflictResolution(
                winner = ConflictWinner.LOCAL,
                winningEntity = local,
                reason = "स्थानीय परिवर्तन नवीनतम"
            )
        } else {
            Log.d(TAG, "Server wins: Latest timestamp")
            ConflictResolution(
                winner = ConflictWinner.SERVER,
                winningEntity = server,
                reason = "सर्वर परिवर्तन नवीनतम"
            )
        }
    }
    
    /**
     * Check if local changes should be uploaded
     * (Local role >= Server role)
     */
    fun shouldUploadLocalChanges(
        localRoleId: Int,
        serverRoleId: Int
    ): Boolean {
        return localRoleId >= serverRoleId
    }
    
    /**
     * Merge beneficiary data intelligently
     * When both local and server have changes, merge non-conflicting fields
     */
    fun mergeBeneficiary(
        local: BeneficiaryEntity,
        server: BeneficiaryEntity,
        winningSource: ConflictWinner
    ): BeneficiaryEntity {
        // Use winner as base
        val base = if (winningSource == ConflictWinner.LOCAL) local else server
        
        // For now, return winner as-is
        // In production, you might want to merge specific fields intelligently
        // e.g., merge visit counts, keep latest GPS coordinates, etc.
        
        return base.copy(
            sync_version = maxOf(local.sync_version, server.sync_version) + 1
        )
    }
}

/**
 * Syncable entity wrapper with metadata
 */
data class SyncableEntity<T>(
    val data: T,
    val lastModifiedByRoleId: Int,
    val syncVersion: Int,
    val updatedAt: Long,
    val lastModifiedByUserId: String
)

/**
 * Conflict resolution result
 */
data class ConflictResolution<T>(
    val winner: ConflictWinner,
    val winningEntity: SyncableEntity<T>,
    val reason: String  // For audit log (Hindi)
)

/**
 * Conflict winner enum
 */
enum class ConflictWinner {
    LOCAL,
    SERVER,
    MERGED
}
