package com.example.data.local

import androidx.room.*
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AdminDao {
    @Query("SELECT * FROM admin_profiles LIMIT 1")
    fun getAdminProfile(): Flow<AdminProfile?>

    @Query("SELECT * FROM admin_profiles WHERE isLoggedIn = 1 LIMIT 1")
    fun getLoggedInAdmin(): Flow<AdminProfile?>

    @Query("SELECT * FROM admin_profiles WHERE phone = :phone AND pin = :pin LIMIT 1")
    suspend fun authenticateAdmin(phone: String, pin: String): AdminProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAdmin(admin: AdminProfile): Long

    @Update
    suspend fun updateAdmin(admin: AdminProfile)

    @Query("UPDATE admin_profiles SET isLoggedIn = 0")
    suspend fun logoutAdmin()
}

@Dao
interface SiteDao {
    @Query("SELECT * FROM construction_sites WHERE isActive = 1 ORDER BY name ASC")
    fun getAllActiveSites(): Flow<List<ConstructionSite>>

    @Query("SELECT * FROM construction_sites WHERE id = :id")
    suspend fun getSiteById(id: Long): ConstructionSite?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSite(site: ConstructionSite): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sites: List<ConstructionSite>)

    @Update
    suspend fun updateSite(site: ConstructionSite)

    @Query("DELETE FROM construction_sites WHERE id = :siteId")
    suspend fun deleteSite(siteId: Long)

    @Query("SELECT COUNT(*) FROM construction_sites")
    suspend fun getSitesCount(): Int
}

@Dao
interface InviteDao {
    @Query("SELECT * FROM party_worker_invites WHERE siteId = :siteId ORDER BY createdAt DESC")
    fun getInvitesForParty(siteId: Long): Flow<List<PartyWorkerInvite>>

    @Query("SELECT * FROM party_worker_invites WHERE workerJoinCode = :code LIMIT 1")
    suspend fun getInviteByCode(code: String): PartyWorkerInvite?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvite(invite: PartyWorkerInvite): Long

    @Query("UPDATE party_worker_invites SET isRedeemed = 1, redeemedByWorkerId = :workerId, workerName = :workerName WHERE id = :inviteId")
    suspend fun markInviteRedeemed(inviteId: Long, workerId: Long, workerName: String)

    @Delete
    suspend fun deleteInvite(invite: PartyWorkerInvite)
}

@Dao
interface WorkerDao {
    @Query("SELECT * FROM workers WHERE isActive = 1 ORDER BY name ASC")
    fun getAllActiveWorkers(): Flow<List<Worker>>

    @Query("SELECT * FROM workers ORDER BY isActive DESC, name ASC")
    fun getAllWorkers(): Flow<List<Worker>>

    @Query("SELECT * FROM workers WHERE assignedSiteId = :siteId AND isActive = 1 ORDER BY name ASC")
    fun getWorkersBySite(siteId: Long): Flow<List<Worker>>

    @Query("SELECT * FROM workers WHERE id = :id")
    suspend fun getWorkerById(id: Long): Worker?

    @Query("SELECT * FROM workers WHERE id = :id")
    fun observeWorkerById(id: Long): Flow<Worker?>

    @Query("SELECT * FROM workers WHERE phone = :phone LIMIT 1")
    suspend fun getWorkerByPhone(phone: String): Worker?

    @Query("SELECT * FROM workers WHERE joinCodeUsed = :code LIMIT 1")
    suspend fun getWorkerByJoinCode(code: String): Worker?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorker(worker: Worker): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(workers: List<Worker>)

    @Update
    suspend fun updateWorker(worker: Worker)

    @Query("UPDATE workers SET isActive = 0 WHERE id = :workerId")
    suspend fun softDeleteWorker(workerId: Long)

    @Delete
    suspend fun deleteWorker(worker: Worker)

    @Query("SELECT COUNT(*) FROM workers")
    suspend fun getWorkersCount(): Int

    @Query("SELECT COUNT(*) FROM workers WHERE syncStatus = 'PENDING'")
    fun getPendingWorkersCount(): Flow<Int>

    @Query("UPDATE workers SET syncStatus = 'SYNCED' WHERE syncStatus = 'PENDING'")
    suspend fun markAllWorkersSynced()
}

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance_records WHERE date = :date ORDER BY createdAt DESC")
    fun getAttendanceForDate(date: String): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records WHERE siteId = :siteId AND date = :date")
    fun getAttendanceForSiteAndDate(siteId: Long, date: String): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records WHERE workerId = :workerId AND date = :date LIMIT 1")
    suspend fun getAttendanceForWorkerAndDate(workerId: Long, date: String): AttendanceRecord?

    @Query("SELECT * FROM attendance_records WHERE workerId = :workerId AND date = :date LIMIT 1")
    fun observeAttendanceForWorkerAndDate(workerId: Long, date: String): Flow<AttendanceRecord?>

    @Query("SELECT * FROM attendance_records WHERE workerId = :workerId ORDER BY date DESC")
    fun getAttendanceForWorkerAll(workerId: Long): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records WHERE workerId = :workerId AND date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    fun getAttendanceForWorkerRange(workerId: Long, startDate: String, endDate: String): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records WHERE date BETWEEN :startDate AND :endDate")
    fun getAttendanceForDateRange(startDate: String, endDate: String): Flow<List<AttendanceRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(record: AttendanceRecord): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<AttendanceRecord>)

    @Query("SELECT * FROM attendance_records WHERE syncStatus = 'PENDING'")
    suspend fun getPendingSyncRecords(): List<AttendanceRecord>

    @Query("SELECT COUNT(*) FROM attendance_records WHERE syncStatus = 'PENDING'")
    fun getPendingAttendanceCount(): Flow<Int>

    @Query("UPDATE attendance_records SET syncStatus = 'SYNCED' WHERE syncStatus = 'PENDING'")
    suspend fun markAllAttendanceSynced()
}

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payment_records ORDER BY paymentDate DESC")
    fun getAllPayments(): Flow<List<PaymentRecord>>

    @Query("SELECT * FROM payment_records WHERE workerId = :workerId ORDER BY paymentDate DESC")
    fun getPaymentsForWorker(workerId: Long): Flow<List<PaymentRecord>>

    @Query("SELECT SUM(amount) FROM payment_records WHERE workerId = :workerId")
    fun getTotalPaidForWorker(workerId: Long): Flow<Double?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: PaymentRecord): Long

    @Query("SELECT COUNT(*) FROM payment_records WHERE syncStatus = 'PENDING'")
    fun getPendingPaymentsCount(): Flow<Int>

    @Query("UPDATE payment_records SET syncStatus = 'SYNCED' WHERE syncStatus = 'PENDING'")
    suspend fun markAllPaymentsSynced()
}

@Dao
interface SyncLogDao {
    @Query("SELECT * FROM sync_logs ORDER BY timestamp DESC LIMIT 50")
    fun getRecentSyncLogs(): Flow<List<SyncLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: SyncLog)

    @Query("DELETE FROM sync_logs")
    suspend fun clearLogs()
}
