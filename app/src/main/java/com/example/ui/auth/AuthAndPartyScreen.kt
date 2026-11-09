package com.example.ui.auth

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.WorkerTrade
import com.example.ui.AppRole
import com.example.ui.MainViewModel
import com.example.ui.theme.*

@Composable
fun AuthAndPartyScreen(viewModel: MainViewModel) {
    var selectedTab by remember { mutableStateOf(0) } // 0 = Worker Join, 1 = Admin Portal
    val adminAuthError by viewModel.adminAuthError.collectAsState()
    val workerJoinError by viewModel.workerJoinError.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(top = 48.dp, bottom = 48.dp)
        ) {
            // Enterprise Hero Header
            item {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(BrandPrimary, BrandPrimaryDark)
                            )
                        )
                        .shadow(12.dp, RoundedCornerShape(22.dp), spotColor = BrandPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Construction,
                        contentDescription = "SiteCrew",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "SiteCrew",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = "Enterprise Workforce & Geofenced Payroll",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Apple-style Segmented Control
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SegmentTab(
                            title = "Worker Portal",
                            icon = Icons.Outlined.Badge,
                            isSelected = selectedTab == 0,
                            modifier = Modifier.weight(1f),
                            onClick = { selectedTab = 0 }
                        )
                        SegmentTab(
                            title = "Admin Console",
                            icon = Icons.Outlined.AdminPanelSettings,
                            isSelected = selectedTab == 1,
                            modifier = Modifier.weight(1f),
                            onClick = { selectedTab = 1 }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            if (selectedTab == 0) {
                // WORKER JOIN TAB
                item {
                    WorkerJoinCard(
                        errorMessage = workerJoinError,
                        onJoin = { code, name, phone, trade, upiId ->
                            viewModel.joinPartyWithCode(code, name, phone, trade, upiId)
                        },
                        onQuickLogin = { phone ->
                            viewModel.loginWorkerByPhone(phone)
                        }
                    )
                }
            } else {
                // ADMIN PORTAL TAB
                item {
                    AdminAuthCard(
                        errorMessage = adminAuthError,
                        onLogin = { phone, pin ->
                            viewModel.loginAdmin(phone, pin)
                        },
                        onRegister = { name, phone, pin, company ->
                            viewModel.registerAdmin(name, phone, pin, company)
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Encrypted Local-First Database • GPS Perimeter Audit",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentTab(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
        shadowElevation = if (isSelected) 2.dp else 0.dp,
        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)) else null,
        modifier = modifier.height(40.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) BrandPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun WorkerJoinCard(
    errorMessage: String?,
    onJoin: (code: String, name: String, phone: String, trade: String, upiId: String) -> Unit,
    onQuickLogin: (phone: String) -> Unit
) {
    var isNewJoinMode by remember { mutableStateOf(true) }
    var joinCode by remember { mutableStateOf("") }
    var workerName by remember { mutableStateOf("") }
    var workerPhone by remember { mutableStateOf("") }
    var trade by remember { mutableStateOf(WorkerTrade.MASON.title) }
    var upiId by remember { mutableStateOf("") }
    var tradeExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isNewJoinMode) "Join with Passkey Code" else "Returning Worker Access",
                    style = MaterialTheme.typography.titleLarge
                )

                TextButton(
                    onClick = { isNewJoinMode = !isNewJoinMode },
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text(
                        if (isNewJoinMode) "Existing Worker?" else "Join with Code",
                        style = MaterialTheme.typography.labelMedium,
                        color = BrandPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = if (isNewJoinMode)
                    "Enter the private join code provided by your site contractor to register into the crew roster."
                else "Enter your registered mobile number to access your worker dashboard and attendance ledger.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (!errorMessage.isNullOrBlank()) {
                Surface(
                    color = BrandRoseContainer,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, BrandRose.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = BrandRose, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = errorMessage,
                            color = OnBrandRoseContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (isNewJoinMode) {
                OutlinedTextField(
                    value = joinCode,
                    onValueChange = { joinCode = it.uppercase() },
                    label = { Text("Worker Join Passkey (e.g. SC-4821)") },
                    placeholder = { Text("Code from Site Admin") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, tint = BrandPrimary) },
                    modifier = Modifier.fillMaxWidth().testTag("worker_join_code_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = workerName,
                    onValueChange = { workerName = it },
                    label = { Text("Full Legal Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.PersonOutline, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().testTag("worker_name_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = workerPhone,
                    onValueChange = { workerPhone = it },
                    label = { Text("Mobile Phone Number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.PhoneIphone, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().testTag("worker_phone_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Trade Picker
                Box {
                    OutlinedTextField(
                        value = trade,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Construction Specialty / Trade") },
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Icon(Icons.Default.Handyman, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { tradeExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = tradeExpanded,
                        onDismissRequest = { tradeExpanded = false },
                        modifier = Modifier.clip(RoundedCornerShape(14.dp))
                    ) {
                        WorkerTrade.values().forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t.title, style = MaterialTheme.typography.bodyMedium) },
                                onClick = {
                                    trade = t.title
                                    tradeExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = upiId,
                    onValueChange = { upiId = it },
                    label = { Text("UPI ID for Direct Wage Disbursal (Optional)") },
                    placeholder = { Text("name@upi") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        onJoin(joinCode, workerName, workerPhone, trade, upiId)
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("join_party_button")
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Join Construction Party", style = MaterialTheme.typography.labelLarge)
                }
            } else {
                OutlinedTextField(
                    value = workerPhone,
                    onValueChange = { workerPhone = it },
                    label = { Text("Registered Mobile Number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.PhoneIphone, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().testTag("worker_login_phone_input")
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        onQuickLogin(workerPhone)
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("worker_quick_login_button")
                ) {
                    Text("Access Worker Dashboard", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
fun AdminAuthCard(
    errorMessage: String?,
    onLogin: (phone: String, pin: String) -> Unit,
    onRegister: (name: String, phone: String, pin: String, company: String) -> Unit
) {
    var isRegisterMode by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isRegisterMode) "Create Admin Organization" else "Contractor Sign In",
                    style = MaterialTheme.typography.titleLarge
                )

                TextButton(
                    onClick = { isRegisterMode = !isRegisterMode },
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text(
                        if (isRegisterMode) "Existing Admin?" else "Register Org",
                        style = MaterialTheme.typography.labelMedium,
                        color = BrandPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = if (isRegisterMode)
                    "Establish your contractor profile to create construction parties, set geofences, and generate worker passkeys."
                else "Authenticate using your registered administrator credentials and security PIN.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (!errorMessage.isNullOrBlank()) {
                Surface(
                    color = BrandRoseContainer,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, BrandRose.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = BrandRose, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = errorMessage,
                            color = OnBrandRoseContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (isRegisterMode) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Manager / Administrator Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.PersonOutline, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().testTag("admin_register_name")
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = company,
                    onValueChange = { company = it },
                    label = { Text("Contractor / Enterprise Firm Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.Business, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Admin Mobile Phone") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Default.PhoneIphone, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().testTag("admin_phone_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 6) pin = it },
                label = { Text("Admin Passcode PIN") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().testTag("admin_pin_input")
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (isRegisterMode) {
                        onRegister(name, phone, pin, company)
                    } else {
                        onLogin(phone, pin)
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("admin_auth_submit_button")
            ) {
                Text(
                    text = if (isRegisterMode) "Complete Registration & Setup" else "Sign In to Admin Console",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
