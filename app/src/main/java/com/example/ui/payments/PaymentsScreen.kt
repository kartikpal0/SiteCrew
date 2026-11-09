package com.example.ui.payments

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.PaymentRecord
import com.example.data.model.Worker
import com.example.ui.MainViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PaymentsScreen(viewModel: MainViewModel) {
    val allPayments by viewModel.allPayments.collectAsState()
    val allWorkers by viewModel.allWorkers.collectAsState()
    var selectedPaymentForReceipt by remember { mutableStateOf<Pair<PaymentRecord, Worker?>?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val workerMap = remember(allWorkers) { allWorkers.associateBy { it.id } }

    val filteredPayments = remember(allPayments, searchQuery, workerMap) {
        allPayments.filter { payment ->
            val worker = workerMap[payment.workerId]
            val workerName = worker?.name ?: ""
            workerName.contains(searchQuery, ignoreCase = true) ||
                    payment.transactionReference.contains(searchQuery, ignoreCase = true) ||
                    payment.paymentMethod.contains(searchQuery, ignoreCase = true)
        }
    }

    val totalPaidOut = remember(allPayments) { allPayments.sumOf { it.amount } }

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
                                    text = "Payment Disbursals",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${allPayments.size} Total Reconciled Transactions",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = BrandEmeraldContainer,
                                border = BorderStroke(1.dp, BrandEmerald.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Verified, contentDescription = null, tint = BrandEmerald, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Online Gateway",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = OnBrandEmeraldContainer
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Stripe/Apple dark summary card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = BrandDark),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, BrandBorderDark)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    text = "TOTAL DISBURSED TO WORKFORCE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF94A3B8),
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "$${String.format(Locale.US, "%,.2f", totalPaidOut)}",
                                    style = MaterialTheme.typography.displayMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "UPI • Instant IMPS • Direct Bank",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = BrandPrimaryLight
                                    )
                                    Text(
                                        text = "100% Settled",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = BrandEmerald
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Filter by worker, txn ref or payment mode...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Section Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Payment Audit Ledger",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Showing ${filteredPayments.size} records",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Transactions list
            if (filteredPayments.isEmpty()) {
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
                                text = "No payment transactions logged yet.\nDisburse wages from the Payroll screen to record payments.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(filteredPayments, key = { it.id }) { payment ->
                    val worker = workerMap[payment.workerId]
                    PaymentTransactionRow(
                        payment = payment,
                        worker = worker,
                        onClick = { selectedPaymentForReceipt = Pair(payment, worker) }
                    )
                }
            }
        }
    }

    // Receipt Dialog
    selectedPaymentForReceipt?.let { (payment, worker) ->
        ReceiptDialog(
            payment = payment,
            worker = worker,
            onDismiss = { selectedPaymentForReceipt = null }
        )
    }
}

