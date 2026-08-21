package lavender.client.android.data.grpc

import android.util.Log
import lavender.client.android.data.models.ErrorHandler
import lavender.client.android.data.models.Message
import lavender.client.android.data.models.Reaction
import lavender.client.android.data.proto.*

/**
 * Handles saved messages operations: addSavedMessage, removeSavedMessage, getSavedMessages.
 *
 * Extracted from RealGrpcClient v1.1.3.25 to reduce God Object anti-pattern.
 */
class GrpcSavedMessagesClient(
    private val getChannel: () -> io.grpc.ManagedChannel?,
    @Suppress("UNUSED_PARAMETER") private val getUserId: () -> String?,
    @Suppress("UNUSED_PARAMETER") private val getUsername: () -> String?,
    @Suppress("UNUSED_PARAMETER") private val scope: kotlinx.coroutines.CoroutineScope,
    private val allUsers: () -> List<UserInfoProto> = { emptyList() },
    private val refreshToken: (() -> Unit)? = null
) {
    companion object {
        private const val TAG = "GrpcSavedMsgClient"
        private const val REMOVED = "Removed"
        private const val FAILED = "Failed"
    }

    fun addSavedMessage(userId: String, messageId: String, callback: (Boolean, String) -> Unit) {
        val currentChannel = getChannel()
        if (currentChannel == null) {
            callback(false, "No channel available")
            return
        }
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<AddSavedMessageRequestProto, AddSavedMessageResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/AddSavedMessage")
                .setRequestMarshaller(AddSavedMessageRequestMarshaller())
                .setResponseMarshaller(AddSavedMessageResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<AddSavedMessageResponseProto>() {
            override fun onMessage(message: AddSavedMessageResponseProto) { callback(message.success, message.message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    Log.e(TAG, "AddFavorite: error ${status.code} — ${status.description}")
                    callback(false, status.description ?: FAILED)
                }
            }
        }, io.grpc.Metadata())
        call.sendMessage(AddSavedMessageRequestProto(userId, messageId))
        call.halfClose()
        call.request(1)
    }

    fun removeSavedMessage(userId: String, messageId: String, callback: (Boolean, String) -> Unit) {
        val currentChannel = getChannel()
        if (currentChannel == null) {
            callback(false, "No channel available")
            return
        }
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<RemoveSavedMessageRequestProto, RemoveSavedMessageResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/RemoveSavedMessage")
                .setRequestMarshaller(RemoveSavedMessageRequestMarshaller())
                .setResponseMarshaller(RemoveSavedMessageResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<RemoveSavedMessageResponseProto>() {
            override fun onMessage(message: RemoveSavedMessageResponseProto) { callback(message.success, if (message.success) REMOVED else FAILED) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    Log.e(TAG, "RemoveFavorite: error ${status.code} — ${status.description}")
                    callback(false, status.description ?: FAILED)
                }
            }
        }, io.grpc.Metadata())
        call.sendMessage(RemoveSavedMessageRequestProto(userId, messageId))
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
            ErrorHandler.handle("GrpcSavedMessagesClient.parseReactions", e)
            emptyList()
        }
    }

    fun getSavedMessages(userId: String, callback: (List<Message>) -> Unit) {
        getSavedMessagesInternal(userId, callback, retryCount = 0)
    }

    private fun getSavedMessagesInternal(userId: String, callback: (List<Message>) -> Unit, retryCount: Int) {
        val currentChannel = getChannel()
        if (currentChannel == null) {
            ErrorHandler.handle("$TAG.getSavedMessages", Exception("No channel available"))
            callback(emptyList())
            return
        }
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<GetSavedMessagesRequestProto, GetSavedMessagesResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/GetSavedMessages")
                .setRequestMarshaller(GetSavedMessagesRequestMarshaller())
                .setResponseMarshaller(GetSavedMessagesResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<GetSavedMessagesResponseProto>() {
            override fun onMessage(message: GetSavedMessagesResponseProto) {
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
                        roomId = "saved_messages_${getUsername() ?: ""}",
                        imageUrl = imageUrl,
                        voiceUrl = voiceUrl,
                        duration = duration,
                        userId = proto.senderId
                    )
                }
                Log.d(TAG, "GetFavorites: received ${msgs.size} messages for userId=$userId")
                callback(msgs)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    if (status.code == io.grpc.Status.Code.UNAUTHENTICATED && retryCount < 1) {
                        Log.w(TAG, "GetFavorites: UNAUTHENTICATED — refreshing token and retrying")
                        refreshToken?.invoke()
                        getSavedMessagesInternal(userId, callback, retryCount + 1)
                        return
                    }
                    Log.e(TAG, "GetFavorites: error ${status.code} — ${status.description}")
                    ErrorHandler.handle("$TAG.getSavedMessages", "Status: ${status.code} — ${status.description}")
                    callback(emptyList())
                }
            }
        }, io.grpc.Metadata())
        call.sendMessage(GetSavedMessagesRequestProto(userId))
        call.halfClose()
        call.request(1)
    }
}
