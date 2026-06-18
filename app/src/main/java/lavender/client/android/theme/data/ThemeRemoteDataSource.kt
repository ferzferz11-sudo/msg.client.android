package lavender.client.android.theme.data

import kotlinx.coroutines.suspendCancellableCoroutine
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.proto.CustomThemeProto
import kotlin.coroutines.resume

import kotlinx.coroutines.withTimeoutOrNull
import lavender.client.android.data.grpc.GrpcClientExtensionsKt.*

class ThemeRemoteDataSource(
    private val grpcClient: GrpcClient = GrpcClient,
) {
    suspend fun getThemes(queryId: String): List<CustomThemeProto> =
        withTimeoutOrNull(5000) {
            suspendCancellableCoroutine { cont ->
                grpcClient.getThemes(queryId) { _, themes ->
                    if (cont.isActive) cont.resume(themes)
                }
            }
        } ?: emptyList()
}

