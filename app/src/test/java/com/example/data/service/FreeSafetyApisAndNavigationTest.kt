package com.example.data.service

import com.example.data.model.CrowdActivityTier
import com.example.data.model.LatLon
import com.example.data.model.NavigationStep
import com.example.data.model.RouteOption
import com.example.data.model.SafetyTier
import com.example.data.model.SegmentSafetyLevel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FreeSafetyApisAndNavigationTest {

    @Test
    fun nominatimSearchAndReverseGeocode_includeCustomUserAgentAndParseReadableLocality() = runBlocking {
        val capturedHeaders = mutableListOf<Map<String, String>>()
        val capturedUrls = mutableListOf<String>()

        val fakeTransport = object : FreeHttpTransport {
            override fun get(url: String, headers: Map<String, String>): String? {
                capturedUrls.add(url)
                capturedHeaders.add(headers)
                return when {
                    url.contains("nominatim.openstreetmap.org/search") -> """
                        [
                          {
                            "place_id": 1001,
                            "lat": "25.6039",
                            "lon": "85.1376",
                            "name": "Patna Junction",
                            "display_name": "Patna Junction, Station Road, Fraser Road Area, Patna, Bihar, 800001, India",
                            "address": {
                              "road": "Station Road",
                              "suburb": "Fraser Road Area",
                              "city": "Patna",
                              "state": "Bihar"
                            }
                          }
                        ]
                    """.trimIndent()
                    url.contains("nominatim.openstreetmap.org/reverse") -> """
                        {
                          "display_name": "Kankarbagh Main Road, Kankarbagh, Patna, Bihar, India",
                          "address": {
                            "road": "Kankarbagh Main Road",
                            "suburb": "Kankarbagh",
                            "city": "Patna",
                            "state": "Bihar"
                          }
                        }
                    """.trimIndent()
                    else -> null
                }
            }

            override fun postForm(url: String, formFields: Map<String, String>, headers: Map<String, String>): String? = null
        }

        val client = FreeSafetyApisClient(transport = fakeTransport)
        val suggestions = client.searchPlacesIndia("Patna Junction")
        assertEquals(1, suggestions.size)
        assertEquals("Patna Junction", suggestions[0].shortName)
        assertEquals(25.6039, suggestions[0].lat, 0.0001)
        assertEquals(85.1376, suggestions[0].lon, 0.0001)

        val locality = client.reverseGeocode(25.5980, 85.1580)
        assertNotNull(locality)
        assertTrue(locality!!.contains("Kankarbagh"))
        assertTrue(locality.contains("Patna"))

        assertTrue(capturedUrls.any { it.contains("countrycodes=in") })
        assertTrue(capturedHeaders.all { it["User-Agent"] == FreeSafetyApisClient.USER_AGENT })
    }

    @Test
    fun routingEngine_withOsrmOverpassAndNightOpenMeteo_buildsTurnByTurnAndColoredSegments() = runBlocking {
        val origin = LatLon(25.5941, 85.1376)
        val destinationQuery = "Patna Junction"

        val osrmJson = """
            {
              "code": "Ok",
              "routes": [
                {
                  "distance": 1850.0,
                  "duration": 260.0,
                  "geometry": {
                    "coordinates": [
                      [85.1376, 25.5941],
                      [85.1378, 25.5980],
                      [85.1376, 25.6010],
                      [85.1376, 25.6039]
                    ]
                  },
                  "legs": [
                    {
                      "steps": [
                        {
                          "distance": 450.0,
                          "duration": 65.0,
                          "name": "Station Road",
                          "maneuver": {
                            "type": "turn",
                            "modifier": "left",
                            "location": [85.1378, 25.5980]
                          }
                        },
                        {
                          "distance": 90.0,
                          "duration": 20.0,
                          "name": "Patna Junction",
                          "maneuver": {
                            "type": "arrive",
                            "modifier": "",
                            "location": [85.1376, 25.6039]
                          }
                        }
                      ]
                    }
                  ]
                },
                {
                  "distance": 2100.0,
                  "duration": 300.0,
                  "geometry": {
                    "coordinates": [
                      [85.1376, 25.5941],
                      [85.1480, 25.5990],
                      [85.1376, 25.6039]
                    ]
                  },
                  "legs": [
                    {
                      "steps": [
                        {
                          "distance": 2100.0,
                          "duration": 300.0,
                          "name": "Dark Bypass Lane",
                          "maneuver": {
                            "type": "depart",
                            "modifier": "straight",
                            "location": [85.1376, 25.5941]
                          }
                        }
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val overpassCorridorJson = """
            {
              "elements": [
                {"type":"node","id":1,"lat":25.5978,"lon":85.1377,"tags":{"shop":"supermarket"}},
                {"type":"node","id":2,"lat":25.5980,"lon":85.1378,"tags":{"highway":"bus_stop"}},
                {"type":"node","id":3,"lat":25.5982,"lon":85.1377,"tags":{"lit":"yes"}},
                {"type":"node","id":4,"lat":25.5995,"lon":85.1377,"tags":{"amenity":"hospital"}},
                {"type":"node","id":5,"lat":25.5998,"lon":85.1377,"tags":{"amenity":"police"}},
                {"type":"node","id":6,"lat":25.6002,"lon":85.1376,"tags":{"amenity":"marketplace"}},
                {"type":"node","id":7,"lat":25.5985,"lon":85.1377,"tags":{"shop":"bakery"}},
                {"type":"node","id":8,"lat":25.5992,"lon":85.1377,"tags":{"lit":"yes"}},
                {"type":"node","id":9,"lat":25.6006,"lon":85.1376,"tags":{"shop":"pharmacy"}},
                {"type":"node","id":10,"lat":25.5989,"lon":85.1377,"tags":{"shop":"convenience"}},
                {"type":"node","id":11,"lat":25.5991,"lon":85.1377,"tags":{"shop":"clothes"}},
                {"type":"node","id":12,"lat":25.6000,"lon":85.1376,"tags":{"amenity":"cafe"}},
                {"type":"node","id":13,"lat":25.6008,"lon":85.1376,"tags":{"lit":"yes"}}
              ]
            }
        """.trimIndent()

        val openMeteoNightJson = """
            {
              "current": {
                "temperature_2m": 22.5,
                "precipitation": 0.0,
                "is_day": 0
              }
            }
        """.trimIndent()

        val fakeTransport = object : FreeHttpTransport {
            override fun get(url: String, headers: Map<String, String>): String? = when {
                url.contains("nominatim.openstreetmap.org/search") -> """
                    [{"place_id":1,"lat":"25.6039","lon":"85.1376","name":"Patna Junction","display_name":"Patna Junction, Patna, Bihar"}]
                """.trimIndent()
                url.contains("router.project-osrm.org/route/v1/driving") -> osrmJson
                url.contains("api.open-meteo.com/v1/forecast") -> openMeteoNightJson
                else -> null
            }

            override fun postForm(url: String, formFields: Map<String, String>, headers: Map<String, String>): String? {
                return if (url.contains("overpass-api.de")) overpassCorridorJson else null
            }
        }

        val apiClient = FreeSafetyApisClient(transport = fakeTransport)
        val result = RoutingAndSafetyEngine.searchRoutes(
            originLabel = "Fraser Road, Patna",
            origin = origin,
            destinationQuery = destinationQuery,
            verifiedReports = emptyList(),
            facilities = emptyList(),
            apiClient = apiClient
        )

        assertFalse(result.hasProviderWarning)
        assertNotNull(result.weatherContext)
        assertFalse(result.weatherContext!!.isDay)
        assertEquals(2, result.routes.size)

        // Safest route with dense POIs + lighting should rank first
        val bestRoute = result.routes.first()
        assertEquals(CrowdActivityTier.HIGH_CROWD_WELL_LIT, bestRoute.crowdActivityTier)
        assertTrue(bestRoute.isNightTime)
        assertTrue(bestRoute.steps.any { it.instruction.contains("Turn left onto Station Road") })
        assertTrue(bestRoute.coloredSegments.any { it.safetyLevel == SegmentSafetyLevel.HIGH_CROWD_LIT })

        val isolatedAlt = result.routes[1]
        assertEquals(CrowdActivityTier.LOW_CROWD_ISOLATED, isolatedAlt.crowdActivityTier)
        assertTrue(isolatedAlt.caveat.contains("Night-safety", ignoreCase = true))
        assertTrue(isolatedAlt.safetyLabel.contains("Night warning", ignoreCase = true))
    }

    @Test
    fun parseOverpassPoliceJson_sortsByDistanceAndPreservesMissingPhoneWithoutFabrication() {
        val raw = """
            {
              "elements": [
                {
                  "type": "node",
                  "id": 201,
                  "lat": 25.6120,
                  "lon": 85.1420,
                  "tags": {
                    "amenity": "police",
                    "name": "Kotwali Police Station",
                    "contact:phone": "+91-612-2221100",
                    "addr:suburb": "Fraser Road"
                  }
                },
                {
                  "type": "way",
                  "id": 202,
                  "center": {"lat": 25.5960, "lon": 85.1380},
                  "tags": {
                    "amenity": "police",
                    "name": "Kankarbagh Thana",
                    "addr:city": "Patna"
                  }
                }
              ]
            }
        """.trimIndent()

        val stations = FreeSafetyApisClient.parseOverpassPoliceJson(raw, 25.5941, 85.1376)
        assertEquals(2, stations.size)
        // Closer way (Kankarbagh Thana) comes first
        assertEquals("Kankarbagh Thana", stations[0].name)
        assertNull(stations[0].phone)
        assertEquals("Kotwali Police Station", stations[1].name)
        assertEquals("+91-612-2221100", stations[1].phone)
        assertTrue(stations[0].distanceMeters < stations[1].distanceMeters)
    }

    @Test
    fun computeRouteProgress_trimsRemainingPolylineAndTriggersArrivalAt35Meters() {
        val route = RouteOption(
            id = "r1",
            label = "Main Corridor",
            summary = "Summary",
            distanceMeters = 1000,
            durationSeconds = 120,
            providerName = "OSRM",
            providerIsFallback = false,
            safetyTier = SafetyTier.HIGHER_CONTEXT,
            safetyLabel = "High Crowd",
            dataCoveragePercent = 90,
            freshnessNote = "Live",
            caveat = "",
            factors = emptyList(),
            geometry = listOf(
                LatLon(25.5941, 85.1376),
                LatLon(25.5980, 85.1376),
                LatLon(25.6039, 85.1376)
            ),
            steps = listOf(
                NavigationStep(
                    instruction = "Turn left onto Station Road",
                    roadName = "Station Road",
                    maneuverType = "turn",
                    maneuverModifier = "left",
                    distanceMeters = 450,
                    durationSeconds = 60,
                    location = LatLon(25.5980, 85.1376)
                ),
                NavigationStep(
                    instruction = "Arrive at Patna Junction",
                    roadName = "Patna Junction",
                    maneuverType = "arrive",
                    maneuverModifier = "",
                    distanceMeters = 50,
                    durationSeconds = 10,
                    location = LatLon(25.6039, 85.1376)
                )
            )
        )
        val dest = LatLon(25.6039, 85.1376)

        // Mid-route -> not arrived yet, remaining polyline starts at current location toward dest
        val midProgress = RoutingAndSafetyEngine.computeRouteProgress(
            currentLocation = LatLon(25.5975, 85.1376),
            route = route,
            destination = dest,
            speedKmh = 24.0
        )
        assertFalse(midProgress.hasArrived)
        assertEquals("Turn left onto Station Road", midProgress.activeStep?.instruction)
        assertTrue(midProgress.remainingDistanceMeters in 650..780)
        assertEquals(1000, midProgress.totalDistanceMeters)
        assertTrue(midProgress.coveredDistanceMeters in 220..350)
        assertTrue(midProgress.progressFraction in 0.2f..0.4f)
        assertEquals(24.0, midProgress.speedKmh ?: 0.0, 0.01)
        assertEquals(LatLon(25.5975, 85.1376), midProgress.remainingPolyline.first())

        assertEquals("850 m", RoutingAndSafetyEngine.formatDistanceMeters(850))
        assertEquals("4.2 km", RoutingAndSafetyEngine.formatDistanceMeters(4200))
        assertEquals("650 m", RoutingAndSafetyEngine.formatDistanceKm(0.65))
        assertEquals("4.2 km", RoutingAndSafetyEngine.formatDistanceKm(4.2))

        // Within 20 meters of destination -> hasArrived triggers!
        val arrivedProgress = RoutingAndSafetyEngine.computeRouteProgress(
            currentLocation = LatLon(25.6038, 85.1376),
            route = route,
            destination = dest
        )
        assertTrue(arrivedProgress.distanceToDestinationMeters <= 35.0)
        assertTrue(arrivedProgress.hasArrived)
        assertEquals(1.0f, arrivedProgress.progressFraction, 0.001f)
        assertEquals(arrivedProgress.totalDistanceMeters, arrivedProgress.coveredDistanceMeters)
    }

    @Test
    fun guardianShareHelper_buildsClickableOsmUrlAndLiveMessage() {
        val url = GuardianShareHelper.buildOpenStreetMapLink(25.6039, 85.1376)
        assertEquals("https://www.openstreetmap.org/?mlat=25.60390&mlon=85.13760#map=17/25.60390/85.13760", url)

        val msg = GuardianShareHelper.buildLiveShareMessage(
            travellerName = "Ananya",
            locationName = "Station Road, Patna",
            lat = 25.6039,
            lon = 85.1376,
            destinationLabel = "Patna Junction",
            shareTokenId = "tok_secure_123456789",
            remainingMeters = 850
        )
        assertTrue(msg.contains("Ananya"))
        assertTrue(msg.contains("Station Road, Patna"))
        assertTrue(msg.contains("tok_secure_123456789"))
        assertTrue(msg.contains("850 m"))
        assertTrue(msg.contains("https://www.openstreetmap.org/?mlat=25.60390&mlon=85.13760"))
    }
}
