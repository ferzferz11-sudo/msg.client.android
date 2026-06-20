package lavender.client.android.data.grpc

import io.grpc.MethodDescriptor
import io.grpc.ClientCall
import io.grpc.Metadata
import io.grpc.Status
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import lavender.client.android.data.proto.*

/**
 * Handles authentication operations: sign-in, sign-up, token refresh, sign-out.
 *
 * Owns auth-related RPC calls to AuthService v2 (JWT).
 * Does NOT own channel management — uses channel from GrpcConnectionManager.
 */
class GrpcAuthClient(
    private val getChannel: () -> io.grpc.ManagedChannel?,
    private val connectionStatus: StateFlow<ConnectionStatus>,
    private val authStatus: MutableStateFlow<String?>,
    private val setAuthFailure: (Boolean) -> Unit
) {
    companion object {
        private const val TAG = "GrpcAuthClient"
    }

    /**
     * SignInV2 — authenticates via AuthService v2 with JWT tokens.
     */
    fun signInV2(
        username: String,
        password: String,
        deviceId: String,
        deviceName: String,
        deviceType: String = "android",
        clientVersion: String = "",
        callback: (AuthResponseV2Proto?, String?) -> Unit
    ) {
        val currentChannel = getChannel() ?: run {
            callback(null, "Not connected")
            return
        }

        val methodDesc = MethodDescriptor.newBuilder<SignInRequestV2Proto, AuthResponseV2Proto>()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.AuthService/SignInV2")
            .setRequestMarshaller(SignInRequestV2Marshaller())
            .setResponseMarshaller(AuthResponseV2Marshaller())
            .build()

        val call = currentChannel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
        call.start(object : ClientCall.Listener<AuthResponseV2Proto>() {
            override fun onMessage(message: AuthResponseV2Proto) {
                if (message.success) {
                    setAuthFailure(false)
                    callback(message, null)
                } else {
                    callback(null, message.message)
                }
            }
            override fun onClose(status: Status, trailers: Metadata) {
                if (!status.isOk) {
                    callback(null, status.description ?: "Auth failed")
                }
            }
        }, Metadata())
        call.sendMessage(SignInRequestV2Proto(
            username = username, password = password,
            deviceId = deviceId, deviceName = deviceName,
            deviceType = deviceType, clientVersion = clientVersion
        ))
        call.halfClose()
        call.request(1)
    }

    /**
     * SignUpV2 — registers a new user via AuthService v2 with JWT tokens.
     */
    fun signUpV2(
        username: String,
        password: String,
        email: String,
        deviceId: String,
        deviceName: String,
        deviceType: String = "android",
        clientVersion: String = "",
        callback: (AuthResponseV2Proto?, String?) -> Unit
    ) {
        val currentChannel = getChannel() ?: run {
            callback(null, "Not connected")
            return
        }

        val methodDesc = MethodDescriptor.newBuilder<SignUpRequestV2Proto, AuthResponseV2Proto>()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.AuthService/SignUpV2")
            .setRequestMarshaller(SignUpRequestV2Marshaller())
            .setResponseMarshaller(AuthResponseV2Marshaller())
            .build()

        val call = currentChannel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
        call.start(object : ClientCall.Listener<AuthResponseV2Proto>() {
            override fun onMessage(message: AuthResponseV2Proto) {
                if (message.success) {
                    callback(message, null)
                } else {
                    callback(null, message.message)
                }
            }
            override fun onClose(status: Status, trailers: Metadata) {
                if (!status.isOk) {
                    callback(null, status.description ?: "Registration failed")
                }
            }
        }, Metadata())
        call.sendMessage(SignUpRequestV2Proto(
            username = username, password = password, email = email,
            deviceId = deviceId, deviceName = deviceName,
            deviceType = deviceType, clientVersion = clientVersion
        ))
        call.halfClose()
        call.request(1)
    }

    /**
     * RefreshToken — exchanges a refresh token for a new access+refresh pair.
     */
    fun refreshToken(
        refreshToken: String,
        callback: (RefreshTokenResponseProto?, String?) -> Unit
    ) {
        val currentChannel = getChannel() ?: run {
            callback(null, "Not connected")
            return
        }

        val methodDesc = MethodDescriptor.newBuilder<RefreshTokenRequestProto, RefreshTokenResponseProto>()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.AuthService/RefreshToken")
            .setRequestMarshaller(RefreshTokenRequestMarshaller())
            .setResponseMarshaller(RefreshTokenResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
        call.start(object : ClientCall.Listener<RefreshTokenResponseProto>() {
            override fun onMessage(message: RefreshTokenResponseProto) {
                callback(message, null)
            }
            override fun onClose(status: Status, trailers: Metadata) {
                if (!status.isOk) {
                    callback(null, status.description ?: "Token refresh failed")
                }
            }
        }, Metadata())
        call.sendMessage(RefreshTokenRequestProto(refreshToken = refreshToken))
        call.halfClose()
        call.request(1)
    }

    /**
     * SignOut — revokes a device session or all sessions.
     */
    fun signOut(
        refreshToken: String = "",
        allDevices: Boolean = false,
        callback: (Boolean, String) -> Unit
    ) {
        val currentChannel = getChannel() ?: run {
            callback(false, "Not connected")
            return
        }

        val methodDesc = MethodDescriptor.newBuilder<SignOutRequestProto, SimpleAuthResponseProto>()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.AuthService/SignOut")
            .setRequestMarshaller(SignOutRequestMarshaller())
            .setResponseMarshaller(SimpleAuthResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
        call.start(object : ClientCall.Listener<SimpleAuthResponseProto>() {
            override fun onMessage(message: SimpleAuthResponseProto) {
                callback(message.success, message.message)
            }
            override fun onClose(status: Status, trailers: Metadata) {
                if (!status.isOk) {
                    callback(false, status.description ?: "Sign out failed")
                }
            }
        }, Metadata())
        call.sendMessage(SignOutRequestProto(refreshToken = refreshToken, allDevices = allDevices))
        call.halfClose()
        call.request(1)
    }

    /**
     * RevokeDevice — deactivates a specific device for the authenticated user.
     */
    fun revokeDevice(
        deviceId: String,
        callback: (Boolean, String) -> Unit
    ) {
        val currentChannel = getChannel() ?: run {
            callback(false, "Not connected")
            return
        }

        val methodDesc = MethodDescriptor.newBuilder<RevokeDeviceRequestProto, SimpleAuthResponseProto>()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.AuthService/RevokeDevice")
            .setRequestMarshaller(RevokeDeviceRequestMarshaller())
            .setResponseMarshaller(SimpleAuthResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
        call.start(object : ClientCall.Listener<SimpleAuthResponseProto>() {
            override fun onMessage(message: SimpleAuthResponseProto) {
                callback(message.success, message.message)
            }
            override fun onClose(status: Status, trailers: Metadata) {
                if (!status.isOk) {
                    callback(false, status.description ?: "Revoke failed")
                }
            }
        }, Metadata())
        call.sendMessage(RevokeDeviceRequestProto(deviceId = deviceId))
        call.halfClose()
        call.request(1)
    }
}
