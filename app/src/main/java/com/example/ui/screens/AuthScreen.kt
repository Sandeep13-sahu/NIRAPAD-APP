package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.auth.AuthManager
import com.example.localization.AppLang
import com.example.localization.NirapadStrings
import com.example.ui.theme.NirapadBorderSubtle
import com.example.ui.theme.NirapadCanvas
import com.example.ui.theme.NirapadCautionContainer
import com.example.ui.theme.NirapadEmergencyContainer
import com.example.ui.theme.NirapadEmergencyRed
import com.example.ui.theme.NirapadNavyDark
import com.example.ui.theme.NirapadPositiveGreen
import com.example.ui.theme.NirapadPrimaryTeal
import com.example.ui.theme.NirapadPrimaryTealContainer
import com.example.ui.theme.NirapadSurface
import com.example.ui.theme.NirapadTextSecondary
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class SignInButtonStage {
    IDLE,
    LOADING,
    VERIFIED
}

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    attemptAutoSignInOnLaunch: Boolean = true,
    onGoogleSignInOverride: (
        (
            onSuccess: () -> Unit,
            onError: (String) -> Unit,
            onCancelled: () -> Unit
        ) -> Unit
    )? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var signInStage by remember { mutableStateOf(SignInButtonStage.IDLE) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedLang by remember { mutableStateOf(AppLang.EN) }
    val copy = NirapadStrings.get(selectedLang)

    // Staggered entrance stage progression for hero, pillars, disclaimer, and CTA
    var revealStage by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        revealStage = 1
        delay(90)
        revealStage = 2
        delay(90)
        revealStage = 3
        delay(90)
        revealStage = 4
        delay(90)
        revealStage = 5
    }

    LaunchedEffect(attemptAutoSignInOnLaunch) {
        if (attemptAutoSignInOnLaunch) {
            AuthManager.attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                scope = scope,
                onAuthSuccess = onAuthSuccess,
                onUnauthenticated = { /* Interactive Google button remains available */ }
            )
        }
    }

    // Ambient backdrop & radar pulse animations
    val infiniteTransition = rememberInfiniteTransition(label = "auth_ambient")
    val orbPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_phase"
    )
    val shieldPulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.24f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shield_pulse"
    )
    val shieldHaloAlpha by infiniteTransition.animateFloat(
        initialValue = 0.34f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shield_halo_alpha"
    )

    // Horizontal shake when Google sign-in produces an error
    val errorShakeOffset = remember { Animatable(0f) }
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            errorShakeOffset.snapTo(0f)
            val keyframes = listOf(-18f, 18f, -12f, 12f, -6f, 6f, 0f)
            keyframes.forEach { target ->
                errorShakeOffset.animateTo(
                    targetValue = target,
                    animationSpec = tween(durationMillis = 48, easing = FastOutSlowInEasing)
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NirapadCanvas)
            .drawBehind {
                // Soft animated protective glow orbs
                val topOrbCenter = Offset(
                    x = size.width * (0.22f + 0.08f * cos(orbPhase)),
                    y = size.height * (0.16f + 0.04f * sin(orbPhase))
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(NirapadPrimaryTeal.copy(alpha = 0.18f), Color.Transparent),
                        center = topOrbCenter,
                        radius = size.width * 0.65f
                    ),
                    radius = size.width * 0.65f,
                    center = topOrbCenter
                )
                val bottomOrbCenter = Offset(
                    x = size.width * (0.82f - 0.08f * sin(orbPhase)),
                    y = size.height * (0.84f - 0.05f * cos(orbPhase))
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(NirapadNavyDark.copy(alpha = 0.14f), Color.Transparent),
                        center = bottomOrbCenter,
                        radius = size.width * 0.72f
                    ),
                    radius = size.width * 0.72f,
                    center = bottomOrbCenter
                )
            }
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Animated Language Selector Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = NirapadSurface,
                    border = BorderStroke(1.dp, NirapadBorderSubtle),
                    shadowElevation = 2.dp
                ) {
                    Row(modifier = Modifier.padding(4.dp)) {
                        AppLang.entries.forEach { lang ->
                            val active = lang == selectedLang
                            val pillColor by animateColorAsState(
                                targetValue = if (active) NirapadPrimaryTeal else Color.Transparent,
                                animationSpec = tween(220),
                                label = "lang_pill_${lang.code}"
                            )
                            val textColor by animateColorAsState(
                                targetValue = if (active) Color.White else NirapadNavyDark,
                                animationSpec = tween(220),
                                label = "lang_text_${lang.code}"
                            )
                            Surface(
                                onClick = { selectedLang = lang },
                                color = pillColor,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("auth_language_${lang.code}")
                            ) {
                                Text(
                                    text = lang.label,
                                    color = textColor,
                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Animated Hero Header Card with pulsing Shield emblem
            AnimatedVisibility(
                visible = revealStage >= 1,
                enter = fadeIn(tween(340)) + slideInVertically(
                    initialOffsetY = { -it / 4 },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(NirapadNavyDark, NirapadPrimaryTeal)
                            )
                        )
                        .padding(24.dp)
                        .testTag("auth_hero_card")
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .testTag("auth_shield_emblem"),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .graphicsLayer {
                                        scaleX = shieldPulseScale
                                        scaleY = shieldPulseScale
                                    }
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = shieldHaloAlpha))
                            )
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Shield,
                                    contentDescription = "NIRAPAD safety emblem",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        AnimatedContent(
                            targetState = copy,
                            transitionSpec = {
                                (fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 6 })
                                    .togetherWith(fadeOut(tween(160)))
                            },
                            label = "hero_language_transition"
                        ) { localized ->
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = localized.appTitle,
                                    style = MaterialTheme.typography.displaySmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = localized.tagline,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White.copy(alpha = 0.92f)
                                )
                                Text(
                                    text = localized.signInSubtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }
            }

            // Core Transparency Pillars with staggered bullet reveal
            AnimatedVisibility(
                visible = revealStage >= 2,
                enter = fadeIn(tween(300)) + slideInVertically(
                    initialOffsetY = { it / 5 },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = NirapadSurface),
                    border = BorderStroke(1.dp, NirapadBorderSubtle),
                    shape = RoundedCornerShape(18.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = copy.signInTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = NirapadNavyDark,
                            fontWeight = FontWeight.Bold
                        )
                        AnimatedVisibility(
                            visible = revealStage >= 2,
                            enter = fadeIn(tween(260)) + slideInHorizontally(tween(260)) { -it / 6 }
                        ) {
                            TrustBullet(
                                icon = Icons.Default.LocationOn,
                                text = copy.locationExplainer
                            )
                        }
                        AnimatedVisibility(
                            visible = revealStage >= 3,
                            enter = fadeIn(tween(260)) + slideInHorizontally(tween(260)) { -it / 6 }
                        ) {
                            TrustBullet(
                                icon = Icons.Default.VerifiedUser,
                                text = "Live sharing is opt-in, time-limited, and revocable in one tap."
                            )
                        }
                        AnimatedVisibility(
                            visible = revealStage >= 4,
                            enter = fadeIn(tween(260)) + slideInHorizontally(tween(260)) { -it / 6 }
                        ) {
                            TrustBullet(
                                icon = Icons.Default.Lock,
                                text = "Your contacts, journeys, and SOS logs are protected by per-user security rules."
                            )
                        }
                    }
                }
            }

            // Decision support boundary banner
            AnimatedVisibility(
                visible = revealStage >= 4,
                enter = fadeIn(tween(280)) + expandVertically(tween(280))
            ) {
                Surface(
                    color = NirapadCautionContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = copy.safetyDisclaimer,
                        style = MaterialTheme.typography.bodySmall,
                        color = NirapadNavyDark,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Animated error banner with shake feedback
            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn(tween(220)) + expandVertically(tween(220)),
                exit = fadeOut(tween(180)) + shrinkVertically(tween(180))
            ) {
                Surface(
                    color = NirapadEmergencyContainer,
                    border = BorderStroke(1.dp, NirapadEmergencyRed.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(errorShakeOffset.value.roundToInt(), 0) }
                        .semantics(mergeDescendants = true) {}
                        .testTag("auth_error_banner")
                ) {
                    Text(
                        text = errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = NirapadEmergencyRed,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Spring-animated Interactive Google Sign-In CTA
            AnimatedVisibility(
                visible = revealStage >= 5,
                enter = fadeIn(tween(320)) + scaleIn(
                    initialScale = 0.94f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + slideInVertically(initialOffsetY = { it / 3 })
            ) {
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val buttonScale by animateFloatAsState(
                    targetValue = if (isPressed) 0.97f else 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "google_btn_scale"
                )
                val buttonContainerColor by animateColorAsState(
                    targetValue = when (signInStage) {
                        SignInButtonStage.VERIFIED -> NirapadPositiveGreen
                        else -> NirapadPrimaryTeal
                    },
                    animationSpec = tween(250),
                    label = "google_btn_color"
                )

                val launchGoogleSignIn = {
                    if (signInStage == SignInButtonStage.IDLE) {
                        signInStage = SignInButtonStage.LOADING
                        errorMessage = null
                        val handleSuccess = {
                            signInStage = SignInButtonStage.VERIFIED
                            onAuthSuccess()
                        }
                        val handleError: (String) -> Unit = { msg ->
                            signInStage = SignInButtonStage.IDLE
                            errorMessage = msg
                        }
                        val handleCancelled = {
                            signInStage = SignInButtonStage.IDLE
                        }

                        if (onGoogleSignInOverride != null) {
                            onGoogleSignInOverride(handleSuccess, handleError, handleCancelled)
                        } else {
                            AuthManager.onGoogleSignInClicked(
                                context = context,
                                credentialManager = credentialManager,
                                scope = scope,
                                onAuthSuccess = handleSuccess,
                                onAuthError = handleError,
                                onAuthCancelled = handleCancelled
                            )
                        }
                    }
                }

                Button(
                    onClick = launchGoogleSignIn,
                    enabled = signInStage == SignInButtonStage.IDLE,
                    interactionSource = interactionSource,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonContainerColor,
                        disabledContainerColor = buttonContainerColor.copy(alpha = 0.88f),
                        disabledContentColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 1.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .graphicsLayer {
                            scaleX = buttonScale
                            scaleY = buttonScale
                        }
                        .minimumInteractiveComponentSize()
                        .testTag("google_sign_in_button")
                ) {
                    AnimatedContent(
                        targetState = signInStage,
                        transitionSpec = {
                            fadeIn(tween(180)).togetherWith(fadeOut(tween(140)))
                        },
                        label = "google_btn_stage_content"
                    ) { stage ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            when (stage) {
                                SignInButtonStage.IDLE -> {
                                    GoogleLogoBadge(modifier = Modifier.size(26.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = copy.signInWithGoogle,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                SignInButtonStage.LOADING -> {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.4.dp,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = "Signing in with Google...",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                SignInButtonStage.VERIFIED -> {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "Signed in safely",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
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

@Composable
private fun GoogleLogoBadge(modifier: Modifier = Modifier) {
    Surface(
        color = Color.White,
        shape = CircleShape,
        modifier = modifier
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(5.dp)
        ) {
            val stroke = Stroke(width = size.minDimension * 0.22f, cap = StrokeCap.Butt)
            val inset = stroke.width / 2f
            val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
            val topLeft = Offset(inset, inset)

            // Red top-left arc
            drawArc(
                color = Color(0xFFEA4335),
                startAngle = -155f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )
            // Yellow left arc
            drawArc(
                color = Color(0xFFFBBC05),
                startAngle = 140f,
                sweepAngle = 68f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )
            // Green bottom arc
            drawArc(
                color = Color(0xFF34A853),
                startAngle = 35f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )
            // Blue right arc + crossbar
            drawArc(
                color = Color(0xFF4285F4),
                startAngle = -10f,
                sweepAngle = 55f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )
            drawLine(
                color = Color(0xFF4285F4),
                start = Offset(size.width * 0.5f, size.height * 0.5f),
                end = Offset(size.width * 0.96f, size.height * 0.5f),
                strokeWidth = stroke.width,
                cap = StrokeCap.Butt
            )
        }
    }
}

@Composable
private fun TrustBullet(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(NirapadPrimaryTealContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = NirapadPrimaryTeal, modifier = Modifier.size(16.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = NirapadTextSecondary,
            modifier = Modifier.weight(1f)
        )
    }
}
