package lavender.client.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import lavender.client.android.databinding.ActivityCallBinding
import lavender.client.android.data.calls.CallManager
import lavender.client.android.data.calls.WebRtcClient
import lavender.client.android.data.proto.CallMessageProto
import org.webrtc.*
import org.json.JSONObject

class CallActivity : AppCompatActivity(), WebRtcClient.Observer {
    private lateinit var binding: ActivityCallBinding
    private var webRtcClient: WebRtcClient? = null
    private var callId: String = ""
    private var receiverId: String = ""
    private var isIncoming: Boolean = false
    
    private var isMicEnabled = true
    private var isCameraEnabled = true

    companion object {
        private const val TAG = "CallActivity"
        private const val PERMISSION_CODE = 101
        private val PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        callId = intent.getStringExtra("CALL_ID") ?: ""
        receiverId = intent.getStringExtra("RECEIVER_ID") ?: ""
        isIncoming = intent.getBooleanExtra("IS_INCOMING", false)

        binding.tvCallerName.text = receiverId

        if (isIncoming) {
            binding.btnAccept.visibility = View.VISIBLE
            binding.btnMic.visibility = View.GONE
            binding.btnCamera.visibility = View.GONE
        }

        if (!hasPermissions()) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_CODE)
        } else if (!isIncoming) {
            initWebRtc()
        }

        setupButtons()
        observeSignals()
    }

    private fun setupButtons() {
        binding.btnHangup.setOnClickListener {
            if (isIncoming && webRtcClient == null) {
                CallManager.rejectCall()
            } else {
                CallManager.hangup()
            }
            finish()
        }

        binding.btnAccept.setOnClickListener {
            binding.btnAccept.visibility = View.GONE
            binding.btnMic.visibility = View.VISIBLE
            binding.btnCamera.visibility = View.VISIBLE
            initWebRtc()
            CallManager.acceptCall()
        }

        binding.btnMic.setOnClickListener {
            isMicEnabled = !isMicEnabled
            webRtcClient?.toggleMic(isMicEnabled)
            binding.btnMic.setImageResource(if (isMicEnabled) R.drawable.ic_mic_on else R.drawable.ic_mic_off)
        }

        binding.btnCamera.setOnClickListener {
            isCameraEnabled = !isCameraEnabled
            webRtcClient?.toggleCamera(isCameraEnabled)
            binding.btnCamera.setImageResource(if (isCameraEnabled) R.drawable.ic_videocam_on else R.drawable.ic_videocam_off)
        }
    }

    private fun observeSignals() {
        lifecycleScope.launch {
            CallManager.incomingSignals.collectLatest { signal ->
                if (signal.callId != callId && callId.isNotEmpty()) return@collectLatest
                
                when (signal.type) {
                    CallMessageProto.Type.ACCEPT -> {
                        webRtcClient?.createOffer()
                    }
                    CallMessageProto.Type.OFFER -> {
                        val sdp = SessionDescription(SessionDescription.Type.OFFER, signal.payload)
                        webRtcClient?.setRemoteDescription(sdp)
                        webRtcClient?.createAnswer()
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
                            Log.e(TAG, "Error parsing ICE candidate", e)
                        }
                    }
                    CallMessageProto.Type.REJECT, CallMessageProto.Type.HANGUP -> {
                        runOnUiThread {
                            Toast.makeText(this@CallActivity, "Звонок завершен", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun hasPermissions(): Boolean {
        return PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun initWebRtc() {
        webRtcClient = WebRtcClient(this, this)
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
        webRtcClient?.initPeerConnection(iceServers)
        webRtcClient?.startLocalStream(binding.localView)
    }

    override fun onLocalStream(stream: MediaStream) {
        runOnUiThread {
            stream.videoTracks.getOrNull(0)?.addSink(binding.localView)
        }
    }

    override fun onRemoteStream(stream: MediaStream) {
        runOnUiThread {
            binding.remoteView.init(EglBase.create().eglBaseContext, null)
            stream.videoTracks.getOrNull(0)?.addSink(binding.remoteView)
        }
    }

    override fun onIceCandidate(candidate: IceCandidate) {
        val payload = JSONObject().apply {
            put("sdpMid", candidate.sdpMid)
            put("sdpMLineIndex", candidate.sdpMLineIndex)
            put("candidate", candidate.sdp)
        }.toString()
        CallManager.sendWebRtcSignal(receiverId, CallMessageProto.Type.ICE_CANDIDATE, payload)
    }

    override fun onOfferCreated(description: SessionDescription) {
        CallManager.sendWebRtcSignal(receiverId, CallMessageProto.Type.OFFER, description.description)
    }

    override fun onAnswerCreated(description: SessionDescription) {
        CallManager.sendWebRtcSignal(receiverId, CallMessageProto.Type.ANSWER, description.description)
    }

    override fun onDestroy() {
        super.onDestroy()
        webRtcClient?.close()
    }
}
