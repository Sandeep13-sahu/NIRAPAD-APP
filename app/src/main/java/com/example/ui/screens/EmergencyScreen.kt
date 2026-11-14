package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.DataProvenance
import com.example.data.model.EmergencyFacility
import com.example.data.model.SafetyReport
import com.example.data.model.SosSession
import com.example.data.service.LocationReading
import com.example.data.service.RoutingAndSafetyEngine
import com.example.localization.NirapadCopy
import com.example.ui.components.DeliberateSosButton
import com.example.ui.components.ProvenanceChip
import com.example.ui.components.SosSignalBeacon
import com.example.ui.theme.NirapadBorderSubtle
import com.example.ui.theme.NirapadCautionContainer
import com.example.ui.theme.NirapadEmergencyContainer
import com.example.ui.theme.NirapadEmergencyRed
import com.example.ui.theme.NirapadNavyDark
import com.example.ui.theme.NirapadPositiveContainer
import com.example.ui.theme.NirapadPositiveGreen
import com.example.ui.theme.NirapadPrimaryTeal
import com.example.ui.theme.NirapadSurface
import com.example.ui.theme.NirapadTextSecondary
import java.util.Locale

@Composable
fun EmergencyScreen(
    copy: NirapadCopy,
    currentLocation: LocationReading,
    sosSessions: List<SosSession>,
    firestoreFacilities: List<EmergencyFacility>,
    myReports: List<SafetyReport>,
    isBackgroundServiceRunning: Boolean,
    broadcastCount: Int,
    lastBroadcastReading: LocationReading?,
    locationPermissionGranted: Boolean,
    onRequestLocationPermission: () -> Unit,
    onRequestSosConfirm: () -> Unit,
    onOpenSosActivation: () -> Unit = {},
    onOpenDirectory: () -> Unit,
    onUpdateSosStatus: (SosSession, String) -> Unit,
    onSubmitReport: (String, String, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val allFacilities = remember(firestoreFacilities) {
        (RoutingAndSafetyEngine.verifiedNationalHelplines + firestoreFacilities).distinctBy { it.phone + it.name }
    }

    var reportCategory by remember { mutableStateOf("POOR_LIGHTING") }
    var reportTitle by remember { mutableStateOf("") }
    var reportDescription by remember { mutableStateOf("") }
    var reportLocation by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Deliberate SOS Panel
        Card(
            colors = CardDefaults.cardColors(containerColor = NirapadEmergencyContainer),
            border = BorderStroke(1.dp, NirapadEmergencyRed.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Emergency SOS & Dispatch", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = NirapadEmergencyRed)
                Text(
                    "Deliberate SOS notifies configured trusted contacts and logs your latest validated coordinates. For immediate police/medical dispatch, tap 112 below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NirapadNavyDark
                )
                DeliberateSosButton(
                    label = "Activate Emergency SOS",
                    onRequestConfirm = onRequestSosConfirm,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = onOpenSosActivation,
                    border = BorderStroke(1.dp, NirapadEmergencyRed.copy(alpha = 0.65f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("open_sos_activation_screen_button")
                ) {
                    Text(copy.openSosActivationCta, color = NirapadEmergencyRed, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Active / Recent SOS Sessions
        if (sosSessions.isNotEmpty()) {
            Text("SOS Audit & Delivery Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = NirapadNavyDark)
            sosSessions.take(4).forEach { sos ->
                val isActive = sos.status == "ACTIVE"
                Card(
                    colors = CardDefaults.cardColors(containerColor = NirapadSurface),
                    border = BorderStroke(1.dp, if (isActive) NirapadEmergencyRed else NirapadBorderSubtle),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Session ${sos.id.take(8)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                text = sos.status,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isActive) NirapadEmergencyRed else NirapadPositiveGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(sos.deliverySummary, style = MaterialTheme.typography.bodySmall, color = NirapadNavyDark)
                        val liveLat = if (isActive && lastBroadcastReading != null) lastBroadcastReading.lat else sos.lat
                        val liveLon = if (isActive && lastBroadcastReading != null) lastBroadcastReading.lon else sos.lon
                        Text(
                            text = String.format(Locale.US, "Location: %s (%.4f, %.4f)", sos.locationLabel, liveLat, liveLon),
                            style = MaterialTheme.typography.bodySmall,
                            color = NirapadTextSecondary
                        )

                        if (isActive) {
                            Surface(
                                color = if (isBackgroundServiceRunning) NirapadPositiveContainer else NirapadCautionContainer,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        SosSignalBeacon(
                                            isActive = true,
                                            color = if (isBackgroundServiceRunning) NirapadPositiveGreen else NirapadEmergencyRed,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = when {
                                                isBackgroundServiceRunning -> "Background Firestore Sync Active ($broadcastCount updates sent)"
                                                locationPermissionGranted -> "Starting background location stream..."
                                                else -> "Grant GPS permission to stream background updates."
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            color = NirapadNavyDark,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    if (!locationPermissionGranted) {
                                        OutlinedButton(
                                            onClick = onRequestLocationPermission,
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Text("Enable GPS")
                                        }
                                    }
                                }
                            }

                            if (sos.shareTokenId.isNotBlank()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(
                                        onClick = { clipboard.setText(AnnotatedString(sos.shareTokenId)) },
                                        modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("copy_sos_token_button")
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Copy Token")
                                    }
                                    Button(
                                        onClick = {
                                            val msg = String.format(
                                                Locale.US,
                                                "NIRAPAD SOS ALERT: Live location active. Token: %s | Coordinates: %.5f, %.5f | https://maps.google.com/?q=%.5f,%.5f",
                                                sos.shareTokenId,
                                                liveLat,
                                                liveLon,
                                                liveLat,
                                                liveLon
                                            )
                                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, msg)
                                            }
                                            context.startActivity(Intent.createChooser(sendIntent, "Share Live SOS Location"))
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = NirapadPrimaryTeal),
                                        modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("share_sos_link_button")
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Share Live")
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { onUpdateSosStatus(sos, "CANCELLED") },
                                    modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("cancel_active_sos_button")
                                ) {
                                    Text("Cancel False Trigger")
                                }
                                Button(
                                    onClick = { onUpdateSosStatus(sos, "RESOLVED") },
                                    colors = ButtonDefaults.buttonColors(containerColor = NirapadPositiveGreen),
                                    modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("resolve_active_sos_button")
                                ) {
                                    Text("I'm Safe (Resolve)")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Verified Emergency Directory
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.LocalPolice, contentDescription = null, tint = NirapadPrimaryTeal)
                Text("Verified Emergency Directory", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = NirapadNavyDark)
            }
            OutlinedButton(
                onClick = onOpenDirectory,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("open_verified_directory_button")
            ) {
                Text(copy.openDirectoryCta)
            }
        }
        allFacilities.forEach { facility ->
            val distM = RoutingAndSafetyEngine.haversineMeters(
                currentLocation.lat,
                currentLocation.lon,
                facility.lat,
                facility.lon
            ).toInt()
            Card(
                colors = CardDefaults.cardColors(containerColor = NirapadSurface),
                border = BorderStroke(1.dp, NirapadBorderSubtle),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ProvenanceChip(provenance = DataProvenance.VERIFIED)
                            Text(facility.type, style = MaterialTheme.typography.labelSmall, color = NirapadTextSecondary)
                        }
                        Text(facility.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = NirapadNavyDark)
                        Text(facility.address, style = MaterialTheme.typography.bodySmall, color = NirapadTextSecondary)
                        Text(
                            text = "Source: ${facility.verifiedSource} • Verified ${facility.verifiedAtLabel}" +
                                if (facility.id.startsWith("nat_")) "" else " • ${String.format("%.1f km", distM / 1000.0)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NirapadPrimaryTeal
                        )
                    }
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${facility.phone}"))
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NirapadNavyDark),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("call_${facility.phone}")
                    ) {
                        Icon(Icons.Default.Call, contentDescription = "Call ${facility.name}", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(facility.phone, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Submit Safety Report (FR-10)
        Card(
            colors = CardDefaults.cardColors(containerColor = NirapadSurface),
            border = BorderStroke(1.dp, NirapadBorderSubtle),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Report, contentDescription = null, tint = NirapadPrimaryTeal)
                    Text("Submit Safety Observation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text(
                    "Submitted reports enter moderation as User-Reported (Pending Review) and are never treated as verified facts until reviewed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NirapadTextSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("POOR_LIGHTING" to "Lighting", "ISOLATED_STRETCH" to "Isolated", "HAZARD" to "Hazard").forEach { (code, label) ->
                        FilterChip(
                            selected = reportCategory == code,
                            onClick = { reportCategory = code },
                            label = { Text(label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = reportTitle,
                    onValueChange = { reportTitle = it },
                    label = { Text("Short title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("report_title_input")
                )
                OutlinedTextField(
                    value = reportLocation,
                    onValueChange = { reportLocation = it },
                    label = { Text("Landmark / street") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = reportDescription,
                    onValueChange = { reportDescription = it },
                    label = { Text("Observation details") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("report_desc_input")
                )
                Button(
                    onClick = {
                        onSubmitReport(reportCategory, reportTitle, reportDescription, reportLocation)
                        reportTitle = ""
                        reportDescription = ""
                        reportLocation = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NirapadPrimaryTeal),
                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("submit_report_button")
                ) {
                    Text("Submit for Moderation")
                }

                if (myReports.isNotEmpty()) {
                    Text("Your Submitted Reports", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    myReports.take(3).forEach { r ->
                        Surface(
                            color = if (r.status == "VERIFIED") NirapadPositiveContainer else NirapadCautionContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(r.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(r.locationLabel, style = MaterialTheme.typography.bodySmall, color = NirapadTextSecondary)
                                }
                                Text(r.status, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
