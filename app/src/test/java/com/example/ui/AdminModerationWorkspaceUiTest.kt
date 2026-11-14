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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.example.data.firestore.UiState
import com.example.data.model.AuditLog
import com.example.data.model.SafetyReport
import com.example.localization.AppLang
import com.example.localization.NirapadStrings
import com.example.ui.screens.AdminModerationWorkspaceScreen
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
class AdminModerationWorkspaceUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleReports(): List<SafetyReport> = listOf(
        SafetyReport(
            id = "rep_light_1",
            reporterId = "user_alice",
            category = "POOR_LIGHTING",
            title = "Dark underpass near Gate 2",
            description = "Streetlights along the pedestrian subway are completely off after 8 PM.",
            locationLabel = "Hauz Khas Metro Gate 2",
            lat = 28.5433,
            lon = 77.2066,
            status = "PENDING_REVIEW"
        ),
        SafetyReport(
            id = "rep_hazard_2",
            reporterId = "user_meera",
            category = "HAZARD",
            title = "Blocked sidewalk construction",
            description = "Open trench forces pedestrians onto unlit carriageway.",
            locationLabel = "Barakhamba Road",
            lat = 28.6290,
            lon = 77.2245,
            status = "FLAGGED",
            moderationNote = "Escalated to municipal team",
            moderatedBy = "admin_1"
        )
    )

    @Test
    fun adminWorkspace_viewDialog_displaysReportDetailsAndFlagAction() {
        val copy = NirapadStrings.get(AppLang.EN)
        val moderatedActions = mutableListOf<Triple<String, String, String>>()

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            var reports by remember { mutableStateOf(sampleReports()) }
            NirapadTheme {
                AdminModerationWorkspaceScreen(
                    copy = copy,
                    isAdmin = true,
                    reportsState = UiState.Success(reports),
                    auditLogs = emptyList(),
                    onRefreshReports = {},
                    onModerateReport = { reportId, status, note ->
                        moderatedActions += Triple(reportId, status, note)
                        reports = reports.map { r ->
                            if (r.id == reportId) r.copy(status = status, moderationNote = note, moderatedBy = "admin_1")
                            else r
                        }
                    },
                    onNavigateBack = {}
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(150)

        composeRule.onNodeWithTag("moderation_report_card_rep_light_1").assertIsDisplayed()
        composeRule.onNodeWithTag("report_view_button_rep_light_1").performScrollTo().performClick()
        composeRule.mainClock.advanceTimeBy(150)

        composeRule.onNodeWithTag("report_detail_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("Reporter ID: user_alice").assertIsDisplayed()
        composeRule.onNodeWithTag("report_detail_note_input")
            .performScrollTo()
            .performTextReplacement("Urgent patrol review requested")
        composeRule.mainClock.advanceTimeBy(150)
        composeRule.onNodeWithTag("dialog_flag_report_button").performScrollTo().performClick()
        composeRule.mainClock.advanceTimeBy(200)

        assertEquals(
            Triple("rep_light_1", "FLAGGED", "Urgent patrol review requested"),
            moderatedActions.single()
        )
        composeRule.onNodeWithText("Moderator Note: Urgent patrol review requested")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun adminWorkspace_flagResolveFilterAndSearch_workTogether() {
        val copy = NirapadStrings.get(AppLang.EN)
        val moderatedActions = mutableListOf<Triple<String, String, String>>()

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            var reports by remember { mutableStateOf(sampleReports()) }
            NirapadTheme {
                AdminModerationWorkspaceScreen(
                    copy = copy,
                    isAdmin = true,
                    reportsState = UiState.Success(reports),
                    auditLogs = listOf(
                        AuditLog(
                            id = "log_1",
                            actorId = "admin_1",
                            action = "REPORT_MODERATED_FLAGGED",
                            resourceType = "safety_report",
                            resourceId = "rep_hazard_2",
                            outcome = "FLAGGED",
                            summary = "Admin marked report FLAGGED: Escalated to municipal team"
                        )
                    ),
                    onRefreshReports = {},
                    onModerateReport = { reportId, status, note ->
                        moderatedActions += Triple(reportId, status, note)
                        reports = reports.map { r ->
                            if (r.id == reportId) r.copy(status = status, moderationNote = note, moderatedBy = "admin_1")
                            else r
                        }
                    },
                    onNavigateBack = {}
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(150)

        composeRule.onNodeWithTag("report_resolve_button_rep_light_1").performScrollTo().performClick()
        composeRule.mainClock.advanceTimeBy(150)
        composeRule.onNodeWithTag("moderation_action_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("moderation_action_note_input")
            .performScrollTo()
            .performTextReplacement("Lighting restored and verified")
        composeRule.mainClock.advanceTimeBy(150)
        composeRule.onNodeWithTag("moderation_action_confirm_button").performClick()
        composeRule.mainClock.advanceTimeBy(200)

        assertEquals(
            Triple("rep_light_1", "RESOLVED", "Lighting restored and verified"),
            moderatedActions.single()
        )

        composeRule.onNodeWithTag("moderation_filter_resolved").performScrollTo().performClick()
        composeRule.mainClock.advanceTimeBy(150)
        composeRule.onNodeWithTag("moderation_report_card_rep_light_1").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("admin_moderation_search_input").performScrollTo().performTextInput("nonexistent corridor")
        composeRule.mainClock.advanceTimeBy(150)
        composeRule.onNodeWithTag("admin_moderation_empty").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun adminWorkspace_errorAndNonAdminRbacStates_renderCleanly() {
        val copy = NirapadStrings.get(AppLang.EN)
        var refreshCount = 0

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            var isAdmin by remember { mutableStateOf(true) }
            NirapadTheme {
                AdminModerationWorkspaceScreen(
                    copy = copy,
                    isAdmin = isAdmin,
                    reportsState = UiState.Error("PERMISSION_DENIED: Missing admin claim"),
                    auditLogs = emptyList(),
                    onRefreshReports = {
                        refreshCount += 1
                        isAdmin = false
                    },
                    onModerateReport = { _, _, _ -> },
                    onNavigateBack = {}
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(150)

        composeRule.onNodeWithTag("admin_moderation_error").assertIsDisplayed()
        composeRule.onNodeWithTag("admin_moderation_retry_button").performClick()
        composeRule.mainClock.advanceTimeBy(150)

        assertTrue(refreshCount == 1)
        composeRule.onNodeWithText("Admin Access Required").assertIsDisplayed()
    }
}
