package com.example.data.repository

import android.util.Log
import com.example.data.model.AdvisoryNavigationComparison
import com.example.data.model.AdvisoryRouteComparisonItem
import com.example.data.model.LatLon
import com.example.data.model.NearbyPoliceStation
import com.example.data.model.RouteOption
import com.example.data.model.WeatherContext
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.Locale

data class SafetyAiContext(
    val currentAddressLabel: String? = null,
    val currentLatLon: LatLon? = null,
    val weatherContext: WeatherContext? = null,
    val nearestPoliceStation: NearbyPoliceStation? = null,
    val nearbyPoliceStations: List<NearbyPoliceStation> = emptyList(),
    val destinationLabel: String? = null
)

sealed interface AiAdvisorUiState {
    data object Idle : AiAdvisorUiState
    data object Loading : AiAdvisorUiState
    data class Streaming(val text: String) : AiAdvisorUiState
    data class Success(val text: String) : AiAdvisorUiState
    data class Error(val message: String) : AiAdvisorUiState
}

interface AdvisoryAiClient {
    fun askStream(
        question: String,
        selectedRoute: RouteOption?,
        activeJourneyState: String?,
        languageCode: String,
        safetyContext: SafetyAiContext = SafetyAiContext()
    ): Flow<AiAdvisorUiState>

    suspend fun compareRoutes(
        originLabel: String,
        destinationLabel: String,
        travelWindowLabel: String,
        routes: List<RouteOption>,
        languageCode: String
    ): AdvisoryNavigationComparison
}

class AiAdvisorRepository : AdvisoryAiClient {

