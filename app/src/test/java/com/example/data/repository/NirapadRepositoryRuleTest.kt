package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.EmergencyContact
import com.example.data.model.EmergencyDirectoryCategory
import com.example.data.model.EmergencyFacility
import com.example.data.model.Journey
import com.example.data.model.JourneyState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class NirapadRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private fun buildRepo(): NirapadRepository = NirapadRepository(
        db = firestore,
        authProvider = { auth.currentUser?.uid },
        emailProvider = {
            val u = auth.currentUser
            u?.email to (u?.isEmailVerified == true)
        }
    )

    @Test
    fun userProfileAndContactCrud_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser("alice_${UUID.randomUUID()}@test.com")
        val repo = buildRepo()

        val profile = withTimeout(TIMEOUT_MS) {
            repo.ensureUserProfile(uid, "Alice", "alice@test.com")
        }
        assertEquals(uid, profile.userId)

        val contactId = withTimeout(TIMEOUT_MS) {
            repo.upsertContact(
                EmergencyContact(
                    ownerUserId = uid,
                    name = "Meera",
                    phoneNumber = "+919876543210",
                    email = "meera@example.com",
                    contactChannel = "+919876543210",
                    channelType = "PHONE",
                    relationship = "Sister",
                    verified = true,
                    notifyOnStart = true,
                    notifyOnSos = true
                )
            )
        }
        assertTrue(contactId.isNotBlank())

        val contacts = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeContacts(uid).first { it.any { c -> c.id == contactId } }
        }
        val created = contacts.first { it.id == contactId }
        assertEquals("Meera", created.name)
        assertEquals("+919876543210", created.resolvedPhone)
        assertEquals("meera@example.com", created.resolvedEmail)

        // Update phone number and email address on existing contact
        withTimeout(TIMEOUT_MS) {
            repo.upsertContact(
                created.copy(
                    phoneNumber = "+919999888877",
                    email = "meera.updated@example.com",
                    notifyOnStart = false
                )
            )
        }
        val updatedContacts = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeContacts(uid).first {
                it.any { c -> c.id == contactId && c.resolvedPhone == "+919999888877" && c.resolvedEmail == "meera.updated@example.com" }
            }
        }
        val updated = updatedContacts.first { it.id == contactId }
        assertEquals(false, updated.notifyOnStart)

        // Delete contact and verify removal
        withTimeout(TIMEOUT_MS) {
            repo.deleteContact(contactId)
        }
        val afterDelete = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeContacts(uid).first { list -> list.none { it.id == contactId } }
        }
        assertTrue(afterDelete.none { it.id == contactId })
    }

    @Test
    fun journeyLifecycleAndShareRevocation_ownerAndTrustedLookup_enforced() = runBlocking {
        val aliceEmail = "alice_${UUID.randomUUID()}@test.com"
        val aliceUid = signInTestUser(aliceEmail)
        val aliceRepo = buildRepo()

        val (journeyId, tokenId) = withTimeout(TIMEOUT_MS) {
            aliceRepo.startProtectedJourney(
                ownerDisplayName = "Alice",
                originLabel = "Connaught Place",
                originLat = 28.6315,
                originLon = 77.2167,
                destinationLabel = "Hauz Khas Metro",
                destinationLat = 28.5433,
                destinationLon = 77.2066,
                selectedRouteId = "route_1",
                selectedRouteLabel = "Main Lit Arterial",
                providerName = "OSRM",
                deviationThresholdMeters = 250L,
                sharingEnabled = true,
                sharedContactCount = 1L,
                shareDurationMinutes = 60L
            )
        }
        assertTrue(journeyId.isNotBlank())
        assertTrue(tokenId.length >= 16)

        val journeys = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeJourneys(aliceUid).first { it.any { j -> j.id == journeyId } }
        }
        val journey = journeys.first { it.id == journeyId }

        // Trusted contact Bob can fetch by exact high-entropy token while active
        signInTestUser("bob_${UUID.randomUUID()}@test.com")
        val bobRepo = buildRepo()
        val fetchedToken = withTimeout(TIMEOUT_MS) { bobRepo.fetchShareTokenById(tokenId) }
        assertNotNull(fetchedToken)
        assertEquals("Hauz Khas Metro", fetchedToken?.destinationLabel)

        // Bob cannot read Alice's journey directly
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeJourneys(aliceUid).first()
            }
            fail("Expected PERMISSION_DENIED when Bob reads Alice's journeys")
        } catch (e: Exception) {
            val fe = e.unwrapFirestoreException()
            assertNotNull(fe)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fe?.code)
        }

        // Alice revokes share & completes journey
        signInTestUser(aliceEmail)
        withTimeout(TIMEOUT_MS) {
            aliceRepo.revokeShareToken(journey)
            aliceRepo.transitionJourneyState(journey, JourneyState.COMPLETED)
        }
    }

    @Test
    fun journeyTerminalLockAndSosDedupe_enforced() = runBlocking {
        val email = "alice_dedupe_${UUID.randomUUID()}@test.com"
        val uid = signInTestUser(email)
        val repo = buildRepo()

        val (journeyId, tokenId) = withTimeout(TIMEOUT_MS) {
            repo.startProtectedJourney(
                ownerDisplayName = "Alice",
                originLabel = "CP",
                originLat = 28.63,
                originLon = 77.21,
                destinationLabel = "AIIMS",
                destinationLat = 28.56,
                destinationLon = 77.21,
                selectedRouteId = "route_1",
                selectedRouteLabel = "Main Lit",
                providerName = "OSRM",
                deviationThresholdMeters = 250L,
                sharingEnabled = true,
                sharedContactCount = 1L,
                shareDurationMinutes = 60L
            )
        }

        val journey = Journey(
            id = journeyId,
            userId = uid,
            state = JourneyState.ACTIVE.value,
            sharingEnabled = true,
            shareTokenId = tokenId
        )

        // SOS Deduplication: two calls in same 60s window return same SOS ID
        val sos1 = withTimeout(TIMEOUT_MS) {
            repo.triggerSosWithDedupe(journeyId, 28.63, 77.21, "CP", emptyList())
        }
        val sos2 = withTimeout(TIMEOUT_MS) {
            repo.triggerSosWithDedupe(journeyId, 28.63, 77.21, "CP", emptyList())
        }
        assertEquals(sos1.id, sos2.id)

        // End journey -> revokes share token and locks terminal state
        withTimeout(TIMEOUT_MS) { repo.transitionJourneyState(journey, JourneyState.COMPLETED) }

        val completedJourney = journey.copy(state = JourneyState.COMPLETED.value)
        try {
            withTimeout(TIMEOUT_MS) {
                repo.transitionJourneyState(completedJourney, JourneyState.ACTIVE)
            }
            fail("Expected invalid state transition to throw")
        } catch (_: IllegalArgumentException) {
            // Expected state machine rejection
        }

        // Bob cannot read revoked token
        signInTestUser("bob_${UUID.randomUUID()}@test.com")
        val bobRepo = buildRepo()
        try {
            val res = withTimeout(TIMEOUT_MS) { bobRepo.fetchShareTokenById(tokenId) }
            assertNull(res)
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }
    }

    @Test
    fun standaloneSos_liveLocationStreamAndTrustedObservation_enforced() = runBlocking {
        val aliceEmail = "alice_sos_${UUID.randomUUID()}@test.com"
        val aliceUid = signInTestUser(aliceEmail)
        val aliceRepo = buildRepo()

        // Standalone SOS (no active journey) with configured trusted contact and alert reason
        val contacts = listOf(
            EmergencyContact(
                id = "c1",
                ownerUserId = aliceUid,
                name = "Meera",
                phoneNumber = "+919876543210",
                email = "meera@example.com",
                contactChannel = "+919876543210",
                channelType = "PHONE",
                notifyOnSos = true
            ),
            EmergencyContact(
                id = "c2",
                ownerUserId = aliceUid,
                name = "NonSosContact",
                phoneNumber = "+911122334455",
                contactChannel = "+911122334455",
                channelType = "PHONE",
                notifyOnSos = false
            )
        )
        val sos = withTimeout(TIMEOUT_MS) {
            aliceRepo.triggerSosWithDedupe(
                journeyId = "",
                lat = 28.6315,
                lon = 77.2167,
                locationLabel = "Connaught Place",
                contacts = contacts,
                ownerDisplayName = "Alice",
                alertReason = "Immediate Danger"
            )
        }
        assertTrue(sos.shareTokenId.length >= 16)
        assertEquals(1L, sos.recipientCount)
        assertTrue(sos.deliverySummary.contains("Immediate Danger"))
        assertTrue(sos.deliverySummary.contains("Meera"))

        // Background service pushes live GPS coordinates to Firestore
        val updated = withTimeout(TIMEOUT_MS) {
            aliceRepo.recordSosLiveLocation(
                sosId = sos.id,
                shareTokenId = sos.shareTokenId,
                journeyId = "",
                lat = 28.6350,
                lon = 77.2201,
                accuracyMeters = 9.0,
                sequence = 1L
            )
        }
        assertTrue(updated)

        // Trusted contact Bob observes the share token in real time and sees updated coordinates
        signInTestUser("bob_sos_${UUID.randomUUID()}@test.com")
        val bobRepo = buildRepo()
        val liveToken = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observeShareTokenById(sos.shareTokenId).first { it != null && it.lastLat > 28.634 }
        }
        assertNotNull(liveToken)
        assertEquals(JourneyState.SOS_TRIGGERED.value, liveToken?.journeyState)
        assertEquals(28.6350, liveToken?.lastLat ?: 0.0, 0.0001)

        // Alice resolves SOS -> standalone token revoked, subsequent live location writes return false
        signInTestUser(aliceEmail)
        withTimeout(TIMEOUT_MS) {
            aliceRepo.updateSosStatus(
                sosId = sos.id,
                newStatus = "RESOLVED",
                summaryNote = "Safe now",
                shareTokenId = sos.shareTokenId,
                standaloneSosOnly = true
            )
        }
        val afterResolve = withTimeout(TIMEOUT_MS) {
            aliceRepo.recordSosLiveLocation(
                sosId = sos.id,
                shareTokenId = sos.shareTokenId,
                journeyId = "",
                lat = 28.6400,
                lon = 77.2250,
                accuracyMeters = 8.0,
                sequence = 2L
            )
        }
        assertTrue(!afterResolve)

        // Ensure aliceUid was scoped cleanly
        val sessions = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeSosSessions(aliceUid).first { it.any { s -> s.id == sos.id } }
        }
        assertEquals("RESOLVED", sessions.first { it.id == sos.id }.status)
    }

    @Test
    fun unauthenticatedCaller_observeJourneys_rejectedWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = buildRepo()

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeJourneys("someone_123").first()
            }
            fail("Expected PERMISSION_DENIED for unauthenticated caller")
        } catch (e: Exception) {
            val fe = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", fe)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fe?.code)
        }
    }

    @Test
    fun emergencyDirectory_rulesAndLocationCategoryFiltering_verified() = runBlocking {
        // 1. Verify unauthenticated caller cannot read emergency_facilities
        auth.signOut()
        val unauthRepo = buildRepo()
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                unauthRepo.observeActiveEmergencyFacilities().first()
            }
            fail("Expected PERMISSION_DENIED when unauthenticated caller queries emergency_facilities")
        } catch (e: Exception) {
            val fe = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", fe)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fe?.code)
        }

        // 2. Signed-in traveller can query active emergency_facilities without PERMISSION_DENIED
        signInTestUser("alice_dir_${UUID.randomUUID()}@test.com")
        val aliceRepo = buildRepo()
        val fetched = withTimeout(TIMEOUT_MS) {
            aliceRepo.fetchEmergencyDirectoryByLocationAndCategory(
                category = EmergencyDirectoryCategory.ALL,
                centerLat = 28.6315,
                centerLon = 77.2167,
                radiusKm = 15.0
            )
        }
        assertNotNull(fetched)

        // 3. Non-admin traveller cannot write unverified emergency facilities
        try {
            withTimeout(TIMEOUT_MS) {
                aliceRepo.upsertVerifiedEmergencyFacility(
                    EmergencyFacility(
                        type = "POLICE",
                        name = "Unverified Desk",
                        phone = "9999900000",
                        address = "Test Street",
                        lat = 28.63,
                        lon = 77.21,
                        verifiedSource = "Unverified",
                        verifiedAtLabel = "2026-11-13",
                        activeStatus = true
                    )
                )
            }
            fail("Expected PERMISSION_DENIED when non-admin writes emergency_facilities")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }

        // 4. Verify location radius & category filtering + nearest-first sorting
        val sampleFacilities = listOf(
            EmergencyFacility(id = "p_far", type = "POLICE", name = "Far Police Station", phone = "112", address = "Noida", lat = 28.5355, lon = 77.3910, verifiedSource = "Dir", verifiedAtLabel = "2026-11-13", activeStatus = true),
            EmergencyFacility(id = "p_near", type = "POLICE", name = "Near Police Station", phone = "100", address = "CP", lat = 28.6320, lon = 77.2175, verifiedSource = "Dir", verifiedAtLabel = "2026-11-13", activeStatus = true),
            EmergencyFacility(id = "a_near", type = "AMBULANCE", name = "Near Ambulance Hub", phone = "108", address = "Minto Road", lat = 28.6340, lon = 77.2240, verifiedSource = "NHM", verifiedAtLabel = "2026-11-13", activeStatus = true),
            EmergencyFacility(id = "w_mid", type = "WOMENS_SUPPORT", name = "Sakhi One Stop Center", phone = "181", address = "Barakhamba", lat = 28.6285, lon = 77.2255, verifiedSource = "MWCD", verifiedAtLabel = "2026-11-13", activeStatus = true),
            EmergencyFacility(id = "w_inactive", type = "WOMENS_SUPPORT", name = "Inactive Hub", phone = "181", address = "Old", lat = 28.6315, lon = 77.2167, verifiedSource = "MWCD", verifiedAtLabel = "2026-11-13", activeStatus = false)
        )

        val nearbyAll = NirapadRepository.filterAndSortEmergencyDirectory(
            facilities = sampleFacilities,
            category = EmergencyDirectoryCategory.ALL,
            centerLat = 28.6315,
            centerLon = 77.2167,
            radiusKm = 5.0
        )
        assertEquals(listOf("p_near", "a_near", "w_mid"), nearbyAll.map { it.facility.id })

        val policeOnlyAllAreas = NirapadRepository.filterAndSortEmergencyDirectory(
            facilities = sampleFacilities,
            category = EmergencyDirectoryCategory.POLICE,
            centerLat = 28.6315,
            centerLon = 77.2167,
            radiusKm = null
        )
        assertEquals(listOf("p_near", "p_far"), policeOnlyAllAreas.map { it.facility.id })

        val womensSupportOnly = NirapadRepository.filterAndSortEmergencyDirectory(
            facilities = sampleFacilities,
            category = EmergencyDirectoryCategory.WOMENS_SUPPORT,
            centerLat = 28.6315,
            centerLon = 77.2167,
            radiusKm = 15.0
        )
        assertEquals(listOf("w_mid"), womensSupportOnly.map { it.facility.id })
    }

    @Test
    fun safetyReports_adminFetchFlagResolve_andNonAdminDenial_enforced() = runBlocking {
        // 1. Traveller submits a safety report (status starts as PENDING_REVIEW)
        val reporterEmail = "reporter_${UUID.randomUUID()}@test.com"
        val reporterUid = signInTestUser(reporterEmail)
        val reporterRepo = buildRepo()

        val reportId = withTimeout(TIMEOUT_MS) {
            reporterRepo.submitSafetyReport(
                category = "POOR_LIGHTING",
                title = "Broken corridor lamps near Gate 3",
                description = "Three consecutive poles are dark after 8 PM.",
                locationLabel = "Hauz Khas Metro Gate 3",
                lat = 28.5433,
                lon = 77.2066
            )
        }
        assertTrue(reportId.isNotBlank())

        val ownReports = withTimeout(FLOW_TIMEOUT_MS) {
            reporterRepo.observeMySafetyReports(reporterUid).first { list -> list.any { it.id == reportId } }
        }
        assertEquals("PENDING_REVIEW", ownReports.first { it.id == reportId }.status)

        // 2. Non-admin traveller cannot moderate (flag/resolve) or list all reports
        try {
            withTimeout(TIMEOUT_MS) {
                reporterRepo.moderateSafetyReport(reportId, "FLAGGED", "Attempted unauthorized flag")
            }
            fail("Expected PERMISSION_DENIED when non-admin flags a safety report")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }

        try {
            withTimeout(TIMEOUT_MS) {
                reporterRepo.fetchSafetyReportsForAdmin()
            }
            fail("Expected PERMISSION_DENIED when non-admin lists all safety reports")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }

        // 3. Verified Operations Admin bootstraps RBAC, fetches reports, flags, and resolves
        val adminUid = signInVerifiedBootstrapAdmin()
        val adminRepo = buildRepo()
        val isAdmin = withTimeout(TIMEOUT_MS) { adminRepo.checkOrBootstrapAdmin(adminUid) }
        assertTrue("Expected bootstrap admin email to be authorized", isAdmin)

        val fetched = withTimeout(TIMEOUT_MS) { adminRepo.fetchSafetyReportsForAdmin() }
        assertTrue(fetched.any { it.id == reportId })

        withTimeout(TIMEOUT_MS) {
            adminRepo.moderateSafetyReport(reportId, "FLAGGED", "Escalated to civic lighting team")
        }
        val flaggedList = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeAllReportsForAdmin().first { list ->
                list.any { it.id == reportId && it.status == "FLAGGED" }
            }
        }
        assertEquals("Escalated to civic lighting team", flaggedList.first { it.id == reportId }.moderationNote)

        withTimeout(TIMEOUT_MS) {
            adminRepo.moderateSafetyReport(reportId, "RESOLVED", "Streetlights repaired and inspected")
        }
        val resolvedList = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeAllReportsForAdmin().first { list ->
                list.any { it.id == reportId && it.status == "RESOLVED" }
            }
        }
        val resolved = resolvedList.first { it.id == reportId }
        assertEquals("Streetlights repaired and inspected", resolved.moderationNote)
        assertEquals(adminUid, resolved.moderatedBy)
    }

    private suspend fun signInVerifiedBootstrapAdmin(): String {
        val uid = signInTestUser(NirapadRepository.BOOTSTRAP_ADMIN_EMAIL)
        val user = checkNotNull(auth.currentUser)
        if (!user.isEmailVerified) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                withTimeout(TIMEOUT_MS) {
                    com.google.android.gms.tasks.Tasks.await(user.sendEmailVerification())
                    val oobJson = java.net.URL(
                        "http://127.0.0.1:9099/emulator/v1/projects/demo-no-project/oobCodes"
                    ).readText()
                    val codes = org.json.JSONObject(oobJson).getJSONArray("oobCodes")
                    var link: String? = null
                    for (i in codes.length() - 1 downTo 0) {
                        val entry = codes.getJSONObject(i)
                        if (entry.optString("email") == NirapadRepository.BOOTSTRAP_ADMIN_EMAIL) {
                            link = entry.optString("oobLink")
                            break
                        }
                    }
                    checkNotNull(link) { "Verification oobLink missing from Auth emulator" }
                    java.net.URL(link).readText()
                    com.google.android.gms.tasks.Tasks.await(user.reload())
                    com.google.android.gms.tasks.Tasks.await(user.getIdToken(true))
                }
            }
        }
        return uid
    }

    private companion object {
        const val TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
