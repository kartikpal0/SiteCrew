package com.example.ui.worker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.AttendanceRecord
import com.example.data.model.AttendanceStatus
import com.example.location.GpsLocationManager
import com.example.ui.AppRole
import com.example.ui.MainViewModel
import com.example.ui.components.GpsLocationStatusBanner
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun WorkerDashboardScreen(
    viewModel: MainViewModel,
    onRequestGpsPermission: () -> Unit
) {
    val worker by viewModel.currentLoggedInWorker.collectAsState()
    val allSites by viewModel.allSites.collectAsState()
    val todayAttendance by viewModel.workerTodayAttendance.collectAsState()
    val pastAttendance by viewModel.workerSelfAttendance.collectAsState()
    val currentGps by viewModel.currentGps.collectAsState()
    val hasGpsPermission by viewModel.hasLocationPermission.collectAsState()

    val assignedSite = remember(allSites, worker) {
        allSites.find { it.id == worker?.assignedSiteId }
    }

    var showEditUpiDialog by remember { mutableStateOf(false) }

    // Calculate worker's earnings
    val effectiveDays = remember(pastAttendance) {
        pastAttendance.sumOf { record ->
            when (record.status) {
                AttendanceStatus.PRESENT -> 1.0
                AttendanceStatus.HALF_DAY -> 0.5
                AttendanceStatus.OVERTIME -> 1.0
                else -> 0.0
            }
        }
    }
    val otHours = remember(pastAttendance) {
        pastAttendance.sumOf { it.overtimeHours }
    }
    val dailyWage = worker?.dailyWage ?: 140.0
    val hourlyRate = worker?.hourlyRate ?: (dailyWage / 8.0)
    val totalEarned = (effectiveDays * dailyWage) + (otHours * hourlyRate * 1.5)

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(BrandPrimaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = worker?.name?.take(1)?.uppercase() ?: "W",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = BrandPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = worker?.name ?: "Worker Portal",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${worker?.trade} • Passkey: ${worker?.joinCodeUsed}",
                                style = MaterialTheme.typography.bodySmall,
                                color = BrandPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { viewModel.selectRole(AppRole.ROLE_SELECT) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Switch Portal", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 48.dp)
        ) {
            // Enterprise Party Banner (Apple Dark UI Card)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = BrandDark),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, BrandBorderDark)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ACTIVE CONSTRUCTION PARTY",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = BrandPrimaryLight,
                                letterSpacing = 1.sp
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF1E293B)
                            ) {
                                Text(
                                    text = "Passkey: ${worker?.joinCodeUsed}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = assignedSite?.name ?: "Construction Project",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Text(
                            text = assignedSite?.address ?: "Site Address",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8)
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = BrandBorderDark)
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Standard Daily Wage", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
                                Text("$${dailyWage.toInt()} / day", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Overtime Rate (1.5x)", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
                                Text("$${String.format(Locale.US, "%.2f", hourlyRate * 1.5)} / hr", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = BrandPrimaryLight)
                            }
                        }
                    }
                }
            }

            // GPS Geofence Telemetry Banner
            item {
                GpsLocationStatusBanner(
                    currentGps = currentGps,
                    selectedSite = assignedSite,
                    hasPermission = hasGpsPermission,
                    onRequestPermission = onRequestGpsPermission,
                    onSimulateSiteLock = {
                        assignedSite?.let { viewModel.simulateGpsLockOnSite(it) }
                    }
                )
            }

            // Today's Clock-In Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Daily Clock-In",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = SimpleDateFormat("EEE, d MMM yyyy", Locale.US).format(Date()),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (todayAttendance != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = BrandEmeraldContainer,
                                border = BorderStroke(1.dp, BrandEmerald.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = BrandEmerald, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Logged: ${todayAttendance?.status?.label?.uppercase()}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = OnBrandEmeraldContainer
                                        )
                                    }
                                    if (todayAttendance?.latitude != null) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "GPS: ${String.format(Locale.US, "%.4f, %.4f", todayAttendance?.latitude, todayAttendance?.longitude)}" +
                                                    (todayAttendance?.distanceFromSiteMeters?.let { " (${GpsLocationManager.formatDistance(it)} from site)" } ?: "") +
                                                    " • Geofence Verified",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = OnBrandEmeraldContainer
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "Record your attendance with verified GPS telemetry. Your daily wage will be credited automatically into payroll.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.workerSelfClockIn(AttendanceStatus.PRESENT) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                                    modifier = Modifier.weight(1.3f).height(46.dp).testTag("worker_clock_in_full_button")
                                ) {
                                    Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Full Day ($${dailyWage.toInt()})", style = MaterialTheme.typography.labelLarge)
                                }

                                OutlinedButton(
                                    onClick = { viewModel.workerSelfClockIn(AttendanceStatus.HALF_DAY) },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                    modifier = Modifier.weight(1f).height(46.dp).testTag("worker_clock_in_half_button")
                                ) {
                                    Text("Half Day", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }

            // Wage Earnings Summary Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    border = BorderStroke(1.dp, BrandPrimary.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Cumulative Gross Earnings",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = OnBrandPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$${String.format(Locale.US, "%,.2f", totalEarned)}",
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = OnBrandPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "$effectiveDays Effective Days Worked",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = OnBrandPrimaryContainer
                            )
                            if (otHours > 0) {
                                Text(
                                    text = "+${otHours}h Overtime",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = OnBrandPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }

            // Online Payout Profile Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Online Disbursal Profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (worker?.upiId.isNullOrBlank()) "No UPI account configured. Add UPI to receive direct wages." else "UPI ID: ${worker?.upiId}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (worker?.upiId.isNullOrBlank()) BrandRose else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = { showEditUpiDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(if (worker?.upiId.isNullOrBlank()) "Configure" else "Edit", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // Attendance History
            item {
                Text(
                    text = "Attendance & Audit History (${pastAttendance.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }

            if (pastAttendance.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No attendance records logged yet.\nClock in above to log your first work day.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(pastAttendance, key = { it.id }) { record ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(record.date, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                if (record.latitude != null) {
                                    Text(
                                        text = "GPS: ${String.format(Locale.US, "%.4f, %.4f", record.latitude, record.longitude)} • ${if (record.isWithinGeofence) "Geofence Verified" else "Perimeter Warning"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = when (record.status) {
                                    AttendanceStatus.PRESENT -> BrandEmeraldContainer
                                    AttendanceStatus.HALF_DAY -> BrandAmberContainer
                                    AttendanceStatus.OVERTIME -> BrandBlueContainer
                                    else -> BrandRoseContainer
                                },
                                border = BorderStroke(
                                    1.dp,
                                    when (record.status) {
                                        AttendanceStatus.PRESENT -> BrandEmerald.copy(alpha = 0.3f)
                                        AttendanceStatus.HALF_DAY -> BrandAmber.copy(alpha = 0.3f)
                                        AttendanceStatus.OVERTIME -> BrandBlue.copy(alpha = 0.3f)
                                        else -> BrandRose.copy(alpha = 0.3f)
                                    }
                                )
                            ) {
                                Text(
                                    text = record.status.label.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = when (record.status) {
                                        AttendanceStatus.PRESENT -> OnBrandEmeraldContainer
                                        AttendanceStatus.HALF_DAY -> OnBrandAmberContainer
                                        AttendanceStatus.OVERTIME -> OnBrandBlueContainer
                                        else -> OnBrandRoseContainer
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit UPI Dialog
    if (showEditUpiDialog) {
        var upiText by remember { mutableStateOf(worker?.upiId ?: "") }
        Dialog(onDismissRequest = { showEditUpiDialog = false }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(22.dp)) {
                    Text("Payment UPI Account", style = MaterialTheme.typography.titleLarge)
                    Text("Site contractor will disburse your verified wages directly to this UPI address.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = upiText,
                        onValueChange = { upiText = it },
                        label = { Text("UPI ID (e.g. name@okhdfcbank)") },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showEditUpiDialog = false }) { Text("Cancel", style = MaterialTheme.typography.labelLarge) }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                worker?.let {
                                    viewModel.updateWorker(it.copy(upiId = upiText.trim()))
                                }
                                showEditUpiDialog = false
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Save UPI Account", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}
