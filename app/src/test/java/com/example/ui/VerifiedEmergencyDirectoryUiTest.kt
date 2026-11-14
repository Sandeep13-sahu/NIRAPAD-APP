package com.example.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.data.firestore.UiState
import com.example.data.model.EmergencyFacility
import com.example.data.service.LocationReading
import com.example.localization.AppLang
import com.example.localization.NirapadStrings
import com.example.ui.screens.VerifiedEmergencyDirectoryScreen
import com.example.ui.theme.NirapadTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp")
class VerifiedEmergencyDirectoryUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val userLocation = LocationReading(
        lat = 28.6315,
        lon = 77.2167,
        accuracyMeters = 12.0,
        timestampMillis = 1_700_000_000_000L
    )

    private val sampleFacilities = listOf(
        EmergencyFacility(
            id = "fac_police_cp",
            type = "POLICE",
            name = "Connaught Place Police Station",
            phone = "01123412345",
            address = "Baba Kharak Singh Marg, New Delhi",
            lat = 28.6304,
            lon = 77.2137,
            verifiedSource = "Delhi Police Official Directory",
            verifiedAtLabel = "2026-11-10",
            activeStatus = true
        ),
        EmergencyFacility(
            id = "fac_amb_aiims",
            type = "AMBULANCE",
            name = "AIIMS Trauma Ambulance Dispatch",
            phone = "01126594405",
            address = "Ansari Nagar East, New Delhi",
            lat = 28.5672,
            lon = 77.2100,
            verifiedSource = "National Health Mission",
            verifiedAtLabel = "2026-11-12",
            activeStatus = true
        ),
        EmergencyFacility(
            id = "fac_women_sakhi",
            type = "WOMENS_SUPPORT",
            name = "Sakhi One Stop Women Support Center",
            phone = "01123379181",
            address = "Barakhamba Road, New Delhi",
            lat = 28.6290,
            lon = 77.2245,
            verifiedSource = "MWCD Sakhi Directory",
            verifiedAtLabel = "2026-11-12",
            activeStatus = true
        ),
        EmergencyFacility(
            id = "fac_police_far",
            type = "POLICE",
            name = "Sector 62 Outer Police Post",
            phone = "01202400100",
            address = "Sector 62, Noida",
            lat = 28.6279,
            lon = 77.3649,
            verifiedSource = "UP Police Directory",
            verifiedAtLabel = "2026-11-08",
            activeStatus = true
        )
    )

    @Test
    fun filtersByCategoryRadiusAndSearch_usingLocationAndFirestoreEntries() {
        val copy = NirapadStrings.get(AppLang.EN)

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            NirapadTheme {
                VerifiedEmergencyDirectoryScreen(
                    copy = copy,
                    currentLocation = userLocation,
                    hasLiveGpsFix = true,
                    locationPermissionGranted = true,
                    facilitiesState = UiState.Success(sampleFacilities),
                    isAdmin = false,
                    onRequestLocationPermission = {},
                    onRefreshDirectory = { _, _ -> },
                    onAddVerifiedFacility = { _, _, _, _, _ -> },
                    onNavigateBack = {}
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(150)

        // Default radius is 15 km: nearby Police, Ambulance, and Women's Support visible; Noida (>14 km? ~14.5 km -> let's test 5 km radius!)
        composeRule.onNodeWithTag("dir_radius_5").performScrollTo().performClick()
        composeRule.mainClock.advanceTimeBy(150)

        composeRule.onNodeWithTag("directory_facility_card_fac_police_cp").assertIsDisplayed()
        composeRule.onNodeWithTag("directory_facility_card_fac_women_sakhi").assertIsDisplayed()
        composeRule.onNodeWithTag("directory_facility_card_fac_amb_aiims").assertDoesNotExist()
        composeRule.onNodeWithTag("directory_facility_card_fac_police_far").assertDoesNotExist()

        // Expand radius to 15 km -> Ambulance (~7.2 km) appears
        composeRule.onNodeWithTag("dir_radius_15").performScrollTo().performClick()
        composeRule.mainClock.advanceTimeBy(150)

        // Filter by Ambulance category
        composeRule.onNodeWithTag("dir_cat_AMBULANCE").performScrollTo().performClick()
        composeRule.mainClock.advanceTimeBy(150)
        composeRule.onNodeWithTag("directory_facility_card_fac_amb_aiims").assertIsDisplayed()
        composeRule.onNodeWithTag("directory_facility_card_fac_police_cp").assertDoesNotExist()
        composeRule.onNodeWithTag("directory_facility_card_fac_women_sakhi").assertDoesNotExist()

        // Filter by Women's Support category
        composeRule.onNodeWithTag("dir_cat_WOMENS_SUPPORT").performScrollTo().performClick()
        composeRule.mainClock.advanceTimeBy(150)
        composeRule.onNodeWithTag("directory_facility_card_fac_women_sakhi").assertIsDisplayed()
        composeRule.onNodeWithTag("directory_facility_card_fac_amb_aiims").assertDoesNotExist()

        // Switch back to All Services and All Areas, then filter with search query
        composeRule.onNodeWithTag("dir_cat_ALL").performScrollTo().performClick()
        composeRule.onNodeWithTag("dir_radius_all").performScrollTo().performClick()
        composeRule.mainClock.advanceTimeBy(150)
        composeRule.onNodeWithTag("directory_search_input").performScrollTo().performTextInput("Noida")
        composeRule.mainClock.advanceTimeBy(150)

        composeRule.onNodeWithTag("directory_facility_card_fac_police_far").assertIsDisplayed()
        composeRule.onNodeWithTag("directory_facility_card_fac_police_cp").assertDoesNotExist()
    }

    @Test
    fun loadingAndErrorStates_renderGracefully() {
        val copy = NirapadStrings.get(AppLang.EN)

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            NirapadTheme {
                VerifiedEmergencyDirectoryScreen(
                    copy = copy,
                    currentLocation = userLocation,
                    hasLiveGpsFix = false,
                    locationPermissionGranted = false,
                    facilitiesState = UiState.Error("Firestore directory unavailable"),
                    isAdmin = false,
                    onRequestLocationPermission = {},
                    onRefreshDirectory = { _, _ -> },
                    onAddVerifiedFacility = { _, _, _, _, _ -> },
                    onNavigateBack = {}
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(150)

        composeRule.onNodeWithTag("directory_error").assertIsDisplayed()
        composeRule.onNodeWithText("Firestore directory unavailable").assertIsDisplayed()
        composeRule.onNodeWithTag("dir_enable_gps_button").assertIsDisplayed()
    }
}
