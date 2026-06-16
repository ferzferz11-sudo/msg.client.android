package lavender.client.android.data.grpc

import android.util.Log
import io.grpc.MethodDescriptor
import io.grpc.ClientCall
import io.grpc.Metadata
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import lavender.client.android.data.proto.*

/**
 * Handles typing indicator via gRPC bidirectional stream.
 *
 * Owns: typing stream, send typing signals.
 */
class GrpcTypingClient(
    private val getChannel: () -> io.grpc.ManagedChannel?,
    private val typingUsers: MutableStateFlow<Map<String, Set<String>>>,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "GrpcTypingClient"
    }

    var typingRequestObserver: StreamObserver<TypingRequestProto>? = null
        private set

    fun startTypingStream() {
        val currentChannel = getChannel() ?: return
        val methodDesc = MethodDescriptor.newBuilder<TypingRequestProto, TypingSignalProto>()
            .setType(MethodDescriptor.MethodType.BIDI_STREAMING)
            .setFullMethodName("messenger.ChatService/Typing")
            .setRequestMarshaller(TypingRequestMarshaller())
            .setResponseMarshaller(TypingSignalMarshaller())
            .build()

        val call = currentChannel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
        typingRequestObserver = call.startTypingStream(typingUsers, scope)
    }

    fun sendTypingSignal(username: String, isTyping: Boolean, roomId: String, userId: String) {
        typingRequestObserver?.onNext(TypingRequestProto(roomId, username, isTyping, userId))
    }

    fun clearTypingObserver() {
        typingRequestObserver = null
    }

    private fun ClientCall<TypingRequestProto, TypingSignalProto>.startTypingStream(
        typingUsers: MutableStateFlow<Map<String, Set<String>>>,
        scope: CoroutineScope
    ): StreamObserver<TypingRequestProto> {
        val responseObserver = object : StreamObserver<TypingSignalProto> {
            override fun onNext(value: TypingSignalProto) {
                typingUsers.update { current ->
                    val roomTyping = current[value.roomId]?.toMutableSet() ?: mutableSetOf()
                    if (value.isTyping) roomTyping.add(value.username) else roomTyping.remove(value.username)
                    current + (value.roomId to roomTyping)
                }
            }
            override fun onError(t: Throwable) {
                Log.e(TAG, "Typing stream error", t)
                scope.launch {
                    delay(5000)
                    startTypingStream()
                }
            }
            override fun onCompleted() {}
        }

        this.start(object : ClientCall.Listener<TypingSignalProto>() {
            override fun onMessage(message: TypingSignalProto) = responseObserver.onNext(message)
            override fun onClose(status: io.grpc.Status, trailers: Metadata) {}
        }, Metadata())
        this.request(Int.MAX_VALUE)

        return object : StreamObserver<TypingRequestProto> {
            override fun onNext(value: TypingRequestProto) = this@startTypingStream.sendMessage(value)
            override fun onError(t: Throwable) = this@startTypingStream.cancel("Error", t)
            override fun onCompleted() = this@startTypingStream.halfClose()
        }
    }
}
