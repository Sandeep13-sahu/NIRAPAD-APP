package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.firestore.UiState
import com.example.data.model.AdvisoryNavigationComparison
import com.example.data.model.AdvisoryRouteComparisonItem
import com.example.data.model.EmergencyFacility
import com.example.data.model.LatLon
import com.example.data.model.RouteOption
import com.example.data.model.RouteSearchResult
import com.example.data.repository.AiAdvisorUiState
import com.example.data.service.LocationReading
import com.example.data.service.RoutingAndSafetyEngine
import com.example.localization.NirapadCopy
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
import com.example.ui.theme.NirapadSurfaceVariant
import com.example.ui.theme.NirapadTextSecondary
import java.util.Locale

private val TRAVEL_WINDOWS = listOf(
    "Current Time" to "advisory_window_current",
    "Evening / Dusk" to "advisory_window_evening",
    "Late Night" to "advisory_window_night"
)

@Composable
fun AdvisoryAiNavigationScreen(
    copy: NirapadCopy,
    currentLocation: LocationReading,
    hasLiveGpsFix: Boolean,
    locationPermissionGranted: Boolean,
    destinationQuery: String,
    travelWindowLabel: String,
    routeSearchState: UiState<RouteSearchResult>?,
    advisoryComparisonState: UiState<AdvisoryNavigationComparison>?,
    selectedRoute: RouteOption?,
    facilities: List<EmergencyFacility>,
    aiChatState: AiAdvisorUiState,
    onDestinationQueryChange: (String) -> Unit,
    onTravelWindowChange: (String) -> Unit,
    onRequestLocationPermission: () -> Unit,
    onCompareWithAi: (String?, String) -> Unit,
    onSelectRoute: (RouteOption) -> Unit,
    onStartRouteJourney: (RouteOption) -> Unit,
    onAskFollowUp: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var followUpQuestion by remember { mutableStateOf("") }
    val searchResult = (routeSearchState as? UiState.Success)?.data
    val candidateRoutesById = remember(searchResult) {
        searchResult?.routes?.associateBy { it.id } ?: emptyMap()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 96.dp)
            .testTag("advisory_ai_navigation_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("advisory_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = NirapadNavyDark
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = copy.advisoryNavTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = NirapadNavyDark
                    )
                    Text(
                        text = copy.advisoryNavSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = NirapadTextSecondary
                    )
                }
                Surface(
                    color = NirapadPrimaryTealContainer,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = NirapadPrimaryTeal,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Gemini",
                            style = MaterialTheme.typography.labelMedium,
                            color = NirapadPrimaryTeal,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        // Advisory Boundary Banner
        Surface(
            color = NirapadCautionContainer,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, NirapadCautionAmber.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = copy.aiAdvisoryBanner,
                style = MaterialTheme.typography.bodySmall,
                color = NirapadNavyDark,
                modifier = Modifier.padding(12.dp)
            )
        }

        // Destination + Travel Context + Trigger Card
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
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (hasLiveGpsFix) Icons.Default.GpsFixed else Icons.Default.GpsOff,
                                contentDescription = null,
                                tint = if (hasLiveGpsFix) NirapadPositiveGreen else NirapadCautionAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (hasLiveGpsFix) {
                                    String.format(Locale.US, "Origin: Live GPS (%.4f, %.4f)", currentLocation.lat, currentLocation.lon)
                                } else {
                                    String.format(Locale.US, "Origin: Reference (%.4f, %.4f)", currentLocation.lat, currentLocation.lon)
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = NirapadTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (!locationPermissionGranted) {
                            OutlinedButton(
                                onClick = onRequestLocationPermission,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text("Enable GPS", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = destinationQuery,
                        onValueChange = onDestinationQueryChange,
                        label = { Text("Destination") },
                        placeholder = { Text(copy.searchDestinationHint) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (destinationQuery.isNotEmpty()) {
                                IconButton(onClick = { onDestinationQueryChange("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear destination")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("advisory_destination_input")
                    )

                    // Destination presets
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RoutingAndSafetyEngine.destinationPresets.forEach { preset ->
                            AssistChip(
                                onClick = {
                                    onDestinationQueryChange(preset.label)
                                    onCompareWithAi(preset.label, travelWindowLabel)
                                },
                                label = { Text(preset.label, style = MaterialTheme.typography.labelMedium) }
                            )
                        }
                    }

                    Text(
                        text = "Lighting & Traffic Window",
                        style = MaterialTheme.typography.labelMedium,
                        color = NirapadNavyDark,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TRAVEL_WINDOWS.forEach { (label, tag) ->
                            FilterChip(
                                selected = travelWindowLabel == label,
                                onClick = { onTravelWindowChange(label) },
                                label = { Text(label) },
                                modifier = Modifier.testTag(tag)
                            )
                        }
                    }

                    Button(
                        onClick = { onCompareWithAi(destinationQuery, travelWindowLabel) },
                        colors = ButtonDefaults.buttonColors(containerColor = NirapadPrimaryTeal),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("advisory_compare_button")
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(copy.advisoryCompareCta, fontWeight = FontWeight.Bold)
                    }
                }
            }
        // Route Map Preview when routes exist
        if (searchResult != null && searchResult.routes.isNotEmpty()) {
            NirapadMapCanvas(
                currentLocation = LatLon(currentLocation.lat, currentLocation.lon),
                routes = searchResult.routes,
                selectedRouteId = selectedRoute?.id
                    ?: (advisoryComparisonState as? UiState.Success)?.data?.recommendedRouteId,
                breadcrumbs = emptyList(),
                facilities = facilities,
                isDeviated = false,
                modifier = Modifier.height(185.dp)
            )
        }

        // Gemini Comparison Results / Status
        when (advisoryComparisonState) {
            null -> {
                Card(
                        colors = CardDefaults.cardColors(containerColor = NirapadSurface),
                        border = BorderStroke(1.dp, NirapadBorderSubtle),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "How Gemini Advisory Navigation Evaluates Routes",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = NirapadNavyDark
                            )
                            EvaluationPillarRow(
                                icon = Icons.Default.Shield,
                                title = "Safety Score & Coverage",
                                description = "Combines moderated safety reports, emergency directory proximity, and telemetry coverage."
                            )
                            EvaluationPillarRow(
                                icon = Icons.Default.LightMode,
                                title = "Street Lighting Data",
                                description = "Prioritizes lit main corridors and highlights inner lanes where lighting audits are unavailable."
                            )
                            EvaluationPillarRow(
                                icon = Icons.Default.DirectionsBus,
                                title = "Public Traffic & Footfall",
                                description = "Favors active transit hubs and commercial stretches over isolated shortcuts."
                            )
                        }
                    }
            }
            UiState.Loading -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = NirapadSurface),
                    border = BorderStroke(1.dp, NirapadPrimaryTeal.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("advisory_loading_card")
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        CircularProgressIndicator(
                            color = NirapadPrimaryTeal,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 3.dp
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Analyzing Safer Route Options...",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = NirapadNavyDark
                            )
                            Text(
                                text = "Gemini is comparing safety scores, street lighting, and public traffic for $destinationQuery.",
                                style = MaterialTheme.typography.bodySmall,
                                color = NirapadTextSecondary
                            )
                        }
                    }
                }
            }

            is UiState.Error -> {
                Surface(
                    color = NirapadEmergencyContainer,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, NirapadEmergencyRed.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("advisory_error_banner")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = NirapadEmergencyRed
                            )
                            Text(
                                text = "Gemini AI Comparison Unavailable",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = NirapadEmergencyRed
                            )
                        }
                        Text(
                            text = advisoryComparisonState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = NirapadNavyDark
                        )
                        OutlinedButton(
                            onClick = { onCompareWithAi(destinationQuery, travelWindowLabel) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("advisory_retry_button")
                        ) {
                            Text("Retry AI Comparison")
                        }
                    }
                }
            }

            is UiState.Success -> {
                val comparison = advisoryComparisonState.data
                Card(
                    colors = CardDefaults.cardColors(containerColor = NirapadPositiveContainer),
                    border = BorderStroke(1.dp, NirapadPositiveGreen.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("advisory_verdict_card")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = NirapadPositiveGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Gemini Safer Route Verdict (${comparison.travelWindowLabel})",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = NirapadPositiveGreen
                            )
                        }
                        Text(
                            text = comparison.headlineSummary,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = NirapadNavyDark
                        )
                        Text(
                            text = comparison.comparisonOverview,
                            style = MaterialTheme.typography.bodySmall,
                            color = NirapadNavyDark
                        )
                    }
                }

                comparison.routeComparisons.forEach { item ->
                    val matchingRoute = candidateRoutesById[item.routeId]
                    val isSelected = selectedRoute?.id == item.routeId
                    AdvisoryRouteComparisonCard(
                        item = item,
                        routeOption = matchingRoute,
                        isSelected = isSelected,
                        onSelect = {
                            if (matchingRoute != null) onSelectRoute(matchingRoute)
                        },
                        onStartJourney = {
                            if (matchingRoute != null) {
                                onSelectRoute(matchingRoute)
                                onStartRouteJourney(matchingRoute)
                            }
                        }
                    )
                }
            }
        }

        // Follow-up AI Questions Card
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
                    Text(
                        text = "Ask Gemini Follow-Up Questions",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = NirapadNavyDark
                    )

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "Which route has better street lighting?",
                            "Where is public traffic busiest right now?",
                            "What precautions help on lower-lit segments?"
                        ).forEach { prompt ->
                            AssistChip(
                                onClick = {
                                    followUpQuestion = prompt
                                    onAskFollowUp(prompt)
                                },
                                label = { Text(prompt, style = MaterialTheme.typography.labelMedium) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = followUpQuestion,
                            onValueChange = { followUpQuestion = it },
                            placeholder = { Text("Ask about lighting, crowd, or checkpoints...") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("advisory_followup_input")
                        )
                        Button(
                            onClick = { onAskFollowUp(followUpQuestion) },
                            colors = ButtonDefaults.buttonColors(containerColor = NirapadPrimaryTeal),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier
                                .size(50.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("advisory_followup_send")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send question",
                                tint = Color.White
                            )
                        }
                    }

                    when (aiChatState) {
                        AiAdvisorUiState.Idle -> Unit
                        AiAdvisorUiState.Loading -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text("Generating advisory guidance...", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        is AiAdvisorUiState.Streaming -> {
                            Surface(
                                color = NirapadSurfaceVariant,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = aiChatState.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NirapadNavyDark,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                        is AiAdvisorUiState.Success -> {
                            Surface(
                                color = NirapadPrimaryTealContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("advisory_followup_response")
                            ) {
                                Text(
                                    text = aiChatState.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NirapadNavyDark,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                        is AiAdvisorUiState.Error -> {
                            Surface(
                                color = NirapadEmergencyContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = aiChatState.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NirapadEmergencyRed,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }
    }
}

@Composable
private fun EvaluationPillarRow(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            color = NirapadPrimaryTealContainer,
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = NirapadPrimaryTeal,
                modifier = Modifier.padding(8.dp).size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = NirapadNavyDark)
            Text(description, style = MaterialTheme.typography.bodySmall, color = NirapadTextSecondary)
        }
    }
}

@Composable
private fun AdvisoryRouteComparisonCard(
    item: AdvisoryRouteComparisonItem,
    routeOption: RouteOption?,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onStartJourney: () -> Unit
) {
    val borderColor = when {
        isSelected -> NirapadPrimaryTeal
        item.isSaferOption -> NirapadPositiveGreen
        else -> NirapadBorderSubtle
    }

    Card(
        onClick = onSelect,
        colors = CardDefaults.cardColors(containerColor = NirapadSurface),
        border = BorderStroke(if (isSelected || item.isSaferOption) 2.dp else 1.dp, borderColor),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("advisory_route_card_${item.routeId}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = if (item.isSaferOption) NirapadPositiveContainer else NirapadSurfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (item.isSaferOption) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = NirapadPositiveGreen,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = if (item.isSaferOption) "#${item.recommendedRank} Safer Option" else "Rank #${item.recommendedRank} Alternative",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (item.isSaferOption) NirapadPositiveGreen else NirapadTextSecondary
                        )
                    }
                }

                if (routeOption != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                            contentDescription = null,
                            tint = NirapadPrimaryTeal,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${(routeOption.durationSeconds / 60).coerceAtLeast(1)} min • ${String.format(Locale.US, "%.1f km", routeOption.distanceMeters / 1000.0)}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = NirapadNavyDark
                        )
                    }
                }
            }

            Text(
                text = item.routeLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = NirapadNavyDark
            )

            // Three core metrics: Safety Score, Street Lighting Data, Public Traffic
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricTile(
                    icon = Icons.Default.Shield,
                    label = "Safety Score",
                    value = "${item.overallSafetyScore}/100",
                    tint = if (item.overallSafetyScore >= 72) NirapadPositiveGreen else NirapadCautionAmber,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    icon = Icons.Default.LightMode,
                    label = "Street Lighting",
                    value = if (item.streetLightingScore > 0) "${item.streetLightingRating} (${item.streetLightingScore})" else item.streetLightingRating,
                    tint = if (item.streetLightingScore >= 70) NirapadPositiveGreen else NirapadCautionAmber,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    icon = Icons.Default.DirectionsBus,
                    label = "Public Traffic",
                    value = "${item.publicTrafficLevel} (${item.publicTrafficScore})",
                    tint = NirapadPrimaryTeal,
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = item.aiRationale,
                style = MaterialTheme.typography.bodyMedium,
                color = NirapadNavyDark
            )

            Surface(
                color = NirapadSurfaceVariant,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Tip: ${item.precautionTip}",
                    style = MaterialTheme.typography.bodySmall,
                    color = NirapadTextSecondary,
                    modifier = Modifier.padding(10.dp)
                )
            }

            if (routeOption != null) {
                HorizontalDivider(color = NirapadBorderSubtle)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onSelect,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("advisory_select_${item.routeId}")
                    ) {
                        Text(if (isSelected) "Selected" else "Select Route", maxLines = 1)
                    }
                    Button(
                        onClick = onStartJourney,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (item.isSaferOption) NirapadPositiveGreen else NirapadPrimaryTeal
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("advisory_start_${item.routeId}")
                    ) {
                        Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Use Route", maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricTile(
    icon: ImageVector,
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = NirapadSurfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = NirapadTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = NirapadNavyDark,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
