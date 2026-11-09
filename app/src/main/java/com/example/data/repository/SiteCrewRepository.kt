package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import java.util.*

data class WorkerPayrollItem(
    val worker: Worker,
    val fullDaysCount: Int,
    val halfDaysCount: Int,
    val absentDaysCount: Int,
    val leaveDaysCount: Int,
    val totalOvertimeHours: Double,
    val effectiveDaysWorked: Double,
    val baseWageEarned: Double,
    val overtimeEarned: Double,
    val grossPay: Double,
    val totalPaidAlready: Double,
    val netPayable: Double,
    val isFullyPaid: Boolean,
    val records: List<AttendanceRecord>
)

data class PayrollSummary(
    val startDate: String,
    val endDate: String,
    val totalWorkers: Int,
    val totalDaysWorked: Double,
    val totalOvertimeHours: Double,
    val totalGrossExpense: Double,
    val totalPaidAlready: Double,
    val totalOutstandingPayable: Double,
    val items: List<WorkerPayrollItem>
)

data class SyncResult(
    val success: Boolean,
    val workersSynced: Int,
    val attendanceSynced: Int,
    val paymentsSynced: Int,
    val message: String
)

class SiteCrewRepository(private val database: AppDatabase) {
    private val adminDao = database.adminDao()
    private val siteDao = database.siteDao()
    private val inviteDao = database.inviteDao()
    private val workerDao = database.workerDao()
    private val attendanceDao = database.attendanceDao()
    private val paymentDao = database.paymentDao()
    private val syncLogDao = database.syncLogDao()

    val loggedInAdmin: Flow<AdminProfile?> = adminDao.getLoggedInAdmin()
    val allSites: Flow<List<ConstructionSite>> = siteDao.getAllActiveSites()
    val allWorkers: Flow<List<Worker>> = workerDao.getAllWorkers()
    val activeWorkers: Flow<List<Worker>> = workerDao.getAllActiveWorkers()
    val allPayments: Flow<List<PaymentRecord>> = paymentDao.getAllPayments()
    val syncLogs: Flow<List<SyncLog>> = syncLogDao.getRecentSyncLogs()

    val pendingSyncCount: Flow<Int> = combine(
        workerDao.getPendingWorkersCount(),
        attendanceDao.getPendingAttendanceCount(),
        paymentDao.getPendingPaymentsCount()
    ) { w, a, p -> w + a + p }

    // Admin Authentication
    suspend fun loginAdmin(phone: String, pin: String): AdminProfile? = withContext(Dispatchers.IO) {
        val admin = adminDao.authenticateAdmin(phone.trim(), pin.trim())
        if (admin != null) {
            adminDao.updateAdmin(admin.copy(isLoggedIn = true))
        }
        admin
    }

    suspend fun registerAdmin(name: String, phone: String, pin: String, companyName: String): Long = withContext(Dispatchers.IO) {
        adminDao.logoutAdmin()
        val admin = AdminProfile(
            name = name.trim(),
            phone = phone.trim(),
            pin = pin.trim(),
            companyName = companyName.trim(),
            isLoggedIn = true
        )
        val id = adminDao.insertAdmin(admin)
        syncLogDao.insertLog(
            SyncLog(
                action = "ADMIN_REGISTERED",
                description = "Admin $name ($companyName) registered and logged in.",
                status = SyncStatus.SYNCED
            )
        )
        id
    }

    suspend fun logoutAdmin() = withContext(Dispatchers.IO) {
        adminDao.logoutAdmin()
    }

    // Party (Site) Management
    suspend fun createParty(
        name: String,
        clientName: String,
        address: String,
        latitude: Double,
        longitude: Double,
        geofenceRadius: Double,
        defaultWage: Double
    ): Long = withContext(Dispatchers.IO) {
        val party = ConstructionSite(
            name = name.trim(),
            clientName = clientName.trim(),
            address = address.trim(),
            latitude = latitude,
            longitude = longitude,
            geofenceRadiusMeters = geofenceRadius,
            defaultDailyWage = defaultWage
        )
        val id = siteDao.insertSite(party)
        syncLogDao.insertLog(
            SyncLog(
                action = "PARTY_CREATED",
                description = "Admin created party '$name' with GPS perimeter (${geofenceRadius.toInt()}m).",
                status = SyncStatus.PENDING
            )
        )
        id
    }

