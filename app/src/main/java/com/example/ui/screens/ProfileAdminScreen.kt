package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.cache.MapTileCache
import com.example.data.cache.MapTileCacheStats
import com.example.data.model.AuditLog
import com.example.data.model.EmergencyContact
import com.example.data.model.SafetyReport
import com.example.data.model.UserProfile
import com.example.localization.AppLang
import com.example.localization.NirapadCopy
import com.example.ui.theme.NirapadBorderSubtle
import com.example.ui.theme.NirapadCautionContainer
import com.example.ui.theme.NirapadEmergencyRed
import com.example.ui.theme.NirapadNavyDark
import com.example.ui.theme.NirapadPositiveContainer
import com.example.ui.theme.NirapadPositiveGreen
import com.example.ui.theme.NirapadPrimaryTeal
import com.example.ui.theme.NirapadPrimaryTealContainer
import com.example.ui.theme.NirapadSurface
import com.example.ui.theme.NirapadTextSecondary

@Composable
fun ProfileAdminScreen(
    copy: NirapadCopy,
    profile: UserProfile?,
    contacts: List<EmergencyContact>,
    language: AppLang,
    isAdmin: Boolean,
    adminReports: List<SafetyReport>,
    auditLogs: List<AuditLog>,
    onSaveContact: (String, String, String, String, String, Boolean, Boolean, Boolean) -> Unit,
    onDeleteContact: (String) -> Unit,
    onOpenTrustedContacts: () -> Unit,
    onUpdateSettings: (AppLang, Long, Long, String) -> Unit,
    onPurgeHistory: () -> Unit,
    onModerateReport: (String, String, String) -> Unit,
    onOpenModerationWorkspace: () -> Unit = {},
    onAddVerifiedFacility: (String, String, String, String, String) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    tileCacheStats: MapTileCacheStats = MapTileCacheStats(),
    onPrefetchOfflineMap: () -> Unit = {},
    onClearMapTileCache: () -> Unit = {}
) {
    var showAddContact by remember { mutableStateOf(false) }
    var editingContact by remember { mutableStateOf<EmergencyContact?>(null) }
    var showAdminWorkspace by remember { mutableStateOf(false) }

    var displayName by remember(profile?.displayName) { mutableStateOf(profile?.displayName ?: "Traveller") }
    var thresholdMeters by remember(profile?.deviationThresholdMeters) { mutableLongStateOf(profile?.deviationThresholdMeters ?: 250L) }
    var retentionDays by remember(profile?.retentionDays) { mutableLongStateOf(profile?.retentionDays ?: 30L) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Profile Header
        Card(
            colors = CardDefaults.cardColors(containerColor = NirapadSurface),
            border = BorderStroke(1.dp, NirapadBorderSubtle),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = NirapadPrimaryTeal, modifier = Modifier.size(28.dp))
                        Column {
                            Text(profile?.displayName ?: "Traveller", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(profile?.email.orEmpty(), style = MaterialTheme.typography.bodySmall, color = NirapadTextSecondary)
                        }
                    }
                    OutlinedButton(
                        onClick = onSignOut,
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("sign_out_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Sign Out")
                    }
                }
            }
        }

        // Trusted Contacts Section (FR-02)
        Card(
            colors = CardDefaults.cardColors(containerColor = NirapadSurface),
            border = BorderStroke(1.dp, NirapadBorderSubtle),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Trusted Contacts (${contacts.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onOpenTrustedContacts,
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("manage_trusted_contacts_button")
                        ) {
                            Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(copy.manageContactsCta)
                        }
                        Button(
                            onClick = { showAddContact = true },
                            colors = ButtonDefaults.buttonColors(containerColor = NirapadPrimaryTeal),
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("add_contact_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add")
                        }
                    }
                }

                if (contacts.isEmpty()) {
                    Text(
                        "Add at least one trusted contact with a phone number or email for live journey sharing and SOS notifications.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NirapadTextSecondary
                    )
                } else {
                    contacts.forEach { c ->
                        Surface(
                            color = NirapadPrimaryTealContainer.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(c.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        if (c.verified) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = "Verified contact", tint = NirapadPositiveGreen, modifier = Modifier.size(15.dp))
                                        }
                                    }
                                    if (c.resolvedPhone.isNotBlank()) {
                                        Text("Phone: ${c.resolvedPhone}", style = MaterialTheme.typography.bodySmall, color = NirapadNavyDark)
                                    }
                                    if (c.resolvedEmail.isNotBlank()) {
                                        Text("Email: ${c.resolvedEmail}", style = MaterialTheme.typography.bodySmall, color = NirapadNavyDark)
                                    }
                                    Text(
                                        text = "Start share: ${if (c.notifyOnStart) "ON" else "OFF"} • SOS: ${if (c.notifyOnSos) "ON" else "OFF"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NirapadTextSecondary
                                    )
                                }
                                Row {
                                    IconButton(
                                        onClick = { editingContact = c },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit ${c.name}", tint = NirapadPrimaryTeal)
                                    }
                                    IconButton(
                                        onClick = { onDeleteContact(c.id) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete ${c.name}", tint = NirapadEmergencyRed)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Language, Deviation Threshold & Privacy Controls
        Card(
            colors = CardDefaults.cardColors(containerColor = NirapadSurface),
            border = BorderStroke(1.dp, NirapadBorderSubtle),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Safety & Privacy Preferences", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Language / भाषा", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLang.entries.forEach { lang ->
                        FilterChip(
                            selected = language == lang,
                            onClick = { onUpdateSettings(lang, thresholdMeters, retentionDays, displayName) },
                            label = { Text(lang.label) },
                            modifier = Modifier.testTag("lang_${lang.code}")
                        )
                    }
                }

                Text("Deviation Alert Threshold", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(150L, 250L, 500L).forEach { m ->
                        FilterChip(
                            selected = thresholdMeters == m,
                            onClick = {
                                thresholdMeters = m
                                onUpdateSettings(language, m, retentionDays, displayName)
                            },
                            label = { Text("${m}m") }
                        )
                    }
                }

                Text("Location Retention Window", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7L, 30L, 90L).forEach { d ->
                        FilterChip(
                            selected = retentionDays == d,
                            onClick = {
                                retentionDays = d
                                onUpdateSettings(language, thresholdMeters, d, displayName)
                            },
                            label = { Text("${d} days") }
                        )
                    }
                }

                Button(
                    onClick = { onUpdateSettings(language, thresholdMeters, retentionDays, displayName) },
                    colors = ButtonDefaults.buttonColors(containerColor = NirapadPrimaryTeal),
                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
                ) {
                    Text("Save Preferences")
                }

                OutlinedButton(
                    onClick = onPurgeHistory,
                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("purge_history_button")
                ) {
                    Text("Purge Completed Location Breadcrumbs")
                }
            }
        }

        // Offline Map Tile Cache (DataStore)
        Card(
            colors = CardDefaults.cardColors(containerColor = NirapadSurface),
            border = BorderStroke(1.dp, NirapadBorderSubtle),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val sizeKb = if (tileCacheStats.totalBytes == 0L) {
                0L
            } else {
                (tileCacheStats.totalBytes / 1024L).coerceAtLeast(1L)
            }
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Map, contentDescription = null, tint = NirapadPrimaryTeal)
                        Text(
                            "Offline Map Cache",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Surface(
                        color = NirapadPrimaryTealContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${tileCacheStats.cachedTileCount}/${MapTileCache.DEFAULT_MAX_CACHED_TILES} tiles",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = NirapadNavyDark,
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("map_cache_count_text")
                        )
                    }
                }
                Text(
                    "Map tiles around your GPS position and planned routes are stored locally in DataStore ($sizeKb KB used) for poor or zero-connectivity navigation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NirapadTextSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onPrefetchOfflineMap,
                        colors = ButtonDefaults.buttonColors(containerColor = NirapadPrimaryTeal),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("cache_map_area_button")
                    ) {
                        Text("Cache Area")
                    }
                    OutlinedButton(
                        onClick = onClearMapTileCache,
                        enabled = tileCacheStats.cachedTileCount > 0,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("clear_map_cache_button")
                    ) {
                        Text("Clear Cache")
                    }
                }
            }
        }

        // Separate Protected Operations Admin Workspace (FR-12)
        Card(
            colors = CardDefaults.cardColors(containerColor = NirapadSurface),
            border = BorderStroke(1.dp, if (isAdmin) NirapadNavyDark else NirapadBorderSubtle),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = NirapadNavyDark)
                        Text(copy.adminWorkspaceTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    if (isAdmin) {
                        TextButton(onClick = { showAdminWorkspace = !showAdminWorkspace }) {
                            Text(if (showAdminWorkspace) "Close" else "Open")
                        }
                    }
                }
                if (!isAdmin) {
                    Text(
                        "Protected by backend RBAC (/admins/{uid}). Current account has Traveller permissions only.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NirapadTextSecondary
                    )
                    OutlinedButton(
                        onClick = onOpenModerationWorkspace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("open_admin_moderation_workspace_button")
                    ) {
                        Text(copy.openModerationWorkspaceCta)
                    }
                } else {
                    val queueCount = adminReports.count { it.status == "PENDING_REVIEW" || it.status == "FLAGGED" }
                    Text(
                        "${adminReports.size} total safety reports ($queueCount pending or flagged).",
                        style = MaterialTheme.typography.bodySmall,
                        color = NirapadTextSecondary
                    )
                    Button(
                        onClick = onOpenModerationWorkspace,
                        colors = ButtonDefaults.buttonColors(containerColor = NirapadNavyDark),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("open_admin_moderation_workspace_button")
                    ) {
                        Text(copy.openModerationWorkspaceCta)
                    }
                    if (showAdminWorkspace) {
                        AdminWorkspaceSection(
                            reports = adminReports,
                            auditLogs = auditLogs,
                            onModerateReport = onModerateReport,
                            onAddVerifiedFacility = onAddVerifiedFacility
                        )
                    }
                }
            }
        }
    }

    if (showAddContact) {
        TrustedContactEditorDialog(
            title = copy.addContactLabel,
            existingContact = null,
            onSave = { name, phone, email, rel, verified, onStart, onSos ->
                onSaveContact("", name, phone, email, rel, verified, onStart, onSos)
                showAddContact = false
            },
            onDismiss = { showAddContact = false }
        )
    }

    editingContact?.let { target ->
        TrustedContactEditorDialog(
            title = copy.editContactLabel,
            existingContact = target,
            onSave = { name, phone, email, rel, verified, onStart, onSos ->
                onSaveContact(target.id, name, phone, email, rel, verified, onStart, onSos)
                editingContact = null
            },
            onDismiss = { editingContact = null }
        )
    }
}

