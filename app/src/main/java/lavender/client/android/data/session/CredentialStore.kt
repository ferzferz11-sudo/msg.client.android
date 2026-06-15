package lavender.client.android.data.session

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

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
    private const val KEY_JWT_SERVER = "jwt_server_address" // server that issued the JWT tokens

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

    /**
     * Returns EncryptedSharedPreferences for JWT auth tokens.
     * Separate from credentials (password) to allow independent lifecycle.
     */
    fun getAuthPrefs(context: Context): SharedPreferences {
        return getEncryptedPrefs(context)
    }

    // --- Read credentials ---

    fun getUsername(context: Context): String {
        return getEncryptedPrefs(context).getString(KEY_USERNAME, null)
            ?: migrateIfNeeded(context)
    }

    /** Get last successfully used username (for prefill in login forms). */
    fun getLastUsername(context: Context): String {
        return getUsername(context)
    }

    /** Save username for future prefill. */
    fun setLastUsername(context: Context, username: String) {
        setCredentials(context, username, getPassword(context), getServerAddress(context))
    }

    fun getPassword(context: Context): String {
        return getEncryptedPrefs(context).getString(KEY_PASSWORD, null)
            ?: migrateIfNeeded(context)
    }

    fun getServerAddress(context: Context): String {
        return getEncryptedPrefs(context).getString(KEY_SERVER, null)
            ?: getLegacyPrefs(context).getString(LEGACY_KEY_SERVER, "") ?: ""
    }

    /** Returns just the host part (without port) from server_address, e.g. "13.140.25.249" */
    fun getServerHost(context: Context): String {
        val addr = getServerAddress(context)
        return addr.split(":").firstOrNull() ?: ""
    }

    /** Returns "http://host:8082" for HTTP file/image uploads */
    fun getHttpServerUrl(context: Context): String {
        val host = getServerHost(context)
        return if (host.isNotEmpty()) "http://$host:8082" else ""
    }

    /** Returns "http://host:80" for APK updates (nginx) */
    fun getApkServerUrl(context: Context): String {
        val host = getServerHost(context)
        return if (host.isNotEmpty()) "http://$host:80" else ""
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

    // --- Server list management ---

    data class ServerEntry(
        val id: String,
        val name: String,
        val host: String,
        val port: Int = 50051,
        val isDefault: Boolean = false,
        val isProtected: Boolean = false
    )

    fun getServerList(context: Context): List<ServerEntry> {
        val json = getLegacyPrefs(context).getString("servers_list", null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<ServerEntry>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(ServerEntry(
                    id = obj.optString("id", ""),
                    name = obj.optString("name", ""),
                    host = obj.optString("host", ""),
                    port = obj.optInt("port", 50051),
                    isDefault = obj.optBoolean("isDefault", false),
                    isProtected = obj.optBoolean("isProtected", false)
                ))
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse server list", e)
            emptyList()
        }
    }

    fun saveServerList(context: Context, servers: List<ServerEntry>) {
        val arr = JSONArray()
        servers.forEach { s ->
            val obj = JSONObject()
            obj.put("id", s.id)
            obj.put("name", s.name)
            obj.put("host", s.host)
            obj.put("port", s.port)
            obj.put("isDefault", s.isDefault)
            arr.put(obj)
        }
        getLegacyPrefs(context).edit().putString("servers_list", arr.toString()).apply()
    }

    fun addServer(context: Context, server: ServerEntry) {
        val list = getServerList(context).toMutableList()
        list.removeAll { it.id == server.id }
        if (server.isDefault) {
            for (i in list.indices) {
                if (list[i].isDefault) list[i] = list[i].copy(isDefault = false)
            }
            list.add(0, server)
        } else {
            // Add to end of list (after all servers)
            list.add(server)
        }
        saveServerList(context, list)
    }

    fun removeServer(context: Context, serverId: String) {
        val list = getServerList(context).toMutableList()
        list.removeAll { it.id == serverId }
        saveServerList(context, list)
    }

    fun getDefaultServer(context: Context): ServerEntry? {
        return getServerList(context).firstOrNull { it.isDefault }
    }

    // --- JWT server tracking ---

    /**
     * Returns the server address that issued the current JWT tokens.
     * Used to detect server switches — if the current server doesn't match,
     * JWT tokens must be cleared to avoid sending stale tokens to the wrong server.
     */
    fun getJwtServerAddress(context: Context): String {
        return getEncryptedPrefs(context).getString(KEY_JWT_SERVER, "") ?: ""
    }

    /**
     * Stores the server address that issued the current JWT tokens.
     * Called after successful SignInV2/SignUpV2.
     */
    fun setJwtServerAddress(context: Context, serverAddress: String) {
        getEncryptedPrefs(context).edit().putString(KEY_JWT_SERVER, serverAddress).apply()
    }

    /**
     * Clears the stored JWT server address.
     * Called on logout or token clear.
     */
    fun clearJwtServerAddress(context: Context) {
        getEncryptedPrefs(context).edit().remove(KEY_JWT_SERVER).apply()
    }
}