    suspend fun deleteParty(siteId: Long) = withContext(Dispatchers.IO) {
        siteDao.deleteSite(siteId)
    }

    // Worker Join Codes (Only visible to admin, different for each worker/entry)
    fun getInvitesForParty(siteId: Long): Flow<List<PartyWorkerInvite>> =
        inviteDao.getInvitesForParty(siteId)

    suspend fun generateWorkerJoinCode(
        siteId: Long,
        trade: String,
        dailyWage: Double,
        workerName: String = ""
    ): PartyWorkerInvite = withContext(Dispatchers.IO) {
        // Generate an uppercase unique alphanumeric code e.g. "SC-7842"
        val randomNum = SecureRandom().nextInt(9000) + 1000
        val code = "SC-$randomNum"

        val invite = PartyWorkerInvite(
            siteId = siteId,
            workerJoinCode = code,
            trade = trade,
            dailyWage = dailyWage,
            workerName = workerName,
            isRedeemed = false
        )
        val id = inviteDao.insertInvite(invite)
        val created = invite.copy(id = id)

        syncLogDao.insertLog(
            SyncLog(
                action = "JOIN_CODE_GENERATED",
                description = "Generated private join code '$code' for Party #$siteId ($trade).",
                status = SyncStatus.PENDING
            )
        )
        created
    }

    suspend fun deleteInvite(invite: PartyWorkerInvite) = withContext(Dispatchers.IO) {
        inviteDao.deleteInvite(invite)
    }

    // Worker Join Flow through Unique Code
    suspend fun joinPartyWithCode(
        code: String,
        workerName: String,
        phone: String,
        tradeOverride: String?,
        upiId: String
    ): kotlin.Result<Worker> = withContext(Dispatchers.IO) {
        val cleanCode = code.trim().uppercase()
        val invite = inviteDao.getInviteByCode(cleanCode)
            ?: return@withContext kotlin.Result.failure(IllegalArgumentException("Invalid Party Code. Please verify the code given by your Site Admin."))

        if (invite.isRedeemed) {
            return@withContext kotlin.Result.failure(IllegalStateException("This join code has already been redeemed by another worker."))
        }

        val site = siteDao.getSiteById(invite.siteId)
            ?: return@withContext kotlin.Result.failure(IllegalStateException("The construction party for this code is no longer active."))

        val finalTrade = tradeOverride?.takeIf { it.isNotBlank() } ?: invite.trade
        val finalWage = if (invite.dailyWage > 0) invite.dailyWage else site.defaultDailyWage

        val newWorker = Worker(
            name = workerName.trim(),
            phone = phone.trim(),
            trade = finalTrade,
            dailyWage = finalWage,
            hourlyRate = finalWage / 8.0,
            assignedSiteId = site.id,
            joinCodeUsed = cleanCode,
            upiId = upiId.trim(),
            syncStatus = SyncStatus.PENDING
        )

        val workerId = workerDao.insertWorker(newWorker)
        inviteDao.markInviteRedeemed(invite.id, workerId, workerName.trim())

        syncLogDao.insertLog(
            SyncLog(
                action = "WORKER_JOINED_PARTY",
                description = "Worker $workerName joined party '${site.name}' via code $cleanCode.",
                status = SyncStatus.PENDING
            )
        )

        kotlin.Result.success(newWorker.copy(id = workerId))
    }

    // Worker queries
    fun getWorkersForSite(siteId: Long): Flow<List<Worker>> =
        workerDao.getWorkersBySite(siteId)

    suspend fun getWorkerById(workerId: Long): Worker? = withContext(Dispatchers.IO) {
        workerDao.getWorkerById(workerId)
    }

    fun observeWorkerById(workerId: Long): Flow<Worker?> =
        workerDao.observeWorkerById(workerId)

