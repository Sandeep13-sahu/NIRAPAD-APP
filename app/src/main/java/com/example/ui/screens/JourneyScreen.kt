package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.firestore.UiState
import com.example.data.model.EmergencyFacility
import com.example.data.model.Journey
import com.example.data.model.JourneyState
import com.example.data.model.LatLon
import com.example.data.model.RouteOption
import com.example.data.model.ShareToken
import com.example.data.service.LocationReading
import com.example.localization.NirapadCopy
import com.example.ui.components.DeliberateSosButton
import com.example.ui.components.NirapadMapCanvas
import com.example.ui.theme.NirapadBorderSubtle
import com.example.ui.theme.NirapadCautionAmber
import com.example.ui.theme.NirapadCautionContainer
import com.example.ui.theme.NirapadEmergencyContainer
import com.example.ui.theme.NirapadEmergencyRed
import com.example.ui.theme.NirapadNavyDark
import com.example.ui.theme.NirapadPositiveContainer
import com.example.ui.theme.NirapadPositiveGreen
import com.example.ui.theme.NirapadPrimaryTeal
import com.example.ui.theme.NirapadPrimaryTealContainer
import com.example.ui.theme.NirapadSurface
import com.example.ui.theme.NirapadTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun JourneyScreen(
    copy: NirapadCopy,
    currentLocation: LocationReading,
    activeJourney: Journey?,
    journeysHistory: List<Journey>,
    selectedRoute: RouteOption?,
    breadcrumbs: List<LatLon>,
    deviationDistanceM: Int,
    facilities: List<EmergencyFacility>,
    shareLookupState: UiState<ShareToken>?,
    onSimulateStep: (Journey, Boolean) -> Unit,
    onConfirmDeviationOk: (Journey) -> Unit,
    onReroute: (Journey) -> Unit,
    onRequestSosConfirm: () -> Unit,
    onRevokeShare: (Journey) -> Unit,
    onEndJourney: (Journey) -> Unit,
    onLookupShareToken: (String) -> Unit,
    onGoToPlan: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    var tokenInput by remember(activeJourney?.shareTokenId) {
        mutableStateOf(activeJourney?.shareTokenId.orEmpty())
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Protected Journey & Live Share", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = NirapadNavyDark)

        if (activeJourney == null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = NirapadSurface),
                border = BorderStroke(1.dp, NirapadBorderSubtle),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("No Active Journey", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Plan and compare routes first, configure your live sharing preference, and start a protected journey.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NirapadTextSecondary
                    )
                    Button(
                        onClick = onGoToPlan,
                        colors = ButtonDefaults.buttonColors(containerColor = NirapadPrimaryTeal),
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text("Go to Route Planner")
                    }
                }
            }
        } else {
            val isDeviated = activeJourney.state == JourneyState.DEVIATION_DETECTED.value
            val isSos = activeJourney.state == JourneyState.SOS_TRIGGERED.value

            NirapadMapCanvas(
                currentLocation = LatLon(activeJourney.lastLat, activeJourney.lastLon),
                routes = listOfNotNull(selectedRoute),
                selectedRouteId = selectedRoute?.id,
                breadcrumbs = breadcrumbs,
                facilities = facilities,
                isDeviated = isDeviated || isSos,
                modifier = Modifier.height(210.dp)
            )

            // Active Journey HUD Card
            Card(
                colors = CardDefaults.cardColors(containerColor = NirapadSurface),
                border = BorderStroke(1.dp, if (isDeviated) NirapadCautionAmber else NirapadPrimaryTeal),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Destination: ${activeJourney.destinationLabel}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Route: ${activeJourney.selectedRouteLabel}", style = MaterialTheme.typography.bodySmall, color = NirapadTextSecondary)
                        }
                        Surface(
                            color = when {
                                isSos -> NirapadEmergencyContainer
                                isDeviated -> NirapadCautionContainer
                                else -> NirapadPositiveContainer
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = activeJourney.state,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    isSos -> NirapadEmergencyRed
                                    isDeviated -> NirapadCautionAmber
                                    else -> NirapadPositiveGreen
                                },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    // Last location timestamp & stale check
                    val lastLocDate = activeJourney.lastLocationAt?.toDate()
                    val ageSeconds = if (lastLocDate != null) (System.currentTimeMillis() - lastLocDate.time) / 1000L else 0L
                    val isStale = ageSeconds > 120L
                    Surface(
                        color = if (isStale) NirapadCautionContainer else NirapadPrimaryTealContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                        val tsText = lastLocDate?.let { fmt.format(it) } ?: "Pending"
                        Text(
                            text = if (isStale) {
                                "Location update may be stale (last received $tsText) • Offset ${deviationDistanceM}m (limit ${activeJourney.deviationThresholdMeters}m)"
                            } else {
                                "Last location received $tsText • Corridor offset ${deviationDistanceM}m (threshold ${activeJourney.deviationThresholdMeters}m)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = NirapadNavyDark,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    // Explicit sharing status & one-tap revoke
                    if (activeJourney.sharingEnabled && activeJourney.shareTokenId.isNotBlank()) {
                        Surface(
                            color = NirapadPositiveContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Sharing ON with ${activeJourney.sharedContactCount} contact(s)",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = NirapadPositiveGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                    OutlinedButton(
                                        onClick = { clipboard.setText(AnnotatedString(activeJourney.shareTokenId)) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Copy Token")
                                    }
                                }
                                Text(
                                    text = "Token: ${activeJourney.shareTokenId}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NirapadNavyDark
                                )
                                OutlinedButton(
                                    onClick = { onRevokeShare(activeJourney) },
                                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("revoke_share_button")
                                ) {
                                    Icon(Icons.Default.LinkOff, contentDescription = null, tint = NirapadCautionAmber)
                                    Spacer(Modifier.width(6.dp))
                                    Text(copy.stopRevokeSharing, color = NirapadNavyDark)
                                }
                            }
                        }
                    } else {
                        Text("Live sharing is OFF for this journey.", style = MaterialTheme.typography.bodySmall, color = NirapadTextSecondary)
                    }

                    // Deviation Prompt Card (calm, non-blaming)
                    if (isDeviated) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = NirapadCautionContainer),
                            border = BorderStroke(1.dp, NirapadCautionAmber),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("deviation_prompt_card")
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = NirapadCautionAmber)
                                    Text(copy.deviationTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = NirapadNavyDark)
                                }
                                Text(copy.deviationBody, style = MaterialTheme.typography.bodySmall, color = NirapadNavyDark)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(
                                        onClick = { onConfirmDeviationOk(activeJourney) },
                                        modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                                    ) {
                                        Text(copy.deviationConfirmOk, style = MaterialTheme.typography.labelMedium)
                                    }
                                    OutlinedButton(
                                        onClick = { onReroute(activeJourney) },
                                        modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                                    ) {
                                        Text(copy.deviationReroute, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }

                    // Field check controls for verifying GPS & deviation flow on emulator
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { onSimulateStep(activeJourney, false) },
                            modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Record On-Route Fix", style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            onClick = { onSimulateStep(activeJourney, true) },
                            modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("test_deviation_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.AltRoute, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Test Deviation", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    HorizontalDivider(color = NirapadBorderSubtle)

                    // End Journey separated cleanly from SOS
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { onEndJourney(activeJourney) },
                            modifier = Modifier.weight(1f).height(52.dp).minimumInteractiveComponentSize().testTag("end_journey_button")
                        ) {
                            Icon(Icons.Default.StopCircle, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(copy.endJourneyLabel, fontWeight = FontWeight.SemiBold)
                        }
                        DeliberateSosButton(
                            label = copy.emergencySosLabel,
                            onRequestConfirm = onRequestSosConfirm
                        )
                    }
                }
            }
        }

        // Trusted Contact Scoped Read-Only Share Viewer
        Card(
            colors = CardDefaults.cardColors(containerColor = NirapadSurface),
            border = BorderStroke(1.dp, NirapadBorderSubtle),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Visibility, contentDescription = null, tint = NirapadPrimaryTeal)
                    Text(copy.trustedShareTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text(
                    "Trusted contacts open a time-limited, high-entropy share token to view permitted journey status only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NirapadTextSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        placeholder = { Text("Paste share token (tok_...)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("share_token_input")
                    )
                    Button(
                        onClick = { onLookupShareToken(tokenInput) },
                        colors = ButtonDefaults.buttonColors(containerColor = NirapadNavyDark),
                        modifier = Modifier.height(54.dp).minimumInteractiveComponentSize().testTag("open_share_token_button")
                    ) {
                        Text("Open")
                    }
                }

                when (shareLookupState) {
                    UiState.Loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    is UiState.Error -> Surface(color = NirapadEmergencyContainer, shape = RoundedCornerShape(10.dp)) {
                        Text(shareLookupState.message, color = NirapadEmergencyRed, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))
                    }
                    is UiState.Success -> {
                        val tok = shareLookupState.data
                        val expired = System.currentTimeMillis() > tok.expiresAtMillis
                        val fmt = SimpleDateFormat("HH:mm, dd MMM", Locale.getDefault())
                        Surface(
                            color = if (tok.revoked || expired) NirapadCautionContainer else NirapadPositiveContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Traveller: ${tok.ownerDisplayName}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text("Destination: ${tok.destinationLabel}", style = MaterialTheme.typography.bodySmall)
                                Text("Status: ${tok.journeyState} • ${if (tok.revoked) "REVOKED" else if (expired) "EXPIRED" else "ACTIVE SHARE"}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "Last update: ${tok.lastUpdatedAt?.toDate()?.let { fmt.format(it) } ?: "Recent"} • ${tok.lastLocationText}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "Expires: ${fmt.format(Date(tok.expiresAtMillis))}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NirapadTextSecondary
                                )
                            }
                        }
                    }
                    null -> Unit
                }
            }
        }

        // Recent Completed/Cancelled Journeys
        if (journeysHistory.isNotEmpty()) {
            Text("Recent Journeys", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = NirapadNavyDark)
            journeysHistory.take(5).forEach { j ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = NirapadSurface),
                    border = BorderStroke(1.dp, NirapadBorderSubtle),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(j.destinationLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text("${j.selectedRouteLabel} • ${j.providerName}", style = MaterialTheme.typography.bodySmall, color = NirapadTextSecondary)
                        }
                        Text(j.state, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = NirapadPrimaryTeal)
                    }
                }
            }
        }
    }
}
