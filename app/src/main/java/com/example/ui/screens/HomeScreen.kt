package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShareLocation
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.EmergencyFacility
import com.example.data.model.Journey
import com.example.data.model.JourneyState
import com.example.data.model.LatLon
import com.example.data.model.RouteOption
import com.example.data.service.LocationReading
import com.example.data.service.RoutingAndSafetyEngine
import com.example.localization.NirapadCopy
import com.example.ui.components.DeliberateSosButton
import com.example.ui.components.NirapadMapCanvas
import com.example.ui.theme.NirapadBorderSubtle
import com.example.ui.theme.NirapadCautionAmber
import com.example.ui.theme.NirapadCautionContainer
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    copy: NirapadCopy,
    currentLocation: LocationReading,
    hasLiveGpsFix: Boolean,
    locationPermissionGranted: Boolean,
    destinationQuery: String,
    routes: List<RouteOption>,
    selectedRoute: RouteOption?,
    activeJourney: Journey?,
    facilities: List<EmergencyFacility>,
    onRequestLocationPermission: () -> Unit,
    onDestinationQueryChange: (String) -> Unit,
    onSearchRoutes: (String?) -> Unit,
    onOpenJourney: () -> Unit,
    onOpenEmergencyDirectory: () -> Unit,
    onOpenAiAdvisor: () -> Unit,
    onRequestSosConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Search Card
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
                OutlinedTextField(
                    value = destinationQuery,
                    onValueChange = onDestinationQueryChange,
                    placeholder = { Text(copy.searchDestinationHint) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("home_destination_input")
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RoutingAndSafetyEngine.destinationPresets.take(3).forEach { preset ->
                        AssistChip(
                            onClick = { onSearchRoutes(preset.label) },
                            label = { Text(preset.label, style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }

                Button(
                    onClick = { onSearchRoutes(null) },
                    colors = ButtonDefaults.buttonColors(containerColor = NirapadPrimaryTeal),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("home_compare_routes_button")
                ) {
                    Icon(Icons.Default.Directions, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(copy.compareRoutesCta, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Interactive Map Canvas
        NirapadMapCanvas(
            currentLocation = LatLon(currentLocation.lat, currentLocation.lon),
            routes = routes,
            selectedRouteId = selectedRoute?.id,
            breadcrumbs = emptyList(),
            facilities = facilities,
            isDeviated = activeJourney?.state == JourneyState.DEVIATION_DETECTED.value,
            modifier = Modifier.height(230.dp)
        )

        // Foreground Location Status & Freshness Card
        Card(
            colors = CardDefaults.cardColors(containerColor = NirapadSurface),
            border = BorderStroke(1.dp, NirapadBorderSubtle),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = if (hasLiveGpsFix) NirapadPositiveContainer else NirapadCautionContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = if (hasLiveGpsFix) Icons.Default.GpsFixed else Icons.Default.GpsOff,
                            contentDescription = null,
                            tint = if (hasLiveGpsFix) NirapadPositiveGreen else NirapadCautionAmber,
                            modifier = Modifier.padding(8.dp).size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = if (hasLiveGpsFix) "Live GPS Active" else if (locationPermissionGranted) "Waiting for GPS Fix" else "Location Permission Needed",
                            style = MaterialTheme.typography.titleSmall,
                            color = NirapadNavyDark,
                            fontWeight = FontWeight.SemiBold
                        )
                        val timeText = if (currentLocation.timestampMillis > 0L) {
                            val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                            "Last fix ${fmt.format(Date(currentLocation.timestampMillis))} • ±${currentLocation.accuracyMeters.toInt()}m"
                        } else {
                            String.format(Locale.US, "Ref %.4f, %.4f (Estimated)", currentLocation.lat, currentLocation.lon)
                        }
                        Text(text = timeText, style = MaterialTheme.typography.bodySmall, color = NirapadTextSecondary)
                    }
                }
                if (!locationPermissionGranted) {
                    OutlinedButton(
                        onClick = onRequestLocationPermission,
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("grant_location_button")
                    ) {
                        Text("Enable GPS")
                    }
                }
            }
        }

        // Active Journey Banner (if any)
        if (activeJourney != null) {
            Surface(
                onClick = onOpenJourney,
                color = NirapadPrimaryTealContainer,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, NirapadPrimaryTeal),
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Active Journey: ${activeJourney.state}",
                            style = MaterialTheme.typography.titleSmall,
                            color = NirapadNavyDark,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "To ${activeJourney.destinationLabel} • ${if (activeJourney.sharingEnabled) "Sharing ON (${activeJourney.sharedContactCount})" else "Sharing OFF"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = NirapadNavyDark
                        )
                    }
                    Text("Open HUD", style = MaterialTheme.typography.labelLarge, color = NirapadPrimaryTeal, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Quick Actions Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickActionCard(
                icon = Icons.Default.ShareLocation,
                title = "Trusted View",
                subtitle = "Open share link",
                onClick = onOpenJourney,
                modifier = Modifier.weight(1f).testTag("quick_trusted_view")
            )
            QuickActionCard(
                icon = Icons.Default.LocalHospital,
                title = "Verified Help",
                subtitle = "Police / Amb / 181",
                onClick = onOpenEmergencyDirectory,
                modifier = Modifier.weight(1f).testTag("quick_verified_help")
            )
            QuickActionCard(
                icon = Icons.Default.AutoAwesome,
                title = "AI Nav",
                subtitle = "Safer route AI",
                onClick = onOpenAiAdvisor,
                modifier = Modifier.weight(1f).testTag("quick_ai_advisor")
            )
        }

        // Visually distinct SOS section (separated from ordinary destructive controls)
        Card(
            colors = CardDefaults.cardColors(containerColor = NirapadSurface),
            border = BorderStroke(1.dp, NirapadBorderSubtle),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Emergency Assistance", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = NirapadNavyDark)
                    Text(copy.sosHoldHint, style = MaterialTheme.typography.bodySmall, color = NirapadTextSecondary)
                }
                DeliberateSosButton(
                    label = copy.emergencySosLabel,
                    onRequestConfirm = onRequestSosConfirm
                )
            }
        }

        Text(
            text = copy.safetyDisclaimer,
            style = MaterialTheme.typography.bodySmall,
            color = NirapadTextSecondary,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun QuickActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = NirapadSurface),
        border = BorderStroke(1.dp, NirapadBorderSubtle),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.minimumInteractiveComponentSize()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = NirapadPrimaryTeal, modifier = Modifier.size(22.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, color = NirapadNavyDark, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = NirapadTextSecondary)
        }
    }
}
