package lavender.client.android.data.grpc

import io.grpc.ClientCall
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.Status
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import io.mockk.*
import lavender.client.android.data.proto.*

class GrpcChatManagementClientTest {

    private lateinit var channel: ManagedChannel
    private lateinit var client: GrpcChatManagementClient
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Before
    fun setup() {
        channel = mockk(relaxed = true)
        client = GrpcChatManagementClient(
            getChannel = { channel },
            getUserId = { "user-uuid-123" },
            getUsername = { "testuser" },
            scope = scope
        )
    }

    @Test
    fun deleteChat_success_returnsTrue() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>(relaxed = true)
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
    fun deleteChatWithUserId_success_returnsTrue() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>(relaxed = true)
        every { channel.newCall<Any, Any>(any(), any()) } returns mockCall
        every { mockCall.start(any(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            firstArg<ClientCall.Listener<Any>>()
                .onMessage(DeleteChatResponseProto(success = true, message = "Deleted"))
        }

        var success = false
        var msg = ""
        client.deleteChatWithUserId("chat-1", "user-uuid", "testuser") { s, m -> success = s; msg = m }
        assertTrue("Delete should succeed", success)
        assertEquals("Deleted", msg)
    }

    @Test
    fun createDirectChat_success_returnsChatId() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>(relaxed = true)
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
    fun createDirectChat_failure_returnsNull() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>(relaxed = true)
        every { channel.newCall<Any, Any>(any(), any()) } returns mockCall
        every { mockCall.start(any(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            firstArg<ClientCall.Listener<Any>>()
                .onMessage(CreateDirectChatResponseProto(chatId = "", success = false))
        }

        var chatId: String? = "initial"
        client.createDirectChat("user1", "user2") { chatId = it }
        assertNull("Chat ID should be null on failure", chatId)
    }

    @Test
    fun createGroupChat_success_returnsChatId() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>(relaxed = true)
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

    @Test
    fun updateChatAvatar_success_returnsTrue() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>(relaxed = true)
        every { channel.newCall<Any, Any>(any(), any()) } returns mockCall
        every { mockCall.start(any(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            firstArg<ClientCall.Listener<Any>>()
                .onMessage(UpdateChatAvatarResponseProto(success = true, message = "Updated"))
        }

        var success = false
        var msg = ""
        client.updateChatAvatar("chat-1", "url", "testuser", "fullUrl") { s, m -> success = s; msg = m }
        assertTrue("Update should succeed", success)
        assertEquals("Updated", msg)
    }

    @Test
    fun updateChatSettings_success_returnsTrue() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>(relaxed = true)
        every { channel.newCall<Any, Any>(any(), any()) } returns mockCall
        every { mockCall.start(any(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            firstArg<ClientCall.Listener<Any>>()
                .onMessage(UpdateChatSettingsResponseProto(success = true, message = "OK"))
        }

        var success = false
        client.updateChatSettings("chat-1", true) { s, _ -> success = s }
        assertTrue("Settings update should succeed", success)
    }

    @Test
    fun updateChatName_success_returnsTrue() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>(relaxed = true)
        every { channel.newCall<Any, Any>(any(), any()) } returns mockCall
        every { mockCall.start(any(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            firstArg<ClientCall.Listener<Any>>()
                .onMessage(UpdateChatNameResponseProto(success = true, message = "Renamed"))
        }

        var success = false
        client.updateChatName("chat-1", "New Name") { s, _ -> success = s }
        assertTrue("Name update should succeed", success)
    }

    @Test
    fun addParticipant_success_returnsTrue() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>(relaxed = true)
        every { channel.newCall<Any, Any>(any(), any()) } returns mockCall
        every { mockCall.start(any(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            firstArg<ClientCall.Listener<Any>>()
                .onMessage(AddParticipantResponseProto(success = true, message = "Added"))
        }

        var success = false
        client.addParticipant("chat-1", "newuser") { s, _ -> success = s }
        assertTrue("Add participant should succeed", success)
    }

    @Test
    fun removeParticipant_success_returnsTrue() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>(relaxed = true)
        every { channel.newCall<Any, Any>(any(), any()) } returns mockCall
        every { mockCall.start(any(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            firstArg<ClientCall.Listener<Any>>()
                .onMessage(RemoveParticipantResponseProto(success = true, message = "Removed"))
        }

        var success = false
        client.removeParticipant("chat-1", "olduser") { s, _ -> success = s }
        assertTrue("Remove participant should succeed", success)
    }

    @Test
    fun nullChannel_doesNotCrash() = runTest {
        val nullClient = GrpcChatManagementClient(
            getChannel = { null },
            getUserId = { "user-uuid" },
            getUsername = { "testuser" },
            scope = scope
        )
        // Should not crash — methods return early on null channel
        nullClient.deleteChat("chat-1", "testuser") { _, _ -> }
        nullClient.createDirectChat("u1", "u2") { }
        nullClient.createGroupChat("G", listOf("u1"), "u1") { }
        nullClient.updateChatAvatar("c", "a", "u", "f") { _, _ -> }
        nullClient.updateChatSettings("c", true) { _, _ -> }
        nullClient.updateChatName("c", "n") { _, _ -> }
        nullClient.addParticipant("c", "u") { _, _ -> }
        nullClient.removeParticipant("c", "u") { _, _ -> }
        assertTrue("Null channel should not crash", true)
    }
}
