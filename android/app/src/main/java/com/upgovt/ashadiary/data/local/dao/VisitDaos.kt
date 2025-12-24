package com.upgovt.ashadiary.data.local.dao

import androidx.room.*
import com.upgovt.ashadiary.data.local.entities.*
import kotlinx.coroutines.flow.Flow

/**
 * Visit DAOs - Combined file for all health visit entities
 * ANC, BP, Blood Sugar, Vaccination
 */

// ==================== ANC VISIT DAO ====================
@Dao
interface ANCVisitDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(visit: ANCVisitEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(visits: List<ANCVisitEntity>): List<Long>
    
    @Query("SELECT * FROM anc_visits WHERE id = :visitId LIMIT 1")
    suspend fun getById(visitId: String): ANCVisitEntity?
    
    @Query("SELECT * FROM anc_visits WHERE beneficiary_id = :beneficiaryId ORDER BY visit_date DESC")
    suspend fun getByBeneficiary(beneficiaryId: String): List<ANCVisitEntity>
    
    @Query("SELECT * FROM anc_visits WHERE beneficiary_id = :beneficiaryId ORDER BY visit_date DESC")
    fun observeByBeneficiary(beneficiaryId: String): Flow<List<ANCVisitEntity>>
    
    @Query("SELECT * FROM anc_visits WHERE beneficiary_id = :beneficiaryId ORDER BY visit_number DESC LIMIT 1")
    suspend fun getLatestVisit(beneficiaryId: String): ANCVisitEntity?
    
    @Query("SELECT MAX(visit_number) FROM anc_visits WHERE beneficiary_id = :beneficiaryId")
    suspend fun getLastVisitNumber(beneficiaryId: String): Int?
    
    @Update
    suspend fun update(visit: ANCVisitEntity): Int
    
    @Query("DELETE FROM anc_visits WHERE id = :visitId")
    suspend fun delete(visitId: String): Int
    
    @Query("SELECT * FROM anc_visits WHERE is_synced = 0 ORDER BY recorded_at ASC")
    suspend fun getUnsynced(): List<ANCVisitEntity>
    
    @Query("SELECT COUNT(*) FROM anc_visits WHERE is_synced = 0")
    fun observeUnsyncedCount(): Flow<Int>
}

// ==================== BP VISIT DAO ====================
@Dao
interface BPVisitDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(visit: BPVisitEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(visits: List<BPVisitEntity>): List<Long>
    
    @Query("SELECT * FROM bp_visits WHERE id = :visitId LIMIT 1")
    suspend fun getById(visitId: String): BPVisitEntity?
    
    @Query("SELECT * FROM bp_visits WHERE beneficiary_id = :beneficiaryId ORDER BY visit_date DESC")
    suspend fun getByBeneficiary(beneficiaryId: String): List<BPVisitEntity>
    
    @Query("SELECT * FROM bp_visits WHERE beneficiary_id = :beneficiaryId ORDER BY visit_date DESC")
    fun observeByBeneficiary(beneficiaryId: String): Flow<List<BPVisitEntity>>
    
    @Query("SELECT * FROM bp_visits WHERE beneficiary_id = :beneficiaryId ORDER BY visit_date DESC LIMIT 1")
    suspend fun getLatestVisit(beneficiaryId: String): BPVisitEntity?
    
    @Query("SELECT * FROM bp_visits WHERE bp_category IN ('HIGH_STAGE_2', 'HYPERTENSIVE_CRISIS') ORDER BY visit_date DESC")
    suspend fun getHighRiskVisits(): List<BPVisitEntity>
    
    @Update
    suspend fun update(visit: BPVisitEntity): Int
    
    @Query("DELETE FROM bp_visits WHERE id = :visitId")
    suspend fun delete(visitId: String): Int
    
    @Query("SELECT * FROM bp_visits WHERE is_synced = 0 ORDER BY recorded_at ASC")
    suspend fun getUnsynced(): List<BPVisitEntity>
    
    @Query("SELECT COUNT(*) FROM bp_visits WHERE is_synced = 0")
    fun observeUnsyncedCount(): Flow<Int>
}

