package com.example.data.service

import com.example.data.model.ColoredRouteSegment
import com.example.data.model.CrowdActivityTier
import com.example.data.model.DataProvenance
import com.example.data.model.EmergencyFacility
import com.example.data.model.LatLon
import com.example.data.model.NavigationStep
import com.example.data.model.PlaceSuggestion
import com.example.data.model.RouteOption
import com.example.data.model.RouteProgress
import com.example.data.model.RouteSearchResult
import com.example.data.model.SafetyFactor
import com.example.data.model.SafetyReport
import com.example.data.model.SafetyTier
import com.example.data.model.SegmentSafetyLevel
import com.example.data.model.WeatherContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class DestinationPreset(
    val label: String,
    val lat: Double,
    val lon: Double,
    val areaNote: String
)

object RoutingAndSafetyEngine {
    const val DESTINATION_ARRIVAL_THRESHOLD_METERS = 35.0

    val defaultApiClient: FreeSafetyApisClient by lazy { FreeSafetyApisClient() }

    // Authoritative national helplines (never fabricated local street numbers)
    val verifiedNationalHelplines: List<EmergencyFacility> = listOf(
        EmergencyFacility(
            id = "nat_112",
            type = "HELPLINE",
            name = "Emergency Response Support System (ERSS)",
            phone = "112",
            address = "Pan-India Integrated Police, Fire & Medical Dispatch",
            lat = 28.6139,
            lon = 77.2090,
            verifiedSource = "Ministry of Home Affairs, Govt. of India",
            verifiedAtLabel = "2026-11-01",
            activeStatus = true
        ),
        EmergencyFacility(
            id = "nat_1091",
            type = "POLICE",
            name = "Women Powerline / Police Helpline",
            phone = "1091",
            address = "24x7 Dedicated Women Safety Desk",
            lat = 28.6139,
            lon = 77.2090,
            verifiedSource = "National Police Helpline Directory",
            verifiedAtLabel = "2026-11-01",
            activeStatus = true
        ),
        EmergencyFacility(
            id = "nat_181",
            type = "HELPLINE",
            name = "Women Helpline (Distress & Support)",
            phone = "181",
            address = "Ministry of Women & Child Development Support Line",
            lat = 28.6139,
            lon = 77.2090,
            verifiedSource = "MWCD Govt. of India",
            verifiedAtLabel = "2026-11-01",
            activeStatus = true
        ),
        EmergencyFacility(
            id = "nat_108",
            type = "HOSPITAL",
            name = "National Medical & Ambulance Emergency",
            phone = "108",
            address = "24x7 Emergency Ambulance Dispatch",
            lat = 28.6139,
            lon = 77.2090,
            verifiedSource = "National Health Mission",
            verifiedAtLabel = "2026-11-01",
            activeStatus = true
        )
    )

    val destinationPresets: List<DestinationPreset> = listOf(
        DestinationPreset("Hauz Khas Metro Gate 1", 28.5433, 77.2066, "South Delhi Transit Hub"),
        DestinationPreset("Rajiv Chowk Metro (CP)", 28.6328, 77.2197, "Central Illuminated Arterial"),
        DestinationPreset("AIIMS Main Gate", 28.5672, 77.2100, "24x7 Hospital Corridor"),
        DestinationPreset("Indiranagar 100ft Road", 12.9784, 77.6408, "Bengaluru Lit Commercial Corridor"),
        DestinationPreset("Bandra Station West", 19.0544, 72.8402, "Mumbai Transit Precinct")
    )

