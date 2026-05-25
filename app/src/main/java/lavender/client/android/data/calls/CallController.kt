package lavender.client.android.data.calls

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.proto.CallMessageProto
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

/**
 * Handles the business logic of a call, including signaling and state transitions.
 */
class CallController(
    private val callId: String,
    private val receiverId: String,
    private val isIncoming: Boolean,
    private val isConference: Boolean,
    private val roomId: String,
    private val webRtcClient: WebRtcClient?,
    private val listener: Listener
) {
    private val TAG = "CallController"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    interface Listener {
        fun onCallAccepted()
        fun onCallTerminated(reason: String)
        fun onConferencePresenceUpdated(participants: List<String>, creatorId: String)
        fun onStatusUpdate(status: String)
        fun onIdAssigned(newCallId: String)
    }

    init {
        observeSignals()
    }

    private fun observeSignals() {
        scope.launch {
            CallManager.incomingSignals.collectLatest { signal ->
                // Route check
                if (signal.callId != callId && callId.isNotEmpty() && !isConference) return@collectLatest
                if (isConference && signal.roomId != roomId) return@collectLatest

                // Self echo ignore (except INITIATE to sync callId)
                if (CallManager.isMe(signal.senderId) && signal.type != CallMessageProto.Type.INITIATE) return@collectLatest

                if (signal.callId.isNotEmpty() && callId.isEmpty()) {
                    listener.onIdAssigned(signal.callId)
                }

                when (signal.type) {
                    CallMessageProto.Type.ACCEPT -> {
                        if (!isIncoming) {
                            webRtcClient?.createOffer()
                            listener.onCallAccepted()
                        }
                    }
                    CallMessageProto.Type.OFFER -> {
                        val sdp = SessionDescription(SessionDescription.Type.OFFER, signal.payload)
                        webRtcClient?.setRemoteDescription(sdp)
                    }
                    CallMessageProto.Type.ANSWER -> {
                        val sdp = SessionDescription(SessionDescription.Type.ANSWER, signal.payload)
                        webRtcClient?.setRemoteDescription(sdp)
                    }
                    CallMessageProto.Type.ICE_CANDIDATE -> {
                        try {
                            val json = JSONObject(signal.payload)
                            val candidate = IceCandidate(
                                json.getString("sdpMid"),
                                json.getInt("sdpMLineIndex"),
                                json.getString("candidate")
                            )
                            webRtcClient?.addIceCandidate(candidate)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse ICE candidate", e)
                        }
                    }
                    CallMessageProto.Type.REJECT, CallMessageProto.Type.HANGUP, CallMessageProto.Type.END_CONFERENCE -> {
                        val reason = if (signal.type == CallMessageProto.Type.END_CONFERENCE) "Конференция завершена" else "Звонок завершен"
                        listener.onCallTerminated(reason)
                    }
                    CallMessageProto.Type.JOIN_CONFERENCE, CallMessageProto.Type.LEAVE_CONFERENCE -> {
                        if (isConference) handleConferencePresence(signal)
                    }
                    else -> {}
                }
            }
        }
    }

    private fun handleConferencePresence(signal: CallMessageProto) {
        if (signal.type == CallMessageProto.Type.JOIN_CONFERENCE) {
            try {
                val response = JSONObject(signal.payload)
                val participantsJson = response.getJSONObject("participants")
                val creatorId = response.optString("creator_id", "")
                val names = mutableListOf<String>()
                val keys = participantsJson.keys()
                while (keys.hasNext()) names.add(participantsJson.getString(keys.next()))
                listener.onConferencePresenceUpdated(names, creatorId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse participants", e)
            }
        }
    }
}
