package com.example.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.ui.screens.AnimatedSplashScreen
import com.example.ui.theme.NirapadTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp")
class AnimatedSplashScreenUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun animatedSplashScreen_revealsShieldLogoTitleAndTagline_thenFinishes() {
        var finishedCount = 0

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            NirapadTheme {
                AnimatedSplashScreen(
                    onSplashFinished = { finishedCount++ },
                    durationMillis = 2000L
                )
            }
        }

        // Phase 1: Shield logo scales & fades in
        composeRule.mainClock.advanceTimeBy(350)
        composeRule.onNodeWithTag("animated_splash_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("splash_shield_logo").assertIsDisplayed()
        assertEquals(0, finishedCount)

        // Phase 2: Brand title & tagline animate in sequentially
        composeRule.mainClock.advanceTimeBy(850)
        composeRule.onNodeWithTag("splash_brand_title").assertIsDisplayed()
        composeRule.onNodeWithText("NIRAPAD").assertIsDisplayed()
        composeRule.onNodeWithTag("splash_tagline").assertIsDisplayed()
        composeRule.onNodeWithText("Your Safe Journey Companion").assertIsDisplayed()
        assertEquals(0, finishedCount)

        // Phase 3: Splash finishes around 2.0s
        composeRule.mainClock.advanceTimeBy(950)
        composeRule.waitForIdle()
        assertEquals(1, finishedCount)
    }
}
