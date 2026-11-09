package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.location.GpsLocationData
import com.example.ui.theme.BrandPrimary
import com.example.ui.theme.BrandPrimaryContainer
import java.util.Locale

@Composable
fun CreatePartyDialog(
    currentGps: GpsLocationData?,
    onDismiss: () -> Unit,
    onCreateParty: (
        name: String,
        clientName: String,
        address: String,
        latitude: Double,
        longitude: Double,
        geofenceRadius: Double,
        defaultWage: Double
    ) -> Unit
) {
    var partyName by remember { mutableStateOf("") }
    var clientName by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var latitudeText by remember { mutableStateOf(currentGps?.latitude?.toString() ?: "37.421998") }
    var longitudeText by remember { mutableStateOf(currentGps?.longitude?.toString() ?: "-122.084000") }
    var geofenceText by remember { mutableStateOf("250") }
    var defaultWageText by remember { mutableStateOf("140") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(BrandPrimaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Apartment, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Create Construction Party", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Project perimeter with GPS geofence auditing",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = partyName,
                    onValueChange = { partyName = it },
                    label = { Text("Party / Project Name *") },
                    placeholder = { Text("e.g. Skyline Heights Phase 2") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("party_name_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = clientName,
                    onValueChange = { clientName = it },
                    label = { Text("Client / Project Developer") },
                    placeholder = { Text("e.g. Apex Infrastructure Ltd") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Site Address / Plot Location *") },
                    placeholder = { Text("e.g. Sector 8B, Industrial Corridor") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("party_address_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // GPS Coordinates Row with "Use My Location"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Perimeter Center Coordinates", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    if (currentGps != null) {
                        TextButton(
                            onClick = {
                                latitudeText = currentGps.latitude.toString()
                                longitudeText = currentGps.longitude.toString()
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(13.dp), tint = BrandPrimary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Use Current GPS", style = MaterialTheme.typography.labelSmall, color = BrandPrimary)
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = latitudeText,
                        onValueChange = { latitudeText = it },
                        label = { Text("Latitude") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = longitudeText,
                        onValueChange = { longitudeText = it },
                        label = { Text("Longitude") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = geofenceText,
                        onValueChange = { geofenceText = it },
                        label = { Text("Perimeter (m)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = defaultWageText,
                        onValueChange = { defaultWageText = it },
                        label = { Text("Base Wage ($/day)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel", style = MaterialTheme.typography.labelLarge) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (partyName.isNotBlank() && address.isNotBlank()) {
                                val lat = latitudeText.toDoubleOrNull() ?: 37.421998
                                val lon = longitudeText.toDoubleOrNull() ?: -122.084000
                                val radius = geofenceText.toDoubleOrNull() ?: 200.0
                                val wage = defaultWageText.toDoubleOrNull() ?: 140.0
                                onCreateParty(partyName, clientName, address, lat, lon, radius, wage)
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                        modifier = Modifier.testTag("submit_create_party_button")
                    ) {
                        Text("Create Party", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
