package lavender.client.android.data.grpc

import android.content.Context
import android.util.Log
import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import lavender.client.android.data.auth.AuthManager

/**
 * ClientInterceptor that automatically attaches a JWT Bearer token to gRPC calls.
 *
 * Rules:
 * - Skips AuthService calls (SignIn, SignUp, Refresh — no token yet)
 * - Skips Chat stream for v1 servers (chat < "2.0" or /info unavailable)
 * - For v2 servers (chat >= "2.0"), attaches Bearer token to Chat stream
 * - Skips calls when no JWT token is available (legacy v1 auth)
 *
 * Backward compatibility:
 * - v1 server: no token → interceptor is a no-op for all calls
 * - v2 server: token available → attaches to all calls including Chat stream
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

        // For Chat stream: only skip if server doesn't support v2
        // v2 servers (chat >= "2.0") use JWT token in first message, so we attach it
        if (method.fullMethodName == "/messenger.ChatService/Chat") {
            if (!ProfileClient.isChatV2Supported()) {
                // v1 server — skip interceptor, use password auth in first message
                return next.newCall(method, callOptions)
            }
            // v2 server — continue to attach token below
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
