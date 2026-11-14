package com.example.data.repository

import com.example.data.model.DataProvenance
import com.example.data.model.LatLon
import com.example.data.model.RouteOption
import com.example.data.model.SafetyFactor
import com.example.data.model.SafetyTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AiAdvisorRepositoryTest {

    private val sampleRoutes = listOf(
        RouteOption(
            id = "route_1",
            label = "Main Lit Arterial Corridor",
            summary = "Follows lit main roads and active transit stops.",
            distanceMeters = 3200,
            durationSeconds = 1800,
            providerName = "OSRM Pedestrian/Road Graph",
            providerIsFallback = false,
            safetyTier = SafetyTier.HIGHER_CONTEXT,
            safetyLabel = "Higher relative safety context",
            dataCoveragePercent = 100,
            freshnessNote = "Live route • 100% factor coverage",
            caveat = "Comparative decision support only.",
            factors = listOf(
                SafetyFactor("lighting", "Street Lighting & Visibility", "Well-lit corridor", 84, "LED main corridor", DataProvenance.ESTIMATED, "Baseline"),
                SafetyFactor("footfall", "Transit & Evening Footfall", "Active public transit", 82, "Metro stops active", DataProvenance.ESTIMATED, "Live window")
            ),
            geometry = listOf(LatLon(28.63, 77.21), LatLon(28.54, 77.20))
        ),
        RouteOption(
            id = "route_3",
            label = "Direct Neighbourhood Link",
            summary = "Shortest distance, but lighting data is unavailable for inner segments.",
            distanceMeters = 2500,
            durationSeconds = 1400,
            providerName = "OSRM Pedestrian/Road Graph",
            providerIsFallback = false,
            safetyTier = SafetyTier.LIMITED_DATA,
            safetyLabel = "Route has limited safety data",
            dataCoveragePercent = 75,
            freshnessNote = "Live route • 75% factor coverage",
            caveat = "Comparative decision support only.",
            factors = listOf(
                SafetyFactor("lighting", "Street Lighting & Visibility", "Unavailable", null, "Lighting audit missing", DataProvenance.UNAVAILABLE, "No feed"),
                SafetyFactor("footfall", "Transit & Evening Footfall", "Quieter residential stretch", 44, "Low crowd density", DataProvenance.ESTIMATED, "Live window")
            ),
            geometry = listOf(LatLon(28.63, 77.21), LatLon(28.54, 77.20))
        )
    )

    @Test
    fun parseComparisonJson_ranksSaferRouteFirst_andPreservesMetrics() {
        // Deliberately return route_3 first in JSON to verify deterministic ranking puts safer option first
        val rawJson = """
            {
              "recommendedRouteId": "route_1",
              "headlineSummary": "Main Lit Arterial Corridor is comparatively safer than Direct Neighbourhood Link.",
              "comparisonOverview": "Route 1 offers municipal street lighting and steady public transit footfall.",
              "routes": [
                {
                  "routeId": "route_3",
                  "routeLabel": "Direct Neighbourhood Link",
                  "overallSafetyScore": 49,
                  "streetLightingRating": "Unavailable",
                  "streetLightingScore": 0,
                  "publicTrafficLevel": "Low evening traffic",
                  "publicTrafficScore": 44,
                  "recommendedRank": 2,
                  "isSaferOption": false,
                  "aiRationale": "Inner blocks lack street lighting telemetry and have quiet stretches.",
                  "precautionTip": "Avoid after dusk unless travelling with company."
                },
                {
                  "routeId": "route_1",
                  "routeLabel": "Main Lit Arterial Corridor",
                  "overallSafetyScore": 85,
                  "streetLightingRating": "Well-Lit Arterial",
                  "streetLightingScore": 84,
                  "publicTrafficLevel": "Active Metro & Bus Crowd",
                  "publicTrafficScore": 82,
                  "recommendedRank": 1,
                  "isSaferOption": true,
                  "aiRationale": "Consistent LED street lighting and active transit footfall near verified desks.",
                  "precautionTip": "Keep trusted live location sharing enabled."
                }
              ]
            }
        """.trimIndent()

        val parsed = AiAdvisorRepository.parseComparisonJson(
            rawJson = rawJson,
            destinationLabel = "Hauz Khas Metro Gate 1",
            travelWindowLabel = "Evening / Dusk",
            candidateRoutes = sampleRoutes
        )

        assertEquals("route_1", parsed.recommendedRouteId)
        assertEquals(2, parsed.routeComparisons.size)
        val top = parsed.routeComparisons.first()
        assertEquals("route_1", top.routeId)
        assertTrue(top.isSaferOption)
        assertEquals(1, top.recommendedRank)
        assertEquals(85, top.overallSafetyScore)
        assertEquals("Well-Lit Arterial", top.streetLightingRating)
        assertEquals(84, top.streetLightingScore)
        assertEquals("Active Metro & Bus Crowd", top.publicTrafficLevel)
        assertEquals(82, top.publicTrafficScore)

        val second = parsed.routeComparisons[1]
        assertEquals("route_3", second.routeId)
        assertFalse(second.isSaferOption)
        assertEquals(0, second.streetLightingScore)
    }
}
