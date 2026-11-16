package com.example.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.example.auth.AuthManager
import com.example.ui.screens.AuthScreen
import com.example.ui.theme.NirapadTheme
import com.google.firebase.auth.FirebaseAuthException
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
class AuthScreenUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun advanceRevealAnimations() {
        repeat(6) {
            composeRule.mainClock.advanceTimeBy(120)
        }
    }

    @Test
    fun authScreen_rendersMinimalistLayout_declutteredCopy_andSwitchesLanguage() {
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
        composeRule.onNodeWithText("Safe navigation & instant emergency protection").assertIsDisplayed()

        // High-contrast Email & Password fields, Forgot Password link, and primary Sign In CTA
        composeRule.onNodeWithTag("auth_full_name_input").assertDoesNotExist()
        composeRule.onNodeWithTag("auth_email_input").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("auth_password_input").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("auth_forgot_password_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("auth_email_sign_in_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Sign In / Continue").assertIsDisplayed()

        // Prominent Continue with Google button
        composeRule.onNodeWithTag("google_sign_in_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Continue with Google").assertIsDisplayed()

        // Single-line privacy assurance footer
        composeRule.onNodeWithTag("auth_privacy_note").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Protected by end-to-end encrypted location sharing").assertIsDisplayed()

        // Legacy verbose paragraphs/banners are removed
        composeRule.onNodeWithText("Travel with Transparent Safety Context").assertDoesNotExist()
        composeRule.onNodeWithText("Decision support only", substring = true).assertDoesNotExist()

        // Switch language to Hindi
        composeRule.onNodeWithTag("auth_language_hi").performScrollTo().performClick()
        repeat(4) { composeRule.mainClock.advanceTimeBy(100) }
        composeRule.onNodeWithText("निरापद (NIRAPAD)").assertIsDisplayed()
        composeRule.onNodeWithText("Google से जारी रखें").assertIsDisplayed()
    }

    @Test
    fun createAccountMode_showsFullNameField_validatesUpfront_andCreatesAccount() {
        var successCount = 0
        var emailCalls = 0
        var capturedName = ""
        var capturedEmail = ""
        var capturedPassword = ""
        var capturedCreateAccount = false
        var pendingOnSuccess: (() -> Unit)? = null
        var pendingOnError: ((String) -> Unit)? = null

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            NirapadTheme {
                AuthScreen(
                    onAuthSuccess = { successCount++ },
                    attemptAutoSignInOnLaunch = false,
                    onEmailSignInOverride = { email, password, fullName, createAccount, onSuccess, onError ->
                        emailCalls++
                        capturedEmail = email
                        capturedPassword = password
                        capturedName = fullName
                        capturedCreateAccount = createAccount
                        pendingOnSuccess = onSuccess
                        pendingOnError = onError
                    }
                )
            }
        }
        advanceRevealAnimations()

        // Switch to Create Account mode -> reveals optional Full Name field & hides Forgot Password
        composeRule.onNodeWithTag("auth_mode_toggle").performScrollTo().performClick()
        repeat(3) { composeRule.mainClock.advanceTimeBy(100) }
        composeRule.onNodeWithTag("auth_full_name_input").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("auth_forgot_password_button").assertDoesNotExist()
        composeRule.onNodeWithText("Create Account & Continue").assertIsDisplayed()

        // Invalid email caught upfront before Firebase call
        composeRule.onNodeWithTag("auth_email_input").performScrollTo().performTextInput("invalid-email")
        composeRule.onNodeWithTag("auth_password_input").performScrollTo().performTextInput("123456")
        composeRule.onNodeWithTag("auth_email_sign_in_button").performScrollTo().performClick()
        repeat(10) { composeRule.mainClock.advanceTimeBy(60) }
        assertEquals(0, emailCalls)
        composeRule.onNodeWithTag("auth_error_banner")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("valid email", substring = true)

        // Short password caught upfront before Firebase call
        composeRule.onNodeWithTag("auth_email_input").performScrollTo().performTextReplacement("ananya@nirapad.in")
        composeRule.onNodeWithTag("auth_password_input").performScrollTo().performTextReplacement("123")
        composeRule.onNodeWithTag("auth_email_sign_in_button").performScrollTo().performClick()
        repeat(10) { composeRule.mainClock.advanceTimeBy(60) }
        assertEquals(0, emailCalls)
        composeRule.onNodeWithTag("auth_error_banner")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("at least 6 characters", substring = true)
    }

    @Test
    fun createAccountAndSignIn_withFullNameEmailPassword_blocksDoubleTapsAndCompletes() {
        var successCount = 0
        var emailCalls = 0
        var capturedName = ""
        var capturedEmail = ""
        var capturedPassword = ""
        var capturedCreateAccount = false
        var pendingOnSuccess: (() -> Unit)? = null
        var pendingOnError: ((String) -> Unit)? = null

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            NirapadTheme {
                AuthScreen(
                    onAuthSuccess = { successCount++ },
                    attemptAutoSignInOnLaunch = false,
                    onEmailSignInOverride = { email, password, fullName, createAccount, onSuccess, onError ->
                        emailCalls++
                        capturedEmail = email
                        capturedPassword = password
                        capturedName = fullName
                        capturedCreateAccount = createAccount
                        pendingOnSuccess = onSuccess
                        pendingOnError = onError
                    }
                )
            }
        }
        advanceRevealAnimations()

        // Enter Create Account mode and fill Name, Email, Password
        composeRule.onNodeWithTag("auth_mode_toggle").performScrollTo().performClick()
        repeat(3) { composeRule.mainClock.advanceTimeBy(100) }
        composeRule.onNodeWithTag("auth_full_name_input").performScrollTo().performTextInput("Ananya Sharma")
        composeRule.onNodeWithTag("auth_email_input").performScrollTo().performTextInput("ananya@nirapad.in")
        composeRule.onNodeWithTag("auth_password_input").performScrollTo().performTextInput("SafePass123")

        composeRule.onNodeWithTag("auth_email_sign_in_button").performScrollTo().performClick()
        composeRule.mainClock.advanceTimeBy(100)

        assertEquals(1, emailCalls)
        assertEquals("Ananya Sharma", capturedName)
        assertEquals("ananya@nirapad.in", capturedEmail)
        assertEquals("SafePass123", capturedPassword)
        assertTrue(capturedCreateAccount)

        // While loading, both buttons are disabled (prevents double taps)
        composeRule.onNodeWithTag("auth_email_sign_in_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("google_sign_in_button").assertIsNotEnabled()

        // Simulated error resets loading & enables buttons again
        pendingOnError?.invoke("An account already exists with this email. Sign in instead.")
        repeat(10) { composeRule.mainClock.advanceTimeBy(60) }
        composeRule.onNodeWithTag("auth_error_banner")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("already exists", substring = true)

        // Switch back to Sign In / Continue and succeed
        composeRule.onNodeWithTag("auth_mode_toggle").performScrollTo().performClick()
        repeat(3) { composeRule.mainClock.advanceTimeBy(100) }
        composeRule.onNodeWithTag("auth_email_sign_in_button").performScrollTo().performClick()
        composeRule.mainClock.advanceTimeBy(100)
        assertFalse(capturedCreateAccount)
        assertEquals(2, emailCalls)

        pendingOnSuccess?.invoke()
        repeat(4) { composeRule.mainClock.advanceTimeBy(100) }
        composeRule.onNodeWithText("Signed in safely").assertIsDisplayed()
        assertEquals(1, successCount)
    }

    @Test
    fun forgotPassword_validatesEmailAndDisplaysConfirmationBanner() {
        var resetCalls = 0
        var resetEmailArg = ""
        var pendingOnSent: ((String) -> Unit)? = null

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            NirapadTheme {
                AuthScreen(
                    onAuthSuccess = {},
                    attemptAutoSignInOnLaunch = false,
                    onForgotPasswordOverride = { email, onSent, _ ->
                        resetCalls++
                        resetEmailArg = email
                        pendingOnSent = onSent
                    }
                )
            }
        }
        advanceRevealAnimations()

        // Clicking Forgot Password without an email surfaces friendly error without calling Firebase
        composeRule.onNodeWithTag("auth_forgot_password_button").performScrollTo().performClick()
        repeat(10) { composeRule.mainClock.advanceTimeBy(60) }
        assertEquals(0, resetCalls)
        composeRule.onNodeWithTag("auth_error_banner")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("Enter your valid email", substring = true)

        // Enter valid email and trigger password reset
        composeRule.onNodeWithTag("auth_email_input").performScrollTo().performTextInput("ananya@nirapad.in")
        composeRule.onNodeWithTag("auth_forgot_password_button").performScrollTo().performClick()
        composeRule.mainClock.advanceTimeBy(100)
        assertEquals(1, resetCalls)
        assertEquals("ananya@nirapad.in", resetEmailArg)

        pendingOnSent?.invoke("Password reset link sent to ananya@nirapad.in. Check your inbox.")
        repeat(4) { composeRule.mainClock.advanceTimeBy(100) }
        composeRule.onNodeWithTag("auth_info_banner")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("Password reset link sent to ananya@nirapad.in", substring = true)
    }

    @Test
    fun googleSignInButton_blocksDoubleTaps_showsLoadingErrorShakeAndVerifiedSuccess() {
        var successCount = 0
        var googleCalls = 0
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
                        googleCalls++
                        pendingOnSuccess = onSuccess
                        pendingOnError = onError
                        pendingOnCancelled = onCancelled
                    }
                )
            }
        }
        advanceRevealAnimations()

        // Click Continue with Google -> enters loading state and disables controls against double-tap
        composeRule.onNodeWithTag("google_sign_in_button").performScrollTo().performClick()
        repeat(3) { composeRule.mainClock.advanceTimeBy(100) }
        composeRule.onNodeWithText("Signing in with Google...").assertIsDisplayed()
        composeRule.onNodeWithTag("google_sign_in_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("auth_email_sign_in_button").assertIsNotEnabled()
        assertEquals(1, googleCalls)

        // Cancelled flow returns cleanly to idle without showing an error banner
        pendingOnCancelled?.invoke()
        repeat(3) { composeRule.mainClock.advanceTimeBy(100) }
        composeRule.onNodeWithText("Continue with Google").assertIsDisplayed()
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

    @Test
    fun authManager_mapsCommonFirebaseAuthExceptionsToFriendlyMessages() {
        val cases = listOf(
            "ERROR_USER_NOT_FOUND" to "No account found",
            "ERROR_WRONG_PASSWORD" to "Incorrect password",
            "ERROR_INVALID_CREDENTIAL" to "Incorrect email or password",
            "ERROR_EMAIL_ALREADY_IN_USE" to "already exists",
            "ERROR_OPERATION_NOT_ALLOWED" to "disabled on Firebase",
            "ERROR_NETWORK_REQUEST_FAILED" to "Network unavailable"
        )
        cases.forEach { (code, expectedSubstring) ->
            val msg = AuthManager.mapFirebaseAuthError(FirebaseAuthException(code, "raw $code"))
            assertTrue("Expected '$expectedSubstring' in '$msg'", msg.contains(expectedSubstring, ignoreCase = true))
        }
    }
}
