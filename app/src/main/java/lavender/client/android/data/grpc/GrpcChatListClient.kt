package lavender.client.android.data.grpc

import android.util.Log
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.data.models.Message
import lavender.client.android.data.models.ErrorHandler
import lavender.client.android.data.proto.*

/**
 * Handles chat list operations: getChats, pinChat, searchChats, archiveChat, pinMessage.
 *
 * Owns all ChatList-related RPC calls to ChatService.
 * Does NOT own channel management — uses channel from GrpcConnectionManager.
 *
 * Extracted from RealGrpcClient v1.1.3.25 to reduce God Object anti-pattern.
 * Split in v1.1.3.40: management methods → GrpcChatManagementClient, aux methods → GrpcChatAuxClient.
 */
class GrpcChatListClient(
    private val getChannel: () -> io.grpc.ManagedChannel?,
    private val getUserId: () -> String?
) {
    companion object {
        private const val TAG = "GrpcChatListClient"
    }

    // ======= Chat List =======

    fun getChats(username: String, skipCache: Boolean = false, callback: (List<ChatInfo>) -> Unit) {
        val currentChannel = getChannel()
        if (currentChannel == null || currentChannel.isShutdown || currentChannel.isTerminated) {
            Log.w(TAG, "getChats: channel not available")
            callback(emptyList())
            return
        }
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<GetChatsRequestProto, GetChatsResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/GetChats")
            .setRequestMarshaller(GetChatsRequestMarshaller())
            .setResponseMarshaller(GetChatsResponseMarshaller())
            .build()
        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<GetChatsResponseProto>() {
            override fun onMessage(message: GetChatsResponseProto) {
                Log.d(TAG, "getChats: received ${message.chats.size} chats")
                val chats = message.chats.map { proto ->
                    ChatInfo(
                        proto.id, proto.name, proto.type, proto.participants,
                        proto.createdAt?.let { it.seconds * 1000 + it.nanos / 1000000 } ?: 0L,
                        proto.unreadCount,
                        proto.lastMessageTime?.let { it.seconds * 1000 + it.nanos / 1000000 } ?: 0L,
                        proto.creator, proto.lastMessageText, proto.avatarUrl, proto.fullAvatarUrl,
                        proto.lastMessageUsername, false, proto.lastMessageHasImage, proto.allowMembersToAdd,
                        proto.conferenceStartTime?.let { it.seconds * 1000 + it.nanos / 1000000 } ?: 0L,
                        proto.isSecret, proto.peerPublicKey, proto.e2eeReady,
                        proto.activeAgentId, proto.agentMode
                    )
                }
                if (chats.isNotEmpty()) {
                    callback(chats)
                }
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    Log.w(TAG, "getChats: onClose error: ${status.code} - ${status.description}")
                    callback(emptyList())
                }
            }
        }, io.grpc.Metadata())
        call.sendMessage(GetChatsRequestProto(username = username, userId = getUserId() ?: ""))
        call.halfClose()
        call.request(1)
    }

    fun getAllChats(callback: (List<ChatInfo>) -> Unit) {
        val currentChannel = getChannel() ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<GetAllChatsRequestProto, GetAllChatsResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/GetAllChats")
            .setRequestMarshaller(GetAllChatsRequestMarshaller())
            .setResponseMarshaller(GetAllChatsResponseMarshaller())
            .build()
        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<GetAllChatsResponseProto>() {
            override fun onMessage(message: GetAllChatsResponseProto) {
                Log.d(TAG, "getAllChats: received ${message.chats.size} chats")
                callback(message.chats.map { proto ->
                    ChatInfo(
                        id = proto.id, name = proto.name, type = proto.type,
                        participants = proto.participants,
                        createdAt = proto.createdAt?.let { it.seconds * 1000 + it.nanos / 1000000 } ?: 0L,
                        unreadCount = proto.unreadCount,
                        lastMessageTime = proto.lastMessageTime?.let { it.seconds * 1000 + it.nanos / 1000000 } ?: 0L,
                        creator = proto.creator, lastMessageText = proto.lastMessageText,
                        avatarUrl = proto.avatarUrl, fullAvatarUrl = proto.fullAvatarUrl,
                        lastMessageUsername = proto.lastMessageUsername, isMuted = false,
                        lastMessageHasImage = proto.lastMessageHasImage,
                        allowMembersToAdd = proto.allowMembersToAdd,
                        conferenceStartTime = proto.conferenceStartTime?.let { it.seconds * 1000 + it.nanos / 1000000 } ?: 0L,
                        isSecret = proto.isSecret, peerPublicKey = proto.peerPublicKey,
                        e2eeReady = proto.e2eeReady
                    )
                })
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) ErrorHandler.handle("GrpcChatListClient.getAllChats", "Status: ${status.code} — ${status.description}")
            }
        }, io.grpc.Metadata())
        call.sendMessage(GetAllChatsRequestProto())
        call.halfClose()
        call.request(1)
    }

    // ======= ChatList v2: Pin/Unpin =======

    suspend fun pinChat(chatId: String): Boolean {
        val userId = getUserId() ?: return false
        return unaryCallWithClass(
            getChannel = getChannel,
            fullMethod = "messenger.ChatService/PinChat",
            request = PinChatRequestProto(userId = userId, chatId = chatId),
            responseType = PinChatResponseProto::class.java
        )?.success ?: false
    }

    suspend fun unpinChat(chatId: String): Boolean {
        val userId = getUserId() ?: return false
        return unaryCallWithClass(
            getChannel = getChannel,
            fullMethod = "messenger.ChatService/UnPinChat",
            request = UnPinChatRequestProto(userId = userId, chatId = chatId),
            responseType = UnPinChatResponseProto::class.java
        )?.success ?: false
    }

    // ======= ChatList v2: Search =======

    suspend fun searchChats(query: String, limit: Int, offset: Int): List<ChatInfo> {
        val userId = getUserId() ?: return emptyList()
        val response = unaryCallWithClass(
            getChannel = getChannel,
            fullMethod = "messenger.ChatService/SearchChats",
            request = SearchChatsRequestProto(userId = userId, query = query, limit = limit, offset = offset),
            responseType = SearchChatsResponseProto::class.java
        )
        return response?.chats?.map { proto ->
            ChatInfo(
                id = proto.id, name = proto.name, type = proto.type,
                participants = proto.participants,
                createdAt = proto.createdAt?.seconds ?: 0L,
                unreadCount = proto.unreadCount,
                lastMessageTime = proto.lastMessageTime?.seconds ?: 0L,
                creator = proto.creator, lastMessageText = proto.lastMessageText,
                avatarUrl = proto.avatarUrl, fullAvatarUrl = proto.fullAvatarUrl,
                lastMessageUsername = proto.lastMessageUsername,
                lastMessageHasImage = proto.lastMessageHasImage,
                allowMembersToAdd = proto.allowMembersToAdd,
                isPinned = proto.isPinned, isMuted = proto.isMuted,
                isArchived = proto.isArchived, pinnedAt = proto.pinnedAt
            )
        } ?: emptyList()
    }

    // ======= ChatList v2: Archive =======

    suspend fun archiveChat(chatId: String): Boolean {
        val userId = getUserId() ?: return false
        return unaryCallWithClass(
            getChannel = getChannel,
            fullMethod = "messenger.ChatService/ArchiveChat",
            request = ArchiveChatRequestProto(userId = userId, chatId = chatId),
            responseType = ArchiveChatResponseProto::class.java
        )?.success ?: false
    }

    suspend fun unarchiveChat(chatId: String): Boolean {
        val userId = getUserId() ?: return false
        return unaryCallWithClass(
            getChannel = getChannel,
            fullMethod = "messenger.ChatService/UnarchiveChat",
            request = UnarchiveChatRequestProto(userId = userId, chatId = chatId),
            responseType = UnarchiveChatResponseProto::class.java
        )?.success ?: false
    }

    // ======= ChatList v2: Pin Message =======

    suspend fun pinMessage(chatId: String, messageId: String): Boolean {
        val userId = getUserId() ?: return false
        return unaryCallWithClass(
            getChannel = getChannel,
            fullMethod = "messenger.ChatService/PinMessage",
            request = PinMessageRequestProto(userId = userId, chatId = chatId, messageId = messageId),
            responseType = PinMessageResponseProto::class.java
        )?.success ?: false
    }

    suspend fun unpinMessage(chatId: String, messageId: String): Boolean {
        val userId = getUserId() ?: return false
        return unaryCallWithClass(
            getChannel = getChannel,
            fullMethod = "messenger.ChatService/UnPinMessage",
            request = UnPinMessageRequestProto(userId = userId, chatId = chatId, messageId = messageId),
            responseType = UnPinMessageResponseProto::class.java
        )?.success ?: false
    }

    suspend fun getPinnedMessages(chatId: String): List<Message> {
        val userId = getUserId() ?: return emptyList()
        val response = unaryCallWithClass(
            getChannel = getChannel,
            fullMethod = "messenger.ChatService/GetPinnedMessages",
            request = GetPinnedMessagesRequestProto(userId = userId, chatId = chatId),
            responseType = GetPinnedMessagesResponseProto::class.java
        )
        return response?.messages?.map { proto ->
            Message(
                id = proto.id, user = proto.user, text = proto.text,
                timestamp = proto.createdAt?.seconds ?: 0L
            )
        } ?: emptyList()
    }

    // ======= Chat List Version =======

    fun getChatListVersion(username: String, callback: (Long) -> Unit) {
        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<GetChatListVersionRequestProto, GetChatListVersionResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/GetChatListVersion")
                .setRequestMarshaller(GetChatListVersionRequestMarshaller())
                .setResponseMarshaller(GetChatListVersionResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<GetChatListVersionResponseProto>() {
            override fun onMessage(message: GetChatListVersionResponseProto) { callback(message.version) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {}
        }, io.grpc.Metadata())
        call.sendMessage(GetChatListVersionRequestProto(username = username, userId = getUserId() ?: ""))
        call.halfClose()
        call.request(1)
    }
}
