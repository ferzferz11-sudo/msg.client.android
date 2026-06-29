package lavender.client.android.data.calls

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.proto.CallMessageProto
import org.json.JSONObject

object CallManager {
    private const val TAG = "CallManager"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private val _currentCall = MutableStateFlow<CallMessageProto?>(null)
    val currentCall: StateFlow<CallMessageProto?> = _currentCall

    private val _incomingSignals = MutableSharedFlow<CallMessageProto>(extraBufferCapacity = 64)
    val incomingSignals: SharedFlow<CallMessageProto> = _incomingSignals

    private var appContext: Context? = null
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true
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

        Log.d(TAG, "Received signal: ${signal.type} from ${signal.senderId} (Me: ${GrpcClient.getCurrentUsername()}/${GrpcClient.getUserId()})")
        
        if (isMe(signal.senderId)) {
            when (signal.type) {
                CallMessageProto.Type.INITIATE, 
                CallMessageProto.Type.INITIATE_CONFERENCE,
                CallMessageProto.Type.JOIN_CONFERENCE,
                CallMessageProto.Type.UPDATE_CONFERENCE,
                CallMessageProto.Type.INVITE_TO_CONFERENCE -> {
                    Log.d(TAG, "Handling self-initiated signal ${signal.type} to update local state")
                    _currentCall.value = signal
                    scope.launch { _incomingSignals.emit(signal) }
                    return
                }
                else -> {}
            }
        }

        scope.launch { _incomingSignals.emit(signal) }

        when (signal.type) {
            CallMessageProto.Type.INITIATE -> {
                if (_currentCall.value == null) {
                    _currentCall.value = signal
                    val senderId = signal.senderId
                    val displayName = signal.senderName.takeIf { it.isNotEmpty() } ?: signal.senderId
                    appContext?.let { ctx ->
                        val intent = android.content.Intent(ctx, lavender.client.android.CallActivity::class.java).apply {
                            putExtra("CALL_ID", signal.callId)
                            putExtra("RECEIVER_ID", senderId)
                            putExtra("SENDER_NAME", displayName)
                            putExtra("IS_INCOMING", true)
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        ctx.startActivity(intent)
                    }
                } else {
                    Log.w(TAG, "Already in a call, ignoring INITIATE")
                }
            }
            CallMessageProto.Type.INITIATE_CONFERENCE -> {
                if (_currentCall.value == null) {
                    _currentCall.value = signal
                    appContext?.let { 
                        CallNavigator.joinConference(it, signal.roomId)
                    }
                }
            }
            CallMessageProto.Type.REJECT, CallMessageProto.Type.HANGUP, CallMessageProto.Type.END_CONFERENCE -> {
                if (_currentCall.value?.callId == signal.callId || _currentCall.value?.roomId == signal.roomId) {
                    _currentCall.value = null
                }
            }
            else -> {}
        }
    }

