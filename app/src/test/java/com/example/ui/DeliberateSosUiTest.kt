package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.localization.AppLang
import com.example.localization.NirapadStrings
import com.example.ui.components.ActiveSosBroadcastBanner
import com.example.ui.components.ActiveSosPulseOverlay
import com.example.ui.components.DeliberateSosCountdownOverlay
import com.example.ui.components.DeliberateSosFab
import com.example.ui.theme.NirapadTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp")
class DeliberateSosUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun deliberateSosFab_clickOpensOverlay_andCancelDuringCountdownPreventsTrigger() {
        var sosTriggered = false
        var cancelled = false
        val copy = NirapadStrings.get(AppLang.EN)

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            var showOverlay by remember { mutableStateOf(false) }
            NirapadTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    DeliberateSosFab(
                        label = copy.emergencySosLabel,
                        activeLabel = copy.sosActiveFabLabel,
                        isSosActive = false,
                        isLiveSharingRunning = false,
                        onClick = { showOverlay = true }
                    )
                    if (showOverlay) {
                        DeliberateSosCountdownOverlay(
                            copy = copy,
                            contactCount = 2,
                            locationPermissionGranted = true,
                            onComplete = {
                                showOverlay = false
                                sosTriggered = true
                            },
                            onCancel = {
                                showOverlay = false
                                cancelled = true
                            }
                        )
                    }
                }
            }
        }

        composeRule.mainClock.advanceTimeBy(100)
        composeRule.onNodeWithTag("deliberate_sos_fab").assertIsDisplayed().performClick()
        composeRule.mainClock.advanceTimeBy(1000)

        composeRule.onNodeWithTag("sos_countdown_overlay").assertIsDisplayed()
        assertFalse(sosTriggered)

        composeRule.onNodeWithTag("sos_countdown_cancel_button")
            .performScrollTo()
            .performClick()
        composeRule.mainClock.advanceTimeBy(500)

        assertTrue(cancelled)
        assertFalse(sosTriggered)
    }

    @Test
    fun deliberateSosOverlay_completesAfter3Seconds_triggersSos() {
        var triggerCount = 0
        val copy = NirapadStrings.get(AppLang.EN)

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            NirapadTheme {
                DeliberateSosCountdownOverlay(
                    copy = copy,
                    contactCount = 1,
                    locationPermissionGranted = true,
                    onComplete = { triggerCount += 1 },
                    onCancel = {}
                )
            }
        }

        composeRule.mainClock.advanceTimeBy(2000)
        assertEquals(0, triggerCount)

        composeRule.mainClock.advanceTimeBy(1200)
        assertEquals(1, triggerCount)
    }

    @Test
    fun deliberateSosOverlay_sendNow_triggersImmediatelyOnce() {
        var triggerCount = 0
        val copy = NirapadStrings.get(AppLang.EN)

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            NirapadTheme {
                DeliberateSosCountdownOverlay(
                    copy = copy,
                    contactCount = 1,
                    locationPermissionGranted = true,
                    onComplete = { triggerCount += 1 },
                    onCancel = {}
                )
            }
        }

        composeRule.mainClock.advanceTimeBy(100)
        composeRule.onNodeWithTag("sos_countdown_send_now_button")
            .performScrollTo()
            .performClick()
        composeRule.mainClock.advanceTimeBy(3500)
        assertEquals(1, triggerCount)
    }

    @Test
    fun activeSosPulseOverlay_showsWhenActive_doesNotBlockClicks_andHidesWhenResolved() {
        val copy = NirapadStrings.get(AppLang.EN)
        var underlyingClicks = 0
        var bannerClicks = 0

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            var isSosActive by remember { mutableStateOf(true) }
            NirapadTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column {
                        AnimatedVisibility(
                            visible = isSosActive,
                            enter = fadeIn(tween(150)),
                            exit = fadeOut(tween(150))
                        ) {
                            ActiveSosBroadcastBanner(
                                title = copy.sosBroadcastingTitle,
                                subtitle = copy.sosBroadcastingSubtitle,
                                isLiveSharingRunning = true,
                                broadcastCount = 3,
                                onClick = { bannerClicks += 1 }
                            )
                        }
                        Button(
                            onClick = { underlyingClicks += 1 },
                            modifier = Modifier.testTag("underlying_action")
                        ) {
                            Text("Underlying Action")
                        }
                        Button(
                            onClick = { isSosActive = false },
                            modifier = Modifier.testTag("resolve_sos_toggle")
                        ) {
                            Text("Resolve")
                        }
                        DeliberateSosFab(
                            label = copy.emergencySosLabel,
                            activeLabel = copy.sosActiveFabLabel,
                            isSosActive = isSosActive,
                            isLiveSharingRunning = isSosActive,
                            onClick = {}
                        )
                    }
                    AnimatedVisibility(
                        visible = isSosActive,
                        enter = fadeIn(tween(150)),
                        exit = fadeOut(tween(150))
                    ) {
                        ActiveSosPulseOverlay(isLiveSharingRunning = true)
                    }
                }
            }
        }

        composeRule.mainClock.advanceTimeBy(600)
        composeRule.onNodeWithTag("active_sos_pulse_overlay").assertIsDisplayed()
        composeRule.onNodeWithTag("active_sos_broadcast_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("deliberate_sos_fab").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("SOS active and broadcasting", substring = true)
            .assertIsDisplayed()

        // Verify the pulsating edge overlay does not intercept touches on underlying controls
        composeRule.onNodeWithTag("underlying_action").performClick()
        composeRule.onNodeWithTag("active_sos_broadcast_banner").performClick()
        composeRule.mainClock.advanceTimeBy(200)
        assertEquals(1, underlyingClicks)
        assertEquals(1, bannerClicks)

        // Resolving SOS dismisses the pulse overlay and banner
        composeRule.onNodeWithTag("resolve_sos_toggle").performClick()
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.onNodeWithTag("active_sos_pulse_overlay").assertDoesNotExist()
        composeRule.onNodeWithTag("active_sos_broadcast_banner").assertDoesNotExist()
    }
}
