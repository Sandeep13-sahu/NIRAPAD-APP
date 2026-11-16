package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColor
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.ShareLocation
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DataProvenance
import com.example.data.model.SafetyTier
import com.example.data.repository.AiAdvisorUiState
import com.example.localization.NirapadCopy
import com.example.ui.theme.NirapadBorderSubtle
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
import com.example.ui.theme.NirapadTextSecondary
import kotlin.math.ceil


@Composable
fun ProvenanceChip(provenance: DataProvenance, modifier: Modifier = Modifier) {
    val (bg, fg, icon) = when (provenance) {
        DataProvenance.VERIFIED -> Triple(NirapadPositiveContainer, NirapadPositiveGreen, Icons.Default.CheckCircle)
        DataProvenance.USER_REPORTED -> Triple(NirapadCautionContainer, NirapadCautionAmber, Icons.Default.ReportProblem)
        DataProvenance.ESTIMATED -> Triple(NirapadPrimaryTealContainer, NirapadPrimaryTeal, Icons.Default.Info)
        DataProvenance.UNAVAILABLE -> Triple(Color(0xFFEDF2F7), NirapadTextSecondary, Icons.AutoMirrored.Filled.HelpOutline)
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
            Text(
                text = provenance.label,
                style = MaterialTheme.typography.labelSmall,
                color = fg,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun SafetyTierBadge(tier: SafetyTier, label: String, modifier: Modifier = Modifier) {
    val (bg, fg, icon) = when (tier) {
        SafetyTier.HIGHER_CONTEXT -> Triple(NirapadPositiveContainer, NirapadPositiveGreen, Icons.Default.Shield)
        SafetyTier.MODERATE_CONTEXT -> Triple(NirapadPrimaryTealContainer, NirapadPrimaryTeal, Icons.Default.Info)
        SafetyTier.LIMITED_DATA -> Triple(NirapadCautionContainer, NirapadCautionAmber, Icons.Default.Warning)
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(15.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = fg,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun DeliberateSosButton(
    label: String,
    onRequestConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onRequestConfirm,
        colors = ButtonDefaults.buttonColors(
            containerColor = NirapadEmergencyRed,
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = 52.dp)
            .testTag("sos_trigger_button")
            .semantics { contentDescription = "Emergency SOS button. Opens confirmation before sending alert." }
    ) {
        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SosSignalBeacon(
    isActive: Boolean,
    color: Color = NirapadEmergencyRed,
    coreColor: Color = color,
    modifier: Modifier = Modifier.size(22.dp)
) {
    val transition = rememberInfiniteTransition(label = "sos_signal_beacon")
    val wave by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1350, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "beacon_wave"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 675, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beacon_core_pulse"
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = size.minDimension / 2f
        if (isActive) {
            drawCircle(
                color = color.copy(alpha = (1f - wave) * 0.55f),
                radius = maxRadius * (0.35f + 0.65f * wave),
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
            val secondaryWave = (wave + 0.5f) % 1f
            drawCircle(
                color = color.copy(alpha = (1f - secondaryWave) * 0.4f),
                radius = maxRadius * (0.35f + 0.65f * secondaryWave),
                center = center
            )
        }
        drawCircle(
            color = coreColor.copy(alpha = if (isActive) pulse else 0.7f),
            radius = maxRadius * 0.36f,
            center = center
        )
    }
}

@Composable
fun ActiveSosPulseOverlay(
    isLiveSharingRunning: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "active_sos_screen_pulse")
    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isLiveSharingRunning) 950 else 1300,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "screen_edge_breath"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("active_sos_pulse_overlay")
            .semantics {
                contentDescription = "Active SOS emergency signal broadcasting indicator"
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val edgeAlpha = 0.10f + 0.16f * breath
            val borderAlpha = 0.48f + 0.47f * breath
            val edgeGlowThickness = (26.dp + 14.dp * breath).toPx()
            val strokeWidth = (3.dp + 2.5.dp * breath).toPx()

            // Top edge red aura
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(NirapadEmergencyRed.copy(alpha = edgeAlpha), Color.Transparent),
                    startY = 0f,
                    endY = edgeGlowThickness
                ),
                topLeft = Offset.Zero,
                size = Size(size.width, edgeGlowThickness)
            )

            // Bottom edge red aura
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, NirapadEmergencyRed.copy(alpha = edgeAlpha)),
                    startY = size.height - edgeGlowThickness,
                    endY = size.height
                ),
                topLeft = Offset(0f, size.height - edgeGlowThickness),
                size = Size(size.width, edgeGlowThickness)
            )

            // Left edge red aura
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(NirapadEmergencyRed.copy(alpha = edgeAlpha * 0.85f), Color.Transparent),
                    startX = 0f,
                    endX = edgeGlowThickness
                ),
                topLeft = Offset.Zero,
                size = Size(edgeGlowThickness, size.height)
            )

            // Right edge red aura
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, NirapadEmergencyRed.copy(alpha = edgeAlpha * 0.85f)),
                    startX = size.width - edgeGlowThickness,
                    endX = size.width
                ),
                topLeft = Offset(size.width - edgeGlowThickness, 0f),
                size = Size(edgeGlowThickness, size.height)
            )

            // Crisp pulsating red perimeter frame
            drawRect(
                color = NirapadEmergencyRed.copy(alpha = borderAlpha),
                topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f),
                size = Size(size.width - strokeWidth, size.height - strokeWidth),
                style = Stroke(width = strokeWidth)
            )
        }
    }
}

