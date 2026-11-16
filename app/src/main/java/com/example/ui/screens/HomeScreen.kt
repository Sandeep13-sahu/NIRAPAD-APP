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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShareLocation
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.EmergencyFacility
import com.example.data.model.Journey
import com.example.data.model.JourneyState
import com.example.data.model.LatLon
import com.example.data.model.NearbyPoliceStation
import com.example.data.model.PlaceSuggestion
import com.example.data.model.RouteOption
import com.example.data.model.WeatherContext
import com.example.data.service.LocationReading
import com.example.data.service.RoutingAndSafetyEngine
import com.example.localization.NirapadCopy
import com.example.ui.components.DeliberateSosButton
import com.example.ui.components.NirapadMapCanvas
import com.example.ui.components.NirapadOutlinedTextField
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
    modifier: Modifier = Modifier,
    currentAddressLabel: String? = null,
    weatherContext: WeatherContext? = null,
    placeSuggestions: List<PlaceSuggestion> = emptyList(),
    isSearchingPlaces: Boolean = false,
    onSelectPlaceSuggestion: (PlaceSuggestion) -> Unit = {},
    onDismissSuggestions: () -> Unit = {},
    nearbyPoliceStations: List<NearbyPoliceStation> = emptyList()
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 112.dp),
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
                NirapadOutlinedTextField(
                    value = destinationQuery,
                    onValueChange = onDestinationQueryChange,
                    placeholder = { Text(copy.searchDestinationHint) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (destinationQuery.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    onDestinationQueryChange("")
                                    onDismissSuggestions()
                                }
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear destination")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("home_destination_input")
                )

                if (isSearchingPlaces) {
                    LinearProgressIndicator(
                        color = NirapadPrimaryTeal,
                        modifier = Modifier.fillMaxWidth().height(2.dp)
                    )
                }

                if (placeSuggestions.isNotEmpty()) {
                    PlaceAutocompleteSuggestionsCard(
                        suggestions = placeSuggestions,
                        onSelectSuggestion = onSelectPlaceSuggestion
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RoutingAndSafetyEngine.destinationPresets.take(3).forEach { preset ->
                        AssistChip(
                            onClick = { onSearchRoutes(preset.label) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = Color(0xFFF1F5F9),
                                labelColor = Color(0xFF0F172A),
                                leadingIconContentColor = NirapadPrimaryTeal
                            ),
                            border = AssistChipDefaults.assistChipBorder(
                                enabled = true,
                                borderColor = Color(0xFF94A3B8)
                            ),
                            label = {
                                Text(
                                    text = preset.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFF0F172A),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        )
                    }
                }

                Button(
                    onClick = { onSearchRoutes(null) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NirapadPrimaryTeal,
                        contentColor = Color.White
                    ),
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
            bearingDegrees = currentLocation.bearingDegrees,
            hasLiveGpsFix = hasLiveGpsFix,
            accuracyMeters = currentLocation.accuracyMeters,
            locationPermissionGranted = locationPermissionGranted,
            autoFollowByDefault = routes.isEmpty() || activeJourney != null,
            fullscreenTitle = activeJourney?.let { "Active Journey • ${it.destinationLabel}" } ?: "NIRAPAD Live Safety Map",
            fullscreenSubtitle = currentAddressLabel,
            destinationQuery = destinationQuery,
            onDestinationQueryChange = onDestinationQueryChange,
            onSearchDestination = onSearchRoutes,
            onRequestLocationPermission = onRequestLocationPermission,
            nearbyPoliceStations = nearbyPoliceStations,
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
                            text = when {
                                !currentAddressLabel.isNullOrBlank() -> currentAddressLabel
                                hasLiveGpsFix -> "Live GPS Active"
                                locationPermissionGranted -> "Waiting for GPS Fix"
                                else -> "Location Permission Needed"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = NirapadNavyDark,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("home_current_address_text")
                        )
                        val timeText = when {
                            currentLocation.timestampMillis > 0L -> {
                                val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                "Live fix ${fmt.format(Date(currentLocation.timestampMillis))} • ±${currentLocation.accuracyMeters.toInt()}m"
                            }
                            locationPermissionGranted -> "Waiting for FusedLocationProviderClient GPS fix..."
                            else -> String.format(
                                Locale.US,
                                "Ref %.4f, %.4f (Grant GPS for live coordinates)",
                                currentLocation.lat,
                                currentLocation.lon
                            )
                        }
                        Text(text = timeText, style = MaterialTheme.typography.bodySmall, color = NirapadTextSecondary)

                        if (weatherContext != null) {
                            Spacer(Modifier.height(4.dp))
                            Surface(
                                color = if (!weatherContext.isDay) Color(0xFFFEF3C7) else NirapadPrimaryTealContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("home_weather_badge")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = if (!weatherContext.isDay) Icons.Default.NightsStay else Icons.Default.WbSunny,
                                        contentDescription = null,
                                        tint = if (!weatherContext.isDay) NirapadCautionAmber else NirapadPrimaryTeal,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = buildString {
                                            append("${weatherContext.temperatureCelsius.toInt()}°C • ${weatherContext.conditionLabel}")
                                            if (!weatherContext.isDay) append(" • Night safety priority")
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF0F172A)
                                    )
                                }
                            }
                        }
                    }
                }
                if (!locationPermissionGranted) {
                    OutlinedButton(
                        onClick = onRequestLocationPermission,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NirapadPrimaryTeal),
                        border = BorderStroke(1.dp, NirapadPrimaryTeal),
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("grant_location_button")
                    ) {
                        Text("Enable GPS", fontWeight = FontWeight.SemiBold)
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

@Composable
internal fun PlaceAutocompleteSuggestionsCard(
    suggestions: List<PlaceSuggestion>,
    onSelectSuggestion: (PlaceSuggestion) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFFFFFFFF),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF94A3B8)),
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth().testTag("autocomplete_suggestions_list")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            suggestions.forEachIndexed { idx, suggestion ->
                Surface(
                    onClick = { onSelectSuggestion(suggestion) },
                    color = Color(0xFFFFFFFF),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("autocomplete_suggestion_$idx")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = NirapadPrimaryTeal,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = suggestion.shortName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF0F172A),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = suggestion.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF475569),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (idx < suggestions.lastIndex) {
                    HorizontalDivider(color = Color(0xFFE2E8F0))
                }
            }
        }
    }
}
