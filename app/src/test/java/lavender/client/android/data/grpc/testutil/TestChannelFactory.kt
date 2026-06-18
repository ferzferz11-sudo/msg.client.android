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
import io.mockk.*

object TestChannelFactory {

    inline fun <reified T : Any> mockChannel(): ManagedChannel {
        val channel = mockk<ManagedChannel>(relaxed = true)
        val call = mockk<ClientCall<Any, Any>>(relaxed = true)
        every {
            channel.newCall<Any, Any>(any<MethodDescriptor<Any, Any>>(), any<CallOptions>())
        } returns call
        return channel
    }

    fun <T> createStateFlow(initial: T): MutableStateFlow<T> = MutableStateFlow(initial)

    fun <T> createSharedFlow(): MutableSharedFlow<T> = MutableSharedFlow()
}