@Composable
fun ActiveSosBroadcastBanner(
    title: String,
    subtitle: String,
    isLiveSharingRunning: Boolean,
    broadcastCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "sos_banner_pulse")
    val bannerColor by transition.animateColor(
        initialValue = Color(0xFFB71C1C),
        targetValue = NirapadEmergencyRed,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "banner_bg_pulse"
    )

    Surface(
        color = bannerColor,
        contentColor = Color.White,
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag("active_sos_broadcast_banner")
            .semantics {
                contentDescription = "$title. $subtitle"
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SosSignalBeacon(
                isActive = true,
                color = Color.White,
                coreColor = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    text = if (isLiveSharingRunning) {
                        "Live GPS broadcasting ($broadcastCount updates) • $subtitle"
                    } else {
                        subtitle
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.92f)
                )
            }
            Surface(
                color = Color.White.copy(alpha = 0.22f),
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.55f))
            ) {
                Text(
                    text = "LIVE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun DeliberateSosFab(
    label: String,
    activeLabel: String,
    isSosActive: Boolean,
    isLiveSharingRunning: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pulseTransition = rememberInfiniteTransition(label = "sos_fab_pulse")
    val pulseContainerColor by pulseTransition.animateColor(
        initialValue = Color(0xFFB71C1C),
        targetValue = NirapadEmergencyRed,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sos_fab_color"
    )
    val haloAlpha by pulseTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sos_fab_halo"
    )

    ExtendedFloatingActionButton(
        onClick = onClick,
        containerColor = if (isSosActive) pulseContainerColor else NirapadEmergencyRed,
        contentColor = Color.White,
        shape = RoundedCornerShape(18.dp),
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = if (isSosActive) 12.dp else 8.dp),
        icon = {
            if (isSosActive) {
                SosSignalBeacon(
                    isActive = true,
                    color = Color.White,
                    coreColor = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
            }
        },
        text = {
            Text(
                text = if (isSosActive) activeLabel else label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold
            )
        },
        modifier = modifier
            .then(
                if (isSosActive) {
                    Modifier.border(
                        width = 2.dp,
                        color = Color.White.copy(alpha = haloAlpha),
                        shape = RoundedCornerShape(18.dp)
                    )
                } else {
                    Modifier
                }
            )
            .minimumInteractiveComponentSize()
            .testTag("deliberate_sos_fab")
            .semantics {
                contentDescription = if (isSosActive) {
                    "SOS active and broadcasting. Open emergency live location controls."
                } else {
                    "Quick SOS trigger. Starts a 3-second cancellation countdown before sharing live location."
                }
            }
    )
}

