package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class JourneyState(val value: String) {
    PLANNED("PLANNED"),
    ACTIVE("ACTIVE"),
    DEVIATION_DETECTED("DEVIATION_DETECTED"),
    SOS_TRIGGERED("SOS_TRIGGERED"),
    COMPLETED("COMPLETED"),
    CANCELLED("CANCELLED");

    val isTerminal: Boolean
        get() = this == COMPLETED || this == CANCELLED

    companion object {
        fun fromValue(raw: String): JourneyState =
            entries.firstOrNull { it.value == raw } ?: PLANNED
    }
}

object JourneyStateMachine {
    private val allowedTransitions: Map<JourneyState, Set<JourneyState>> = mapOf(
        JourneyState.PLANNED to setOf(JourneyState.ACTIVE, JourneyState.CANCELLED),
        JourneyState.ACTIVE to setOf(
            JourneyState.DEVIATION_DETECTED,
            JourneyState.SOS_TRIGGERED,
            JourneyState.COMPLETED,
            JourneyState.CANCELLED
        ),
        JourneyState.DEVIATION_DETECTED to setOf(
            JourneyState.ACTIVE,
            JourneyState.SOS_TRIGGERED,
            JourneyState.COMPLETED,
            JourneyState.CANCELLED
        ),
        JourneyState.SOS_TRIGGERED to setOf(
            JourneyState.ACTIVE,
            JourneyState.COMPLETED,
            JourneyState.CANCELLED
        ),
        JourneyState.COMPLETED to emptySet(),
        JourneyState.CANCELLED to emptySet()
    )

    fun canTransition(from: JourneyState, to: JourneyState): Boolean =
        allowedTransitions[from]?.contains(to) == true
}