    suspend fun searchRoutes(
        originLabel: String,
        origin: LatLon,
        destinationQuery: String,
        verifiedReports: List<SafetyReport>,
        facilities: List<EmergencyFacility>,
        selectedPlace: PlaceSuggestion? = null,
        apiClient: FreeSafetyApisClient = defaultApiClient
    ): RouteSearchResult = withContext(Dispatchers.IO) {
        val resolvedDest = resolveDestination(destinationQuery, origin, selectedPlace, apiClient)
        val destination = LatLon(resolvedDest.lat, resolvedDest.lon)

        val (osrmRoutes, weatherContext) = coroutineScope {
            val routesDeferred = async { apiClient.fetchOsrmDrivingAlternatives(origin, destination) }
            val weatherDeferred = async { apiClient.fetchWeatherContext(origin.lat, origin.lon) }
            routesDeferred.await() to weatherDeferred.await()
        }

        val isFallback = osrmRoutes.isEmpty()
        val candidateRoutes: List<Triple<String, OsrmAlternativeRoute, Int>> = if (!isFallback) {
            osrmRoutes.mapIndexed { idx, r ->
                val title = when (idx) {
                    0 -> "Primary Road Corridor"
                    1 -> "Alternative Transit Corridor"
                    else -> "Secondary Neighbourhood Link"
                }
                Triple(title, r, idx)
            }
        } else {
            buildExplicitFallbackCorridors(origin, destination, resolvedDest.label).mapIndexed { idx, r ->
                val title = when (idx) {
                    0 -> "Main Arterial Boulevard (Offline Est.)"
                    1 -> "Transit & Market Corridor (Offline Est.)"
                    else -> "Direct Inner-Lane Cut (Offline Est.)"
                }
                Triple(title, r, idx)
            }
        }

        val allPoints = candidateRoutes.flatMap { it.second.geometry }
        val overpassSignals = if (allPoints.isNotEmpty()) {
            apiClient.fetchCorridorCrowdAndLighting(allPoints)
        } else {
            OverpassCorridorSignals()
        }

        val options = candidateRoutes.mapIndexed { idx, (label, parsedRoute, corridorIndex) ->
            assessRouteSafety(
                routeId = "route_${idx + 1}",
                label = label,
                distanceMeters = parsedRoute.distanceMeters,
                durationSeconds = parsedRoute.durationSeconds,
                geometry = parsedRoute.geometry,
                steps = parsedRoute.steps,
                providerName = if (isFallback) "Estimated Offline Corridor (OSRM unreachable)" else "OSRM Real Road Graph",
                providerIsFallback = isFallback,
                corridorIndex = corridorIndex,
                verifiedReports = verifiedReports,
                facilities = facilities,
                overpassSignals = overpassSignals,
                weatherContext = weatherContext
            )
        }.sortedByDescending {
            when (it.crowdActivityTier) {
                CrowdActivityTier.HIGH_CROWD_WELL_LIT -> 3
                CrowdActivityTier.MODERATE_ACTIVITY -> 2
                CrowdActivityTier.LOW_CROWD_ISOLATED -> 1
            } * 100 + it.crowdPoiCount
        }

        val nightNote = if (weatherContext != null && !weatherContext.isDay) {
            " • After-dark caution active (${weatherContext.temperatureCelsius.roundToInt()}°C)"
        } else if (weatherContext != null) {
            " • ${weatherContext.temperatureCelsius.roundToInt()}°C (${weatherContext.conditionLabel})"
        } else {
            ""
        }

        RouteSearchResult(
            originLabel = originLabel,
            origin = origin,
            destinationLabel = resolvedDest.label,
            destination = destination,
            routes = options,
            providerBanner = if (isFallback) {
                "OSRM offline/unreachable — showing estimated corridors$nightNote"
            } else if (overpassSignals.isAvailable) {
                "Live OSRM roads + OSM Overpass crowd/lighting intelligence$nightNote"
            } else {
                "Live road geometry & ETA from OSRM$nightNote"
            },
            hasProviderWarning = isFallback,
            weatherContext = weatherContext
        )
    }

    private suspend fun resolveDestination(
        query: String,
        origin: LatLon,
        selectedPlace: PlaceSuggestion?,
        apiClient: FreeSafetyApisClient
    ): DestinationPreset {
        if (selectedPlace != null) {
            return DestinationPreset(
                label = selectedPlace.shortName,
                lat = selectedPlace.lat,
                lon = selectedPlace.lon,
                areaNote = selectedPlace.locality
            )
        }
        val trimmed = query.trim()
        val presetMatch = destinationPresets.firstOrNull {
            it.label.equals(trimmed, ignoreCase = true) ||
                (trimmed.length >= 4 && it.label.contains(trimmed, ignoreCase = true))
        }
        if (presetMatch != null && haversineMeters(origin.lat, origin.lon, presetMatch.lat, presetMatch.lon) < 120_000.0) {
            return presetMatch
        }

        if (trimmed.length >= 2) {
            val nominatimMatch = apiClient.searchPlacesIndia(trimmed).firstOrNull()
            if (nominatimMatch != null) {
                return DestinationPreset(
                    label = nominatimMatch.shortName,
                    lat = nominatimMatch.lat,
                    lon = nominatimMatch.lon,
                    areaNote = nominatimMatch.locality
                )
            }
        }

        if (presetMatch != null) return presetMatch
        val destLabel = trimmed.ifBlank { "Selected Safe Transit Hub" }
        return DestinationPreset(
            label = destLabel,
            lat = (origin.lat + 0.016).coerceIn(-89.0, 89.0),
            lon = (origin.lon + 0.014).coerceIn(-179.0, 179.0),
            areaNote = "Estimated coordinates (offline)"
        )
    }

