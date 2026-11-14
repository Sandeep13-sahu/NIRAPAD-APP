package com.example.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.ui.screens.AuthScreen
import com.example.ui.theme.NirapadTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp")
class AuthScreenUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun advanceRevealAnimations() {
        repeat(6) {
            composeRule.mainClock.advanceTimeBy(120)
        }
    }

    @Test
    fun authScreen_revealsStaggeredHeroPillarsAndGoogleCta_andSwitchesLanguage() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            NirapadTheme {
                AuthScreen(
                    onAuthSuccess = {},
                    attemptAutoSignInOnLaunch = false
                )
            }
        }
        advanceRevealAnimations()

        composeRule.onNodeWithTag("auth_shield_emblem").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_hero_card").assertIsDisplayed()
        composeRule.onNodeWithText("NIRAPAD").assertIsDisplayed()
        composeRule.onNodeWithTag("google_sign_in_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Sign in with Google").assertIsDisplayed()

        // Switch language to Hindi
        composeRule.onNodeWithTag("auth_language_hi").performScrollTo().performClick()
        repeat(4) { composeRule.mainClock.advanceTimeBy(100) }
        composeRule.onNodeWithText("निरापद (NIRAPAD)").assertIsDisplayed()
    }

    @Test
    fun googleSignInButton_showsLoadingErrorShakeAndVerifiedSuccess() {
        var successCount = 0
        var pendingOnSuccess: (() -> Unit)? = null
        var pendingOnError: ((String) -> Unit)? = null
        var pendingOnCancelled: (() -> Unit)? = null

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            NirapadTheme {
                AuthScreen(
                    onAuthSuccess = { successCount++ },
                    attemptAutoSignInOnLaunch = false,
                    onGoogleSignInOverride = { onSuccess, onError, onCancelled ->
                        pendingOnSuccess = onSuccess
                        pendingOnError = onError
                        pendingOnCancelled = onCancelled
                    }
                )
            }
        }
        advanceRevealAnimations()

        // Click Google Sign-In -> enters loading state
        composeRule.onNodeWithTag("google_sign_in_button").performScrollTo().performClick()
        repeat(3) { composeRule.mainClock.advanceTimeBy(100) }
        composeRule.onNodeWithText("Signing in with Google...").assertIsDisplayed()

        // Cancelled flow returns cleanly to idle without showing an error banner
        pendingOnCancelled?.invoke()
        repeat(3) { composeRule.mainClock.advanceTimeBy(100) }
        composeRule.onNodeWithText("Sign in with Google").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_error_banner").assertDoesNotExist()

        // Error flow reveals animated error banner with message
        composeRule.onNodeWithTag("google_sign_in_button").performScrollTo().performClick()
        repeat(2) { composeRule.mainClock.advanceTimeBy(100) }
        pendingOnError?.invoke("No Google account is signed in on this device.")
        repeat(10) { composeRule.mainClock.advanceTimeBy(60) }
        composeRule.onNodeWithTag("auth_error_banner")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("No Google account", substring = true)

        // Retry and succeed -> shows verified confirmation and notifies caller
        composeRule.onNodeWithTag("google_sign_in_button").performScrollTo().performClick()
        repeat(2) { composeRule.mainClock.advanceTimeBy(100) }
        pendingOnSuccess?.invoke()
        repeat(4) { composeRule.mainClock.advanceTimeBy(100) }
        composeRule.onNodeWithText("Signed in safely").assertIsDisplayed()
        assertEquals(1, successCount)
    }
}
