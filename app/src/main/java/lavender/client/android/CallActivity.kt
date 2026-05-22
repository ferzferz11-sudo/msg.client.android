package lavender.client.android

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
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
import lavender.client.android.data.grpc.GrpcClient
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

    private lateinit var audioManager: AudioManager
    private var oldAudioMode: Int = AudioManager.MODE_NORMAL

    // Shared EGL context for all renderers
    private val eglBase = EglBase.create()
    private var isRemoteViewInitialized = false
    private var isLocalViewInitialized = false

    companion object {
        private const val TAG = "CallActivity"
        private const val PERMISSION_CODE = 101
        private val PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: isIncoming=$isIncoming")
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        oldAudioMode = audioManager.mode
        
        // Optimize for communication
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        setSpeakerphoneEnabled(true)

        callId = intent.getStringExtra("CALL_ID") ?: ""
        receiverId = intent.getStringExtra("RECEIVER_ID") ?: ""
        isIncoming = intent.getBooleanExtra("IS_INCOMING", false)

        Log.d(TAG, "Call details: ID=$callId, Other=$receiverId, Incoming=$isIncoming")

        CallManager.init(applicationContext)
        CallManager.syncCallState(callId, receiverId, isIncoming)
        GrpcClient.startCallSession()

        binding.tvCallerName.text = receiverId

        if (isIncoming) {
            binding.btnAccept.visibility = View.VISIBLE
            binding.btnMic.visibility = View.GONE
            binding.btnCamera.visibility = View.GONE
        }

        if (!hasPermissions()) {
            Log.d(TAG, "Permissions missing, requesting...")
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_CODE)
        } else {
            Log.d(TAG, "Permissions OK")
            if (!isIncoming) {
                Log.d(TAG, "Initializing WebRTC for outgoing call")
                initWebRtc()
            }
        }

        setupButtons()
        observeSignals()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newCallId = intent.getStringExtra("CALL_ID") ?: ""
        if (newCallId.isNotEmpty() && newCallId != callId) {
            Log.d(TAG, "onNewIntent: updating callId from $callId to $newCallId")
            callId = newCallId
            receiverId = intent.getStringExtra("RECEIVER_ID") ?: ""
            isIncoming = intent.getBooleanExtra("IS_INCOMING", false)
            CallManager.syncCallState(callId, receiverId, isIncoming)
        }
    }

    private fun setupButtons() {
        binding.btnHangup.setOnClickListener {
            Log.d(TAG, "Hangup button clicked")
            if (isIncoming && webRtcClient == null) {
                Log.d(TAG, "Rejecting incoming call")
                CallManager.rejectCall()
            } else {
                Log.d(TAG, "Hanging up active call")
                CallManager.hangup()
            }
            finish()
        }

        binding.btnAccept.setOnClickListener {
            Log.d(TAG, "Accept button clicked")
            binding.btnAccept.visibility = View.GONE
            binding.btnMic.visibility = View.VISIBLE
            binding.btnCamera.visibility = View.VISIBLE
            initWebRtc()
            CallManager.acceptCall()
        }

        binding.btnMic.setOnClickListener {
            isMicEnabled = !isMicEnabled
            Log.d(TAG, "Mic toggle: $isMicEnabled")
            webRtcClient?.toggleMic(isMicEnabled)
            binding.btnMic.setImageResource(if (isMicEnabled) R.drawable.ic_mic_on else R.drawable.ic_mic_off)
        }

        binding.btnCamera.setOnClickListener {
            isCameraEnabled = !isCameraEnabled
            Log.d(TAG, "Camera toggle: $isCameraEnabled")
            webRtcClient?.toggleCamera(isCameraEnabled)
            binding.btnCamera.setImageResource(if (isCameraEnabled) R.drawable.ic_videocam_on else R.drawable.ic_videocam_off)
        }
    }

    private fun observeSignals() {
        lifecycleScope.launch {
            CallManager.incomingSignals.collectLatest { signal ->
                val myUserId = GrpcClient.getUserId() ?: GrpcClient.getCurrentUsername()
                Log.d(TAG, "Incoming signal: ${signal.type} (CallID: ${signal.callId}) from ${signal.senderId}")
                
                // For outgoing calls, we pick up the callId from the first INITIATE echo from server
                if (callId.isEmpty() && signal.type == CallMessageProto.Type.INITIATE && !isIncoming) {
                    if (signal.senderId == myUserId) {
                        callId = signal.callId
                        Log.d(TAG, "CallID assigned from server echo: $callId")
                    }
                }

                if (signal.callId != callId && callId.isNotEmpty()) {
                    Log.w(TAG, "Ignored signal with mismatching CallID: expected $callId, got ${signal.callId}")
                    return@collectLatest
                }

                // Ignore self-echoed signals for most types to avoid "glare" and self-termination
                if (signal.senderId == myUserId && signal.type != CallMessageProto.Type.INITIATE) {
                    Log.d(TAG, "Ignored self-echoed signal: ${signal.type}")
                    return@collectLatest
                }
                
                when (signal.type) {
                    CallMessageProto.Type.ACCEPT -> {
                        if (!isIncoming) {
                            Log.d(TAG, "Peer accepted call, creating WebRTC Offer")
                            webRtcClient?.createOffer()
                        } else {
                            Log.d(TAG, "Received ACCEPT signal as receiver, ignoring to avoid glare")
                        }
                    }
                    CallMessageProto.Type.OFFER -> {
                        Log.d(TAG, "Received OFFER, creating WebRTC Answer")
                        val sdp = SessionDescription(SessionDescription.Type.OFFER, signal.payload)
                        webRtcClient?.setRemoteDescription(sdp)
                        webRtcClient?.createAnswer()
                    }
                    CallMessageProto.Type.ANSWER -> {
                        Log.d(TAG, "Received ANSWER, setting remote description")
                        val sdp = SessionDescription(SessionDescription.Type.ANSWER, signal.payload)
                        webRtcClient?.setRemoteDescription(sdp)
                    }
                    CallMessageProto.Type.ICE_CANDIDATE -> {
                        Log.d(TAG, "Received ICE_CANDIDATE")
                        try {
                            val json = JSONObject(signal.payload)
                            val candidate = IceCandidate(
                                json.getString("sdpMid"),
                                json.getInt("sdpMLineIndex"),
                                json.getString("candidate")
                            )
                            webRtcClient?.addIceCandidate(candidate)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse ICE candidate JSON", e)
                        }
                    }
                    CallMessageProto.Type.REJECT, CallMessageProto.Type.HANGUP -> {
                        Log.d(TAG, "Call terminated by peer (${signal.type})")
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_CODE && hasPermissions()) {
            Log.d(TAG, "Permissions granted via request")
            if (!isIncoming) initWebRtc()
        } else if (requestCode == PERMISSION_CODE) {
            Log.w(TAG, "Permissions denied")
            Toast.makeText(this, "Camera and Microphone permissions are required for calls", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun initWebRtc() {
        Log.d(TAG, "Initializing WebRTC components")
        webRtcClient = WebRtcClient(this, eglBase.eglBaseContext, this)
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
        webRtcClient?.initPeerConnection(iceServers)
        
        if (!isLocalViewInitialized) {
            binding.localView.init(eglBase.eglBaseContext, null)
            binding.localView.setEnableHardwareScaler(true)
            binding.localView.setMirror(true)
            isLocalViewInitialized = true
        }
        webRtcClient?.startLocalStream(binding.localView)
    }

    override fun onLocalStream(stream: MediaStream) {
        Log.d(TAG, "Local stream ready")
        runOnUiThread {
            stream.videoTracks.getOrNull(0)?.addSink(binding.localView)
        }
    }

    override fun onRemoteStream(stream: MediaStream) {
        Log.d(TAG, "Remote stream received!")
        runOnUiThread {
            if (!isRemoteViewInitialized) {
                binding.remoteView.init(eglBase.eglBaseContext, null)
                isRemoteViewInitialized = true
            }
            stream.videoTracks.getOrNull(0)?.addSink(binding.remoteView)
        }
    }

    override fun onRemoteTrack(track: MediaStreamTrack) {
        Log.d(TAG, "onRemoteTrack: ${track.kind()}")
        if (track is VideoTrack) {
            runOnUiThread {
                if (!isRemoteViewInitialized) {
                    binding.remoteView.init(eglBase.eglBaseContext, null)
                    binding.remoteView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                    binding.remoteView.setEnableHardwareScaler(true)
                    isRemoteViewInitialized = true
                }
                track.addSink(binding.remoteView)
            }
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
        Log.d(TAG, "WebRTC Offer created")
        CallManager.sendWebRtcSignal(receiverId, CallMessageProto.Type.OFFER, description.description)
    }

    override fun onAnswerCreated(description: SessionDescription) {
        Log.d(TAG, "WebRTC Answer created")
        CallManager.sendWebRtcSignal(receiverId, CallMessageProto.Type.ANSWER, description.description)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: cleaning up")
        
        // Restore audio settings
        audioManager.mode = oldAudioMode
        setSpeakerphoneEnabled(false)
        
        CallManager.clearCurrentCall()
        
        // Properly release views and tracks
        binding.localView.release()
        binding.remoteView.release()

        webRtcClient?.close()
        eglBase.release()
    }

    private fun setSpeakerphoneEnabled(enabled: Boolean) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (enabled) {
                val devices = audioManager.availableCommunicationDevices
                val speakerDevice = devices.find { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                speakerDevice?.let { audioManager.setCommunicationDevice(it) }
            } else {
                audioManager.clearCommunicationDevice()
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = enabled
        }
    }
}