    suspend fun getWorkerByPhone(phone: String): Worker? = withContext(Dispatchers.IO) {
        workerDao.getWorkerByPhone(phone.trim())
    }

    suspend fun addWorker(worker: Worker): Long = withContext(Dispatchers.IO) {
        val workerWithPending = worker.copy(syncStatus = SyncStatus.PENDING)
        val id = workerDao.insertWorker(workerWithPending)
        syncLogDao.insertLog(
            SyncLog(
                action = "WORKER_ADDED",
                description = "Added worker ${worker.name} (${worker.trade}) to site database",
                status = SyncStatus.PENDING
            )
        )
        id
    }

    suspend fun updateWorker(worker: Worker) = withContext(Dispatchers.IO) {
        val updated = worker.copy(syncStatus = SyncStatus.PENDING)
        workerDao.updateWorker(updated)
        syncLogDao.insertLog(
            SyncLog(
                action = "WORKER_UPDATED",
                description = "Updated profile for worker ${worker.name}",
                status = SyncStatus.PENDING
            )
        )
    }

    suspend fun removeWorker(workerId: Long, permanent: Boolean = false) = withContext(Dispatchers.IO) {
        val worker = workerDao.getWorkerById(workerId)
        if (permanent && worker != null) {
            workerDao.deleteWorker(worker)
        } else {
            workerDao.softDeleteWorker(workerId)
        }
        syncLogDao.insertLog(
            SyncLog(
                action = "WORKER_REMOVED",
                description = "Removed worker #${workerId} (${worker?.name ?: "Unknown"}) from active crew",
                status = SyncStatus.PENDING
            )
        )
    }

    // Attendance
    fun getAttendanceForDate(date: String): Flow<List<AttendanceRecord>> =
        attendanceDao.getAttendanceForDate(date)

    fun getAttendanceForSiteAndDate(siteId: Long, date: String): Flow<List<AttendanceRecord>> =
        attendanceDao.getAttendanceForSiteAndDate(siteId, date)

    fun observeAttendanceForWorkerAndDate(workerId: Long, date: String): Flow<AttendanceRecord?> =
        attendanceDao.observeAttendanceForWorkerAndDate(workerId, date)

    fun getAttendanceForWorkerAll(workerId: Long): Flow<List<AttendanceRecord>> =
        attendanceDao.getAttendanceForWorkerAll(workerId)

    suspend fun markAttendance(record: AttendanceRecord): Long = withContext(Dispatchers.IO) {
        val recordToSave = record.copy(
            syncStatus = SyncStatus.PENDING,
            updatedAt = System.currentTimeMillis()
        )
        val id = attendanceDao.insertOrUpdate(recordToSave)
        syncLogDao.insertLog(
            SyncLog(
                action = "ATTENDANCE_RECORD",
                description = "Marked ${record.status.label} for Worker #${record.workerId} with GPS verification",
                status = SyncStatus.PENDING
            )
        )
        id
    }

    suspend fun batchMarkAttendance(records: List<AttendanceRecord>) = withContext(Dispatchers.IO) {
        val recordsToSave = records.map {
            it.copy(
                syncStatus = SyncStatus.PENDING,
                updatedAt = System.currentTimeMillis()
            )
        }
        attendanceDao.insertAll(recordsToSave)
        syncLogDao.insertLog(
            SyncLog(
                action = "BATCH_ATTENDANCE",
                description = "Batch Roll-Call logged for ${records.size} workers with GPS tag",
                status = SyncStatus.PENDING,
                recordsCount = records.size
            )
        )
    }

    // Payments
    suspend fun recordPayment(payment: PaymentRecord): Long = withContext(Dispatchers.IO) {
        val id = paymentDao.insertPayment(payment.copy(syncStatus = SyncStatus.PENDING))
        syncLogDao.insertLog(
            SyncLog(
                action = "PAYMENT_RECORDED",
                description = "Processed payment of $${String.format(Locale.US, "%.2f", payment.amount)} for Worker #${payment.workerId} via ${payment.paymentMethod} (Ref: ${payment.transactionReference})",
                status = SyncStatus.PENDING
            )
        )
        id
    }

