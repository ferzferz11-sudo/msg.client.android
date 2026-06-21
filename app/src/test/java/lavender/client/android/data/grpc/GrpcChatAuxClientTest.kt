package lavender.client.android.data.grpc

import io.grpc.ClientCall
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.Status
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import io.mockk.*
import lavender.client.android.data.models.AIChatInfo
import lavender.client.android.data.proto.*

class GrpcChatAuxClientTest {

    private lateinit var channel: ManagedChannel
    private lateinit var allUsers: MutableStateFlow<List<UserInfoProto>>
    private lateinit var serverTime: MutableStateFlow<com.google.protobuf.Timestamp?>
    private lateinit var client: GrpcChatAuxClient

    @Before
    fun setup() {
        channel = mockk(relaxed = true)
        allUsers = MutableStateFlow(emptyList())
        serverTime = MutableStateFlow(null)
        client = GrpcChatAuxClient(
            getChannel = { channel },
            getUserId = { "user-uuid-123" },
            allUsers = allUsers,
            serverTime = serverTime
        )
    }

    @Test
    fun loadAllUsers_success_updatesAllUsers() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>(relaxed = true)
        every { channel.newCall<Any, Any>(any(), any()) } returns mockCall
        val users = listOf(UserInfoProto(userId = "u1", username = "user1"))
        every { mockCall.start(any(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            firstArg<ClientCall.Listener<Any>>()
                .onMessage(GetAllUsersResponseProto(users = users))
        }

        var result: List<UserInfoProto>? = null
        client.loadAllUsers { result = it }
        assertNotNull("Result should not be null", result)
        assertEquals("Should have 1 user", 1, result!!.size)
        assertEquals("user1", result!![0].username)
        assertEquals("allUsers flow should be updated", 1, allUsers.value.size)
    }

    @Test
    fun fetchUserId_success_returnsUserId() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>(relaxed = true)
        every { channel.newCall<Any, Any>(any(), any()) } returns mockCall
        every { mockCall.start(any(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            firstArg<ClientCall.Listener<Any>>()
                .onMessage(GetUserIdResponseProto(userId = "found-uuid", found = true))
        }

        var userId: String? = null
        var found = false
        client.fetchUserId("testuser") { id, f -> userId = id; found = f }
        assertEquals("found-uuid", userId)
        assertTrue("Should be found", found)
    }

    @Test
    fun getAIChats_success_returnsList() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>(relaxed = true)
        every { channel.newCall<Any, Any>(any(), any()) } returns mockCall
        every { mockCall.start(any(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            firstArg<ClientCall.Listener<Any>>()
                .onMessage(GetAIChatsResponseProto(chats = listOf(
                    AIChatInfoProto(id = "ai-1", name = "Hermes Chat", type = "hermes")
                )))
        }

        var result: List<AIChatInfo>? = null
        client.getAIChats("user-uuid") { result = it }
        assertNotNull("Result should not be null", result)
        assertEquals("Should have 1 AI chat", 1, result!!.size)
        assertEquals("Hermes Chat", result!![0].name)
    }

    @Test
    fun renameAIChat_success_returnsTrue() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>(relaxed = true)
        every { channel.newCall<Any, Any>(any(), any()) } returns mockCall
        every { mockCall.start(any(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            firstArg<ClientCall.Listener<Any>>()
                .onMessage(RenameAIChatResponseProto(success = true, error = ""))
        }

        var success = false
        client.renameAIChat("ai-1", "user-uuid", "New Name") { s, _ -> success = s }
        assertTrue("Rename should succeed", success)
    }

    @Test
    fun registerToken_sendsRequest() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>(relaxed = true)
        every { channel.newCall<Any, Any>(any(), any()) } returns mockCall
        every { mockCall.start(any(), any()) } just Runs

        // Should not crash
        client.registerToken("testuser", "fcm-token-123", true)
        verify { channel.newCall<Any, Any>(any(), any()) }
    }

    @Test
    fun getMutedChats_success_returnsList() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>(relaxed = true)
        every { channel.newCall<Any, Any>(any(), any()) } returns mockCall
        every { mockCall.start(any(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            firstArg<ClientCall.Listener<Any>>()
                .onMessage(GetMutedChatsResponseProto(roomIds = listOf("room-1", "room-2")))
        }

        var result: List<String>? = null
        client.getMutedChats { result = it }
        assertNotNull("Result should not be null", result)
        assertEquals("Should have 2 muted chats", 2, result!!.size)
        assertTrue("Should contain room-1", result!!.contains("room-1"))
    }

    @Test
    fun setMutedChat_success_returnsTrue() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>(relaxed = true)
        every { channel.newCall<Any, Any>(any(), any()) } returns mockCall
        every { mockCall.start(any(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            firstArg<ClientCall.Listener<Any>>()
                .onMessage(SetMutedChatResponseProto(success = true))
        }

        var success = false
        client.setMutedChat("room-1", true) { success = it }
        assertTrue("Set mute should succeed", success)
    }

    @Test
    fun nullChannel_doesNotCrash() = runTest {
        val nullClient = GrpcChatAuxClient(
            getChannel = { null },
            getUserId = { "user-uuid" },
            allUsers = allUsers,
            serverTime = serverTime
        )
        nullClient.loadAllUsers { }
        nullClient.fetchUserId("u") { _, _ -> }
        nullClient.getAIChats("u") { }
        nullClient.renameAIChat("c", "u", "n") { _, _ -> }
        nullClient.registerToken("u", "t", true)
        nullClient.getMutedChats { }
        nullClient.setMutedChat("r", true) { }
        assertTrue("Null channel should not crash", true)
    }
}
