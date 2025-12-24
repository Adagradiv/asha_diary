package com.upgovt.ashadiary.data.local.database

import android.content.Context
import com.upgovt.ashadiary.data.local.entities.RoleDefaults

/**
 * DatabaseInitializer - Pre-populates database with required data
 * Runs once after database creation
 */
object DatabaseInitializer {
    
    /**
     * Initialize database with default data
     * - Pre-populate roles
     * - Set up initial configuration
     */
    suspend fun initialize(context: Context, database: ASHADiaryDatabase) {
        // Pre-populate roles
        val roleDao = database.roleDao()
        val existingRoles = roleDao.getAllRoles()
        
        if (existingRoles.isEmpty()) {
            roleDao.insertAll(RoleDefaults.getAll())
        }
        
        // Additional initialization can be added here
        // e.g., default villages, sample data for testing, etc.
    }
    
    /**
     * Check if database has been initialized
     */
    suspend fun isInitialized(database: ASHADiaryDatabase): Boolean {
        val roleDao = database.roleDao()
        val roles = roleDao.getAllRoles()
        return roles.isNotEmpty()
    }
}
