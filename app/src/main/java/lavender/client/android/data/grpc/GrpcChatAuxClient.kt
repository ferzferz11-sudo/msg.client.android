package lavender.client.android.data.grpc

import android.util.Log
import lavender.client.android.data.models.AIChatInfo
import lavender.client.android.data.models.ErrorHandler
import lavender.client.android.data.proto.*

/**
 * Auxiliary chat operations: users, AI chats, FCM tokens, mute.
 *
 * Owns all auxiliary RPC calls to ChatService that are not chat-list or chat-management.
 * Does NOT own channel management — uses channel from GrpcConnectionManager.
 *
 * Extracted from GrpcChatListClient v1.1.3.40 to reduce file size.
 */
class GrpcChatAuxClient(
    private val getChannel: () -> io.grpc.ManagedChannel?,
    private val getUserId: () -> String?,
    private val allUsers: kotlinx.coroutines.flow.MutableStateFlow<List<UserInfoProto>>,
    private val serverTime: kotlinx.coroutines.flow.MutableStateFlow<com.google.protobuf.Timestamp?>
) {
    companion object {
        private const val TAG = "GrpcChatAuxClient"
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
                if (!status.isOk) ErrorHandler.handle("GrpcChatAuxClient.getAllUsers", "Status: ${status.code} — ${status.description}")
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
                if (!status.isOk) ErrorHandler.handle("GrpcChatAuxClient.getAIChats", "Status: ${status.code} — ${status.description}")
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
