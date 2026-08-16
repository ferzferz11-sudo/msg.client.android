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
import lavender.client.android.data.grpc.*

/**
 * ProfileClient — client for ProfileService v2 (JWT Bearer auth).
 *
 * All methods require a valid JWT token (attached automatically by BearerTokenInterceptor).
 */
object ProfileClient {
    private const val TAG = "ProfileClient"

    /** Cached service versions from /info endpoint. */
    @Volatile var serviceProfileVersion: String = ""; internal set
    @Volatile var serviceChatVersion: String = ""; internal set
    @Volatile var serviceAuthVersion: String = ""; internal set
    @Volatile var serviceAIVersion: String = ""; internal set
    @Volatile var maxUploadSize: Long = 30L * 1024 * 1024; internal set

    fun isProfileV2Supported(): Boolean = true
    fun isChatV2Supported(): Boolean = true
    fun isAuthV2Supported(): Boolean = true

    /**
     * Determine service versions from /info endpoint.
     * Dev server: assume v2. Prod server: try HTTP /info.
     */
    suspend fun fetchServerInfo(context: Context, serverAddress: String, httpPort: Int = 8083) {
        withContext(Dispatchers.IO) {
            try {
                val url = "http://$serverAddress:$httpPort/info"
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
                if (json.has("max_upload_size")) {
                    maxUploadSize = json.optLong("max_upload_size", 30L * 1024 * 1024)
                    Log.d(TAG, "Max upload size: $maxUploadSize")
                }
            } catch (e: Exception) {
                Log.d(TAG, "HTTP /info unavailable (${e.message})")
                serviceProfileVersion = ""
                serviceChatVersion = ""
                serviceAuthVersion = ""
                serviceAIVersion = ""
                maxUploadSize = 30L * 1024 * 1024
            }
        }
    }

    suspend fun getProfile(context: Context): GetProfileResponseProto? {
        return try {
            unaryCall(
                fullMethod = "messenger.ProfileService/GetProfile",
                requestMarshaller = GetProfileRequestMarshaller(),
                responseMarshaller = GetProfileResponseMarshaller(),
                request = GetProfileRequestProto()
            )
        } catch (e: Exception) {
            Log.w(TAG, "getProfile failed: ${e.message}")
            null
        }
    }

    suspend fun updateProfile(
        context: Context,
        username: String = "",
        bio: String = "",
        status: String = "",
        locale: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = UpdateProfileV2RequestProto(
                username = username, bio = bio, status = status, locale = locale
            )
            val response = unaryCall(
                fullMethod = "messenger.ProfileService/UpdateProfile",
                requestMarshaller = UpdateProfileV2RequestMarshaller(),
                responseMarshaller = UpdateProfileV2ResponseMarshaller(),
                request = request
            )
            response?.success ?: false
        } catch (e: Exception) {
            Log.w(TAG, "updateProfile failed: ${e.message}")
            false
        }
    }

    suspend fun updateAvatar(
        context: Context,
        avatarUrl: String,
        fullAvatarUrl: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = UpdateAvatarV2RequestProto(avatarUrl = avatarUrl, fullAvatarUrl = fullAvatarUrl)
            val response = unaryCall(
                fullMethod = "messenger.ProfileService/UpdateAvatar",
                requestMarshaller = UpdateAvatarV2RequestMarshaller(),
                responseMarshaller = UpdateAvatarV2ResponseMarshaller(),
                request = request
            )
            response?.success ?: false
        } catch (e: Exception) {
            Log.w(TAG, "updateAvatar failed: ${e.message}")
            false
        }
    }

    suspend fun deleteProfile(
        context: Context,
        password: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = DeleteProfileV2RequestProto(password = password)
            val response = unaryCall(
                fullMethod = "messenger.ProfileService/DeleteProfile",
                requestMarshaller = DeleteProfileV2RequestMarshaller(),
                responseMarshaller = DeleteProfileV2ResponseMarshaller(),
                request = request
            )
            response?.success ?: false
        } catch (e: Exception) {
            Log.w(TAG, "deleteProfile failed: ${e.message}")
            false
        }
    }

    suspend fun getUserSettings(context: Context): GetUserSettingsResponseProto? {
        return try {
            unaryCall(
                fullMethod = "messenger.ProfileService/GetUserSettings",
                requestMarshaller = GetUserSettingsRequestMarshaller(),
                responseMarshaller = GetUserSettingsResponseMarshaller(),
                request = GetUserSettingsRequestProto()
            )
        } catch (e: Exception) {
            Log.w(TAG, "getUserSettings failed: ${e.message}")
            null
        }
    }

    suspend fun updateUserSettings(
        context: Context,
        locale: String = "",
        themeId: String = "",
        pushEnabled: Boolean? = null,
        custom: Map<String, String> = emptyMap()
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = UpdateUserSettingsRequestProto(
                locale = locale, themeId = themeId, pushEnabled = pushEnabled, custom = custom
            )
            val response = unaryCall(
                fullMethod = "messenger.ProfileService/UpdateUserSettings",
                requestMarshaller = UpdateUserSettingsRequestMarshaller(),
                responseMarshaller = UpdateUserSettingsResponseMarshaller(),
                request = request
            )
            response?.success ?: false
        } catch (e: Exception) {
            Log.w(TAG, "updateUserSettings failed: ${e.message}")
            false
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <ReqT, RespT> unaryCall(
        fullMethod: String,
        requestMarshaller: io.grpc.MethodDescriptor.Marshaller<ReqT>,
        responseMarshaller: io.grpc.MethodDescriptor.Marshaller<RespT>,
        request: ReqT
    ): RespT? = suspendCancellableCoroutine { cont ->
        val channel = RealGrpcClient.getChannel()
        if (channel == null) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }

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
}
