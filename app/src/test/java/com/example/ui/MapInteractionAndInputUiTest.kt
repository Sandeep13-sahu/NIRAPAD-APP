package com.example.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.cache.SlippyMapMath
import com.example.data.model.LatLon
import com.example.data.service.LocationReading
import com.example.localization.AppLang
import com.example.localization.NirapadStrings
import com.example.ui.components.EsriWorldImageryTileSource
import com.example.ui.components.NirapadInputDefaults
import com.example.ui.components.NirapadMapCanvas
import com.example.ui.components.NirapadOutlinedTextField
import com.example.ui.components.ensureOsmDroidConfigured
import com.example.ui.components.esriWorldImageryTileUrl
import com.example.ui.screens.EmergencyScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.JourneyScreen
import com.example.ui.theme.NirapadTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.config.Configuration
import org.osmdroid.util.MapTileIndex
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp")
class MapInteractionAndInputUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun slippyMapMath_cameraProjectionAndPanRoundTripCorrectly() {
        val startCenter = LatLon(28.6315, 77.2167)
        val proj = SlippyMapMath.buildCameraProjection(
            center = startCenter,
            continuousZoom = 15.25f,
            widthPx = 800f,
            heightPx = 600f
        )

        val (cx, cy) = proj.project(startCenter)
        assertEquals(400f, cx, 1.5f)
        assertEquals(300f, cy, 1.5f)

        val unprojected = proj.unproject(400f, 300f)
        assertEquals(startCenter.lat, unprojected.lat, 0.0001)
        assertEquals(startCenter.lon, unprojected.lon, 0.0001)

