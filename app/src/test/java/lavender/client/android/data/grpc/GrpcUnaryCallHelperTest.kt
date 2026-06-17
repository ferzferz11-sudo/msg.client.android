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
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*

/**
 * Unit-тесты для GrpcUnaryCallHelper (top-level функции unaryCall, unaryCallWithClass).
 *
 * Тестируем: успешный вызов, null channel, server error, class-based variant.
 */
class GrpcUnaryCallHelperTest {

    @Test
    fun unaryCall_success_returnsResponse() = runTest {
        val mockCall = mock(ClientCall::class.java)
        val mockChannel = mock(ManagedChannel::class.java)

        `when`(mockChannel.newCall<Any, Any>(any(MethodDescriptor::class.java), any(CallOptions::class.java)))
            .thenReturn(mockCall)

        `when`(mockCall.start(any(ClientCall.Listener::class.java), any(Metadata::class.java)))
            .thenAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                val listener = invocation.arguments[0] as ClientCall.Listener<Any>
                // Simulate response
                val response = GetChatsResponseProto.newBuilder().build()
                listener.onMessage(response)
                listener.onClose(Status.OK, Metadata())
                null
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
        val mockCall = mock(ClientCall::class.java)
        val mockChannel = mock(ManagedChannel::class.java)

        `when`(mockChannel.newCall<Any, Any>(any(MethodDescriptor::class.java), any(CallOptions::class.java)))
            .thenReturn(mockCall)

        `when`(mockCall.start(any(ClientCall.Listener::class.java), any(Metadata::class.java)))
            .thenAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                val listener = invocation.arguments[0] as ClientCall.Listener<Any>
                listener.onClose(Status.INTERNAL.withDescription("Internal error"), Metadata())
                null
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
        val mockCall = mock(ClientCall::class.java)
        val mockChannel = mock(ManagedChannel::class.java)

        `when`(mockChannel.newCall<Any, Any>(any(MethodDescriptor::class.java), any(CallOptions::class.java)))
            .thenReturn(mockCall)

        `when`(mockCall.start(any(ClientCall.Listener::class.java), any(Metadata::class.java)))
            .thenAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                val listener = invocation.arguments[0] as ClientCall.Listener<Any>
                val response = GetChatsResponseProto.newBuilder().build()
                listener.onMessage(response)
                listener.onClose(Status.OK, Metadata())
                null
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