    private fun buildExplicitFallbackCorridors(
        origin: LatLon,
        destination: LatLon,
        destinationLabel: String
    ): List<OsrmAlternativeRoute> {
        val baseDist = haversineMeters(origin.lat, origin.lon, destination.lat, destination.lon).toInt().coerceAtLeast(450)
        val midLat = (origin.lat + destination.lat) / 2.0
        val midLon = (origin.lon + destination.lon) / 2.0

        val mainGeom = listOf(
            origin,
            LatLon(origin.lat + (destination.lat - origin.lat) * 0.35 + 0.0022, origin.lon + (destination.lon - origin.lon) * 0.30 - 0.0018),
            LatLon(midLat + 0.0018, midLon - 0.0014),
            LatLon(origin.lat + (destination.lat - origin.lat) * 0.72 + 0.0012, origin.lon + (destination.lon - origin.lon) * 0.74 - 0.0008),
            destination
        )
        val transitGeom = listOf(
            origin,
            LatLon(origin.lat + (destination.lat - origin.lat) * 0.32 - 0.0020, origin.lon + (destination.lon - origin.lon) * 0.36 + 0.0022),
            LatLon(midLat - 0.0016, midLon + 0.0020),
            destination
        )
        val directGeom = listOf(
            origin,
            LatLon(midLat, midLon),
            destination
        )

        fun fallbackSteps(geom: List<LatLon>, totalDist: Int, totalDur: Int): List<NavigationStep> {
            val mid = geom[geom.size / 2]
            return listOf(
                NavigationStep("Head along main road toward $destinationLabel", "Main Road", "depart", "", totalDist / 2, totalDur / 2, geom.first()),
                NavigationStep("Continue toward $destinationLabel", "Approach Road", "turn", "slight right", totalDist / 2, totalDur / 2, mid),
                NavigationStep("Arrive at $destinationLabel", "", "arrive", "", 0, 0, geom.last())
            )
        }

        val d0 = (baseDist * 1.18).toInt()
        val t0 = (d0 / 4.5).toInt().coerceAtLeast(120)
        val d1 = (baseDist * 1.26).toInt()
        val t1 = (d1 / 4.2).toInt().coerceAtLeast(140)
        val d2 = (baseDist * 1.04).toInt()
        val t2 = (d2 / 4.8).toInt().coerceAtLeast(100)

        return listOf(
            OsrmAlternativeRoute(d0, t0, mainGeom, fallbackSteps(mainGeom, d0, t0)),
            OsrmAlternativeRoute(d1, t1, transitGeom, fallbackSteps(transitGeom, d1, t1)),
            OsrmAlternativeRoute(d2, t2, directGeom, fallbackSteps(directGeom, d2, t2))
        )
    }

