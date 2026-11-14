package com.example.data.repository

import android.util.Log
import com.example.data.model.AdvisoryNavigationComparison
import com.example.data.model.AdvisoryRouteComparisonItem
import com.example.data.model.RouteOption
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
        languageCode: String
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
                4. NEVER invent or guess emergency phone numbers, police stations, hospitals, or addresses. Only reference verified numbers provided in context (112, 1091, 181, 108).
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
        languageCode: String
    ): Flow<AiAdvisorUiState> = flow {
        emit(AiAdvisorUiState.Loading)
        val contextBlock = buildString {
            appendLine("Preferred response language: ${if (languageCode == "hi") "Hindi (clear simple Hindi)" else "English"}.")
            if (selectedRoute != null) {
                appendLine("Selected route: ${selectedRoute.label} (${selectedRoute.distanceMeters}m, ~${selectedRoute.durationSeconds / 60} min)")
                appendLine("Safety classification: ${selectedRoute.safetyLabel} (Data coverage: ${selectedRoute.dataCoveragePercent}%)")
                appendLine("Provider: ${selectedRoute.providerName}")
                appendLine("Factors:")
                selectedRoute.factors.forEach { f ->
                    appendLine("- ${f.title}: ${f.statusLabel} [${f.provenance.label}] — ${f.explanation}")
                }
            }
            if (!activeJourneyState.isNullOrBlank()) {
                appendLine("Current journey state: $activeJourneyState")
            }
            appendLine("Verified national emergency numbers in directory: 112 (ERSS), 1091 (Women Helpline), 181 (Women Distress), 108 (Ambulance).")
        }

        val fullPrompt = "$contextBlock\nUser question: $question"
        val accumulated = StringBuilder()
        try {
            withTimeout(60_000L) {
                model.generateContentStream(fullPrompt).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        emit(AiAdvisorUiState.Streaming(accumulated.toString()))
                    }
                }
            }
            if (accumulated.isNotBlank()) {
                emit(AiAdvisorUiState.Success(accumulated.toString()))
            } else {
                emit(AiAdvisorUiState.Error("Advisory assistant returned an empty response. Please try again."))
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI advisor timed out", e)
            emit(AiAdvisorUiState.Error("Request timed out after 60 seconds. Please retry."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI advisor failed", e)
            emit(AiAdvisorUiState.Error(e.localizedMessage ?: "Unable to reach AI advisor right now."))
        }
    }

    companion object {
        private const val TAG = "AiAdvisorRepository"

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
