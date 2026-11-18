package com.example.ui

import android.app.Activity
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.firestore.UiState
import com.example.data.model.CrowdActivityTier
import com.example.data.model.Journey
import com.example.data.model.JourneyState
import com.example.data.model.LatLon
import com.example.data.model.NavigationStep
import com.example.data.model.NearbyPoliceStation
import com.example.data.model.PlaceSuggestion
import com.example.data.model.RouteOption
import com.example.data.model.RouteProgress
import com.example.data.model.RouteSearchResult
import com.example.data.model.SafetyTier
import com.example.data.model.WeatherContext
import com.example.data.service.LocationReading
import com.example.localization.AppLang
import com.example.localization.NirapadStrings
import com.example.ui.screens.EmergencyScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.JourneyScreen
import com.example.ui.screens.PlanRouteScreen
import com.example.ui.theme.NirapadTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp")
class RealWorldNavigationUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeScreen_rendersReverseGeocodedAddressWeatherAndAutocompleteSelection() {
        val suggestions = listOf(
            PlaceSuggestion(
                placeId = "nom_1",
                shortName = "Patna Junction",
                displayName = "Patna Junction, Station Road, Patna, Bihar",
                locality = "Station Road, Patna",
                lat = 25.6039,
                lon = 85.1376
            )
        )
        var pickedPlace: PlaceSuggestion? = null

        composeRule.setContent {
            NirapadTheme(darkTheme = false) {
                HomeScreen(
                    copy = NirapadStrings.get(AppLang.EN),
                    currentLocation = LocationReading(25.5941, 85.1376, 12.0, 1_700_000_000_000L),
                    hasLiveGpsFix = true,
                    locationPermissionGranted = true,
                    destinationQuery = "Patna",
                    routes = emptyList(),
                    selectedRoute = null,
                    activeJourney = null,
                    facilities = emptyList(),
                    onRequestLocationPermission = {},
                    onDestinationQueryChange = {},
                    onSearchRoutes = {},
                    onOpenJourney = {},
                    onOpenEmergencyDirectory = {},
                    onOpenAiAdvisor = {},
                    onRequestSosConfirm = {},
                    currentAddressLabel = "Kankarbagh Main Road, Patna",
                    weatherContext = WeatherContext(
                        temperatureCelsius = 24.0,
                        precipitationMm = 0.0,
                        isDay = false,
                        conditionLabel = "Night",
                        fetchedAtMillis = 1_700_000_000_000L
                    ),
                    placeSuggestions = suggestions,
                    onSelectPlaceSuggestion = { pickedPlace = it }
                )
            }
        }

        composeRule.onNodeWithTag("autocomplete_suggestions_list").assertIsDisplayed()
        composeRule.onNodeWithTag("autocomplete_suggestion_0").performClick()
        assertEquals("Patna Junction", pickedPlace?.shortName)

        composeRule.onNodeWithTag("home_current_address_text").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Kankarbagh Main Road, Patna").assertIsDisplayed()
        composeRule.onNodeWithTag("home_weather_badge").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun homeScreen_startJourneyButtonTriggersDirectJourneyLaunch() {
        var launchedWithOverride: String? = "UNSET"
        var clickCount = 0

        composeRule.setContent {
            NirapadTheme(darkTheme = false) {
                HomeScreen(
                    copy = NirapadStrings.get(AppLang.EN),
                    currentLocation = LocationReading(25.5941, 85.1376, 12.0, 1_700_000_000_000L),
                    hasLiveGpsFix = true,
                    locationPermissionGranted = true,
                    destinationQuery = "Patna Junction",
                    routes = emptyList(),
                    selectedRoute = null,
                    activeJourney = null,
                    facilities = emptyList(),
                    onRequestLocationPermission = {},
                    onDestinationQueryChange = {},
                    onSearchRoutes = {},
                    onStartJourney = { override ->
                        launchedWithOverride = override
                        clickCount++
                    },
                    onOpenJourney = {},
                    onOpenEmergencyDirectory = {},
                    onOpenAiAdvisor = {},
                    onRequestSosConfirm = {}
                )
            }
        }

        composeRule.onNodeWithTag("home_compare_routes_button").assertIsDisplayed()
        composeRule.onNodeWithTag("home_start_journey_button").assertIsDisplayed().performClick()
        assertEquals(1, clickCount)
        assertEquals(null, launchedWithOverride)
    }

