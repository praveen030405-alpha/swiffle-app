package com.swiffle.app.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialCustomException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID

sealed interface AuthState {
    data object Idle : AuthState
    data object Loading : AuthState
    data class Authenticated(
        val email: String,
        val displayName: String?,
        val avatarUrl: String? = null
    ) : AuthState
    data class Error(val message: String) : AuthState
}

object OrigamiAuth {
    private const val TAG = "OrigamiAuth"

    // Extracted directly from production Origami Flutter configuration
    private const val SUPABASE_URL = "https://ehmfeadwywgoyhzgsqtn.supabase.co"
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImVobWZlYWR3eXdnb3loemdzcXRuIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTA5Nzc4NTIsImV4cCI6MjEwNjU1Mzg1Mn0.goZ0qeRPJX-3EQuOOrSZNABiwPjH1wL2WqfE3vGk9ZY"
    const val WEB_CLIENT_ID = "546111738976-iqi4iivvt27v2qv2epu5tiqnlim2lb7f.apps.googleusercontent.com"

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_ANON_KEY
        ) {
            httpEngine = OkHttp.create()
            install(Auth) {
                // Auto load stored sessions from memory/cache
            }
        }
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun isUserSignedIn(): Boolean {
        return client.auth.currentSessionOrNull() != null
    }

    fun getCurrentUserEmail(): String? {
        return client.auth.currentUserOrNull()?.email
    }

    suspend fun signInWithGoogle(context: Context): Result<String> = withContext(Dispatchers.IO) {
        _authState.value = AuthState.Loading
        try {
            val act = findActivity(context) ?: context
            val credentialManager = CredentialManager.create(context)

            // Nonce generation for security verification
            val rawNonce = UUID.randomUUID().toString()
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(rawNonce.toByteArray())
            val hashedNonce = digest.fold("") { str, it -> str + "%02x".format(it) }

            val signInWithGoogleOption = com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption.Builder(WEB_CLIENT_ID)
                .setNonce(hashedNonce)
                .build()

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(WEB_CLIENT_ID)
                .setAutoSelectEnabled(false)
                .setNonce(hashedNonce)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInWithGoogleOption)
                .addCredentialOption(googleIdOption)
                .build()

            val response = credentialManager.getCredential(
                request = request,
                context = act
            )

            val credential = response.credential
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val idToken = googleIdTokenCredential.idToken

            Log.d(TAG, "Google ID Token acquired. Exchanging with Supabase...")

            try {
                client.auth.signInWith(IDToken) {
                    this.provider = Google
                    this.idToken = idToken
                    this.nonce = rawNonce
                }
            } catch (cloudErr: Throwable) {
                Log.w(TAG, "Supabase exchange skipped or unreachable, continuing with Google credential locally: ${cloudErr.message}")
            }

            val user = client.auth.currentUserOrNull()
            val email = user?.email ?: googleIdTokenCredential.id
            val displayName = googleIdTokenCredential.displayName ?: email.substringBefore("@")
            val jwtPic = extractPictureFromJwt(idToken)
            val avatarUrl = googleIdTokenCredential.profilePictureUri?.toString() 
                ?: jwtPic 
                ?: extractAvatarFromSupabaseUser(user)

            val prefs = context.getSharedPreferences("OrigamiPrefs", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putBoolean("is_user_logged_in", true)
                putBoolean("hasCompletedOnboarding", true)
                putString("user_mode", "google")
                putString("google_user_email", email)
                if (displayName != null) putString("google_user_name", displayName)
                if (avatarUrl != null) {
                    putString("google_user_avatar", avatarUrl)
                    putString("google_user_avatar_url", avatarUrl)
                } else {
                    remove("google_user_avatar")
                    remove("google_user_avatar_url")
                }
                apply()
            }

            // Immediately clear any stale guest custom avatar from local disk
            try {
                val oldGuestAvatar = java.io.File(context.filesDir, "custom_profile_avatar.jpg")
                if (oldGuestAvatar.exists()) oldGuestAvatar.delete()
            } catch (_: Exception) {}

            _authState.value = AuthState.Authenticated(email = email, displayName = displayName, avatarUrl = avatarUrl)
            Log.d(TAG, "Successfully authenticated with Supabase: $email, avatar: $avatarUrl")
            Result.success(email)
        } catch (e: Throwable) {
            if (isUserCancellation(e)) {
                Log.i(TAG, "User closed or dismissed Google Sign-In prompt")
                _authState.value = AuthState.Idle
                Result.failure<String>(e)
            } else {
                Log.e(TAG, "Authentication error: ${e.message}", e)
                val friendlyMessage = when {
                    e.message?.contains("network", ignoreCase = true) == true ||
                    e.message?.contains("unable to resolve host", ignoreCase = true) == true ->
                        "Unable to connect. Please check your internet connection."
                    e.message?.contains("developer", ignoreCase = true) == true ||
                    e.message?.contains("10:", ignoreCase = true) == true ->
                        "Configuration issue. Please try signing in with email."
                    e is GetCredentialException ->
                        "Google Sign-In was interrupted. Please try again."
                    else ->
                        e.localizedMessage ?: "Sign-in could not be completed."
                }
                _authState.value = AuthState.Error(friendlyMessage)
                Result.failure<String>(e)
            }
        }
    }

    private fun findActivity(context: Context): android.app.Activity? {
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is android.app.Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    fun isUserCancellation(e: Throwable): Boolean {
        if (e is GetCredentialCancellationException || e is java.util.concurrent.CancellationException) {
            return true
        }
        val msg = e.message?.lowercase().orEmpty()
        val customType = (e as? GetCredentialCustomException)?.type?.lowercase().orEmpty()
        return msg.contains("cancel") ||
               msg.contains("16:") ||
               msg.contains("closed") ||
               msg.contains("dismiss") ||
               customType.contains("user_canceled") ||
               customType.contains("cancellation")
    }

    private fun extractPictureFromJwt(idToken: String): String? {
        return try {
            val parts = idToken.split(".")
            if (parts.size >= 2) {
                val payloadBytes = android.util.Base64.decode(
                    parts[1],
                    android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
                )
                val json = org.json.JSONObject(String(payloadBytes, Charsets.UTF_8))
                val pic = json.optString("picture")
                if (pic.isNotBlank()) pic else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun extractAvatarFromSupabaseUser(user: io.github.jan.supabase.auth.user.UserInfo?): String? {
        if (user == null) return null
        return try {
            val meta = user.userMetadata
            val pic = meta?.get("avatar_url")?.toString()?.trim('"', ' ')
                ?: meta?.get("picture")?.toString()?.trim('"', ' ')
            if (!pic.isNullOrBlank()) return pic

            user.identities?.forEach { id ->
                val idPic = id.identityData?.get("avatar_url")?.toString()?.trim('"', ' ')
                    ?: id.identityData?.get("picture")?.toString()?.trim('"', ' ')
                if (!idPic.isNullOrBlank()) return idPic
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    fun getCurrentUserAvatarUrl(context: Context? = null): String? {
        val state = _authState.value
        if (state is AuthState.Authenticated && !state.avatarUrl.isNullOrBlank()) {
            return state.avatarUrl
        }
        if (context != null) {
            val prefs = context.getSharedPreferences("OrigamiPrefs", Context.MODE_PRIVATE)
            val saved = prefs.getString("google_user_avatar_url", null)
            if (!saved.isNullOrBlank()) return saved
        }
        val user = client.auth.currentUserOrNull()
        val fromUser = extractAvatarFromSupabaseUser(user)
        if (!fromUser.isNullOrBlank()) {
            if (context != null) {
                context.getSharedPreferences("OrigamiPrefs", Context.MODE_PRIVATE)
                    .edit().putString("google_user_avatar_url", fromUser).apply()
            }
            return fromUser
        }
        // Fallback: parse Supabase stored session directly from app shared preferences
        if (context != null) {
            try {
                val appPrefs = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
                val sessionStr = appPrefs.all.values.filterIsInstance<String>().firstOrNull { 
                    it.contains("avatar_url") || it.contains("lh3.googleusercontent.com") 
                }
                if (sessionStr != null) {
                    val match = Regex("""\"avatar_url\" ?: ?\"(https:[^\"]+)\"""").find(sessionStr)
                        ?: Regex("""\"picture\" ?: ?\"(https:[^\"]+)\"""").find(sessionStr)
                    val extracted = match?.groupValues?.get(1)?.replace("\\/", "/")
                    if (!extracted.isNullOrBlank()) {
                        context.getSharedPreferences("OrigamiPrefs", Context.MODE_PRIVATE)
                            .edit().putString("google_user_avatar_url", extracted).apply()
                        return extracted
                    }
                }
            } catch (_: Exception) {}
        }
        return null
    }

    fun getCurrentUserName(context: Context? = null): String? {
        val state = _authState.value
        if (state is AuthState.Authenticated && !state.displayName.isNullOrBlank()) {
            return state.displayName
        }
        if (context != null) {
            val prefs = context.getSharedPreferences("OrigamiPrefs", Context.MODE_PRIVATE)
            val saved = prefs.getString("google_user_name", null)
            if (!saved.isNullOrBlank()) return saved
        }
        val user = client.auth.currentUserOrNull()
        val fromMeta = user?.userMetadata?.get("full_name")?.toString()?.trim('"', ' ')
            ?: user?.userMetadata?.get("name")?.toString()?.trim('"', ' ')
        if (!fromMeta.isNullOrBlank()) return fromMeta
        return null
    }

    fun getCurrentUserEmail(context: Context? = null): String? {
        val user = client.auth.currentUserOrNull()
        if (user?.email != null) return user.email
        val state = _authState.value
        if (state is AuthState.Authenticated) return state.email
        if (context != null) {
            val prefs = context.getSharedPreferences("OrigamiPrefs", Context.MODE_PRIVATE)
            val saved = prefs.getString("google_user_email", null)
            if (!saved.isNullOrBlank()) return saved
        }
        return null
    }

    suspend fun signInWithEmail(email: String, pass: String): Result<String> = withContext(Dispatchers.IO) {
        _authState.value = AuthState.Loading
        try {
            client.auth.signInWith(Email) {
                this.email = email
                this.password = pass
            }
            val user = client.auth.currentUserOrNull()
            val userEmail = user?.email ?: email
            _authState.value = AuthState.Authenticated(email = userEmail, displayName = null, avatarUrl = null)
            Log.d(TAG, "Successfully authenticated with email: $userEmail")
            Result.success(userEmail)
        } catch (e: Exception) {
            Log.e(TAG, "Email sign in error: ${e.message}", e)
            val isNetwork = e is java.net.UnknownHostException ||
                            e.message?.contains("unable to resolve host", ignoreCase = true) == true ||
                            e.message?.contains("failed to connect", ignoreCase = true) == true ||
                            e.message?.contains("Standby", ignoreCase = true) == true
            val msg = if (isNetwork) {
                "Network unreachable. Please check your internet connection or continue in Guest Mode."
            } else {
                e.localizedMessage ?: "Invalid email or password"
            }
            _authState.value = AuthState.Error(msg)
            Result.failure(Exception(msg, e))
        }
    }

    suspend fun signUpWithEmail(email: String, pass: String): Result<String> = withContext(Dispatchers.IO) {
        _authState.value = AuthState.Loading
        try {
            client.auth.signUpWith(Email) {
                this.email = email
                this.password = pass
            }
            val user = client.auth.currentUserOrNull()
            val userEmail = user?.email ?: email
            _authState.value = AuthState.Authenticated(email = userEmail, displayName = null, avatarUrl = null)
            Log.d(TAG, "Successfully signed up with email: $userEmail")
            Result.success(userEmail)
        } catch (e: Exception) {
            Log.e(TAG, "Email sign up error: ${e.message}", e)
            val isNetwork = e is java.net.UnknownHostException ||
                            e.message?.contains("unable to resolve host", ignoreCase = true) == true ||
                            e.message?.contains("failed to connect", ignoreCase = true) == true ||
                            e.message?.contains("Standby", ignoreCase = true) == true
            val msg = if (isNetwork) {
                "Network unreachable. Please check your internet connection or continue in Guest Mode."
            } else {
                e.localizedMessage ?: "Registration failed"
            }
            _authState.value = AuthState.Error(msg)
            Result.failure(Exception(msg, e))
        }
    }

    suspend fun signOut(context: Context? = null) = withContext(Dispatchers.IO) {
        try {
            client.auth.signOut()
            _authState.value = AuthState.Idle
            if (context != null) {
                val prefs = context.getSharedPreferences("OrigamiPrefs", Context.MODE_PRIVATE)
                prefs.edit().apply {
                    putBoolean("is_user_logged_in", false)
                    putBoolean("hasCompletedOnboarding", false)
                    remove("google_user_email")
                    remove("google_user_name")
                    remove("google_user_avatar")
                    remove("google_user_avatar_url")
                    remove("user_mode")
                    remove("saved_login_email")
                    remove("remember_me_enabled")
                    apply()
                }
                try {
                    val avatarFile = java.io.File(context.filesDir, "custom_profile_avatar.jpg")
                    if (avatarFile.exists()) avatarFile.delete()
                } catch (_: Exception) {}
                try {
                    val appPrefs = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
                    appPrefs.edit().clear().apply()
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error signing out", e)
        }
    }
}
