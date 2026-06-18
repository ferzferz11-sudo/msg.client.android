package lavender.client.android.data.grpc

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lavender.client.android.data.models.AIChatInfo
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.data.models.Message
import lavender.client.android.data.models.ErrorHandler
import lavender.client.android.data.proto.*

/**
 * Handles chat list operations: getChats, pinChat, searchChats, archiveChat, chat management.
 *
 * Owns all ChatList-related RPC calls to ChatService.
 * Does NOT own channel management — uses channel from GrpcConnectionManager.
 *
 * Extracted from RealGrpcClient v1.1.3.25 to reduce God Object anti-pattern.
 */
class GrpcChatListClient(
    private val getChannel: () -> io.grpc.ManagedChannel?,
    private val getUserId: () -> String?,
    private val getUsername: () -> String?,
    private val chatDeletedEvent: kotlinx.coroutines.flow.MutableStateFlow<String?>,
    private val allUsers: kotlinx.coroutines.flow.MutableStateFlow<List<UserInfoProto>>,
    private val serverTime: kotlinx.coroutines.flow.MutableStateFlow<com.google.protobuf.Timestamp?>,
    private val scope: kotlinx.coroutines.CoroutineScope
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

    // ======= Chat Management =======

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
                    scope.launch(Dispatchers.IO) {
                        // Clear local messages for deleted chat
                    }
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
                    scope.launch(Dispatchers.IO) { /* clear local */ }
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

    // ======= Users =======

    fun loadAllUsers(callback: (List<UserInfoProto>) -> Unit) {
        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<GetAllUsersRequestProto, GetAllUsersResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/GetAllUsers")
                .setRequestMarshaller(GetAllUsersRequestMarshaller())
                .setResponseMarshaller(GetAllUsersResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<GetAllUsersResponseProto>() {
            override fun onMessage(message: GetAllUsersResponseProto) {
                Log.d(TAG, "GetAllUsers: received ${message.users.size} users")
                allUsers.value = message.users
                serverTime.value = message.serverTime
                callback(message.users)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) ErrorHandler.handle("GrpcChatListClient.getAllUsers", "Status: ${status.code} — ${status.description}")
            }
        }, io.grpc.Metadata())
        call.sendMessage(GetAllUsersRequestProto())
        call.halfClose()
        call.request(1)
    }

    fun fetchUserId(username: String, callback: (String?, Boolean) -> Unit) {
        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<GetUserIdRequestProto, GetUserIdResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/GetUserId")
                .setRequestMarshaller(GetUserIdRequestMarshaller())
                .setResponseMarshaller(GetUserIdResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<GetUserIdResponseProto>() {
            override fun onMessage(message: GetUserIdResponseProto) { callback(message.userId, message.found) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {}
        }, io.grpc.Metadata())
        call.sendMessage(GetUserIdRequestProto(username))
        call.halfClose()
        call.request(1)
    }

    // ======= AI Chats =======

    fun getAIChats(userId: String, callback: (List<AIChatInfo>) -> Unit) {
        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<GetAIChatsRequestProto, GetAIChatsResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/GetAIChats")
                .setRequestMarshaller(GetAIChatsRequestMarshaller())
                .setResponseMarshaller(GetAIChatsResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<GetAIChatsResponseProto>() {
            override fun onMessage(message: GetAIChatsResponseProto) {
                callback(message.chats.map { proto ->
                    AIChatInfo(
                        id = proto.id, name = proto.name, type = proto.type, createdAt = proto.createdAt
                    )
                })
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) ErrorHandler.handle("GrpcChatListClient.getAIChats", "Status: ${status.code} — ${status.description}")
            }
        }, io.grpc.Metadata())
        call.sendMessage(GetAIChatsRequestProto().apply { this.userId = userId })
        call.halfClose()
        call.request(1)
    }

    fun renameAIChat(chatId: String, userId: String, newName: String, callback: (Boolean, String) -> Unit) {
        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<RenameAIChatRequestProto, RenameAIChatResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/RenameAIChat")
                .setRequestMarshaller(RenameAIChatRequestMarshaller())
                .setResponseMarshaller(RenameAIChatResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<RenameAIChatResponseProto>() {
            override fun onMessage(message: RenameAIChatResponseProto) { callback(message.success, message.error) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) callback(false, status.description ?: "Unknown error")
            }
        }, io.grpc.Metadata())
        call.sendMessage(RenameAIChatRequestProto().apply { this.chatId = chatId; this.userId = userId; this.newName = newName })
        call.halfClose()
        call.request(1)
    }

    // ======= FCM Token =======

    fun registerToken(user: String, token: String, pushEnabled: Boolean) {
        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<TokenRequestProto, TokenResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/RegisterToken")
                .setRequestMarshaller(TokenRequestMarshaller())
                .setResponseMarshaller(TokenResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<TokenResponseProto>() {}, io.grpc.Metadata())
        call.sendMessage(TokenRequestProto(user, token, pushEnabled, getUserId() ?: ""))
        call.halfClose()
        call.request(1)
    }

    // ======= Mute =======

    fun getMutedChats(callback: (List<String>) -> Unit) {
        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<GetMutedChatsRequestProto, GetMutedChatsResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/GetMutedChats")
                .setRequestMarshaller(GetMutedChatsRequestMarshaller())
                .setResponseMarshaller(GetMutedChatsResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<GetMutedChatsResponseProto>() {
            override fun onMessage(message: GetMutedChatsResponseProto) { callback(message.roomIds) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {}
        }, io.grpc.Metadata())
        call.sendMessage(GetMutedChatsRequestProto(getUserId() ?: ""))
        call.halfClose()
        call.request(1)
    }

    fun setMutedChat(roomId: String, muted: Boolean, callback: (Boolean) -> Unit) {
        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<SetMutedChatRequestProto, SetMutedChatResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/SetMutedChat")
                .setRequestMarshaller(SetMutedChatRequestMarshaller())
                .setResponseMarshaller(SetMutedChatResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<SetMutedChatResponseProto>() {
            override fun onMessage(message: SetMutedChatResponseProto) { callback(message.success) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {}
        }, io.grpc.Metadata())
        call.sendMessage(SetMutedChatRequestProto(getUserId() ?: "", roomId, muted))
        call.halfClose()
        call.request(1)
    }
}