@Composable
fun PaymentTransactionRow(
    payment: PaymentRecord,
    worker: Worker?,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy • hh:mm a", Locale.US) }
    val dateString = remember(payment.paymentDate) { dateFormat.format(Date(payment.paymentDate)) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onClick() }
            .testTag("payment_row_${payment.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        when {
                            payment.paymentMethod.contains("UPI", ignoreCase = true) -> BrandBlueContainer
                            payment.paymentMethod.contains("Bank", ignoreCase = true) -> BrandAmberContainer
                            else -> BrandEmeraldContainer
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        payment.paymentMethod.contains("UPI", ignoreCase = true) -> Icons.Default.QrCode
                        payment.paymentMethod.contains("Bank", ignoreCase = true) -> Icons.Default.AccountBalance
                        else -> Icons.Default.Payments
                    },
                    contentDescription = null,
                    tint = when {
                        payment.paymentMethod.contains("UPI", ignoreCase = true) -> BrandBlue
                        payment.paymentMethod.contains("Bank", ignoreCase = true) -> BrandAmber
                        else -> BrandEmerald
                    },
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = worker?.name ?: "Worker #${payment.workerId}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${payment.paymentMethod} • ${payment.transactionReference}",
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandPrimary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = dateString,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$${String.format(Locale.US, "%.2f", payment.amount)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = BrandEmerald
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = BrandEmeraldContainer,
                    border = BorderStroke(1.dp, BrandEmerald.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = "SETTLED",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = OnBrandEmeraldContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PaymentDialog(
    worker: Worker,
    suggestedAmount: Double,
    onDismiss: () -> Unit,
    onPaymentSuccess: (PaymentRecord) -> Unit,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var amountText by remember { mutableStateOf(String.format(Locale.US, "%.2f", suggestedAmount)) }
    var selectedMethod by remember { mutableStateOf("UPI Transfer") }
    var notes by remember { mutableStateOf("Wage payment for site labor") }
    var isProcessing by remember { mutableStateOf(false) }
    var completedPayment by remember { mutableStateOf<PaymentRecord?>(null) }

    val amount = amountText.toDoubleOrNull() ?: 0.0

    if (completedPayment != null) {
        ReceiptDialog(
            payment = completedPayment!!,
            worker = worker,
            onDismiss = {
                onPaymentSuccess(completedPayment!!)
                onDismiss()
            }
        )
        return
    }

    Dialog(onDismissRequest = { if (!isProcessing) onDismiss() }) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(BrandPrimaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = null,
                            tint = BrandPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Disburse Worker Wages", style = MaterialTheme.typography.titleLarge)
                        Text("Recipient: ${worker.name} (${worker.trade})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Payment amount field
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Disbursal Amount ($ USD)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("payment_amount_input")
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text("Select Payout Rail", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                val methods = listOf(
                    Triple("UPI Transfer", Icons.Default.QrCode, if (worker.upiId.isNotBlank()) "Direct to ${worker.upiId}" else "Instant UPI ID / QR"),
                    Triple("Instant Bank IMPS", Icons.Default.AccountBalance, if (worker.bankAccount.isNotBlank()) "Acc: ${worker.bankAccount}" else "Direct Bank NEFT/IMPS"),
                    Triple("Corporate Card", Icons.Default.CreditCard, "Construction Card Disbursal"),
                    Triple("Cash Payout", Icons.Default.Payments, "Site Cash Voucher Log")
                )

                methods.forEach { (mode, icon, sub) ->
                    val isSelected = selectedMethod == mode
                    Surface(
                        onClick = { selectedMethod = mode },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) BrandPrimaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = if (isSelected) BorderStroke(1.5.dp, BrandPrimary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedMethod = mode }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(icon, contentDescription = null, tint = if (isSelected) BrandPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(mode, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Payment Remarks / Memo") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Processing Animation or Action Buttons
                if (isProcessing) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = BrandPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Processing transaction via banking rail...",
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel", style = MaterialTheme.typography.labelLarge)
                        }
                        Spacer(modifier = Modifier.width(8.dp))

                        if (selectedMethod == "UPI Transfer" && worker.upiId.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    val upiUri = Uri.parse("upi://pay?pa=${worker.upiId}&pn=${Uri.encode(worker.name)}&am=$amount&cu=INR&tn=${Uri.encode("SiteCrew Labor Wages")}")
                                    val intent = Intent(Intent.ACTION_VIEW, upiUri)
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "No external UPI app found. Processing via gateway.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Open UPI App", style = MaterialTheme.typography.labelSmall)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }

                        Button(
                            onClick = {
                                if (amount > 0) {
                                    isProcessing = true
                                    coroutineScope.launch {
                                        delay(1400)
                                        viewModel.processPayment(
                                            worker = worker,
                                            amount = amount,
                                            paymentMethod = selectedMethod,
                                            notes = notes,
                                            onSuccess = { payment ->
                                                isProcessing = false
                                                completedPayment = payment
                                            }
                                        )
                                    }
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                            modifier = Modifier.testTag("confirm_payment_button")
                        ) {
                            Text("Authorize $${String.format(Locale.US, "%.2f", amount)}", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReceiptDialog(
    payment: PaymentRecord,
    worker: Worker?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US) }
    val formattedDate = remember(payment.paymentDate) { dateFormat.format(Date(payment.paymentDate)) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(BrandEmeraldContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = BrandEmerald,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Payment Settled Successfully",
                    style = MaterialTheme.typography.titleLarge,
                    color = OnBrandEmeraldContainer
                )

                Text(
                    text = "$${String.format(Locale.US, "%.2f", payment.amount)}",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(12.dp))

                ReceiptDetailRow("Transaction Ref", payment.transactionReference)
                ReceiptDetailRow("Recipient Worker", worker?.name ?: "Worker #${payment.workerId}")
                ReceiptDetailRow("Specialty / Trade", worker?.trade ?: "Labor")
                ReceiptDetailRow("Payout Rail", payment.paymentMethod)
                ReceiptDetailRow("Date & Timestamp", formattedDate)
                ReceiptDetailRow("Status", "SUCCESS (Reconciled)")
                if (payment.notes.isNotBlank()) {
                    ReceiptDetailRow("Remarks", payment.notes)
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = {
                            val shareText = buildString {
                                appendLine("🏗️ SITECREW OFFICIAL PAYMENT RECEIPT")
                                appendLine("Txn Ref: ${payment.transactionReference}")
                                appendLine("Amount: $${String.format(Locale.US, "%.2f", payment.amount)}")
                                appendLine("Recipient: ${worker?.name ?: "Worker #${payment.workerId}"}")
                                appendLine("Method: ${payment.paymentMethod}")
                                appendLine("Date: $formattedDate")
                                appendLine("Status: SUCCESS (Paid & Reconciled)")
                            }
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Payment Receipt - ${payment.transactionReference}")
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Payment Receipt"))
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share Receipt", style = MaterialTheme.typography.labelSmall)
                    }

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
private fun ReceiptDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}
