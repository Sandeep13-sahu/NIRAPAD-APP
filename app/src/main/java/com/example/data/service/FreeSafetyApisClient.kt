package com.example.data.service

import android.util.Log
import com.example.data.model.LatLon
import com.example.data.model.NavigationStep
import com.example.data.model.NearbyPoliceStation
import com.example.data.model.PlaceSuggestion
import com.example.data.model.WeatherContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class OsrmAlternativeRoute(
    val distanceMeters: Int,
    val durationSeconds: Int,
    val geometry: List<LatLon>,
    val steps: List<NavigationStep>
)

data class OverpassCorridorSignals(
    val crowdPois: List<LatLon> = emptyList(),
    val litSpots: List<LatLon> = emptyList(),
    val policeSpots: List<LatLon> = emptyList(),
    val isAvailable: Boolean = false
)

interface FreeHttpTransport {
    fun get(url: String, headers: Map<String, String>): String?
    fun postForm(url: String, formFields: Map<String, String>, headers: Map<String, String>): String?
}

internal class OkHttpFreeTransport(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
) : FreeHttpTransport {
    override fun get(url: String, headers: Map<String, String>): String? {
        val builder = Request.Builder().url(url)
        headers.forEach { (k, v) -> builder.header(k, v) }
        return client.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.w("FreeSafetyApis", "GET $url failed HTTP ${resp.code}")
                null
            } else {
                resp.body?.string()
            }
        }
    }

    override fun postForm(url: String, formFields: Map<String, String>, headers: Map<String, String>): String? {
        val form = FormBody.Builder().apply {
            formFields.forEach { (k, v) -> add(k, v) }
        }.build()
        val builder = Request.Builder().url(url).post(form)
        headers.forEach { (k, v) -> builder.header(k, v) }
        return client.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.w("FreeSafetyApis", "POST $url failed HTTP ${resp.code}")
                null
            } else {
                resp.body?.string()
            }
        }
    }
}