// ==================== BLOOD SUGAR VISIT DAO ====================
@Dao
interface BloodSugarVisitDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(visit: BloodSugarVisitEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(visits: List<BloodSugarVisitEntity>): List<Long>
    
    @Query("SELECT * FROM blood_sugar_visits WHERE id = :visitId LIMIT 1")
    suspend fun getById(visitId: String): BpoodSugarVisitEntity?
    
    @Query("SELECT * FROM blood_sugar_visits WHERE beneficiary_id = :beneficiaryId ORDER BY visit_date DESC")
    suspend fun getByBeneficiary(beneficiaryId: String): List<BloodSugarVisitEntity>
    
    @Query("SELECT * FROM blood_sugar_visits WHERE beneficiary_id = :beneficiaryId ORDER BY visit_date DESC")
    fun observeByBeneficiary(beneficiaryId: String): Flow<List<BloodSugarVisitEntity>>
    
    @Query("SELECT * FROM blood_sugar_visits WHERE beneficiary_id = :beneficiaryId ORDER BY visit_date DESC LIMIT 1")
    suspend fun getLatestVisit(beneficiaryId: String): BloodSugarVisitEntity?
    
    @Query("SELECT * FROM blood_sugar_visits WHERE diabetes_risk = 'DIABETIC' ORDER BY visit_date DESC")
    suspend fun getDiabeticVisits(): List<BloodSugarVisitEntity>
    
    @Update
    suspend fun update(visit: BloodSugarVisitEntity): Int
    
    @Query("DELETE FROM blood_sugar_visits WHERE id = :visitId")
    suspend fun delete(visitId: String): Int
    
    @Query("SELECT * FROM blood_sugar_visits WHERE is_synced = 0 ORDER BY recorded_at ASC")
    suspend fun getUnsynced(): List<BloodSugarVisitEntity>
    
    @Query("SELECT COUNT(*) FROM blood_sugar_visits WHERE is_synced = 0")
    fun observeUnsyncedCount(): Flow<Int>
}

// ==================== VACCINATION VISIT DAO ====================
@Dao
interface VaccinationVisitDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(visit: VaccinationVisitEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(visits: List<VaccinationVisitEntity>): List<Long>
    
    @Query("SELECT * FROM vaccination_visits WHERE id = :visitId LIMIT 1")
    suspend fun getById(visitId: String): VaccinationVisitEntity?
    
    @Query("SELECT * FROM vaccination_visits WHERE beneficiary_id = :beneficiaryId ORDER BY visit_date DESC")
    suspend fun getByBeneficiary(beneficiaryId: String): List<VaccinationVisitEntity>
    
    @Query("SELECT * FROM vaccination_visits WHERE beneficiary_id = :beneficiaryId ORDER BY visit_date DESC")
    fun observeByBeneficiary(beneficiaryId: String): Flow<List<VaccinationVisitEntity>>
    
    @Query("SELECT * FROM vaccination_visits WHERE beneficiary_id = :beneficiaryId AND vaccine_type = :vaccineType ORDER BY dose_number DESC LIMIT 1")
    suspend fun getLatestDoseForVaccine(beneficiaryId: String, vaccineType: String): VaccinationVisitEntity?
    
    @Query("SELECT * FROM vaccination_visits WHERE has_adverse_reaction = 1 ORDER BY visit_date DESC")
    suspend fun getVisitsWithAdverseReactions(): List<VaccinationVisitEntity>
    
    @Update
    suspend fun update(visit: VaccinationVisitEntity): Int
    
    @Query("DELETE FROM vaccination_visits WHERE id = :visitId")
    suspend fun delete(visitId: String): Int
    
    @Query("SELECT * FROM vaccination_visits WHERE is_synced = 0 ORDER BY recorded_at ASC")
    suspend fun getUnsynced(): List<VaccinationVisitEntity>
    
    @Query("SELECT COUNT(*) FROM vaccination_visits WHERE is_synced = 0")
    fun observeUnsyncedCount(): Flow<Int>
}
