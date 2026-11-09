package com.example.ui.attendance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.AttendanceRecord
import com.example.data.model.AttendanceStatus
import com.example.data.model.ConstructionSite
import com.example.data.model.Worker
import com.example.location.GpsLocationData
import com.example.location.GpsLocationManager
import com.example.ui.MainViewModel
import com.example.ui.components.GpsLocationStatusBanner
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AttendanceScreen(
    viewModel: MainViewModel,
    onRequestGpsPermission: () -> Unit
) {
    val selectedSite by viewModel.selectedSite.collectAsState()
    val allWorkers by viewModel.allWorkers.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val attendanceRecords by viewModel.currentAttendance.collectAsState()
    val currentGps by viewModel.currentGps.collectAsState()
    val hasGpsPermission by viewModel.hasLocationPermission.collectAsState()

    val siteWorkers = remember(allWorkers, selectedSite) {
        if (selectedSite == null) allWorkers.filter { it.isActive }
        else allWorkers.filter { it.assignedSiteId == selectedSite?.id && it.isActive }
    }

    val attendanceMap = remember(attendanceRecords) {
        attendanceRecords.associateBy { it.workerId }
    }

    var selectedWorkerForOt by remember { mutableStateOf<Worker?>(null) }
    var selectedWorkerForDetails by remember { mutableStateOf<Pair<Worker, AttendanceRecord?>?>(null) }
    var showBatchRollCallConfirm by remember { mutableStateOf(false) }

    val fullDaysCount = attendanceRecords.count { it.status == AttendanceStatus.PRESENT }
    val halfDaysCount = attendanceRecords.count { it.status == AttendanceStatus.HALF_DAY }
    val otCount = attendanceRecords.count { it.status == AttendanceStatus.OVERTIME }
    val absentCount = attendanceRecords.count { it.status == AttendanceStatus.ABSENT }

    Scaffold { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            // GPS Geofence status card
            item {
                GpsLocationStatusBanner(
                    currentGps = currentGps,
                    selectedSite = selectedSite,
                    hasPermission = hasGpsPermission,
                    onRequestPermission = onRequestGpsPermission,
                    onSimulateSiteLock = {
                        selectedSite?.let { viewModel.simulateGpsLockOnSite(it) }
                    }
                )
            }

            // Date selector strip
            item {
                DateSelectorStrip(
                    selectedDate = selectedDate,
                    onDateSelected = { viewModel.selectDate(it) }
                )
            }

            // Attendance Summary Metrics
            item {
                AttendanceMetricsBar(
                    totalCrew = siteWorkers.size,
                    presentCount = fullDaysCount,
                    halfDaysCount = halfDaysCount,
                    otCount = otCount,
                    absentCount = absentCount
                )
            }

            // Quick Roll-Call Action Row
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Site Roll Call Engine",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Audit all on-site crew with instant GPS timestamp",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = { showBatchRollCallConfirm = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrandPrimary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("batch_present_button")
                        ) {
                            Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Mark All Present", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // Section Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Roster Crew (${siteWorkers.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = selectedSite?.name ?: "All Sites",
                        style = MaterialTheme.typography.labelMedium,
                        color = BrandPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Workers List
            if (siteWorkers.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.GroupOff,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "No workers in this party yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Generate a private join code under 'Workers & Codes' to onboard your crew.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(siteWorkers, key = { it.id }) { worker ->
                    val record = attendanceMap[worker.id]
                    WorkerAttendanceRowCard(
                        worker = worker,
                        record = record,
                        onStatusClick = { status ->
                            if (status == AttendanceStatus.OVERTIME) {
                                selectedWorkerForOt = worker
                            } else {
                                viewModel.markWorkerAttendance(worker, status)
                            }
                        },
                        onCardClick = {
                            selectedWorkerForDetails = Pair(worker, record)
                        }
                    )
                }
            }
        }
    }

    // Overtime Hours Dialog
    selectedWorkerForOt?.let { worker ->
        OvertimeDialog(
            worker = worker,
            onDismiss = { selectedWorkerForOt = null },
            onConfirm = { otHours, notes ->
                viewModel.markWorkerAttendance(
                    worker = worker,
                    status = AttendanceStatus.OVERTIME,
                    overtimeHours = otHours,
                    notes = notes
                )
                selectedWorkerForOt = null
            }
        )
    }

    // Attendance Record Details Dialog
    selectedWorkerForDetails?.let { (worker, record) ->
        AttendanceDetailsDialog(
            worker = worker,
            record = record,
            selectedSite = selectedSite,
            onDismiss = { selectedWorkerForDetails = null },
            onMarkStatus = { status ->
                if (status == AttendanceStatus.OVERTIME) {
                    selectedWorkerForOt = worker
                } else {
                    viewModel.markWorkerAttendance(worker, status)
                }
                selectedWorkerForDetails = null
            }
        )
    }

    // Batch Roll-Call Confirmation
    if (showBatchRollCallConfirm) {
        AlertDialog(
            onDismissRequest = { showBatchRollCallConfirm = false },
            icon = { Icon(Icons.Default.DoneAll, contentDescription = null, tint = BrandPrimary) },
            title = { Text("Confirm Batch Roll Call", style = MaterialTheme.typography.titleMedium) },
            text = {
                Text(
                    "Log all ${siteWorkers.size} workers at '${selectedSite?.name ?: "this party"}' as PRESENT (Full Day) for $selectedDate? Precise GPS coordinates will be recorded for audit trail.",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.batchMarkAllAssignedWorkers(AttendanceStatus.PRESENT)
                        showBatchRollCallConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                ) {
                    Text("Confirm All Present", style = MaterialTheme.typography.labelSmall)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchRollCallConfirm = false }) {
                    Text("Cancel", style = MaterialTheme.typography.labelSmall)
                }
            }
        )
    }
}

