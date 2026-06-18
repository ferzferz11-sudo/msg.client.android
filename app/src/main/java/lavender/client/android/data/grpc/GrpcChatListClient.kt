package lavender.client.android.data.grpc

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.data.models.ErrorHandler
import lavender.client.android.data.proto.*

/**
 * Chat list core operations: getChats, create/delete chats, manage participants, settings.
 *
 * Extracted from RealGrpcClient v1.1.3.25.
 * ChatList v2 → GrpcChatListV2Client, Users/AI/FCM/Mute → GrpcChatAuxClient.
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
                if (message.success) { scope.launch(Dispatchers.IO) {} }
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
                if (message.success) { scope.launch(Dispatchers.IO) {} }
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
