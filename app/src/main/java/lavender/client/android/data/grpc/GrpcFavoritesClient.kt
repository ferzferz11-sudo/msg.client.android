package lavender.client.android.data.grpc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import lavender.client.android.data.models.Message
import lavender.client.android.data.proto.*
import lavender.client.android.data.proto.ProtoUtils

/**
 * Handles favorites operations: addFavorite, removeFavorite, getFavorites, saveFavoriteMessage.
 *
 * Extracted from RealGrpcClient v1.1.3.25 to reduce God Object anti-pattern.
 */
class GrpcFavoritesClient(
    private val getChannel: () -> io.grpc.ManagedChannel?,
    private val getUserId: () -> String?,
    private val getUsername: () -> String?,
    private val scope: kotlinx.coroutines.CoroutineScope
) {

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
            override fun onMessage(message: RemoveFavoriteResponseProto) { callback(message.success, if (message.success) "Removed" else "Failed") }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {}
        }, io.grpc.Metadata())
        call.sendMessage(RemoveFavoriteRequestProto(userId, messageId))
        call.halfClose()
        call.request(1)
    }

    fun getFavorites(userId: String, callback: (List<Message>) -> Unit) {
        // Load from cache first
        scope.launch(Dispatchers.IO) {
            // Note: favorites room ID pattern is "favorites_<username>"
            // Cache loading would need db() access — delegated to caller for now
        }

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
                val msgs = message.messages.map { proto ->
                    Message(
                        id = proto.id,
                        user = proto.user,
                        text = proto.text,
                        timestamp = proto.createdAt?.seconds ?: 0L,
                        roomId = "favorites_${getUsername() ?: ""}"
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

    fun saveFavoriteMessage(message: Message, callback: (Boolean, String) -> Unit) {
        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<MessageProto, AddFavoriteResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/SaveFavoriteMessage")
                .setRequestMarshaller(MessageProtoMarshaller())
                .setResponseMarshaller(AddFavoriteResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<AddFavoriteResponseProto>() {
            override fun onMessage(message: AddFavoriteResponseProto) { callback(message.success, message.message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {}
        }, io.grpc.Metadata())
        call.sendMessage(ProtoUtils.createMessageProto(message))
        call.halfClose()
        call.request(1)
    }
}
