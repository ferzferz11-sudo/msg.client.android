package lavender.client.android.data.grpc

import lavender.client.android.data.models.ChatInfo
import lavender.client.android.data.models.Message
import lavender.client.android.data.proto.*

class GrpcChatListV2Client(
    private val getChannel: () -> io.grpc.ManagedChannel?,
    private val getUserId: () -> String?
) {
    companion object {
        private const val TAG = "GrpcChatListV2Client"
    }

    suspend fun pinChat(chatId: String): Boolean {
        val userId = getUserId() ?: return false
        return unaryCallWithClass(
            getChannel = getChannel,
            fullMethod = "messenger.ChatService/PinChat",
            request = PinChatRequestProto(userId = userId, chatId = chatId),
            responseType = PinChatResponseProto::class.java,
            requestMarshaller = PinChatRequestMarshaller(),
            responseMarshaller = PinChatResponseMarshaller()
        )?.success ?: false
    }

    suspend fun unpinChat(chatId: String): Boolean {
        val userId = getUserId() ?: return false
        return unaryCallWithClass(
            getChannel = getChannel,
            fullMethod = "messenger.ChatService/UnPinChat",
            request = UnPinChatRequestProto(userId = userId, chatId = chatId),
            responseType = UnPinChatResponseProto::class.java,
            requestMarshaller = UnPinChatRequestMarshaller(),
            responseMarshaller = UnPinChatResponseMarshaller()
        )?.success ?: false
    }

    suspend fun searchChats(query: String, limit: Int, offset: Int): List<ChatInfo> {
        val userId = getUserId() ?: return emptyList()
        val response = unaryCallWithClass(
            getChannel = getChannel,
            fullMethod = "messenger.ChatService/SearchChats",
            request = SearchChatsRequestProto(userId = userId, query = query, limit = limit, offset = offset),
            responseType = SearchChatsResponseProto::class.java,
            requestMarshaller = SearchChatsRequestMarshaller(),
            responseMarshaller = SearchChatsResponseMarshaller()
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

    suspend fun archiveChat(chatId: String): Boolean {
        val userId = getUserId() ?: return false
        return unaryCallWithClass(
            getChannel = getChannel,
            fullMethod = "messenger.ChatService/ArchiveChat",
            request = ArchiveChatRequestProto(userId = userId, chatId = chatId),
            responseType = ArchiveChatResponseProto::class.java,
            requestMarshaller = ArchiveChatRequestMarshaller(),
            responseMarshaller = ArchiveChatResponseMarshaller()
        )?.success ?: false
    }

    suspend fun unarchiveChat(chatId: String): Boolean {
        val userId = getUserId() ?: return false
        return unaryCallWithClass(
            getChannel = getChannel,
            fullMethod = "messenger.ChatService/UnarchiveChat",
            request = UnarchiveChatRequestProto(userId = userId, chatId = chatId),
            responseType = UnarchiveChatResponseProto::class.java,
            requestMarshaller = UnarchiveChatRequestMarshaller(),
            responseMarshaller = UnarchiveChatResponseMarshaller()
        )?.success ?: false
    }

    suspend fun pinMessage(chatId: String, messageId: String): Boolean {
        val userId = getUserId() ?: return false
        return unaryCallWithClass(
            getChannel = getChannel,
            fullMethod = "messenger.ChatService/PinMessage",
            request = PinMessageRequestProto(userId = userId, chatId = chatId, messageId = messageId),
            responseType = PinMessageResponseProto::class.java,
            requestMarshaller = PinMessageRequestMarshaller(),
            responseMarshaller = PinMessageResponseMarshaller()
        )?.success ?: false
    }

    suspend fun unpinMessage(chatId: String, messageId: String): Boolean {
        val userId = getUserId() ?: return false
        return unaryCallWithClass(
            getChannel = getChannel,
            fullMethod = "messenger.ChatService/UnPinMessage",
            request = UnPinMessageRequestProto(userId = userId, chatId = chatId, messageId = messageId),
            responseType = UnPinMessageResponseProto::class.java,
            requestMarshaller = UnPinMessageRequestMarshaller(),
            responseMarshaller = UnPinMessageResponseMarshaller()
        )?.success ?: false
    }

    suspend fun getPinnedMessages(chatId: String): List<Message> {
        val userId = getUserId() ?: return emptyList()
        val response = unaryCallWithClass(
            getChannel = getChannel,
            fullMethod = "messenger.ChatService/GetPinnedMessages",
            request = GetPinnedMessagesRequestProto(userId = userId, chatId = chatId),
            responseType = GetPinnedMessagesResponseProto::class.java,
            requestMarshaller = GetPinnedMessagesRequestMarshaller(),
            responseMarshaller = GetPinnedMessagesResponseMarshaller()
        )
        return response?.messages?.map { proto ->
            Message(
                id = proto.id, user = proto.user, text = proto.text,
                timestamp = proto.createdAt?.seconds ?: 0L
            )
        } ?: emptyList()
    }
}