class FreeSafetyApisClient(
    private val transport: FreeHttpTransport = OkHttpFreeTransport(),
    private val clockMillis: () -> Long = { System.currentTimeMillis() }
) {
    private data class CachedValue<T>(val value: T, val expiresAtMs: Long)

    private val searchCache = ConcurrentHashMap<String, CachedValue<List<PlaceSuggestion>>>()
    private val reverseCache = ConcurrentHashMap<String, CachedValue<String>>()
    private val policeCache = ConcurrentHashMap<String, CachedValue<List<NearbyPoliceStation>>>()
    private val corridorCache = ConcurrentHashMap<String, CachedValue<OverpassCorridorSignals>>()
    private val weatherCache = ConcurrentHashMap<String, CachedValue<WeatherContext>>()

    private val nominatimHeaders = mapOf(
        "User-Agent" to USER_AGENT,
        "Accept" to "application/json",
        "Accept-Language" to "en-IN,en;q=0.9"
    )

    private val defaultHeaders = mapOf(
        "User-Agent" to USER_AGENT,
        "Accept" to "application/json"
    )

    suspend fun searchPlacesIndia(query: String): List<PlaceSuggestion> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.length < 2) return@withContext emptyList()
        val cacheKey = clean.lowercase(Locale.US)
        val now = clockMillis()
        searchCache[cacheKey]?.takeIf { it.expiresAtMs > now }?.let { return@withContext it.value }

        val encoded = URLEncoder.encode(clean, "UTF-8")
        val url = "https://nominatim.openstreetmap.org/search?format=json&addressdetails=1&limit=6&countrycodes=in&q=$encoded"
        val body = runCatching { transport.get(url, nominatimHeaders) }.getOrNull()
        val parsed = if (!body.isNullOrBlank()) parseNominatimSearchJson(body) else emptyList()
        if (parsed.isNotEmpty()) {
            searchCache[cacheKey] = CachedValue(parsed, now + SEARCH_TTL_MS)
        }
        parsed
    }

    suspend fun reverseGeocode(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        val cellKey = String.format(Locale.US, "%.4f,%.4f", lat, lon)
        val now = clockMillis()
        reverseCache[cellKey]?.takeIf { it.expiresAtMs > now }?.let { return@withContext it.value }

        val url = String.format(
            Locale.US,
            "https://nominatim.openstreetmap.org/reverse?format=json&addressdetails=1&lat=%.6f&lon=%.6f",
            lat,
            lon
        )
        val body = runCatching { transport.get(url, nominatimHeaders) }.getOrNull()
        val parsed = if (!body.isNullOrBlank()) parseNominatimReverseJson(body) else null
        if (!parsed.isNullOrBlank()) {
            reverseCache[cellKey] = CachedValue(parsed, now + REVERSE_TTL_MS)
        }
        parsed
    }

    suspend fun fetchOsrmDrivingAlternatives(
        origin: LatLon,
        destination: LatLon
    ): List<OsrmAlternativeRoute> = withContext(Dispatchers.IO) {
        val url = String.format(
            Locale.US,
            "https://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?alternatives=true&steps=true&geometries=geojson&overview=full",
            origin.lon,
            origin.lat,
            destination.lon,
            destination.lat
        )
        val body = runCatching { transport.get(url, defaultHeaders) }.getOrNull() ?: return@withContext emptyList()
        parseOsrmRoutesJson(body)
    }

    suspend fun fetchNearbyPoliceStations(
        centerLat: Double,
        centerLon: Double,
        radiusMeters: Int = 8000
    ): List<NearbyPoliceStation> = withContext(Dispatchers.IO) {
        val cellKey = String.format(Locale.US, "%.2f,%.2f:%d", centerLat, centerLon, radiusMeters)
        val now = clockMillis()
        policeCache[cellKey]?.takeIf { it.expiresAtMs > now }?.let { cached ->
            return@withContext cached.value.map { st ->
                st.copy(distanceMeters = haversineMeters(centerLat, centerLon, st.lat, st.lon))
            }.sortedBy { it.distanceMeters }
        }

        val ql = String.format(
            Locale.US,
            "[out:json][timeout:12];(node[\"amenity\"=\"police\"](around:%d,%.5f,%.5f);way[\"amenity\"=\"police\"](around:%d,%.5f,%.5f););out center tags;",
            radiusMeters, centerLat, centerLon,
            radiusMeters, centerLat, centerLon
        )
        val body = runCatching {
            transport.postForm("https://overpass-api.de/api/interpreter", mapOf("data" to ql), defaultHeaders)
        }.getOrNull()
        val parsed = if (!body.isNullOrBlank()) {
            parseOverpassPoliceJson(body, centerLat, centerLon)
        } else {
            emptyList()
        }
        if (parsed.isNotEmpty()) {
            policeCache[cellKey] = CachedValue(parsed, now + OVERPASS_TTL_MS)
        }
        parsed
    }

    suspend fun fetchCorridorCrowdAndLighting(points: List<LatLon>): OverpassCorridorSignals =
        withContext(Dispatchers.IO) {
            if (points.isEmpty()) return@withContext OverpassCorridorSignals()
            val minLat = points.minOf { it.lat } - 0.004
            val maxLat = points.maxOf { it.lat } + 0.004
            val minLon = points.minOf { it.lon } - 0.004
            val maxLon = points.maxOf { it.lon } + 0.004
            val bboxKey = String.format(Locale.US, "%.3f,%.3f,%.3f,%.3f", minLat, minLon, maxLat, maxLon)
            val now = clockMillis()
            corridorCache[bboxKey]?.takeIf { it.expiresAtMs > now }?.let { return@withContext it.value }

            val bbox = String.format(Locale.US, "%.5f,%.5f,%.5f,%.5f", minLat, minLon, maxLat, maxLon)
            val ql = """
                [out:json][timeout:12];
                (
                  node["shop"]($bbox);
                  node["amenity"="marketplace"]($bbox);
                  way["amenity"="marketplace"]($bbox);
                  node["highway"="bus_stop"]($bbox);
                  node["amenity"="hospital"]($bbox);
                  way["amenity"="hospital"]($bbox);
                  node["amenity"="police"]($bbox);
                  way["amenity"="police"]($bbox);
                  way["lit"="yes"]($bbox);
                  node["lit"="yes"]($bbox);
                );
                out center tags 350;
            """.trimIndent()
            val body = runCatching {
                transport.postForm("https://overpass-api.de/api/interpreter", mapOf("data" to ql), defaultHeaders)
            }.getOrNull() ?: return@withContext OverpassCorridorSignals()

            val parsed = parseOverpassCorridorJson(body)
            if (parsed.isAvailable) {
                corridorCache[bboxKey] = CachedValue(parsed, now + OVERPASS_TTL_MS)
            }
            parsed
        }

    suspend fun fetchWeatherContext(lat: Double, lon: Double): WeatherContext? = withContext(Dispatchers.IO) {
        val cellKey = String.format(Locale.US, "%.2f,%.2f", lat, lon)
        val now = clockMillis()
        weatherCache[cellKey]?.takeIf { it.expiresAtMs > now }?.let { return@withContext it.value }

        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.5f&longitude=%.5f&current=temperature_2m,precipitation,is_day",
            lat,
            lon
        )
        val body = runCatching { transport.get(url, defaultHeaders) }.getOrNull() ?: return@withContext null
        val parsed = parseOpenMeteoJson(body, now)
        if (parsed != null) {
            weatherCache[cellKey] = CachedValue(parsed, now + WEATHER_TTL_MS)
        }
        parsed
    }

    companion object {
        const val USER_AGENT = "NirapadSafetyApp/1.0"
        private const val SEARCH_TTL_MS = 5 * 60_000L
        private const val REVERSE_TTL_MS = 5 * 60_000L
        private const val OVERPASS_TTL_MS = 10 * 60_000L
        private const val WEATHER_TTL_MS = 10 * 60_000L

        fun parseNominatimSearchJson(raw: String): List<PlaceSuggestion> {
            return try {
                val arr = JSONArray(raw)
                val out = mutableListOf<PlaceSuggestion>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val lat = obj.optString("lat").toDoubleOrNull() ?: obj.optDouble("lat", Double.NaN)
                    val lon = obj.optString("lon").toDoubleOrNull() ?: obj.optDouble("lon", Double.NaN)
                    if (lat.isNaN() || lon.isNaN()) continue
                    val display = obj.optString("display_name").trim()
                    val nameProp = obj.optString("name").trim()
                    val addr = obj.optJSONObject("address")
                    val localityParts = listOfNotNull(
                        addr?.optString("road")?.takeIf { it.isNotBlank() },
                        addr?.optString("suburb")?.takeIf { it.isNotBlank() }
                            ?: addr?.optString("neighbourhood")?.takeIf { it.isNotBlank() },
                        addr?.optString("city")?.takeIf { it.isNotBlank() }
                            ?: addr?.optString("town")?.takeIf { it.isNotBlank() }
                            ?: addr?.optString("county")?.takeIf { it.isNotBlank() },
                        addr?.optString("state")?.takeIf { it.isNotBlank() }
                    ).distinct()
                    val shortName = nameProp.ifBlank {
                        display.substringBefore(",").trim().ifBlank { "Selected Place" }
                    }
                    val locality = if (localityParts.isNotEmpty()) {
                        localityParts.joinToString(", ")
                    } else {
                        display.substringAfter(",", "").trim().take(80)
                    }
                    out.add(
                        PlaceSuggestion(
                            placeId = obj.optString("place_id").ifBlank { "${lat}_${lon}" },
                            shortName = shortName,
                            displayName = display.ifBlank { shortName },
                            locality = locality.ifBlank { "India" },
                            lat = lat,
                            lon = lon,
                            isFromPreset = false
                        )
                    )
                }
                out
            } catch (e: Exception) {
                Log.w("FreeSafetyApis", "Nominatim search parse failed: ${e.message}")
                emptyList()
            }
        }

        fun parseNominatimReverseJson(raw: String): String? {
            return try {
                val obj = JSONObject(raw)
                val addr = obj.optJSONObject("address")
                if (addr != null) {
                    val street = addr.optString("road").takeIf { it.isNotBlank() }
                        ?: addr.optString("pedestrian").takeIf { it.isNotBlank() }
                        ?: addr.optString("amenity").takeIf { it.isNotBlank() }
                    val area = addr.optString("suburb").takeIf { it.isNotBlank() }
                        ?: addr.optString("neighbourhood").takeIf { it.isNotBlank() }
                        ?: addr.optString("village").takeIf { it.isNotBlank() }
                        ?: addr.optString("town").takeIf { it.isNotBlank() }
                    val city = addr.optString("city").takeIf { it.isNotBlank() }
                        ?: addr.optString("state_district").takeIf { it.isNotBlank() }
                        ?: addr.optString("county").takeIf { it.isNotBlank() }
                    val state = addr.optString("state").takeIf { it.isNotBlank() }
                    val concise = listOfNotNull(street, area, city, state).distinct().take(3)
                    if (concise.isNotEmpty()) {
                        return concise.joinToString(", ")
                    }
                }
                val display = obj.optString("display_name").trim()
                if (display.isNotBlank()) {
                    display.split(",").map { it.trim() }.filter { it.isNotEmpty() }.take(3).joinToString(", ")
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.w("FreeSafetyApis", "Nominatim reverse parse failed: ${e.message}")
                null
            }
        }

        fun parseOsrmRoutesJson(raw: String): List<OsrmAlternativeRoute> {
            return try {
                val json = JSONObject(raw)
                if (json.optString("code") != "Ok") return emptyList()
                val routesArr = json.optJSONArray("routes") ?: return emptyList()
                val parsed = mutableListOf<OsrmAlternativeRoute>()
                for (i in 0 until min(routesArr.length(), 3)) {
                    val obj = routesArr.getJSONObject(i)
                    val distance = obj.optDouble("distance", 0.0).toInt().coerceAtLeast(100)
                    val duration = obj.optDouble("duration", 0.0).toInt().coerceAtLeast(60)
                    val geomObj = obj.optJSONObject("geometry")
                    val coords = geomObj?.optJSONArray("coordinates")
                    val points = mutableListOf<LatLon>()
                    if (coords != null) {
                        for (j in 0 until coords.length()) {
                            val pt = coords.getJSONArray(j)
                            points.add(LatLon(lat = pt.getDouble(1), lon = pt.getDouble(0)))
                        }
                    }

                    val stepsList = mutableListOf<NavigationStep>()
                    val legsArr = obj.optJSONArray("legs")
                    if (legsArr != null && legsArr.length() > 0) {
                        val stepsArr = legsArr.getJSONObject(0).optJSONArray("steps")
                        if (stepsArr != null) {
                            for (s in 0 until stepsArr.length()) {
                                val stepObj = stepsArr.getJSONObject(s)
                                val roadName = stepObj.optString("name").trim()
                                val stepDist = stepObj.optDouble("distance", 0.0).toInt().coerceAtLeast(0)
                                val stepDur = stepObj.optDouble("duration", 0.0).toInt().coerceAtLeast(0)
                                val maneuver = stepObj.optJSONObject("maneuver")
                                val type = maneuver?.optString("type").orEmpty().ifBlank { "continue" }
                                val modifier = maneuver?.optString("modifier").orEmpty()
                                val locArr = maneuver?.optJSONArray("location")
                                val loc = if (locArr != null && locArr.length() >= 2) {
                                    LatLon(lat = locArr.getDouble(1), lon = locArr.getDouble(0))
                                } else {
                                    points.firstOrNull() ?: LatLon(0.0, 0.0)
                                }
                                val instruction = formatOsrmInstruction(type, modifier, roadName)
                                stepsList.add(
                                    NavigationStep(
                                        instruction = instruction,
                                        roadName = roadName,
                                        maneuverType = type,
                                        maneuverModifier = modifier,
                                        distanceMeters = stepDist,
                                        durationSeconds = stepDur,
                                        location = loc
                                    )
                                )
                            }
                        }
                    }

                    if (points.size >= 2) {
                        parsed.add(
                            OsrmAlternativeRoute(
                                distanceMeters = distance,
                                durationSeconds = duration,
                                geometry = points,
                                steps = stepsList
                            )
                        )
                    }
                }
                parsed
            } catch (e: Exception) {
                Log.w("FreeSafetyApis", "OSRM parse failed: ${e.message}")
                emptyList()
            }
        }

        fun formatOsrmInstruction(type: String, modifier: String, roadName: String): String {
            val cleanRoad = roadName.trim()
            val ontoClause = if (cleanRoad.isNotBlank()) " onto $cleanRoad" else ""
            val onClause = if (cleanRoad.isNotBlank()) " on $cleanRoad" else ""
            val cleanMod = modifier.replace('-', ' ').trim()
            return when (type.lowercase(Locale.US)) {
                "depart" -> if (cleanRoad.isNotBlank()) "Head along $cleanRoad" else "Start along route"
                "arrive" -> "Arrive at destination"
                "roundabout", "rotary" -> "Take the roundabout$ontoClause"
                "merge" -> if (cleanMod.isNotBlank()) "Merge $cleanMod$ontoClause" else "Merge$ontoClause"
                "fork" -> if (cleanMod.isNotBlank()) "Keep $cleanMod at fork$ontoClause" else "Keep on fork$ontoClause"
                "end of road" -> if (cleanMod.isNotBlank()) "At road end, turn $cleanMod$ontoClause" else "Turn at road end$ontoClause"
                "turn", "new name", "on ramp", "off ramp" -> when (cleanMod) {
                    "uturn" -> "Make a U-turn$ontoClause"
                    "" -> "Turn$ontoClause"
                    else -> "Turn $cleanMod$ontoClause"
                }
                else -> when {
                    cleanMod.isNotBlank() && cleanMod != "straight" -> "Bear $cleanMod$ontoClause"
                    else -> "Continue straight$onClause"
                }
            }
        }

        fun parseOverpassPoliceJson(
            raw: String,
            centerLat: Double,
            centerLon: Double
        ): List<NearbyPoliceStation> {
            return try {
                val root = JSONObject(raw)
                val elements = root.optJSONArray("elements") ?: return emptyList()
                val list = mutableListOf<NearbyPoliceStation>()
                for (i in 0 until elements.length()) {
                    val el = elements.getJSONObject(i)
                    val tags = el.optJSONObject("tags") ?: continue
                    val centerObj = el.optJSONObject("center")
                    val lat = centerObj?.optDouble("lat", Double.NaN) ?: el.optDouble("lat", Double.NaN)
                    val lon = centerObj?.optDouble("lon", Double.NaN) ?: el.optDouble("lon", Double.NaN)
                    if (lat.isNaN() || lon.isNaN()) continue
                    val name = tags.optString("name").trim()
                        .ifBlank { tags.optString("official_name").trim() }
                        .ifBlank { "Local Police Station" }
                    val rawPhone = tags.optString("phone").trim()
                        .ifBlank { tags.optString("contact:phone").trim() }
                        .ifBlank { tags.optString("emergency:phone").trim() }
                        .ifBlank { null }
                    val phone = rawPhone?.split(";", ",")?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
                    val locality = listOfNotNull(
                        tags.optString("addr:street").takeIf { it.isNotBlank() },
                        tags.optString("addr:suburb").takeIf { it.isNotBlank() }
                            ?: tags.optString("addr:city").takeIf { it.isNotBlank() }
                            ?: tags.optString("addr:district").takeIf { it.isNotBlank() }
                    ).joinToString(", ").ifBlank { "Nearby Area (OSM)" }
                    val distM = haversineMeters(centerLat, centerLon, lat, lon)
                    list.add(
                        NearbyPoliceStation(
                            id = "osm_pol_${el.optLong("id", i.toLong())}",
                            name = name,
                            phone = phone,
                            locality = locality,
                            lat = lat,
                            lon = lon,
                            distanceMeters = distM
                        )
                    )
                }
                list.distinctBy { "${it.name.lowercase(Locale.US)}:${(it.lat * 1000).toInt()}" }
                    .sortedBy { it.distanceMeters }
                    .take(12)
            } catch (e: Exception) {
                Log.w("FreeSafetyApis", "Overpass police parse failed: ${e.message}")
                emptyList()
            }
        }

        fun parseOverpassCorridorJson(raw: String): OverpassCorridorSignals {
            return try {
                val root = JSONObject(raw)
                val elements = root.optJSONArray("elements") ?: return OverpassCorridorSignals()
                val crowd = mutableListOf<LatLon>()
                val lit = mutableListOf<LatLon>()
                val police = mutableListOf<LatLon>()
                for (i in 0 until elements.length()) {
                    val el = elements.getJSONObject(i)
                    val tags = el.optJSONObject("tags") ?: continue
                    val centerObj = el.optJSONObject("center")
                    val lat = centerObj?.optDouble("lat", Double.NaN) ?: el.optDouble("lat", Double.NaN)
                    val lon = centerObj?.optDouble("lon", Double.NaN) ?: el.optDouble("lon", Double.NaN)
                    if (lat.isNaN() || lon.isNaN()) continue
                    val pt = LatLon(lat, lon)
                    val amenity = tags.optString("amenity")
                    val highway = tags.optString("highway")
                    val shop = tags.optString("shop")
                    val litTag = tags.optString("lit")

                    if (amenity == "police") {
                        police.add(pt)
                        crowd.add(pt)
                    } else if (shop.isNotBlank() || amenity == "marketplace" || highway == "bus_stop" || amenity == "hospital") {
                        crowd.add(pt)
                    }
                    if (litTag.equals("yes", ignoreCase = true)) {
                        lit.add(pt)
                    }
                }
                OverpassCorridorSignals(
                    crowdPois = crowd,
                    litSpots = lit,
                    policeSpots = police,
                    isAvailable = true
                )
            } catch (e: Exception) {
                Log.w("FreeSafetyApis", "Overpass corridor parse failed: ${e.message}")
                OverpassCorridorSignals()
            }
        }

        fun parseOpenMeteoJson(raw: String, fetchedAtMs: Long = System.currentTimeMillis()): WeatherContext? {
            return try {
                val root = JSONObject(raw)
                val current = root.optJSONObject("current") ?: return null
                val temp = current.optDouble("temperature_2m", Double.NaN)
                if (temp.isNaN()) return null
                val precip = current.optDouble("precipitation", 0.0).coerceAtLeast(0.0)
                val isDay = current.optInt("is_day", 1) == 1
                val condition = when {
                    precip >= 2.0 -> "Rainy"
                    precip > 0.0 -> "Light Rain"
                    !isDay -> "After Dark"
                    else -> "Clear Daylight"
                }
                WeatherContext(
                    temperatureCelsius = temp,
                    precipitationMm = precip,
                    isDay = isDay,
                    conditionLabel = condition,
                    fetchedAtMillis = fetchedAtMs
                )
            } catch (e: Exception) {
                Log.w("FreeSafetyApis", "Open-Meteo parse failed: ${e.message}")
                null
            }
        }

        private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
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
}
