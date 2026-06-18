package lavender.client.android.data.grpc

import io.grpc.CallOptions
import io.grpc.ClientCall
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Status
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import io.mockk.*
import lavender.client.android.data.proto.*

/**
 * Unit-тесты для GrpcUnaryCallHelper (top-level функции unaryCall, unaryCallWithClass).
 */
class GrpcUnaryCallHelperTest {

    @Test
    fun unaryCall_success_returnsResponse() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>(relaxed = true)
        val mockChannel = mockk<ManagedChannel>(relaxed = true)

        every {
            mockChannel.newCall<Any, Any>(any<MethodDescriptor<Any, Any>>(), any<CallOptions>())
        } returns mockCall

        every {
            mockCall.start(any<ClientCall.Listener<Any>>(), any<Metadata>())
        } answers {
            @Suppress("UNCHECKED_CAST")
            val listener = firstArg<ClientCall.Listener<Any>>()
            val response = GetChatsResponseProto.newBuilder().build()
            listener.onMessage(response)
            listener.onClose(Status.OK, Metadata())
        }

        val result = unaryCall(
            getChannel = { mockChannel },
            fullMethod = "messenger.ChatService/GetChats",
            request = GetChatsRequestProto.newBuilder().build(),
            requestMarshaller = GetChatsRequestMarshaller(),
            responseMarshaller = GetChatsResponseMarshaller()
        )

        assertNotNull("Result should not be null", result)
    }

    @Test
    fun unaryCall_nullChannel_returnsNull() = runTest {
        val result = unaryCall(
            getChannel = { null },
            fullMethod = "messenger.ChatService/GetChats",
            request = GetChatsRequestProto.newBuilder().build(),
            requestMarshaller = GetChatsRequestMarshaller(),
            responseMarshaller = GetChatsResponseMarshaller()
        )

        assertNull("Result should be null when channel is null", result)
    }

    @Test
    fun unaryCall_serverError_returnsNull() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>(relaxed = true)
        val mockChannel = mockk<ManagedChannel>(relaxed = true)

        every {
            mockChannel.newCall<Any, Any>(any<MethodDescriptor<Any, Any>>(), any<CallOptions>())
        } returns mockCall

        every {
            mockCall.start(any<ClientCall.Listener<Any>>(), any<Metadata>())
        } answers {
            @Suppress("UNCHECKED_CAST")
            firstArg<ClientCall.Listener<Any>>()
                .onClose(Status.INTERNAL.withDescription("Internal error"), Metadata())
        }

        val result = unaryCall(
            getChannel = { mockChannel },
            fullMethod = "messenger.ChatService/GetChats",
            request = GetChatsRequestProto.newBuilder().build(),
            requestMarshaller = GetChatsRequestMarshaller(),
            responseMarshaller = GetChatsResponseMarshaller()
        )

        assertNull("Result should be null on server error", result)
    }

    @Test
    fun unaryCallWithClass_success_returnsResponse() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>(relaxed = true)
        val mockChannel = mockk<ManagedChannel>(relaxed = true)

        every {
            mockChannel.newCall<Any, Any>(any<MethodDescriptor<Any, Any>>(), any<CallOptions>())
        } returns mockCall

        every {
            mockCall.start(any<ClientCall.Listener<Any>>(), any<Metadata>())
        } answers {
            @Suppress("UNCHECKED_CAST")
            val listener = firstArg<ClientCall.Listener<Any>>()
            val response = GetChatsResponseProto.newBuilder().build()
            listener.onMessage(response)
            listener.onClose(Status.OK, Metadata())
        }

        val result = unaryCallWithClass(
            getChannel = { mockChannel },
            fullMethod = "messenger.ChatService/GetChats",
            request = GetChatsRequestProto.newBuilder().build(),
            responseType = GetChatsResponseProto::class.java
        )

        assertNotNull("Result should not be null", result)
    }
}
