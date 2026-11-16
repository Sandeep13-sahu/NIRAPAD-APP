package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.firestore.UiState
import com.example.data.model.AuditLog
import com.example.data.model.SafetyReport
import com.example.data.model.SafetyReportStatus
import com.example.localization.NirapadCopy
import com.example.ui.components.NirapadOutlinedTextField
import com.example.ui.theme.NirapadBorderSubtle
import com.example.ui.theme.NirapadCanvas
import com.example.ui.theme.NirapadCautionAmber
import com.example.ui.theme.NirapadCautionContainer
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
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

private enum class ModerationFilter(
    val label: String,
    val statusValue: String?,
    val testTag: String
) {
    ALL("All", null, "moderation_filter_all"),
    PENDING_REVIEW("Pending", SafetyReportStatus.PENDING_REVIEW.value, "moderation_filter_pending_review"),
    FLAGGED("Flagged", SafetyReportStatus.FLAGGED.value, "moderation_filter_flagged"),
    VERIFIED("Verified", SafetyReportStatus.VERIFIED.value, "moderation_filter_verified"),
    RESOLVED("Resolved", SafetyReportStatus.RESOLVED.value, "moderation_filter_resolved"),
    DISMISSED("Dismissed", SafetyReportStatus.DISMISSED.value, "moderation_filter_dismissed")
}