@Composable
fun DeliberateSosCountdownOverlay(
    copy: NirapadCopy,
    contactCount: Int,
    locationPermissionGranted: Boolean,
    onComplete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val progress = remember { Animatable(1f) }
    var triggered by remember { mutableStateOf(false) }
    var cancelled by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        if (!triggered && !cancelled) {
            cancelled = true
            onCancel()
        }
    }

    val pulseTransition = rememberInfiniteTransition(label = "countdown_halo")
    val haloScale by pulseTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_scale"
    )
    val edgePulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.14f,
        targetValue = 0.36f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "countdown_edge_pulse"
    )

    LaunchedEffect(Unit) {
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
            .background(Color(0xEE0B192C))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { /* Intercept background touches while countdown is visible */ }
            .testTag("sos_countdown_overlay"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val edgeDepth = 42.dp.toPx()
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(NirapadEmergencyRed.copy(alpha = edgePulseAlpha), Color.Transparent),
                    startY = 0f,
                    endY = edgeDepth
                ),
                topLeft = Offset.Zero,
                size = Size(size.width, edgeDepth)
            )
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, NirapadEmergencyRed.copy(alpha = edgePulseAlpha)),
                    startY = size.height - edgeDepth,
                    endY = size.height
                ),
                topLeft = Offset(0f, size.height - edgeDepth),
                size = Size(size.width, edgeDepth)
            )
            val borderStroke = 4.dp.toPx()
            drawRect(
                color = NirapadEmergencyRed.copy(alpha = edgePulseAlpha * 2f),
                topLeft = Offset(borderStroke / 2f, borderStroke / 2f),
                size = Size(size.width - borderStroke, size.height - borderStroke),
                style = Stroke(width = borderStroke)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Surface(
                color = NirapadEmergencyRed.copy(alpha = 0.22f),
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, NirapadEmergencyRed.copy(alpha = 0.7f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.ShareLocation, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Text(
                        text = "DELIBERATE SOS • 3-SEC SAFETY CHECK",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = copy.sosCountdownTitle,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            // Animated 3-second circular countdown dial
            Box(
                modifier = Modifier.size(188.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val baseRadius = (size.minDimension / 2f) - 18f

                    drawCircle(
                        color = NirapadEmergencyRed.copy(alpha = 0.18f),
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
                    val arcSize = Size(size.width - arcInset * 2, size.height - arcInset * 2)
                    drawArc(
                        color = NirapadEmergencyRed,
                        startAngle = -90f,
                        sweepAngle = 360f * progress.value,
                        useCenter = false,
                        topLeft = Offset(arcInset, arcInset),
                        size = arcSize,
                        style = Stroke(width = 16f, cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = secondsRemaining.toString(),
                        fontSize = 56.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = "SECONDS",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.78f),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Text(
                text = copy.sosCountdownSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )

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
                        text = if (contactCount > 0) {
                            "Real-time Firestore share token will be activated for $contactCount trusted contact(s)."
                        } else {
                            "Real-time Firestore share token will be activated. Add trusted contacts on Profile or share the token directly."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                    if (!locationPermissionGranted) {
                        Text(
                            text = "Location permission is not granted yet — you will be prompted immediately to start background GPS streaming.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NirapadCautionContainer
                        )
                    }
                    Text(
                        text = "Official local emergency dispatch (112) still requires a direct call from the Emergency screen.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
                        .testTag("sos_countdown_cancel_button")
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
                        if (!triggered) {
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
                        .testTag("sos_countdown_send_now_button")
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = NirapadEmergencyRed)
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
}

@Composable
fun SosConfirmationDialog(
    copy: NirapadCopy,
    contactCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = NirapadEmergencyRed,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(copy.sosConfirmTitle, fontWeight = FontWeight.Bold, color = NirapadNavyDark)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(copy.sosConfirmBody, style = MaterialTheme.typography.bodyMedium)
                Surface(
                    color = NirapadEmergencyContainer,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (contactCount > 0) {
                            "Configured SOS recipients: $contactCount trusted contact(s). Official local dispatch (112) remains your fastest direct call."
                        } else {
                            "No trusted contacts configured yet. Use direct 112 / 1091 call buttons on the Emergency tab immediately."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = NirapadEmergencyRed,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NirapadEmergencyRed,
                    contentColor = Color.White
                ),
                modifier = Modifier.minimumInteractiveComponentSize().testTag("confirm_sos_button")
            ) {
                Text(copy.sosTriggerNow, color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NirapadNavyDark),
                border = BorderStroke(1.dp, Color(0xFF94A3B8)),
                modifier = Modifier.minimumInteractiveComponentSize().testTag("cancel_sos_button")
            ) {
                Text(copy.cancelLabel)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiAdvisorModal(
    copy: NirapadCopy,
    aiState: AiAdvisorUiState,
    onAsk: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var question by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = NirapadPrimaryTeal)
                    Text(copy.aiAdvisorTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close AI advisor")
                }
            }

            Surface(
                color = NirapadCautionContainer,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, NirapadCautionAmber.copy(alpha = 0.35f))
            ) {
                Text(
                    text = copy.aiAdvisoryBanner,
                    style = MaterialTheme.typography.bodySmall,
                    color = NirapadNavyDark,
                    modifier = Modifier.padding(10.dp)
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "Explain why this route was scored this way",
                    "What precautions help when safety data is limited?",
                    "What should I do if I notice a route deviation?"
                ).forEach { prompt ->
                    AssistChip(
                        onClick = {
                            question = prompt
                            onAsk(prompt)
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color(0xFFF1F5F9),
                            labelColor = Color(0xFF0F172A)
                        ),
                        border = AssistChipDefaults.assistChipBorder(
                            enabled = true,
                            borderColor = Color(0xFF94A3B8)
                        ),
                        label = {
                            Text(
                                text = prompt,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF0F172A)
                            )
                        }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NirapadOutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    placeholder = { Text("Ask about route context or journey tips...") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("ai_question_input")
                )
                IconButton(
                    onClick = { onAsk(question) },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NirapadPrimaryTeal)
                        .testTag("ai_send_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Ask AI", tint = Color.White)
                }
            }

            when (aiState) {
                AiAdvisorUiState.Idle -> {
                    Text(
                        text = "Select a prompt above or ask a question about your route factors.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NirapadTextSecondary
                    )
                }
                AiAdvisorUiState.Loading -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Generating advisory explanation...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is AiAdvisorUiState.Streaming -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, NirapadBorderSubtle)
                    ) {
                        Text(
                            text = aiState.text,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
                is AiAdvisorUiState.Success -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, NirapadBorderSubtle)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Advisory Response", style = MaterialTheme.typography.labelMedium, color = NirapadPrimaryTeal, fontWeight = FontWeight.Bold)
                            Text(text = aiState.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                is AiAdvisorUiState.Error -> {
                    Surface(color = NirapadEmergencyContainer, shape = RoundedCornerShape(10.dp)) {
                        Text(
                            text = aiState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = NirapadEmergencyRed,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