    internal fun assessRouteSafety(
        routeId: String,
        label: String,
        distanceMeters: Int,
        durationSeconds: Int,
        geometry: List<LatLon>,
        steps: List<NavigationStep>,
        providerName: String,
        providerIsFallback: Boolean,
        corridorIndex: Int,
        verifiedReports: List<SafetyReport>,
        facilities: List<EmergencyFacility>,
        overpassSignals: OverpassCorridorSignals,
        weatherContext: WeatherContext?
    ): RouteOption {
        val isNight = weatherContext != null && !weatherContext.isDay
        val isRaining = (weatherContext?.precipitationMm ?: 0.0) > 0.2

        val nearbyFacilities = facilities.count { fac ->
            minDistanceToPolylineMeters(LatLon(fac.lat, fac.lon), geometry) <= 1500.0
        }
        val nearbyReports = verifiedReports.count { rep ->
            minDistanceToPolylineMeters(LatLon(rep.lat, rep.lon), geometry) <= 600.0
        }

        val poiCount: Int
        val litCount: Int
        val overpassPoliceCount: Int
        val coloredSegments: List<ColoredRouteSegment>
        val crowdTier: CrowdActivityTier

        if (overpassSignals.isAvailable) {
            poiCount = overpassSignals.crowdPois.count { poi ->
                minDistanceToPolylineMeters(poi, geometry) <= 220.0
            }
            litCount = overpassSignals.litSpots.count { lit ->
                minDistanceToPolylineMeters(lit, geometry) <= 160.0
            }
            overpassPoliceCount = overpassSignals.policeSpots.count { pol ->
                minDistanceToPolylineMeters(pol, geometry) <= 1200.0
            }
            crowdTier = when {
                poiCount >= 8 && (litCount >= 3 || poiCount >= 16) -> CrowdActivityTier.HIGH_CROWD_WELL_LIT
                poiCount >= 3 -> CrowdActivityTier.MODERATE_ACTIVITY
                else -> CrowdActivityTier.LOW_CROWD_ISOLATED
            }
            coloredSegments = buildColoredSegments(geometry, overpassSignals, fallbackTier = crowdTier)
        } else {
            poiCount = 0
            litCount = 0
            overpassPoliceCount = 0
            crowdTier = when (corridorIndex) {
                0 -> CrowdActivityTier.HIGH_CROWD_WELL_LIT
                1 -> CrowdActivityTier.MODERATE_ACTIVITY
                else -> CrowdActivityTier.LOW_CROWD_ISOLATED
            }
            val segLevel = when (crowdTier) {
                CrowdActivityTier.HIGH_CROWD_WELL_LIT -> SegmentSafetyLevel.HIGH_CROWD_LIT
                CrowdActivityTier.MODERATE_ACTIVITY -> SegmentSafetyLevel.MODERATE_ACTIVITY
                CrowdActivityTier.LOW_CROWD_ISOLATED -> SegmentSafetyLevel.LOW_CROWD_ISOLATED
            }
            coloredSegments = if (geometry.size >= 2) listOf(ColoredRouteSegment(geometry, segLevel)) else emptyList()
        }

        val lightingScore: Int? = if (overpassSignals.isAvailable) {
            val baseLit = when {
                litCount >= 6 -> 90
                litCount >= 2 -> 76
                poiCount >= 10 -> 72
                poiCount >= 3 -> 58
                else -> 40
            }
            if (isNight && litCount == 0 && poiCount < 6) (baseLit - 15).coerceAtLeast(25) else baseLit
        } else {
            when (corridorIndex) {
                0 -> if (isNight) 74 else 82
                1 -> if (isNight) 64 else 74
                else -> null
            }
        }

        val footfallScore = if (overpassSignals.isAvailable) {
            val base = (40 + poiCount * 4).coerceIn(32, 95)
            if (isNight && crowdTier == CrowdActivityTier.LOW_CROWD_ISOLATED) (base - 12).coerceAtLeast(22) else base
        } else {
            when (corridorIndex) {
                0 -> 79
                1 -> 84
                else -> 44
            }
        }

        val totalPolice = nearbyFacilities + overpassPoliceCount
        val emergencyProxScore = (52 + totalPolice * 12).coerceAtMost(95)
        val incidentContextScore = (88 - nearbyReports * 18).coerceIn(30, 92)

        val nightWarningSuffix = if (isNight) " Night-time visibility & crowd drop factored (Open-Meteo after-dark)." else ""
        val rainSuffix = if (isRaining) " Wet road conditions (${String.format(Locale.US, "%.1f", weatherContext?.precipitationMm ?: 0.0)} mm precip)." else ""

        val factors = listOf(
            SafetyFactor(
                id = "lighting",
                title = "Street Lighting & Visibility",
                statusLabel = when {
                    lightingScore == null -> "Unavailable"
                    litCount > 0 -> "$litCount OSM lit tags mapped"
                    lightingScore >= 72 -> "Well-lit commercial corridor"
                    else -> if (isNight) "Dim / unverified after-dark stretch" else "Moderate lighting"
                },
                scoreOutOf100 = lightingScore,
                explanation = if (lightingScore == null) {
                    "Municipal lighting audit data is not yet available for inner-lane segments.$nightWarningSuffix"
                } else if (overpassSignals.isAvailable) {
                    "Measured from OSM Overpass lit=yes tags ($litCount) and active commercial nodes.$nightWarningSuffix$rainSuffix"
                } else {
                    "Primary arterial segments have baseline street lighting along major junctions.$nightWarningSuffix$rainSuffix"
                },
                provenance = when {
                    lightingScore == null -> DataProvenance.UNAVAILABLE
                    overpassSignals.isAvailable -> DataProvenance.VERIFIED
                    else -> DataProvenance.ESTIMATED
                },
                freshnessLabel = if (overpassSignals.isAvailable) "Live Overpass OSM" else "Estimated baseline"
            ),
            SafetyFactor(
                id = "footfall",
                title = "Crowd Density & Active POIs",
                statusLabel = if (overpassSignals.isAvailable) {
                    "${crowdTier.label} ($poiCount POIs)"
                } else {
                    crowdTier.label
                },
                scoreOutOf100 = footfallScore,
                explanation = if (overpassSignals.isAvailable) {
                    "Counted $poiCount active shops, marketplaces, bus stops, and hospitals within 220m of route.$nightWarningSuffix"
                } else {
                    "Estimated activity tier based on road class and transit corridors.$nightWarningSuffix"
                },
                provenance = if (overpassSignals.isAvailable) DataProvenance.VERIFIED else DataProvenance.ESTIMATED,
                freshnessLabel = if (overpassSignals.isAvailable) "Live Overpass OSM" else "Offline profile"
            ),
            SafetyFactor(
                id = "emergency_prox",
                title = "Nearby Police & Help Points",
                statusLabel = if (totalPolice > 0) "$totalPolice station/help records near route" else "112 / 1091 always active",
                scoreOutOf100 = emergencyProxScore,
                explanation = "Combines Overpass OSM police stations ($overpassPoliceCount along route) and verified NIRAPAD directory entries.",
                provenance = DataProvenance.VERIFIED,
                freshnessLabel = "OSM + Verified Directory"
            ),
            SafetyFactor(
                id = "community_reports",
                title = "Moderated Safety Reports",
                statusLabel = if (nearbyReports == 0) "No verified alerts nearby" else "$nearbyReports verified report(s) along corridor",
                scoreOutOf100 = incidentContextScore,
                explanation = "Only admin-moderated reports affect this signal; pending reports are excluded.",
                provenance = if (nearbyReports > 0) DataProvenance.VERIFIED else DataProvenance.ESTIMATED,
                freshnessLabel = "Live Firestore moderation"
            )
        )

        val availableCount = factors.count { it.scoreOutOf100 != null }
        val coveragePercent = (availableCount * 100) / factors.size

        val (tier, safetyLabel) = when {
            lightingScore == null || (isNight && crowdTier == CrowdActivityTier.LOW_CROWD_ISOLATED) ->
                SafetyTier.LIMITED_DATA to if (isNight) "Night warning: low crowd / isolated stretch" else crowdTier.label
            crowdTier == CrowdActivityTier.HIGH_CROWD_WELL_LIT ->
                SafetyTier.HIGHER_CONTEXT to crowdTier.label
            crowdTier == CrowdActivityTier.MODERATE_ACTIVITY ->
                SafetyTier.MODERATE_CONTEXT to crowdTier.label
            else ->
                SafetyTier.LIMITED_DATA to crowdTier.label
        }

        val summary = buildString {
            append(
                when (crowdTier) {
                    CrowdActivityTier.HIGH_CROWD_WELL_LIT -> "Follows busy shops, transit stops, and better-lit road segments."
                    CrowdActivityTier.MODERATE_ACTIVITY -> "Balanced main-road activity with some quieter mid-blocks."
                    CrowdActivityTier.LOW_CROWD_ISOLATED -> "Fewer open shops and sparse street-lighting tags mapped."
                }
            )
            if (isNight) append(" After dark: prefer high-crowd lit corridor & share live link.")
        }

        return RouteOption(
            id = routeId,
            label = label,
            summary = summary,
            distanceMeters = distanceMeters,
            durationSeconds = durationSeconds,
            providerName = providerName,
            providerIsFallback = providerIsFallback,
            safetyTier = tier,
            safetyLabel = safetyLabel,
            dataCoveragePercent = coveragePercent,
            freshnessNote = "${crowdTier.label} • $poiCount POIs • $litCount lit",
            caveat = if (isNight) {
                "Night-safety boost active: stay on populated roads and keep Guardian Sharing ON."
            } else {
                "Comparative decision support from OSM & Overpass — stay alert to live conditions."
            },
            factors = factors,
            geometry = geometry,
            steps = steps,
            coloredSegments = coloredSegments,
            crowdActivityTier = crowdTier,
            crowdPoiCount = poiCount,
            litCount = litCount,
            nearbyPoliceCount = overpassPoliceCount,
            isNightTime = isNight
        )
    }

