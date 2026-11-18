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
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.firestore.UiState
import com.example.data.model.EmergencyFacility
import com.example.data.model.Journey
import com.example.data.model.JourneyState
import com.example.data.model.LatLon
import com.example.data.model.NearbyPoliceStation
import com.example.data.model.RouteOption
import com.example.data.model.RouteProgress
import com.example.data.model.ShareToken
import com.example.data.model.WeatherContext
import com.example.data.service.GuardianShareHelper
import com.example.data.service.LocationReading
import com.example.data.service.RoutingAndSafetyEngine
import com.example.localization.NirapadCopy
import com.example.ui.components.DeliberateSosButton
import com.example.ui.components.NirapadMapCanvas
import com.example.ui.components.NirapadOutlinedTextField
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
import kotlin.math.roundToInt

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
    modifier: Modifier = Modifier,
    hasLiveGpsFix: Boolean = currentLocation.timestampMillis > 0L,
    locationPermissionGranted: Boolean = hasLiveGpsFix,
    onRequestLocationPermission: () -> Unit = {},
    currentAddressLabel: String? = null,
    weatherContext: WeatherContext? = null,
    routeProgress: RouteProgress? = null,
    arrivedJourneyId: String? = null,
    nearbyPoliceStations: List<NearbyPoliceStation> = emptyList(),
    onDismissArrival: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var tokenInput by remember(activeJourney?.shareTokenId) {
        mutableStateOf(activeJourney?.shareTokenId.orEmpty())
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 112.dp),
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
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NirapadPrimaryTeal,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text("Go to Route Planner", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else {
            val isDeviated = activeJourney.state == JourneyState.DEVIATION_DETECTED.value
            val isSos = activeJourney.state == JourneyState.SOS_TRIGGERED.value

            val liveLoc = if (currentLocation.timestampMillis > 0L) {
                LatLon(currentLocation.lat, currentLocation.lon)
            } else {
                LatLon(activeJourney.lastLat, activeJourney.lastLon)
            }

            // Live Turn-by-Turn Navigation Instruction Card
            TurnByTurnNavigationCard(
                activeJourney = activeJourney,
                selectedRoute = selectedRoute,
                routeProgress = routeProgress,
                currentAddressLabel = currentAddressLabel,
                weatherContext = weatherContext
            )

            // Live Trip Metrics Row (Remaining, Covered, ETA & Speed) + Completion Progress Bar
            TripMetricsProgressCard(
                activeJourney = activeJourney,
                selectedRoute = selectedRoute,
                routeProgress = routeProgress
            )

            NirapadMapCanvas(
                currentLocation = liveLoc,
                routes = listOfNotNull(selectedRoute),
                selectedRouteId = selectedRoute?.id,
                breadcrumbs = breadcrumbs,
                facilities = facilities,
                isDeviated = isDeviated || isSos,
                bearingDegrees = currentLocation.bearingDegrees,
                hasLiveGpsFix = hasLiveGpsFix,
                accuracyMeters = currentLocation.accuracyMeters,
                locationPermissionGranted = locationPermissionGranted,
                autoFollowByDefault = true,
                fullscreenTitle = "Protected Journey • ${activeJourney.destinationLabel}",
                fullscreenSubtitle = currentAddressLabel ?: "${activeJourney.state} • Corridor offset ${deviationDistanceM}m",
                onRequestLocationPermission = onRequestLocationPermission,
                remainingPolyline = routeProgress?.remainingPolyline,
                nearbyPoliceStations = nearbyPoliceStations,
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

                    // One-tap Share Live on WhatsApp (OSM clickable link + live locality + share token)
                    Button(
                        onClick = {
                            val msg = GuardianShareHelper.buildLiveShareMessage(
                                travellerName = activeJourney.ownerDisplayName,
                                locationName = currentAddressLabel.orEmpty(),
                                lat = liveLoc.lat,
                                lon = liveLoc.lon,
                                destinationLabel = activeJourney.destinationLabel,
                                shareTokenId = activeJourney.shareTokenId,
                                remainingMeters = routeProgress?.remainingDistanceMeters
                            )
                            GuardianShareHelper.launchWhatsAppShare(context, msg)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NirapadPositiveGreen,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("share_whatsapp_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Share Live on WhatsApp", color = Color.White, fontWeight = FontWeight.Bold)
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
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NirapadNavyDark),
                                        border = BorderStroke(1.dp, NirapadPrimaryTeal),
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
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NirapadNavyDark),
                                    border = BorderStroke(1.dp, Color(0xFF94A3B8)),
                                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("revoke_share_button")
                                ) {
                                    Icon(Icons.Default.LinkOff, contentDescription = null, tint = NirapadCautionAmber)
                                    Spacer(Modifier.width(6.dp))
                                    Text(copy.stopRevokeSharing, color = NirapadNavyDark)
                                }
                            }
                        }
                    } else {
                        Text("Live sharing token is OFF for this journey.", style = MaterialTheme.typography.bodySmall, color = NirapadTextSecondary)
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
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NirapadNavyDark),
                                        border = BorderStroke(1.dp, Color(0xFF94A3B8)),
                                        modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                                    ) {
                                        Text(copy.deviationConfirmOk, style = MaterialTheme.typography.labelMedium)
                                    }
                                    OutlinedButton(
                                        onClick = { onReroute(activeJourney) },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NirapadNavyDark),
                                        border = BorderStroke(1.dp, Color(0xFF94A3B8)),
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
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NirapadNavyDark),
                            border = BorderStroke(1.dp, Color(0xFF94A3B8)),
                            modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Record On-Route Fix", style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            onClick = { onSimulateStep(activeJourney, true) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NirapadNavyDark),
                            border = BorderStroke(1.dp, Color(0xFF94A3B8)),
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
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NirapadNavyDark),
                            border = BorderStroke(1.dp, Color(0xFF94A3B8)),
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
                    NirapadOutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        placeholder = { Text("Paste share token (tok_...)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("share_token_input")
                    )
                    Button(
                        onClick = { onLookupShareToken(tokenInput) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NirapadNavyDark,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(54.dp).minimumInteractiveComponentSize().testTag("open_share_token_button")
                    ) {
                        Text("View", color = Color.White, fontWeight = FontWeight.SemiBold)
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
            Text("Recent Journeys", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
            journeysHistory.take(5).forEach { j ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = NirapadSurface),
                    border = BorderStroke(1.dp, NirapadBorderSubtle),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("recent_journey_card_${j.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(j.destinationLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
                            Text("${j.selectedRouteLabel} • ${j.providerName}", style = MaterialTheme.typography.bodySmall, color = NirapadTextSecondary)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            val distBadge = if (j.totalDistanceKm > 0.0) {
                                "${RoutingAndSafetyEngine.formatDistanceKm(j.totalDistanceKm)} • ${j.state}"
                            } else {
                                j.state
                            }
                            Text(
                                text = distBadge,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            if (j.durationMinutes > 0L) {
                                Text(
                                    text = "${j.durationMinutes} min",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NirapadTextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Destination Reached (<= 35m) Celebratory Completion Modal
    if (activeJourney != null && (arrivedJourneyId == activeJourney.id || routeProgress?.hasArrived == true)) {
        if (arrivedJourneyId == activeJourney.id) {
            val totalMeters = routeProgress?.totalDistanceMeters
                ?: selectedRoute?.distanceMeters
                ?: (activeJourney.totalDistanceKm * 1000.0).roundToInt().takeIf { it > 0 }
                ?: RoutingAndSafetyEngine.haversineMeters(
                    activeJourney.originLat,
                    activeJourney.originLon,
                    activeJourney.destinationLat,
                    activeJourney.destinationLon
                ).roundToInt()
            val completedKm = maxOf(activeJourney.totalDistanceKm, totalMeters / 1000.0)
            val completedMins = activeJourney.durationMinutes.takeIf { it > 0L }
                ?: selectedRoute?.let { (it.durationSeconds / 60.0).roundToInt().toLong().coerceAtLeast(1L) }
                ?: 1L
            DestinationReachedModal(
                destinationLabel = activeJourney.destinationLabel,
                totalDistanceKm = completedKm,
                durationMinutes = completedMins,
                onCompleteAndClose = { onEndJourney(activeJourney) },
                onDismiss = { onDismissArrival(activeJourney.id) }
            )
        }
    }
}

@Composable
private fun TurnByTurnNavigationCard(
    activeJourney: Journey,
    selectedRoute: RouteOption?,
    routeProgress: RouteProgress?,
    currentAddressLabel: String?,
    weatherContext: WeatherContext?
) {
    val activeStep = routeProgress?.activeStep ?: selectedRoute?.steps?.firstOrNull()
    val remMeters = routeProgress?.remainingDistanceMeters ?: selectedRoute?.distanceMeters ?: 0
    val remMins = ((routeProgress?.remainingDurationSeconds ?: selectedRoute?.durationSeconds ?: 0) / 60).coerceAtLeast(1)
    val stepDistMeters = routeProgress?.distanceToNextStepMeters ?: activeStep?.distanceMeters ?: 0

    val maneuverIcon = when {
        routeProgress?.hasArrived == true || activeStep?.maneuverType == "arrive" -> Icons.Default.Flag
        activeStep?.maneuverModifier?.contains("left", ignoreCase = true) == true -> Icons.Default.TurnLeft
        activeStep?.maneuverModifier?.contains("right", ignoreCase = true) == true -> Icons.Default.TurnRight
        activeStep != null -> Icons.Default.Straight
        else -> Icons.Default.Navigation
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = NirapadNavyDark),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().testTag("turn_by_turn_card")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = NirapadPrimaryTeal,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = maneuverIcon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(10.dp).size(26.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    val primaryInstruction = when {
                        routeProgress?.hasArrived == true -> "Arriving at ${activeJourney.destinationLabel}"
                        activeStep != null && stepDistMeters > 0 -> "${activeStep.instruction} in $stepDistMeters m"
                        activeStep != null -> activeStep.instruction
                        else -> "Follow safe corridor toward ${activeJourney.destinationLabel}"
                    }
                    Text(
                        text = primaryInstruction,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    val remDistText = if (remMeters >= 1000) {
                        String.format(Locale.US, "%.1f km remaining", remMeters / 1000.0)
                    } else if (remMeters > 0) {
                        "$remMeters m remaining"
                    } else {
                        "Tracking route progress"
                    }
                    Text(
                        text = "$remDistText • ETA $remMins min",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFBAE6FD)
                    )
                }
            }
            if (!currentAddressLabel.isNullOrBlank() || (weatherContext != null && !weatherContext.isDay)) {
                HorizontalDivider(color = Color.White.copy(alpha = 0.16f))
                Text(
                    text = buildString {
                        if (!currentAddressLabel.isNullOrBlank()) {
                            append("Near $currentAddressLabel")
                        }
                        if (weatherContext != null && !weatherContext.isDay) {
                            if (isNotEmpty()) append(" • ")
                            append("After-dark safety priority ON")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFE2E8F0)
                )
            }
        }
    }
}

@Composable
private fun TripMetricsProgressCard(
    activeJourney: Journey,
    selectedRoute: RouteOption?,
    routeProgress: RouteProgress?
) {
    val fallbackTotalMeters = selectedRoute?.distanceMeters
        ?: (activeJourney.totalDistanceKm * 1000.0).roundToInt().takeIf { it > 0 }
        ?: RoutingAndSafetyEngine.haversineMeters(
            activeJourney.originLat,
            activeJourney.originLon,
            activeJourney.destinationLat,
            activeJourney.destinationLon
        ).roundToInt().coerceAtLeast(100)
    val totalMeters = (routeProgress?.totalDistanceMeters ?: fallbackTotalMeters).coerceAtLeast(1)
    val remMeters = routeProgress?.remainingDistanceMeters ?: totalMeters
    val coveredMeters = routeProgress?.coveredDistanceMeters ?: 0
    val progressFrac = (routeProgress?.progressFraction ?: 0f).coerceIn(0f, 1f)
    val progressPct = (progressFrac * 100f).roundToInt().coerceIn(0, 100)
    val remMins = if (routeProgress?.hasArrived == true) {
        0
    } else {
        val secs = routeProgress?.remainingDurationSeconds
            ?: selectedRoute?.durationSeconds
            ?: (activeJourney.durationMinutes * 60L).toInt().takeIf { it > 0 }
            ?: (totalMeters / 6)
        (secs / 60.0).roundToInt().coerceAtLeast(1)
    }
    val speedKmh = routeProgress?.speedKmh
    val speedLabel = if (speedKmh != null && speedKmh >= 1.0) {
        "${speedKmh.roundToInt()} km/h"
    } else {
        "Live GPS"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = NirapadSurface),
        border = BorderStroke(1.dp, NirapadBorderSubtle),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().testTag("journey_trip_metrics_row"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TripStatPill(
                    label = "Remaining",
                    value = if (routeProgress?.hasArrived == true) {
                        "Arrived"
                    } else {
                        "${RoutingAndSafetyEngine.formatDistanceMeters(remMeters)} left"
                    },
                    modifier = Modifier.weight(1f).testTag("metric_remaining_distance")
                )
                TripStatPill(
                    label = "Covered",
                    value = RoutingAndSafetyEngine.formatDistanceMeters(coveredMeters),
                    modifier = Modifier.weight(1f).testTag("metric_covered_distance")
                )
                TripStatPill(
                    label = "ETA & Speed",
                    value = "$remMins min • $speedLabel",
                    modifier = Modifier.weight(1f).testTag("metric_eta_speed")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Trip Progress • $progressPct%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = "Total ${RoutingAndSafetyEngine.formatDistanceMeters(totalMeters)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = NirapadTextSecondary
                )
            }

            LinearProgressIndicator(
                progress = { progressFrac },
                color = NirapadPrimaryTeal,
                trackColor = Color(0xFFE2E8F0),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .testTag("journey_progress_bar")
            )
        }
    }
}

@Composable
private fun TripStatPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF475569),
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = Color(0xFF0F172A),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DestinationReachedModal(
    destinationLabel: String,
    totalDistanceKm: Double = 0.0,
    durationMinutes: Long = 0L,
    onCompleteAndClose: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = NirapadSurface,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.5.dp, NirapadPositiveGreen),
            tonalElevation = 6.dp,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .testTag("destination_reached_modal")
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = NirapadPositiveContainer,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = NirapadPositiveGreen,
                        modifier = Modifier.padding(12.dp).size(36.dp)
                    )
                }
                Text(
                    text = "Destination Reached — Journey Succeeded!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                if (totalDistanceKm > 0.0) {
                    Surface(
                        color = NirapadPositiveContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("modal_total_distance_summary")
                    ) {
                        val durPart = if (durationMinutes > 0L) " • $durationMinutes min" else ""
                        Text(
                            text = "Total Distance: ${RoutingAndSafetyEngine.formatDistanceKm(totalDistanceKm)}$durPart",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
                Text(
                    text = "You have arrived safely within 35 meters of $destinationLabel. Complete the journey to stop GPS tracking and close live guardian sharing.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NirapadTextSecondary
                )
                Button(
                    onClick = onCompleteAndClose,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NirapadPositiveGreen,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("complete_close_journey_button")
                ) {
                    Text("Complete & Close Journey", color = Color.White, fontWeight = FontWeight.Bold)
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("dismiss_arrival_button")
                ) {
                    Text("Keep Map Open", color = Color(0xFF0F172A))
                }
            }
        }
    }
}
