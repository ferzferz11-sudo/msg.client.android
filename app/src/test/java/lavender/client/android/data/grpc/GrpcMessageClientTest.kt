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
import io.mockk.*
import lavender.client.android.data.models.Message
import lavender.client.android.data.proto.*

class GrpcMessageClientTest {

    private lateinit var channel: ManagedChannel
    private lateinit var messages: MutableStateFlow<List<Message>>
    private lateinit var deletedMessageHashes: MutableSet<String>
    private lateinit var pendingReads: MutableSet<String>
    private lateinit var client: GrpcMessageClient
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Before
    fun setup() {
        channel = mockk()
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
            appContext = { null },
            onReadReceipt = null
        )
    }

    @Test
    fun sendMessage_validMessage_callsOnNext() {
        val mockObserver = mockk<StreamObserver<MessageProto>>()
        val message = Message(id = "msg-1", user = "testuser", text = "Hello World",
            roomId = "room-1", timestamp = System.currentTimeMillis())
        client.sendMessage(message, mockObserver)
        verify { mockObserver.onNext(any<MessageProto>()) }
    }

    @Test
    fun sendMessage_nullObserver_noCrash() {
        val message = Message(id = "msg-1", user = "testuser", text = "Hello",
            roomId = "room-1", timestamp = System.currentTimeMillis())
        client.sendMessage(message, null)
    }

    @Test
    fun addLocalMessage_addsToStateFlow() = runTest {
        val message = Message(id = "local-msg-1", user = "testuser", text = "Local message",
            roomId = "room-1", timestamp = System.currentTimeMillis())
        client.addLocalMessage(message)
        kotlinx.coroutines.delay(100)
        assertEquals(1, messages.value.size)
        assertEquals("Local message", messages.value[0].text)
    }

    @Test
    fun addLocalMessage_duplicate_replacesExisting() = runTest {
        val ts = System.currentTimeMillis()
        val m1 = Message(id = "msg-dup", user = "testuser", text = "First", roomId = "room-1", timestamp = ts)
        val m2 = Message(id = "msg-dup", user = "testuser", text = "Updated", roomId = "room-1", timestamp = ts + 1000)
        client.addLocalMessage(m1)
        kotlinx.coroutines.delay(50)
        client.addLocalMessage(m2)
        kotlinx.coroutines.delay(50)
        assertEquals(1, messages.value.size)
        assertEquals("Updated", messages.value[0].text)
    }

    @Test
    fun loadHistory_success_updatesMessages() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>()
        every { channel.newCall<Any, Any>(any(), any()) } returns mockCall
        every { mockCall.start(any(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            val listener = firstArg<ClientCall.Listener<Any>>()
            val ts = com.google.protobuf.Timestamp.newBuilder().setSeconds(1000).build()
            val msgProto = MessageProto(id = "hist-msg-1", user = "otheruser", text = "History message",
                roomId = "room-1", createdAt = ts)
            listener.onMessage(GetHistoryResponseProto(messages = listOf(msgProto)))
            listener.onClose(Status.OK, Metadata())
        }

        var called = false
        client.loadHistory("room-1") { called = true }
        kotlinx.coroutines.delay(200)

        assertEquals(1, messages.value.size)
        assertEquals("History message", messages.value[0].text)
        assertTrue("Completion called", called)
    }

    @Test
    fun loadHistory_nullChannel_noCrash() = runTest {
        val nullClient = GrpcMessageClient(
            getChannel = { null }, getUserId = { "user-uuid" }, getUsername = { "testuser" },
            messages = messages, deletedMessageHashes = deletedMessageHashes, pendingReads = pendingReads,
            scope = scope, appContext = { null }, onReadReceipt = null
        )
        var called = false
        nullClient.loadHistory("room-1") { called = true }
        assertTrue("Completion called", called)
    }

    @Test
    fun markRead_connectionReady_sendsMarkRead() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>()
        every { channel.newCall<Any, Any>(any(), any()) } returns mockCall
        every { mockCall.start(any(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            val listener = firstArg<ClientCall.Listener<Any>>()
            listener.onMessage(MarkReadResponseProto())
            listener.onClose(Status.OK, Metadata())
        }
        client.markRead("room-1", "testuser", ConnectionStatus.READY, null)
        verify { channel.newCall<Any, Any>(any(), any()) }
    }

    @Test
    fun markRead_connectionNotReady_queuesPendingRead() = runTest {
        val nullClient = GrpcMessageClient(
            getChannel = { null }, getUserId = { "user-uuid" }, getUsername = { "testuser" },
            messages = messages, deletedMessageHashes = deletedMessageHashes, pendingReads = pendingReads,
            scope = scope, appContext = { null }, onReadReceipt = null
        )
        pendingReads.clear()
        nullClient.markRead("room-1", "testuser", ConnectionStatus.CONNECTING, null)
        assertTrue("Pending reads contain room-1", pendingReads.contains("room-1"))
    }

    @Test
    fun resendPendingReads_noCrash() = runTest {
        pendingReads.add("room-1")
        pendingReads.add("room-2")
        val mockCall = mockk<ClientCall<Any, Any>>()
        every { channel.newCall<Any, Any>(any(), any()) } returns mockCall
        every { mockCall.start(any(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            firstArg<ClientCall.Listener<Any>>().onClose(Status.OK, Metadata())
        }
        client.resendPendingReads("testuser", ConnectionStatus.READY)
    }

    @Test
    fun handleDeleteMessageSignal_removesFromMessages() = runTest {
        val message = Message(id = "msg-to-delete", user = "testuser", text = "Deleted",
            roomId = "room-1", timestamp = System.currentTimeMillis())
        client.addLocalMessage(message)
        kotlinx.coroutines.delay(100)
        assertEquals(1, messages.value.size)

        client.handleDeleteMessageSignal("msg-to-delete")
        kotlinx.coroutines.delay(100)
        assertTrue("Messages empty after delete", messages.value.none { it.id == "msg-to-delete" })
    }

    @Test
    fun handleReadAllSignal_sameRoom_marksAllRead() = runTest {
        val ts = System.currentTimeMillis()
        val m1 = Message(id = "msg-r1", user = "otheruser", text = "M1", roomId = "room-1", timestamp = ts, isRead = false)
        val m2 = Message(id = "msg-r2", user = "otheruser", text = "M2", roomId = "room-1", timestamp = ts + 1000, isRead = false)
        client.addLocalMessage(m1)
        kotlinx.coroutines.delay(50)
        client.addLocalMessage(m2)
        kotlinx.coroutines.delay(50)
        assertEquals(2, messages.value.size)

        client.handleReadAllSignal("reader", "room-1", "room-1")
        kotlinx.coroutines.delay(100)
        assertTrue("All read", messages.value.all { it.isRead })
    }

    @Test
    fun handleReadAllSignal_differentRoom_doesNotMarkRead() = runTest {
        val msg = Message(id = "msg-r3", user = "otheruser", text = "M",
            roomId = "room-1", timestamp = System.currentTimeMillis(), isRead = false)
        client.addLocalMessage(msg)
        kotlinx.coroutines.delay(100)

        client.handleReadAllSignal("reader", "room-2", "room-1")
        kotlinx.coroutines.delay(100)
        assertFalse("Not marked read", messages.value[0].isRead)
    }
}