    internal fun buildColoredSegments(
        geometry: List<LatLon>,
        signals: OverpassCorridorSignals,
        fallbackTier: CrowdActivityTier
    ): List<ColoredRouteSegment> {
        if (geometry.size < 2) return emptyList()
        if (!signals.isAvailable) {
            val lvl = when (fallbackTier) {
                CrowdActivityTier.HIGH_CROWD_WELL_LIT -> SegmentSafetyLevel.HIGH_CROWD_LIT
                CrowdActivityTier.MODERATE_ACTIVITY -> SegmentSafetyLevel.MODERATE_ACTIVITY
                CrowdActivityTier.LOW_CROWD_ISOLATED -> SegmentSafetyLevel.LOW_CROWD_ISOLATED
            }
            return listOf(ColoredRouteSegment(geometry, lvl))
        }

        fun classifyPoint(pt: LatLon): SegmentSafetyLevel {
            val nearPois = signals.crowdPois.count { haversineMeters(pt.lat, pt.lon, it.lat, it.lon) <= 230.0 }
            val nearLit = signals.litSpots.any { haversineMeters(pt.lat, pt.lon, it.lat, it.lon) <= 160.0 }
            return when {
                nearPois >= 2 || (nearPois >= 1 && nearLit) -> SegmentSafetyLevel.HIGH_CROWD_LIT
                nearPois == 1 || nearLit -> SegmentSafetyLevel.MODERATE_ACTIVITY
                else -> SegmentSafetyLevel.LOW_CROWD_ISOLATED
            }
        }

        val result = mutableListOf<ColoredRouteSegment>()
        var currentPoints = mutableListOf(geometry.first())
        var currentLevel = classifyPoint(
            LatLon(
                (geometry[0].lat + geometry[1].lat) / 2.0,
                (geometry[0].lon + geometry[1].lon) / 2.0
            )
        )

        for (i in 0 until geometry.size - 1) {
            val a = geometry[i]
            val b = geometry[i + 1]
            val mid = LatLon((a.lat + b.lat) / 2.0, (a.lon + b.lon) / 2.0)
            val lvl = classifyPoint(mid)
            if (lvl == currentLevel) {
                currentPoints.add(b)
            } else {
                result.add(ColoredRouteSegment(currentPoints.toList(), currentLevel))
                currentPoints = mutableListOf(a, b)
                currentLevel = lvl
            }
        }
        if (currentPoints.size >= 2) {
            result.add(ColoredRouteSegment(currentPoints.toList(), currentLevel))
        }
        return result
    }

