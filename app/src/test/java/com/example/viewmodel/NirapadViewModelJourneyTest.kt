package com.example.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AdvisoryNavigationComparison
import com.example.data.model.AdvisoryRouteComparisonItem
import com.example.data.model.JourneyState
import com.example.data.model.RouteOption
import com.example.data.repository.AdvisoryAiClient
import com.example.data.repository.AiAdvisorUiState
import com.example.data.repository.NirapadRepository
import com.example.data.repository.SafetyAiContext
import com.example.data.service.FreeHttpTransport
import com.example.data.service.FreeSafetyApisClient
import com.example.data.service.LocationReading
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NirapadViewModelJourneyTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId("com.aistudio.nirapad.k7m2px")
                    .setProjectId("demo-nirapad-unit")
                    .setApiKey("unit-test-placeholder-key")
                    .build()
            )
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun startJourney_fromHomeWithOfflineFirestore_createsLocalJourneyTracksArrivalWithin35mAndEndsCleanly() = runTest(testDispatcher) {
        val firestore = FirebaseFirestore.getInstance(FirebaseApp.getInstance()).apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
        }
        val repo = NirapadRepository(
            db = firestore,
            authProvider = { "uid_test_user" },
            emailProvider = { "ananya@example.com" to true }
        )

        var capturedAiContext: SafetyAiContext? = null
        val fakeAi = object : AdvisoryAiClient {
            override fun askStream(
                question: String,
                selectedRoute: RouteOption?,
                activeJourneyState: String?,
                languageCode: String,
                safetyContext: SafetyAiContext
            ): Flow<AiAdvisorUiState> {
                capturedAiContext = safetyContext
                return flowOf(AiAdvisorUiState.Success("Guidance for ${safetyContext.currentAddressLabel}"))
            }

            override suspend fun compareRoutes(
                originLabel: String,
                destinationLabel: String,
                travelWindowLabel: String,
                routes: List<RouteOption>,
                languageCode: String
            ): AdvisoryNavigationComparison {
                val first = routes.first()
                return AdvisoryNavigationComparison(
                    destinationLabel = destinationLabel,
                    travelWindowLabel = travelWindowLabel,
                    recommendedRouteId = first.id,
                    headlineSummary = "Ok",
                    comparisonOverview = "Ok",
                    routeComparisons = listOf(
                        AdvisoryRouteComparisonItem(
                            routeId = first.id,
                            routeLabel = first.label,
                            overallSafetyScore = 80,
                            streetLightingRating = "Lit",
                            streetLightingScore = 80,
                            publicTrafficLevel = "Active",
                            publicTrafficScore = 80,
                            recommendedRank = 1,
                            isSaferOption = true,
                            aiRationale = "Ok",
                            precautionTip = "Ok"
                        )
                    )
                )
            }
        }

        val fakeTransport = object : FreeHttpTransport {
            override fun get(url: String, headers: Map<String, String>): String? = when {
                url.contains("nominatim.openstreetmap.org/reverse") -> """
                    {
                      "display_name": "Kankarbagh Main Road, Patna, Bihar, India",
                      "address": { "road": "Kankarbagh Main Road", "city": "Patna" }
                    }
                """.trimIndent()
                url.contains("nominatim.openstreetmap.org/search") -> """
                    [
                      {
                        "place_id": 2001,
                        "lat": "25.6039",
                        "lon": "85.1376",
                        "name": "Patna Junction",
                        "display_name": "Patna Junction, Patna, Bihar, India"
                      }
                    ]
                """.trimIndent()
                url.contains("router.project-osrm.org/route") -> """
                    {
                      "code": "Ok",
                      "routes": [
                        {
                          "distance": 1200.0,
                          "duration": 180.0,
                          "geometry": { "coordinates": [[85.1376, 25.5941], [85.1376, 25.6039]] },
                          "legs": [{ "steps": [] }]
                        }
                      ]
                    }
                """.trimIndent()
                url.contains("api.open-meteo.com") -> """
                    { "current": { "temperature_2m": 26.5, "precipitation": 0.0, "is_day": 0 } }
                """.trimIndent()
                else -> null
            }

            override fun postForm(url: String, formFields: Map<String, String>, headers: Map<String, String>): String? = """
                {
                  "elements": [
                    {
                      "id": 901,
                      "lat": 25.6010,
                      "lon": 85.1380,
                      "tags": {
                        "name": "Kotwali Police Station",
                        "amenity": "police",
                        "phone": "0612-2221100"
                      }
                    }
                  ]
                }
            """.trimIndent()
        }

        val viewModel = NirapadViewModel(
            repository = repo,
            aiAdvisor = fakeAi,
            currentUserId = "uid_test_user",
            currentUserName = "Ananya",
            currentUserEmail = "ananya@example.com",
            mapTileCache = null,
            freeApisClient = FreeSafetyApisClient(transport = fakeTransport)
        )

        val collectorJob = backgroundScope.launch {
            viewModel.activeJourneyState.collect {}
        }
        val historyJob = backgroundScope.launch {
            viewModel.journeysHistoryState.collect {}
        }

        // Start a journey directly from Home before searching routes first
        viewModel.onLiveLocationReading(LocationReading(25.5941, 85.1376, 8.0, 1_700_000_000_000L))
        viewModel.startJourney(
            sharingEnabled = true,
            selectedContactCount = 1L,
            shareDurationMinutes = 60L,
            destinationOverride = "Patna Junction"
        )

        val immediateActive = viewModel.activeJourneyState.value
        assertNotNull(immediateActive)
        assertEquals(JourneyState.ACTIVE.value, immediateActive?.state)
        assertEquals("Patna Junction", immediateActive?.destinationLabel)
        assertEquals("Patna Junction", viewModel.destinationQuery.value)
        // Instant Haversine placeholder populated before OSRM resolves
        assertTrue((immediateActive?.totalDistanceKm ?: 0.0) > 0.9)
        assertTrue((immediateActive?.durationMinutes ?: 0L) >= 1L)

        // Wait briefly for Dispatchers.IO OSRM route resolution to smoothly update totalDistanceKm & durationMinutes
        val deadline = System.currentTimeMillis() + 4_000L
        while (
            (viewModel.selectedRoute.value == null ||
                (viewModel.activeJourneyState.value?.totalDistanceKm ?: 0.0) > 1.5) &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(20L)
        }
        val activeAfterOsrm = viewModel.activeJourneyState.value
        assertNotNull(viewModel.selectedRoute.value)
        assertEquals(1.2, activeAfterOsrm?.totalDistanceKm ?: 0.0, 0.05)
        assertEquals(3L, activeAfterOsrm?.durationMinutes ?: 0L)

        // Move mid-route with live speed -> verifies coveredDistanceMeters, progressFraction, and speedKmh
        viewModel.onLiveLocationReading(LocationReading(25.5990, 85.1376, 7.0, 1_700_000_003_000L, speedMps = 6.5f))
        val midProgress = viewModel.routeProgress.value
        assertNotNull(midProgress)
        assertTrue("Expected covered > 500m, got ${midProgress?.coveredDistanceMeters}", (midProgress?.coveredDistanceMeters ?: 0) > 500)
        assertTrue("Expected progress > 0.4, got ${midProgress?.progressFraction}", (midProgress?.progressFraction ?: 0f) > 0.4f)
        assertTrue((midProgress?.speedKmh ?: 0.0) > 20.0)

        // Move within 35 meters of Patna Junction (25.6039, 85.1376) -> triggers arrival modal ID
        viewModel.onLiveLocationReading(LocationReading(25.6038, 85.1376, 6.0, 1_700_000_005_000L))
        assertEquals(activeAfterOsrm?.id, viewModel.arrivedJourneyId.value)

        // Ask AI advisor -> receives real-time SafetyAiContext
        viewModel.askAiAdvisor("Is my current route safe right now?")
        assertNotNull(capturedAiContext)
        assertEquals("Patna Junction", capturedAiContext?.destinationLabel)
        assertTrue(viewModel.aiState.value is AiAdvisorUiState.Success)

        // End journey cleanly clears active local fallback & progress and records history with totalDistanceKm
        viewModel.endJourney(activeAfterOsrm!!)
        assertNull(viewModel.activeJourneyState.value)
        assertNull(viewModel.routeProgress.value)
        assertNull(viewModel.arrivedJourneyId.value)

        val history = viewModel.journeysHistoryState.value
        assertTrue(history.isNotEmpty())
        assertEquals(JourneyState.COMPLETED.value, history.first().state)
        assertTrue(history.first().totalDistanceKm >= 1.2)
        collectorJob.cancel()
        historyJob.cancel()
    }
}