    @Test
    fun homeScreen_displaysSelectedRouteDistanceAndEtaPreviewCard() {
        val route = RouteOption(
            id = "osrm_home",
            label = "Fraser Road Lit Corridor",
            summary = "Well-lit avenue",
            distanceMeters = 4200,
            durationSeconds = 720,
            providerName = "OSRM",
            providerIsFallback = false,
            safetyTier = SafetyTier.HIGHER_CONTEXT,
            safetyLabel = "High Crowd & Well-Lit",
            dataCoveragePercent = 95,
            freshnessNote = "Live",
            caveat = "",
            factors = emptyList(),
            geometry = listOf(LatLon(25.5941, 85.1376), LatLon(25.6039, 85.1376))
        )

        composeRule.setContent {
            NirapadTheme(darkTheme = false) {
                HomeScreen(
                    copy = NirapadStrings.get(AppLang.EN),
                    currentLocation = LocationReading(25.5941, 85.1376, 12.0, 1_700_000_000_000L),
                    hasLiveGpsFix = true,
                    locationPermissionGranted = true,
                    destinationQuery = "Patna Junction",
                    routes = listOf(route),
                    selectedRoute = route,
                    activeJourney = null,
                    facilities = emptyList(),
                    onRequestLocationPermission = {},
                    onDestinationQueryChange = {},
                    onSearchRoutes = {},
                    onOpenJourney = {},
                    onOpenEmergencyDirectory = {},
                    onOpenAiAdvisor = {},
                    onRequestSosConfirm = {}
                )
            }
        }

        composeRule.onNodeWithTag("home_route_preview_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("4.2 km", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("ETA 12 min", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun planRouteScreen_displaysAfterDarkWarningCrowdTierAndStepPreviews() {
        val route = RouteOption(
            id = "osrm_0",
            label = "Station Road Corridor",
            summary = "Busy commercial route with police coverage.",
            distanceMeters = 1850,
            durationSeconds = 260,
            providerName = "OSRM OpenStreetMap",
            providerIsFallback = false,
            safetyTier = SafetyTier.HIGHER_CONTEXT,
            safetyLabel = "High Crowd & Well-Lit (Safest)",
            dataCoveragePercent = 92,
            freshnessNote = "Live OSRM + Overpass corridor",
            caveat = "Night hours active: stick to illuminated main stretches.",
            factors = emptyList(),
            geometry = listOf(LatLon(25.5941, 85.1376), LatLon(25.6039, 85.1376)),
            steps = listOf(
                NavigationStep(
                    instruction = "Turn left onto Station Road",
                    roadName = "Station Road",
                    maneuverType = "turn",
                    maneuverModifier = "left",
                    distanceMeters = 320,
                    durationSeconds = 45,
                    location = LatLon(25.5980, 85.1376)
                )
            ),
            crowdActivityTier = CrowdActivityTier.HIGH_CROWD_WELL_LIT,
            crowdPoiCount = 14,
            litCount = 6,
            nearbyPoliceCount = 2,
            isNightTime = true
        )
        val result = RouteSearchResult(
            originLabel = "Kankarbagh, Patna",
            origin = LatLon(25.5941, 85.1376),
            destinationLabel = "Patna Junction",
            destination = LatLon(25.6039, 85.1376),
            routes = listOf(route),
            providerBanner = "Routes powered by OpenStreetMap OSRM & Overpass safety signals.",
            hasProviderWarning = false,
            weatherContext = WeatherContext(22.0, 0.0, false, "Night", 1_700_000_000_000L)
        )

        composeRule.setContent {
            NirapadTheme(darkTheme = false) {
                PlanRouteScreen(
                    copy = NirapadStrings.get(AppLang.EN),
                    currentLocation = LocationReading(25.5941, 85.1376, 12.0, 1_700_000_000_000L),
                    destinationQuery = "Patna Junction",
                    routeSearchState = UiState.Success(result),
                    selectedRoute = route,
                    contacts = emptyList(),
                    facilities = emptyList(),
                    onDestinationQueryChange = {},
                    onSearchRoutes = {},
                    onSelectRoute = {},
                    onStartJourney = { _, _, _ -> },
                    onOpenAiAdvisor = {}
                )
            }
        }

        composeRule.onNodeWithTag("plan_weather_warning_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("route_card_osrm_0").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("route_distance_eta_osrm_0", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("1.9 km • 4 min", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("route_crowd_tier_osrm_0", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Overpass corridor signals: 14 active POIs • 6 lit tags • 2 police", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Explain safety factors", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Turn left onto Station Road (320 m)", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun journeyScreen_displaysTurnByTurnWhatsAppShareAnd35mDestinationReachedModal() {
        var endedJourney = false
        val journey = Journey(
            id = "j_patna_1",
            ownerDisplayName = "Ananya",
            originLabel = "Kankarbagh",
            originLat = 25.5941,
            originLon = 85.1376,
            destinationLabel = "Patna Junction",
            destinationLat = 25.6039,
            destinationLon = 85.1376,
            selectedRouteId = "osrm_0",
            selectedRouteLabel = "Station Road Corridor",
            state = JourneyState.ACTIVE.value,
            sharingEnabled = true,
            sharedContactCount = 2L,
            shareTokenId = "tok_test_9876543210",
            lastLat = 25.6038,
            lastLon = 85.1376,
            totalDistanceKm = 4.2,
            durationMinutes = 14L
        )
        val completedHistory = Journey(
            id = "hist_1",
            destinationLabel = "Gandhi Maidan",
            selectedRouteLabel = "Fraser Road",
            providerName = "OSRM",
            state = JourneyState.COMPLETED.value,
            totalDistanceKm = 4.2,
            durationMinutes = 14L
        )
        val progress = RouteProgress(
            remainingPolyline = listOf(LatLon(25.6038, 85.1376), LatLon(25.6039, 85.1376)),
            remainingDistanceMeters = 18,
            remainingDurationSeconds = 10,
            distanceToDestinationMeters = 18.0,
            activeStep = NavigationStep(
                instruction = "Turn left onto Station Road",
                roadName = "Station Road",
                maneuverType = "turn",
                maneuverModifier = "left",
                distanceMeters = 18,
                durationSeconds = 10,
                location = LatLon(25.6039, 85.1376)
            ),
            distanceToNextStepMeters = 18,
            hasArrived = true,
            totalDistanceMeters = 4200,
            coveredDistanceMeters = 4200,
            progressFraction = 1f,
            speedKmh = 24.0
        )

        composeRule.setContent {
            NirapadTheme(darkTheme = false) {
                JourneyScreen(
                    copy = NirapadStrings.get(AppLang.EN),
                    currentLocation = LocationReading(25.6038, 85.1376, 10.0, 1_700_000_000_000L),
                    activeJourney = journey,
                    journeysHistory = listOf(completedHistory),
                    selectedRoute = null,
                    breadcrumbs = emptyList(),
                    deviationDistanceM = 4,
                    facilities = emptyList(),
                    shareLookupState = null,
                    onSimulateStep = { _, _ -> },
                    onConfirmDeviationOk = {},
                    onReroute = {},
                    onRequestSosConfirm = {},
                    onRevokeShare = {},
                    onEndJourney = { endedJourney = true },
                    onLookupShareToken = {},
                    onGoToPlan = {},
                    currentAddressLabel = "Station Road, Patna",
                    routeProgress = progress,
                    arrivedJourneyId = "j_patna_1"
                )
            }
        }

        // Destination Reached Modal appears with persisted Total Distance summary
        composeRule.onNodeWithTag("destination_reached_modal").assertIsDisplayed()
        composeRule.onNodeWithText("Destination Reached — Journey Succeeded!").assertIsDisplayed()
        composeRule.onNodeWithTag("modal_total_distance_summary", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Total Distance: 4.2 km • 14 min", useUnmergedTree = true).assertIsDisplayed()

        // Complete & Close Journey button triggers onEndJourney
        composeRule.onNodeWithTag("complete_close_journey_button").performClick()
        assertTrue(endedJourney)

        // Dismiss dialog and verify Trip Metrics Row, Progress Bar, WhatsApp share & Recent Journeys distance
        composeRule.onNodeWithTag("dismiss_arrival_button").performClick()
        composeRule.onNodeWithTag("turn_by_turn_card").assertIsDisplayed()
        composeRule.onNodeWithTag("journey_trip_metrics_row").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("metric_remaining_distance", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("metric_covered_distance", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("metric_eta_speed", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("journey_progress_bar").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("recent_journey_card_hist_1").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("4.2 km • COMPLETED", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag("share_whatsapp_button").performScrollTo().performClick()

        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val startedIntent: Intent? = Shadows.shadowOf(app).nextStartedActivity
        assertNotNull(startedIntent)
        val actualAction = startedIntent?.action
        assertTrue(actualAction == Intent.ACTION_SEND || actualAction == Intent.ACTION_CHOOSER)
    }

    @Test
    fun emergencyScreen_displaysNearbyOverpassPoliceStationsAndDialsOneTap() {
        val policeStations = listOf(
            NearbyPoliceStation(
                id = "ps_kotwali",
                name = "Kotwali Police Station",
                phone = "0612-2221100",
                locality = "Fraser Road, Patna",
                lat = 25.6090,
                lon = 85.1390,
                distanceMeters = 1450.0
            )
        )

        composeRule.setContent {
            NirapadTheme(darkTheme = false) {
                EmergencyScreen(
                    copy = NirapadStrings.get(AppLang.EN),
                    currentLocation = LocationReading(25.5941, 85.1376, 12.0, 1_700_000_000_000L),
                    sosSessions = emptyList(),
                    firestoreFacilities = emptyList(),
                    myReports = emptyList(),
                    isBackgroundServiceRunning = false,
                    broadcastCount = 0,
                    lastBroadcastReading = null,
                    locationPermissionGranted = true,
                    onRequestLocationPermission = {},
                    onRequestSosConfirm = {},
                    onOpenDirectory = {},
                    onUpdateSosStatus = { _, _ -> },
                    onSubmitReport = { _, _, _, _ -> },
                    nearbyPoliceStations = policeStations
                )
            }
        }

        composeRule.onNodeWithTag("police_station_card_ps_kotwali").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Kotwali Police Station").assertIsDisplayed()
        composeRule.onNodeWithTag("call_police_ps_kotwali").performScrollTo().performClick()

        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val dialIntent: Intent? = Shadows.shadowOf(app).nextStartedActivity
        assertNotNull(dialIntent)
        assertEquals(Intent.ACTION_DIAL, dialIntent?.action)
        assertEquals("tel:0612-2221100", dialIntent?.dataString)
    }
}