    fun computeRouteProgress(
        currentLocation: LatLon,
        route: RouteOption?,
        destination: LatLon,
        arrivalThresholdMeters: Double = DESTINATION_ARRIVAL_THRESHOLD_METERS
    ): RouteProgress {
        val distToDest = haversineMeters(currentLocation.lat, currentLocation.lon, destination.lat, destination.lon)
        val hasArrived = distToDest <= arrivalThresholdMeters
        val geom = route?.geometry.orEmpty()
        if (geom.size < 2) {
            val remDist = distToDest.roundToInt().coerceAtLeast(0)
            val remDur = ((remDist / 6.5).roundToInt()).coerceAtLeast(if (remDist == 0) 0 else 30)
            return RouteProgress(
                remainingPolyline = listOf(currentLocation, destination),
                remainingDistanceMeters = remDist,
                remainingDurationSeconds = remDur,
                distanceToDestinationMeters = distToDest,
                activeStep = route?.steps?.firstOrNull(),
                distanceToNextStepMeters = remDist,
                hasArrived = hasArrived
            )
        }

        var bestSegIdx = 0
        var bestProj = geom.first()
        var bestDist = Double.MAX_VALUE
        for (i in 0 until geom.size - 1) {
            val (projPt, segDist) = projectPointOnSegment(currentLocation, geom[i], geom[i + 1])
            if (segDist < bestDist) {
                bestDist = segDist
                bestSegIdx = i
                bestProj = projPt
            }
        }

        val remainingPolyline = buildList {
            add(bestProj)
            for (k in (bestSegIdx + 1) until geom.size) {
                add(geom[k])
            }
        }
        var remDistMeters = 0.0
        for (i in 0 until remainingPolyline.size - 1) {
            remDistMeters += haversineMeters(
                remainingPolyline[i].lat,
                remainingPolyline[i].lon,
                remainingPolyline[i + 1].lat,
                remainingPolyline[i + 1].lon
            )
        }
        val roundedRemDist = remDistMeters.roundToInt().coerceAtLeast(0)
        val totalRouteDist = max(route?.distanceMeters ?: roundedRemDist, 1)
        val totalRouteDur = route?.durationSeconds ?: (totalRouteDist / 6)
        val remDurSec = if (hasArrived || roundedRemDist <= 15) {
            0
        } else {
            ((roundedRemDist.toDouble() / totalRouteDist.toDouble()) * totalRouteDur.toDouble())
                .roundToInt()
                .coerceAtLeast(30)
        }

        val steps = route?.steps.orEmpty()
        var chosenStep: NavigationStep? = null
        var distToNextStep = roundedRemDist
        if (steps.isNotEmpty()) {
            // Find the nearest upcoming non-arrive step whose maneuver is still ahead or close
            val nonArrive = steps.filter { it.maneuverType != "arrive" }
            val nextAhead = nonArrive.firstOrNull { step ->
                haversineMeters(bestProj.lat, bestProj.lon, step.location.lat, step.location.lon) > 15.0 &&
                    haversineMeters(step.location.lat, step.location.lon, destination.lat, destination.lon) < distToDest + 40.0
            } ?: steps.lastOrNull()
            chosenStep = nextAhead
            if (nextAhead != null) {
                distToNextStep = haversineMeters(currentLocation.lat, currentLocation.lon, nextAhead.location.lat, nextAhead.location.lon)
                    .roundToInt()
                    .coerceAtLeast(0)
            }
        }

        return RouteProgress(
            remainingPolyline = remainingPolyline,
            remainingDistanceMeters = roundedRemDist,
            remainingDurationSeconds = remDurSec,
            distanceToDestinationMeters = distToDest,
            activeStep = chosenStep,
            distanceToNextStepMeters = distToNextStep,
            hasArrived = hasArrived
        )
    }

