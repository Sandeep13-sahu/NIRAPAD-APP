package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShareLocation
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.firestore.UiState
import com.example.data.model.EmergencyContact
import com.example.data.model.SosSession
import com.example.data.service.LocationReading
import com.example.localization.NirapadCopy
import com.example.ui.components.SosSignalBeacon
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
import java.util.Locale
import kotlin.math.ceil

private val sosAlertPresets = listOf(
    "Immediate Danger",
    "Unsafe Pursuit",
    "Medical Emergency",
    "Stranded / Low Battery"
)

@Composable
fun SosActivationScreen(
    copy: NirapadCopy,
    currentLocation: LocationReading,
    hasLiveGpsFix: Boolean,
    locationPermissionGranted: Boolean,
    contactsState: UiState<List<EmergencyContact>>,
    activeSos: SosSession?,
    isBackgroundServiceRunning: Boolean,
    broadcastCount: Int,
    lastBroadcastReading: LocationReading?,
    autoStartCountdown: Boolean = false,
    isSirenPlaying: Boolean = false,
    isSirenMuted: Boolean = false,
    onToggleSiren: ((Boolean) -> Unit)? = null,
    onRequestLocationPermission: () -> Unit,
    onManageContacts: () -> Unit,
    onActivateSos: (alertReason: String) -> Unit,
    onUpdateSosStatus: (SosSession, String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedPreset by remember { mutableStateOf(sosAlertPresets.first()) }
    var customNote by remember { mutableStateOf("") }
    var localSirenMuted by remember { mutableStateOf(false) }
    val effectiveSirenMuted = if (onToggleSiren != null) isSirenMuted else localSirenMuted
    val sirenEnabled = !effectiveSirenMuted
    var isCountingDown by remember { mutableStateOf(false) }
    var countdownTriggerKey by remember { mutableIntStateOf(0) }

    val resolvedAlertReason = remember(selectedPreset, customNote) {
        val note = customNote.trim()
        if (note.isNotEmpty()) "$selectedPreset - $note".take(80) else selectedPreset
    }

    LaunchedEffect(autoStartCountdown) {
        if (autoStartCountdown && activeSos == null) {
            countdownTriggerKey += 1
            isCountingDown = true
        }
    }

    LaunchedEffect(activeSos?.id) {
        if (activeSos != null) {
            isCountingDown = false
        }
    }

    val allContacts = (contactsState as? UiState.Success)?.data ?: emptyList()
    val sosContacts = remember(allContacts) { allContacts.filter { it.notifyOnSos } }

    if (isCountingDown && activeSos == null) {
        SosCountdownStage(
            copy = copy,
            triggerKey = countdownTriggerKey,
            contactCount = sosContacts.size,
            alertReason = resolvedAlertReason,
            locationPermissionGranted = locationPermissionGranted,
            onComplete = {
                isCountingDown = false
                onActivateSos(resolvedAlertReason)
            },
            onCancel = {
                isCountingDown = false
            },
            modifier = modifier
        )
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(NirapadCanvas)
            .testTag("sos_activation_list"),
        contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Screen Top Bar Header
        item(key = "top_bar") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("sos_screen_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = NirapadNavyDark
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = copy.sosActivationTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = NirapadNavyDark
                    )
                    Text(
                        text = copy.sosActivationSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = NirapadTextSecondary
                    )
                }
                IconButton(
                    onClick = {
                        val nextMuted = !effectiveSirenMuted
                        if (onToggleSiren != null) {
                            onToggleSiren(nextMuted)
                        } else {
                            localSirenMuted = nextMuted
                        }
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("sos_screen_siren_toggle")
                ) {
                    Icon(
                        imageVector = if (sirenEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                        contentDescription = if (sirenEnabled) "Emergency siren armed at max volume" else "Emergency siren muted",
                        tint = if (sirenEnabled) NirapadEmergencyRed else NirapadTextSecondary
                    )
                }
            }
        }

        if (activeSos != null) {
            sosActiveBroadcastItems(
                activeSos = activeSos,
                currentLocation = currentLocation,
                lastBroadcastReading = lastBroadcastReading,
                isBackgroundServiceRunning = isBackgroundServiceRunning,
                broadcastCount = broadcastCount,
                locationPermissionGranted = locationPermissionGranted,
                sosContacts = sosContacts,
                isSirenPlaying = isSirenPlaying,
                isSirenMuted = effectiveSirenMuted,
                onToggleAlertTone = {
                    val nextMuted = !effectiveSirenMuted
                    if (onToggleSiren != null) {
                        onToggleSiren(nextMuted)
                    } else {
                        localSirenMuted = nextMuted
                    }
                },
                onRequestLocationPermission = onRequestLocationPermission,
                onManageContacts = onManageContacts,
                onUpdateSosStatus = onUpdateSosStatus
            )
        } else {
            sosArmedStageItems(
                currentLocation = currentLocation,
                hasLiveGpsFix = hasLiveGpsFix,
                locationPermissionGranted = locationPermissionGranted,
                contactsState = contactsState,
                sosContacts = sosContacts,
                selectedPreset = selectedPreset,
                customNote = customNote,
                isHighPriorityToneEnabled = sirenEnabled,
                onSelectPreset = { selectedPreset = it },
                onCustomNoteChange = { if (it.length <= 60) customNote = it },
                onRequestLocationPermission = onRequestLocationPermission,
                onManageContacts = onManageContacts,
                onStartCountdown = {
                    countdownTriggerKey += 1
                    isCountingDown = true
                }
            )
        }

        item(key = "official_dispatch") {
            DirectEmergencyDispatchStrip()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.sosArmedStageItems(
    currentLocation: LocationReading,
    hasLiveGpsFix: Boolean,
    locationPermissionGranted: Boolean,
    contactsState: UiState<List<EmergencyContact>>,
    sosContacts: List<EmergencyContact>,
    selectedPreset: String,
    customNote: String,
    isHighPriorityToneEnabled: Boolean,
    onSelectPreset: (String) -> Unit,
    onCustomNoteChange: (String) -> Unit,
    onRequestLocationPermission: () -> Unit,
    onManageContacts: () -> Unit,
    onStartCountdown: () -> Unit
) {
    // Tactical Dark Command Hero Card
    item(key = "armed_hero") {
        val radarPulse = rememberInfiniteTransition(label = "sos_armed_radar")
        val ringScale by radarPulse.animateFloat(
            initialValue = 0.88f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "radar_ring_scale"
        )
        Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1D2F)),
        border = BorderStroke(1.5.dp, NirapadEmergencyRed.copy(alpha = 0.7f)),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sos_screen_armed_hero")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF13263E), Color(0xFF0B1624))
                    )
                )
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = NirapadEmergencyRed.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, NirapadEmergencyRed.copy(alpha = 0.65f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = Color(0xFFFF8A80),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (isHighPriorityToneEnabled) "MAX-VOLUME SIREN & GPS ARMED" else "SILENT HIGH-PRIORITY ARMED",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
                Surface(
                    color = Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = "${sosContacts.size} Contact(s)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            // Concentric Tactical SOS Trigger Dial
            Box(
                modifier = Modifier.size(172.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val maxRadius = size.minDimension / 2f
                    drawCircle(
                        color = NirapadEmergencyRed.copy(alpha = 0.14f),
                        radius = maxRadius * ringScale,
                        center = center
                    )
                    drawCircle(
                        color = NirapadEmergencyRed.copy(alpha = 0.35f),
                        radius = maxRadius * 0.84f,
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
                Button(
                    onClick = onStartCountdown,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NirapadEmergencyRed,
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 10.dp),
                    modifier = Modifier
                        .size(132.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("sos_screen_activate_button")
                        .semantics {
                            contentDescription = "Activate High-Priority SOS with 3-second countdown"
                        }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "SOS",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "3s Countdown",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }

            Text(
                text = "Tap to start a 3-second cancellation countdown, then broadcast high-priority alert & immediate GPS to trusted contacts via Firebase.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.84f),
                textAlign = TextAlign.Center
            )
        }
        }
    }

    // Immediate GPS Readiness Card
    item(key = "armed_gps") {
        Card(
        colors = CardDefaults.cardColors(
            containerColor = if (locationPermissionGranted) NirapadPositiveContainer.copy(alpha = 0.6f) else NirapadCautionContainer
        ),
        border = BorderStroke(
            1.dp,
            if (locationPermissionGranted) NirapadPositiveGreen.copy(alpha = 0.45f) else NirapadCautionAmber
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sos_screen_gps_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (locationPermissionGranted) Icons.Default.GpsFixed else Icons.Default.GpsOff,
                contentDescription = null,
                tint = if (locationPermissionGranted) NirapadPositiveGreen else NirapadCautionAmber
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (locationPermissionGranted) {
                        if (hasLiveGpsFix) "GPS Locked • Immediate Sharing Ready" else "GPS Permission Granted • Ready to Share"
                    } else {
                        "Location Permission Required for Live GPS"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = NirapadNavyDark
                )
                Text(
                    text = String.format(
                        Locale.US,
                        "Current Fix: %.5f, %.5f (±%dm)",
                        currentLocation.lat,
                        currentLocation.lon,
                        currentLocation.accuracyMeters.toInt()
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = NirapadTextSecondary
                )
            }
            if (!locationPermissionGranted) {
                Button(
                    onClick = onRequestLocationPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = NirapadCautionAmber),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("sos_screen_grant_gps_button")
                ) {
                    Text("Enable GPS", fontWeight = FontWeight.Bold)
                }
            }
        }
        }
    }

    // High-Priority Alert Situation Preset & Custom Note
    item(key = "armed_context") {
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
            Text(
                text = "Emergency Alert Context",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = NirapadNavyDark
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                sosAlertPresets.forEach { preset ->
                    FilterChip(
                        selected = selectedPreset == preset,
                        onClick = { onSelectPreset(preset) },
                        label = { Text(preset, maxLines = 1) },
                        modifier = Modifier.testTag("sos_preset_${preset.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "_")}")
                    )
                }
            }
            OutlinedTextField(
                value = customNote,
                onValueChange = onCustomNoteChange,
                label = { Text("Optional Note for Contacts (e.g., Gate 2 Metro)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sos_screen_note_input")
            )
        }
        }
    }

    // Configured Trusted Contacts Dispatch Roster
    item(key = "armed_contacts") {
        Card(
        colors = CardDefaults.cardColors(containerColor = NirapadSurface),
        border = BorderStroke(1.dp, NirapadBorderSubtle),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sos_screen_contacts_card")
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
                    Icon(Icons.Default.Group, contentDescription = null, tint = NirapadPrimaryTeal)
                    Text(
                        text = "Trusted Contacts (${sosContacts.size} SOS-Enabled)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = NirapadNavyDark
                    )
                }
                TextButton(
                    onClick = onManageContacts,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("sos_screen_manage_contacts_button")
                ) {
                    Text("Manage")
                }
            }

            when (contactsState) {
                is UiState.Loading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            text = "Syncing trusted contacts from Firebase...",
                            style = MaterialTheme.typography.bodySmall,
                            color = NirapadTextSecondary
                        )
                    }
                }
                is UiState.Error -> {
                    Text(
                        text = contactsState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = NirapadEmergencyRed
                    )
                }
                is UiState.Success -> {
                    if (sosContacts.isEmpty()) {
                        Surface(
                            color = NirapadCautionContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sos_screen_no_contacts_banner")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "No SOS-enabled trusted contacts configured yet.",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = NirapadNavyDark
                                )
                                Text(
                                    text = "You can still activate SOS to generate a live Firebase share token, or add trusted contacts now.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NirapadTextSecondary
                                )
                            }
                        }
                    } else {
                        sosContacts.forEach { contact ->
                            Surface(
                                color = NirapadSurfaceVariant.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("sos_screen_contact_${contact.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = contact.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = NirapadNavyDark
                                            )
                                            if (contact.verified) {
                                                Icon(
                                                    imageVector = Icons.Default.VerifiedUser,
                                                    contentDescription = "Verified contact",
                                                    tint = NirapadPositiveGreen,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                        }
                                        val channelInfo = listOfNotNull(
                                            contact.resolvedPhone.takeIf { it.isNotBlank() },
                                            contact.resolvedEmail.takeIf { it.isNotBlank() }
                                        ).joinToString(" • ")
                                        Text(
                                            text = listOfNotNull(
                                                contact.relationship.takeIf { it.isNotBlank() },
                                                channelInfo.takeIf { it.isNotBlank() }
                                            ).joinToString(" | "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = NirapadTextSecondary
                                        )
                                    }
                                    Surface(
                                        color = NirapadPositiveContainer,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "SOS ARMED",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = NirapadPositiveGreen,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun SosCountdownStage(
    copy: NirapadCopy,
    triggerKey: Int,
    contactCount: Int,
    alertReason: String,
    locationPermissionGranted: Boolean,
    onComplete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val progress = remember(triggerKey) { Animatable(1f) }
    var triggered by remember(triggerKey) { mutableStateOf(false) }
    var cancelled by remember(triggerKey) { mutableStateOf(false) }

    BackHandler(enabled = true) {
        if (!triggered && !cancelled) {
            cancelled = true
            onCancel()
        }
    }

    val pulseTransition = rememberInfiniteTransition(label = "sos_screen_countdown_pulse")
    val haloScale by pulseTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.09f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "countdown_halo_scale"
    )
    val edgeAlpha by pulseTransition.animateFloat(
        initialValue = 0.16f,
        targetValue = 0.40f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "countdown_edge_alpha"
    )

    LaunchedEffect(triggerKey) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        progress.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 3000, easing = LinearEasing)
        )
        if (!triggered && !cancelled) {
            triggered = true
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onComplete()
        }
    }

    val secondsRemaining = ceil((progress.value * 3f).toDouble()).toInt().coerceIn(1, 3)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B192C))
            .testTag("sos_screen_countdown_stage"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = 4.dp.toPx()
            val edgeDepth = 44.dp.toPx()
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(NirapadEmergencyRed.copy(alpha = edgeAlpha), Color.Transparent),
                    startY = 0f,
                    endY = edgeDepth
                ),
                topLeft = Offset.Zero,
                size = Size(size.width, edgeDepth)
            )
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, NirapadEmergencyRed.copy(alpha = edgeAlpha)),
                    startY = size.height - edgeDepth,
                    endY = size.height
                ),
                topLeft = Offset(0f, size.height - edgeDepth),
                size = Size(size.width, edgeDepth)
            )
            drawRect(
                color = NirapadEmergencyRed.copy(alpha = edgeAlpha * 1.9f),
                topLeft = Offset(strokePx / 2f, strokePx / 2f),
                size = Size(size.width - strokePx, size.height - strokePx),
                style = Stroke(width = strokePx)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Surface(
                color = NirapadEmergencyRed.copy(alpha = 0.25f),
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, NirapadEmergencyRed)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.ShareLocation, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Text(
                        text = "HIGH-PRIORITY SOS • 3-SEC COUNTDOWN",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Text(
                text = copy.sosCountdownTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Box(
                modifier = Modifier.size(196.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val baseRadius = (size.minDimension / 2f) - 18f
                    drawCircle(
                        color = NirapadEmergencyRed.copy(alpha = 0.20f),
                        radius = baseRadius * haloScale,
                        center = center
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.14f),
                        radius = baseRadius,
                        center = center,
                        style = Stroke(width = 14f)
                    )
                    val arcInset = 18f
                    drawArc(
                        color = NirapadEmergencyRed,
                        startAngle = -90f,
                        sweepAngle = 360f * progress.value,
                        useCenter = false,
                        topLeft = Offset(arcInset, arcInset),
                        size = Size(size.width - arcInset * 2, size.height - arcInset * 2),
                        style = Stroke(width = 16f, cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = secondsRemaining.toString(),
                        fontSize = 60.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        modifier = Modifier.testTag("sos_screen_countdown_seconds")
                    )
                    Text(
                        text = "SECONDS",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Surface(
                color = Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Alert: $alertReason",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = if (contactCount > 0) {
                            "Immediate Firebase live GPS sharing will notify $contactCount trusted contact(s)."
                        } else {
                            "Live Firebase share token will be created immediately."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                    if (!locationPermissionGranted) {
                        Text(
                            text = "Location permission will be requested to stream live background GPS.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NirapadCautionContainer
                        )
                    }
                }
            }

            Button(
                onClick = {
                    if (!triggered && !cancelled) {
                        cancelled = true
                        onCancel()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = NirapadNavyDark
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("sos_screen_cancel_countdown_button")
            ) {
                Icon(Icons.Default.Close, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = copy.sosCountdownCancel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            OutlinedButton(
                onClick = {
                    if (!triggered && !cancelled) {
                        triggered = true
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onComplete()
                    }
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = BorderStroke(1.5.dp, NirapadEmergencyRed),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("sos_screen_send_now_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = NirapadEmergencyRed)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = copy.sosCountdownSendNow,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun LazyListScope.sosActiveBroadcastItems(
    activeSos: SosSession,
    currentLocation: LocationReading,
    lastBroadcastReading: LocationReading?,
    isBackgroundServiceRunning: Boolean,
    broadcastCount: Int,
    locationPermissionGranted: Boolean,
    sosContacts: List<EmergencyContact>,
    isSirenPlaying: Boolean,
    isSirenMuted: Boolean,
    onToggleAlertTone: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onManageContacts: () -> Unit,
    onUpdateSosStatus: (SosSession, String) -> Unit
) {
    // Active High-Priority Banner Card
    item(key = "active_banner") {
        Card(
        colors = CardDefaults.cardColors(containerColor = NirapadEmergencyRed),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sos_screen_active_stage")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SosSignalBeacon(
                        isActive = true,
                        color = Color.White,
                        coreColor = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                    Column {
                        Text(
                            text = "HIGH-PRIORITY SOS ACTIVE",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "Session ${activeSos.id.take(8)} • Broadcasting live",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
                OutlinedButton(
                    onClick = onToggleAlertTone,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.65f)),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("sos_screen_active_siren_toggle")
                ) {
                    Icon(
                        imageVector = if (!isSirenMuted) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        when {
                            isSirenMuted -> "Muted"
                            isSirenPlaying -> "Siren MAX"
                            else -> "Siren ON"
                        }
                    )
                }
            }

            Surface(
                color = Color.White.copy(alpha = 0.16f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = activeSos.deliverySummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
        }
    }

    // Immediate Firebase Location Telemetry & Share Token Card
    item(key = "active_location") {
        val context = LocalContext.current
        val clipboard = LocalClipboardManager.current
        val liveLat = lastBroadcastReading?.lat ?: activeSos.lat
        val liveLon = lastBroadcastReading?.lon ?: activeSos.lon
        val liveAcc = (lastBroadcastReading?.accuracyMeters ?: currentLocation.accuracyMeters).toInt()
        val shareMsg = remember(activeSos.shareTokenId, liveLat, liveLon) {
            String.format(
                Locale.US,
                "NIRAPAD HIGH-PRIORITY SOS ALERT! Live GPS active. Coordinates: %.5f, %.5f | Map: https://maps.google.com/?q=%.5f,%.5f | Firebase Share Token: %s",
                liveLat,
                liveLon,
                liveLat,
                liveLon,
                activeSos.shareTokenId
            )
        }
        Card(
        colors = CardDefaults.cardColors(containerColor = NirapadSurface),
        border = BorderStroke(1.5.dp, NirapadEmergencyRed.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sos_screen_live_location_card")
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
                Text(
                    text = "Immediate Firebase Live Location",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = NirapadNavyDark
                )
                Surface(
                    color = if (isBackgroundServiceRunning) NirapadPositiveContainer else NirapadCautionContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isBackgroundServiceRunning) "SYNCING ($broadcastCount)" else "FIRESTORE ARMED",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isBackgroundServiceRunning) NirapadPositiveGreen else NirapadCautionAmber,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = String.format(Locale.US, "Coordinates: %.5f, %.5f (±%dm)", liveLat, liveLon, liveAcc),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = NirapadPrimaryTeal
            )

            if (!locationPermissionGranted) {
                Button(
                    onClick = onRequestLocationPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = NirapadCautionAmber),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("sos_screen_enable_gps_active_button")
                ) {
                    Text("Grant GPS Permission for Continuous Background Updates")
                }
            }

            if (activeSos.shareTokenId.isNotBlank()) {
                Surface(
                    color = NirapadSurfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Live Token: ${activeSos.shareTokenId}",
                        style = MaterialTheme.typography.labelMedium,
                        color = NirapadNavyDark,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { clipboard.setText(AnnotatedString(activeSos.shareTokenId)) },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("sos_screen_copy_token_button")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Copy Token")
                    }
                    Button(
                        onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareMsg)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Live SOS Alert"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NirapadPrimaryTeal),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("sos_screen_share_link_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Share Alert")
                    }
                }
            }
        }
        }
    }

    // Trusted Contacts Alert Dispatch Card
    item(key = "active_contacts") {
        val context = LocalContext.current
        val liveLat = lastBroadcastReading?.lat ?: activeSos.lat
        val liveLon = lastBroadcastReading?.lon ?: activeSos.lon
        val shareMsg = remember(activeSos.shareTokenId, liveLat, liveLon) {
            String.format(
                Locale.US,
                "NIRAPAD HIGH-PRIORITY SOS ALERT! Live GPS active. Coordinates: %.5f, %.5f | Map: https://maps.google.com/?q=%.5f,%.5f | Firebase Share Token: %s",
                liveLat,
                liveLon,
                liveLat,
                liveLon,
                activeSos.shareTokenId
            )
        }
        Card(
        colors = CardDefaults.cardColors(containerColor = NirapadSurface),
        border = BorderStroke(1.dp, NirapadBorderSubtle),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sos_screen_active_contacts_card")
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
                Text(
                    text = "Trusted Contacts Notified (${sosContacts.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = NirapadNavyDark
                )
                TextButton(
                    onClick = onManageContacts,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("Edit Roster")
                }
            }

            if (sosContacts.isEmpty()) {
                Text(
                    text = "Share the Firebase token above with someone you trust, or tap 112 below for emergency dispatch.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NirapadTextSecondary
                )
            } else {
                sosContacts.forEach { contact ->
                    Surface(
                        color = NirapadSurfaceVariant.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sos_active_recipient_${contact.id}")
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = contact.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = NirapadNavyDark
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = NirapadPositiveGreen,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Token Shared",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NirapadPositiveGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val phone = contact.resolvedPhone
                                if (phone.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = {
                                            val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone")).apply {
                                                putExtra("sms_body", shareMsg)
                                            }
                                            context.startActivity(smsIntent)
                                        },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(Icons.Default.Sms, contentDescription = "SMS ${contact.name}", modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("SMS")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                                        },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(Icons.Default.Call, contentDescription = "Call ${contact.name}", modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Call")
                                    }
                                }
                                val email = contact.resolvedEmail
                                if (email.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = {
                                            val mailIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email")).apply {
                                                putExtra(Intent.EXTRA_SUBJECT, "URGENT NIRAPAD SOS Live Location")
                                                putExtra(Intent.EXTRA_TEXT, shareMsg)
                                            }
                                            context.startActivity(mailIntent)
                                        },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(Icons.Default.Email, contentDescription = "Email ${contact.name}", modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Email")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }

    // Resolution & Cancellation Controls
    item(key = "active_controls") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = { onUpdateSosStatus(activeSos, "CANCELLED") },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("sos_screen_cancel_active_button")
            ) {
                Text("Cancel False Trigger", fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = { onUpdateSosStatus(activeSos, "RESOLVED") },
                colors = ButtonDefaults.buttonColors(containerColor = NirapadPositiveGreen),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("sos_screen_resolve_active_button")
            ) {
                Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("I'm Safe (Resolve)", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DirectEmergencyDispatchStrip() {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = NirapadEmergencyContainer.copy(alpha = 0.7f)),
        border = BorderStroke(1.dp, NirapadEmergencyRed.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Official Emergency Dispatch",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = NirapadNavyDark
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))) },
                    colors = ButtonDefaults.buttonColors(containerColor = NirapadNavyDark),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("sos_screen_call_112")
                ) {
                    Icon(Icons.Default.Call, contentDescription = "Call 112", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Call 112", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:1091"))) },
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("sos_screen_call_1091")
                ) {
                    Icon(Icons.Default.Call, contentDescription = "Call 1091 Women Helpline", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Call 1091", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
