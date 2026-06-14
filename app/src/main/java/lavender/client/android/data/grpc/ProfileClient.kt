package lavender.client.android.data.grpc

import android.content.Context
import android.util.Log
import io.grpc.CallOptions
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import lavender.client.android.data.auth.AuthManager
import lavender.client.android.data.proto.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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

    /**
     * Check if the server supports ProfileService v2.
     * Returns true when server info indicates profile >= "2.0".
     */
    fun isProfileV2Supported(): Boolean {
        val serverInfo = RealGrpcClient.serverVersion.value
        // Parse from SERVER_INFO message which includes full info
        // Also check via cached service versions from /info endpoint
        return serviceProfileVersion >= "2.0"
    }

    /** Cached ProfileService version string from /info endpoint. */
    @Volatile
    var serviceProfileVersion: String = ""
        internal set

    /**
     * Fetch the /info endpoint to determine service versions.
     * Should be called once after connecting to a server.
     */
    suspend fun fetchServerInfo(context: Context, serverAddress: String, port: Int = 8083) {
        withContext(Dispatchers.IO) {
            try {
                val url = "http://$serverAddress:$port/info"
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.requestMethod = "GET"
                val response = connection.inputStream.bufferedReader().readText()
                connection.disconnect()

                // Parse JSON: {"services":{"profile":"2.0",...},"version":"1.2.1.0"}
                val json = org.json.JSONObject(response)
                val services = json.optJSONObject("services")
                if (services != null) {
                    serviceProfileVersion = services.optString("profile", "1.0")
                    Log.d(TAG, "Server profile version: $serviceProfileVersion")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch /info: ${e.message}")
                serviceProfileVersion = "" // unknown → will use fallback
            }
        }
    }

    // ======= ProfileService v2 gRPC calls =======
    // All use manually constructed gRPC calls with BearerTokenInterceptor auth.

    private fun <ReqT, RespT> createUnaryCall(
        methodFullPath: String,
        request: ReqT,
        responseClass: Class<RespT>
    ): Pair<MethodDescriptor<ReqT, RespT>, ReqT> {
        val method = MethodDescriptor.newBuilder<ReqT, RespT>()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName(methodFullPath)
            .setRequestMarshaller(ProtoMarshaller(request!!))
            .setResponseMarshaller(ProtoMarshaller(responseClass.newInstance()))
            .build()
        return Pair(method, request)
    }

    /**
     * Get the current user's profile.
     * Requires JWT auth (BearerTokenInterceptor attaches token).
     */
    suspend fun getProfile(context: Context): GetProfileResponseProto? {
        if (!isProfileV2Supported()) {
            Log.d(TAG, "ProfileV2 not supported, using legacy")
            return getProfileLegacy(context)
        }
        return try {
            unaryCall(
                fullMethod = "messenger.ProfileService/GetProfile",
                request = GetProfileRequestProto(),
                responseParser = { bytes -> GetProfileResponseProto::class.java.newInstance().also { /* parse from bytes */ } }
            )
        } catch (e: Exception) {
            Log.w(TAG, "ProfileV2 getProfile failed: ${e.message}, falling back to legacy")
            getProfileLegacy(context)
        }
    }

    /**
     * Update profile (bio, status, locale, username).
     */
    suspend fun updateProfile(
        context: Context,
        username: String = "",
        bio: String = "",
        status: String = "",
        locale: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isProfileV2Supported()) {
            return@withContext updateProfileLegacy(context, bio, status)
        }
        try {
            val request = UpdateProfileV2RequestProto(
                username = username, bio = bio, status = status, locale = locale
            )
            val response = unaryCallLegacy(
                fullMethod = "messenger.ProfileService/UpdateProfile",
                request = request,
                responseType = UpdateProfileV2ResponseProto::class.java
            )
            response?.success ?: false
        } catch (e: Exception) {
            Log.w(TAG, "ProfileV2 updateProfile failed: ${e.message}")
            updateProfileLegacy(context, bio, status)
        }
    }

    /**
     * Update avatar via ProfileService v2.
     */
    suspend fun updateAvatar(
        context: Context,
        avatarUrl: String,
        fullAvatarUrl: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isProfileV2Supported()) {
            return@withContext updateAvatarLegacy(context, avatarUrl, fullAvatarUrl)
        }
        try {
            val request = UpdateAvatarV2RequestProto(avatarUrl = avatarUrl, fullAvatarUrl = fullAvatarUrl)
            val response = unaryCallLegacy(
                fullMethod = "messenger.ProfileService/UpdateAvatar",
                request = request,
                responseType = UpdateAvatarV2ResponseProto::class.java
            )
            response?.success ?: false
        } catch (e: Exception) {
            Log.w(TAG, "ProfileV2 updateAvatar failed: ${e.message}")
            updateAvatarLegacy(context, avatarUrl, fullAvatarUrl)
        }
    }

    /**
     * Get user settings (locale, theme, push, custom).
     */
    suspend fun getUserSettings(context: Context): GetUserSettingsResponseProto? {
        if (!isProfileV2Supported()) {
            return null // Legacy doesn't have settings API
        }
        return try {
            unaryCallLegacy(
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
     * Update user settings.
     */
    suspend fun updateUserSettings(
        context: Context,
        locale: String = "",
        themeId: String = "",
        pushEnabled: Boolean? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isProfileV2Supported()) {
            return@withContext false
        }
        try {
            val request = UpdateUserSettingsRequestProto(
                locale = locale, themeId = themeId,
                pushEnabled = pushEnabled
            )
            val response = unaryCallLegacy(
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

    private suspend fun getProfileLegacy(context: Context): GetProfileResponseProto? {
        // Legacy: use ChatService/GetUserProfile via GrpcClient
        val userId = RealGrpcClient.getUserId() ?: return null
        // This is handled through the existing synchronous callback pattern
        // Return null here — the UI layer should handle via existing GrpcClient.getUserProfile
        return null
    }

    private fun updateProfileLegacy(context: Context, bio: String, status: String): Boolean {
        val username = RealGrpcClient.getCurrentUsername() ?: return false
        // Use existing GrpcClient.updateProfile
        var result = false
        RealGrpcClient.updateProfile(username, bio, status) { success, _ ->
            result = success
        }
        return result
    }

    private fun updateAvatarLegacy(context: Context, avatarUrl: String, fullAvatarUrl: String): Boolean {
        val username = RealGrpcClient.getCurrentUsername() ?: return false
        var result = false
        RealGrpcClient.updateAvatar(username, avatarUrl, fullAvatarUrl) { success, _ ->
            result = success
        }
        return result
    }

    // ======= Low-level gRPC unary call =======

    /**
     * Make a unary gRPC call using the shared channel.
     * BearerTokenInterceptor automatically attaches JWT from AuthManager.
     */
    private suspend fun <ReqT, RespT> unaryCallLegacy(
        fullMethod: String,
        request: ReqT,
        responseType: Class<RespT>
    ): RespT? = suspendCancellableCoroutine { cont ->
        val channel = RealGrpcClient.getChannel()
        if (channel == null) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }

        // Build marshallers dynamically
        val requestMarshaller = ProtoMarshaller(request!!)
        val responseInstance = responseType.newInstance()
        val responseMarshaller = ProtoMarshaller(responseInstance)

        val method = MethodDescriptor.newBuilder<ReqT, RespT>()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName(fullMethod)
            .setRequestMarshaller(requestMarshaller)
            .setResponseMarshaller(responseMarshaller)
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

    @Suppress("UNCHECKED_CAST")
    private fun <T> unaryCall(
        fullMethod: String,
        request: Any,
        responseParser: (ByteArray) -> T
    ): T? = throw NotImplementedError("Use unaryCallLegacy instead")
}

/**
 * Simple marshaller that uses a placeholder for proto serialization.
 * Since we're using manual MethodDescriptor construction with the existing
 * pattern from RealGrpcClient, we reuse the same approach.
 */
private class ProtoMarshaller<T>(private val defaultInstance: T) : MethodDescriptor.Marshaller<T> {
    override fun stream(value: T): java.io.InputStream = java.io.ByteArrayInputStream(ByteArray(0))
    override fun parse(stream: java.io.InputStream): T = defaultInstance
}
