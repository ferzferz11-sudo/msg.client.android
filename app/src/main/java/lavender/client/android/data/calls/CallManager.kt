package lavender.client.android.data.calls

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.proto.CallMessageProto

object CallManager {
    private const val TAG = "CallManager"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private val _currentCall = MutableStateFlow<CallMessageProto?>(null)
    val currentCall: StateFlow<CallMessageProto?> = _currentCall

    init {
        scope.launch {
            GrpcClient.callSignals.collect { signal ->
                handleIncomingSignal(signal)
            }
        }
    }

    private fun handleIncomingSignal(signal: CallMessageProto) {
        Log.d(TAG, "Received signal: ${signal.type} from ${signal.senderId}")
        when (signal.type) {
            CallMessageProto.Type.INITIATE -> {
                _currentCall.value = signal
                // TODO: Trigger incoming call UI
            }
            CallMessageProto.Type.ACCEPT -> {
                // TODO: Start WebRTC session
            }
            CallMessageProto.Type.REJECT, CallMessageProto.Type.HANGUP -> {
                _currentCall.value = null
                // TODO: Close WebRTC and UI
            }
            else -> {
                // Handle WebRTC signals (OFFER, ANSWER, ICE_CANDIDATE)
            }
        }
    }

    fun initiateCall(receiverId: String) {
        val senderId = GrpcClient.getCurrentUsername() ?: return
        val signal = CallMessageProto(
            senderId = senderId,
            receiverId = receiverId,
            type = CallMessageProto.Type.INITIATE
        )
        GrpcClient.sendCallSignal(signal)
        _currentCall.value = signal
    }

    fun acceptCall() {
        val call = _currentCall.value ?: return
        val senderId = GrpcClient.getCurrentUsername() ?: return
        val signal = CallMessageProto(
            callId = call.callId,
            senderId = senderId,
            receiverId = call.senderId,
            type = CallMessageProto.Type.ACCEPT
        )
        GrpcClient.sendCallSignal(signal)
    }

    fun rejectCall() {
        val call = _currentCall.value ?: return
        val senderId = GrpcClient.getCurrentUsername() ?: return
        val signal = CallMessageProto(
            callId = call.callId,
            senderId = senderId,
            receiverId = call.senderId,
            type = CallMessageProto.Type.REJECT
        )
        GrpcClient.sendCallSignal(signal)
        _currentCall.value = null
    }

    fun hangup() {
        val call = _currentCall.value ?: return
        val senderId = GrpcClient.getCurrentUsername() ?: return
        val signal = CallMessageProto(
            callId = call.callId,
            senderId = senderId,
            receiverId = if (call.senderId == senderId) call.receiverId else call.senderId,
            type = CallMessageProto.Type.HANGUP
        )
        GrpcClient.sendCallSignal(signal)
        _currentCall.value = null
    }
}
