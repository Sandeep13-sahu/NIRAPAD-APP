package com.example.ui.screens

import android.util.Patterns
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
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import com.example.auth.AuthManager
import com.example.localization.AppLang
import com.example.localization.NirapadStrings
import com.example.ui.components.NirapadOutlinedTextField
import com.example.ui.theme.NirapadBorderSubtle
import com.example.ui.theme.NirapadCanvas
import com.example.ui.theme.NirapadEmergencyContainer
import com.example.ui.theme.NirapadEmergencyRed
import com.example.ui.theme.NirapadNavyDark
import com.example.ui.theme.NirapadPositiveContainer
import com.example.ui.theme.NirapadPositiveGreen
import com.example.ui.theme.NirapadPrimaryTeal
import com.example.ui.theme.NirapadSurface
import com.example.ui.theme.NirapadTextSecondary
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class SignInButtonStage {
    IDLE,
    EMAIL_LOADING,
    RESET_LOADING,
    GOOGLE_LOADING,
    EMAIL_VERIFIED,
    GOOGLE_VERIFIED
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
    )? = null,
    onEmailSignInOverride: (
        (
            email: String,
            password: String,
            fullName: String,
            createAccount: Boolean,
            onSuccess: () -> Unit,
            onError: (String) -> Unit
        ) -> Unit
    )? = null,
    onForgotPasswordOverride: (
        (
            email: String,
            onSent: (String) -> Unit,
            onError: (String) -> Unit
        ) -> Unit
    )? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var createAccountMode by remember { mutableStateOf(false) }

    var signInStage by remember { mutableStateOf(SignInButtonStage.IDLE) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var selectedLang by remember { mutableStateOf(AppLang.EN) }
    val copy = NirapadStrings.get(selectedLang)

    val isBusy = signInStage != SignInButtonStage.IDLE

    // Smooth staggered reveal for hero, form card, and privacy footer
    var revealStage by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        revealStage = 1
        delay(80)
        revealStage = 2
        delay(90)
        revealStage = 3
    }

    LaunchedEffect(attemptAutoSignInOnLaunch) {
        if (attemptAutoSignInOnLaunch) {
            AuthManager.attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                scope = scope,
                onAuthSuccess = onAuthSuccess,
                onUnauthenticated = { /* Interactive AuthScreen remains ready */ }
            )
        }
    }

    // Subtle ambient aura + gentle shield halo
    val infiniteTransition = rememberInfiniteTransition(label = "auth_ambient")
    val orbPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_phase"
    )
    val shieldPulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shield_pulse"
    )
    val shieldHaloAlpha by infiniteTransition.animateFloat(
        initialValue = 0.24f,
        targetValue = 0.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shield_halo_alpha"
    )

    // Shake animation for inline auth errors
    val errorShakeOffset = remember { Animatable(0f) }
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            errorShakeOffset.snapTo(0f)
            val keyframes = listOf(-16f, 16f, -10f, 10f, -5f, 5f, 0f)
            keyframes.forEach { target ->
                errorShakeOffset.animateTo(
                    targetValue = target,
                    animationSpec = tween(durationMillis = 45, easing = FastOutSlowInEasing)
                )
            }
        }
    }

    val submitEmailAuth = submit@{
        if (signInStage != SignInButtonStage.IDLE) return@submit
        focusManager.clearFocus()
        errorMessage = null
        infoMessage = null

        val trimmedEmail = email.trim()
        val trimmedName = fullName.trim()

        if (trimmedEmail.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            errorMessage = "Please enter a valid email address."
            return@submit
        }
        if (password.length < 6) {
            errorMessage = "Password must be at least 6 characters."
            return@submit
        }

        signInStage = SignInButtonStage.EMAIL_LOADING

        val handleSuccess = {
            signInStage = SignInButtonStage.EMAIL_VERIFIED
            onAuthSuccess()
        }
        val handleError: (String) -> Unit = { msg ->
            signInStage = SignInButtonStage.IDLE
            errorMessage = msg
        }

        if (onEmailSignInOverride != null) {
            onEmailSignInOverride(
                trimmedEmail,
                password,
                trimmedName,
                createAccountMode,
                handleSuccess,
                handleError
            )
        } else {
            AuthManager.signInWithEmailPassword(
                context = context,
                email = trimmedEmail,
                password = password,
                fullName = trimmedName,
                createAccount = createAccountMode,
                scope = scope,
                onAuthSuccess = handleSuccess,
                onAuthError = handleError,
                onFinished = {
                    if (signInStage == SignInButtonStage.EMAIL_LOADING) {
                        signInStage = SignInButtonStage.IDLE
                    }
                }
            )
        }
    }

    val submitForgotPassword = forgot@{
        if (signInStage != SignInButtonStage.IDLE) return@forgot
        focusManager.clearFocus()
        errorMessage = null
        infoMessage = null

        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            errorMessage = "Enter your valid email address first to receive a password reset link."
            return@forgot
        }

        signInStage = SignInButtonStage.RESET_LOADING

        val handleSent: (String) -> Unit = { msg ->
            signInStage = SignInButtonStage.IDLE
            infoMessage = msg
        }
        val handleError: (String) -> Unit = { msg ->
            signInStage = SignInButtonStage.IDLE
            errorMessage = msg
        }

        if (onForgotPasswordOverride != null) {
            onForgotPasswordOverride(trimmedEmail, handleSent, handleError)
        } else {
            AuthManager.sendPasswordResetEmail(
                email = trimmedEmail,
                scope = scope,
                onResetEmailSent = handleSent,
                onResetError = handleError,
                onFinished = {
                    if (signInStage == SignInButtonStage.RESET_LOADING) {
                        signInStage = SignInButtonStage.IDLE
                    }
                }
            )
        }
    }

    val launchGoogleSignIn = google@{
        if (signInStage != SignInButtonStage.IDLE) return@google
        focusManager.clearFocus()
        signInStage = SignInButtonStage.GOOGLE_LOADING
        errorMessage = null
        infoMessage = null

        val handleSuccess = {
            signInStage = SignInButtonStage.GOOGLE_VERIFIED
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
                onAuthCancelled = handleCancelled,
                onFinished = {
                    if (signInStage == SignInButtonStage.GOOGLE_LOADING) {
                        signInStage = SignInButtonStage.IDLE
                    }
                }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NirapadCanvas)
            .drawBehind {
                val topOrbCenter = Offset(
                    x = size.width * (0.25f + 0.06f * cos(orbPhase)),
                    y = size.height * (0.14f + 0.04f * sin(orbPhase))
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(NirapadPrimaryTeal.copy(alpha = 0.14f), Color.Transparent),
                        center = topOrbCenter,
                        radius = size.width * 0.65f
                    ),
                    radius = size.width * 0.65f,
                    center = topOrbCenter
                )
                val bottomOrbCenter = Offset(
                    x = size.width * (0.80f - 0.06f * sin(orbPhase)),
                    y = size.height * (0.86f - 0.04f * cos(orbPhase))
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(NirapadNavyDark.copy(alpha = 0.09f), Color.Transparent),
                        center = bottomOrbCenter,
                        radius = size.width * 0.70f
                    ),
                    radius = size.width * 0.70f,
                    center = bottomOrbCenter
                )
            }
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Compact Language Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = NirapadSurface,
                    border = BorderStroke(1.dp, NirapadBorderSubtle),
                    shadowElevation = 1.dp
                ) {
                    Row(modifier = Modifier.padding(3.dp)) {
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
                                onClick = { if (!isBusy) selectedLang = lang },
                                color = pillColor,
                                shape = RoundedCornerShape(9.dp),
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

            // Minimalist Top/Center Brand Hero
            AnimatedVisibility(
                visible = revealStage >= 1,
                enter = fadeIn(tween(340)) + slideInVertically(
                    initialOffsetY = { -it / 5 },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 4.dp)
                        .testTag("auth_hero_card"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .testTag("auth_shield_emblem"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .graphicsLayer {
                                    scaleX = shieldPulseScale
                                    scaleY = shieldPulseScale
                                }
                                .clip(CircleShape)
                                .background(NirapadPrimaryTeal.copy(alpha = shieldHaloAlpha))
                        )
                        Box(
                            modifier = Modifier
                                .size(66.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(NirapadNavyDark, NirapadPrimaryTeal)
                                    )
                                )
                                .border(1.5.dp, Color.White.copy(alpha = 0.7f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "NIRAPAD safety emblem",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }

                    AnimatedContent(
                        targetState = copy,
                        transitionSpec = {
                            fadeIn(tween(220)).togetherWith(fadeOut(tween(140)))
                        },
                        label = "auth_hero_copy"
                    ) { localized ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = localized.appTitle,
                                style = MaterialTheme.typography.displaySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.6.sp
                                ),
                                color = NirapadNavyDark,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = localized.signInSubtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = NirapadTextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Middle: Clean Auth Card (Optional Name for Sign-Up, Email + Password, Forgot Password, & Google)
            AnimatedVisibility(
                visible = revealStage >= 2,
                enter = fadeIn(tween(340)) + slideInVertically(
                    initialOffsetY = { it / 6 },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = NirapadSurface),
                    border = BorderStroke(1.dp, NirapadBorderSubtle),
                    shape = RoundedCornerShape(22.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AnimatedVisibility(
                            visible = createAccountMode,
                            enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                            exit = fadeOut(tween(160)) + shrinkVertically(tween(160))
                        ) {
                            NirapadOutlinedTextField(
                                value = fullName,
                                onValueChange = {
                                    fullName = it
                                    if (errorMessage != null) errorMessage = null
                                },
                                enabled = !isBusy,
                                label = { Text("Full Name (optional)") },
                                placeholder = { Text("e.g. Ananya Sharma") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Person,
                                        contentDescription = null
                                    )
                                },
                                singleLine = true,
                                forceLightSurface = true,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Words,
                                    keyboardType = KeyboardType.Text,
                                    imeAction = ImeAction.Next
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auth_full_name_input")
                            )
                        }

                        NirapadOutlinedTextField(
                            value = email,
                            onValueChange = {
                                email = it
                                if (errorMessage != null) errorMessage = null
                                if (infoMessage != null) infoMessage = null
                            },
                            enabled = !isBusy,
                            label = { Text("Email") },
                            placeholder = { Text("you@example.com") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Email,
                                    contentDescription = null
                                )
                            },
                            singleLine = true,
                            forceLightSurface = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_email_input")
                        )

                        NirapadOutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                if (errorMessage != null) errorMessage = null
                            },
                            enabled = !isBusy,
                            label = { Text("Password") },
                            placeholder = { Text("At least 6 characters") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Lock,
                                    contentDescription = null
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { passwordVisible = !passwordVisible },
                                    enabled = !isBusy,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("auth_password_visibility_toggle")
                                ) {
                                    Icon(
                                        imageVector = if (passwordVisible) {
                                            Icons.Outlined.VisibilityOff
                                        } else {
                                            Icons.Outlined.Visibility
                                        },
                                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            singleLine = true,
                            forceLightSurface = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { submitEmailAuth() }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_password_input")
                        )

                        // Forgot Password link in Sign In mode
                        AnimatedVisibility(
                            visible = !createAccountMode,
                            enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                            exit = fadeOut(tween(140)) + shrinkVertically(tween(140))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = submitForgotPassword,
                                    enabled = !isBusy,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("auth_forgot_password_button")
                                ) {
                                    if (signInStage == SignInButtonStage.RESET_LOADING) {
                                        CircularProgressIndicator(
                                            color = NirapadPrimaryTeal,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "Sending reset link...",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = NirapadPrimaryTeal
                                        )
                                    } else {
                                        Text(
                                            text = "Forgot Password?",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = NirapadPrimaryTeal
                                        )
                                    }
                                }
                            }
                        }

                        // Inline confirmation banner (e.g. password reset email sent)
                        AnimatedVisibility(
                            visible = infoMessage != null,
                            enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                            exit = fadeOut(tween(160)) + shrinkVertically(tween(160))
                        ) {
                            Surface(
                                color = NirapadPositiveContainer,
                                border = BorderStroke(1.dp, NirapadPositiveGreen.copy(alpha = 0.35f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics(mergeDescendants = true) {}
                                    .testTag("auth_info_banner")
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = NirapadPositiveGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = infoMessage.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF0B462D)
                                    )
                                }
                            }
                        }

                        // Inline error feedback banner with shake
                        AnimatedVisibility(
                            visible = errorMessage != null,
                            enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                            exit = fadeOut(tween(160)) + shrinkVertically(tween(160))
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
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NirapadEmergencyRed,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        // Primary Sign In / Continue button
                        val emailButtonColor by animateColorAsState(
                            targetValue = when (signInStage) {
                                SignInButtonStage.EMAIL_VERIFIED -> NirapadPositiveGreen
                                else -> NirapadPrimaryTeal
                            },
                            animationSpec = tween(220),
                            label = "email_btn_color"
                        )
                        Button(
                            onClick = submitEmailAuth,
                            enabled = !isBusy,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = emailButtonColor,
                                contentColor = Color.White,
                                disabledContainerColor = emailButtonColor.copy(alpha = 0.72f),
                                disabledContentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("auth_email_sign_in_button")
                        ) {
                            when (signInStage) {
                                SignInButtonStage.EMAIL_LOADING -> {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.2.dp,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = if (createAccountMode) "Creating account..." else "Signing in...",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                SignInButtonStage.EMAIL_VERIFIED -> {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Signed in safely",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                else -> {
                                    Text(
                                        text = if (createAccountMode) "Create Account & Continue" else "Sign In / Continue",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    createAccountMode = !createAccountMode
                                    errorMessage = null
                                    infoMessage = null
                                },
                                enabled = !isBusy,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("auth_mode_toggle")
                            ) {
                                Text(
                                    text = if (createAccountMode) {
                                        "Already have an account? Sign In"
                                    } else {
                                        "New to NIRAPAD? Create Account"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = NirapadPrimaryTeal
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = NirapadBorderSubtle
                            )
                            Text(
                                text = "or",
                                style = MaterialTheme.typography.labelMedium,
                                color = NirapadTextSecondary
                            )
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = NirapadBorderSubtle
                            )
                        }

                        // Prominent Continue with Google Button
                        val googleInteraction = remember { MutableInteractionSource() }
                        val isGooglePressed by googleInteraction.collectIsPressedAsState()
                        val googleScale by animateFloatAsState(
                            targetValue = if (isGooglePressed && !isBusy) 0.98f else 1.0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "google_btn_scale"
                        )
                        val isGoogleVerified = signInStage == SignInButtonStage.GOOGLE_VERIFIED
                        OutlinedButton(
                            onClick = launchGoogleSignIn,
                            enabled = !isBusy,
                            interactionSource = googleInteraction,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(
                                width = 1.2.dp,
                                color = if (isGoogleVerified) NirapadPositiveGreen else Color(0xFFCBD5E1)
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isGoogleVerified) NirapadPositiveGreen else Color.White,
                                contentColor = if (isGoogleVerified) Color.White else Color(0xFF0F172A),
                                disabledContainerColor = if (isGoogleVerified) NirapadPositiveGreen else Color(0xFFF8FAFC),
                                disabledContentColor = if (isGoogleVerified) Color.White else Color(0xFF64748B)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .graphicsLayer {
                                    scaleX = googleScale
                                    scaleY = googleScale
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
                                        SignInButtonStage.GOOGLE_LOADING -> {
                                            CircularProgressIndicator(
                                                color = NirapadPrimaryTeal,
                                                strokeWidth = 2.2.dp,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Text(
                                                text = "Signing in with Google...",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF0F172A)
                                            )
                                        }
                                        SignInButtonStage.GOOGLE_VERIFIED -> {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Text(
                                                text = "Signed in safely",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White
                                            )
                                        }
                                        else -> {
                                            GoogleLogoBadge(modifier = Modifier.size(24.dp))
                                            Spacer(Modifier.width(12.dp))
                                            Text(
                                                text = copy.signInWithGoogle,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isBusy) Color(0xFF64748B) else Color(0xFF0F172A)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom: Single-line Privacy / Safety Assurance Note
            AnimatedVisibility(
                visible = revealStage >= 3,
                enter = fadeIn(tween(300))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("auth_privacy_note"),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = NirapadPrimaryTeal,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = copy.privacyAssuranceNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = NirapadTextSecondary,
                        textAlign = TextAlign.Center
                    )
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
                .padding(3.dp)
        ) {
            val stroke = Stroke(width = size.minDimension * 0.22f, cap = StrokeCap.Butt)
            val inset = stroke.width / 2f
            val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
            val topLeft = Offset(inset, inset)

            drawArc(
                color = Color(0xFFEA4335),
                startAngle = -155f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )
            drawArc(
                color = Color(0xFFFBBC05),
                startAngle = 140f,
                sweepAngle = 68f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )
            drawArc(
                color = Color(0xFF34A853),
                startAngle = 35f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )
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
