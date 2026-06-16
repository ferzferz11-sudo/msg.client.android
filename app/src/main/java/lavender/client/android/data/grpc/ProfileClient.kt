package lavender.client.android.data.grpc

import android.content.Context
import android.util.Log
import io.grpc.CallOptions
import io.grpc.ClientCall
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import lavender.client.android.data.proto.*
import kotlin.coroutines.resume

/**
 * ProfileClient — client for ProfileService v2 (JWT Bearer auth).
 *
 * All methods require a valid JWT token (attached automatically by BearerTokenInterceptor).
 * Falls back to legacy ChatService profile methods if ProfileService is not available (prod).
 *
 * Dev server: ProfileService v2 (profile >= "2.0" in /info)
 * Prod server: legacy ChatService (profile < "2.0" or /info not available)
 */
object ProfileClient {
    private const val TAG = "ProfileClient"

    /** Cached ProfileService version string from /info endpoint. */
    @Volatile
    var serviceProfileVersion: String = ""
        internal set

    /** Cached ChatService version string from /info endpoint. */
    @Volatile
    var serviceChatVersion: String = ""
        internal set

    /** Cached AuthService version string from /info endpoint. */
    @Volatile
    var serviceAuthVersion: String = ""
        internal set

    /** Cached AIService version string from /info endpoint. */
    @Volatile
    var serviceAIVersion: String = ""
        internal set

    /** Check if the server supports ProfileService v2. */
    fun isProfileV2Supported(): Boolean = serviceProfileVersion >= "2.0"

    /** Check if the server supports ChatService v2 (JWT in Chat stream, Pin/Search/Archive). */
    fun isChatV2Supported(): Boolean = serviceChatVersion >= "2.0"

    /** Check if the server supports AuthService v2 (JWT). */
    fun isAuthV2Supported(): Boolean = serviceAuthVersion >= "2.0"

    /**
     * Fetch the /info endpoint to determine service versions.
     * Called automatically from RealGrpcClient.connect().
     * If /info is unavailable, all versions stay empty → v1 fallback for everything.
     */
    suspend fun fetchServerInfo(context: Context, serverAddress: String, port: Int = 8083) {
        Log.d(TAG, "fetchServerInfo: starting for $serverAddress:$port")
        withContext(Dispatchers.IO) {
            try {
                val url = "http://$serverAddress:$port/info"
                Log.d(TAG, "fetchServerInfo: fetching $url")
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.requestMethod = "GET"
                val response = connection.inputStream.bufferedReader().readText()
                connection.disconnect()

                val json = org.json.JSONObject(response)
                val services = json.optJSONObject("services")
                if (services != null) {
                    serviceProfileVersion = services.optString("profile", "")
                    serviceChatVersion = services.optString("chat", "")
                    serviceAuthVersion = services.optString("auth", "")
                    serviceAIVersion = services.optString("ai", "")
                    Log.d(TAG, "Server versions: profile=$serviceProfileVersion chat=$serviceChatVersion auth=$serviceAuthVersion ai=$serviceAIVersion")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch /info: ${e.message} — using v1 fallback for all services")
                // All versions stay empty → v1 fallback everywhere
                serviceProfileVersion = ""
                serviceChatVersion = ""
                serviceAuthVersion = ""
                serviceAIVersion = ""
            }
        }
    }

    // ======= ProfileService v2 gRPC calls =======

    /**
     * Get the current user's profile via ProfileService v2.
     * Falls back to legacy ChatService/GetUserProfile on prod.
     */
    suspend fun getProfile(context: Context): GetProfileResponseProto? {
        if (!isProfileV2Supported()) {
            Log.d(TAG, "ProfileV2 not supported, returning null (use GrpcClient.getUserProfile)")
            return null
        }
        return try {
            unaryCall(
                fullMethod = "messenger.ProfileService/GetProfile",
                request = GetProfileRequestProto(),
                responseType = GetProfileResponseProto::class.java
            )
        } catch (e: Exception) {
            Log.w(TAG, "ProfileV2 getProfile failed: ${e.message}")
            null
        }
    }

    /**
     * Update profile (bio, status, locale, username) via ProfileService v2.
     * Falls back to legacy ChatService/UpdateProfile on prod.
     */
    suspend fun updateProfile(
        context: Context,
        username: String = "",
        bio: String = "",
        status: String = "",
        locale: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isProfileV2Supported()) {
            return@withContext updateProfileLegacy(bio, status)
        }
        try {
            val request = UpdateProfileV2RequestProto(
                username = username, bio = bio, status = status, locale = locale
            )
            val response = unaryCall(
                fullMethod = "messenger.ProfileService/UpdateProfile",
                request = request,
                responseType = UpdateProfileV2ResponseProto::class.java
            )
            response?.success ?: false
        } catch (e: Exception) {
            Log.w(TAG, "ProfileV2 updateProfile failed: ${e.message}")
            updateProfileLegacy(bio, status)
        }
    }