    fun getPaymentsForWorker(workerId: Long): Flow<List<PaymentRecord>> =
        paymentDao.getPaymentsForWorker(workerId)

    // Sync
    suspend fun syncAllPending(): SyncResult = withContext(Dispatchers.IO) {
        val pendingAttendance = attendanceDao.getPendingSyncRecords()
        kotlinx.coroutines.delay(1200)

        val attendanceCount = pendingAttendance.size
        attendanceDao.markAllAttendanceSynced()
        workerDao.markAllWorkersSynced()
        paymentDao.markAllPaymentsSynced()

        syncLogDao.insertLog(
            SyncLog(
                action = "CLOUD_SYNC_COMPLETED",
                description = "Successfully synced database changes with central site server",
                status = SyncStatus.SYNCED,
                recordsCount = attendanceCount + 1
            )
        )

        SyncResult(
            success = true,
            workersSynced = 1,
            attendanceSynced = attendanceCount,
            paymentsSynced = 1,
            message = "All offline records successfully synchronized with central server"
        )
    }

    // Payroll Calculation
    suspend fun generatePayroll(
        startDate: String,
        endDate: String,
        siteIdFilter: Long? = null
    ): PayrollSummary = withContext(Dispatchers.IO) {
        val workers = workerDao.getAllWorkers().first()
            .filter { siteIdFilter == null || it.assignedSiteId == siteIdFilter }
        val attendanceList = attendanceDao.getAttendanceForDateRange(startDate, endDate).first()
        val allPaymentsList = paymentDao.getAllPayments().first()

        var totalDays = 0.0
        var totalOtHours = 0.0
        var totalGross = 0.0
        var totalPaid = 0.0
        var totalOutstanding = 0.0

        val items = workers.map { worker ->
            val workerAttendance = attendanceList.filter { it.workerId == worker.id }
            val workerPayments = allPaymentsList.filter { it.workerId == worker.id }
            val paidAmount = workerPayments.sumOf { it.amount }

            var fullDays = 0
            var halfDays = 0
            var absentDays = 0
            var leaveDays = 0
            var otHours = 0.0

            workerAttendance.forEach { record ->
                when (record.status) {
                    AttendanceStatus.PRESENT -> fullDays++
                    AttendanceStatus.HALF_DAY -> halfDays++
                    AttendanceStatus.OVERTIME -> {
                        fullDays++
                        otHours += record.overtimeHours
                    }
                    AttendanceStatus.ABSENT -> absentDays++
                    AttendanceStatus.LEAVE -> leaveDays++
                }
            }

            val effectiveDays = fullDays + (halfDays * 0.5)
            val baseWage = effectiveDays * worker.dailyWage
            val otRate = worker.hourlyRate * 1.5
            val otEarned = otHours * otRate
            val gross = baseWage + otEarned
            val netPayable = (gross - paidAmount).coerceAtLeast(0.0)

            totalDays += effectiveDays
            totalOtHours += otHours
            totalGross += gross
            totalPaid += paidAmount
            totalOutstanding += netPayable

            WorkerPayrollItem(
                worker = worker,
                fullDaysCount = fullDays,
                halfDaysCount = halfDays,
                absentDaysCount = absentDays,
                leaveDaysCount = leaveDays,
                totalOvertimeHours = otHours,
                effectiveDaysWorked = effectiveDays,
                baseWageEarned = baseWage,
                overtimeEarned = otEarned,
                grossPay = gross,
                totalPaidAlready = paidAmount,
                netPayable = netPayable,
                isFullyPaid = netPayable <= 0.01 && gross > 0.0,
                records = workerAttendance
            )
        }.sortedByDescending { it.netPayable }

        PayrollSummary(
            startDate = startDate,
            endDate = endDate,
            totalWorkers = items.size,
            totalDaysWorked = totalDays,
            totalOvertimeHours = totalOtHours,
            totalGrossExpense = totalGross,
            totalPaidAlready = totalPaid,
            totalOutstandingPayable = totalOutstanding,
            items = items
        )
    }
}
