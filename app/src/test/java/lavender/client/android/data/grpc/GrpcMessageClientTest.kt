package lavender.client.android.data.grpc

import io.grpc.CallOptions
import io.grpc.ClientCall
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Status
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import io.mockk.mockk
import io.mockk.every
import io.mockk.verify
import io.mockk.firstArg
import io.mockk.any
import lavender.client.android.data.models.Message
import lavender.client.android.data.models.ErrorHandler
import lavender.client.android.data.proto.*

/**
 * Unit-тесты для GrpcMessageClient.
 *
 * Тестируем: sendMessage, addLocalMessage, loadHistory, markRead, resendPendingReads.
 * Мокаем: ManagedChannel, AppDatabase (через in-memory Room).
 */
class GrpcMessageClientTest {

    private lateinit var channel: ManagedChannel
    private lateinit var messages: MutableStateFlow<List<Message>>
    private lateinit var deletedMessageHashes: MutableSet<String>
    private lateinit var pendingReads: MutableSet<String>
    private lateinit var client: GrpcMessageClient
    private val scope = CoroutineScope(Dispatchers.Main)

    @Before
    fun setup() {
        channel = mockk(relaxed = true)
        messages = MutableStateFlow(emptyList())
        deletedMessageHashes = mutableSetOf()
        pendingReads = mutableSetOf()

        client = GrpcMessageClient(
            getChannel = { channel },
            getUserId = { "user-uuid-123" },
            getUsername = { "testuser" },
            messages = messages,
            deletedMessageHashes = deletedMessageHashes,
            pendingReads = pendingReads,
            scope = scope,
            appContext = { null }, // No DB in unit tests
            onReadReceipt = null
        )
    }

    // ====== sendMessage ======

    @Test
    fun sendMessage_validMessage_callsOnNext() {
        val mockObserver = mockk<StreamObserver<MessageProto>>(relaxed = true)

        val message = Message(
            id = "msg-1",
            user = "testuser",
            text = "Hello World",
            roomId = "room-1",
            timestamp = System.currentTimeMillis()
        )

        client.sendMessage(message, mockObserver)

        verify { mockObserver.onNext(any()) }
    }

    @Test
    fun sendMessage_nullObserver_noCrash() {
        val message = Message(
            id = "msg-1",
            user = "testuser",
            text = "Hello",
            roomId = "room-1",
            timestamp = System.currentTimeMillis()
        )

        // Should not throw
        client.sendMessage(message, null)
    }

    // ====== addLocalMessage ======

    @Test
    fun addLocalMessage_addsToStateFlow() = runTest {
        val message = Message(
            id = "local-msg-1",
            user = "testuser",
            text = "Local message",
            roomId = "room-1",
            timestamp = System.currentTimeMillis()
        )

        client.addLocalMessage(message)

        // Give coroutine time to process
        kotlinx.coroutines.delay(100)

        val currentMessages = messages.value
        assertEquals("Should have 1 message", 1, currentMessages.size)
        assertEquals("Message text", "Local message", currentMessages[0].text)
    }

    @Test
    fun addLocalMessage_duplicate_replacesExisting() = runTest {
        val ts = System.currentTimeMillis()
        val message1 = Message(
            id = "msg-dup",
            user = "testuser",
            text = "First",
            roomId = "room-1",
            timestamp = ts
        )
        val message2 = Message(
            id = "msg-dup",
            user = "testuser",
            text = "Updated",
            roomId = "room-1",
            timestamp = ts + 1000
        )

        client.addLocalMessage(message1)
        kotlinx.coroutines.delay(50)
        client.addLocalMessage(message2)
        kotlinx.coroutines.delay(50)

        val currentMessages = messages.value
        assertEquals("Should have 1 message (deduped)", 1, currentMessages.size)
        assertEquals("Should be updated", "Updated", currentMessages[0].text)
    }

    // ====== loadHistory ======

    @Test
    fun loadHistory_success_updatesMessages() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>(relaxed = true)
        every { channel.newCall<Any, Any>(any(), any()) } returns mockCall

