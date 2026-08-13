package lavender.client.android.data.grpc

import android.util.Log
import lavender.client.android.data.proto.*
import kotlin.coroutines.resume

/**
 * GrpcStickerClient — client for StickerService (JWT Bearer auth).
 */
object GrpcStickerClient {
    private const val TAG = "GrpcStickerClient"

    suspend fun createStickerPack(title: String, name: String): CreateStickerPackResponseProto? {
        return try {
            unaryCall(
                fullMethod = "messenger.StickerService/CreateStickerPack",
                requestMarshaller = CreateStickerPackRequestMarshaller(),
                responseMarshaller = CreateStickerPackResponseMarshaller(),
                request = CreateStickerPackRequestProto(title = title, name = name)
            )
        } catch (e: Exception) {
            Log.w(TAG, "createStickerPack failed: ${e.message}"); null
        }
    }

    suspend fun addSticker(packId: String, lottieUrl: String, thumbnailUrl: String, emoji: String, width: Int, height: Int): AddStickerResponseProto? {
        return try {
            unaryCall(
                fullMethod = "messenger.StickerService/AddSticker",
                requestMarshaller = AddStickerRequestMarshaller(),
                responseMarshaller = AddStickerResponseMarshaller(),
                request = AddStickerRequestProto(packId, lottieUrl, thumbnailUrl, emoji, width, height)
            )
        } catch (e: Exception) {
            Log.w(TAG, "addSticker failed: ${e.message}"); null
        }
    }

    suspend fun removeSticker(packId: String, stickerId: String): RemoveStickerResponseProto? {
        return try {
            unaryCall(
                fullMethod = "messenger.StickerService/RemoveSticker",
                requestMarshaller = RemoveStickerRequestMarshaller(),
                responseMarshaller = RemoveStickerResponseMarshaller(),
                request = RemoveStickerRequestProto(packId, stickerId)
            )
        } catch (e: Exception) {
            Log.w(TAG, "removeSticker failed: ${e.message}"); null
        }
    }

    suspend fun deleteStickerPack(packId: String): DeleteStickerPackResponseProto? {
        return try {
            unaryCall(
                fullMethod = "messenger.StickerService/DeleteStickerPack",
                requestMarshaller = DeleteStickerPackRequestMarshaller(),
                responseMarshaller = DeleteStickerPackResponseMarshaller(),
                request = DeleteStickerPackRequestProto(packId)
            )
        } catch (e: Exception) {
            Log.w(TAG, "deleteStickerPack failed: ${e.message}"); null
        }
    }

    suspend fun getUserStickerPacks(): GetUserStickerPacksResponseProto? {
        return try {
            unaryCall(
                fullMethod = "messenger.StickerService/GetUserStickerPacks",
                requestMarshaller = GetUserStickerPacksRequestMarshaller(),
                responseMarshaller = GetUserStickerPacksResponseMarshaller(),
                request = GetUserStickerPacksRequestProto()
            )
        } catch (e: Exception) {
            Log.w(TAG, "getUserStickerPacks failed: ${e.message}"); null
        }
    }

    suspend fun getPublicStickerPacks(cursor: String = "", limit: Int = 30): GetPublicStickerPacksResponseProto? {
        return try {
            unaryCall(
                fullMethod = "messenger.StickerService/GetPublicStickerPacks",
                requestMarshaller = GetPublicStickerPacksRequestMarshaller(),
                responseMarshaller = GetPublicStickerPacksResponseMarshaller(),
                request = GetPublicStickerPacksRequestProto(cursor, limit)
            )
        } catch (e: Exception) {
            Log.w(TAG, "getPublicStickerPacks failed: ${e.message}"); null
        }
    }

    suspend fun getStickerPack(packId: String): GetStickerPackResponseProto? {
        return try {
            unaryCall(
                fullMethod = "messenger.StickerService/GetStickerPack",
                requestMarshaller = GetStickerPackRequestMarshaller(),
                responseMarshaller = GetStickerPackResponseMarshaller(),
                request = GetStickerPackRequestProto(packId)
            )
        } catch (e: Exception) {
            Log.w(TAG, "getStickerPack failed: ${e.message}"); null
        }
    }

