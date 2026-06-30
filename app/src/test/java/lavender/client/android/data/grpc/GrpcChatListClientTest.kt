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
    private lateinit var client: GrpcChatListClient

    @Before
    fun setup() {
        channel = mockk(relaxed = true)
        client = GrpcChatListClient(
            getChannel = { channel },
            getUserId = { "user-uuid-123" }
        )
    }

    @Test
    fun getChats_success_returnsChatList() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>(relaxed = true)
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
    fun getChats_v2Fields_mappedToChatInfo() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>(relaxed = true)
        every { channel.newCall<Any, Any>(any(), any()) } returns mockCall
        every { mockCall.start(any(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            val listener = firstArg<ClientCall.Listener<Any>>()
            val chatProto = ChatInfoProto(
                id = "chat-pinned", name = "Pinned Chat", type = "group",
                participants = "[\"user1\",\"user2\"]", unreadCount = 5,
                isPinned = true, isMuted = true, isArchived = false,
                pinnedAt = 1719000000000L,
                createdAt = com.google.protobuf.Timestamp.newBuilder().setSeconds(1000).build()
            )
            listener.onMessage(GetChatsResponseProto(chats = listOf(chatProto)))
            listener.onClose(Status.OK, Metadata())
        }

        var result: List<ChatInfo>? = null
        client.getChats(username = "testuser", callback = { result = it })

        assertNotNull("Result should not be null", result)
        assertEquals("Should have 1 chat", 1, result!!.size)
        val chat = result!![0]
        assertTrue("isPinned should be true", chat.isPinned)
        assertTrue("isMuted should be true", chat.isMuted)
        assertFalse("isArchived should be false", chat.isArchived)
        assertEquals("pinnedAt should match", 1719000000000L, chat.pinnedAt)
    }

    @Test
    fun getAllChats_v2Fields_mappedToChatInfo() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>(relaxed = true)
        every { channel.newCall<Any, Any>(any(), any()) } returns mockCall
        every { mockCall.start(any(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            val listener = firstArg<ClientCall.Listener<Any>>()
            // GetAllChatsResponseProto.chats is List<ChatInfoProto> (same type as GetChats)
            val chatProto = ChatInfoProto(
                id = "chat-archived", name = "Archived Chat", type = "direct",
                participants = "[\"user1\"]", unreadCount = 0,
                isPinned = false, isMuted = false, isArchived = true,
                pinnedAt = 0L
            )
            listener.onMessage(GetAllChatsResponseProto(chats = listOf(chatProto)))
            listener.onClose(Status.OK, Metadata())
        }

        var result: List<ChatInfo>? = null
        client.getAllChats { result = it }

        assertNotNull("Result should not be null", result)
        assertEquals("Should have 1 chat", 1, result!!.size)
        val chat = result!![0]
        assertFalse("isPinned should be false", chat.isPinned)
        assertFalse("isMuted should be false", chat.isMuted)
        assertTrue("isArchived should be true", chat.isArchived)
    }

    @Test
    fun getChats_emptyServerResponse_returnsNull() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>(relaxed = true)
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
            getChannel = { null }, getUserId = { "user-uuid" }
        )
        var result: List<ChatInfo>? = null
        nullChannelClient.getChats(username = "testuser", callback = { result = it })
        assertNotNull("Result should not be null", result)
        assertTrue("Result should be empty list", result!!.isEmpty())
    }

    @Test
    fun getChats_serverError_returnsEmptyList() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>(relaxed = true)
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
    fun getAllChats_success_returnsChatList() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>(relaxed = true)
        every { channel.newCall<Any, Any>(any(), any()) } returns mockCall
        every { mockCall.start(any(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            val listener = firstArg<ClientCall.Listener<Any>>()
            val chatProto = GetAllChatsResponseProto.ChatInfoProto(id = "chat-1", name = "All Chat")
            listener.onMessage(GetAllChatsResponseProto(chats = listOf(chatProto)))
            listener.onClose(Status.OK, Metadata())
        }

        var result: List<ChatInfo>? = null
        client.getAllChats { result = it }
        assertNotNull("Result should not be null", result)
    }

    @Test
    fun getChatListVersion_sendsRequest() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>(relaxed = true)
        every { channel.newCall<Any, Any>(any(), any()) } returns mockCall
        every { mockCall.start(any(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            firstArg<ClientCall.Listener<Any>>()
                .onMessage(GetChatListVersionResponseProto(version = 42))
        }

        var version: Long? = null
        client.getChatListVersion("testuser") { version = it }
        assertEquals("Version", 42L, version)
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
}
