package lavender.client.android.data.calls

import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import lavender.client.android.CallActivity
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.proto.CallMessageProto

object CallManager {
    private const val TAG = "CallManager"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private val _currentCall = MutableStateFlow<CallMessageProto?>(null)
    val currentCall: StateFlow<CallMessageProto?> = _currentCall

    private val _incomingSignals = MutableSharedFlow<CallMessageProto>(extraBufferCapacity = 64)
    val incomingSignals: SharedFlow<CallMessageProto> = _incomingSignals

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        scope.launch {
            GrpcClient.callSignals.collect { signal ->
                handleIncomingSignal(signal)
            }
        }
    }

    private fun handleIncomingSignal(signal: CallMessageProto) {
        // Ignore internal identity signals
        if (signal.payload == "IDENTITY") return

        Log.d(TAG, "Received signal: ${signal.type} from ${signal.senderId}")
        
        val currentUsername = GrpcClient.getCurrentUsername()
        if (signal.senderId == currentUsername && signal.type == CallMessageProto.Type.INITIATE) {
            Log.d(TAG, "Handling self-initiated INITIATE signal to update local call state")
            _currentCall.value = signal
            scope.launch { _incomingSignals.emit(signal) }
            return
        }

        scope.launch { _incomingSignals.emit(signal) }

        when (signal.type) {
            CallMessageProto.Type.INITIATE -> {
                if (_currentCall.value == null) {
                    _currentCall.value = signal
                    launchCallActivity(signal.callId, signal.senderId, true)
                } else {
                    Log.w(TAG, "Already in a call, ignoring INITIATE")
                    // Optionally send BUSY signal back
                }
            }
            CallMessageProto.Type.REJECT, CallMessageProto.Type.HANGUP -> {
                if (_currentCall.value?.callId == signal.callId) {
                    _currentCall.value = null
                }
            }
            else -> {}
        }
    }

    private fun launchCallActivity(callId: String, receiverId: String, isIncoming: Boolean) {
        appContext?.let { context ->
            val intent = Intent(context, CallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("CALL_ID", callId)
                putExtra("RECEIVER_ID", receiverId)
                putExtra("IS_INCOMING", isIncoming)
            }
            context.startActivity(intent)
        }
    }

    fun initiateCall(receiverId: String) {
        val senderId = GrpcClient.getCurrentUsername() ?: return
        
        // Clear state before starting new call
        _currentCall.value = null

        GrpcClient.startCallSession()
        
        val signal = CallMessageProto(
            senderId = senderId,
            receiverId = receiverId,
            type = CallMessageProto.Type.INITIATE
        )
        GrpcClient.sendCallSignal(signal)
        _currentCall.value = signal
        
        // Activity will be launched from the UI that calls this method
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

    fun sendWebRtcSignal(receiverId: String, type: CallMessageProto.Type, payload: String) {
        val call = _currentCall.value ?: return
        val senderId = GrpcClient.getCurrentUsername() ?: return
        val signal = CallMessageProto(
            callId = call.callId,
            senderId = senderId,
            receiverId = receiverId,
            type = type,
            payload = payload
        )
        GrpcClient.sendCallSignal(signal)
    }
}
