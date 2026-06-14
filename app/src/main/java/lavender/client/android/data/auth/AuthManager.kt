package lavender.client.android.data.auth

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import lavender.client.android.data.session.CredentialStore
import org.json.JSONObject

/**
 * AuthManager handles JWT token storage, parsing, and refresh logic.
 *
 * The server (AuthService v2) returns access_token + refresh_token on SignInV2/SignUpV2.
 * Access tokens are short-lived (15 min), refresh tokens are long-lived (30 days).
 *
 * Flow:
 * 1. SignInV2 → store tokens
 * 2. On each API call → check if access token expired → if yes, refresh
 * 3. Refresh → send refresh_token to server → get new access+refresh pair
 * 4. Store new tokens → continue with API call
 */
object AuthManager {
    private const val TAG = "AuthManager"

    // Encrypted keys for JWT storage
    private const val KEY_ACCESS_TOKEN = "jwt_access_token"
    private const val KEY_REFRESH_TOKEN = "jwt_refresh_token"
    private const val KEY_ACCESS_EXPIRES_AT = "jwt_access_expires_at"   // unix seconds
    private const val KEY_REFRESH_EXPIRES_AT = "jwt_refresh_expires_at" // unix seconds
    private const val KEY_AUTH_USER_ID = "jwt_user_id"
    private const val KEY_AUTH_USERNAME = "jwt_username"
    private const val KEY_AUTH_DEVICE_ID = "jwt_device_id"
    private const val KEY_AUTH_METHOD = "auth_method" // "v1_legacy" or "v2_jwt"

    // Buffer before actual expiry to trigger refresh early (5 minutes)
    private const val REFRESH_BUFFER_SECONDS = 5 * 60

    /**
     * Checks if the user is authenticated via AuthService v2 (JWT)
     */
    fun isJwtAuthenticated(context: Context): Boolean {
        val method = getAuthMethod(context)
        if (method != "v2_jwt") return false
        val accessToken = getAccessToken(context)
        return accessToken != null && accessToken.isNotEmpty()
    }

    /**
     * Checks if the current access token needs refreshing
     */
    fun needsRefresh(context: Context): Boolean {
        val authMethod = getAuthMethod(context)
        if (authMethod != "v2_jwt") return false

        val expiresAt = getAccessExpiresAt(context)
        if (expiresAt == 0L) return true

        val now = System.currentTimeMillis() / 1000
        return now >= (expiresAt - REFRESH_BUFFER_SECONDS)
    }

    /**
     * Checks if the refresh token itself is expired
     */
    fun isRefreshTokenExpired(context: Context): Boolean {
        val refreshExpiresAt = getRefreshExpiresAt(context)
        if (refreshExpiresAt == 0L) return true
        val now = System.currentTimeMillis() / 1000
        return now >= refreshExpiresAt
    }

    /**
     * Stores the JWT token pair received from SignInV2/SignUpV2/RefreshToken
     */
    fun storeTokens(
        context: Context,
        accessToken: String,
        refreshToken: String,
        accessExpiresAt: Long,
        refreshExpiresAt: Long,
        userId: String,
        username: String,
        deviceId: String
    ) {
        val prefs = CredentialStore.getAuthPrefs(context)
        prefs.edit {
            putString(KEY_ACCESS_TOKEN, accessToken)
            putString(KEY_REFRESH_TOKEN, refreshToken)
            putLong(KEY_ACCESS_EXPIRES_AT, accessExpiresAt)
            putLong(KEY_REFRESH_EXPIRES_AT, refreshExpiresAt)
            putString(KEY_AUTH_USER_ID, userId)
            putString(KEY_AUTH_USERNAME, username)
            putString(KEY_AUTH_DEVICE_ID, deviceId)
            putString(KEY_AUTH_METHOD, "v2_jwt")
        }
        Log.d(TAG, "Tokens stored for user=$username, device=$deviceId, access_expires=$accessExpiresAt")
    }

    /**
     * Parses the JWT payload to extract claims (without signature verification — that's server-side)
     */
    fun parseTokenPayload(token: String): JSONObject? {
        return try {
            val parts = token.split(".")
            if (parts.size != 3) {
                Log.e(TAG, "Invalid JWT format: expected 3 parts, got ${parts.size}")
                return null
            }
            // JWT payload is Base64URL encoded
            val payload = parts[1]
            val decoded = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            JSONObject(String(decoded, Charsets.UTF_8))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse JWT payload: ${e.message}")
            null
        }
    }

    /**
     * Gets the Bearer authorization header value for gRPC metadata
     */
    fun getBearerToken(context: Context): String? {
        val token = getAccessToken(context)
        return if (!token.isNullOrEmpty()) "Bearer $token" else null
    }

    /**
     * Gets the current access token
     */
    fun getAccessToken(context: Context): String? {
        return CredentialStore.getAuthPrefs(context).getString(KEY_ACCESS_TOKEN, null)
    }

    /**
     * Gets the current refresh token — sent to server for rotation
     */
    fun getRefreshToken(context: Context): String? {
        return CredentialStore.getAuthPrefs(context).getString(KEY_REFRESH_TOKEN, null)
    }

    /**
     * Gets user_id from stored JWT tokens
     */
    fun getUserId(context: Context): String {
        return CredentialStore.getAuthPrefs(context).getString(KEY_AUTH_USER_ID, "") ?: ""
    }

    /**
     * Gets username from stored JWT tokens
     */
    fun getUsername(context: Context): String {
        return CredentialStore.getAuthPrefs(context).getString(KEY_AUTH_USERNAME, "") ?: ""
    }

    /**
     * Gets device_id from stored JWT tokens
     */
    fun getDeviceId(context: Context): String {
        return CredentialStore.getAuthPrefs(context).getString(KEY_AUTH_DEVICE_ID, "") ?: ""
    }

    /**
     * Clears all JWT tokens on sign-out
     */
    fun clearTokens(context: Context) {
        CredentialStore.getAuthPrefs(context).edit {
            remove(KEY_ACCESS_TOKEN)
            remove(KEY_REFRESH_TOKEN)
            remove(KEY_ACCESS_EXPIRES_AT)
            remove(KEY_REFRESH_EXPIRES_AT)
            remove(KEY_AUTH_USER_ID)
            remove(KEY_AUTH_USERNAME)
            remove(KEY_AUTH_DEVICE_ID)
            putString(KEY_AUTH_METHOD, "")
        }
        Log.d(TAG, "JWT tokens cleared")
    }

    /**
     * Returns "v1_legacy" for old auth, "v2_jwt" for new auth, "" for none
     */
    fun getAuthMethod(context: Context): String {
        return CredentialStore.getAuthPrefs(context).getString(KEY_AUTH_METHOD, "") ?: ""
    }

    /**
     * Mark that we're using legacy auth (v1)
     */
    fun setLegacyAuth(context: Context) {
        CredentialStore.getAuthPrefs(context).edit {
            putString(KEY_AUTH_METHOD, "v1_legacy")
        }
    }

    // --- Private helpers ---

    private fun getAccessToken(context: Context): String? {
        return CredentialStore.getAuthPrefs(context).getString(KEY_ACCESS_TOKEN, null)
    }

    private fun getAccessExpiresAt(context: Context): Long {
        return CredentialStore.getAuthPrefs(context).getLong(KEY_ACCESS_EXPIRES_AT, 0L)
    }

    private fun getRefreshExpiresAt(context: Context): Long {
        return CredentialStore.getAuthPrefs(context).getLong(KEY_REFRESH_EXPIRES_AT, 0L)
    }
}