    suspend fun submitForApproval(packId: String): SubmitForApprovalResponseProto? {
        return try {
            unaryCall(
                fullMethod = "messenger.StickerService/SubmitForApproval",
                requestMarshaller = SubmitForApprovalRequestMarshaller(),
                responseMarshaller = SubmitForApprovalResponseMarshaller(),
                request = SubmitForApprovalRequestProto(packId)
            )
        } catch (e: Exception) {
            Log.w(TAG, "submitForApproval failed: ${e.message}"); null
        }
    }

    suspend fun approveStickerPack(packId: String, approved: Boolean, reason: String = ""): ApproveStickerPackResponseProto? {
        return try {
            unaryCall(
                fullMethod = "messenger.StickerService/ApproveStickerPack",
                requestMarshaller = ApproveStickerPackRequestMarshaller(),
                responseMarshaller = ApproveStickerPackResponseMarshaller(),
                request = ApproveStickerPackRequestProto(packId, approved, reason)
            )
        } catch (e: Exception) {
            Log.w(TAG, "approveStickerPack failed: ${e.message}"); null
        }
    }

    suspend fun getPendingStickerPacks(cursor: String = "", limit: Int = 30): GetPendingStickerPacksResponseProto? {
        return try {
            unaryCall(
                fullMethod = "messenger.StickerService/GetPendingStickerPacks",
                requestMarshaller = GetPendingStickerPacksRequestMarshaller(),
                responseMarshaller = GetPendingStickerPacksResponseMarshaller(),
                request = GetPendingStickerPacksRequestProto(cursor, limit)
            )
        } catch (e: Exception) {
            Log.w(TAG, "getPendingStickerPacks failed: ${e.message}"); null
        }
    }

    suspend fun searchStickerPacks(query: String, limit: Int = 20): SearchStickerPacksResponseProto? {
        return try {
            unaryCall(
                fullMethod = "messenger.StickerService/SearchStickerPacks",
                requestMarshaller = SearchStickerPacksRequestMarshaller(),
                responseMarshaller = SearchStickerPacksResponseMarshaller(),
                request = SearchStickerPacksRequestProto(query, limit)
            )
        } catch (e: Exception) {
            Log.w(TAG, "searchStickerPacks failed: ${e.message}"); null
        }
    }

    suspend fun updateStickerPack(packId: String, title: String = "", coverStickerId: String = ""): UpdateStickerPackResponseProto? {
        return try {
            unaryCall(
                fullMethod = "messenger.StickerService/UpdateStickerPack",
                requestMarshaller = UpdateStickerPackRequestMarshaller(),
                responseMarshaller = UpdateStickerPackResponseMarshaller(),
                request = UpdateStickerPackRequestProto(packId, title, coverStickerId)
            )
        } catch (e: Exception) {
            Log.w(TAG, "updateStickerPack failed: ${e.message}"); null
        }
    }

    suspend fun setFeaturedStickerPack(packId: String, featured: Boolean): SetFeaturedStickerPackResponseProto? {
        return try {
            unaryCall(
                fullMethod = "messenger.StickerService/SetFeaturedStickerPack",
                requestMarshaller = SetFeaturedStickerPackRequestMarshaller(),
                responseMarshaller = SetFeaturedStickerPackResponseMarshaller(),
                request = SetFeaturedStickerPackRequestProto(packId, featured)
            )
        } catch (e: Exception) {
            Log.w(TAG, "setFeaturedStickerPack failed: ${e.message}"); null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <ReqT, RespT> unaryCall(
        fullMethod: String,
        requestMarshaller: io.grpc.MethodDescriptor.Marshaller<ReqT>,
        responseMarshaller: io.grpc.MethodDescriptor.Marshaller<RespT>,
        request: ReqT
    ): RespT? = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        val channel = RealGrpcClient.getChannel()
        if (channel == null) { cont.resume(null); return@suspendCancellableCoroutine }

        val method = io.grpc.MethodDescriptor.newBuilder<ReqT, RespT>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName(fullMethod)
            .setRequestMarshaller(requestMarshaller)
            .setResponseMarshaller(responseMarshaller)
            .build()

        val call = channel.newCall(method, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<RespT>() {
            private var response: RespT? = null
            override fun onMessage(message: RespT) { response = message }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (status.isOk) cont.resume(response)
                else {
                    if (status.code == io.grpc.Status.Code.UNAUTHENTICATED)
                        Log.w(TAG, "$fullMethod auth failed")
                    cont.resume(null)
                }
            }
        }, io.grpc.Metadata())
        call.sendMessage(request); call.halfClose(); call.request(1)
    }
}
