package lavender.client.android.data.grpc

import android.util.Log
import io.grpc.MethodDescriptor
import io.grpc.ClientCall
import io.grpc.Metadata
import io.grpc.Status
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import lavender.client.android.data.proto.*
import lavender.client.android.data.calls.CallManager

/**
 * Handles video call signaling via gRPC bidirectional stream.
 *
 * Owns: call session stream, call signal sending.
 */
class GrpcCallClient(
    private val getChannel: () -> io.grpc.ManagedChannel?,
    private val getUsername: () -> String?,
    private val getUserId: () -> String?,
    private val callSignals: MutableSharedFlow<CallMessageProto>,
    private val connectionStatus: MutableStateFlow<ConnectionStatus>,
    private val requestObserverRef: () -> StreamObserver<*>?,
    private val scope: CoroutineScope,
    private val onCallStreamError: (Throwable) -> Unit
) {
    companion object {
        private const val TAG = "GrpcCallClient"
    }

    var callRequestObserver: StreamObserver<CallMessageProto>? = null
        private set

    fun startCallSession() {
        val currentChannel = getChannel()
        if (currentChannel == null || currentChannel.isShutdown) {
            Log.e(TAG, "Cannot start call session: channel not ready")
            return
        }
        if (callRequestObserver != null) return // Already started

        Log.d(TAG, "Starting CallSession stream")
        val methodDesc = MethodDescriptor.newBuilder<CallMessageProto, CallMessageProto>()
            .setType(MethodDescriptor.MethodType.BIDI_STREAMING)
            .setFullMethodName("messenger.ChatService/CallSession")
            .setRequestMarshaller(CallMessageProtoMarshaller())
            .setResponseMarshaller(CallMessageProtoMarshaller())
            .build()

        val call = currentChannel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
        callRequestObserver = call.startCallStream(scope, callSignals, connectionStatus, onCallStreamError)

        // Send identity signal to register with the hub
        val identityId = getUserId() ?: getUsername()
        identityId?.let { id ->
            callRequestObserver?.onNext(CallMessageProto(
                senderId = id,
                type = CallMessageProto.Type.ICE_CANDIDATE,
                payload = "IDENTITY"
            ))
        }
    }

    fun sendCallSignal(signal: CallMessageProto) {
        if (callRequestObserver == null) {
            startCallSession()
        }
        callRequestObserver?.onNext(signal)
    }

    fun clearCallObserver() {
        callRequestObserver = null
    }

    private fun ClientCall<CallMessageProto, CallMessageProto>.startCallStream(
        scope: CoroutineScope,
        callSignals: MutableSharedFlow<CallMessageProto>,
        connectionStatus: MutableStateFlow<ConnectionStatus>,
        onCallStreamError: (Throwable) -> Unit
    ): StreamObserver<CallMessageProto> {
        val responseObserver = object : StreamObserver<CallMessageProto> {
            override fun onNext(value: CallMessageProto) {
                scope.launch { callSignals.emit(value) }
            }
            override fun onError(t: Throwable) {
                Log.e(TAG, "Call session stream error", t)
                callRequestObserver = null
                lavender.client.android.data.calls.CallManager.clearCurrentCall()
                scope.launch {
                    delay(5000)
                    startCallSession()
                }
            }
            override fun onCompleted() {
                Log.d(TAG, "Call session stream completed")
                callRequestObserver = null
                lavender.client.android.data.calls.CallManager.clearCurrentCall()
            }
        }

        this.start(object : ClientCall.Listener<CallMessageProto>() {
            override fun onMessage(message: CallMessageProto) = responseObserver.onNext(message)
            override fun onClose(status: Status, trailers: Metadata) {
                if (!status.isOk) {
                    connectionStatus.value = ConnectionStatus.FAILED
                    // Clear request observer to prevent broken stream reuse
                    requestObserverRef()?.let { /* cleared by caller */ }
                }
                if (status.isOk) responseObserver.onCompleted() else responseObserver.onError(status.asRuntimeException())
            }
        }, Metadata())
        this.request(Int.MAX_VALUE)

        return object : StreamObserver<CallMessageProto> {
            override fun onNext(value: CallMessageProto) = this@startCallStream.sendMessage(value)
            override fun onError(t: Throwable) = this@startCallStream.cancel("Error", t)
            override fun onCompleted() = this@startCallStream.halfClose()
        }
    }
}
