package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.*
import com.example.data.repository.*
import com.example.location.GpsLocationData
import com.example.location.GpsLocationManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class AppRole {
    ROLE_SELECT,
    ADMIN,
    WORKER
}

enum class AppScreen(val title: String) {
    ATTENDANCE("Attendance"),
    WORKERS("Workers & Codes"),
    PAYROLL("Payroll"),
    PAYMENTS("Payments"),
    SYNC_CENTER("Sync Center")
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application, viewModelScope)
    val repository = SiteCrewRepository(database)
    val networkMonitor = NetworkMonitor(application)
    val gpsManager = GpsLocationManager(application)

    // Current Role
    private val _currentRole = MutableStateFlow(AppRole.ROLE_SELECT)
    val currentRole: StateFlow<AppRole> = _currentRole.asStateFlow()

    // Admin Session
    val loggedInAdmin: StateFlow<AdminProfile?> = repository.loggedInAdmin
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _adminAuthError = MutableStateFlow<String?>(null)
    val adminAuthError: StateFlow<String?> = _adminAuthError.asStateFlow()

    // Worker Session
    private val _currentLoggedInWorker = MutableStateFlow<Worker?>(null)
    val currentLoggedInWorker: StateFlow<Worker?> = _currentLoggedInWorker.asStateFlow()

    private val _workerJoinError = MutableStateFlow<String?>(null)
    val workerJoinError: StateFlow<String?> = _workerJoinError.asStateFlow()

    private val _workerJoinSuccess = MutableStateFlow<String?>(null)
    val workerJoinSuccess: StateFlow<String?> = _workerJoinSuccess.asStateFlow()

    // Connectivity & Sync
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val pendingSyncCount: StateFlow<Int> = repository.pendingSyncCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val allSites: StateFlow<List<ConstructionSite>> = repository.allSites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allWorkers: StateFlow<List<Worker>> = repository.allWorkers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeWorkers: StateFlow<List<Worker>> = repository.activeWorkers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPayments: StateFlow<List<PaymentRecord>> = repository.allPayments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val syncLogs: StateFlow<List<SyncLog>> = repository.syncLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentScreen = MutableStateFlow(AppScreen.ATTENDANCE)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _selectedSite = MutableStateFlow<ConstructionSite?>(null)
    val selectedSite: StateFlow<ConstructionSite?> = _selectedSite.asStateFlow()

    // Invites (Private Join Codes) for the selected party
    val partyInvites: StateFlow<List<PartyWorkerInvite>> = _selectedSite
        .flatMapLatest { site ->
            if (site != null) repository.getInvitesForParty(site.id)
            else flowOf(emptyList())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val _selectedDate = MutableStateFlow(dateFormat.format(Date()))
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    private val _currentGps = MutableStateFlow<GpsLocationData?>(null)
    val currentGps: StateFlow<GpsLocationData?> = _currentGps.asStateFlow()

    private val _hasLocationPermission = MutableStateFlow(gpsManager.hasLocationPermission())
    val hasLocationPermission: StateFlow<Boolean> = _hasLocationPermission.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    // Payroll filter state
    private val _payrollStartDate = MutableStateFlow(getDaysAgoDate(7))
    val payrollStartDate: StateFlow<String> = _payrollStartDate.asStateFlow()

    private val _payrollEndDate = MutableStateFlow(dateFormat.format(Date()))
    val payrollEndDate: StateFlow<String> = _payrollEndDate.asStateFlow()

    private val _payrollSiteFilter = MutableStateFlow<Long?>(null)
    val payrollSiteFilter: StateFlow<Long?> = _payrollSiteFilter.asStateFlow()

    private val _payrollSummary = MutableStateFlow<PayrollSummary?>(null)
    val payrollSummary: StateFlow<PayrollSummary?> = _payrollSummary.asStateFlow()

    // Attendance records for current selected site and date
    val currentAttendance: StateFlow<List<AttendanceRecord>> = combine(
        selectedSite,
        selectedDate
    ) { site, date ->
        Pair(site?.id, date)
    }.flatMapLatest { (siteId, date) ->
        if (siteId != null) {
            repository.getAttendanceForSiteAndDate(siteId, date)
        } else {
            repository.getAttendanceForDate(date)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Worker's own attendance records if in Worker Mode
    val workerSelfAttendance: StateFlow<List<AttendanceRecord>> = _currentLoggedInWorker
        .flatMapLatest { worker ->
            if (worker != null) repository.getAttendanceForWorkerAll(worker.id)
            else flowOf(emptyList())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val workerTodayAttendance: StateFlow<AttendanceRecord?> = combine(
        _currentLoggedInWorker,
        _selectedDate
    ) { worker, date ->
        Pair(worker?.id, date)
    }.flatMapLatest { (workerId, date) ->
        if (workerId != null) repository.observeAttendanceForWorkerAndDate(workerId, date)
        else flowOf(null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        // Auto-select first site if available
        viewModelScope.launch {
            allSites.collect { sites ->
                if (_selectedSite.value == null && sites.isNotEmpty()) {
                    _selectedSite.value = sites.first()
                } else if (_selectedSite.value != null && !sites.any { it.id == _selectedSite.value?.id }) {
                    _selectedSite.value = sites.firstOrNull()
                }
            }
        }

        // Auto-switch to Admin role if an admin is already logged in
        viewModelScope.launch {
            loggedInAdmin.collect { admin ->
                if (admin != null && _currentRole.value == AppRole.ROLE_SELECT) {
                    _currentRole.value = AppRole.ADMIN
                }
            }
        }

        startLocationUpdates()
        refreshPayroll()
    }

    // Role switching
    fun selectRole(role: AppRole) {
        _currentRole.value = role
        _adminAuthError.value = null
        _workerJoinError.value = null
    }

    // Admin Login / Registration
    fun loginAdmin(phone: String, pin: String) {
        viewModelScope.launch {
            val admin = repository.loginAdmin(phone, pin)
            if (admin != null) {
                _adminAuthError.value = null
                _currentRole.value = AppRole.ADMIN
            } else {
                _adminAuthError.value = "Invalid phone number or PIN. Please check credentials or register."
            }
        }
    }

    fun registerAdmin(name: String, phone: String, pin: String, companyName: String) {
        viewModelScope.launch {
            if (name.isBlank() || phone.isBlank() || pin.length < 4) {
                _adminAuthError.value = "Please enter your name, phone and a 4-digit PIN."
                return@launch
            }
            repository.registerAdmin(name, phone, pin, companyName)
            _adminAuthError.value = null
            _currentRole.value = AppRole.ADMIN
        }
    }

    fun logoutAdmin() {
        viewModelScope.launch {
            repository.logoutAdmin()
            _currentRole.value = AppRole.ROLE_SELECT
        }
    }

    fun logoutWorker() {
        _currentLoggedInWorker.value = null
        _currentRole.value = AppRole.ROLE_SELECT
    }

    // Party Creation
    fun createParty(
        name: String,
        clientName: String,
        address: String,
        latitude: Double,
        longitude: Double,
        geofenceRadius: Double,
        defaultDailyWage: Double,
        onSuccess: (Long) -> Unit
    ) {
        viewModelScope.launch {
            val id = repository.createParty(
                name = name,
                clientName = clientName,
                address = address,
                latitude = latitude,
                longitude = longitude,
                geofenceRadius = geofenceRadius,
                defaultWage = defaultDailyWage
            )
            // Auto-select newly created site
            val createdSite = ConstructionSite(
                id = id,
                name = name,
                clientName = clientName,
                address = address,
                latitude = latitude,
                longitude = longitude,
                geofenceRadiusMeters = geofenceRadius,
                defaultDailyWage = defaultDailyWage
            )
            _selectedSite.value = createdSite
            onSuccess(id)
        }
    }

    fun deleteParty(siteId: Long) {
        viewModelScope.launch {
            repository.deleteParty(siteId)
            _selectedSite.value = allSites.value.firstOrNull { it.id != siteId }
        }
    }

    // Generate Worker Join Code (Only visible to admin, different for each worker/entry)
    fun generateWorkerJoinCode(
        trade: String,
        dailyWage: Double,
        workerNamePlaceholder: String = ""
    ) {
        val site = _selectedSite.value ?: return
        viewModelScope.launch {
            repository.generateWorkerJoinCode(
                siteId = site.id,
                trade = trade,
                dailyWage = dailyWage,
                workerName = workerNamePlaceholder
            )
        }
    }

    fun deleteInvite(invite: PartyWorkerInvite) {
        viewModelScope.launch {
            repository.deleteInvite(invite)
        }
    }

    // Worker joins a party using unique code
    fun joinPartyWithCode(
        code: String,
        name: String,
        phone: String,
        trade: String?,
        upiId: String
    ) {
        viewModelScope.launch {
            _workerJoinError.value = null
            _workerJoinSuccess.value = null

            if (code.isBlank()) {
                _workerJoinError.value = "Please enter the Party Join Code given by your Admin."
                return@launch
            }
            if (name.isBlank() || phone.isBlank()) {
                _workerJoinError.value = "Please enter your full name and phone number."
                return@launch
            }

            val result = repository.joinPartyWithCode(
                code = code,
                workerName = name,
                phone = phone,
                tradeOverride = trade,
                upiId = upiId
            )

            result.onSuccess { worker ->
                _currentLoggedInWorker.value = worker
                _currentRole.value = AppRole.WORKER
                _workerJoinSuccess.value = "Successfully joined party! Welcome to the crew."
                refreshPayroll()
            }.onFailure { error ->
                _workerJoinError.value = error.message ?: "Failed to join party. Please check the code."
            }
        }
    }

    // Returning worker quick sign-in by phone
    fun loginWorkerByPhone(phone: String) {
        viewModelScope.launch {
            val worker = repository.getWorkerByPhone(phone)
            if (worker != null) {
                _currentLoggedInWorker.value = worker
                _currentRole.value = AppRole.WORKER
                _workerJoinError.value = null
            } else {
                _workerJoinError.value = "No joined worker found for phone '$phone'. Please enter your Join Code first."
            }
        }
    }

    // Worker Self-Clock-In with GPS
    fun workerSelfClockIn(status: AttendanceStatus, otHours: Double = 0.0, notes: String = "") {
        val worker = _currentLoggedInWorker.value ?: return
        markWorkerAttendance(worker, status, otHours, notes)
    }

    // Admin Screen Navigation & Operations
    fun setScreen(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun selectSite(site: ConstructionSite) {
        _selectedSite.value = site
    }

    fun selectDate(date: String) {
        _selectedDate.value = date
    }

    fun updatePermissionStatus(granted: Boolean) {
        _hasLocationPermission.value = granted
        if (granted) {
            startLocationUpdates()
        }
    }

    private fun startLocationUpdates() {
        if (!_hasLocationPermission.value) return
        viewModelScope.launch {
            gpsManager.getLocationUpdates().collect { loc ->
                if (loc != null) {
                    _currentGps.value = loc
                }
            }
        }
    }

    fun simulateGpsLockOnSite(site: ConstructionSite) {
        _currentGps.value = GpsLocationData(
            latitude = site.latitude + (Math.random() - 0.5) * 0.0002,
            longitude = site.longitude + (Math.random() - 0.5) * 0.0002,
            accuracy = 3.5f,
            timestamp = System.currentTimeMillis()
        )
    }

    fun markWorkerAttendance(
        worker: Worker,
        status: AttendanceStatus,
        overtimeHours: Double = 0.0,
        notes: String = ""
    ) {
        viewModelScope.launch {
            val site = _selectedSite.value ?: allSites.value.find { it.id == worker.assignedSiteId }
            val gps = _currentGps.value

            val distanceMeters = if (gps != null && site != null) {
                GpsLocationManager.computeDistanceMeters(
                    gps.latitude,
                    gps.longitude,
                    site.latitude,
                    site.longitude
                )
            } else null

            val isWithinGeofence = if (distanceMeters != null && site != null) {
                distanceMeters <= site.geofenceRadiusMeters
            } else true

            val record = AttendanceRecord(
                workerId = worker.id,
                siteId = site?.id ?: worker.assignedSiteId,
                date = _selectedDate.value,
                status = status,
                checkInTimestamp = System.currentTimeMillis(),
                latitude = gps?.latitude,
                longitude = gps?.longitude,
                locationAccuracy = gps?.accuracy,
                distanceFromSiteMeters = distanceMeters,
                isWithinGeofence = isWithinGeofence,
                overtimeHours = overtimeHours,
                notes = notes,
                syncStatus = SyncStatus.PENDING
            )
            repository.markAttendance(record)
            refreshPayroll()
        }
    }

    fun batchMarkAllAssignedWorkers(status: AttendanceStatus) {
        viewModelScope.launch {
            val site = _selectedSite.value ?: return@launch
            val siteWorkers = allWorkers.value.filter { it.assignedSiteId == site.id && it.isActive }
            val gps = _currentGps.value

            val distanceMeters = if (gps != null) {
                GpsLocationManager.computeDistanceMeters(
                    gps.latitude,
                    gps.longitude,
                    site.latitude,
                    site.longitude
                )
            } else null

            val isWithinGeofence = if (distanceMeters != null) {
                distanceMeters <= site.geofenceRadiusMeters
            } else true

            val records = siteWorkers.map { worker ->
                AttendanceRecord(
                    workerId = worker.id,
                    siteId = site.id,
                    date = _selectedDate.value,
                    status = status,
                    checkInTimestamp = System.currentTimeMillis(),
                    latitude = gps?.latitude,
                    longitude = gps?.longitude,
                    locationAccuracy = gps?.accuracy,
                    distanceFromSiteMeters = distanceMeters,
                    isWithinGeofence = isWithinGeofence,
                    overtimeHours = 0.0,
                    syncStatus = SyncStatus.PENDING
                )
            }
            repository.batchMarkAttendance(records)
            refreshPayroll()
        }
    }

    fun addWorker(
        name: String,
        phone: String,
        trade: String,
        dailyWage: Double,
        siteId: Long,
        upiId: String,
        bankAccount: String,
        bankIfsc: String,
        emergencyContact: String
    ) {
        viewModelScope.launch {
            val worker = Worker(
                name = name.trim(),
                phone = phone.trim(),
                trade = trade,
                dailyWage = dailyWage,
                hourlyRate = dailyWage / 8.0,
                assignedSiteId = siteId,
                upiId = upiId.trim(),
                bankAccount = bankAccount.trim(),
                bankIfsc = bankIfsc.trim().uppercase(),
                emergencyContact = emergencyContact.trim(),
                syncStatus = SyncStatus.PENDING
            )
            repository.addWorker(worker)
            refreshPayroll()
        }
    }

    fun updateWorker(worker: Worker) {
        viewModelScope.launch {
            repository.updateWorker(worker)
            refreshPayroll()
        }
    }

    fun removeWorker(workerId: Long, permanent: Boolean = false) {
        viewModelScope.launch {
            repository.removeWorker(workerId, permanent)
            refreshPayroll()
        }
    }

    fun setPayrollRange(startDate: String, endDate: String, siteFilter: Long? = null) {
        _payrollStartDate.value = startDate
        _payrollEndDate.value = endDate
        _payrollSiteFilter.value = siteFilter
        refreshPayroll()
    }

    fun refreshPayroll() {
        viewModelScope.launch {
            val summary = repository.generatePayroll(
                _payrollStartDate.value,
                _payrollEndDate.value,
                _payrollSiteFilter.value
            )
            _payrollSummary.value = summary
        }
    }

    fun triggerSyncNow() {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val result = repository.syncAllPending()
                _syncMessage.value = result.message
            } catch (e: Exception) {
                _syncMessage.value = "Sync error: ${e.localizedMessage}"
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    fun processPayment(
        worker: Worker,
        amount: Double,
        paymentMethod: String,
        notes: String,
        onSuccess: (PaymentRecord) -> Unit
    ) {
        viewModelScope.launch {
            val txnRef = "TXN-" + System.currentTimeMillis().toString().takeLast(8) + "-" + (1000..9999).random()
            val payment = PaymentRecord(
                workerId = worker.id,
                amount = amount,
                paymentDate = System.currentTimeMillis(),
                paymentMethod = paymentMethod,
                transactionReference = txnRef,
                notes = notes,
                syncStatus = SyncStatus.PENDING
            )
            repository.recordPayment(payment)
            refreshPayroll()
            onSuccess(payment)
        }
    }

    companion object {
        fun getDaysAgoDate(days: Int): String {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, -days)
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            return format.format(calendar.time)
        }
    }
}
