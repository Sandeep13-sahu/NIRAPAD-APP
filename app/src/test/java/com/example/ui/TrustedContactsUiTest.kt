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
import com.example.data.model.EmergencyContact
import com.example.localization.AppLang
import com.example.localization.NirapadStrings
import com.example.ui.screens.TrustedContactsScreen
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
class TrustedContactsUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun addContact_validatesMissingPhoneAndEmail_andSavesBothWhenProvided() {
        val copy = NirapadStrings.get(AppLang.EN)
        val savedCalls = mutableListOf<EmergencyContact>()

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            var contacts by remember { mutableStateOf<List<EmergencyContact>>(emptyList()) }
            NirapadTheme {
                TrustedContactsScreen(
                    copy = copy,
                    contactsState = UiState.Success(contacts),
                    onSaveContact = { id, name, phone, email, relationship, verified, notifyStart, notifySos ->
                        val record = EmergencyContact(
                            id = id.ifBlank { "contact_${contacts.size + 1}" },
                            name = name,
                            phoneNumber = phone,
                            email = email,
                            contactChannel = phone.ifBlank { email },
                            channelType = if (phone.isNotBlank()) "PHONE" else "EMAIL",
                            relationship = relationship,
                            verified = verified,
                            notifyOnStart = notifyStart,
                            notifyOnSos = notifySos
                        )
                        savedCalls += record
                        contacts = contacts + record
                    },
                    onDeleteContact = {},
                    onNavigateBack = {}
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(150)

        // Empty state shown initially
        composeRule.onNodeWithTag("trusted_contacts_empty_state").assertIsDisplayed()
        composeRule.onNodeWithTag("trusted_contacts_add_button").performClick()
        composeRule.mainClock.advanceTimeBy(150)

        composeRule.onNodeWithTag("trusted_contact_editor_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("contact_name_input").performScrollTo().performTextInput("Meera Sharma")
        composeRule.mainClock.advanceTimeBy(150)

        // Attempting to save without phone or email shows validation error and keeps dialog open
        composeRule.onNodeWithTag("save_contact_button").performClick()
        composeRule.mainClock.advanceTimeBy(150)
        composeRule.onNodeWithTag("contact_editor_error").performScrollTo().assertIsDisplayed()
        assertTrue(savedCalls.isEmpty())

        // Enter phone and email and save
        composeRule.onNodeWithTag("contact_phone_input").performScrollTo().performTextInput("+91 9876543210")
        composeRule.onNodeWithTag("contact_email_input").performScrollTo().performTextInput("meera@example.com")
        composeRule.mainClock.advanceTimeBy(150)
        composeRule.onNodeWithTag("save_contact_button").performClick()
        composeRule.mainClock.advanceTimeBy(200)

        assertEquals(1, savedCalls.size)
        assertEquals("Meera Sharma", savedCalls.first().name)
        assertEquals("+91 9876543210", savedCalls.first().phoneNumber)
        assertEquals("meera@example.com", savedCalls.first().email)

        composeRule.onNodeWithTag("trusted_contact_card_contact_1").assertIsDisplayed()
        composeRule.onNodeWithText("+91 9876543210").assertIsDisplayed()
        composeRule.onNodeWithText("meera@example.com").assertIsDisplayed()
    }

    @Test
    fun editContact_updatesPhoneAndEmail_andDeleteRequiresConfirmation() {
        val copy = NirapadStrings.get(AppLang.EN)
        val initial = EmergencyContact(
            id = "c_1",
            name = "Asha Verma",
            phoneNumber = "+91 9999900000",
            email = "asha.old@example.com",
            contactChannel = "+91 9999900000",
            channelType = "PHONE",
            relationship = "Family",
            verified = true,
            notifyOnStart = true,
            notifyOnSos = true
        )
        val deletedIds = mutableListOf<String>()

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            var contacts by remember { mutableStateOf(listOf(initial)) }
            NirapadTheme {
                TrustedContactsScreen(
                    copy = copy,
                    contactsState = UiState.Success(contacts),
                    onSaveContact = { id, name, phone, email, relationship, verified, notifyStart, notifySos ->
                        contacts = contacts.map { existing ->
                            if (existing.id == id) {
                                existing.copy(
                                    name = name,
                                    phoneNumber = phone,
                                    email = email,
                                    contactChannel = phone.ifBlank { email },
                                    relationship = relationship,
                                    verified = verified,
                                    notifyOnStart = notifyStart,
                                    notifyOnSos = notifySos
                                )
                            } else {
                                existing
                            }
                        }
                    },
                    onDeleteContact = { contactId ->
                        deletedIds += contactId
                        contacts = contacts.filterNot { it.id == contactId }
                    },
                    onNavigateBack = {}
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(150)

        // Open edit dialog
        composeRule.onNodeWithTag("edit_contact_c_1").performClick()
        composeRule.mainClock.advanceTimeBy(150)
        composeRule.onNodeWithTag("trusted_contact_editor_dialog").assertIsDisplayed()

        // Replace phone and email
        composeRule.onNodeWithTag("contact_phone_input").performScrollTo().performTextReplacement("+91 9123456789")
        composeRule.onNodeWithTag("contact_email_input").performScrollTo().performTextReplacement("asha.new@example.com")
        composeRule.mainClock.advanceTimeBy(150)
        composeRule.onNodeWithTag("save_contact_button").performClick()
        composeRule.mainClock.advanceTimeBy(200)

        composeRule.onNodeWithText("+91 9123456789").assertIsDisplayed()
        composeRule.onNodeWithText("asha.new@example.com").assertIsDisplayed()

        // Remove contact with confirmation dialog
        composeRule.onNodeWithTag("delete_contact_c_1").performClick()
        composeRule.mainClock.advanceTimeBy(150)
        composeRule.onNodeWithTag("delete_contact_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_delete_contact_button").performClick()
        composeRule.mainClock.advanceTimeBy(200)

        assertEquals(listOf("c_1"), deletedIds)
        composeRule.onNodeWithTag("trusted_contacts_empty_state").assertIsDisplayed()
    }
}