    /**
     * Update avatar via ProfileService v2.
     * Falls back to legacy ChatService/UpdateAvatar on prod.
     */
    suspend fun updateAvatar(
        context: Context,
        avatarUrl: String,
        fullAvatarUrl: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isProfileV2Supported()) {
            return@withContext updateAvatarLegacy(avatarUrl, fullAvatarUrl)
        }
        try {
            val request = UpdateAvatarV2RequestProto(avatarUrl = avatarUrl, fullAvatarUrl = fullAvatarUrl)
            val response = unaryCall(
                fullMethod = "messenger.ProfileService/UpdateAvatar",
                request = request,
                responseType = UpdateAvatarV2ResponseProto::class.java
            )
            response?.success ?: false
        } catch (e: Exception) {
            Log.w(TAG, "ProfileV2 updateAvatar failed: ${e.message}")
            updateAvatarLegacy(avatarUrl, fullAvatarUrl)
        }
    }

    /**
     * Get user settings (locale, theme, push) via ProfileService v2.
     * Returns null on prod (no legacy equivalent).
     */
    suspend fun getUserSettings(context: Context): GetUserSettingsResponseProto? {
        if (!isProfileV2Supported()) return null
        return try {
            unaryCall(
                fullMethod = "messenger.ProfileService/GetUserSettings",
                request = GetUserSettingsRequestProto(),
                responseType = GetUserSettingsResponseProto::class.java
            )
        } catch (e: Exception) {
            Log.w(TAG, "ProfileV2 getUserSettings failed: ${e.message}")
            null
        }
    }

    /**
     * Update user settings via ProfileService v2.
     * Returns false on prod.
     */
    suspend fun updateUserSettings(
        context: Context,
        locale: String = "",
        themeId: String = "",
        pushEnabled: Boolean? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isProfileV2Supported()) return@withContext false
        try {
            val request = UpdateUserSettingsRequestProto(
                locale = locale, themeId = themeId, pushEnabled = pushEnabled
            )
            val response = unaryCall(
                fullMethod = "messenger.ProfileService/UpdateUserSettings",
                request = request,
                responseType = UpdateUserSettingsResponseProto::class.java
            )
            response?.success ?: false
        } catch (e: Exception) {
            Log.w(TAG, "ProfileV2 updateUserSettings failed: ${e.message}")
            false
        }
    }

    // ======= Legacy fallbacks (via ChatService) =======

    private fun updateProfileLegacy(bio: String, status: String): Boolean {
        val username = RealGrpcClient.getCurrentUsername() ?: return false
        var result = false
        RealGrpcClient.updateProfile(username, bio, status) { success, _ -> result = success }
        return result
    }

    private fun updateAvatarLegacy(avatarUrl: String, fullAvatarUrl: String): Boolean {
        val username = RealGrpcClient.getCurrentUsername() ?: return false
        var result = false
        RealGrpcClient.updateAvatar(username, avatarUrl, fullAvatarUrl) { success, _ -> result = success }
        return result
    }

    // ======= Low-level gRPC unary call =======

    /**
     * Make a unary gRPC call using the shared channel.
     * BearerTokenInterceptor automatically attaches JWT from AuthManager.
     */
    @Suppress("UNCHECKED_CAST")
    private suspend fun <ReqT, RespT> unaryCall(
        fullMethod: String,
        request: ReqT,
        responseType: Class<RespT>
    ): RespT? = suspendCancellableCoroutine { cont ->
        val channel = RealGrpcClient.getChannel()
        if (channel == null) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }

        val method = MethodDescriptor.newBuilder<ReqT, RespT>()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName(fullMethod)
            .setRequestMarshaller(object : MethodDescriptor.Marshaller<ReqT> {
                override fun stream(value: ReqT): java.io.InputStream = java.io.ByteArrayInputStream(ByteArray(0))
                override fun parse(stream: java.io.InputStream): ReqT = request
            })
            .setResponseMarshaller(object : MethodDescriptor.Marshaller<RespT> {
                override fun stream(value: RespT): java.io.InputStream = java.io.ByteArrayInputStream(ByteArray(0))
                @Suppress("DEPRECATION")
                override fun parse(stream: java.io.InputStream): RespT = responseType.getDeclaredConstructor().newInstance()
            })
            .build()

        val call = channel.newCall(method, CallOptions.DEFAULT)
        call.start(object : ClientCall.Listener<RespT>() {
            private var response: RespT? = null

            override fun onMessage(message: RespT) {
                response = message
            }

            override fun onClose(status: io.grpc.Status, trailers: Metadata) {
                if (status.isOk) {
                    cont.resume(response)
                } else {
                    if (status.code == io.grpc.Status.Code.UNAUTHENTICATED) {
                        Log.w(TAG, "ProfileService auth failed — token may be expired")
                    }
                    cont.resume(null)
                }
            }
        }, io.grpc.Metadata())

        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }
}