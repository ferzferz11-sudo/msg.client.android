package lavender.client.android.data.grpc

import android.content.Context
import android.util.Log
import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Status
import lavender.client.android.data.auth.AuthManager

/**
 * ClientInterceptor that automatically attaches a JWT Bearer token to all gRPC calls.
 *
 * Rules:
 * - Skips AuthService calls (SignIn, SignUp, Refresh — no token yet)
 * - Skips calls when no JWT token is available (legacy v1 auth)
 * - Only attaches Bearer token when AuthManager has a valid JWT
 *
 * This ensures backward compatibility with v1 servers (prod):
 * - If server doesn't support JWT, client uses legacy flow → no token → interceptor is a no-op
 * - If server supports JWT (dev), client stores tokens → interceptor attaches them
 */
class BearerTokenInterceptor(
    private val context: Context
) : ClientInterceptor {

    companion object {
        private const val TAG = "BearerTokenInterceptor"
        private val AUTHORIZATION_KEY: Metadata.Key<String> =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)
    }

    override fun <ReqT, RespT> interceptCall(
        method: MethodDescriptor<ReqT, RespT>,
        callOptions: CallOptions,
        next: Channel
    ): ClientCall<ReqT, RespT> {

        // Skip AuthService — no token available yet
        if (method.fullMethodName.startsWith("/messenger.AuthService/")) {
            return next.newCall(method, callOptions)
        }

        // Skip Chat stream — legacy auth uses password in first message
        // Server's AuthStreamInterceptor also skips ChatService/Chat
        if (method.fullMethodName == "/messenger.ChatService/Chat") {
            return next.newCall(method, callOptions)
        }

        // Only attach token if we have one (JWT v2 auth)
        val bearerToken = AuthManager.getBearerToken(context)
        if (bearerToken == null) {
            // No token — legacy v1 flow, proceed without auth header
            return next.newCall(method, callOptions)
        }

        Log.d(TAG, "Attaching Bearer token for ${method.fullMethodName}")

        return object : io.grpc.ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
            next.newCall(method, callOptions)
        ) {
            override fun start(responseListener: ClientCall.Listener<RespT>, headers: Metadata) {
                headers.put(AUTHORIZATION_KEY, bearerToken)
                super.start(responseListener, headers)
            }
        }
    }
}
