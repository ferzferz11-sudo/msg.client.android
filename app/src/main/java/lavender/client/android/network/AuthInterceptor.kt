package lavender.client.android.network

import android.content.Context
import lavender.client.android.data.auth.AuthManager
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val context: Context) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.encodedPath.startsWith("/info") ||
            request.url.encodedPath.startsWith("/health")) {
            return chain.proceed(request)
        }
        val bearer = AuthManager.getBearerToken(context)
        val newRequest = if (bearer != null) {
            request.newBuilder().addHeader("Authorization", bearer).build()
        } else request
        return chain.proceed(newRequest)
    }
}
