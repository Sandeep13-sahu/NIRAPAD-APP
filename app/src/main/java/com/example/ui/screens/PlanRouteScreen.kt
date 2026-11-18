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
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.firestore.UiState
import com.example.data.model.CrowdActivityTier
import com.example.data.model.EmergencyContact
import com.example.data.model.EmergencyFacility
import com.example.data.model.LatLon
import com.example.data.model.NearbyPoliceStation
import com.example.data.model.PlaceSuggestion
import com.example.data.model.RouteOption
import com.example.data.model.RouteSearchResult
import com.example.data.model.SafetyFactor
import com.example.data.model.WeatherContext
import com.example.data.service.LocationReading
import com.example.data.service.RoutingAndSafetyEngine
import com.example.localization.NirapadCopy
import com.example.ui.components.NirapadMapCanvas
import com.example.ui.components.NirapadOutlinedTextField
import com.example.ui.components.ProvenanceChip
import com.example.ui.components.SafetyTierBadge
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanRouteScreen(
    copy: NirapadCopy,
    currentLocation: LocationReading,
    destinationQuery: String,
    routeSearchState: UiState<RouteSearchResult>?,
    selectedRoute: RouteOption?,
    contacts: List<EmergencyContact>,
    facilities: List<EmergencyFacility>,
    onDestinationQueryChange: (String) -> Unit,
    onSearchRoutes: () -> Unit,
    onSelectRoute: (RouteOption) -> Unit,
    onStartJourney: (Boolean, Long, Long) -> Unit,
    onOpenAiAdvisor: () -> Unit,
    onOpenAdvisoryAiNavigation: () -> Unit = {},
    modifier: Modifier = Modifier,
    hasLiveGpsFix: Boolean = currentLocation.timestampMillis > 0L,
    locationPermissionGranted: Boolean = hasLiveGpsFix,
    onRequestLocationPermission: () -> Unit = {},
    weatherContext: WeatherContext? = null,
    placeSuggestions: List<PlaceSuggestion> = emptyList(),
    isSearchingPlaces: Boolean = false,
    onSelectPlaceSuggestion: (PlaceSuggestion) -> Unit = {},
    nearbyPoliceStations: List<NearbyPoliceStation> = emptyList()
) {
    var expandedRouteId by remember { mutableStateOf<String?>(null) }
    var sharingEnabled by remember(contacts.size) { mutableStateOf(contacts.isNotEmpty()) }
    var shareDurationMinutes by remember { mutableLongStateOf(60L) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Plan & Compare Routes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = NirapadNavyDark)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NirapadOutlinedTextField(
                value = destinationQuery,
                onValueChange = onDestinationQueryChange,
                placeholder = { Text(copy.searchDestinationHint) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).testTag("plan_destination_input")
            )
            Button(
                onClick = onSearchRoutes,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NirapadPrimaryTeal,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(54.dp).minimumInteractiveComponentSize().testTag("plan_search_button")
            ) {
                Text("Go", fontWeight = FontWeight.Bold)
            }
        }

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

        OutlinedButton(
            onClick = onOpenAdvisoryAiNavigation,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = NirapadPrimaryTeal),
            border = BorderStroke(1.dp, NirapadPrimaryTeal),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("plan_open_advisory_ai_button")
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = NirapadPrimaryTeal, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(copy.advisoryCompareCta, color = NirapadPrimaryTeal, fontWeight = FontWeight.SemiBold)
        }

        when (routeSearchState) {
            null -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = NirapadSurface),
                    border = BorderStroke(1.dp, NirapadBorderSubtle),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Transparent Route Comparison", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = NirapadNavyDark)
                        Text(
                            "Search a destination to fetch OSRM route alternatives, ETA, distance, and explainable safety factor coverage.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NirapadTextSecondary
                        )
                        OutlinedButton(
                            onClick = onSearchRoutes,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NirapadNavyDark),
                            border = BorderStroke(1.dp, Color(0xFF94A3B8)),
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text("Load Routes to $destinationQuery")
                        }
                    }
                }
            }
            UiState.Loading -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(color = NirapadPrimaryTeal)
                    Spacer(Modifier.width(12.dp))
                    Text("Fetching routes and assessing safety factors...")
                }
            }
            is UiState.Error -> {
                Surface(color = NirapadEmergencyContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(routeSearchState.message, color = NirapadEmergencyRed, style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = onSearchRoutes) { Text("Retry") }
                    }
                }
            }
            is UiState.Success -> {
                val data = routeSearchState.data
                // Provider attribution & fallback warning
                Surface(
                    color = if (data.hasProviderWarning) NirapadCautionContainer else NirapadPrimaryTealContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (data.hasProviderWarning) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = NirapadCautionAmber, modifier = Modifier.size(18.dp))
                        }
                        Text(
                            text = data.providerBanner,
                            style = MaterialTheme.typography.bodySmall,
                            color = NirapadNavyDark
                        )
                    }
                }

                val activeWeather = data.weatherContext ?: weatherContext
                if (activeWeather != null && (!activeWeather.isDay || activeWeather.precipitationMm >= 0.5)) {
                    Surface(
                        color = NirapadCautionContainer,
                        border = BorderStroke(1.dp, NirapadCautionAmber.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("plan_weather_warning_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.NightsStay, contentDescription = null, tint = NirapadCautionAmber, modifier = Modifier.size(18.dp))
                            Text(
                                text = "${activeWeather.temperatureCelsius.toInt()}°C • ${activeWeather.conditionLabel}: ${if (!activeWeather.isDay) "After-dark lighting & busy market stretches prioritized." else "Wet-weather caution enabled along isolated stretches."}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF0F172A)
                            )
                        }
                    }
                }

                NirapadMapCanvas(
                    currentLocation = LatLon(currentLocation.lat, currentLocation.lon),
                    routes = data.routes,
                    selectedRouteId = selectedRoute?.id,
                    breadcrumbs = emptyList(),
                    facilities = facilities,
                    isDeviated = false,
                    bearingDegrees = currentLocation.bearingDegrees,
                    hasLiveGpsFix = hasLiveGpsFix,
                    accuracyMeters = currentLocation.accuracyMeters,
                    locationPermissionGranted = locationPermissionGranted,
                    autoFollowByDefault = false,
                    fullscreenTitle = "Routes to ${data.destinationLabel}",
                    destinationQuery = destinationQuery,
                    onDestinationQueryChange = onDestinationQueryChange,
                    onSearchDestination = { onSearchRoutes() },
                    onRequestLocationPermission = onRequestLocationPermission,
                    nearbyPoliceStations = nearbyPoliceStations,
                    modifier = Modifier.height(190.dp)
                )

                // Route comparison cards
                data.routes.forEach { route ->
                    val isSelected = selectedRoute?.id == route.id
                    val isExpanded = expandedRouteId == route.id
                    RouteOptionCard(
                        route = route,
                        isSelected = isSelected,
                        isExpanded = isExpanded,
                        onSelect = { onSelectRoute(route) },
                        onToggleDetails = {
                            expandedRouteId = if (isExpanded) null else route.id
                        },
                        onAskAi = onOpenAiAdvisor
                    )
                }

                // Start Journey & Opt-In Sharing Configuration
                if (selectedRoute != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = NirapadSurface),
                        border = BorderStroke(1.dp, NirapadPrimaryTeal),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Configure Sharing Before Start",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = NirapadNavyDark
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Share live progress with trusted contacts", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = if (contacts.isEmpty()) "No trusted contacts added yet (optional)" else "${contacts.count { it.notifyOnStart }} contact(s) opted in",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NirapadTextSecondary
                                    )
                                }
                                Switch(
                                    checked = sharingEnabled,
                                    onCheckedChange = { sharingEnabled = it },
                                    modifier = Modifier.testTag("plan_share_toggle")
                                )
                            }

                            if (sharingEnabled) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(30L, 60L, 120L).forEach { mins ->
                                        FilterChip(
                                            selected = shareDurationMinutes == mins,
                                            onClick = { shareDurationMinutes = mins },
                                            colors = FilterChipDefaults.filterChipColors(
                                                containerColor = Color(0xFFF1F5F9),
                                                labelColor = Color(0xFF0F172A),
                                                selectedContainerColor = NirapadPrimaryTealContainer,
                                                selectedLabelColor = NirapadNavyDark,
                                                selectedLeadingIconColor = NirapadPrimaryTeal
                                            ),
                                            border = FilterChipDefaults.filterChipBorder(
                                                enabled = true,
                                                selected = shareDurationMinutes == mins,
                                                borderColor = Color(0xFF94A3B8),
                                                selectedBorderColor = NirapadPrimaryTeal
                                            ),
                                            label = { Text("${mins}m expiry") }
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    val count = if (sharingEnabled) contacts.count { it.notifyOnStart }.toLong().coerceAtLeast(1L) else 0L
                                    onStartJourney(sharingEnabled, count, shareDurationMinutes)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NirapadPrimaryTeal,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .minimumInteractiveComponentSize()
                                    .testTag("start_protected_journey_button")
                            ) {
                                Icon(Icons.Default.Navigation, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(copy.startProtectedJourney, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RouteOptionCard(
    route: RouteOption,
    isSelected: Boolean,
    isExpanded: Boolean,
    onSelect: () -> Unit,
    onToggleDetails: () -> Unit,
    onAskAi: () -> Unit
) {
    Card(
        onClick = onSelect,
        colors = CardDefaults.cardColors(containerColor = NirapadSurface),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) NirapadPrimaryTeal else NirapadBorderSubtle),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().testTag("route_card_${route.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(route.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    Text(route.providerName, style = MaterialTheme.typography.bodySmall, color = NirapadTextSecondary)
                }
                Surface(
                    color = Color(0xFFF1F5F9),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("route_distance_eta_${route.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.DirectionsWalk, contentDescription = null, tint = NirapadPrimaryTeal, modifier = Modifier.size(16.dp))
                        Text(
                            text = "${RoutingAndSafetyEngine.formatDistanceMeters(route.distanceMeters)} • ${RoutingAndSafetyEngine.formatDurationMinutes(route.durationSeconds)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SafetyTierBadge(tier = route.safetyTier, label = route.safetyLabel)
                val (crowdBg, crowdFg) = when (route.crowdActivityTier) {
                    CrowdActivityTier.HIGH_CROWD_WELL_LIT -> NirapadPositiveContainer to NirapadPositiveGreen
                    CrowdActivityTier.MODERATE_ACTIVITY -> NirapadPrimaryTealContainer to NirapadNavyDark
                    CrowdActivityTier.LOW_CROWD_ISOLATED -> NirapadCautionContainer to NirapadCautionAmber
                }
                Surface(
                    color = crowdBg,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("route_crowd_tier_${route.id}")
                ) {
                    Text(
                        text = route.crowdActivityTier.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = crowdFg,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(route.summary, style = MaterialTheme.typography.bodyMedium, color = NirapadTextSecondary)

            if (route.crowdPoiCount > 0 || route.litCount > 0 || route.nearbyPoliceCount > 0) {
                Text(
                    text = "Overpass corridor signals: ${route.crowdPoiCount} active POIs • ${route.litCount} lit tags • ${route.nearbyPoliceCount} police",
                    style = MaterialTheme.typography.labelSmall,
                    color = NirapadPrimaryTeal,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(route.freshnessNote, style = MaterialTheme.typography.labelSmall, color = NirapadTextSecondary)
                TextButton(
                    onClick = onToggleDetails,
                    colors = ButtonDefaults.textButtonColors(contentColor = NirapadPrimaryTeal),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text(if (isExpanded) "Hide factors" else "Explain safety factors")
                    Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                }
            }

            if (isExpanded) {
                HorizontalDivider(color = NirapadBorderSubtle)
                Text(
                    text = route.caveat,
                    style = MaterialTheme.typography.bodySmall,
                    color = NirapadCautionAmber,
                    fontWeight = FontWeight.Medium
                )
                route.factors.forEach { factor ->
                    FactorRow(factor)
                }
                if (route.steps.isNotEmpty()) {
                    HorizontalDivider(color = NirapadBorderSubtle)
                    Text(
                        text = "Turn-by-Turn Preview (${route.steps.size} steps)",
                        style = MaterialTheme.typography.labelLarge,
                        color = NirapadNavyDark,
                        fontWeight = FontWeight.Bold
                    )
                    route.steps.take(4).forEach { step ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.TurnRight, contentDescription = null, tint = NirapadPrimaryTeal, modifier = Modifier.size(16.dp))
                            Text(
                                text = "${step.instruction} (${step.distanceMeters} m)",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF0F172A)
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = onAskAi,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NirapadPrimaryTeal),
                    border = BorderStroke(1.dp, NirapadPrimaryTeal),
                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Ask AI Advisor about this route")
                }
            }
        }
    }
}

@Composable
private fun FactorRow(factor: SafetyFactor) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(factor.title, style = MaterialTheme.typography.labelLarge, color = NirapadNavyDark, modifier = Modifier.weight(1f))
            ProvenanceChip(provenance = factor.provenance)
        }
        Text(
            text = "${factor.statusLabel} • ${factor.freshnessLabel}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = NirapadPrimaryTeal
        )
        Text(factor.explanation, style = MaterialTheme.typography.bodySmall, color = NirapadTextSecondary)
    }
}
