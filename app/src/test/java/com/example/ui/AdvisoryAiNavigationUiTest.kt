package com.example.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.data.firestore.UiState
import com.example.data.model.AdvisoryNavigationComparison
import com.example.data.model.AdvisoryRouteComparisonItem
import com.example.data.model.DataProvenance
import com.example.data.model.LatLon
import com.example.data.model.RouteOption
import com.example.data.model.RouteSearchResult
import com.example.data.model.SafetyFactor
import com.example.data.model.SafetyTier
import com.example.data.repository.AiAdvisorUiState
import com.example.data.service.LocationReading
import com.example.localization.AppLang
import com.example.localization.NirapadStrings
import com.example.ui.screens.AdvisoryAiNavigationScreen
import com.example.ui.theme.NirapadTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp")
class AdvisoryAiNavigationUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val origin = LocationReading(28.6315, 77.2167, 12.0, 1_700_000_000_000L)

    private val route1 = RouteOption(
        id = "route_1",
        label = "Main Lit Arterial Corridor",
        summary = "Follows lit main roads and active transit stops.",
        distanceMeters = 3100,
        durationSeconds = 1800,
        providerName = "OSRM Pedestrian/Road Graph",
        providerIsFallback = false,
        safetyTier = SafetyTier.HIGHER_CONTEXT,
        safetyLabel = "Higher relative safety context",
        dataCoveragePercent = 100,
        freshnessNote = "Live route",
        caveat = "Decision support only.",
        factors = listOf(
            SafetyFactor("lighting", "Street Lighting", "Well-lit corridor", 86, "Municipal LED", DataProvenance.ESTIMATED, "Baseline"),
            SafetyFactor("footfall", "Public Traffic", "Active metro corridor", 84, "High transit flow", DataProvenance.ESTIMATED, "Live")
        ),
        geometry = listOf(LatLon(28.6315, 77.2167), LatLon(28.5433, 77.2066))
    )

    private val route2 = RouteOption(
        id = "route_2",
        label = "Quiet Inner Shortcut",
        summary = "Lighting telemetry unavailable for inner lanes.",
        distanceMeters = 2400,
        durationSeconds = 1440,
        providerName = "OSRM Pedestrian/Road Graph",
        providerIsFallback = false,
        safetyTier = SafetyTier.LIMITED_DATA,
        safetyLabel = "Route has limited safety data",
        dataCoveragePercent = 75,
        freshnessNote = "Live route",
        caveat = "Decision support only.",
        factors = listOf(
            SafetyFactor("lighting", "Street Lighting", "Unavailable", null, "Missing feed", DataProvenance.UNAVAILABLE, "None"),
            SafetyFactor("footfall", "Public Traffic", "Low evening traffic", 42, "Quiet lane", DataProvenance.ESTIMATED, "Live")
        ),
        geometry = listOf(LatLon(28.6315, 77.2167), LatLon(28.5433, 77.2066))
    )

    @Test
    fun destinationCompare_displaysSaferRouteWithLightingAndTrafficMetrics() {
        val copy = NirapadStrings.get(AppLang.EN)
        var capturedDestination: String? = null
        var capturedWindow = ""
        var startedRouteId = ""

        var destination by mutableStateOf("Rajiv Chowk Metro")
        var travelWindow by mutableStateOf("Evening / Dusk")
        var comparisonState by mutableStateOf<UiState<AdvisoryNavigationComparison>?>(null)

        val searchResult = RouteSearchResult(
            originLabel = "Current GPS Location",
            origin = LatLon(origin.lat, origin.lon),
            destinationLabel = "AIIMS Main Gate",
            destination = LatLon(28.5672, 77.2100),
            routes = listOf(route1, route2),
            providerBanner = "Live OSRM corridors",
            hasProviderWarning = false
        )

        val comparisonResult = AdvisoryNavigationComparison(
            destinationLabel = "AIIMS Main Gate",
            travelWindowLabel = "Evening / Dusk",
            recommendedRouteId = "route_1",
            headlineSummary = "Main Lit Arterial Corridor ranks #1 safer for evening travel.",
            comparisonOverview = "Higher street lighting (86/100) and busy metro traffic (84/100).",
            routeComparisons = listOf(
                AdvisoryRouteComparisonItem(
                    routeId = "route_1",
                    routeLabel = "Main Lit Arterial Corridor",
                    overallSafetyScore = 86,
                    streetLightingRating = "Well-Lit Arterial",
                    streetLightingScore = 86,
                    publicTrafficLevel = "Active Metro Flow",
                    publicTrafficScore = 84,
                    recommendedRank = 1,
                    isSaferOption = true,
                    aiRationale = "Consistent street lighting and steady public transit activity along main road.",
                    precautionTip = "Stay along main exits with active metro lighting."
                ),
                AdvisoryRouteComparisonItem(
                    routeId = "route_2",
                    routeLabel = "Quiet Inner Shortcut",
                    overallSafetyScore = 46,
                    streetLightingRating = "Limited Data",
                    streetLightingScore = 0,
                    publicTrafficLevel = "Quiet Residential",
                    publicTrafficScore = 42,
                    recommendedRank = 2,
                    isSaferOption = false,
                    aiRationale = "Shorter path lacks municipal street lighting telemetry and evening foot traffic.",
                    precautionTip = "Prefer main corridor after sunset."
                )
            )
        )

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            NirapadTheme {
                AdvisoryAiNavigationScreen(
                    copy = copy,
                    currentLocation = origin,
                    hasLiveGpsFix = true,
                    locationPermissionGranted = true,
                    destinationQuery = destination,
                    travelWindowLabel = travelWindow,
                    routeSearchState = UiState.Success(searchResult),
                    advisoryComparisonState = comparisonState,
                    selectedRoute = route1,
                    facilities = emptyList(),
                    aiChatState = AiAdvisorUiState.Idle,
                    onDestinationQueryChange = { destination = it },
                    onTravelWindowChange = { travelWindow = it },
                    onRequestLocationPermission = {},
                    onCompareWithAi = { dest, win ->
                        capturedDestination = dest
                        capturedWindow = win
                        comparisonState = UiState.Success(comparisonResult)
                    },
                    onSelectRoute = {},
                    onStartRouteJourney = { startedRouteId = it.id },
                    onAskFollowUp = {},
                    onNavigateBack = {}
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(150)

        composeRule.onNodeWithContentDescription("Clear destination").performClick()
        composeRule.mainClock.advanceTimeBy(150)
        composeRule.onNodeWithTag("advisory_destination_input").performScrollTo().performTextInput("AIIMS Main Gate")
        composeRule.mainClock.advanceTimeBy(150)

        composeRule.onNodeWithTag("advisory_compare_button").performScrollTo().performClick()
        composeRule.mainClock.advanceTimeBy(150)

        assertEquals("AIIMS Main Gate", capturedDestination)
        assertEquals("Evening / Dusk", capturedWindow)

        composeRule.onNodeWithTag("advisory_verdict_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("advisory_route_card_route_1").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("#1 Safer Option").assertIsDisplayed()
        composeRule.onNodeWithText("86/100").assertIsDisplayed()
        composeRule.onNodeWithText("Well-Lit Arterial (86)").assertIsDisplayed()
        composeRule.onNodeWithText("Active Metro Flow (84)").assertIsDisplayed()

        composeRule.onNodeWithTag("advisory_start_route_1").performScrollTo().performClick()
        composeRule.mainClock.advanceTimeBy(150)
        assertEquals("route_1", startedRouteId)
    }

    @Test
    fun loadingAndErrorStates_surfaceRealStatusAndRetry() {
        val copy = NirapadStrings.get(AppLang.EN)
        var retried = false
        var comparisonState by mutableStateOf<UiState<AdvisoryNavigationComparison>?>(null)

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            NirapadTheme {
                AdvisoryAiNavigationScreen(
                    copy = copy,
                    currentLocation = origin,
                    hasLiveGpsFix = false,
                    locationPermissionGranted = false,
                    destinationQuery = "Hauz Khas Metro",
                    travelWindowLabel = "Current Time",
                    routeSearchState = null,
                    advisoryComparisonState = comparisonState,
                    selectedRoute = null,
                    facilities = emptyList(),
                    aiChatState = AiAdvisorUiState.Idle,
                    onDestinationQueryChange = {},
                    onTravelWindowChange = {},
                    onRequestLocationPermission = {},
                    onCompareWithAi = { _, _ ->
                        retried = true
                    },
                    onSelectRoute = {},
                    onStartRouteJourney = {},
                    onAskFollowUp = {},
                    onNavigateBack = {}
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(150)

        // Initial evaluation pillars are displayed cleanly
        composeRule.onNodeWithText("How Gemini Advisory Navigation Evaluates Routes")
            .performScrollTo()
            .assertIsDisplayed()

        // Transition to error state and verify real error message & retry CTA
        comparisonState = UiState.Error("Gemini AI quota temporarily reached.")
        composeRule.mainClock.advanceTimeBy(150)

        composeRule.onNodeWithTag("advisory_error_banner").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Gemini AI quota temporarily reached.").assertIsDisplayed()
        composeRule.onNodeWithTag("advisory_retry_button").performScrollTo().performClick()
        composeRule.mainClock.advanceTimeBy(150)

        assertEquals(true, retried)
    }
}
