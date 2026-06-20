package lavender.client.android.data.grpc

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
 * Skips AuthService calls (SignIn, SignUp, Refresh — no token yet).
 * For all other calls, attaches Bearer token if available.
 */
class BearerTokenInterceptor(
    private val context: android.content.Context
) : ClientInterceptor {

    companion object {
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

        val bearerToken = AuthManager.getBearerToken(context)
        if (bearerToken == null) {
            return next.newCall(method, callOptions)
        }

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
