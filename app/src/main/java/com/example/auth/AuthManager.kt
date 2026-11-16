package com.example.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.example.data.repository.NirapadRepository
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object AuthManager {
    private const val TAG = "AuthManager"

    fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser)
        }
        addAuthStateListener(listener)
        awaitClose { removeAuthStateListener(listener) }
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    fun attemptAutoSignIn(
        context: Context,
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        onAuthSuccess: () -> Unit,
        onUnauthenticated: () -> Unit
    ) {
        val existingUser = Firebase.auth.currentUser
        if (existingUser != null) {
            scope.launch {
                syncUserProfileBestEffort(context, existingUser)
                onAuthSuccess()
            }
            return
        }
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            onUnauthenticated()
            return
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val hostContext = context.findActivity() ?: context

        scope.launch {
            try {
                val result = credentialManager.getCredential(hostContext, request)
                authenticateFirebaseWithCredential(
                    context = context,
                    credential = result.credential,
                    onAuthSuccess = onAuthSuccess,
                    onAuthError = { onUnauthenticated() }
                )
            } catch (_: Exception) {
                onUnauthenticated()
            }
        }
    }

    fun onGoogleSignInClicked(
        context: Context,
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        onAuthSuccess: () -> Unit,
        onAuthError: (String) -> Unit,
        onAuthCancelled: () -> Unit = {},
        onFinished: () -> Unit = {}
    ) {
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            onAuthError("Google Sign-In configuration missing: default_web_client_id not found.")
            onFinished()
            return
        }

        val hostContext = context.findActivity()
        if (hostContext == null) {
            onAuthError("Unable to launch Google Sign-In because no active screen context was found.")
            onFinished()
            return
        }

        scope.launch {
            var completedCallback = false
            try {
                // Primary: Credential Manager Google ID option
                val primaryOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(clientId)
                    .setAutoSelectEnabled(false)
                    .build()
                val primaryRequest = GetCredentialRequest.Builder()
                    .addCredentialOption(primaryOption)
                    .build()

                val result = try {
                    credentialManager.getCredential(hostContext, primaryRequest)
                } catch (cancel: GetCredentialCancellationException) {
                    throw cancel
                } catch (e: GetCredentialException) {
                    // Fallback: explicit Sign-In with Google bottom-sheet account picker
                    Log.w(TAG, "Primary Google ID request fell back to GetSignInWithGoogleOption: ${e.message}")
                    val fallbackOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
                    val fallbackRequest = GetCredentialRequest.Builder()
                        .addCredentialOption(fallbackOption)
                        .build()
                    credentialManager.getCredential(hostContext, fallbackRequest)
                }

                authenticateFirebaseWithCredential(
                    context = context,
                    credential = result.credential,
                    onAuthSuccess = {
                        completedCallback = true
                        onAuthSuccess()
                    },
                    onAuthError = { err ->
                        completedCallback = true
                        onAuthError(err)
                    }
                )
            } catch (e: GetCredentialCancellationException) {
                Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
                completedCallback = true
                onAuthCancelled()
            } catch (e: NoCredentialException) {
                Log.w(TAG, "No Google accounts available for sign-in: ${e.message}", e)
                completedCallback = true
                onAuthError("No Google account is signed in on this device. Add a Google account in Settings or sign in with Email.")
            } catch (e: GetCredentialException) {
                Log.e(TAG, "Credential Manager error during Google Sign-In", e)
                completedCallback = true
                val detail = e.errorMessage?.toString().orEmpty()
                if (detail.contains("play services", ignoreCase = true) ||
                    detail.contains("developer_error", ignoreCase = true) ||
                    detail.contains("16:", ignoreCase = true)
                ) {
                    onAuthError("Google Sign-In unavailable on this device right now. Try again or use Email Sign-In.")
                } else {
                    onAuthError(detail.ifBlank { "Google Sign-In could not be completed. Please try again." })
                }
            } catch (e: CancellationException) {
                completedCallback = true
                onAuthCancelled()
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Google Sign-In failed", e)
                completedCallback = true
                onAuthError(mapFirebaseAuthError(e, defaultMessage = "Google Sign-In failed. Please try again."))
            } finally {
                if (!completedCallback) {
                    onAuthCancelled()
                }
                onFinished()
            }
        }
    }

    fun signInWithEmailPassword(
        context: Context,
        email: String,
        password: String,
        fullName: String = "",
        createAccount: Boolean = false,
        scope: CoroutineScope,
        onAuthSuccess: () -> Unit,
        onAuthError: (String) -> Unit,
        onFinished: () -> Unit = {}
    ) {
        val trimmedEmail = email.trim()
        val trimmedName = fullName.trim()

        if (trimmedEmail.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            onAuthError("Please enter a valid email address.")
            onFinished()
            return
        }
        if (password.length < 6) {
            onAuthError("Password must be at least 6 characters.")
            onFinished()
            return
        }

        scope.launch {
            var completedCallback = false
            try {
                val authResult = if (createAccount) {
                    Firebase.auth.createUserWithEmailAndPassword(trimmedEmail, password).await()
                } else {
                    Firebase.auth.signInWithEmailAndPassword(trimmedEmail, password).await()
                }

                val user = authResult.user ?: Firebase.auth.currentUser
                if (user != null) {
                    if (createAccount && trimmedName.isNotEmpty()) {
                        try {
                            val profileUpdates = userProfileChangeRequest {
                                displayName = trimmedName.take(100)
                            }
                            user.updateProfile(profileUpdates).await()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "Non-fatal displayName update failure: ${e.message}")
                        }
                    }

                    syncUserProfileBestEffort(
                        context = context,
                        user = user,
                        overrideDisplayName = trimmedName.takeIf { it.isNotBlank() }
                    )

                    completedCallback = true
                    onAuthSuccess()
                } else {
                    completedCallback = true
                    onAuthError("Authentication succeeded, but user session was not established.")
                }
            } catch (e: CancellationException) {
                completedCallback = true
                onAuthError("Sign-in was interrupted. Please try again.")
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Email authentication failed", e)
                completedCallback = true
                onAuthError(mapFirebaseAuthError(e, defaultMessage = "Sign in failed. Please try again."))
            } finally {
                if (!completedCallback) {
                    onAuthError("Unable to complete sign-in. Please try again.")
                }
                onFinished()
            }
        }
    }

    fun sendPasswordResetEmail(
        email: String,
        scope: CoroutineScope,
        onResetEmailSent: (String) -> Unit,
        onResetError: (String) -> Unit,
        onFinished: () -> Unit = {}
    ) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            onResetError("Enter your valid email address first to receive a password reset link.")
            onFinished()
            return
        }

        scope.launch {
            var completedCallback = false
            try {
                Firebase.auth.sendPasswordResetEmail(trimmedEmail).await()
                completedCallback = true
                onResetEmailSent("Password reset link sent to $trimmedEmail. Check your inbox.")
            } catch (e: CancellationException) {
                completedCallback = true
                onResetError("Password reset request cancelled.")
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Password reset failed", e)
                completedCallback = true
                onResetError(mapFirebaseAuthError(e, defaultMessage = "Could not send password reset email. Please try again."))
            } finally {
                if (!completedCallback) {
                    onResetError("Could not send password reset email.")
                }
                onFinished()
            }
        }
    }

    internal fun mapFirebaseAuthError(
        exception: Exception,
        defaultMessage: String = "Authentication failed. Please try again."
    ): String {
        if (exception is FirebaseNetworkException) {
            return "Network unavailable. Check your internet connection and try again."
        }
        if (exception is FirebaseAuthException) {
            return when (exception.errorCode) {
                "ERROR_USER_NOT_FOUND" ->
                    "No account found for this email. Switch to Create Account to sign up."
                "ERROR_WRONG_PASSWORD" ->
                    "Incorrect password. Please try again or tap Forgot Password?"
                "ERROR_INVALID_CREDENTIAL" ->
                    "Incorrect email or password. Check your details or tap Forgot Password?"
                "ERROR_INVALID_EMAIL" ->
                    "Please enter a valid email address."
                "ERROR_EMAIL_ALREADY_IN_USE" ->
                    "An account already exists with this email. Sign in instead."
                "ERROR_WEAK_PASSWORD" ->
                    "Password is too weak. Choose at least 6 characters."
                "ERROR_OPERATION_NOT_ALLOWED" ->
                    "Email/Password sign-in is currently disabled on Firebase. Please use Continue with Google or enable Email/Password in Firebase Console."
                "ERROR_TOO_MANY_REQUESTS" ->
                    "Too many attempts. Please wait a moment and try again."
                "ERROR_USER_DISABLED" ->
                    "This account has been disabled. Contact support for help."
                "ERROR_NETWORK_REQUEST_FAILED" ->
                    "Network unavailable. Check your internet connection and try again."
                else -> mapMessageFallback(exception.message, defaultMessage)
            }
        }
        return mapMessageFallback(exception.message, defaultMessage)
    }

    private fun mapMessageFallback(rawMessage: String?, defaultMessage: String): String {
        val msg = rawMessage.orEmpty()
        return when {
            msg.contains("ERROR_USER_NOT_FOUND", ignoreCase = true) ||
                msg.contains("no user record", ignoreCase = true) ->
                "No account found for this email. Switch to Create Account to sign up."
            msg.contains("ERROR_WRONG_PASSWORD", ignoreCase = true) ||
                msg.contains("password is invalid", ignoreCase = true) ->
                "Incorrect password. Please try again or tap Forgot Password?"
            msg.contains("ERROR_INVALID_CREDENTIAL", ignoreCase = true) ||
                msg.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) ->
                "Incorrect email or password. Check your details or tap Forgot Password?"
            msg.contains("ERROR_EMAIL_ALREADY_IN_USE", ignoreCase = true) ||
                msg.contains("already in use", ignoreCase = true) ->
                "An account already exists with this email. Sign in instead."
            msg.contains("ERROR_OPERATION_NOT_ALLOWED", ignoreCase = true) ->
                "Email/Password sign-in is currently disabled on Firebase. Please use Continue with Google or enable Email/Password in Firebase Console."
            msg.contains("badly formatted", ignoreCase = true) ->
                "Please enter a valid email address."
            msg.contains("network", ignoreCase = true) ->
                "Network unavailable. Check your internet connection and try again."
            else -> defaultMessage
        }
    }

    private suspend fun authenticateFirebaseWithCredential(
        context: Context,
        credential: Credential,
        onAuthSuccess: () -> Unit,
        onAuthError: (String) -> Unit
    ) {
        if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
            val result = Firebase.auth.signInWithCredential(authCredential).await()
            val user = result.user ?: Firebase.auth.currentUser
            if (user != null) {
                syncUserProfileBestEffort(context, user)
                onAuthSuccess()
            } else {
                onAuthError("Google sign-in completed, but Firebase user session was not established.")
            }
        } else {
            onAuthError("Unexpected credential type received.")
        }
    }

    private suspend fun syncUserProfileBestEffort(
        context: Context,
        user: FirebaseUser,
        overrideDisplayName: String? = null
    ) {
        try {
            val resolvedName = overrideDisplayName
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: user.displayName?.trim()?.takeIf { it.isNotBlank() }
                ?: user.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
                ?: "Traveller"
            NirapadRepository(context.applicationContext).ensureUserProfile(
                uid = user.uid,
                displayName = resolvedName,
                email = user.email.orEmpty()
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Deferred Firestore profile sync (ViewModel will retry): ${e.message}")
        }
    }

    fun signOut(
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        onSignOutComplete: () -> Unit = {}
    ) {
        scope.launch {
            withContext(NonCancellable) {
                try {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to clear credential state: ${e.message}")
                } finally {
                    Firebase.auth.signOut()
                    onSignOutComplete()
                }
            }
        }
    }
}
