package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firestore.OperationType
import com.example.data.firestore.handleFirestoreError
import com.example.data.model.AuditLog
import com.example.data.model.DirectoryFacilityItem
import com.example.data.model.EmergencyContact
import com.example.data.model.EmergencyDirectoryCategory
import com.example.data.model.EmergencyFacility
import com.example.data.model.Journey
import com.example.data.model.JourneyState
import com.example.data.model.JourneyStateMachine
import com.example.data.model.LocationPoint
import com.example.data.model.SafetyReport
import com.example.data.model.ShareToken
import com.example.data.model.SosSession
import com.example.data.model.UserProfile
import com.example.data.service.RoutingAndSafetyEngine
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

class NirapadRepository(
    private val db: FirebaseFirestore,
    private val authProvider: () -> String? = { Firebase.auth.currentUser?.uid },
    private val emailProvider: () -> Pair<String?, Boolean> = {
        val u = Firebase.auth.currentUser
        u?.email to (u?.isEmailVerified == true)
    }
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        authProvider() ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    // ---------------- User Profile ----------------

    suspend fun ensureUserProfile(uid: String, displayName: String, email: String): UserProfile {
        val docRef = db.collection("users").document(uid)
        return try {
            val snap = docRef.get().await()
            val cleanInputName = displayName.trim().take(100)
            val cleanInputEmail = email.trim().take(160)
            if (snap.exists()) {
                val existing = snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: UserProfile(id = uid, userId = uid, displayName = cleanInputName, email = cleanInputEmail)
                val resolvedName = cleanInputName
                    .takeIf { it.isNotBlank() && it != "Traveller" }
                    ?: existing.displayName.ifBlank { cleanInputName }.ifBlank { "Traveller" }
                val resolvedEmail = cleanInputEmail.ifBlank { existing.email }
                if (resolvedName != existing.displayName || resolvedEmail != existing.email) {
                    docRef.set(
                        mapOf(
                            "displayName" to resolvedName.take(100),
                            "email" to resolvedEmail.take(160),
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    ).await()
                }
                existing.copy(id = uid, displayName = resolvedName, email = resolvedEmail)
            } else {
                val fallbackName = cleanInputName.ifBlank {
                    cleanInputEmail.substringBefore("@").ifBlank { "Traveller" }
                }
                val profile = UserProfile(
                    userId = uid,
                    displayName = fallbackName.take(100),
                    email = cleanInputEmail,
                    language = "en",
                    deviationThresholdMeters = 250L,
                    retentionDays = 30L
                )
                docRef.set(profile.toCreateMap(uid), SetOptions.merge()).await()
                profile.copy(id = uid)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    fun observeUserProfile(uid: String): Flow<UserProfile?> = flow {
        val docRef = db.collection("users").document(uid)
        emitAll(
            docRef.snapshots()
                .map { it.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fe = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fe == null && error is CancellationException) throw error
                    if (authProvider() == uid) handleFirestoreError(fe ?: error, OperationType.GET, docRef.path)
                    throw error
                }
        )
    }

    suspend fun updateUserSettings(
        uid: String,
        displayName: String,
        language: String,
        deviationThresholdMeters: Long,
        retentionDays: Long
    ) {
        val docRef = db.collection("users").document(uid)
        try {
            docRef.update(
                mapOf(
                    "displayName" to displayName.trim().ifBlank { "Traveller" }.take(100),
                    "language" to if (language == "hi") "hi" else "en",
                    "deviationThresholdMeters" to deviationThresholdMeters.coerceIn(100L, 2000L),
                    "retentionDays" to retentionDays.coerceIn(1L, 90L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    // ---------------- Trusted Contacts ----------------

    fun observeContacts(uid: String): Flow<List<EmergencyContact>> = flow {
        val path = "users/$uid/contacts"
        emitAll(
            db.collection("users").document(uid).collection("contacts")
                .snapshots()
                .map { snap ->
                    snap.toObjects(EmergencyContact::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.name.lowercase() }
                }
                .catch { error ->
                    val fe = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fe == null && error is CancellationException) throw error
                    if (authProvider() == uid) handleFirestoreError(fe ?: error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun upsertContact(contact: EmergencyContact): String {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("contacts")
        val docRef = if (contact.id.isNotBlank()) colRef.document(contact.id) else colRef.document()
        return try {
            if (contact.id.isNotBlank()) {
                docRef.update(contact.toUpdateMap()).await()
            } else {
                docRef.set(contact.toCreateMap(uid)).await()
            }
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun deleteContact(contactId: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("contacts").document(contactId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // ---------------- Journeys & Live Sharing ----------------

    fun observeJourneys(uid: String): Flow<List<Journey>> = flow {
        val path = "journeys"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Journey::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val fe = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fe == null && error is CancellationException) throw error
                    if (authProvider() == uid) handleFirestoreError(fe ?: error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun startProtectedJourney(
        ownerDisplayName: String,
        originLabel: String,
        originLat: Double,
        originLon: Double,
        destinationLabel: String,
        destinationLat: Double,
        destinationLon: Double,
        selectedRouteId: String,
        selectedRouteLabel: String,
        providerName: String,
        deviationThresholdMeters: Long,
        sharingEnabled: Boolean,
        sharedContactCount: Long,
        shareDurationMinutes: Long
    ): Pair<String, String> {
        val uid = requireUserId()
        val journeyRef = db.collection("journeys").document()
        val tokenId = if (sharingEnabled) generateHighEntropyToken() else ""

        try {
            val journeyPayload = mapOf(
                "userId" to uid,
                "ownerDisplayName" to ownerDisplayName.ifBlank { "Traveller" }.take(100),
                "originLabel" to originLabel.take(160),
                "originLat" to originLat.coerceIn(-90.0, 90.0),
                "originLon" to originLon.coerceIn(-180.0, 180.0),
                "destinationLabel" to destinationLabel.take(160),
                "destinationLat" to destinationLat.coerceIn(-90.0, 90.0),
                "destinationLon" to destinationLon.coerceIn(-180.0, 180.0),
                "selectedRouteId" to selectedRouteId.take(64),
                "selectedRouteLabel" to selectedRouteLabel.take(120),
                "providerName" to providerName.take(64),
                "state" to JourneyState.ACTIVE.value,
                "deviationThresholdMeters" to deviationThresholdMeters.coerceIn(100L, 2000L),
                "sharingEnabled" to sharingEnabled,
                "sharedContactCount" to sharedContactCount.coerceIn(0L, 20L),
                "shareTokenId" to tokenId,
                "lastLat" to originLat.coerceIn(-90.0, 90.0),
                "lastLon" to originLon.coerceIn(-180.0, 180.0),
                "lastAccuracyMeters" to 15.0,
                "lastLocationAt" to FieldValue.serverTimestamp(),
                "startedAt" to FieldValue.serverTimestamp(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            journeyRef.set(journeyPayload).await()

            if (sharingEnabled && tokenId.isNotEmpty()) {
                val expiresAtMillis = System.currentTimeMillis() + (shareDurationMinutes.coerceIn(15L, 720L) * 60_000L)
                val sharePayload = mapOf(
                    "tokenHash" to sha256Hex(tokenId),
                    "journeyId" to journeyRef.id,
                    "ownerUserId" to uid,
                    "ownerDisplayName" to ownerDisplayName.ifBlank { "Traveller" }.take(100),
                    "destinationLabel" to destinationLabel.take(160),
                    "journeyState" to JourneyState.ACTIVE.value,
                    "lastLat" to originLat.coerceIn(-90.0, 90.0),
                    "lastLon" to originLon.coerceIn(-180.0, 180.0),
                    "lastLocationText" to "Departed from $originLabel".take(160),
                    "revoked" to false,
                    "expiresAtMillis" to expiresAtMillis,
                    "lastUpdatedAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                db.collection("share_tokens").document(tokenId).set(sharePayload).await()
            }

            writeAuditLogInternal(
                actorId = uid,
                action = "JOURNEY_STARTED",
                resourceType = "journey",
                resourceId = journeyRef.id,
                outcome = "SUCCESS",
                summary = "Protected journey started (sharing=${if (sharingEnabled) "ON" else "OFF"})."
            )
            return journeyRef.id to tokenId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, journeyRef.path)
            throw e
        }
    }

    suspend fun recordLocationUpdate(
        journey: Journey,
        lat: Double,
        lon: Double,
        accuracyMeters: Double,
        sequence: Long,
        nextState: JourneyState? = null,
        locationLabel: String = ""
    ) {
        val uid = requireUserId()
        val currentState = JourneyState.fromValue(journey.state)
        if (currentState.isTerminal) return

        val resolvedState = if (nextState != null && JourneyStateMachine.canTransition(currentState, nextState)) {
            nextState
        } else {
            currentState
        }

        val journeyRef = db.collection("journeys").document(journey.id)
        val pointRef = journeyRef.collection("locations").document()
        try {
            journeyRef.update(
                mapOf(
                    "state" to resolvedState.value,
                    "lastLat" to lat.coerceIn(-90.0, 90.0),
                    "lastLon" to lon.coerceIn(-180.0, 180.0),
                    "lastAccuracyMeters" to accuracyMeters.coerceIn(0.0, 5000.0),
                    "lastLocationAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            pointRef.set(
                mapOf(
                    "userId" to uid,
                    "journeyId" to journey.id,
                    "lat" to lat.coerceIn(-90.0, 90.0),
                    "lon" to lon.coerceIn(-180.0, 180.0),
                    "accuracyMeters" to accuracyMeters.coerceIn(0.0, 5000.0),
                    "sequence" to sequence.coerceAtLeast(0L),
                    "recordedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            if (journey.sharingEnabled && journey.shareTokenId.isNotBlank()) {
                val shareText = if (locationLabel.isNotBlank()) {
                    "$locationLabel • To ${journey.destinationLabel}"
                } else {
                    "Heading toward ${journey.destinationLabel}"
                }.take(160)
                db.collection("share_tokens").document(journey.shareTokenId).update(
                    mapOf(
                        "journeyState" to resolvedState.value,
                        "lastLat" to lat.coerceIn(-90.0, 90.0),
                        "lastLon" to lon.coerceIn(-180.0, 180.0),
                        "lastLocationText" to shareText,
                        "lastUpdatedAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, journeyRef.path)
            throw e
        }
    }

    suspend fun transitionJourneyState(journey: Journey, targetState: JourneyState) {
        val uid = requireUserId()
        val currentState = JourneyState.fromValue(journey.state)
        require(JourneyStateMachine.canTransition(currentState, targetState)) {
            "Invalid journey transition from ${currentState.value} to ${targetState.value}"
        }
        val docRef = db.collection("journeys").document(journey.id)
        try {
            val updates = mutableMapOf<String, Any>(
                "state" to targetState.value,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (targetState.isTerminal) {
                updates["endedAt"] = FieldValue.serverTimestamp()
            }
            docRef.update(updates).await()

            if (journey.shareTokenId.isNotBlank()) {
                val shareUpdates = mutableMapOf<String, Any>(
                    "journeyState" to targetState.value,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (targetState.isTerminal) {
                    shareUpdates["revoked"] = true
                }
                db.collection("share_tokens").document(journey.shareTokenId).update(shareUpdates).await()
            }

            writeAuditLogInternal(
                actorId = uid,
                action = "JOURNEY_STATE_${targetState.value}",
                resourceType = "journey",
                resourceId = journey.id,
                outcome = "SUCCESS",
                summary = "Journey state changed ${currentState.value} -> ${targetState.value}."
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun revokeShareToken(journey: Journey) {
        val uid = requireUserId()
        if (journey.shareTokenId.isBlank()) return
        val tokenRef = db.collection("share_tokens").document(journey.shareTokenId)
        val journeyRef = db.collection("journeys").document(journey.id)
        try {
            tokenRef.update(
                mapOf(
                    "revoked" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            if (!JourneyState.fromValue(journey.state).isTerminal) {
                journeyRef.update(
                    mapOf(
                        "sharingEnabled" to false,
                        "sharedContactCount" to 0L,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            writeAuditLogInternal(
                actorId = uid,
                action = "SHARE_REVOKED",
                resourceType = "share_token",
                resourceId = journey.shareTokenId,
                outcome = "SUCCESS",
                summary = "Live share token revoked immediately by owner."
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, tokenRef.path)
            throw e
        }
    }

    suspend fun fetchShareTokenById(tokenId: String): ShareToken? {
        requireUserId()
        val cleanId = tokenId.trim()
        require(cleanId.length >= 16) { "Invalid share token format." }
        val docRef = db.collection("share_tokens").document(cleanId)
        return try {
            val snap = docRef.get().await()
            if (!snap.exists()) null
            else snap.toObject(ShareToken::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    fun observeShareTokenById(tokenId: String): Flow<ShareToken?> = flow {
        requireUserId()
        val cleanId = tokenId.trim()
        require(cleanId.length >= 16) { "Invalid share token format." }
        val docRef = db.collection("share_tokens").document(cleanId)
        emitAll(
            docRef.snapshots()
                .map { snap ->
                    if (!snap.exists()) null
                    else snap.toObject(ShareToken::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val fe = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fe == null && error is CancellationException) throw error
                    if (authProvider() != null) handleFirestoreError(fe ?: error, OperationType.GET, docRef.path)
                    throw error
                }
        )
    }

    // ---------------- SOS Workflow with Deduplication & Live Sharing ----------------

    fun observeSosSessions(uid: String): Flow<List<SosSession>> = flow {
        val path = "sos_sessions"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(SosSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.activatedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val fe = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fe == null && error is CancellationException) throw error
                    if (authProvider() == uid) handleFirestoreError(fe ?: error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun triggerSosWithDedupe(
        journeyId: String,
        lat: Double,
        lon: Double,
        locationLabel: String,
        contacts: List<EmergencyContact>,
        ownerDisplayName: String = "Traveller",
        existingShareTokenId: String = "",
        alertReason: String = ""
    ): SosSession {
        val uid = requireUserId()
        // Deduplicate within 60-second window so double taps do not spawn duplicate SOS sessions
        val bucket = System.currentTimeMillis() / 60_000L
        val dedupeKey = "${uid}_${journeyId.ifBlank { "standalone" }}_$bucket"

        val existing = db.collection("sos_sessions")
            .whereEqualTo("userId", uid)
            .whereEqualTo("dedupeKey", dedupeKey)
            .get()
            .await()
            .toObjects(SosSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .firstOrNull { it.status == "ACTIVE" }

        if (existing != null) {
            return existing
        }

        val sosRef = db.collection("sos_sessions").document()
        val sosShareTokenId = existingShareTokenId.ifBlank { generateHighEntropyToken() }
        val safeLat = lat.coerceIn(-90.0, 90.0)
        val safeLon = lon.coerceIn(-180.0, 180.0)
        val safeLabel = locationLabel.ifBlank { "Current GPS coordinates" }.take(160)
        val cleanReason = alertReason.trim().take(80)

        val sosContacts = contacts.filter { it.notifyOnSos }
        val reasonPrefix = if (cleanReason.isNotEmpty()) "[$cleanReason] " else ""
        val deliverySummary = if (sosContacts.isEmpty()) {
            "${reasonPrefix}High-priority share token $sosShareTokenId active. Call 112 / 1091 directly using verified directory."
        } else {
            val names = sosContacts.joinToString(", ") { contact ->
                val channels = listOfNotNull(
                    contact.resolvedPhone.takeIf { it.isNotBlank() }?.let { "Phone" },
                    contact.resolvedEmail.takeIf { it.isNotBlank() }?.let { "Email" }
                ).ifEmpty { listOf(contact.channelType) }.joinToString("+")
                "${contact.name} ($channels: live link)"
            }
            "${reasonPrefix}Live SOS location shared to ${sosContacts.size} contact(s): $names • Token: $sosShareTokenId"
        }

        return try {
            val payload = mapOf(
                "userId" to uid,
                "journeyId" to journeyId.take(128),
                "shareTokenId" to sosShareTokenId,
                "dedupeKey" to dedupeKey,
                "status" to "ACTIVE",
                "lat" to safeLat,
                "lon" to safeLon,
                "locationLabel" to safeLabel,
                "deliverySummary" to deliverySummary.take(400),
                "recipientCount" to sosContacts.size.toLong().coerceIn(0L, 20L),
                "acknowledgedCount" to 0L,
                "lastLocationAt" to FieldValue.serverTimestamp(),
                "activatedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            sosRef.set(payload).await()

            val tokenLocationText = if (cleanReason.isNotEmpty()) {
                "SOS ($cleanReason) • $safeLabel"
            } else {
                "SOS Active • $safeLabel"
            }.take(160)

            val tokenRef = db.collection("share_tokens").document(sosShareTokenId)
            if (existingShareTokenId.isBlank()) {
                val expiresAtMillis = System.currentTimeMillis() + (4L * 3_600_000L)
                tokenRef.set(
                    mapOf(
                        "tokenHash" to sha256Hex(sosShareTokenId),
                        "journeyId" to journeyId.ifBlank { "sos_${sosRef.id}" }.take(128),
                        "ownerUserId" to uid,
                        "ownerDisplayName" to ownerDisplayName.ifBlank { "Traveller" }.take(100),
                        "destinationLabel" to "Emergency SOS Live Location".take(160),
                        "journeyState" to JourneyState.SOS_TRIGGERED.value,
                        "lastLat" to safeLat,
                        "lastLon" to safeLon,
                        "lastLocationText" to tokenLocationText,
                        "revoked" to false,
                        "expiresAtMillis" to expiresAtMillis,
                        "lastUpdatedAt" to FieldValue.serverTimestamp(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                tokenRef.update(
                    mapOf(
                        "journeyState" to JourneyState.SOS_TRIGGERED.value,
                        "lastLat" to safeLat,
                        "lastLon" to safeLon,
                        "lastLocationText" to tokenLocationText,
                        "revoked" to false,
                        "lastUpdatedAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            writeAuditLogInternal(
                actorId = uid,
                action = "SOS_ACTIVATED",
                resourceType = "sos_session",
                resourceId = sosRef.id,
                outcome = "ACTIVE",
                summary = "High-priority SOS triggered (recipients=${sosContacts.size}${if (cleanReason.isNotEmpty()) ", alert=$cleanReason" else ""}, liveToken=${sosShareTokenId.take(10)}...)."
            )
            SosSession(
                id = sosRef.id,
                userId = uid,
                journeyId = journeyId,
                shareTokenId = sosShareTokenId,
                dedupeKey = dedupeKey,
                status = "ACTIVE",
                lat = safeLat,
                lon = safeLon,
                locationLabel = safeLabel,
                deliverySummary = deliverySummary.take(400),
                recipientCount = sosContacts.size.toLong(),
                acknowledgedCount = 0L
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, sosRef.path)
            throw e
        }
    }

    suspend fun recordSosLiveLocation(
        sosId: String,
        shareTokenId: String,
        journeyId: String,
        lat: Double,
        lon: Double,
        accuracyMeters: Double,
        sequence: Long
    ): Boolean {
        val uid = requireUserId()
        if (sosId.isBlank()) return false

        val safeLat = lat.coerceIn(-90.0, 90.0)
        val safeLon = lon.coerceIn(-180.0, 180.0)
        val safeAcc = accuracyMeters.coerceIn(0.0, 5000.0)
        val liveLabel = "Live GPS (${"%.4f".format(java.util.Locale.US, safeLat)}, ${"%.4f".format(java.util.Locale.US, safeLon)}) ±${safeAcc.toInt()}m".take(160)

        val sosRef = db.collection("sos_sessions").document(sosId)
        return try {
            val snap = sosRef.get().await()
            if (!snap.exists() || snap.getString("status") != "ACTIVE") {
                return false
            }

            sosRef.update(
                mapOf(
                    "lat" to safeLat,
                    "lon" to safeLon,
                    "locationLabel" to liveLabel,
                    "lastLocationAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val effectiveTokenId = shareTokenId.ifBlank { snap.getString("shareTokenId").orEmpty() }
            if (effectiveTokenId.isNotBlank()) {
                db.collection("share_tokens").document(effectiveTokenId).update(
                    mapOf(
                        "journeyState" to JourneyState.SOS_TRIGGERED.value,
                        "lastLat" to safeLat,
                        "lastLon" to safeLon,
                        "lastLocationText" to "SOS $liveLabel".take(160),
                        "lastUpdatedAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            if (journeyId.isNotBlank() && !journeyId.startsWith("sos_")) {
                val journeyRef = db.collection("journeys").document(journeyId)
                val journeySnap = journeyRef.get().await()
                val journeyState = journeySnap.getString("state").orEmpty()
                if (journeySnap.exists() && !JourneyState.fromValue(journeyState).isTerminal) {
                    journeyRef.update(
                        mapOf(
                            "state" to JourneyState.SOS_TRIGGERED.value,
                            "lastLat" to safeLat,
                            "lastLon" to safeLon,
                            "lastAccuracyMeters" to safeAcc,
                            "lastLocationAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                    journeyRef.collection("locations").document().set(
                        mapOf(
                            "userId" to uid,
                            "journeyId" to journeyId,
                            "lat" to safeLat,
                            "lon" to safeLon,
                            "accuracyMeters" to safeAcc,
                            "sequence" to sequence.coerceAtLeast(0L),
                            "recordedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, sosRef.path)
            false
        }
    }

    suspend fun updateSosStatus(
        sosId: String,
        newStatus: String,
        summaryNote: String,
        shareTokenId: String = "",
        standaloneSosOnly: Boolean = false
    ) {
        val uid = requireUserId()
        require(newStatus in setOf("CANCELLED", "RESOLVED")) { "Invalid SOS terminal status" }
        val docRef = db.collection("sos_sessions").document(sosId)
        try {
            docRef.update(
                mapOf(
                    "status" to newStatus,
                    "deliverySummary" to summaryNote.take(400),
                    "resolvedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            if (shareTokenId.isNotBlank()) {
                val tokenUpdates = mutableMapOf<String, Any>(
                    "journeyState" to newStatus,
                    "lastLocationText" to "SOS marked $newStatus by user".take(160),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (standaloneSosOnly) {
                    tokenUpdates["revoked"] = true
                }
                db.collection("share_tokens").document(shareTokenId).update(tokenUpdates).await()
            }

            writeAuditLogInternal(
                actorId = uid,
                action = "SOS_$newStatus",
                resourceType = "sos_session",
                resourceId = sosId,
                outcome = newStatus,
                summary = "SOS marked $newStatus."
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    // ---------------- Safety Reports & Moderation ----------------

    fun observeMySafetyReports(uid: String): Flow<List<SafetyReport>> = flow {
        val path = "safety_reports"
        emitAll(
            db.collection(path)
                .whereEqualTo("reporterId", uid)
                .snapshots()
                .map { snap -> snap.toObjects(SafetyReport::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fe = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fe == null && error is CancellationException) throw error
                    if (authProvider() == uid) handleFirestoreError(fe ?: error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeVerifiedSafetyReports(): Flow<List<SafetyReport>> = flow {
        val path = "safety_reports"
        emitAll(
            db.collection(path)
                .whereEqualTo("status", "VERIFIED")
                .snapshots()
                .map { snap -> snap.toObjects(SafetyReport::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fe = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fe == null && error is CancellationException) throw error
                    handleFirestoreError(fe ?: error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeAllReportsForAdmin(): Flow<List<SafetyReport>> = flow {
        val path = "safety_reports"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snap ->
                    snap.toObjects(SafetyReport::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val fe = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fe == null && error is CancellationException) throw error
                    handleFirestoreError(fe ?: error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun fetchSafetyReportsForAdmin(): List<SafetyReport> {
        val path = "safety_reports"
        return try {
            db.collection(path)
                .get()
                .await()
                .toObjects(SafetyReport::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .sortedByDescending { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            throw e
        }
    }

    suspend fun submitSafetyReport(
        category: String,
        title: String,
        description: String,
        locationLabel: String,
        lat: Double,
        lon: Double
    ): String {
        val uid = requireUserId()
        val docRef = db.collection("safety_reports").document()
        return try {
            docRef.set(
                mapOf(
                    "reporterId" to uid,
                    "category" to category,
                    "title" to title.trim().take(100),
                    "description" to description.trim().take(500),
                    "locationLabel" to locationLabel.trim().take(160),
                    "lat" to lat.coerceIn(-90.0, 90.0),
                    "lon" to lon.coerceIn(-180.0, 180.0),
                    "status" to "PENDING_REVIEW",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun moderateSafetyReport(reportId: String, newStatus: String, note: String) {
        val uid = requireUserId()
        require(reportId.isNotBlank()) { "Report ID is required" }
        require(newStatus in setOf("PENDING_REVIEW", "FLAGGED", "VERIFIED", "RESOLVED", "DISMISSED")) {
            "Invalid moderation status: $newStatus"
        }
        val docRef = db.collection("safety_reports").document(reportId)
        try {
            val cleanNote = note.trim().take(300)
            docRef.update(
                mapOf(
                    "status" to newStatus,
                    "moderationNote" to cleanNote,
                    "moderatedBy" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            writeAuditLogInternal(
                actorId = uid,
                action = "REPORT_MODERATED_$newStatus",
                resourceType = "safety_report",
                resourceId = reportId,
                outcome = newStatus,
                summary = if (cleanNote.isNotBlank()) {
                    "Admin marked report $newStatus: $cleanNote"
                } else {
                    "Admin moderated report to $newStatus."
                }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    // ---------------- Emergency Facilities Directory ----------------

    fun observeActiveEmergencyFacilities(): Flow<List<EmergencyFacility>> = flow {
        val path = "emergency_facilities"
        emitAll(
            db.collection(path)
                .whereEqualTo("activeStatus", true)
                .snapshots()
                .map { snap -> snap.toObjects(EmergencyFacility::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fe = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fe == null && error is CancellationException) throw error
                    handleFirestoreError(fe ?: error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun fetchEmergencyDirectoryByLocationAndCategory(
        category: EmergencyDirectoryCategory,
        centerLat: Double,
        centerLon: Double,
        radiusKm: Double? = null
    ): List<DirectoryFacilityItem> {
        val path = "emergency_facilities"
        return try {
            val docs = db.collection(path)
                .whereEqualTo("activeStatus", true)
                .get()
                .await()
                .toObjects(EmergencyFacility::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            filterAndSortEmergencyDirectory(
                facilities = docs,
                category = category,
                centerLat = centerLat,
                centerLon = centerLon,
                radiusKm = radiusKm
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            throw e
        }
    }

    suspend fun upsertVerifiedEmergencyFacility(facility: EmergencyFacility): String {
        val uid = requireUserId()
        val col = db.collection("emergency_facilities")
        val docRef = if (facility.id.isNotBlank()) col.document(facility.id) else col.document()
        return try {
            if (facility.id.isNotBlank()) {
                docRef.update(
                    mapOf(
                        "type" to facility.type,
                        "name" to facility.name.trim().take(120),
                        "phone" to facility.phone.trim().take(32),
                        "address" to facility.address.trim().take(200),
                        "lat" to facility.lat.coerceIn(-90.0, 90.0),
                        "lon" to facility.lon.coerceIn(-180.0, 180.0),
                        "verifiedSource" to facility.verifiedSource.trim().take(120),
                        "verifiedAtLabel" to facility.verifiedAtLabel.trim().take(40),
                        "activeStatus" to facility.activeStatus,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "type" to facility.type,
                        "name" to facility.name.trim().take(120),
                        "phone" to facility.phone.trim().take(32),
                        "address" to facility.address.trim().take(200),
                        "lat" to facility.lat.coerceIn(-90.0, 90.0),
                        "lon" to facility.lon.coerceIn(-180.0, 180.0),
                        "verifiedSource" to facility.verifiedSource.trim().take(120),
                        "verifiedAtLabel" to facility.verifiedAtLabel.trim().take(40),
                        "activeStatus" to facility.activeStatus,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            writeAuditLogInternal(
                actorId = uid,
                action = "FACILITY_VERIFIED",
                resourceType = "emergency_facility",
                resourceId = docRef.id,
                outcome = "SUCCESS",
                summary = "Verified facility '${facility.name}' saved."
            )
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    // ---------------- Admin RBAC & Audit ----------------

    suspend fun checkOrBootstrapAdmin(uid: String): Boolean {
        val docRef = db.collection("admins").document(uid)
        return try {
            val snap = docRef.get().await()
            if (snap.exists()) return true
            val (email, verified) = emailProvider()
            if (verified && email == BOOTSTRAP_ADMIN_EMAIL) {
                docRef.set(
                    mapOf(
                        "email" to email,
                        "role" to "OPS_ADMIN",
                        "grantedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                true
            } else {
                false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            false
        }
    }

    fun observeAuditLogs(uid: String, isAdmin: Boolean): Flow<List<AuditLog>> = flow {
        val path = "audit_logs"
        val query = if (isAdmin) db.collection(path) else db.collection(path).whereEqualTo("actorId", uid)
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(AuditLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val fe = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fe == null && error is CancellationException) throw error
                    if (authProvider() == uid) handleFirestoreError(fe ?: error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun purgeCompletedLocationBreadcrumbs(uid: String) {
        try {
            val journeys = db.collection("journeys")
                .whereEqualTo("userId", uid)
                .get()
                .await()
                .toObjects(Journey::class.java)
            journeys.filter { JourneyState.fromValue(it.state).isTerminal }.forEach { journey ->
                val pts = db.collection("journeys").document(journey.id)
                    .collection("locations")
                    .whereEqualTo("userId", uid)
                    .get()
                    .await()
                pts.documents.forEach { it.reference.delete().await() }
            }
            writeAuditLogInternal(
                actorId = uid,
                action = "LOCATION_HISTORY_PURGED",
                resourceType = "user",
                resourceId = uid,
                outcome = "SUCCESS",
                summary = "User purged location breadcrumbs from terminal journeys."
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "journeys/locations")
            throw e
        }
    }

    private suspend fun writeAuditLogInternal(
        actorId: String,
        action: String,
        resourceType: String,
        resourceId: String,
        outcome: String,
        summary: String
    ) {
        val docRef = db.collection("audit_logs").document()
        try {
            docRef.set(
                mapOf(
                    "actorId" to actorId,
                    "action" to action.take(80),
                    "resourceType" to resourceType.take(64),
                    "resourceId" to resourceId.take(128),
                    "outcome" to outcome.take(40),
                    "summary" to summary.take(300),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
        }
    }

    companion object {
        const val BOOTSTRAP_ADMIN_EMAIL = "sandeepsahukumar54321@gmail.com"

        fun generateHighEntropyToken(): String {
            val bytes = ByteArray(16)
            SecureRandom().nextBytes(bytes)
            val hex = bytes.joinToString("") { "%02x".format(it) }
            return "tok_$hex"
        }

        fun sha256Hex(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            return digest.joinToString("") { "%02x".format(it) }
        }

        fun filterAndSortEmergencyDirectory(
            facilities: List<EmergencyFacility>,
            category: EmergencyDirectoryCategory,
            centerLat: Double,
            centerLon: Double,
            radiusKm: Double?
        ): List<DirectoryFacilityItem> {
            val maxMeters = radiusKm?.times(1000.0)
            return facilities
                .asSequence()
                .filter { fac -> fac.activeStatus }
                .filter { fac ->
                    category == EmergencyDirectoryCategory.ALL ||
                        fac.type.uppercase() in category.firestoreTypes
                }
                .map { fac ->
                    val distanceM = RoutingAndSafetyEngine.haversineMeters(
                        centerLat,
                        centerLon,
                        fac.lat,
                        fac.lon
                    )
                    DirectoryFacilityItem(
                        facility = fac,
                        distanceMeters = distanceM,
                        category = EmergencyDirectoryCategory.fromFacilityType(fac.type)
                    )
                }
                .filter { item -> maxMeters == null || item.distanceMeters <= maxMeters }
                .sortedBy { it.distanceMeters }
                .toList()
        }
    }
}
