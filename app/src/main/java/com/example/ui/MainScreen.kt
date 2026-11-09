package com.example.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.attendance.AttendanceScreen
import com.example.ui.auth.AuthAndPartyScreen
import com.example.ui.components.CreatePartyDialog
import com.example.ui.components.SiteCrewBottomNav
import com.example.ui.components.SiteCrewTopBar
import com.example.ui.payments.PaymentsScreen
import com.example.ui.payroll.PayrollScreen
import com.example.ui.sync.SyncCenterScreen
import com.example.ui.worker.WorkerDashboardScreen
import com.example.ui.workers.WorkersScreen

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val currentRole by viewModel.currentRole.collectAsState()
    val loggedInAdmin by viewModel.loggedInAdmin.collectAsState()
    val currentLoggedInWorker by viewModel.currentLoggedInWorker.collectAsState()
    val currentScreen by viewModel.currentScreen.collectAsState()
    val selectedSite by viewModel.selectedSite.collectAsState()
    val allSites by viewModel.allSites.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsState()
    val currentGps by viewModel.currentGps.collectAsState()

    var showCreatePartyDialog by remember { mutableStateOf(false) }

    // Location permission launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        viewModel.updatePermissionStatus(fineGranted || coarseGranted)
    }

    LaunchedEffect(Unit) {
        if (!viewModel.hasLocationPermission.value) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Role-based routing
    when (currentRole) {
        AppRole.ROLE_SELECT -> {
            AuthAndPartyScreen(viewModel = viewModel)
        }
        AppRole.WORKER -> {
            WorkerDashboardScreen(
                viewModel = viewModel,
                onRequestGpsPermission = {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            )
        }
        AppRole.ADMIN -> {
            // Admin portal
            BackHandler(enabled = currentScreen != AppScreen.ATTENDANCE) {
                viewModel.setScreen(AppScreen.ATTENDANCE)
            }

            Scaffold(
                topBar = {
                    SiteCrewTopBar(
                        selectedSite = selectedSite,
                        allSites = allSites,
                        onSelectSite = { viewModel.selectSite(it) },
                        onCreatePartyClick = { showCreatePartyDialog = true },
                        adminName = loggedInAdmin?.name,
                        onSwitchRoleClick = { viewModel.selectRole(AppRole.ROLE_SELECT) },
                        isOnline = isOnline,
                        pendingSyncCount = pendingSyncCount,
                        onSyncClick = { viewModel.setScreen(AppScreen.SYNC_CENTER) }
                    )
                },
                bottomBar = {
                    SiteCrewBottomNav(
                        currentScreen = currentScreen,
                        onScreenSelected = { viewModel.setScreen(it) },
                        pendingSyncCount = pendingSyncCount
                    )
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    if (allSites.isEmpty()) {
                        // Empty State: Prompt Admin to Create First Party
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 480.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(CircleShape)
                                            .background(com.example.ui.theme.BrandPrimary.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Apartment,
                                            contentDescription = null,
                                            modifier = Modifier.size(38.dp),
                                            tint = com.example.ui.theme.BrandPrimary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(18.dp))
                                    Text(
                                        text = "Welcome, ${loggedInAdmin?.name ?: "Admin"}!",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "You don't have any construction parties set up yet. Create your first party to define site GPS geofencing, log crew attendance, and generate private passkeys for each worker.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        lineHeight = 20.sp
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Button(
                                        onClick = { showCreatePartyDialog = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.BrandPrimary),
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                            .testTag("create_first_party_button")
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "Create Construction Party",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        when (currentScreen) {
                            AppScreen.ATTENDANCE -> {
                                AttendanceScreen(
                                    viewModel = viewModel,
                                    onRequestGpsPermission = {
                                        locationPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    }
                                )
                            }
                            AppScreen.WORKERS -> {
                                WorkersScreen(viewModel = viewModel)
                            }
                            AppScreen.PAYROLL -> {
                                PayrollScreen(viewModel = viewModel)
                            }
                            AppScreen.PAYMENTS -> {
                                PaymentsScreen(viewModel = viewModel)
                            }
                            AppScreen.SYNC_CENTER -> {
                                SyncCenterScreen(viewModel = viewModel)
                            }
                        }
                    }
                }
            }
        }
    }

    // Create Party Dialog
    if (showCreatePartyDialog) {
        CreatePartyDialog(
            currentGps = currentGps,
            onDismiss = { showCreatePartyDialog = false },
            onCreateParty = { name, client, address, lat, lon, radius, defaultWage ->
                viewModel.createParty(
                    name = name,
                    clientName = client,
                    address = address,
                    latitude = lat,
                    longitude = lon,
                    geofenceRadius = radius,
                    defaultDailyWage = defaultWage,
                    onSuccess = {
                        showCreatePartyDialog = false
                    }
                )
            }
        )
    }
}