@Composable
fun DateSelectorStrip(
    selectedDate: String,
    onDateSelected: (String) -> Unit
) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val dayOfWeekFormat = SimpleDateFormat("EEE", Locale.US)
    val dayNumberFormat = SimpleDateFormat("d", Locale.US)
    val calendar = Calendar.getInstance()

    val dateOptions = remember {
        val list = mutableListOf<Triple<String, String, String>>()
        for (i in 0..6) {
            calendar.time = Date()
            calendar.add(Calendar.DAY_OF_YEAR, -i)
            val dateKey = dateFormat.format(calendar.time)
            val dow = if (i == 0) "TODAY" else dayOfWeekFormat.format(calendar.time).uppercase()
            val dayNum = dayNumberFormat.format(calendar.time)
            list.add(Triple(dateKey, dow, dayNum))
        }
        list
    }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(dateOptions) { (dateKey, dow, dayNum) ->
            val isSelected = dateKey == selectedDate
            Surface(
                onClick = { onDateSelected(dateKey) },
                shape = RoundedCornerShape(14.dp),
                color = if (isSelected) BrandDark else MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    if (isSelected) BrandDark else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                ),
                modifier = Modifier
                    .width(62.dp)
                    .height(68.dp)
                    .testTag("date_chip_$dateKey")
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = dow,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) BrandPrimaryLight else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = dayNum,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun AttendanceMetricsBar(
    totalCrew: Int,
    presentCount: Int,
    halfDaysCount: Int,
    otCount: Int,
    absentCount: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            MetricItem(label = "Present", count = presentCount, color = BrandEmerald)
            MetricDivider()
            MetricItem(label = "Half-Day", count = halfDaysCount, color = BrandAmber)
            MetricDivider()
            MetricItem(label = "Overtime", count = otCount, color = BrandBlue)
            MetricDivider()
            MetricItem(label = "Absent", count = absentCount, color = BrandRose)
            MetricDivider()
            MetricItem(label = "Crew Size", count = totalCrew, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun MetricDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(30.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    )
}

@Composable
private fun MetricItem(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$count",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun WorkerAttendanceRowCard(
    worker: Worker,
    record: AttendanceRecord?,
    onStatusClick: (AttendanceStatus) -> Unit,
    onCardClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onCardClick() }
            .testTag("worker_card_${worker.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Worker identity row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Monogram avatar
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = worker.name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString(""),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = worker.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = BrandPrimaryContainer
                        ) {
                            Text(
                                text = "$${worker.dailyWage.toInt()}/day",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = OnBrandPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "${worker.trade} • Code: ${worker.joinCodeUsed}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Current status badge
                if (record != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when (record.status) {
                            AttendanceStatus.PRESENT -> BrandEmeraldContainer
                            AttendanceStatus.HALF_DAY -> BrandAmberContainer
                            AttendanceStatus.OVERTIME -> BrandBlueContainer
                            AttendanceStatus.ABSENT -> BrandRoseContainer
                            AttendanceStatus.LEAVE -> Color(0xFFF3E8FF)
                        },
                        border = BorderStroke(
                            1.dp,
                            when (record.status) {
                                AttendanceStatus.PRESENT -> BrandEmerald.copy(alpha = 0.3f)
                                AttendanceStatus.HALF_DAY -> BrandAmber.copy(alpha = 0.3f)
                                AttendanceStatus.OVERTIME -> BrandBlue.copy(alpha = 0.3f)
                                AttendanceStatus.ABSENT -> BrandRose.copy(alpha = 0.3f)
                                AttendanceStatus.LEAVE -> Color(0xFFC084FC)
                            }
                        )
                    ) {
                        Text(
                            text = if (record.status == AttendanceStatus.OVERTIME) "+${record.overtimeHours}h OT" else record.status.label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = when (record.status) {
                                AttendanceStatus.PRESENT -> OnBrandEmeraldContainer
                                AttendanceStatus.HALF_DAY -> OnBrandAmberContainer
                                AttendanceStatus.OVERTIME -> OnBrandBlueContainer
                                AttendanceStatus.ABSENT -> OnBrandRoseContainer
                                AttendanceStatus.LEAVE -> Color(0xFF6B21A8)
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "NOT LOGGED",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // GPS log info if recorded
            if (record?.latitude != null && record.longitude != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "GPS Logged",
                        tint = if (record.isWithinGeofence) BrandEmerald else BrandRose,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "GPS: ${String.format(Locale.US, "%.4f, %.4f", record.latitude, record.longitude)}" +
                                (record.distanceFromSiteMeters?.let { " • ${GpsLocationManager.formatDistance(it)} from center" } ?: "") +
                                (if (record.isWithinGeofence) " • Geofence Verified" else " • Perimeter Warning"),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (record.isWithinGeofence) MaterialTheme.colorScheme.onSurfaceVariant else BrandRose
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Status Segmented Pill Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                StatusSegmentButton(
                    label = "Present",
                    isSelected = record?.status == AttendanceStatus.PRESENT,
                    color = BrandEmerald,
                    modifier = Modifier.weight(1f),
                    onClick = { onStatusClick(AttendanceStatus.PRESENT) }
                )
                StatusSegmentButton(
                    label = "Half Day",
                    isSelected = record?.status == AttendanceStatus.HALF_DAY,
                    color = BrandAmber,
                    modifier = Modifier.weight(1f),
                    onClick = { onStatusClick(AttendanceStatus.HALF_DAY) }
                )
                StatusSegmentButton(
                    label = "Overtime",
                    isSelected = record?.status == AttendanceStatus.OVERTIME,
                    color = BrandBlue,
                    modifier = Modifier.weight(1f),
                    onClick = { onStatusClick(AttendanceStatus.OVERTIME) }
                )
                StatusSegmentButton(
                    label = "Absent",
                    isSelected = record?.status == AttendanceStatus.ABSENT,
                    color = BrandRose,
                    modifier = Modifier.weight(1f),
                    onClick = { onStatusClick(AttendanceStatus.ABSENT) }
                )
            }
        }
    }
}

@Composable
private fun StatusSegmentButton(
    label: String,
    isSelected: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) color else Color.Transparent,
        modifier = modifier.height(32.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun OvertimeDialog(
    worker: Worker,
    onDismiss: () -> Unit,
    onConfirm: (Double, String) -> Unit
) {
    var otHoursText by remember { mutableStateOf("2.0") }
    var notes by remember { mutableStateOf("") }
    val hourlyRate = worker.hourlyRate
    val otRate = hourlyRate * 1.5

    val otHours = otHoursText.toDoubleOrNull() ?: 0.0
    val totalOtEarnings = otHours * otRate

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(BrandBlueContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreTime,
                            contentDescription = null,
                            tint = BrandBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Record Overtime",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = worker.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Standard Hourly Rate: $${String.format(Locale.US, "%.2f", hourlyRate)} | OT Rate (1.5x): $${String.format(Locale.US, "%.2f", otRate)}/hr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Quick buttons for hours
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(1.0, 1.5, 2.0, 3.0, 4.0).forEach { hours ->
                        FilterChip(
                            selected = otHoursText == hours.toString(),
                            onClick = { otHoursText = hours.toString() },
                            label = { Text("${hours}h", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = otHoursText,
                    onValueChange = { otHoursText = it },
                    label = { Text("Overtime Hours") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Task Notes (Optional)") },
                    placeholder = { Text("e.g. Concrete pour completion") },
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Calculated earnings preview
                Surface(
                    color = BrandBlueContainer,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BrandBlue.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Additional OT Pay:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = OnBrandBlueContainer
                        )
                        Text(
                            text = "+$${String.format(Locale.US, "%.2f", totalOtEarnings)}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = OnBrandBlueContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(otHours, notes) },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                    ) {
                        Text("Save Overtime", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
fun AttendanceDetailsDialog(
    worker: Worker,
    record: AttendanceRecord?,
    selectedSite: ConstructionSite?,
    onDismiss: () -> Unit,
    onMarkStatus: (AttendanceStatus) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(BrandPrimaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = BrandPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Attendance Audit Log",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                DetailItem("Worker", worker.name)
                DetailItem("Specialty / Trade", worker.trade)
                DetailItem("Join Code Used", worker.joinCodeUsed)
                DetailItem("Daily Base Wage", "$${worker.dailyWage.toInt()} ($${String.format(Locale.US, "%.2f", worker.hourlyRate)}/h)")
                DetailItem("Construction Party", selectedSite?.name ?: "Site #${worker.assignedSiteId}")

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                if (record != null) {
                    DetailItem("Attendance Status", record.status.label)
                    if (record.status == AttendanceStatus.OVERTIME) {
                        DetailItem("Overtime Hours", "${record.overtimeHours} hrs")
                    }
                    if (record.latitude != null && record.longitude != null) {
                        DetailItem("GPS Coordinates", "${record.latitude}, ${record.longitude}")
                        DetailItem("Location Accuracy", "±${record.locationAccuracy?.toInt() ?: 0} meters")
                        DetailItem("Distance from Center", record.distanceFromSiteMeters?.let { GpsLocationManager.formatDistance(it) } ?: "N/A")
                        DetailItem("Geofence Verification", if (record.isWithinGeofence) "PASSED (Within Perimeter)" else "OUTSIDE BOUNDARY")
                    }
                    if (record.notes.isNotBlank()) {
                        DetailItem("Field Notes", record.notes)
                    }
                    DetailItem("Sync Status", record.syncStatus.name)
                } else {
                    Text(
                        text = "No attendance has been logged for this worker on this date yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Done", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}
