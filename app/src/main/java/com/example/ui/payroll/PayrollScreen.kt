package com.example.ui.payroll

import android.content.Context
import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.AttendanceRecord
import com.example.data.model.Worker
import com.example.data.repository.PayrollSummary
import com.example.data.repository.WorkerPayrollItem
import com.example.ui.MainViewModel
import com.example.ui.payments.PaymentDialog
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PayrollScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val payrollSummary by viewModel.payrollSummary.collectAsState()
    val allSites by viewModel.allSites.collectAsState()
    val startDate by viewModel.payrollStartDate.collectAsState()
    val endDate by viewModel.payrollEndDate.collectAsState()
    val selectedSiteId by viewModel.payrollSiteFilter.collectAsState()

    var workerForPayment by remember { mutableStateOf<WorkerPayrollItem?>(null) }
    var workerForDetailedSlip by remember { mutableStateOf<WorkerPayrollItem?>(null) }

    val rangePresets = listOf(
        Pair("Past 7 Days", 7),
        Pair("Past 14 Days", 14),
        Pair("Past 30 Days", 30),
        Pair("This Month", 31)
    )

    Scaffold { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            // Header
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Payroll & Disbursals",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Period: $startDate to $endDate",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            FilledTonalButton(
                                onClick = {
                                    payrollSummary?.let { summary ->
                                        sharePayrollReport(context, summary)
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(36.dp).testTag("export_payroll_button")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export Audit", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Range selector chips
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(rangePresets) { (label, days) ->
                                val targetStart = MainViewModel.getDaysAgoDate(days)
                                val isSelected = startDate == targetStart
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                                        viewModel.setPayrollRange(targetStart, today, selectedSiteId)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Site filter selector
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item {
                                FilterChip(
                                    selected = selectedSiteId == null,
                                    onClick = { viewModel.setPayrollRange(startDate, endDate, null) },
                                    shape = RoundedCornerShape(10.dp),
                                    label = { Text("All Parties", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                            items(allSites) { site ->
                                FilterChip(
                                    selected = selectedSiteId == site.id,
                                    onClick = { viewModel.setPayrollRange(startDate, endDate, site.id) },
                                    shape = RoundedCornerShape(10.dp),
                                    label = { Text(site.name, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }
            }

            // Executive Financial Summary Cards
            item {
                payrollSummary?.let { summary ->
                    PayrollExecutiveSummary(summary = summary)
                }
            }

            // Section Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Itemized Worker Payslips (${payrollSummary?.items?.size ?: 0})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Ranked by Balance",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Worker Payroll Cards
            val items = payrollSummary?.items ?: emptyList()
            if (items.isEmpty()) {
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
                                .padding(36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No attendance or payroll records found for selected period.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(items, key = { it.worker.id }) { item ->
                    WorkerPayrollCard(
                        item = item,
                        onPayClick = { workerForPayment = item },
                        onViewDetails = { workerForDetailedSlip = item }
                    )
                }
            }
        }
    }

    // Online Payment Dialog
    workerForPayment?.let { item ->
        PaymentDialog(
            worker = item.worker,
            suggestedAmount = item.netPayable,
            onDismiss = { workerForPayment = null },
            onPaymentSuccess = { payment ->
                workerForPayment = null
                viewModel.refreshPayroll()
            },
            viewModel = viewModel
        )
    }

    // Detailed Payslip Modal Dialog
    workerForDetailedSlip?.let { item ->
        DetailedPayslipDialog(
            item = item,
            onDismiss = { workerForDetailedSlip = null },
            onPayClick = {
                val current = item
                workerForDetailedSlip = null
                workerForPayment = current
            }
        )
    }
}

@Composable
fun PayrollExecutiveSummary(summary: PayrollSummary) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Stripe/Apple style dark overview card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = BrandDark),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, BrandBorderDark)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "TOTAL PERIOD GROSS EXPENSE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$${String.format(Locale.US, "%,.2f", summary.totalGrossExpense)}",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = BrandBorderDark)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Disbursed to Date",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            text = "$${String.format(Locale.US, "%,.2f", summary.totalPaidAlready)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = BrandEmerald
                        )
                    }

                    Column {
                        Text(
                            text = "Pending Wages",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            text = "$${String.format(Locale.US, "%,.2f", summary.totalOutstandingPayable)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (summary.totalOutstandingPayable > 0) BrandPrimaryLight else BrandEmerald
                        )
                    }

                    Column {
                        Text(
                            text = "Days Logged",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            text = "${summary.totalDaysWorked}d",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Column {
                        Text(
                            text = "OT Hours",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            text = "${summary.totalOvertimeHours}h",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WorkerPayrollCard(
    item: WorkerPayrollItem,
    onPayClick: () -> Unit,
    onViewDetails: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onViewDetails() }
            .testTag("payroll_card_${item.worker.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Worker identity
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.worker.name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString(""),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.worker.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${item.worker.trade} • $${item.worker.dailyWage.toInt()}/day",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Payment Status Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        item.isFullyPaid -> BrandEmeraldContainer
                        item.totalPaidAlready > 0 -> BrandAmberContainer
                        item.grossPay == 0.0 -> MaterialTheme.colorScheme.surfaceVariant
                        else -> BrandRoseContainer
                    },
                    border = BorderStroke(
                        1.dp,
                        when {
                            item.isFullyPaid -> BrandEmerald.copy(alpha = 0.3f)
                            item.totalPaidAlready > 0 -> BrandAmber.copy(alpha = 0.3f)
                            item.grossPay == 0.0 -> Color.Transparent
                            else -> BrandRose.copy(alpha = 0.3f)
                        }
                    )
                ) {
                    Text(
                        text = when {
                            item.isFullyPaid -> "PAID"
                            item.totalPaidAlready > 0 -> "PARTIAL"
                            item.grossPay == 0.0 -> "NO DUES"
                            else -> "UNPAID"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            item.isFullyPaid -> OnBrandEmeraldContainer
                            item.totalPaidAlready > 0 -> OnBrandAmberContainer
                            item.grossPay == 0.0 -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> OnBrandRoseContainer
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Work breakdown pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(vertical = 8.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${item.effectiveDaysWorked} Days (${item.fullDaysCount}F + ${item.halfDaysCount}H)",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                if (item.totalOvertimeHours > 0) {
                    Text(
                        text = "+${item.totalOvertimeHours}h OT ($${String.format(Locale.US, "%.0f", item.overtimeEarned)})",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = BrandBlue
                    )
                }
                Text(
                    text = "Gross: $${String.format(Locale.US, "%.2f", item.grossPay)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Financial Balance & Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Net Payable Balance",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$${String.format(Locale.US, "%.2f", item.netPayable)}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (item.netPayable > 0) BrandPrimary else BrandEmerald
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onViewDetails,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Payslip", style = MaterialTheme.typography.labelSmall)
                    }

                    if (item.netPayable > 0) {
                        Button(
                            onClick = onPayClick,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("pay_worker_button_${item.worker.id}")
                        ) {
                            Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Disburse", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailedPayslipDialog(
    item: WorkerPayrollItem,
    onDismiss: () -> Unit,
    onPayClick: () -> Unit
) {
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            LazyColumn(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("CONSTRUCTION PAYSLIP", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = BrandPrimary, letterSpacing = 1.sp)
                            Text("Worker: ${item.worker.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Specialty: ${item.worker.trade} | Phone: ${item.worker.phone}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                }

                item {
                    Text("Wage Rate Breakdown", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    PayslipRow("Daily Standard Wage", "$${item.worker.dailyWage.toInt()} / 8-hr shift")
                    PayslipRow("Hourly Equivalent", "$${String.format(Locale.US, "%.2f", item.worker.hourlyRate)} / hr")
                    PayslipRow("Overtime Rate (1.5x)", "$${String.format(Locale.US, "%.2f", item.worker.hourlyRate * 1.5)} / hr")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                }

                item {
                    Text("Attendance & Hours Summary", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    PayslipRow("Full Days Worked (${item.fullDaysCount})", "$${String.format(Locale.US, "%.2f", item.fullDaysCount * item.worker.dailyWage)}")
                    PayslipRow("Half Days Worked (${item.halfDaysCount})", "$${String.format(Locale.US, "%.2f", item.halfDaysCount * item.worker.dailyWage * 0.5)}")
                    PayslipRow("Overtime Hours (${item.totalOvertimeHours}h)", "$${String.format(Locale.US, "%.2f", item.overtimeEarned)}")
                    PayslipRow("Absent Days (${item.absentDaysCount})", "$0.00")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                }

                item {
                    Text("Earnings & Deductions", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    PayslipRow("Gross Earnings", "$${String.format(Locale.US, "%.2f", item.grossPay)}", bold = true)
                    PayslipRow("Less: Paid to Date", "-$${String.format(Locale.US, "%.2f", item.totalPaidAlready)}", color = BrandEmerald)
                    PayslipRow("Net Payable Outstanding", "$${String.format(Locale.US, "%.2f", item.netPayable)}", bold = true, color = BrandPrimary)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                }

                item {
                    Text("Payment Routing Details", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    if (item.worker.upiId.isNotBlank()) {
                        PayslipRow("UPI Account", item.worker.upiId)
                    }
                    if (item.worker.bankAccount.isNotBlank()) {
                        PayslipRow("Bank Account", item.worker.bankAccount)
                        PayslipRow("IFSC Code", item.worker.bankIfsc)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(
                            onClick = {
                                shareSinglePayslip(context, item)
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share Slip", style = MaterialTheme.typography.labelSmall)
                        }

                        Row {
                            TextButton(onClick = onDismiss) {
                                Text("Close", style = MaterialTheme.typography.labelLarge)
                            }
                            if (item.netPayable > 0) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = onPayClick,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                                ) {
                                    Text("Pay $${String.format(Locale.US, "%.0f", item.netPayable)}", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PayslipRow(
    label: String,
    value: String,
    bold: Boolean = false,
    color: Color = Color.Unspecified
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
            color = if (color != Color.Unspecified) color else MaterialTheme.colorScheme.onSurface
        )
    }
}

fun sharePayrollReport(context: Context, summary: PayrollSummary) {
    val text = buildString {
        appendLine("🏗️ SITECREW CONSTRUCTION PAYROLL REPORT")
        appendLine("Period: ${summary.startDate} to ${summary.endDate}")
        appendLine("Total Workers: ${summary.totalWorkers}")
        appendLine("Total Days Worked: ${summary.totalDaysWorked} days")
        appendLine("Total Overtime Hours: ${summary.totalOvertimeHours} hrs")
        appendLine("Gross Payroll Expense: $${String.format(Locale.US, "%.2f", summary.totalGrossExpense)}")
        appendLine("Disbursed to Date: $${String.format(Locale.US, "%.2f", summary.totalPaidAlready)}")
        appendLine("Outstanding Balance: $${String.format(Locale.US, "%.2f", summary.totalOutstandingPayable)}")
        appendLine("----------------------------------------")
        summary.items.forEachIndexed { i, item ->
            appendLine("${i + 1}. ${item.worker.name} (${item.worker.trade})")
            appendLine("   Days: ${item.effectiveDaysWorked} | OT: ${item.totalOvertimeHours}h | Gross: $${String.format(Locale.US, "%.2f", item.grossPay)} | Due: $${String.format(Locale.US, "%.2f", item.netPayable)}")
        }
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "SiteCrew Payroll Report: ${summary.startDate} to ${summary.endDate}")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share Payroll Report"))
}

fun shareSinglePayslip(context: Context, item: WorkerPayrollItem) {
    val text = buildString {
        appendLine("🏗️ SITECREW WORKER PAYSLIP")
        appendLine("Worker: ${item.worker.name}")
        appendLine("Role: ${item.worker.trade}")
        appendLine("Daily Rate: $${item.worker.dailyWage.toInt()} | OT Rate: $${String.format(Locale.US, "%.2f", item.worker.hourlyRate * 1.5)}/h")
        appendLine("Effective Days Worked: ${item.effectiveDaysWorked}")
        appendLine("Overtime Hours: ${item.totalOvertimeHours}h")
        appendLine("Gross Wage: $${String.format(Locale.US, "%.2f", item.grossPay)}")
        appendLine("Paid Already: $${String.format(Locale.US, "%.2f", item.totalPaidAlready)}")
        appendLine("Net Balance Payable: $${String.format(Locale.US, "%.2f", item.netPayable)}")
        if (item.worker.upiId.isNotBlank()) appendLine("UPI ID: ${item.worker.upiId}")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Payslip - ${item.worker.name}")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share Payslip"))
}
