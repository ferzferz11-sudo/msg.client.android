package lavender.client.android.theme.data

import kotlinx.coroutines.suspendCancellableCoroutine
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.proto.CustomThemeProto
import kotlin.coroutines.resume

class ThemeRemoteDataSource(
    private val grpcClient: GrpcClient = GrpcClient,
) {
    suspend fun getThemes(queryId: String): List<CustomThemeProto> =
        suspendCancellableCoroutine { cont ->
            grpcClient.getThemes(queryId) { _, themes ->
                cont.resume(themes)
            }
        }
}