    private fun projectPointOnSegment(p: LatLon, a: LatLon, b: LatLon): Pair<LatLon, Double> {
        val latScale = 111_320.0
        val lonScale = 111_320.0 * cos(a.lat * PI / 180.0)
        val bx = (b.lon - a.lon) * lonScale
        val by = (b.lat - a.lat) * latScale
        val px = (p.lon - a.lon) * lonScale
        val py = (p.lat - a.lat) * latScale
        val lenSq = bx * bx + by * by
        if (lenSq < 1e-6) return a to sqrt(px * px + py * py)
        val t = ((px * bx + py * by) / lenSq).coerceIn(0.0, 1.0)
        val projX = t * bx
        val projY = t * by
        val dx = px - projX
        val dy = py - projY
        val projLatLon = LatLon(
            lat = a.lat + (b.lat - a.lat) * t,
            lon = a.lon + (b.lon - a.lon) * t
        )
        return projLatLon to sqrt(dx * dx + dy * dy)
    }

    fun minDistanceToPolylineMeters(point: LatLon, polyline: List<LatLon>): Double {
        if (polyline.isEmpty()) return 0.0
        if (polyline.size == 1) return haversineMeters(point.lat, point.lon, polyline[0].lat, polyline[0].lon)
        var best = Double.MAX_VALUE
        for (i in 0 until polyline.size - 1) {
            val d = pointToSegmentDistanceMeters(point, polyline[i], polyline[i + 1])
            if (d < best) best = d
        }
        return best
    }

    private fun pointToSegmentDistanceMeters(p: LatLon, a: LatLon, b: LatLon): Double =
        projectPointOnSegment(p, a, b).second

    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = (lat2 - lat1) * PI / 180.0
        val dLon = (lon2 - lon1) * PI / 180.0
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1 * PI / 180.0) * cos(lat2 * PI / 180.0) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