@Composable
private fun AdminWorkspaceSection(
    reports: List<SafetyReport>,
    auditLogs: List<AuditLog>,
    onModerateReport: (String, String, String) -> Unit,
    onAddVerifiedFacility: (String, String, String, String, String) -> Unit
) {
    var facName by remember { mutableStateOf("") }
    var facPhone by remember { mutableStateOf("") }
    var facAddress by remember { mutableStateOf("") }
    var facSource by remember { mutableStateOf("") }
    var facType by remember { mutableStateOf("POLICE") }

    HorizontalDivider(color = NirapadBorderSubtle)
    Text("Safety Reports Moderation Queue", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    val pending = reports.filter { it.status == "PENDING_REVIEW" }
    if (pending.isEmpty()) {
        Text("No pending safety reports.", style = MaterialTheme.typography.bodySmall, color = NirapadTextSecondary)
    } else {
        pending.forEach { report ->
            Surface(color = NirapadCautionContainer, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(report.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("${report.category} • ${report.locationLabel}", style = MaterialTheme.typography.bodySmall)
                    Text(report.description, style = MaterialTheme.typography.bodySmall, color = NirapadTextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onModerateReport(report.id, "FLAGGED", "Flagged for urgent escalation") },
                            modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                        ) {
                            Text("Flag")
                        }
                        Button(
                            onClick = { onModerateReport(report.id, "RESOLVED", "Resolved by Ops Admin") },
                            colors = ButtonDefaults.buttonColors(containerColor = NirapadPositiveGreen),
                            modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                        ) {
                            Text("Resolve")
                        }
                    }
                }
            }
        }
    }

    HorizontalDivider(color = NirapadBorderSubtle)
    Text("Publish Verified Emergency Facility", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf("POLICE", "AMBULANCE", "WOMENS_SUPPORT", "HOSPITAL", "SAFE_HUB").forEach { t ->
            FilterChip(selected = facType == t, onClick = { facType = t }, label = { Text(t) })
        }
    }
    OutlinedTextField(value = facName, onValueChange = { facName = it }, label = { Text("Facility Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = facPhone, onValueChange = { facPhone = it }, label = { Text("Verified Phone") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = facAddress, onValueChange = { facAddress = it }, label = { Text("Address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = facSource, onValueChange = { facSource = it }, label = { Text("Authoritative Source") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Button(
        onClick = {
            onAddVerifiedFacility(facType, facName, facPhone, facAddress, facSource)
            facName = ""; facPhone = ""; facAddress = ""; facSource = ""
        },
        colors = ButtonDefaults.buttonColors(containerColor = NirapadNavyDark),
        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
    ) {
        Text("Publish Verified Facility")
    }

    HorizontalDivider(color = NirapadBorderSubtle)
    Text("Audit Trail (Recent ${auditLogs.take(5).size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    auditLogs.take(5).forEach { log ->
        Surface(color = NirapadPositiveContainer.copy(alpha = 0.4f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text("${log.action} • ${log.outcome}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(log.summary, style = MaterialTheme.typography.bodySmall, color = NirapadTextSecondary)
            }
        }
    }
}
