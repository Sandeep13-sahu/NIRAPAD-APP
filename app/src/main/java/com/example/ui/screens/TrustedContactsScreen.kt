package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.firestore.UiState
import com.example.data.model.EmergencyContact
import com.example.localization.NirapadCopy
import com.example.ui.theme.NirapadBorderSubtle
import com.example.ui.theme.NirapadEmergencyContainer
import com.example.ui.theme.NirapadEmergencyRed
import com.example.ui.theme.NirapadNavyDark
import com.example.ui.theme.NirapadPositiveContainer
import com.example.ui.theme.NirapadPositiveGreen
import com.example.ui.theme.NirapadPrimaryTeal
import com.example.ui.theme.NirapadPrimaryTealContainer
import com.example.ui.theme.NirapadSurface
import com.example.ui.theme.NirapadSurfaceVariant
import com.example.ui.theme.NirapadTextSecondary

private enum class ContactFilter {
    ALL,
    SOS_READY,
    VERIFIED
}

private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

@Composable
fun TrustedContactsScreen(
    copy: NirapadCopy,
    contactsState: UiState<List<EmergencyContact>>,
    onSaveContact: (String, String, String, String, String, Boolean, Boolean, Boolean) -> Unit,
    onDeleteContact: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(ContactFilter.ALL) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingContact by remember { mutableStateOf<EmergencyContact?>(null) }
    var contactPendingDelete by remember { mutableStateOf<EmergencyContact?>(null) }

    val contacts = (contactsState as? UiState.Success)?.data ?: emptyList()
    val filteredContacts = remember(contacts, searchQuery, activeFilter) {
        val q = searchQuery.trim().lowercase()
        contacts.filter { c ->
            val matchesFilter = when (activeFilter) {
                ContactFilter.ALL -> true
                ContactFilter.SOS_READY -> c.notifyOnSos
                ContactFilter.VERIFIED -> c.verified
            }
            val matchesQuery = q.isEmpty() ||
                c.name.lowercase().contains(q) ||
                c.resolvedPhone.lowercase().contains(q) ||
                c.resolvedEmail.lowercase().contains(q) ||
                c.relationship.lowercase().contains(q)
            matchesFilter && matchesQuery
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("trusted_contacts_screen"),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("trusted_contacts_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back",
                            tint = NirapadNavyDark
                        )
                    }
                    Column {
                        Text(
                            text = copy.trustedContactsTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = NirapadNavyDark
                        )
                        Text(
                            text = copy.trustedContactsSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = NirapadTextSecondary
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { showCreateDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = NirapadPrimaryTeal),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("trusted_contacts_add_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add")
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = NirapadSurface),
                border = BorderStroke(1.dp, NirapadBorderSubtle),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Shield,
                                contentDescription = null,
                                tint = NirapadPrimaryTeal,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "${contacts.size} Trusted • ${contacts.count { it.notifyOnSos }} SOS Enabled",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = NirapadNavyDark
                            )
                        }
                        Surface(
                            color = NirapadPositiveContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.CloudDone,
                                    contentDescription = null,
                                    tint = NirapadPositiveGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Cloud Synced",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = NirapadPositiveGreen
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search name, phone, email, or relation") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.testTag("trusted_contacts_clear_search")
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("trusted_contacts_search_input")
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = activeFilter == ContactFilter.ALL,
                            onClick = { activeFilter = ContactFilter.ALL },
                            label = { Text("All (${contacts.size})") }
                        )
                        FilterChip(
                            selected = activeFilter == ContactFilter.SOS_READY,
                            onClick = { activeFilter = ContactFilter.SOS_READY },
                            label = { Text("SOS Alerts (${contacts.count { it.notifyOnSos }})") }
                        )
                        FilterChip(
                            selected = activeFilter == ContactFilter.VERIFIED,
                            onClick = { activeFilter = ContactFilter.VERIFIED },
                            label = { Text("Verified (${contacts.count { it.verified }})") }
                        )
                    }
                }
            }
        }

        when (contactsState) {
            is UiState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp)
                            .testTag("trusted_contacts_loading"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(color = NirapadPrimaryTeal)
                            Text(
                                text = "Syncing trusted contacts...",
                                style = MaterialTheme.typography.bodySmall,
                                color = NirapadTextSecondary
                            )
                        }
                    }
                }
            }

            is UiState.Error -> {
                item {
                    Surface(
                        color = NirapadEmergencyContainer,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, NirapadEmergencyRed.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("trusted_contacts_error")
                    ) {
                        Text(
                            text = contactsState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = NirapadEmergencyRed,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            is UiState.Success -> {
                if (contacts.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = NirapadSurface),
                            border = BorderStroke(1.dp, NirapadBorderSubtle),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("trusted_contacts_empty_state")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    color = NirapadPrimaryTealContainer,
                                    shape = CircleShape,
                                    modifier = Modifier.size(56.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.PersonAdd,
                                            contentDescription = null,
                                            tint = NirapadPrimaryTeal,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "No Trusted Contacts Added",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = NirapadNavyDark
                                )
                                Text(
                                    text = "Add family members or friends with their phone number and email address so they receive live GPS links during Deliberate SOS.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NirapadTextSecondary
                                )
                                Button(
                                    onClick = { showCreateDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = NirapadPrimaryTeal),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("trusted_contacts_empty_add_button")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(copy.addContactLabel)
                                }
                            }
                        }
                    }
                } else if (filteredContacts.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = NirapadSurface),
                            border = BorderStroke(1.dp, NirapadBorderSubtle),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "No contacts match \"$searchQuery\"",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                TextButton(
                                    onClick = {
                                        searchQuery = ""
                                        activeFilter = ContactFilter.ALL
                                    }
                                ) {
                                    Text("Reset filters")
                                }
                            }
                        }
                    }
                } else {
                    items(filteredContacts, key = { it.id }) { contact ->
                        TrustedContactCard(
                            contact = contact,
                            onEdit = { editingContact = contact },
                            onDelete = { contactPendingDelete = contact },
                            onToggleNotifyStart = { enabled ->
                                onSaveContact(
                                    contact.id,
                                    contact.name,
                                    contact.resolvedPhone,
                                    contact.resolvedEmail,
                                    contact.relationship,
                                    contact.verified,
                                    enabled,
                                    contact.notifyOnSos
                                )
                            },
                            onToggleNotifySos = { enabled ->
                                onSaveContact(
                                    contact.id,
                                    contact.name,
                                    contact.resolvedPhone,
                                    contact.resolvedEmail,
                                    contact.relationship,
                                    contact.verified,
                                    contact.notifyOnStart,
                                    enabled
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        TrustedContactEditorDialog(
            title = copy.addContactLabel,
            existingContact = null,
            onSave = { name, phone, email, relationship, verified, notifyOnStart, notifyOnSos ->
                onSaveContact("", name, phone, email, relationship, verified, notifyOnStart, notifyOnSos)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    editingContact?.let { target ->
        TrustedContactEditorDialog(
            title = copy.editContactLabel,
            existingContact = target,
            onSave = { name, phone, email, relationship, verified, notifyOnStart, notifyOnSos ->
                onSaveContact(target.id, name, phone, email, relationship, verified, notifyOnStart, notifyOnSos)
                editingContact = null
            },
            onDismiss = { editingContact = null }
        )
    }

    contactPendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { contactPendingDelete = null },
            modifier = Modifier.testTag("delete_contact_dialog"),
            title = { Text("Remove Trusted Contact?", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "${target.name} will no longer receive journey sharing links or Deliberate SOS alerts.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (target.resolvedPhone.isNotBlank()) {
                        Text(
                            text = "Phone: ${target.resolvedPhone}",
                            style = MaterialTheme.typography.bodySmall,
                            color = NirapadTextSecondary
                        )
                    }
                    if (target.resolvedEmail.isNotBlank()) {
                        Text(
                            text = "Email: ${target.resolvedEmail}",
                            style = MaterialTheme.typography.bodySmall,
                            color = NirapadTextSecondary
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteContact(target.id)
                        contactPendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NirapadEmergencyRed),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("confirm_delete_contact_button")
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { contactPendingDelete = null },
                    modifier = Modifier.testTag("cancel_delete_contact_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrustedContactCard(
    contact: EmergencyContact,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleNotifyStart: (Boolean) -> Unit,
    onToggleNotifySos: (Boolean) -> Unit
) {
    val initials = remember(contact.name) {
        contact.name.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
            .ifBlank { "TC" }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = NirapadSurface),
        border = BorderStroke(1.dp, NirapadBorderSubtle),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("trusted_contact_card_${contact.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(NirapadPrimaryTealContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initials,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = NirapadPrimaryTeal
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = contact.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = NirapadNavyDark
                            )
                            if (contact.verified) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Verified contact",
                                    tint = NirapadPositiveGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        if (contact.relationship.isNotBlank()) {
                            Surface(
                                color = NirapadSurfaceVariant,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = contact.relationship,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NirapadTextSecondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("edit_contact_${contact.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit ${contact.name}",
                            tint = NirapadPrimaryTeal
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("delete_contact_${contact.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remove ${contact.name}",
                            tint = NirapadEmergencyRed
                        )
                    }
                }
            }

            HorizontalDivider(color = NirapadBorderSubtle)

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = "Phone number",
                        tint = if (contact.resolvedPhone.isNotBlank()) NirapadPrimaryTeal else NirapadTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = contact.resolvedPhone.ifBlank { "No phone number added" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = if (contact.resolvedPhone.isBlank()) FontStyle.Italic else FontStyle.Normal,
                        color = if (contact.resolvedPhone.isNotBlank()) NirapadNavyDark else NirapadTextSecondary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = "Email address",
                        tint = if (contact.resolvedEmail.isNotBlank()) NirapadPrimaryTeal else NirapadTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = contact.resolvedEmail.ifBlank { "No email address added" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = if (contact.resolvedEmail.isBlank()) FontStyle.Italic else FontStyle.Normal,
                        color = if (contact.resolvedEmail.isNotBlank()) NirapadNavyDark else NirapadTextSecondary
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilterChip(
                    selected = contact.notifyOnStart,
                    onClick = { onToggleNotifyStart(!contact.notifyOnStart) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.NotificationsActive,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    label = { Text("Journey Start") },
                    modifier = Modifier.testTag("toggle_start_${contact.id}")
                )
                FilterChip(
                    selected = contact.notifyOnSos,
                    onClick = { onToggleNotifySos(!contact.notifyOnSos) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    label = { Text("SOS Alerts") },
                    modifier = Modifier.testTag("toggle_sos_${contact.id}")
                )
            }
        }
    }
}

@Composable
fun TrustedContactEditorDialog(
    title: String,
    existingContact: EmergencyContact?,
    onSave: (String, String, String, String, Boolean, Boolean, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(existingContact) { mutableStateOf(existingContact?.name.orEmpty()) }
    var phoneNumber by remember(existingContact) { mutableStateOf(existingContact?.resolvedPhone.orEmpty()) }
    var email by remember(existingContact) { mutableStateOf(existingContact?.resolvedEmail.orEmpty()) }
    var relationship by remember(existingContact) {
        mutableStateOf(existingContact?.relationship?.ifBlank { "Family" } ?: "Family")
    }
    var verified by remember(existingContact) { mutableStateOf(existingContact?.verified ?: true) }
    var notifyOnStart by remember(existingContact) { mutableStateOf(existingContact?.notifyOnStart ?: true) }
    var notifyOnSos by remember(existingContact) { mutableStateOf(existingContact?.notifyOnSos ?: true) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val isEditMode = existingContact != null

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = NirapadSurface,
            border = BorderStroke(1.dp, NirapadBorderSubtle),
            tonalElevation = 6.dp,
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .widthIn(min = 280.dp, max = 480.dp)
                .fillMaxWidth()
                .testTag("trusted_contact_editor_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = NirapadNavyDark
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        validationError = null
                    },
                    label = { Text("Full Name *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("contact_name_input")
                )

                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = {
                        phoneNumber = it
                        validationError = null
                    },
                    label = { Text("Phone Number") },
                    placeholder = { Text("+91 98765 43210") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("contact_phone_input")
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        validationError = null
                    },
                    label = { Text("Email Address") },
                    placeholder = { Text("contact@example.com") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("contact_email_input")
                )

                OutlinedTextField(
                    value = relationship,
                    onValueChange = { relationship = it },
                    label = { Text("Relationship") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("contact_relationship_input")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Family", "Friend", "Partner", "Colleague").forEach { preset ->
                        FilterChip(
                            selected = relationship.equals(preset, ignoreCase = true),
                            onClick = { relationship = preset },
                            label = { Text(preset) }
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Verified contact", style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = verified,
                        onCheckedChange = { verified = it },
                        modifier = Modifier.testTag("contact_verified_switch")
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Notify on journey start", style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = notifyOnStart,
                        onCheckedChange = { notifyOnStart = it },
                        modifier = Modifier.testTag("contact_notify_start_switch")
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Notify on Deliberate SOS", style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = notifyOnSos,
                        onCheckedChange = { notifyOnSos = it },
                        modifier = Modifier.testTag("contact_notify_sos_switch")
                    )
                }

                validationError?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = NirapadEmergencyRed,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.testTag("contact_editor_error")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("cancel_contact_editor_button")
                    ) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val cleanName = name.trim()
                            val cleanPhone = phoneNumber.trim()
                            val cleanEmail = email.trim()
                            validationError = when {
                                cleanName.isBlank() -> "Enter the contact's full name."
                                cleanPhone.isBlank() && cleanEmail.isBlank() ->
                                    "Enter at least a phone number or an email address."
                                cleanPhone.isNotBlank() && cleanPhone.count { it.isDigit() } < 7 ->
                                    "Enter a valid phone number (at least 7 digits)."
                                cleanEmail.isNotBlank() && !EMAIL_REGEX.matches(cleanEmail) ->
                                    "Enter a valid email address."
                                else -> null
                            }
                            if (validationError == null) {
                                onSave(
                                    cleanName,
                                    cleanPhone,
                                    cleanEmail,
                                    relationship.trim(),
                                    verified,
                                    notifyOnStart,
                                    notifyOnSos
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NirapadPrimaryTeal),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("save_contact_button")
                    ) {
                        Text(if (isEditMode) "Update" else "Save")
                    }
                }
            }
        }
    }
}
