package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NirapadDarkTeal
import com.example.ui.theme.NirapadNavyDark
import com.example.ui.theme.NirapadPrimaryTeal
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AnimatedSplashScreen(
    onSplashFinished: () -> Unit,
    durationMillis: Long = 2000L
) {
    // Phase 1: Shield logo fade-in & spring scale-up
    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.60f) }

    // Phase 2: Brand title & tagline staggered reveal
    var showTitle by remember { mutableStateOf(false) }
    var showTagline by remember { mutableStateOf(false) }

    val titleAlpha by animateFloatAsState(
        targetValue = if (showTitle) 1f else 0f,
        animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
        label = "splash_title_alpha"
    )
    val titleTranslationY by animateFloatAsState(
        targetValue = if (showTitle) 0f else 36f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "splash_title_offset"
    )
    val taglineAlpha by animateFloatAsState(
        targetValue = if (showTagline) 1f else 0f,
        animationSpec = tween(durationMillis = 460, easing = FastOutSlowInEasing),
        label = "splash_tagline_alpha"
    )
    val taglineTranslationY by animateFloatAsState(
        targetValue = if (showTagline) 0f else 22f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "splash_tagline_offset"
    )

    // Ambient subtle pulse glow behind shield
    val infiniteTransition = rememberInfiniteTransition(label = "splash_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "splash_pulse_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.36f,
        targetValue = 0.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "splash_pulse_alpha"
    )

    LaunchedEffect(durationMillis) {
        val safeDuration = durationMillis.coerceAtLeast(1000L)
        launch {
            logoAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 460, easing = FastOutSlowInEasing)
            )
        }
        launch {
            logoScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }

        delay(420L)
        showTitle = true

        delay(220L)
        showTagline = true

        val elapsed = 640L
        if (safeDuration > elapsed) {
            delay(safeDuration - elapsed)
        }
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A1929),
                        NirapadNavyDark,
                        Color(0xFF0A4352)
                    )
                )
            )
            .drawBehind {
                // Soft radial protective aura behind center emblem
                val center = Offset(size.width * 0.5f, size.height * 0.44f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            NirapadDarkTeal.copy(alpha = 0.24f),
                            NirapadPrimaryTeal.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = size.minDimension * 0.65f
                    ),
                    radius = size.minDimension * 0.65f,
                    center = center
                )
            }
            .safeDrawingPadding()
            .padding(horizontal = 24.dp)
            .testTag("animated_splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(136.dp)
                    .graphicsLayer {
                        alpha = logoAlpha.value
                        scaleX = logoScale.value
                        scaleY = logoScale.value
                    }
                    .testTag("splash_shield_logo"),
                contentAlignment = Alignment.Center
            ) {
                // Expanding glow ring
                Box(
                    modifier = Modifier
                        .size(118.dp)
                        .graphicsLayer {
                            scaleX = pulseScale
                            scaleY = pulseScale
                        }
                        .clip(CircleShape)
                        .background(NirapadDarkTeal.copy(alpha = pulseAlpha))
                )
                // Crisp outer frosted halo
                Box(
                    modifier = Modifier
                        .size(102.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.10f))
                        .border(1.5.dp, Color.White.copy(alpha = 0.26f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    // Inner solid safety core
                    Box(
                        modifier = Modifier
                            .size(78.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(NirapadPrimaryTeal, NirapadDarkTeal)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "NIRAPAD Shield Logo",
                            tint = Color.White,
                            modifier = Modifier.size(42.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(26.dp))

            Text(
                text = "NIRAPAD",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = 34.sp,
                    letterSpacing = 5.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .graphicsLayer {
                        alpha = titleAlpha
                        translationY = titleTranslationY
                    }
                    .testTag("splash_brand_title")
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = "Your Safe Journey Companion",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.4.sp
                ),
                color = Color(0xFFD9F2F5),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .graphicsLayer {
                        alpha = taglineAlpha
                        translationY = taglineTranslationY
                    }
                    .testTag("splash_tagline")
            )
        }
    }
}
