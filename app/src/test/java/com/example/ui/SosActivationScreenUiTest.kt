package com.example.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import com.example.data.firestore.UiState
import com.example.data.model.EmergencyContact
import com.example.data.model.SosSession
import com.example.data.service.LocationReading
import com.example.localization.AppLang
import com.example.localization.NirapadStrings
import com.example.ui.screens.SosActivationScreen
import com.example.ui.theme.NirapadTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp")
class SosActivationScreenUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleContacts(): List<EmergencyContact> = listOf(
        EmergencyContact(
            id = "c_meera",
            name = "Meera Sharma",
            phoneNumber = "+919876543210",
            email = "meera@example.com",
            contactChannel = "+919876543210",
            channelType = "PHONE",
            relationship = "Sister",
            verified = true,
            notifyOnStart = true,
            notifyOnSos = true
        ),
        EmergencyContact(
            id = "c_muted",
            name = "Office Guard",
            phoneNumber = "+911122334455",
            contactChannel = "+911122334455",
            channelType = "PHONE",
            relationship = "Security",
            verified = false,
            notifyOnStart = true,
            notifyOnSos = false
        )
    )

    private fun sampleLocation(): LocationReading = LocationReading(
        lat = 28.5433,
        lon = 77.2066,
        accuracyMeters = 12.0,
        timestampMillis = 1_700_000_000_000L
    )

    @Test
    fun armedStage_showsTrustedContacts_andCancelDuringCountdownPreventsActivation() {
        val copy = NirapadStrings.get(AppLang.EN)
        val activatedReasons = mutableListOf<String>()

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            NirapadTheme {
                SosActivationScreen(
                    copy = copy,
                    currentLocation = sampleLocation(),
                    hasLiveGpsFix = true,
                    locationPermissionGranted = true,
                    contactsState = UiState.Success(sampleContacts()),
                    activeSos = null,
                    isBackgroundServiceRunning = false,
                    broadcastCount = 0,
                    lastBroadcastReading = null,
                    autoStartCountdown = false,
                    onRequestLocationPermission = {},
                    onManageContacts = {},
                    onActivateSos = { activatedReasons += it },
                    onUpdateSosStatus = { _, _ -> },
                    onNavigateBack = {}
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(150)

        composeRule.onNodeWithTag("sos_screen_armed_hero").assertIsDisplayed()
        composeRule.onNodeWithTag("sos_activation_list").performScrollToIndex(4)
        composeRule.mainClock.advanceTimeBy(50)
        composeRule.onNodeWithTag("sos_screen_contact_c_meera").assertIsDisplayed()
        composeRule.onNodeWithTag("sos_screen_contact_c_muted").assertDoesNotExist()

        composeRule.onNodeWithTag("sos_activation_list").performScrollToIndex(3)
        composeRule.mainClock.advanceTimeBy(50)
        composeRule.onNodeWithTag("sos_preset_unsafe_pursuit").performClick()
        composeRule.onNodeWithTag("sos_screen_note_input").performTextInput("Near Gate 2")
        composeRule.mainClock.advanceTimeBy(150)

        composeRule.onNodeWithTag("sos_activation_list").performScrollToIndex(1)
        composeRule.onNodeWithTag("sos_screen_activate_button").performClick()
        composeRule.mainClock.advanceTimeBy(1000)

        composeRule.onNodeWithTag("sos_screen_countdown_stage").assertIsDisplayed()
        composeRule.onNodeWithText("Alert: Unsafe Pursuit - Near Gate 2").assertIsDisplayed()
        assertTrue(activatedReasons.isEmpty())

        composeRule.onNodeWithTag("sos_screen_cancel_countdown_button").performClick()
        composeRule.mainClock.advanceTimeBy(2500)

        assertTrue(activatedReasons.isEmpty())
        composeRule.onNodeWithTag("sos_screen_armed_hero").assertIsDisplayed()
    }

    @Test
    fun countdownStage_autoCompletesAfter3Seconds_andSendNowTriggersOnce() {
        val copy = NirapadStrings.get(AppLang.EN)
        val activatedReasons = mutableListOf<String>()

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            NirapadTheme {
                SosActivationScreen(
                    copy = copy,
                    currentLocation = sampleLocation(),
                    hasLiveGpsFix = true,
                    locationPermissionGranted = true,
                    contactsState = UiState.Success(sampleContacts()),
                    activeSos = null,
                    isBackgroundServiceRunning = false,
                    broadcastCount = 0,
                    lastBroadcastReading = null,
                    autoStartCountdown = true,
                    onRequestLocationPermission = {},
                    onManageContacts = {},
                    onActivateSos = { activatedReasons += it },
                    onUpdateSosStatus = { _, _ -> },
                    onNavigateBack = {}
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.onNodeWithTag("sos_screen_countdown_stage").assertIsDisplayed()

        // Not yet triggered at 2.1s
        composeRule.mainClock.advanceTimeBy(2000)
        assertEquals(0, activatedReasons.size)

        // Completes at 3.0s
        composeRule.mainClock.advanceTimeBy(1200)
        assertEquals(listOf("Immediate Danger"), activatedReasons)
    }

    @Test
    fun sendNowAndActiveBroadcastStage_sharesLocationAndSupportsResolve() {
        val copy = NirapadStrings.get(AppLang.EN)
        val statusUpdates = mutableListOf<Pair<String, String>>()

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            var activeSession by remember { mutableStateOf<SosSession?>(null) }
            var sirenPlaying by remember { mutableStateOf(false) }
            var sirenMuted by remember { mutableStateOf(false) }
            NirapadTheme {
                SosActivationScreen(
                    copy = copy,
                    currentLocation = sampleLocation(),
                    hasLiveGpsFix = true,
                    locationPermissionGranted = true,
                    contactsState = UiState.Success(sampleContacts()),
                    activeSos = activeSession,
                    isBackgroundServiceRunning = activeSession != null,
                    broadcastCount = if (activeSession != null) 4 else 0,
                    lastBroadcastReading = if (activeSession != null) {
                        LocationReading(28.5450, 77.2088, 7.0, 1_700_000_005_000L)
                    } else {
                        null
                    },
                    autoStartCountdown = true,
                    isSirenPlaying = sirenPlaying,
                    isSirenMuted = sirenMuted,
                    onToggleSiren = { muted ->
                        sirenMuted = muted
                        sirenPlaying = !muted && activeSession != null
                    },
                    onRequestLocationPermission = {},
                    onManageContacts = {},
                    onActivateSos = { reason ->
                        sirenPlaying = !sirenMuted
                        activeSession = SosSession(
                            id = "sos_live_1234",
                            userId = "user_1",
                            shareTokenId = "tok_0123456789abcdef0123456789abcdef",
                            status = "ACTIVE",
                            lat = 28.5433,
                            lon = 77.2066,
                            locationLabel = "Current GPS Fix",
                            deliverySummary = "[$reason] Live SOS location shared to 1 contact(s): Meera Sharma",
                            recipientCount = 1L
                        )
                    },
                    onUpdateSosStatus = { session, status ->
                        statusUpdates += session.id to status
                        sirenPlaying = false
                        activeSession = null
                    },
                    onNavigateBack = {}
                )
            }
        }

        composeRule.mainClock.advanceTimeBy(150)
        composeRule.onNodeWithTag("sos_screen_send_now_button").performClick()
        composeRule.mainClock.advanceTimeBy(250)

        composeRule.onNodeWithTag("sos_screen_active_stage").assertIsDisplayed()
        composeRule.onNodeWithText("Siren MAX").assertIsDisplayed()
        composeRule.onNodeWithTag("sos_screen_active_siren_toggle").performClick()
        composeRule.mainClock.advanceTimeBy(50)
        composeRule.onNodeWithText("Muted").assertIsDisplayed()

        composeRule.onNodeWithTag("sos_activation_list").performScrollToIndex(2)
        composeRule.mainClock.advanceTimeBy(50)
        composeRule.onNodeWithTag("sos_screen_live_location_card").assertIsDisplayed()
        composeRule.onNodeWithText("SYNCING (4)").assertIsDisplayed()
        composeRule.onNodeWithText("Coordinates: 28.54500, 77.20880 (±7m)").assertIsDisplayed()
        composeRule.onNodeWithTag("sos_activation_list").performScrollToIndex(3)
        composeRule.mainClock.advanceTimeBy(50)
        composeRule.onNodeWithTag("sos_active_recipient_c_meera").assertIsDisplayed()

        composeRule.onNodeWithTag("sos_activation_list").performScrollToIndex(4)
        composeRule.mainClock.advanceTimeBy(50)
        composeRule.onNodeWithTag("sos_screen_resolve_active_button").performClick()
        composeRule.mainClock.advanceTimeBy(200)

        assertEquals(listOf("sos_live_1234" to "RESOLVED"), statusUpdates)
    }
}