private data class ModerationActionPrompt(
    val report: SafetyReport,
    val targetStatus: SafetyReportStatus
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminModerationWorkspaceScreen(
    copy: NirapadCopy,
    isAdmin: Boolean,
    reportsState: UiState<List<SafetyReport>>,
    auditLogs: List<AuditLog>,
    onRefreshReports: () -> Unit,
    onModerateReport: (reportId: String, status: String, note: String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(ModerationFilter.ALL) }
    var inspectedReport by remember { mutableStateOf<SafetyReport?>(null) }
    var pendingActionPrompt by remember { mutableStateOf<ModerationActionPrompt?>(null) }

    val allReports = (reportsState as? UiState.Success)?.data ?: emptyList()

    val pendingCount = remember(allReports) {
        allReports.count { it.status == SafetyReportStatus.PENDING_REVIEW.value }
    }
    val flaggedCount = remember(allReports) {
        allReports.count { it.status == SafetyReportStatus.FLAGGED.value }
    }
    val verifiedCount = remember(allReports) {
        allReports.count { it.status == SafetyReportStatus.VERIFIED.value }
    }
    val resolvedCount = remember(allReports) {
        allReports.count { it.status == SafetyReportStatus.RESOLVED.value }
    }

    val filteredReports = remember(allReports, searchQuery, activeFilter) {
        val q = searchQuery.trim().lowercase(Locale.US)
        allReports.filter { report ->
            val matchesFilter = activeFilter.statusValue == null || report.status == activeFilter.statusValue
            val matchesQuery = q.isEmpty() ||
                report.title.lowercase(Locale.US).contains(q) ||
                report.description.lowercase(Locale.US).contains(q) ||
                report.locationLabel.lowercase(Locale.US).contains(q) ||
                report.category.lowercase(Locale.US).contains(q) ||
                report.moderationNote.lowercase(Locale.US).contains(q)
            matchesFilter && matchesQuery
        }
    }

    val reportAuditLogs = remember(auditLogs) {
        auditLogs.filter { it.resourceType == "safety_report" }.take(6)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NirapadCanvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("admin_moderation_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = NirapadNavyDark
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = copy.adminModerationTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = NirapadNavyDark
                )
                Text(
                    text = copy.adminModerationSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = NirapadTextSecondary
                )
            }
            IconButton(
                onClick = onRefreshReports,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("admin_moderation_refresh_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Sync Firestore reports",
                    tint = NirapadPrimaryTeal
                )
            }
        }

        // RBAC Authorization Status Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isAdmin) NirapadPrimaryTealContainer.copy(alpha = 0.45f) else NirapadCautionContainer
            ),
            border = BorderStroke(
                1.dp,
                if (isAdmin) NirapadPrimaryTeal.copy(alpha = 0.5f) else NirapadCautionAmber
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("admin_moderation_rbac_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (isAdmin) NirapadPrimaryTeal else NirapadCautionAmber),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isAdmin) Icons.Default.AdminPanelSettings else Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = if (isAdmin) "Operations Admin Authorized" else "Admin Access Required",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = NirapadNavyDark
                    )
                    Text(
                        text = if (isAdmin) {
                            "Backed by Firestore /admins/{uid} RBAC. Every moderation change appends an immutable audit log."
                        } else {
                            "Current account has Traveller access only. Sign in with an authorized Operations Admin account to moderate safety reports."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = NirapadTextSecondary
                    )
                }
            }
        }

        if (isAdmin) {
            // Summary KPI Metrics Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModerationMetricCard(
                    label = "Pending",
                    count = pendingCount,
                    accent = NirapadCautionAmber,
                    container = NirapadCautionContainer,
                    selected = activeFilter == ModerationFilter.PENDING_REVIEW,
                    onClick = {
                        activeFilter = if (activeFilter == ModerationFilter.PENDING_REVIEW) {
                            ModerationFilter.ALL
                        } else {
                            ModerationFilter.PENDING_REVIEW
                        }
                    },
                    modifier = Modifier.weight(1f).testTag("metric_pending")
                )
                ModerationMetricCard(
                    label = "Flagged",
                    count = flaggedCount,
                    accent = NirapadEmergencyRed,
                    container = NirapadEmergencyContainer,
                    selected = activeFilter == ModerationFilter.FLAGGED,
                    onClick = {
                        activeFilter = if (activeFilter == ModerationFilter.FLAGGED) {
                            ModerationFilter.ALL
                        } else {
                            ModerationFilter.FLAGGED
                        }
                    },
                    modifier = Modifier.weight(1f).testTag("metric_flagged")
                )
                ModerationMetricCard(
                    label = "Verified",
                    count = verifiedCount,
                    accent = NirapadPrimaryTeal,
                    container = NirapadPrimaryTealContainer,
                    selected = activeFilter == ModerationFilter.VERIFIED,
                    onClick = {
                        activeFilter = if (activeFilter == ModerationFilter.VERIFIED) {
                            ModerationFilter.ALL
                        } else {
                            ModerationFilter.VERIFIED
                        }
                    },
                    modifier = Modifier.weight(1f).testTag("metric_verified")
                )
                ModerationMetricCard(
                    label = "Resolved",
                    count = resolvedCount,
                    accent = NirapadPositiveGreen,
                    container = NirapadPositiveContainer,
                    selected = activeFilter == ModerationFilter.RESOLVED,
                    onClick = {
                        activeFilter = if (activeFilter == ModerationFilter.RESOLVED) {
                            ModerationFilter.ALL
                        } else {
                            ModerationFilter.RESOLVED
                        }
                    },
                    modifier = Modifier.weight(1f).testTag("metric_resolved")
                )
            }

            // Search Input
            NirapadOutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by title, street/location, category, or note") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_moderation_search_input")
            )

            // Status Filter Chips
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ModerationFilter.entries.forEach { filter ->
                    val count = when (filter) {
                        ModerationFilter.ALL -> allReports.size
                        else -> allReports.count { it.status == filter.statusValue }
                    }
                    FilterChip(
                        selected = activeFilter == filter,
                        onClick = { activeFilter = filter },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color(0xFFF1F5F9),
                            labelColor = Color(0xFF0F172A),
                            selectedContainerColor = NirapadPrimaryTealContainer,
                            selectedLabelColor = NirapadNavyDark
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = activeFilter == filter,
                            borderColor = Color(0xFF94A3B8),
                            selectedBorderColor = NirapadPrimaryTeal
                        ),
                        label = { Text("${filter.label} ($count)") },
                        modifier = Modifier.testTag(filter.testTag)
                    )
                }
            }

            // Main Reports State Area
            when (reportsState) {
                is UiState.Loading -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = NirapadSurface),
                        border = BorderStroke(1.dp, NirapadBorderSubtle),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_moderation_loading")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = NirapadPrimaryTeal)
                            Text(
                                "Loading submitted safety reports from Firestore...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = NirapadTextSecondary
                            )
                        }
                    }
                }

                is UiState.Error -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = NirapadEmergencyContainer),
                        border = BorderStroke(1.dp, NirapadEmergencyRed.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_moderation_error")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = NirapadEmergencyRed)
                                Text(
                                    "Could Not Load Safety Reports",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = NirapadEmergencyRed
                                )
                            }
                            Text(
                                reportsState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = NirapadNavyDark
                            )
                            Button(
                                onClick = onRefreshReports,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NirapadEmergencyRed,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("admin_moderation_retry_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Retry")
                            }
                        }
                    }
                }

                is UiState.Success -> {
                    if (filteredReports.isEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = NirapadSurface),
                            border = BorderStroke(1.dp, NirapadBorderSubtle),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_moderation_empty")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = NirapadPrimaryTeal,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = if (allReports.isEmpty()) {
                                        "No submitted safety reports yet"
                                    } else {
                                        "No reports match current filter"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = NirapadNavyDark
                                )
                                Text(
                                    text = if (allReports.isEmpty()) {
                                        "Community safety reports submitted from the Emergency & Reporting screen will appear here in real time."
                                    } else {
                                        "Try clearing your search query or switching back to All reports."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NirapadTextSecondary
                                )
                                if (allReports.isNotEmpty() && (searchQuery.isNotBlank() || activeFilter != ModerationFilter.ALL)) {
                                    OutlinedButton(
                                        onClick = {
                                            searchQuery = ""
                                            activeFilter = ModerationFilter.ALL
                                        },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("admin_moderation_clear_filters_button")
                                    ) {
                                        Text("Clear Filters")
                                    }
                                }
                            }
                        }
                    } else {
                        filteredReports.forEach { report ->
                            SafetyReportModerationCard(
                                report = report,
                                onView = { inspectedReport = report },
                                onFlag = {
                                    pendingActionPrompt = ModerationActionPrompt(
                                        report = report,
                                        targetStatus = SafetyReportStatus.FLAGGED
                                    )
                                },
                                onResolve = {
                                    pendingActionPrompt = ModerationActionPrompt(
                                        report = report,
                                        targetStatus = SafetyReportStatus.RESOLVED
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // Recent Report Moderation Audit Trail
            if (reportAuditLogs.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = NirapadSurface),
                    border = BorderStroke(1.dp, NirapadBorderSubtle),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_moderation_audit_card")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.History, contentDescription = null, tint = NirapadNavyDark)
                            Text(
                                "Recent Report Moderation Audit Trail",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = NirapadNavyDark
                            )
                        }
                        reportAuditLogs.forEach { log ->
                            Surface(
                                color = NirapadSurfaceVariant.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        "${log.action} • ${log.outcome}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = NirapadNavyDark
                                    )
                                    Text(
                                        log.summary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NirapadTextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(104.dp))
    }

    // View Full Safety Report Details & Moderation Dialog
    inspectedReport?.let { currentReport ->
        SafetyReportDetailDialog(
            report = currentReport,
            onDismiss = { inspectedReport = null },
            onRequestStatusAction = { targetStatus, note ->
                onModerateReport(currentReport.id, targetStatus.value, note)
                inspectedReport = null
            }
        )
    }

    // Quick Flag / Resolve Confirmation Dialog with Preset Reasons & Note
    pendingActionPrompt?.let { prompt ->
        ModerationActionDialog(
            report = prompt.report,
            targetStatus = prompt.targetStatus,
            onConfirm = { note ->
                onModerateReport(prompt.report.id, prompt.targetStatus.value, note)
                pendingActionPrompt = null
            },
            onDismiss = { pendingActionPrompt = null }
        )
    }
}

@Composable
private fun ModerationMetricCard(
    label: String,
    count: Int,
    accent: Color,
    container: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) container else NirapadSurface
        ),
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) accent else NirapadBorderSubtle),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = NirapadNavyDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SafetyReportModerationCard(
    report: SafetyReport,
    onView: () -> Unit,
    onFlag: () -> Unit,
    onResolve: () -> Unit
) {
    val status = SafetyReportStatus.fromValue(report.status)
    val style = statusVisualStyle(status)

    Card(
        colors = CardDefaults.cardColors(containerColor = NirapadSurface),
        border = BorderStroke(
            width = if (status == SafetyReportStatus.FLAGGED) 1.5.dp else 1.dp,
            color = if (status == SafetyReportStatus.FLAGGED) NirapadEmergencyRed.copy(alpha = 0.6f) else NirapadBorderSubtle
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("moderation_report_card_${report.id}")
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
                Surface(
                    color = style.container,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = style.icon,
                            contentDescription = null,
                            tint = style.content,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = status.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = style.content
                        )
                    }
                }
                Surface(
                    color = NirapadSurfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = formatCategoryLabel(report.category),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = NirapadNavyDark,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = report.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = NirapadNavyDark
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = NirapadPrimaryTeal,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "${report.locationLabel} (${String.format(Locale.US, "%.4f, %.4f", report.lat, report.lon)})",
                        style = MaterialTheme.typography.labelSmall,
                        color = NirapadPrimaryTeal
                    )
                }
                Text(
                    text = report.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = NirapadTextSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (report.moderationNote.isNotBlank()) {
                Surface(
                    color = style.container.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Moderator Note: ${report.moderationNote}",
                        style = MaterialTheme.typography.labelSmall,
                        color = NirapadNavyDark,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            Text(
                text = "Submitted ${formatTimestampLabel(report.createdAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = NirapadTextSecondary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onView,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NirapadNavyDark),
                    border = BorderStroke(1.dp, Color(0xFF94A3B8)),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("report_view_button_${report.id}")
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("View", maxLines = 1)
                }
                OutlinedButton(
                    onClick = onFlag,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NirapadEmergencyRed),
                    border = BorderStroke(1.dp, NirapadEmergencyRed.copy(alpha = 0.65f)),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("report_flag_button_${report.id}")
                ) {
                    Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Flag", maxLines = 1)
                }
                Button(
                    onClick = onResolve,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NirapadPositiveGreen,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("report_resolve_button_${report.id}")
                ) {
                    Icon(Icons.Default.TaskAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Resolve", color = Color.White, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SafetyReportDetailDialog(
    report: SafetyReport,
    onDismiss: () -> Unit,
    onRequestStatusAction: (SafetyReportStatus, String) -> Unit
) {
    val currentStatus = SafetyReportStatus.fromValue(report.status)
    val style = statusVisualStyle(currentStatus)
    var moderationNote by remember(report.id) { mutableStateOf(report.moderationNote) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = NirapadSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .widthIn(max = 560.dp)
                .testTag("report_detail_dialog")
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Safety Report Details",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = NirapadNavyDark
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("report_detail_close_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close report details")
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(color = style.container, shape = RoundedCornerShape(8.dp)) {
                        Text(
                            text = currentStatus.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = style.content,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                    Surface(color = NirapadSurfaceVariant, shape = RoundedCornerShape(8.dp)) {
                        Text(
                            text = formatCategoryLabel(report.category),
                            style = MaterialTheme.typography.labelMedium,
                            color = NirapadNavyDark,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                Text(
                    text = report.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = NirapadNavyDark
                )

                Text(
                    text = report.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NirapadNavyDark
                )

                Surface(
                    color = NirapadSurfaceVariant.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "Location: ${report.locationLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = NirapadNavyDark
                        )
                        Text(
                            "Coordinates: ${String.format(Locale.US, "%.5f, %.5f", report.lat, report.lon)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NirapadPrimaryTeal
                        )
                        Text(
                            "Reporter ID: ${report.reporterId.ifBlank { "N/A" }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NirapadTextSecondary
                        )
                        Text(
                            "Submitted: ${formatTimestampLabel(report.createdAt)} • Updated: ${formatTimestampLabel(report.updatedAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NirapadTextSecondary
                        )
                        if (report.moderatedBy.isNotBlank()) {
                            Text(
                                "Last Moderated By: ${report.moderatedBy}",
                                style = MaterialTheme.typography.labelSmall,
                                color = NirapadTextSecondary
                            )
                        }
                    }
                }

                HorizontalDivider(color = NirapadBorderSubtle)

                NirapadOutlinedTextField(
                    value = moderationNote,
                    onValueChange = { if (it.length <= 300) moderationNote = it },
                    label = { Text("Moderation Note (Reason / Resolution Summary)") },
                    placeholder = { Text("e.g., Flagged for municipal lighting team or Verified safe") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("report_detail_note_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onRequestStatusAction(
                                SafetyReportStatus.FLAGGED,
                                moderationNote.ifBlank { "Flagged for urgent escalation" }
                            )
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NirapadEmergencyRed),
                        border = BorderStroke(1.dp, NirapadEmergencyRed),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("dialog_flag_report_button")
                    ) {
                        Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Flag")
                    }
                    Button(
                        onClick = {
                            onRequestStatusAction(
                                SafetyReportStatus.RESOLVED,
                                moderationNote.ifBlank { "Resolved by Operations Admin" }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NirapadPositiveGreen,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("dialog_resolve_report_button")
                    ) {
                        Icon(Icons.Default.TaskAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Resolve")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            onRequestStatusAction(
                                SafetyReportStatus.VERIFIED,
                                moderationNote.ifBlank { "Verified by Operations Admin" }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NirapadPrimaryTeal,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("dialog_verify_report_button")
                    ) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Verify")
                    }
                    OutlinedButton(
                        onClick = {
                            onRequestStatusAction(
                                SafetyReportStatus.DISMISSED,
                                moderationNote.ifBlank { "Dismissed after review" }
                            )
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NirapadNavyDark),
                        border = BorderStroke(1.dp, Color(0xFF94A3B8)),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("dialog_dismiss_report_button")
                    ) {
                        Text("Dismiss")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModerationActionDialog(
    report: SafetyReport,
    targetStatus: SafetyReportStatus,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val isFlag = targetStatus == SafetyReportStatus.FLAGGED
    val presets = if (isFlag) {
        listOf(
            "Urgent hazard - escalate to field team",
            "Severe lighting outage - warn commuters",
            "Repeated safety reports in corridor"
        )
    } else {
        listOf(
            "Hazard cleared and verified safe",
            "Street lighting restored by civic team",
            "Patrol unit addressed incident"
        )
    }
    var note by remember(report.id, targetStatus) {
        mutableStateOf(
            report.moderationNote.ifBlank { presets.first() }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = NirapadSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .widthIn(max = 520.dp)
                .testTag("moderation_action_dialog")
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isFlag) Icons.Default.Flag else Icons.Default.TaskAlt,
                        contentDescription = null,
                        tint = if (isFlag) NirapadEmergencyRed else NirapadPositiveGreen
                    )
                    Text(
                        text = if (isFlag) "Flag Safety Report" else "Resolve Safety Report",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = NirapadNavyDark
                    )
                }

                Text(
                    text = "${report.title} (${report.locationLabel})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = NirapadNavyDark
                )

                Text(
                    text = "Quick Reason Presets",
                    style = MaterialTheme.typography.labelMedium,
                    color = NirapadTextSecondary
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    presets.forEach { preset ->
                        FilterChip(
                            selected = note == preset,
                            onClick = { note = preset },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color(0xFFF1F5F9),
                                labelColor = Color(0xFF0F172A),
                                selectedContainerColor = NirapadPrimaryTealContainer,
                                selectedLabelColor = NirapadNavyDark
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = note == preset,
                                borderColor = Color(0xFF94A3B8),
                                selectedBorderColor = NirapadPrimaryTeal
                            ),
                            label = { Text(preset, maxLines = 1) }
                        )
                    }
                }

                NirapadOutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 300) note = it },
                    label = { Text(if (isFlag) "Flag Reason / Escalation Note" else "Resolution Summary") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("moderation_action_note_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("moderation_action_cancel_button")
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { onConfirm(note.ifBlank { presets.first() }) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFlag) NirapadEmergencyRed else NirapadPositiveGreen,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("moderation_action_confirm_button")
                    ) {
                        Text(if (isFlag) "Confirm Flag" else "Confirm Resolve")
                    }
                }
            }
        }
    }
}

private data class StatusVisualStyle(
    val container: Color,
    val content: Color,
    val icon: ImageVector
)

private fun statusVisualStyle(status: SafetyReportStatus): StatusVisualStyle = when (status) {
    SafetyReportStatus.PENDING_REVIEW -> StatusVisualStyle(
        container = NirapadCautionContainer,
        content = NirapadCautionAmber,
        icon = Icons.Default.ReportProblem
    )
    SafetyReportStatus.FLAGGED -> StatusVisualStyle(
        container = NirapadEmergencyContainer,
        content = NirapadEmergencyRed,
        icon = Icons.Default.Flag
    )
    SafetyReportStatus.VERIFIED -> StatusVisualStyle(
        container = NirapadPrimaryTealContainer,
        content = NirapadPrimaryTeal,
        icon = Icons.Default.VerifiedUser
    )
    SafetyReportStatus.RESOLVED -> StatusVisualStyle(
        container = NirapadPositiveContainer,
        content = NirapadPositiveGreen,
        icon = Icons.Default.CheckCircle
    )
    SafetyReportStatus.DISMISSED -> StatusVisualStyle(
        container = NirapadSurfaceVariant,
        content = NirapadTextSecondary,
        icon = Icons.Default.Close
    )
}

private fun formatCategoryLabel(raw: String): String = when (raw.uppercase(Locale.US)) {
    "POOR_LIGHTING" -> "Poor Lighting"
    "HARASSMENT" -> "Harassment"
    "ISOLATED_STRETCH" -> "Isolated Stretch"
    "HAZARD" -> "Hazard"
    else -> "Other"
}

private fun formatTimestampLabel(timestamp: Timestamp?): String {
    if (timestamp == null) return "Just now"
    val formatter = SimpleDateFormat("dd MMM, HH:mm", Locale.US)
    return formatter.format(timestamp.toDate())
}
