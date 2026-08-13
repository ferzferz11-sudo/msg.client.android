package lavender.client.android.data.grpc

import lavender.client.android.data.models.ErrorHandler
import lavender.client.android.data.models.Message
import lavender.client.android.data.models.Reaction
import lavender.client.android.data.proto.*

/**
 * Handles favorites operations: addFavorite, removeFavorite, getFavorites.
 *
 * Extracted from RealGrpcClient v1.1.3.25 to reduce God Object anti-pattern.
 */
class GrpcFavoritesClient(
    private val getChannel: () -> io.grpc.ManagedChannel?,
    @Suppress("UNUSED_PARAMETER") private val getUserId: () -> String?,
    @Suppress("UNUSED_PARAMETER") private val getUsername: () -> String?,
    @Suppress("UNUSED_PARAMETER") private val scope: kotlinx.coroutines.CoroutineScope,
    private val allUsers: () -> List<UserInfoProto> = { emptyList() }
) {
    companion object {
        private const val REMOVED = "Removed"
        private const val FAILED = "Failed"
    }

    fun addFavorite(userId: String, messageId: String, callback: (Boolean, String) -> Unit) {
        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<AddFavoriteRequestProto, AddFavoriteResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/AddFavorite")
                .setRequestMarshaller(AddFavoriteRequestMarshaller())
                .setResponseMarshaller(AddFavoriteResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<AddFavoriteResponseProto>() {
            override fun onMessage(message: AddFavoriteResponseProto) { callback(message.success, message.message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {}
        }, io.grpc.Metadata())
        call.sendMessage(AddFavoriteRequestProto(userId, messageId))
        call.halfClose()
        call.request(1)
    }

    fun removeFavorite(userId: String, messageId: String, callback: (Boolean, String) -> Unit) {
        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<RemoveFavoriteRequestProto, RemoveFavoriteResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/RemoveFavorite")
                .setRequestMarshaller(RemoveFavoriteRequestMarshaller())
                .setResponseMarshaller(RemoveFavoriteResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<RemoveFavoriteResponseProto>() {
            override fun onMessage(message: RemoveFavoriteResponseProto) { callback(message.success, if (message.success) REMOVED else FAILED) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {}
        }, io.grpc.Metadata())
        call.sendMessage(RemoveFavoriteRequestProto(userId, messageId))
        call.halfClose()
        call.request(1)
    }

    private fun parseReactions(reactionsBytes: ByteArray): List<Reaction> {
        if (reactionsBytes.isEmpty()) return emptyList()
        return try {
            val obj = org.json.JSONObject(String(reactionsBytes))
            val result = mutableListOf<Reaction>()
            for (key in obj.keys()) {
                val emoji = obj.getString(key)
                if (emoji.isNotEmpty()) {
                    result.add(Reaction(user = key, emoji = emoji))
                }
            }
            result
        } catch (e: Exception) {
            ErrorHandler.handle("GrpcFavoritesClient.parseReactions", e)
            emptyList()
        }
    }

    fun getFavorites(userId: String, callback: (List<Message>) -> Unit) {
        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<GetFavoritesRequestProto, GetFavoritesResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/GetFavorites")
                .setRequestMarshaller(GetFavoritesRequestMarshaller())
                .setResponseMarshaller(GetFavoritesResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<GetFavoritesResponseProto>() {
            override fun onMessage(message: GetFavoritesResponseProto) {
                val users = allUsers()
                val msgs = message.messages.map { proto ->
                    val username = users.firstOrNull { it.userId == proto.senderId }?.username ?: ""
                    val timestamp = proto.createdAt?.let { it.seconds * 1000 + (it.nanos / 1000000) } ?: 0L
                    var imageUrl = ""; var voiceUrl = ""; var duration = 0
                    when {
                        proto.media != null -> {
                            when (proto.media.type) {
                                "image" -> imageUrl = proto.media.url
                                "voice" -> { voiceUrl = proto.media.url; duration = proto.media.duration }
                            }
                        }
                    }
                    val reactions = parseReactions(proto.reactions)
                    Message(
                        id = proto.id,
                        user = username,
                        text = proto.text,
                        timestamp = timestamp,
                        reactions = reactions,
                        roomId = "favorites_${getUsername() ?: ""}",
                        imageUrl = imageUrl,
                        voiceUrl = voiceUrl,
                        duration = duration,
                        userId = proto.senderId
                    )
                }
                callback(msgs)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {}
        }, io.grpc.Metadata())
        call.sendMessage(GetFavoritesRequestProto(userId))
        call.halfClose()
        call.request(1)
    }
}