    fun initiateCall(receiverId: String) {
        val senderId = GrpcClient.getUserId() ?: GrpcClient.getCurrentUsername() ?: return
        
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

    fun syncCallState(callId: String, otherPartyId: String, isIncoming: Boolean) {
        if (_currentCall.value == null || (_currentCall.value?.callId != callId && callId.isNotEmpty())) {
            Log.d(TAG, "Syncing call state for $callId (isIncoming: $isIncoming, other: $otherPartyId)")
            val myId = GrpcClient.getUserId() ?: GrpcClient.getCurrentUsername() ?: ""
            _currentCall.value = CallMessageProto(
                callId = callId,
                senderId = if (isIncoming) otherPartyId else myId,
                receiverId = if (isIncoming) myId else otherPartyId,
                type = CallMessageProto.Type.INITIATE
            )
        }
    }

    fun clearCurrentCall() {
        Log.d(TAG, "Clearing current call state")
        _currentCall.value = null
    }

    fun acceptCall() {
        val call = _currentCall.value ?: return
        val myId = GrpcClient.getUserId() ?: GrpcClient.getCurrentUsername() ?: return
        val signal = CallMessageProto(
            callId = call.callId,
            senderId = myId,
            receiverId = call.senderId,
            type = CallMessageProto.Type.ACCEPT
        )
        GrpcClient.sendCallSignal(signal)
    }

    fun rejectCall() {
        val call = _currentCall.value ?: return
        val myId = GrpcClient.getUserId() ?: GrpcClient.getCurrentUsername() ?: return
        val signal = CallMessageProto(
            callId = call.callId,
            senderId = myId,
            receiverId = call.senderId,
            type = CallMessageProto.Type.REJECT
        )
        GrpcClient.sendCallSignal(signal)
        _currentCall.value = null
    }

    fun hangup() {
        val call = _currentCall.value ?: return
        val myId = GrpcClient.getUserId() ?: GrpcClient.getCurrentUsername() ?: return
        val targetId = if (isMe(call.senderId)) call.receiverId else call.senderId
        
        Log.d(TAG, "Hangup: myId=$myId, call.senderId=${call.senderId}, targetId=$targetId")
        
        val signal = CallMessageProto(
            callId = call.callId,
            senderId = myId,
            receiverId = targetId,
            type = CallMessageProto.Type.HANGUP
        )
        GrpcClient.sendCallSignal(signal)
        _currentCall.value = null
    }

    fun isMe(id: String): Boolean {
        if (id.isEmpty()) return false
        val myUsername = GrpcClient.getCurrentUsername()
        val myUserId = GrpcClient.getUserId()
        
        // Compare with both username and UUID
        if (id == myUsername || id == myUserId) return true
        
        // Also check if id is a UUID that belongs to me (safety check)
        return false
    }

    fun sendWebRtcSignal(receiverId: String, type: CallMessageProto.Type, payload: String) {
    	val call = _currentCall.value ?: return
    	// Always use UUID for senderId (never username)
    	val senderId = GrpcClient.getUserId() ?: GrpcClient.getCurrentUsername() ?: return
    	val signal = CallMessageProto(
    		callId = call.callId,
    		senderId = senderId,
    		receiverId = receiverId,
    		type = type,
    		payload = payload,
    		roomId = call.roomId
    	)
    	GrpcClient.sendCallSignal(signal)
    }

    fun initiateConference(roomId: String) {
        val senderId = GrpcClient.getCurrentUsername() ?: return
        GrpcClient.startCallSession()
        val signal = CallMessageProto(
            senderId = senderId,
            roomId = roomId,
            type = CallMessageProto.Type.INITIATE_CONFERENCE
        )
        GrpcClient.sendCallSignal(signal)
        _currentCall.value = signal
    }

    fun joinConference(roomId: String) {
        val senderId = GrpcClient.getCurrentUsername() ?: return
        GrpcClient.startCallSession()
        val signal = CallMessageProto(
            senderId = senderId,
            roomId = roomId,
            type = CallMessageProto.Type.JOIN_CONFERENCE
        )
        GrpcClient.sendCallSignal(signal)
        _currentCall.value = signal
    }

    fun leaveConference() {
        val call = _currentCall.value ?: return
        val senderId = GrpcClient.getCurrentUsername() ?: return
        val signal = CallMessageProto(
            senderId = senderId,
            roomId = call.roomId,
            type = CallMessageProto.Type.LEAVE_CONFERENCE
        )
        GrpcClient.sendCallSignal(signal)
        _currentCall.value = null
    }

    fun inviteToConference(roomId: String, targetUserId: String, targetUserName: String) {
        val senderId = GrpcClient.getCurrentUsername() ?: return
        val signal = CallMessageProto(
            senderId = senderId,
            receiverId = targetUserId,
            receiverName = targetUserName,
            roomId = roomId,
            type = CallMessageProto.Type.INVITE_TO_CONFERENCE
        )
        GrpcClient.sendCallSignal(signal)
    }

    fun removeFromConference(roomId: String, targetUserId: String) {
        val senderId = GrpcClient.getCurrentUsername() ?: return
        val signal = CallMessageProto(
            senderId = senderId,
            receiverId = targetUserId,
            roomId = roomId,
            type = CallMessageProto.Type.REMOVE_FROM_CONFERENCE
        )
        GrpcClient.sendCallSignal(signal)
    }

    fun updateConferenceMetadata(roomId: String, topic: String, startTime: Long) {
        val senderId = GrpcClient.getCurrentUsername() ?: return
        val payload = JSONObject().apply {
            put("topic", topic)
            put("start_time", startTime)
        }.toString()
        val signal = CallMessageProto(
            senderId = senderId,
            roomId = roomId,
            type = CallMessageProto.Type.UPDATE_CONFERENCE,
            payload = payload
        )
        GrpcClient.sendCallSignal(signal)
    }

    fun endConference(roomId: String? = null) {
        val targetRoomId = roomId ?: _currentCall.value?.roomId ?: return
        val senderId = GrpcClient.getCurrentUsername() ?: return
        val signal = CallMessageProto(
            senderId = senderId,
            roomId = targetRoomId,
            type = CallMessageProto.Type.END_CONFERENCE
        )
        GrpcClient.sendCallSignal(signal)
        if (targetRoomId == _currentCall.value?.roomId) {
            _currentCall.value = null
        }
    }
}
