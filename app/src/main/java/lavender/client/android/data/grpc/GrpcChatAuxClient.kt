package lavender.client.android.data.grpc

import android.util.Log
import lavender.client.android.data.models.ErrorHandler
import lavender.client.android.data.proto.*

class GrpcChatAuxClient(
    private val getChannel: () -> io.grpc.ManagedChannel?,
    private val getUserId: () -> String?,
    private val allUsers: kotlinx.coroutines.flow.MutableStateFlow<List<UserInfoProto>>,
    private val serverTime: kotlinx.coroutines.flow.MutableStateFlow<com.google.protobuf.Timestamp?>,
    private val reconnect: (() -> Unit)? = null
) {
    companion object {
        private const val TAG = "GrpcChatAuxClient"
    }

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
                if (!status.isOk) {
                    ErrorHandler.handle("GrpcChatAuxClient.getAllUsers", "Status: ${status.code} — ${status.description}")
                    if (status.code == io.grpc.Status.Code.UNAVAILABLE) {
                        reconnect?.invoke()
                    }
                }
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

    fun getAdminUserList(query: String, cursor: String, limit: Int, sortBy: String, callback: (GetAdminUserListResponseProto) -> Unit) {
        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<GetAdminUserListRequestProto, GetAdminUserListResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/GetAdminUserList")
                .setRequestMarshaller(GetAdminUserListRequestMarshaller())
                .setResponseMarshaller(GetAdminUserListResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<GetAdminUserListResponseProto>() {
            override fun onMessage(message: GetAdminUserListResponseProto) {
                Log.d(TAG, "GetAdminUserList: received ${message.users.size} users, hasMore=${message.hasMore}")
                callback(message)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) ErrorHandler.handle("GrpcChatAuxClient.getAdminUserList", "Status: ${status.code} — ${status.description}")
            }
        }, io.grpc.Metadata())
        call.sendMessage(GetAdminUserListRequestProto(query, cursor, limit, sortBy))
        call.halfClose()
        call.request(1)
    }

    fun getAdminUserSessions(userId: String, callback: (GetAdminUserSessionsResponseProto) -> Unit) {
        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<GetAdminUserSessionsRequestProto, GetAdminUserSessionsResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/GetAdminUserSessions")
                .setRequestMarshaller(GetAdminUserSessionsRequestMarshaller())
                .setResponseMarshaller(GetAdminUserSessionsResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<GetAdminUserSessionsResponseProto>() {
            override fun onMessage(message: GetAdminUserSessionsResponseProto) {
                Log.d(TAG, "GetAdminUserSessions: received ${message.sessions.size} sessions")
                callback(message)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) ErrorHandler.handle("GrpcChatAuxClient.getAdminUserSessions", "Status: ${status.code} — ${status.description}")
            }
        }, io.grpc.Metadata())
        call.sendMessage(GetAdminUserSessionsRequestProto(userId))
        call.halfClose()
        call.request(1)
    }
}