enum class DataProvenance(val label: String) {
    VERIFIED("Verified"),
    USER_REPORTED("User-Reported"),
    ESTIMATED("Estimated"),
    UNAVAILABLE("Unavailable")
}

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val language: String = "en",
    val deviationThresholdMeters: Long = 250L,
    val retentionDays: Long = 30L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "displayName" to displayName.ifBlank { "Traveller" }.take(100),
        "email" to email.take(160),
        "language" to if (language == "hi") "hi" else "en",
        "deviationThresholdMeters" to deviationThresholdMeters.coerceIn(100L, 2000L),
        "retentionDays" to retentionDays.coerceIn(1L, 90L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class EmergencyContact(
    @DocumentId val id: String = "",
    val ownerUserId: String = "",
    val name: String = "",
    val phoneNumber: String = "",
    val email: String = "",
    val contactChannel: String = "",
    val channelType: String = "PHONE",
    val relationship: String = "",
    val verified: Boolean = false,
    val notifyOnStart: Boolean = true,
    val notifyOnSos: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val resolvedPhone: String
        get() = phoneNumber.ifBlank {
            if (channelType.equals("PHONE", ignoreCase = true) || channelType.equals("SMS", ignoreCase = true)) {
                contactChannel
            } else {
                ""
            }
        }

    val resolvedEmail: String
        get() = email.ifBlank {
            if (channelType.equals("EMAIL", ignoreCase = true)) contactChannel else ""
        }

    fun toCreateMap(uid: String): Map<String, Any> {
        val cleanPhone = resolvedPhone.trim().take(32)
        val cleanEmail = resolvedEmail.trim().take(160)
        val primaryChannel = cleanPhone.ifBlank { cleanEmail }.ifBlank { contactChannel.trim() }.take(160)
        val primaryType = when {
            channelType.uppercase() == "SMS" && cleanPhone.isNotBlank() -> "SMS"
            cleanPhone.isNotBlank() -> "PHONE"
            cleanEmail.isNotBlank() -> "EMAIL"
            else -> channelType.uppercase().takeIf { it in setOf("PHONE", "EMAIL", "SMS") } ?: "PHONE"
        }
        return mapOf(
            "ownerUserId" to uid,
            "name" to name.trim().take(80),
            "phoneNumber" to cleanPhone,
            "email" to cleanEmail,
            "contactChannel" to primaryChannel,
            "channelType" to primaryType,
            "relationship" to relationship.trim().take(50),
            "verified" to verified,
            "notifyOnStart" to notifyOnStart,
            "notifyOnSos" to notifyOnSos,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    fun toUpdateMap(): Map<String, Any> {
        val cleanPhone = resolvedPhone.trim().take(32)
        val cleanEmail = resolvedEmail.trim().take(160)
        val primaryChannel = cleanPhone.ifBlank { cleanEmail }.ifBlank { contactChannel.trim() }.take(160)
        val primaryType = when {
            channelType.uppercase() == "SMS" && cleanPhone.isNotBlank() -> "SMS"
            cleanPhone.isNotBlank() -> "PHONE"
            cleanEmail.isNotBlank() -> "EMAIL"
            else -> channelType.uppercase().takeIf { it in setOf("PHONE", "EMAIL", "SMS") } ?: "PHONE"
        }
        return mapOf(
            "name" to name.trim().take(80),
            "phoneNumber" to cleanPhone,
            "email" to cleanEmail,
            "contactChannel" to primaryChannel,
            "channelType" to primaryType,
            "relationship" to relationship.trim().take(50),
            "verified" to verified,
            "notifyOnStart" to notifyOnStart,
            "notifyOnSos" to notifyOnSos,
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }
}

@IgnoreExtraProperties
data class Journey(
    @DocumentId val id: String = "",
    val userId: String = "",
    val ownerDisplayName: String = "",
    val originLabel: String = "",
    val originLat: Double = 0.0,
    val originLon: Double = 0.0,
    val destinationLabel: String = "",
    val destinationLat: Double = 0.0,
    val destinationLon: Double = 0.0,
    val selectedRouteId: String = "",
    val selectedRouteLabel: String = "",
    val providerName: String = "",
    val state: String = JourneyState.PLANNED.value,
    val deviationThresholdMeters: Long = 250L,
    val sharingEnabled: Boolean = false,
    val sharedContactCount: Long = 0L,
    val shareTokenId: String = "",
    val lastLat: Double = 0.0,
    val lastLon: Double = 0.0,
    val lastAccuracyMeters: Double = 0.0,
    val lastLocationAt: Timestamp? = null,
    val startedAt: Timestamp? = null,
    val endedAt: Timestamp? = null,
    val totalDistanceKm: Double = 0.0,
    val durationMinutes: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class LocationPoint(
    @DocumentId val id: String = "",
    val userId: String = "",
    val journeyId: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val accuracyMeters: Double = 0.0,
    val sequence: Long = 0L,
    @ServerTimestamp val recordedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ShareToken(
    @DocumentId val id: String = "",
    val tokenHash: String = "",
    val journeyId: String = "",
    val ownerUserId: String = "",
    val ownerDisplayName: String = "",
    val destinationLabel: String = "",
    val journeyState: String = JourneyState.ACTIVE.value,
    val lastLat: Double = 0.0,
    val lastLon: Double = 0.0,
    val lastLocationText: String = "",
    val revoked: Boolean = false,
    val expiresAtMillis: Long = 0L,
    val lastUpdatedAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SosSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val journeyId: String = "",
    val shareTokenId: String = "",
    val dedupeKey: String = "",
    val status: String = "ACTIVE",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val locationLabel: String = "",
    val deliverySummary: String = "",
    val recipientCount: Long = 0L,
    val acknowledgedCount: Long = 0L,
    val lastLocationAt: Timestamp? = null,
    val activatedAt: Timestamp? = null,
    val resolvedAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SafetyReport(
    @DocumentId val id: String = "",
    val reporterId: String = "",
    val category: String = "POOR_LIGHTING",
    val title: String = "",
    val description: String = "",
    val locationLabel: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val status: String = SafetyReportStatus.PENDING_REVIEW.value,
    val moderationNote: String = "",
    val moderatedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

enum class SafetyReportStatus(val value: String, val label: String) {
    PENDING_REVIEW("PENDING_REVIEW", "Pending Review"),
    FLAGGED("FLAGGED", "Flagged"),
    VERIFIED("VERIFIED", "Verified"),
    RESOLVED("RESOLVED", "Resolved"),
    DISMISSED("DISMISSED", "Dismissed");

    companion object {
        val validModerationValues: Set<String> = entries.map { it.value }.toSet()

        fun fromValue(raw: String): SafetyReportStatus =
            entries.firstOrNull { it.value.equals(raw, ignoreCase = true) } ?: PENDING_REVIEW
    }
}

@IgnoreExtraProperties
data class EmergencyFacility(
    @DocumentId val id: String = "",
    val type: String = "HELPLINE",
    val name: String = "",
    val phone: String = "",
    val address: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val verifiedSource: String = "",
    val verifiedAtLabel: String = "",
    val activeStatus: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

enum class EmergencyDirectoryCategory(
    val label: String,
    val firestoreTypes: Set<String>
) {
    ALL("All Services", setOf("POLICE", "AMBULANCE", "WOMENS_SUPPORT", "HOSPITAL", "HELPLINE", "SAFE_HUB")),
    POLICE("Police", setOf("POLICE")),
    AMBULANCE("Ambulance", setOf("AMBULANCE", "HOSPITAL")),
    WOMENS_SUPPORT("Women's Support", setOf("WOMENS_SUPPORT", "SAFE_HUB", "HELPLINE"));

    companion object {
        fun fromFacilityType(rawType: String): EmergencyDirectoryCategory = when (rawType.uppercase()) {
            "POLICE" -> POLICE
            "AMBULANCE", "HOSPITAL" -> AMBULANCE
            "WOMENS_SUPPORT", "SAFE_HUB", "HELPLINE" -> WOMENS_SUPPORT
            else -> ALL
        }
    }
}

data class DirectoryFacilityItem(
    val facility: EmergencyFacility,
    val distanceMeters: Double,
    val category: EmergencyDirectoryCategory
)

@IgnoreExtraProperties
data class AuditLog(
    @DocumentId val id: String = "",
    val actorId: String = "",
    val action: String = "",
    val resourceType: String = "",
    val resourceId: String = "",
    val outcome: String = "SUCCESS",
    val summary: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class LatLon(val lat: Double, val lon: Double)

data class PlaceSuggestion(
    val placeId: String,
    val shortName: String,
    val displayName: String,
    val locality: String,
    val lat: Double,
    val lon: Double,
    val isFromPreset: Boolean = false
)

enum class CrowdActivityTier(val label: String) {
    HIGH_CROWD_WELL_LIT("High Crowd & Well-Lit (Safest)"),
    MODERATE_ACTIVITY("Moderate Activity"),
    LOW_CROWD_ISOLATED("Low Crowd / Isolated Stretch")
}

enum class SegmentSafetyLevel {
    HIGH_CROWD_LIT,
    MODERATE_ACTIVITY,
    LOW_CROWD_ISOLATED
}

data class ColoredRouteSegment(
    val points: List<LatLon>,
    val safetyLevel: SegmentSafetyLevel
)

data class NavigationStep(
    val instruction: String,
    val roadName: String,
    val maneuverType: String,
    val maneuverModifier: String,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val location: LatLon
)

data class NearbyPoliceStation(
    val id: String,
    val name: String,
    val phone: String?,
    val locality: String,
    val lat: Double,
    val lon: Double,
    val distanceMeters: Double
)

data class WeatherContext(
    val temperatureCelsius: Double,
    val precipitationMm: Double,
    val isDay: Boolean,
    val conditionLabel: String,
    val fetchedAtMillis: Long
)

data class RouteProgress(
    val remainingPolyline: List<LatLon>,
    val remainingDistanceMeters: Int,
    val remainingDurationSeconds: Int,
    val distanceToDestinationMeters: Double,
    val activeStep: NavigationStep?,
    val distanceToNextStepMeters: Int,
    val hasArrived: Boolean,
    val totalDistanceMeters: Int = remainingDistanceMeters,
    val coveredDistanceMeters: Int = 0,
    val progressFraction: Float = if (hasArrived) 1f else 0f,
    val speedKmh: Double? = null
)

data class SafetyFactor(
    val id: String,
    val title: String,
    val statusLabel: String,
    val scoreOutOf100: Int?, // null => Unavailable
    val explanation: String,
    val provenance: DataProvenance,
    val freshnessLabel: String
)

enum class SafetyTier {
    HIGHER_CONTEXT,
    MODERATE_CONTEXT,
    LIMITED_DATA
}

data class RouteOption(
    val id: String,
    val label: String,
    val summary: String,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val providerName: String,
    val providerIsFallback: Boolean,
    val safetyTier: SafetyTier,
    val safetyLabel: String,
    val dataCoveragePercent: Int,
    val freshnessNote: String,
    val caveat: String,
    val factors: List<SafetyFactor>,
    val geometry: List<LatLon>,
    val steps: List<NavigationStep> = emptyList(),
    val coloredSegments: List<ColoredRouteSegment> = emptyList(),
    val crowdActivityTier: CrowdActivityTier = CrowdActivityTier.MODERATE_ACTIVITY,
    val crowdPoiCount: Int = 0,
    val litCount: Int = 0,
    val nearbyPoliceCount: Int = 0,
    val isNightTime: Boolean = false
)

data class RouteSearchResult(
    val originLabel: String,
    val origin: LatLon,
    val destinationLabel: String,
    val destination: LatLon,
    val routes: List<RouteOption>,
    val providerBanner: String,
    val hasProviderWarning: Boolean,
    val weatherContext: WeatherContext? = null
)

data class AdvisoryRouteComparisonItem(
    val routeId: String,
    val routeLabel: String,
    val overallSafetyScore: Int,
    val streetLightingRating: String,
    val streetLightingScore: Int,
    val publicTrafficLevel: String,
    val publicTrafficScore: Int,
    val recommendedRank: Int,
    val isSaferOption: Boolean,
    val aiRationale: String,
    val precautionTip: String
)

data class AdvisoryNavigationComparison(
    val destinationLabel: String,
    val travelWindowLabel: String,
    val recommendedRouteId: String,
    val headlineSummary: String,
    val comparisonOverview: String,
    val routeComparisons: List<AdvisoryRouteComparisonItem>
)

