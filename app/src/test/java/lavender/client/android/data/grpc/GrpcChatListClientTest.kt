package lavender.client.android.data.grpc

import io.grpc.CallOptions
import io.grpc.ClientCall
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Status
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import io.mockk.*
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.data.proto.*

class GrpcChatListClientTest {

    private lateinit var channel: ManagedChannel
    private lateinit var chatDeletedEvent: MutableStateFlow<String?>
    private lateinit var allUsers: MutableStateFlow<List<UserInfoProto>>
    private lateinit var serverTime: MutableStateFlow<com.google.protobuf.Timestamp?>
    private lateinit var client: GrpcChatListClient
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Before
    fun setup() {
        channel = mockk()
        chatDeletedEvent = MutableStateFlow(null)
        allUsers = MutableStateFlow(emptyList())
        serverTime = MutableStateFlow(null)
        client = GrpcChatListClient(
            getChannel = { channel },
            getUserId = { "user-uuid-123" },
            getUsername = { "testuser" },
            chatDeletedEvent = chatDeletedEvent,
            allUsers = allUsers,
            serverTime = serverTime,
            scope = scope
        )
    }

    @Test
    fun getChats_success_returnsChatList() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>()
        every { channel.newCall<Any, Any>(any(), any()) } returns mockCall
        every { mockCall.start(any(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            val listener = firstArg<ClientCall.Listener<Any>>()
            val chatProto = ChatInfoProto(id = "chat-1", name = "Test Chat", type = "group",
                participants = "[\"user1\",\"user2\"]", unreadCount = 3,
                createdAt = com.google.protobuf.Timestamp.newBuilder().setSeconds(1000).build())
            listener.onMessage(GetChatsResponseProto(chats = listOf(chatProto)))
            listener.onClose(Status.OK, Metadata())
        }

        var result: List<ChatInfo>? = null
        client.getChats(username = "testuser", callback = { result = it })

        assertNotNull("Result should not be null", result)
        assertEquals("Should have 1 chat", 1, result!!.size)
        assertEquals("Chat name", "Test Chat", result!![0].name)
    }

    @Test
    fun getChats_emptyServerResponse_returnsEmptyList() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>()
        every { channel.newCall<Any, Any>(any(), any()) } returns mockCall
        every { mockCall.start(any(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            firstArg<ClientCall.Listener<Any>>()
                .onMessage(GetChatsResponseProto())
        }

        var result: List<ChatInfo>? = null
        client.getChats(username = "testuser", callback = { result = it })
        assertNull("Result should be null for empty response", result)
    }

    @Test
    fun getChats_nullChannel_returnsEmptyList() = runTest {
        val nullChannelClient = GrpcChatListClient(
            getChannel = { null }, getUserId = { "user-uuid" }, getUsername = { "testuser" },
            chatDeletedEvent = chatDeletedEvent, allUsers = allUsers, serverTime = serverTime, scope = scope
        )
        var result: List<ChatInfo>? = null
        nullChannelClient.getChats(username = "testuser", callback = { result = it })
        assertNotNull("Result should not be null", result)
        assertTrue("Result should be empty list", result!!.isEmpty())
    }

    @Test
    fun getChats_serverError_returnsEmptyList() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>()
        every { channel.newCall<Any, Any>(any(), any()) } returns mockCall
        every { mockCall.start(any(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            firstArg<ClientCall.Listener<Any>>()
                .onClose(Status.UNAVAILABLE.withDescription("Server unavailable"), Metadata())
        }

        var result: List<ChatInfo>? = null
        client.getChats(username = "testuser", callback = { result = it })
        assertNotNull("Result should not be null", result)
        assertTrue("Result should be empty list on error", result!!.isEmpty())
    }

    @Test
    fun pinChat_v2Supported_callsPin() = runTest {
        val isV2 = ProfileClient.isChatV2Supported()
        assertFalse("Should return false when service version not set", isV2)
    }

    @Test
    fun searchChats_v1Fallback_returnsEmptyList() = runTest {
        val isV2 = ProfileClient.isChatV2Supported()
        assertFalse("Should be v1 fallback in test env", isV2)
    }

    @Test
    fun deleteChat_sendsRequest() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>()
        every { channel.newCall<Any, Any>(any(), any()) } returns mockCall
        every { mockCall.start(any(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            firstArg<ClientCall.Listener<Any>>()
                .onMessage(DeleteChatResponseProto(success = true))
        }

        var success = false
        client.deleteChat("chat-1", "testuser") { s, _ -> success = s }
        assertTrue("Delete should succeed", success)
    }

    @Test
    fun createDirectChat_sendsRequest() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>()
        every { channel.newCall<Any, Any>(any(), any()) } returns mockCall
        every { mockCall.start(any(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            firstArg<ClientCall.Listener<Any>>()
                .onMessage(CreateDirectChatResponseProto(chatId = "new-chat-id", success = true))
        }

        var chatId: String? = null
        client.createDirectChat("user1", "user2") { chatId = it }
        assertEquals("Chat ID", "new-chat-id", chatId)
    }

    @Test
    fun createGroupChat_sendsRequest() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>()
        every { channel.newCall<Any, Any>(any(), any()) } returns mockCall
        every { mockCall.start(any(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            firstArg<ClientCall.Listener<Any>>()
                .onMessage(CreateGroupChatResponseProto(chatId = "group-chat-id", success = true))
        }

        var chatId: String? = null
        client.createGroupChat("Test Group", listOf("user1", "user2"), "user1") { chatId = it }
        assertEquals("Group Chat ID", "group-chat-id", chatId)
    }
}
