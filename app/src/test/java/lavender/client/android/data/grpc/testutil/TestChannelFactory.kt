package lavender.client.android.data.grpc.testutil

import io.grpc.CallOptions
import io.grpc.ClientCall
import io.grpc.ManagedChannel
import io.grpc.MethodDescriptor
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharedFlow
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.ArgumentMatchers.any

/**
 * Создаёт mock ManagedChannel, который возвращает mock stub для любого метода.
 * Используется для тестирования gRPC клиентов без реального сервера.
 */
object TestChannelFactory {

    /**
     * Создаёт mock channel, который для любого вызова newCall возвращает
     * mock ClientCall, который можно настроить через listener.
     */
    fun createMockChannel(): ManagedChannel {
        val channel = mock(ManagedChannel::class.java)
        val call = mock(ClientCall::class.java)

        `when`(channel.newCall<Any, Any>(any(MethodDescriptor::class.java), any(CallOptions::class.java)))
            .thenReturn(call)

        return channel
    }

    /**
     * Создаёт mock channel с настроенным listener для конкретного метода.
     * onMessage вызывается при onMessage(listener), onClose при завершении.
     */
    fun createMockChannelWithListener(
        onMessage: (ClientCall.Listener<*>) -> Unit,
        onClose: (io.grpc.Status, io.grpc.Metadata) -> Unit = { _, _ -> }
    ): ManagedChannel {
        val channel = mock(ManagedChannel::class.java)
        val call = mock(ClientCall::class.java)

        `when`(call.start(any(ClientCall.Listener::class.java), any(io.grpc.Metadata::class.java)))
            .thenAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                val listener = invocation.arguments[0] as ClientCall.Listener<Any>
                onMessage(listener)
                onClose(io.grpc.Status.OK, io.grpc.Metadata())
                null
            }

        `when`(channel.newCall<Any, Any>(any(MethodDescriptor::class.java), any(CallOptions::class.java)))
            .thenReturn(call)

        return channel
    }

    /**
     * Создаёт StateFlow с начальным значением для тестирования.
     */
    fun <T> createStateFlow(initial: T): MutableStateFlow<T> = MutableStateFlow(initial)

    /**
     * Создаёт MutableSharedFlow для тестирования.
     */
    fun <T> createSharedFlow(): MutableSharedFlow<T> = MutableSharedFlow()
}
