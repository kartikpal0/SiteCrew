package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ConstructionSite
import com.example.location.GpsLocationData
import com.example.location.GpsLocationManager
import com.example.ui.AppScreen
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteCrewTopBar(
    selectedSite: ConstructionSite?,
    allSites: List<ConstructionSite>,
    onSelectSite: (ConstructionSite) -> Unit,
    onCreatePartyClick: () -> Unit,
    adminName: String?,
    onSwitchRoleClick: () -> Unit,
    isOnline: Boolean,
    pendingSyncCount: Int,
    onSyncClick: () -> Unit
) {
    var siteMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 0.5.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Brand mark & Party Selector
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(BrandPrimary, BrandPrimaryDark)
                                )
                            )
                            .shadow(4.dp, RoundedCornerShape(12.dp), spotColor = BrandPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Construction,
                            contentDescription = "SiteCrew",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "ADMIN CONSOLE",
                                style = MaterialTheme.typography.labelSmall,
                                color = BrandPrimary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            if (!adminName.isNullOrBlank()) {
                                Text(
                                    text = " • $adminName",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Box {
                            Surface(
                                onClick = { siteMenuExpanded = true },
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Transparent,
                                modifier = Modifier.testTag("site_selector_dropdown")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                ) {
                                    Text(
                                        text = selectedSite?.name ?: "Select Construction Party",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.UnfoldMore,
                                        contentDescription = "Select Party",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = siteMenuExpanded,
                                onDismissRequest = { siteMenuExpanded = false },
                                modifier = Modifier
                                    .widthIn(min = 260.dp)
                                    .clip(RoundedCornerShape(16.dp))
                            ) {
                                Text(
                                    text = "CONSTRUCTION PARTIES",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )

                                allSites.forEach { site ->
                                    val isSelected = site.id == selectedSite?.id
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    site.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) BrandPrimary else MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    site.address,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        },
                                        onClick = {
                                            onSelectSite(site)
                                            siteMenuExpanded = false
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Apartment,
                                                contentDescription = null,
                                                tint = if (isSelected) BrandPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        },
                                        trailingIcon = if (isSelected) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = BrandPrimary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        } else null
                                    )
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "+ Create New Party",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = BrandPrimary
                                        )
                                    },
                                    onClick = {
                                        siteMenuExpanded = false
                                        onCreatePartyClick()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.AddBusiness,
                                            contentDescription = null,
                                            tint = BrandPrimary
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                // Role switch & Sync badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Switch to Worker mode
                    FilledTonalIconButton(
                        onClick = onSwitchRoleClick,
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = "Switch to Worker Portal",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Network Sync Capsule (Apple-like pill)
                    Surface(
                        onClick = onSyncClick,
                        shape = RoundedCornerShape(24.dp),
                        color = when {
                            !isOnline -> BrandRoseContainer
                            pendingSyncCount > 0 -> BrandAmberContainer
                            else -> BrandEmeraldContainer
                        },
                        border = BorderStroke(
                            1.dp,
                            when {
                                !isOnline -> BrandRose.copy(alpha = 0.3f)
                                pendingSyncCount > 0 -> BrandAmber.copy(alpha = 0.3f)
                                else -> BrandEmerald.copy(alpha = 0.3f)
                            }
                        ),
                        modifier = Modifier.testTag("sync_status_badge")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            !isOnline -> BrandRose
                                            pendingSyncCount > 0 -> BrandAmber
                                            else -> BrandEmerald
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when {
                                    !isOnline -> "Offline"
                                    pendingSyncCount > 0 -> "$pendingSyncCount Pending"
                                    else -> "Live"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    !isOnline -> OnBrandRoseContainer
                                    pendingSyncCount > 0 -> OnBrandAmberContainer
                                    else -> OnBrandEmeraldContainer
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GpsLocationStatusBanner(
    currentGps: GpsLocationData?,
    selectedSite: ConstructionSite?,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onSimulateSiteLock: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("gps_status_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                if (currentGps != null) BrandEmeraldContainer else MaterialTheme.colorScheme.surfaceVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GpsFixed,
                            contentDescription = "GPS Status",
                            tint = if (currentGps != null) BrandEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Site Geofence Telemetry",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (selectedSite != null) "Radius: ${selectedSite.geofenceRadiusMeters.toInt()}m around perimeter" else "Select a party to track",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!hasPermission) {
                    Button(
                        onClick = onRequestPermission,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp).testTag("enable_gps_button")
                    ) {
                        Text("Enable GPS", style = MaterialTheme.typography.labelSmall)
                    }
                } else if (selectedSite != null) {
                    OutlinedButton(
                        onClick = onSimulateSiteLock,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, BrandPrimary.copy(alpha = 0.4f)),
                        modifier = Modifier.height(30.dp).testTag("simulate_gps_button")
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(13.dp), tint = BrandPrimary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Lock Coords", style = MaterialTheme.typography.labelSmall, color = BrandPrimary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (!hasPermission) {
                Text(
                    text = "Location access is required to automatically verify whether workers check-in within the site perimeter.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (currentGps == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = BrandPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Acquiring precision GPS fix...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val distance = if (selectedSite != null) {
                    GpsLocationManager.computeDistanceMeters(
                        currentGps.latitude,
                        currentGps.longitude,
                        selectedSite.latitude,
                        selectedSite.longitude
                    )
                } else null

                val isInside = distance != null && selectedSite != null && distance <= selectedSite.geofenceRadiusMeters

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = String.format(java.util.Locale.US, "%.5f, %.5f", currentGps.latitude, currentGps.longitude),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "±${currentGps.accuracy.toInt()}m accuracy",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (distance != null && selectedSite != null) {
                            Text(
                                text = "${GpsLocationManager.formatDistance(distance)} from center",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isInside) BrandEmerald else MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isInside) BrandEmeraldContainer else BrandRoseContainer,
                        border = BorderStroke(1.dp, if (isInside) BrandEmerald.copy(alpha = 0.3f) else BrandRose.copy(alpha = 0.3f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (isInside) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isInside) BrandEmerald else BrandRose,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (isInside) "INSIDE PERIMETER" else "OUTSIDE BOUNDARY",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isInside) OnBrandEmeraldContainer else OnBrandRoseContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SiteCrewBottomNav(
    currentScreen: AppScreen,
    onScreenSelected: (AppScreen) -> Unit,
    pendingSyncCount: Int
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets.navigationBars
        ) {
            val items = listOf(
                Triple(AppScreen.ATTENDANCE, Icons.Default.FactCheck, Icons.Outlined.FactCheck),
                Triple(AppScreen.WORKERS, Icons.Default.Badge, Icons.Outlined.Badge),
                Triple(AppScreen.PAYROLL, Icons.Default.ReceiptLong, Icons.Outlined.ReceiptLong),
                Triple(AppScreen.PAYMENTS, Icons.Default.Payment, Icons.Outlined.Payment),
                Triple(AppScreen.SYNC_CENTER, Icons.Default.CloudSync, Icons.Outlined.CloudSync)
            )

            items.forEach { (screen, selectedIcon, unselectedIcon) ->
                val isSelected = currentScreen == screen
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onScreenSelected(screen) },
                    icon = {
                        BadgedBox(badge = {
                            if (screen == AppScreen.SYNC_CENTER && pendingSyncCount > 0) {
                                Badge(
                                    containerColor = BrandAmber,
                                    contentColor = Color.White
                                ) {
                                    Text("$pendingSyncCount", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }) {
                            Icon(
                                imageVector = if (isSelected) selectedIcon else unselectedIcon,
                                contentDescription = screen.title,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    },
                    label = {
                        Text(
                            screen.title,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = BrandPrimary,
                        selectedTextColor = BrandPrimary,
                        indicatorColor = BrandPrimaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.testTag("nav_item_${screen.name.lowercase()}")
                )
            }
        }
    }
}
