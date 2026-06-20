package lavender.client.android.data.grpc

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.data.models.ErrorHandler
import lavender.client.android.data.proto.*

data class ChatListPage(
    val chats: List<ChatInfo>,
    val nextCursor: String,
    val hasMore: Boolean
)

class GrpcChatClient(
    private val getChannel: () -> io.grpc.ManagedChannel?,
    private val getUserId: () -> String?,
    private val getUsername: () -> String?,
    private val scope: kotlinx.coroutines.CoroutineScope
) {
    companion object {
        private const val TAG = "GrpcChatClient"
    }

    fun getChats(username: String, @Suppress("UNUSED_PARAMETER") skipCache: Boolean = false, limit: Int = 100, cursor: String = "", callback: (ChatListPage) -> Unit) {
        val currentChannel = getChannel()
        if (currentChannel == null || currentChannel.isShutdown || currentChannel.isTerminated) {
            Log.w(TAG, "getChats: channel not available")
            callback(ChatListPage(emptyList(), "", false))
            return
        }
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<GetChatsRequestProto, GetChatsResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/GetChatsV2")
            .setRequestMarshaller(GetChatsRequestMarshaller())
            .setResponseMarshaller(GetChatsResponseMarshaller())
            .build()
        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<GetChatsResponseProto>() {
            override fun onMessage(message: GetChatsResponseProto) {
                val chats = message.chats.map { proto ->
                    ChatInfo(
                        proto.id, proto.name, proto.type, proto.participants,
                        proto.createdAt?.let { it.seconds * 1000 + it.nanos / 1000000 } ?: 0L,
                        proto.unreadCount,
                        proto.lastMessageTime?.let { it.seconds * 1000 + it.nanos / 1000000 } ?: 0L,
                        proto.creator, proto.lastMessageText, proto.avatarUrl, proto.fullAvatarUrl,
                        proto.lastMessageUsername, proto.isMuted, proto.lastMessageHasImage, proto.allowMembersToAdd,
                        proto.conferenceStartTime?.let { it.seconds * 1000 + it.nanos / 1000000 } ?: 0L,
                        proto.isSecret, proto.peerPublicKey, proto.e2eeReady,
                        proto.activeAgentId, proto.agentMode, proto.isPinned, proto.isArchived, proto.pinnedAt
                    )
                }
                val unreadChats = chats.filter { it.unreadCount > 0 }
                Log.d(TAG, "getChats: ${chats.size} chats, ${unreadChats.size} unread: ${unreadChats.joinToString { "${it.name}=${it.unreadCount}" }}")
                callback(ChatListPage(chats, message.nextCursor, message.hasMore))
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    Log.w(TAG, "getChats: onClose error: ${status.code} - ${status.description}")
                    callback(ChatListPage(emptyList(), "", false))
                }
            }
        }, io.grpc.Metadata())
        call.sendMessage(GetChatsRequestProto(username = username, userId = getUserId() ?: "", limit = limit, cursor = cursor))
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
                        lastMessageUsername = proto.lastMessageUsername, isMuted = proto.isMuted,
                        lastMessageHasImage = proto.lastMessageHasImage,
                        allowMembersToAdd = proto.allowMembersToAdd,
                        conferenceStartTime = proto.conferenceStartTime?.let { it.seconds * 1000 + it.nanos / 1000000 } ?: 0L,
                        isSecret = proto.isSecret, peerPublicKey = proto.peerPublicKey,
                        e2eeReady = proto.e2eeReady
                    )
                })
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) ErrorHandler.handle("GrpcChatClient.getAllChats", "Status: ${status.code} — ${status.description}")
            }
        }, io.grpc.Metadata())
        call.sendMessage(GetAllChatsRequestProto())
        call.halfClose()
        call.request(1)
    }

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

    fun deleteChat(chatId: String, requesterUsername: String, callback: (Boolean, String) -> Unit) {
        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<DeleteChatRequestProto, DeleteChatResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/DeleteChat")
                .setRequestMarshaller(DeleteChatRequestMarshaller())
                .setResponseMarshaller(DeleteChatResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<DeleteChatResponseProto>() {
            override fun onMessage(message: DeleteChatResponseProto) {
                if (message.success) {
                    scope.launch(Dispatchers.IO) { }
                }
                callback(message.success, message.message)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) callback(false, status.description ?: "Error")
            }
        }, io.grpc.Metadata())
        call.sendMessage(DeleteChatRequestProto(chatId, requesterUsername, getUserId() ?: ""))
        call.halfClose()
        call.request(1)
    }

    fun deleteChatWithUserId(chatId: String, userId: String, username: String, callback: (Boolean, String) -> Unit) {
        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<DeleteChatRequestProto, DeleteChatResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/DeleteChat")
                .setRequestMarshaller(DeleteChatRequestMarshaller())
                .setResponseMarshaller(DeleteChatResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<DeleteChatResponseProto>() {
            override fun onMessage(message: DeleteChatResponseProto) {
                if (message.success) {
                    scope.launch(Dispatchers.IO) { }
                }
                callback(message.success, message.message)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) callback(false, status.description ?: "Error")
            }
        }, io.grpc.Metadata())
        call.sendMessage(DeleteChatRequestProto(chatId, username, userId))
        call.halfClose()
        call.request(1)
    }

    fun createDirectChat(user1: String, user2: String, callback: (String?) -> Unit) {
        val currentChannel = getChannel() ?: return
        val u1Id = if (user1 == getUsername()) getUserId() ?: "" else ""
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<CreateDirectChatRequestProto, CreateDirectChatResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/CreateDirectChat")
                .setRequestMarshaller(CreateDirectChatRequestMarshaller())
                .setResponseMarshaller(CreateDirectChatResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<CreateDirectChatResponseProto>() {
            override fun onMessage(message: CreateDirectChatResponseProto) {
                if (message.success) callback(message.chatId) else callback(null)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) callback(null)
            }
        }, io.grpc.Metadata())
        call.sendMessage(CreateDirectChatRequestProto(user1, user2, u1Id, ""))
        call.halfClose()
        call.request(1)
    }

    fun createGroupChat(name: String, participants: List<String>, creator: String, type: String = "group", callback: (String?) -> Unit) {
        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<CreateGroupChatRequestProto, CreateGroupChatResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/CreateGroupChat")
                .setRequestMarshaller(CreateGroupChatRequestMarshaller())
                .setResponseMarshaller(CreateGroupChatResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<CreateGroupChatResponseProto>() {
            override fun onMessage(message: CreateGroupChatResponseProto) {
                if (message.success) callback(message.chatId) else callback(null)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) callback(null)
            }
        }, io.grpc.Metadata())
        call.sendMessage(CreateGroupChatRequestProto(name, participants, creator, getUserId() ?: "", emptyList(), type))
        call.halfClose()
        call.request(1)
    }

    fun updateChatAvatar(chatId: String, avatarUrl: String, username: String, fullAvatarUrl: String, callback: (Boolean, String) -> Unit) {
        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<UpdateChatAvatarRequestProto, UpdateChatAvatarResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/UpdateChatAvatar")
                .setRequestMarshaller(UpdateChatAvatarRequestMarshaller())
                .setResponseMarshaller(UpdateChatAvatarResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<UpdateChatAvatarResponseProto>() {
            override fun onMessage(message: UpdateChatAvatarResponseProto) { callback(message.success, message.message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) callback(false, status.description ?: "Error")
            }
        }, io.grpc.Metadata())
        call.sendMessage(UpdateChatAvatarRequestProto(chatId, avatarUrl, username, fullAvatarUrl, getUserId() ?: ""))
        call.halfClose()
        call.request(1)
    }

    fun updateChatSettings(chatId: String, allowAdd: Boolean, callback: (Boolean, String) -> Unit) {
        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<UpdateChatSettingsRequestProto, UpdateChatSettingsResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/UpdateChatSettings")
                .setRequestMarshaller(UpdateChatSettingsRequestMarshaller())
                .setResponseMarshaller(UpdateChatSettingsResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<UpdateChatSettingsResponseProto>() {
            override fun onMessage(message: UpdateChatSettingsResponseProto) { callback(message.success, message.message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) callback(false, status.description ?: "Error")
            }
        }, io.grpc.Metadata())
        call.sendMessage(UpdateChatSettingsRequestProto(chatId, allowAdd, getUserId() ?: ""))
        call.halfClose()
        call.request(1)
    }

    fun updateChatName(chatId: String, newName: String, callback: (Boolean, String) -> Unit) {
        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<UpdateChatNameRequestProto, UpdateChatNameResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/UpdateChatName")
                .setRequestMarshaller(UpdateChatNameRequestMarshaller())
                .setResponseMarshaller(UpdateChatNameResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<UpdateChatNameResponseProto>() {
            override fun onMessage(message: UpdateChatNameResponseProto) { callback(message.success, message.message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) callback(false, status.description ?: "Error")
            }
        }, io.grpc.Metadata())
        call.sendMessage(UpdateChatNameRequestProto(chatId, newName))
        call.halfClose()
        call.request(1)
    }

    fun addParticipant(chatId: String, username: String, callback: (Boolean, String) -> Unit) {
        val currentChannel = getChannel() ?: return
        val uId = if (username == getUsername()) getUserId() ?: "" else ""
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<AddParticipantRequestProto, AddParticipantResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/AddParticipant")
                .setRequestMarshaller(AddParticipantRequestMarshaller())
                .setResponseMarshaller(AddParticipantResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<AddParticipantResponseProto>() {
            override fun onMessage(message: AddParticipantResponseProto) { callback(message.success, message.message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) callback(false, status.description ?: "Error")
            }
        }, io.grpc.Metadata())
        call.sendMessage(AddParticipantRequestProto(chatId, username, uId))
        call.halfClose()
        call.request(1)
    }

    fun addParticipants(chatId: String, users: List<String>, callback: (Boolean, String) -> Unit) {
        var completed = 0; var allSuccess = true; var lastMsg = ""
        if (users.isEmpty()) { callback(true, ""); return }
        users.forEach { u ->
            addParticipant(chatId, u) { success, msg ->
                completed++; if (!success) allSuccess = false; lastMsg = msg
                if (completed == users.size) callback(allSuccess, lastMsg)
            }
        }
    }

    fun removeParticipant(chatId: String, username: String, callback: (Boolean, String) -> Unit) {
        val currentChannel = getChannel() ?: return
        val uId = if (username == getUsername()) getUserId() ?: "" else ""
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<RemoveParticipantRequestProto, RemoveParticipantResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/RemoveParticipant")
                .setRequestMarshaller(RemoveParticipantRequestMarshaller())
                .setResponseMarshaller(RemoveParticipantResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<RemoveParticipantResponseProto>() {
            override fun onMessage(message: RemoveParticipantResponseProto) { callback(message.success, message.message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) callback(false, status.description ?: "Error")
            }
        }, io.grpc.Metadata())
        call.sendMessage(RemoveParticipantRequestProto(chatId, username, uId))
        call.halfClose()
        call.request(1)
    }
}