        // Panning east (+dx) moves longitude east
        val panned = proj.panByScreenDelta(120f, -80f)
        assertTrue(panned.lon > startCenter.lon)
        assertTrue(panned.lat > startCenter.lat)
    }

    @Test
    fun osmdroidConfiguration_usesInternalCacheDirectoryWithoutExternalStorage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ensureOsmDroidConfigured(context)
        val cfg = Configuration.getInstance()
        assertTrue(cfg.osmdroidBasePath.absolutePath.startsWith(context.cacheDir.absolutePath))
        assertTrue(cfg.osmdroidTileCache.absolutePath.startsWith(context.cacheDir.absolutePath))
        assertTrue(cfg.userAgentValue.isNotBlank())
    }

    @Test
    fun esriWorldImageryTileSource_formatsArcGisZoomYXUrlCorrectly() {
        val expectedUrl = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/15/13654/24123"
        assertEquals(expectedUrl, esriWorldImageryTileUrl(zoom = 15, x = 24123, y = 13654))

        val tileIndex = MapTileIndex.getTileIndex(15, 24123, 13654)
        assertEquals(expectedUrl, EsriWorldImageryTileSource.getTileURLString(tileIndex))
    }

    @Test
    fun mapCanvas_layerToggleSwitchesBetweenStreetAndSatelliteInInlineAndFullscreen() {
        val loc = LatLon(25.5941, 85.1376)

        composeRule.setContent {
            NirapadTheme(darkTheme = false) {
                NirapadMapCanvas(
                    currentLocation = loc,
                    routes = emptyList(),
                    selectedRouteId = null,
                    breadcrumbs = emptyList(),
                    facilities = emptyList(),
                    isDeviated = false,
                    hasLiveGpsFix = true,
                    autoFollowByDefault = true,
                    fullscreenTitle = "Patna Explorer",
                    modifier = Modifier.height(220.dp)
                )
            }
        }

        // Starts in Street View -> toggle reads "Satellite" and badge reflects street/offline mode
        composeRule.onNodeWithTag("map_layer_toggle_button").assertIsDisplayed()
        composeRule.onNodeWithText("Satellite").assertIsDisplayed()
        composeRule.onNodeWithTag("map_cache_status_badge").assertIsDisplayed()

        // Tap toggle on inline map -> switches to Esri Satellite View ("Street" toggle + "© Esri" attribution + "Satellite • Esri")
        composeRule.onNodeWithTag("map_layer_toggle_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Street").assertIsDisplayed()
        composeRule.onNodeWithText("© Esri").assertIsDisplayed()
        composeRule.onNodeWithText("Satellite • Esri").assertIsDisplayed()

        // Expand fullscreen -> stays in Satellite mode and exposes fullscreen_map_layer_toggle_button
        composeRule.onNodeWithTag("map_expand_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("fullscreen_map_layer_toggle_button").assertIsDisplayed()
        composeRule.onNodeWithText("© Esri World Imagery", substring = true).assertIsDisplayed()

        // Toggle back to Street inside fullscreen -> syncs state when minimized
        composeRule.onNodeWithTag("fullscreen_map_layer_toggle_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("© OpenStreetMap", substring = true).assertIsDisplayed()

        composeRule.onNodeWithTag("map_minimize_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Satellite").assertIsDisplayed()
    }

    @Test
    fun mapCanvas_supportsPanRecenterAndFullscreenExpandMinimizeFlow() {
        val loc = LatLon(28.6315, 77.2167)

        composeRule.setContent {
            NirapadTheme(darkTheme = false) {
                NirapadMapCanvas(
                    currentLocation = loc,
                    routes = emptyList(),
                    selectedRouteId = null,
                    breadcrumbs = emptyList(),
                    facilities = emptyList(),
                    isDeviated = false,
                    hasLiveGpsFix = true,
                    autoFollowByDefault = true,
                    fullscreenTitle = "Test Map Explorer",
                    modifier = Modifier
                        .height(220.dp)
                        .testTag("inline_map")
                )
            }
        }

        composeRule.onNodeWithTag("map_expand_button").assertIsDisplayed()
        composeRule.onNodeWithTag("map_recenter_button").assertIsDisplayed()

        // Pan the inline map manually -> breaks auto-follow and surfaces "Re-center"
        composeRule.onNodeWithTag("inline_map").performTouchInput {
            swipeLeft(startX = centerX + 100f, endX = centerX - 100f)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Re-center").assertIsDisplayed()

        // Expand into Full-Screen Map Exploration Mode
        composeRule.onNodeWithTag("map_expand_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("fullscreen_map_container").assertIsDisplayed()
        composeRule.onNodeWithTag("map_minimize_button").assertIsDisplayed()
        composeRule.onNodeWithTag("map_zoom_in_button").assertIsDisplayed()
        composeRule.onNodeWithTag("map_zoom_out_button").assertIsDisplayed()
        composeRule.onNodeWithTag("fullscreen_map_recenter_button").assertIsDisplayed()
        composeRule.onNodeWithText("Test Map Explorer").assertIsDisplayed()

        // Tapping Re-center snaps back to auto-follow ("Following")
        composeRule.onNodeWithTag("fullscreen_map_recenter_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Following").assertIsDisplayed()

        // Zoom in button updates zoom readout
        composeRule.onNodeWithTag("map_zoom_in_button").performClick()
        composeRule.waitForIdle()

        // Minimize returns to inline view cleanly
        composeRule.onNodeWithTag("map_minimize_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("fullscreen_map_container").assertDoesNotExist()
        composeRule.onNodeWithTag("map_expand_button").assertIsDisplayed()
    }

    @Test
    fun mapCanvas_fullscreenSearchOverlayUpdatesQueryAndTriggersSearch() {
        var query by mutableStateOf("AIIMS")
        var searchedQuery: String? = null

        composeRule.setContent {
            NirapadTheme(darkTheme = false) {
                NirapadMapCanvas(
                    currentLocation = LatLon(28.6139, 77.2090),
                    routes = emptyList(),
                    selectedRouteId = null,
                    breadcrumbs = emptyList(),
                    facilities = emptyList(),
                    isDeviated = false,
                    hasLiveGpsFix = true,
                    destinationQuery = query,
                    onDestinationQueryChange = { query = it },
                    onSearchDestination = { searchedQuery = it },
                    modifier = Modifier.height(220.dp)
                )
            }
        }

        composeRule.onNodeWithTag("map_expand_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("fullscreen_map_search_input").assertIsDisplayed()
        composeRule.onNodeWithTag("fullscreen_map_search_input").performTextReplacement("Rajiv Chowk Metro")
        composeRule.onNodeWithTag("fullscreen_map_search_go").performClick()
        composeRule.waitForIdle()

        assertEquals("Rajiv Chowk Metro", searchedQuery)
        composeRule.onNodeWithTag("fullscreen_map_container").assertDoesNotExist()
    }

    @Test
    fun nirapadOutlinedTextField_providesDistinctHighContrastColorsInLightAndDarkTheme() {
        var lightTextColor = 0L
        var lightPlaceholderColor = 0L
        var darkTextColor = 0L
        var isLightDetected = true
        var isDarkDetected = false
        var typedValue by mutableStateOf("Rajiv Chowk Metro")

        composeRule.setContent {
            Column {
                NirapadTheme(darkTheme = false) {
                    isLightDetected = !NirapadInputDefaults.isDarkTheme()
                    lightTextColor = NirapadInputDefaults.textColor().value.toLong()
                    lightPlaceholderColor = NirapadInputDefaults.placeholderColor().value.toLong()
                    NirapadOutlinedTextField(
                        value = typedValue,
                        onValueChange = { typedValue = it },
                        label = { Text("Destination") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("light_input")
                    )
                }
                NirapadTheme(darkTheme = true) {
                    isDarkDetected = NirapadInputDefaults.isDarkTheme()
                    darkTextColor = NirapadInputDefaults.textColor().value.toLong()
                    NirapadOutlinedTextField(
                        value = typedValue,
                        onValueChange = { typedValue = it },
                        label = { Text("Destination Dark") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dark_input")
                    )
                }
            }
        }

        assertTrue(isLightDetected)
        assertTrue(isDarkDetected)
        assertEquals(Color(0xFF0F172A).value.toLong(), lightTextColor)
        assertEquals(Color(0xFF64748B).value.toLong(), lightPlaceholderColor)
        assertFalse(lightTextColor == darkTextColor)
        assertNotEquals(0xFFFFFFFFL, lightTextColor)

        composeRule.onNodeWithTag("light_input").assertIsDisplayed().assertTextContains("Rajiv Chowk Metro")
        composeRule.onNodeWithTag("dark_input").assertIsDisplayed().assertTextContains("Rajiv Chowk Metro")
    }

    @Test
    fun homeScreen_rendersWaitingGpsFixAndReadablePresets() {
        val copy = NirapadStrings.get(AppLang.EN)

        composeRule.setContent {
            NirapadTheme(darkTheme = false) {
                HomeScreen(
                    copy = copy,
                    currentLocation = LocationReading(28.6315, 77.2167, 15.0, timestampMillis = 0L),
                    hasLiveGpsFix = false,
                    locationPermissionGranted = true,
                    destinationQuery = "",
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
                    onRequestSosConfirm = {}
                )
            }
        }

        composeRule.onNodeWithText("Hauz Khas Metro Gate 1").assertIsDisplayed()
        composeRule.onNodeWithText("Waiting for FusedLocationProviderClient GPS fix...")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun journeyScreen_rendersHighContrastViewShareButton() {
        val copy = NirapadStrings.get(AppLang.EN)

        composeRule.setContent {
            NirapadTheme(darkTheme = false) {
                JourneyScreen(
                    copy = copy,
                    currentLocation = LocationReading(28.6315, 77.2167, 10.0, timestampMillis = 1000L),
                    activeJourney = null,
                    journeysHistory = emptyList(),
                    selectedRoute = null,
                    breadcrumbs = emptyList(),
                    deviationDistanceM = 0,
                    facilities = emptyList(),
                    shareLookupState = null,
                    onSimulateStep = { _, _ -> },
                    onConfirmDeviationOk = {},
                    onReroute = {},
                    onRequestSosConfirm = {},
                    onRevokeShare = {},
                    onEndJourney = {},
                    onLookupShareToken = {},
                    onGoToPlan = {}
                )
            }
        }

        composeRule.onNodeWithTag("open_share_token_button")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("View")
    }

    @Test
    fun emergencyScreen_rendersHighContrastCall112Button() {
        val copy = NirapadStrings.get(AppLang.EN)

        composeRule.setContent {
            NirapadTheme(darkTheme = false) {
                EmergencyScreen(
                    copy = copy,
                    currentLocation = LocationReading(28.6315, 77.2167, 10.0, timestampMillis = 1000L),
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
                    onSubmitReport = { _, _, _, _ -> }
                )
            }
        }

        composeRule.onNodeWithTag("call_112")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("Call 112")
    }
}