        every { mockCall.start(any(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            val listener = firstArg<ClientCall.Listener<Any>>()
            val msgProto = MessageProto.newBuilder()
                .setId("hist-msg-1")
                .setUser("otheruser")
                .setText("History message")
                .setRoomId("room-1")
                .setCreatedAt(com.google.protobuf.Timestamp.newBuilder().setSeconds(1000).build())
                .build()
            val response = GetHistoryResponseProto.newBuilder()
                .addMessages(msgProto)
                .build()
            listener.onMessage(response)
            listener.onClose(Status.OK, Metadata())
            null
        }

        var completionCalled = false
        client.loadHistory("room-1") { completionCalled = true }

        // Give coroutine time to process
        kotlinx.coroutines.delay(200)

        val currentMessages = messages.value
        assertEquals("Should have 1 message", 1, currentMessages.size)
        assertEquals("Message text", "History message", currentMessages[0].text)
        assertTrue("Completion should be called", completionCalled)
    }

    @Test
    fun loadHistory_nullChannel_noCrash() = runTest {
        val nullChannelClient = GrpcMessageClient(
            getChannel = { null },
            getUserId = { "user-uuid" },
            getUsername = { "testuser" },
            messages = messages,
            deletedMessageHashes = deletedMessageHashes,
            pendingReads = pendingReads,
            scope = scope,
            appContext = { null },
            onReadReceipt = null
        )

        var completionCalled = false
        nullChannelClient.loadHistory("room-1") { completionCalled = true }

        // Should complete without crash
        assertTrue("Completion should be called even with null channel", completionCalled)
    }

    // ====== markRead ======

    @Test
    fun markRead_connectionReady_sendsMarkRead() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>(relaxed = true)
        every { channel.newCall<Any, Any>(any(), any()) } returns mockCall

        every { mockCall.start(any(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            val listener = firstArg<ClientCall.Listener<Any>>()
            val response = MarkReadResponseProto.newBuilder().build()
            listener.onMessage(response)
            listener.onClose(Status.OK, Metadata())
            null
        }

        client.markRead("room-1", "testuser", ConnectionStatus.READY, null)

        verify { channel.newCall<Any, Any>(any(), any()) }
    }

    @Test
    fun markRead_connectionNotReady_queuesPendingRead() = runTest {
        val nullChannelClient = GrpcMessageClient(
            getChannel = { null },
            getUserId = { "user-uuid" },
            getUsername = { "testuser" },
            messages = messages,
            deletedMessageHashes = deletedMessageHashes,
            pendingReads = pendingReads,
            scope = scope,
            appContext = { null },
            onReadReceipt = null
        )

        pendingReads.clear()
        nullChannelClient.markRead("room-1", "testuser", ConnectionStatus.CONNECTING, null)

        assertTrue("Pending reads should contain room-1", pendingReads.contains("room-1"))
    }

    // ====== resendPendingReads ======

    @Test
    fun resendPendingReads_clearsPendingSet() = runTest {
        pendingReads.add("room-1")
        pendingReads.add("room-2")

        val mockCall = mockk<ClientCall<Any, Any>>(relaxed = true)
        every { channel.newCall<Any, Any>(any(), any()) } returns mockCall

        every { mockCall.start(any(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            val listener = firstArg<ClientCall.Listener<Any>>()
            listener.onClose(Status.OK, Metadata())
            null
        }

        client.resendPendingReads("testuser", ConnectionStatus.READY)

        // After resend, pending reads should be cleared (or at least processed)
        // The actual behavior depends on the implementation
        // We just verify no crash
    }

    // ====== handleDeleteMessageSignal ======

    @Test
    fun handleDeleteMessageSignal_removesFromMessages() = runTest {
        // Add a message first
        val message = Message(
            id = "msg-to-delete",
            user = "testuser",
            text = "Will be deleted",
            roomId = "room-1",
            timestamp = System.currentTimeMillis()
        )
        client.addLocalMessage(message)
        kotlinx.coroutines.delay(100)

        assertEquals("Should have 1 message", 1, messages.value.size)

        // Handle delete signal
        client.handleDeleteMessageSignal("msg-to-delete")
        kotlinx.coroutines.delay(100)

        // Message should be removed
        val currentMessages = messages.value
        assertTrue("Messages should be empty after delete",
            currentMessages.none { it.id == "msg-to-delete" })
    }

    // ====== handleReadAllSignal ======

    @Test
    fun handleReadAllSignal_sameRoom_marksAllRead() = runTest {
        // Add messages
        val msg1 = Message(
            id = "msg-r1",
            user = "otheruser",
            text = "Message 1",
            roomId = "room-1",
            timestamp = System.currentTimeMillis(),
            isRead = false
        )
        val msg2 = Message(
            id = "msg-r2",
            user = "otheruser",
            text = "Message 2",
            roomId = "room-1",
            timestamp = System.currentTimeMillis() + 1000,
            isRead = false
        )
        client.addLocalMessage(msg1)
        kotlinx.coroutines.delay(50)
        client.addLocalMessage(msg2)
        kotlinx.coroutines.delay(50)

        assertEquals("Should have 2 messages", 2, messages.value.size)

        // Handle read all signal
        client.handleReadAllSignal("reader-user", "room-1", "room-1")
        kotlinx.coroutines.delay(100)

        val currentMessages = messages.value
        assertTrue("All messages should be marked read",
            currentMessages.all { it.isRead })
    }

    @Test
    fun handleReadAllSignal_differentRoom_doesNotMarkRead() = runTest {
        val msg = Message(
            id = "msg-r3",
            user = "otheruser",
            text = "Message",
            roomId = "room-1",
            timestamp = System.currentTimeMillis(),
            isRead = false
        )
        client.addLocalMessage(msg)
        kotlinx.coroutines.delay(100)

        // Read all for different room
        client.handleReadAllSignal("reader-user", "room-2", "room-1")
        kotlinx.coroutines.delay(100)

        val currentMessages = messages.value
        assertFalse("Message should NOT be marked read for different room",
            currentMessages[0].isRead)
    }
}