    private val routeComparisonSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "recommendedRouteId" to Schema.string(
                    description = "The routeId of the relatively safest candidate route provided in input."
                ),
                "headlineSummary" to Schema.string(
                    description = "One clear sentence summarizing which route is comparatively safer and why."
                ),
                "comparisonOverview" to Schema.string(
                    description = "Concise comparative summary weighing safety scores, street lighting data, and public traffic."
                ),
                "routes" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "routeId" to Schema.string(description = "Exact routeId from candidate list."),
                            "routeLabel" to Schema.string(description = "Route name."),
                            "overallSafetyScore" to Schema.integer(description = "Comparative safety score from 0 to 100."),
                            "streetLightingRating" to Schema.string(description = "Short label for street lighting coverage (e.g., Well-Lit Arterial, Moderate, Limited Data)."),
                            "streetLightingScore" to Schema.integer(description = "Street lighting score 0 to 100 (0 if unavailable)."),
                            "publicTrafficLevel" to Schema.string(description = "Short label for public traffic and transit crowd activity."),
                            "publicTrafficScore" to Schema.integer(description = "Public traffic/footfall presence score 0 to 100."),
                            "recommendedRank" to Schema.integer(description = "Rank 1 for the safest relative option, 2, 3, etc."),
                            "isSaferOption" to Schema.boolean(description = "True if this is the safer route option to prefer."),
                            "aiRationale" to Schema.string(description = "Concise explanation of why this route scored this way across safety, lighting, and traffic."),
                            "precautionTip" to Schema.string(description = "Practical precautionary tip for travellers choosing this route.")
                        )
                    ),
                    description = "Ranked comparison list covering every candidate route."
                )
            )
        )
    }

    private val systemBoundaryInstruction by lazy {
        content {
            text(
                """
                You are NIRAPAD's Advisory AI Navigation Assistant for women's journey safety.
                STRICT SAFETY BOUNDARIES:
                1. You are ADVISORY ONLY. Never claim any route is 100% safe or guarantee prevention of harm.
                2. Compare candidate routes strictly using the provided safety factors, street lighting data, public traffic / transit footfall signals, verified nearby emergency facilities, and moderated community reports.
                3. Highlight where street lighting or telemetry data is unavailable or estimated rather than pretending certainty.
                4. NEVER invent or guess emergency phone numbers, police stations, hospitals, or addresses. Only reference verified numbers or Overpass stations provided in context (112, 1091, 181, 108).
                5. NEVER claim help has been dispatched or override SOS backend decisions.
                """.trimIndent()
            )
        }
    }

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = systemBoundaryInstruction
        )
    }

    private val comparisonModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = routeComparisonSchema
            },
            systemInstruction = systemBoundaryInstruction
        )
    }

    override suspend fun compareRoutes(
        originLabel: String,
        destinationLabel: String,
        travelWindowLabel: String,
        routes: List<RouteOption>,
        languageCode: String
    ): AdvisoryNavigationComparison {
        require(routes.isNotEmpty()) { "At least one candidate route is required for AI comparison." }

        val langNote = if (languageCode == "hi") {
            "Respond in simple Hindi for human-readable descriptions while keeping routeId values exact."
        } else {
            "Respond in clear, concise English while keeping routeId values exact."
        }

        val prompt = buildString {
            appendLine(langNote)
            appendLine("Compare the following candidate routes from '$originLabel' to '$destinationLabel' during travel window '$travelWindowLabel'.")
            appendLine("Evaluate each route by: (1) overall safety score & tier, (2) street lighting data & visibility, (3) public traffic & transit footfall, and (4) verified emergency access / moderated reports.")
            appendLine("Identify the safer option(s), rank safer routes first (recommendedRank = 1), and never exaggerate certainty where lighting data is unavailable.")
            appendLine()
            routes.forEach { r ->
                appendLine("Candidate routeId='${r.id}' | label='${r.label}'")
                appendLine("  Distance: ${r.distanceMeters}m, ETA: ${(r.durationSeconds / 60).coerceAtLeast(1)} min")
                appendLine("  Provider: ${r.providerName} | Classification: ${r.safetyLabel} (Data coverage: ${r.dataCoveragePercent}%)")
                appendLine("  Crowd & Lighting telemetry: tier=${r.crowdActivityTier.label}, activePOIs=${r.crowdPoiCount}, streetLitTags=${r.litCount}, corridorPolice=${r.nearbyPoliceCount}")
                r.factors.forEach { f ->
                    val scoreDesc = f.scoreOutOf100?.let { "$it/100" } ?: "UNAVAILABLE"
                    appendLine("  - Factor [${f.id}] ${f.title}: score=$scoreDesc, status='${f.statusLabel}', provenance=${f.provenance.label} — ${f.explanation}")
                }
            }
        }

        val response = try {
            withTimeout(60_000L) {
                comparisonModel.generateContent(prompt)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini route comparison timed out", e)
            throw IllegalStateException("Gemini AI route comparison timed out after 60 seconds. Please retry.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini route comparison failed", e)
            throw IllegalStateException(
                e.localizedMessage ?: "Unable to reach Gemini AI Navigation service right now.",
                e
            )
        }

        val rawJson = response.text
        if (rawJson.isNullOrBlank()) {
            throw IllegalStateException("Gemini AI returned an empty route comparison response.")
        }

        return try {
            parseComparisonJson(
                rawJson = rawJson,
                destinationLabel = destinationLabel,
                travelWindowLabel = travelWindowLabel,
                candidateRoutes = routes
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Gemini route comparison JSON", e)
            throw IllegalStateException("Gemini AI returned an unexpected comparison format. Please retry.", e)
        }
    }

    override fun askStream(
        question: String,
        selectedRoute: RouteOption?,
        activeJourneyState: String?,
        languageCode: String,
        safetyContext: SafetyAiContext
    ): Flow<AiAdvisorUiState> = flow {
        emit(AiAdvisorUiState.Loading)
        val prefersHindi = languageCode == "hi" || containsHindiScriptOrRequest(question)
        val contextBlock = buildContextPromptBlock(
            selectedRoute = selectedRoute,
            activeJourneyState = activeJourneyState,
            prefersHindi = prefersHindi,
            safetyContext = safetyContext
        )

        val fullPrompt = "$contextBlock\nUser question: $question"
        val accumulated = StringBuilder()
        var remoteSucceeded = false
        try {
            withTimeout(22_000L) {
                model.generateContentStream(fullPrompt).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        emit(AiAdvisorUiState.Streaming(accumulated.toString()))
                    }
                }
            }
            if (accumulated.isNotBlank()) {
                remoteSucceeded = true
                emit(AiAdvisorUiState.Success(accumulated.toString().trim()))
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "AI advisor timed out, using context-aware local safety guidance", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Firebase AI advisor unavailable, using context-aware local safety guidance: ${e.message}")
        }

        if (!remoteSucceeded) {
            val groundedAdvice = buildContextAwareFallbackAdvice(
                question = question,
                selectedRoute = selectedRoute,
                activeJourneyState = activeJourneyState,
                languageCode = if (prefersHindi) "hi" else languageCode,
                safetyContext = safetyContext
            )
            emit(AiAdvisorUiState.Success(groundedAdvice))
        }
    }

    companion object {
        private const val TAG = "AiAdvisorRepository"

        private fun containsHindiScriptOrRequest(text: String): Boolean {
            if (text.any { it in '\u0900'..'\u097F' }) return true
            val lower = text.lowercase(Locale.ROOT)
            return lower.contains("hindi") || lower.contains("हिन्दी") || lower.contains("हिंदी")
        }

        internal fun buildContextPromptBlock(
            selectedRoute: RouteOption?,
            activeJourneyState: String?,
            prefersHindi: Boolean,
            safetyContext: SafetyAiContext
        ): String = buildString {
            appendLine(
                "Preferred response language: ${
                    if (prefersHindi) "Hindi (clear, respectful simple Hindi)" else "English (crisp, structured bullet points)"
                }."
            )
            if (!safetyContext.currentAddressLabel.isNullOrBlank()) {
                appendLine("User current locality (Nominatim): ${safetyContext.currentAddressLabel}")
            }
            safetyContext.currentLatLon?.let { pt ->
                appendLine(String.format(Locale.US, "Current coordinates: %.4f, %.4f", pt.lat, pt.lon))
            }
            if (!safetyContext.destinationLabel.isNullOrBlank()) {
                appendLine("Target destination: ${safetyContext.destinationLabel}")
            }
            safetyContext.weatherContext?.let { w ->
                val dayNight = if (w.isDay) "Daylight" else "After-dark / Night"
                appendLine("Live weather & time: ${w.temperatureCelsius.toInt()}°C, ${w.conditionLabel}, $dayNight (precip ${w.precipitationMm} mm)")
            }
            if (selectedRoute != null) {
                val mins = (selectedRoute.durationSeconds / 60).coerceAtLeast(1)
                appendLine("Selected route: ${selectedRoute.label} (${selectedRoute.distanceMeters}m, ~$mins min)")
                appendLine("Safety classification: ${selectedRoute.safetyLabel} (Data coverage: ${selectedRoute.dataCoveragePercent}%)")
                appendLine("Crowd & lighting telemetry: ${selectedRoute.crowdActivityTier.label}, ${selectedRoute.crowdPoiCount} active POIs, ${selectedRoute.litCount} street-lit tags, ${selectedRoute.nearbyPoliceCount} police hubs on corridor")
                appendLine("Provider: ${selectedRoute.providerName}")
                appendLine("Factors:")
                selectedRoute.factors.forEach { f ->
                    appendLine("- ${f.title}: ${f.statusLabel} [${f.provenance.label}] — ${f.explanation}")
                }
            }
            val stations = (listOfNotNull(safetyContext.nearestPoliceStation) + safetyContext.nearbyPoliceStations)
                .distinctBy { it.id }
                .sortedBy { it.distanceMeters }
                .take(3)
            if (stations.isNotEmpty()) {
                appendLine("Nearest real OSM Overpass police stations:")
                stations.forEach { st ->
                    val km = String.format(Locale.US, "%.1f km", st.distanceMeters / 1000.0)
                    val phoneTxt = st.phone?.takeIf { it.isNotBlank() } ?: "Dial 112"
                    appendLine("- ${st.name} ($km, ${st.locality.ifBlank { "Nearby" }}, phone: $phoneTxt)")
                }
            }
            if (!activeJourneyState.isNullOrBlank()) {
                appendLine("Current journey state: $activeJourneyState")
            }
            appendLine("Verified national emergency numbers: 112 (ERSS), 1091 (Women Police Helpline), 181 (Women Distress), 108 (Ambulance).")
        }

        fun buildContextAwareFallbackAdvice(
            question: String,
            selectedRoute: RouteOption?,
            activeJourneyState: String?,
            languageCode: String,
            safetyContext: SafetyAiContext
        ): String {
            val isHindi = languageCode == "hi" || containsHindiScriptOrRequest(question)
            val q = question.lowercase(Locale.ROOT)
            val locality = safetyContext.currentAddressLabel?.takeIf { it.isNotBlank() }
                ?: if (isHindi) "आपका वर्तमान GPS क्षेत्र" else "your current GPS area"
            val dest = safetyContext.destinationLabel?.takeIf { it.isNotBlank() }
                ?: if (isHindi) "चयनित गंतव्य" else "your destination"
            val weather = safetyContext.weatherContext
            val isNight = weather?.let { !it.isDay } ?: (selectedRoute?.isNightTime == true)
            val weatherLineEn = weather?.let {
                val period = if (it.isDay) "Daylight" else "Night / Low Light"
                "${it.temperatureCelsius.toInt()}°C (${it.conditionLabel}, $period)"
            } ?: "Live weather telemetry unavailable"
            val weatherLineHi = weather?.let {
                val period = if (it.isDay) "दिन का समय" else "रात्रि / कम रोशनी"
                "${it.temperatureCelsius.toInt()}°C (${it.conditionLabel}, $period)"
            } ?: "मौसम डेटा उपलब्ध नहीं"

            val stations = (listOfNotNull(safetyContext.nearestPoliceStation) + safetyContext.nearbyPoliceStations)
                .distinctBy { it.id }
                .sortedBy { it.distanceMeters }
            val nearestPolice = stations.firstOrNull()
            val policeDistKm = nearestPolice?.let { String.format(Locale.US, "%.1f km", it.distanceMeters / 1000.0) }

            val isFollowedQuery = q.contains("follow") || q.contains("stalk") || q.contains("danger") ||
                q.contains("unsafe") || q.contains("threat") || q.contains("पीछा") || q.contains("खतरा")
            val isPoliceQuery = q.contains("police") || q.contains("station") || q.contains("spot") ||
                q.contains("help") || q.contains("थाना") || q.contains("पुलिस") || q.contains("मदद")
            val isNightQuery = q.contains("night") || q.contains("dark") || q.contains("evening") ||
                q.contains("रात") || q.contains("अंधेरा")

            if (isHindi) {
                return buildString {
                    appendLine("• स्थान और स्थिति: $locality • $weatherLineHi")
                    when {
                        isFollowedQuery -> {
                            appendLine("• तुरंत कार्रवाई (यदि कोई पीछा कर रहा हो): तुरंत किसी भीड़भाड़ वाले व रोशनी वाले स्थान (दुकान, मेडिकल स्टोर, मेट्रो स्टेशन या पेट्रोल पंप) के अंदर जाएं — सीधे घर या सुनसान गली की ओर न जाएं।")
                            if (nearestPolice != null) {
                                appendLine("• निकटतम पुलिस सहायता: ${nearestPolice.name} (~$policeDistKm, ${nearestPolice.phone?.takeIf { it.isNotBlank() } ?: "112 पर कॉल करें"})।")
                            }
                            appendLine("• तुरंत कॉल/SOS: 112 (ERSS) या 1091 (महिला हेल्पलाइन) डायल करें, किसी विश्वसनीय संपर्क से लाइव कॉल पर रहें और WhatsApp पर अपनी लाइव लोकेशन साझा करें।")
                        }
                        isPoliceQuery -> {
                            if (stations.isNotEmpty()) {
                                appendLine("• निकटतम पुलिस स्टेशन (OpenStreetMap Overpass):")
                                stations.take(3).forEach { st ->
                                    val km = String.format(Locale.US, "%.1f km", st.distanceMeters / 1000.0)
                                    val ph = st.phone?.takeIf { it.isNotBlank() } ?: "112"
                                    appendLine("  - ${st.name}: $km (${st.locality.ifBlank { "आस-पास" }}) • फ़ोन: $ph")
                                }
                            } else {
                                appendLine("• पुलिस स्टेशन जानकारी: वर्तमान त्रिज्या में Overpass डेटा लोड नहीं हुआ है; आपात स्थिति में तुरंत 112 या 1091 पर कॉल करें।")
                            }
                            if (selectedRoute != null && selectedRoute.crowdPoiCount > 0) {
                                appendLine("• सक्रिय सुरक्षित स्थान: इस मार्ग पर ${selectedRoute.crowdPoiCount} सक्रिय दुकानें/ट्रांज़िट स्पॉट पहचाने गए हैं।")
                            }
                            appendLine("• सत्यापित हेल्पलाइन: 112 (आपातकाल), 1091 (महिला पुलिस), 181 (महिला सहायता), 108 (एम्बुलेंस)।")
                        }
                        isNightQuery -> {
                            appendLine("• रात्रि यात्रा सुझाव: कम रोशनी में मुख्य बाज़ार या ट्रांज़िट सड़कों का उपयोग करें जहां लोगों की आवाजाही बनी रहती है।")
                            if (selectedRoute != null) {
                                appendLine("• चयनित मार्ग (${selectedRoute.label}): ${selectedRoute.crowdActivityTier.label} • ${selectedRoute.litCount} स्ट्रीट-लाइट टैग, ${selectedRoute.crowdPoiCount} सक्रिय POI।")
                            }
                            appendLine("• सुरक्षा जांच: यात्रा शुरू करने से पहले WhatsApp लाइव शेयरिंग चालू रखें, कैब/ऑटो नंबर विश्वसनीय व्यक्ति को भेजें और 112/SOS तैयार रखें।")
                        }
                        else -> {
                            if (selectedRoute != null) {
                                val mins = (selectedRoute.durationSeconds / 60).coerceAtLeast(1)
                                val km = String.format(Locale.US, "%.1f km", selectedRoute.distanceMeters / 1000.0)
                                appendLine("• वर्तमान मार्ग मूल्यांकन (${selectedRoute.label} → $dest): $km (~$mins मिनट) • ${selectedRoute.crowdActivityTier.label}।")
                                appendLine("• रूट संकेत: ${selectedRoute.crowdPoiCount} सक्रिय स्थान, ${selectedRoute.litCount} रोशनी टैग, सुरक्षा स्थिति: ${selectedRoute.safetyLabel} (कवरेज ${selectedRoute.dataCoveragePercent}%)।")
                            } else {
                                appendLine("• मार्ग स्थिति: $dest के लिए मार्ग चुनने पर स्ट्रीट लाइटिंग व भीड़ संकेत दिखेंगे। तब तक मुख्य और रोशनी वाली सड़कों को प्राथमिकता दें।")
                            }
                            if (nearestPolice != null) {
                                appendLine("• निकटतम पुलिस: ${nearestPolice.name} ($policeDistKm) • आपातकालीन नंबर: 112 / 1091।")
                            }
                            if (isNight) {
                                appendLine("• सलाह: अभी रात्रि समय है — यात्रा के दौरान लाइव ट्रैकिंग साझा करें। यह केवल सहायक सलाह है, 100% सुरक्षा की गारंटी नहीं।")
                            }
                        }
                    }
                }.trim()
            }

            return buildString {
                appendLine("Live Context: $locality • $weatherLineEn")
                when {
                    isFollowedQuery -> {
                        appendLine("1. Move into an active public venue right away — enter a busy shop, pharmacy, metro/bus booth, or fuel station. Avoid heading into quiet side lanes or straight to an isolated residence.")
                        if (nearestPolice != null) {
                            appendLine("2. Nearest Overpass Police Desk: ${nearestPolice.name} (~$policeDistKm, ${nearestPolice.locality.ifBlank { "nearby" }} • ${nearestPolice.phone?.takeIf { it.isNotBlank() } ?: "Call 112"}). Head toward it if safely reachable.")
                        } else {
                            appendLine("2. Emergency Dispatch: Call 112 (ERSS) or 1091 (Women Helpline) immediately and trigger NIRAPAD Deliberate SOS.")
                        }
                        appendLine("3. Stay on a live call with a trusted contact, describe visible landmarks aloud, and keep WhatsApp live location active.")
                    }
                    isPoliceQuery -> {
                        if (stations.isNotEmpty()) {
                            appendLine("Nearest OpenStreetMap Police Stations:")
                            stations.take(3).forEach { st ->
                                val km = String.format(Locale.US, "%.1f km", st.distanceMeters / 1000.0)
                                val ph = st.phone?.takeIf { it.isNotBlank() } ?: "Dial 112"
                                appendLine("• ${st.name} — $km (${st.locality.ifBlank { "Near current area" }}) • Phone: $ph")
                            }
                        } else {
                            appendLine("• No nearby police station pin returned in the immediate cache — dial 112 for closest patrol dispatch.")
                        }
                        if (selectedRoute != null && selectedRoute.crowdPoiCount > 0) {
                            appendLine("• Active Public Safe Spots along ${selectedRoute.label}: ${selectedRoute.crowdPoiCount} transit stops, cafes, shops & lit nodes (${selectedRoute.litCount} street-lit tags).")
                        }
                        appendLine("• Verified Helplines: 112 (ERSS), 1091 (Women Police), 181 (Women Support), 108 (Ambulance).")
                    }
                    isNightQuery -> {
                        appendLine("• Current Window: ${if (isNight) "After-dark conditions — extra vigilance recommended." else "Daylight right now; apply these tips for evening or late travel."}")
                        if (selectedRoute != null) {
                            appendLine("• Selected Corridor (${selectedRoute.label}): ${selectedRoute.crowdActivityTier.label} • ${selectedRoute.litCount} street-lit tags & ${selectedRoute.crowdPoiCount} active POIs (${selectedRoute.dataCoveragePercent}% telemetry coverage).")
                        }
                        appendLine("• Prefer main commercial & transit arteries over unlit shortcuts even if slightly shorter.")
                        appendLine("• Turn on Live Share / Share Live on WhatsApp before boarding, confirm vehicle plate details with a guardian, and wait near lit open storefronts.")
                        appendLine("• Keep 112 & NIRAPAD SOS accessible (advisory guidance only; cannot replace 112).")
                    }
                    else -> {
                        if (selectedRoute != null) {
                            val mins = (selectedRoute.durationSeconds / 60).coerceAtLeast(1)
                            val km = String.format(Locale.US, "%.1f km", selectedRoute.distanceMeters / 1000.0)
                            appendLine("Route Assessment (${selectedRoute.label} → $dest • $km, ~$mins min):")
                            appendLine("• Crowd & Lighting: ${selectedRoute.crowdActivityTier.label} (${selectedRoute.crowdPoiCount} active POIs, ${selectedRoute.litCount} lit tags).")
                            appendLine("• Classification: ${selectedRoute.safetyLabel} (Data coverage ${selectedRoute.dataCoveragePercent}%).")
                            selectedRoute.factors.take(2).forEach { factor ->
                                appendLine("• ${factor.title}: ${factor.statusLabel} [${factor.provenance.label}]")
                            }
                        } else {
                            appendLine("• No corridor selected yet toward $dest — tap 'Compare Routes' to evaluate street lighting and crowd activity across OSRM alternatives.")
                        }
                        if (!activeJourneyState.isNullOrBlank()) {
                            appendLine("• Active Journey Status: $activeJourneyState (deviation monitoring & WhatsApp sharing ready).")
                        }
                        if (nearestPolice != null) {
                            appendLine("• Closest Police Station: ${nearestPolice.name} (~$policeDistKm • ${nearestPolice.phone?.takeIf { it.isNotBlank() } ?: "112"}).")
                        }
                        appendLine("• Advisory Note: No route is 100% guaranteed safe; keep 112 accessible and trust your instincts.")
                    }
                }
            }.trim()
        }

        fun parseComparisonJson(
            rawJson: String,
            destinationLabel: String,
            travelWindowLabel: String,
            candidateRoutes: List<RouteOption>
        ): AdvisoryNavigationComparison {
            val json = JSONObject(rawJson)
            val routesById = candidateRoutes.associateBy { it.id }
            val parsedArray = json.optJSONArray("routes")
            val parsedItems = mutableListOf<AdvisoryRouteComparisonItem>()

            if (parsedArray != null) {
                for (i in 0 until parsedArray.length()) {
                    val obj = parsedArray.getJSONObject(i)
                    val id = obj.optString("routeId").trim()
                    val candidate = routesById[id]
                    val fallbackLighting = candidate?.factors?.firstOrNull { it.id == "lighting" }
                    val fallbackTraffic = candidate?.factors?.firstOrNull { it.id == "footfall" }

                    parsedItems.add(
                        AdvisoryRouteComparisonItem(
                            routeId = id.ifBlank { candidateRoutes.getOrNull(i)?.id ?: "route_${i + 1}" },
                            routeLabel = obj.optString("routeLabel").ifBlank { candidate?.label ?: "Route ${i + 1}" },
                            overallSafetyScore = obj.optInt(
                                "overallSafetyScore",
                                candidate?.factors?.mapNotNull { it.scoreOutOf100 }?.average()?.toInt() ?: 60
                            ).coerceIn(0, 100),
                            streetLightingRating = obj.optString("streetLightingRating")
                                .ifBlank { fallbackLighting?.statusLabel ?: "Limited Data" },
                            streetLightingScore = obj.optInt(
                                "streetLightingScore",
                                fallbackLighting?.scoreOutOf100 ?: 0
                            ).coerceIn(0, 100),
                            publicTrafficLevel = obj.optString("publicTrafficLevel")
                                .ifBlank { fallbackTraffic?.statusLabel ?: "Moderate Traffic" },
                            publicTrafficScore = obj.optInt(
                                "publicTrafficScore",
                                fallbackTraffic?.scoreOutOf100 ?: 50
                            ).coerceIn(0, 100),
                            recommendedRank = obj.optInt("recommendedRank", i + 1).coerceAtLeast(1),
                            isSaferOption = obj.optBoolean("isSaferOption", i == 0),
                            aiRationale = obj.optString("aiRationale")
                                .ifBlank { candidate?.summary ?: "Evaluated on lighting, transit traffic, and safety coverage." },
                            precautionTip = obj.optString("precautionTip")
                                .ifBlank { "Share live journey progress with a trusted contact and keep 112 accessible." }
                        )
                    )
                }
            }

            val existingIds = parsedItems.map { it.routeId }.toSet()
            candidateRoutes.forEachIndexed { idx, r ->
                if (r.id !in existingIds) {
                    val lighting = r.factors.firstOrNull { it.id == "lighting" }
                    val traffic = r.factors.firstOrNull { it.id == "footfall" }
                    val avg = r.factors.mapNotNull { it.scoreOutOf100 }.ifEmpty { listOf(50) }.average().toInt()
                    parsedItems.add(
                        AdvisoryRouteComparisonItem(
                            routeId = r.id,
                            routeLabel = r.label,
                            overallSafetyScore = avg.coerceIn(0, 100),
                            streetLightingRating = lighting?.statusLabel ?: "Unavailable",
                            streetLightingScore = lighting?.scoreOutOf100 ?: 0,
                            publicTrafficLevel = traffic?.statusLabel ?: "Estimated",
                            publicTrafficScore = traffic?.scoreOutOf100 ?: 50,
                            recommendedRank = parsedItems.size + idx + 1,
                            isSaferOption = false,
                            aiRationale = r.summary,
                            precautionTip = "Stay on active transit corridors and verify live telemetry."
                        )
                    )
                }
            }

            val sorted = parsedItems.sortedWith(
                compareByDescending<AdvisoryRouteComparisonItem> { it.isSaferOption }
                    .thenBy { it.recommendedRank }
                    .thenByDescending { it.overallSafetyScore }
            )

            val recommendedId = json.optString("recommendedRouteId")
                .takeIf { id -> sorted.any { it.routeId == id } }
                ?: sorted.firstOrNull()?.routeId.orEmpty()

            return AdvisoryNavigationComparison(
                destinationLabel = destinationLabel,
                travelWindowLabel = travelWindowLabel,
                recommendedRouteId = recommendedId,
                headlineSummary = json.optString("headlineSummary")
                    .ifBlank { "Ranked route alternatives for $destinationLabel by safety score, lighting, and public traffic." },
                comparisonOverview = json.optString("comparisonOverview")
                    .ifBlank { "Decision support only — never guarantees safety or replaces local emergency services (112)." },
                routeComparisons = sorted
            )
        }
    }
}
