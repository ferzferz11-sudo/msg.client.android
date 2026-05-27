package lavender.client.android.data.session

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Secure credential storage using EncryptedSharedPreferences.
 *
 * Passwords and other sensitive data are encrypted at rest using AES-256
 * with a hardware-backed keystore when available.
 *
 * Migration strategy:
 * - On first run, reads from legacy lavender_prefs (plain SharedPreferences)
 * - Copies credentials to encrypted storage
 * - Removes credentials from legacy storage
 */
object CredentialStore {

    private const val TAG = "CredentialStore"
    private const val ENCRYPTED_PREFS_FILE = "lavender_credentials"

    // Legacy keys that were stored in plain SharedPreferences
    private const val LEGACY_KEY_USERNAME = "saved_username"
    private const val LEGACY_KEY_PASSWORD = "saved_password"
    private const val LEGACY_KEY_SERVER = "server_address"
    private const val LEGACY_KEY_USER_ID = "userId"
    private const val LEGACY_KEY_EMAIL = "saved_email"

    // Encrypted keys
    private const val KEY_USERNAME = "username"
    private const val KEY_PASSWORD = "password"
    private const val KEY_SERVER = "server_address"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_EMAIL = "email"

    private var encryptedPrefs: EncryptedSharedPreferences? = null

    private fun getEncryptedPrefs(context: Context): EncryptedSharedPreferences {
        return encryptedPrefs ?: synchronized(this) {
            encryptedPrefs ?: createEncryptedPrefs(context).also { encryptedPrefs = it }
        }
    }

    private fun createEncryptedPrefs(context: Context): EncryptedSharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ) as EncryptedSharedPreferences
    }

    /**
     * Legacy accessor — returns plain SharedPreferences for non-credential data
     * (theme settings, push preferences, etc.). Do NOT store passwords here.
     */
    fun getLegacyPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences("lavender_prefs", Context.MODE_PRIVATE)
    }

    // --- Read credentials ---

    fun getUsername(context: Context): String {
        return getEncryptedPrefs(context).getString(KEY_USERNAME, null)
            ?: migrateIfNeeded(context)
    }

    fun getPassword(context: Context): String {
        return getEncryptedPrefs(context).getString(KEY_PASSWORD, null)
            ?: migrateIfNeeded(context)
    }

    fun getServerAddress(context: Context): String {
        return getEncryptedPrefs(context).getString(KEY_SERVER, null)
            ?: getLegacyPrefs(context).getString(LEGACY_KEY_SERVER, "") ?: ""
    }

    fun getUserId(context: Context): String {
        return getEncryptedPrefs(context).getString(KEY_USER_ID, null)
            ?: getLegacyPrefs(context).getString(LEGACY_KEY_USER_ID, "") ?: ""
    }

    fun getEmail(context: Context): String {
        return getEncryptedPrefs(context).getString(KEY_EMAIL, null)
            ?: getLegacyPrefs(context).getString(LEGACY_KEY_EMAIL, "") ?: ""
    }

    // --- Write credentials ---

    fun setCredentials(
        context: Context,
        username: String,
        password: String,
        userId: String = "",
        email: String = "",
        serverAddress: String = ""
    ) {
        getEncryptedPrefs(context).edit().apply {
            putString(KEY_USERNAME, username)
            putString(KEY_PASSWORD, password)
            if (userId.isNotEmpty()) putString(KEY_USER_ID, userId)
            if (email.isNotEmpty()) putString(KEY_EMAIL, email)
            if (serverAddress.isNotEmpty()) putString(KEY_SERVER, serverAddress)
            apply()
        }
    }

    fun setServerAddress(context: Context, serverAddress: String) {
        getEncryptedPrefs(context).edit().apply {
            putString(KEY_SERVER, serverAddress)
            apply()
        }
    }

    fun setUserId(context: Context, userId: String) {
        getEncryptedPrefs(context).edit().apply {
            putString(KEY_USER_ID, userId)
            apply()
        }
    }

    // --- Clear ---

    fun clear(context: Context) {
        getEncryptedPrefs(context).edit().clear().apply()
        encryptedPrefs = null
    }

    // --- Migration ---

    /**
     * One-time migration from plain SharedPreferences to EncryptedSharedPreferences.
     * Called when read returns null (i.e. not yet in encrypted storage).
     * Copies credentials and removes them from the legacy location.
     */
    private fun migrateIfNeeded(context: Context): String {
        val legacy = getLegacyPrefs(context)
        val legacyUsername = legacy.getString(LEGACY_KEY_USERNAME, "") ?: ""
        val legacyPassword = legacy.getString(LEGACY_KEY_PASSWORD, "") ?: ""
        val legacyServer = legacy.getString(LEGACY_KEY_SERVER, "") ?: ""
        val legacyUserId = legacy.getString(LEGACY_KEY_USER_ID, "") ?: ""
        val legacyEmail = legacy.getString(LEGACY_KEY_EMAIL, "") ?: ""

        if (legacyUsername.isNotEmpty() || legacyPassword.isNotEmpty()) {
            Log.d(TAG, "Migrating credentials from plain SharedPreferences to EncryptedSharedPreferences")

            // Copy to encrypted storage
            getEncryptedPrefs(context).edit().apply {
                putString(KEY_USERNAME, legacyUsername)
                putString(KEY_PASSWORD, legacyPassword)
                if (legacyUserId.isNotEmpty()) putString(KEY_USER_ID, legacyUserId)
                if (legacyEmail.isNotEmpty()) putString(KEY_EMAIL, legacyEmail)
                if (legacyServer.isNotEmpty()) putString(KEY_SERVER, legacyServer)
                apply()
            }

            // Remove from legacy storage
            legacy.edit().apply {
                remove(LEGACY_KEY_USERNAME)
                remove(LEGACY_KEY_PASSWORD)
                remove(LEGACY_KEY_SERVER)
                remove(LEGACY_KEY_USER_ID)
                remove(LEGACY_KEY_EMAIL)
                apply()
            }

            Log.d(TAG, "Credential migration complete")
        }

        return ""
    }

    /**
     * Checks if migration is needed (legacy prefs still contain credentials).
     */
    fun needsMigration(context: Context): Boolean {
        val legacy = getLegacyPrefs(context)
        return legacy.contains(LEGACY_KEY_PASSWORD) || legacy.contains(LEGACY_KEY_USERNAME)
    }
}
