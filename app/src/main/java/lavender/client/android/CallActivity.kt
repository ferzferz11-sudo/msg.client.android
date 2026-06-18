package lavender.client.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import lavender.client.android.databinding.ActivityCallBinding
import lavender.client.android.data.calls.*
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.proto.CallMessageProto
import lavender.client.android.ui.calls.CallViewModel
import org.webrtc.*
import org.json.JSONObject
import lavender.client.android.data.grpc.*
import lavender.client.android.data.grpc.GrpcClientExtensions.*

class CallActivity : AppCompatActivity(), WebRtcClient.Observer {
    private lateinit var binding: ActivityCallBinding
    private val viewModel: CallViewModel by viewModels()
    private var webRtcClient: WebRtcClient? = null
    private var callController: CallController? = null
    private lateinit var audioModeManager: AudioModeManager
    private lateinit var soundManager: CallSoundManager
    
    private var callId: String = ""
    private var receiverId: String = ""
    private var isIncoming: Boolean = false
    private var isConference: Boolean = false
    private var roomId: String = ""
    
    private var isMicEnabled = true
    private var isCameraEnabled = false

    private val eglBase = EglBase.create()
    private var isRemoteViewInitialized = false
    private var isLocalViewInitialized = false

    // WebRTC connection timeout
    private var connectionTimeoutRunnable: Runnable? = null
    private val connectionTimeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val CONNECTION_TIMEOUT_MS = 30_000L // 30 seconds

    companion object {
        private const val TAG = "CallActivity"
        private const val PERMISSION_CODE = 101
        private val PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        audioModeManager = AudioModeManager(this)
        audioModeManager.setCallMode()
        
        soundManager = CallSoundManager(this)

        callId = intent.getStringExtra("CALL_ID") ?: ""
        receiverId = intent.getStringExtra("RECEIVER_ID") ?: ""
        isIncoming = intent.getBooleanExtra("IS_INCOMING", false)
        isConference = intent.getBooleanExtra("IS_CONFERENCE", false)
        roomId = intent.getStringExtra("ROOM_ID") ?: ""

        CallManager.init(applicationContext)
        if (isConference) {
             isCameraEnabled = true
             CallManager.joinConference(roomId)
             binding.tvCallStatus.text = getString(R.string.waiting_for_participants)
             binding.btnAccept.visibility = View.GONE
             binding.btnMic.visibility = View.VISIBLE
             binding.btnCamera.visibility = View.VISIBLE
             binding.btnCamera.setImageResource(R.drawable.ic_videocam_on)
             viewModel.startTimer()
        } else {
             CallManager.syncCallState(callId, receiverId, isIncoming)
             binding.tvCallStatus.text = if (isIncoming) getString(R.string.call_status_incoming) else getString(R.string.call_status_calling)
             
             if (isIncoming) {
                 soundManager.startRingtone()
             } else {
                 soundManager.startDialTone()
             }
        }
        GrpcClient.startCallSession()

        binding.tvCallerName.text = if (isConference) getString(R.string.group_conference) else receiverId
        if (!isConference) loadOtherParticipantAvatar()

        if (isIncoming && !isConference) {
            binding.btnAccept.visibility = View.VISIBLE
            binding.btnMic.visibility = View.GONE
            binding.btnCamera.visibility = View.GONE
        }

        if (hasPermissions()) {
            if (!isIncoming || isConference) initWebRtc()
        } else {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_CODE)
        }

        setupButtons()
        setupController()
        
        lifecycleScope.launch {
            viewModel.timerText.collect { binding.tvCallDuration.text = it }
        }

        // Start connection timeout for outgoing calls
        if (!isIncoming && !isConference) {
            startConnectionTimeout()
        }
    }

    private fun startConnectionTimeout() {
        connectionTimeoutRunnable = Runnable {
            Log.w(TAG, "Connection timeout reached!")
            if (!isFinishing) {
                soundManager.stop()
                Toast.makeText(this@CallActivity, getString(R.string.call_connection_failed), Toast.LENGTH_SHORT).show()
                CallManager.hangup()
                CallManager.clearCurrentCall()
                finish()
            }
        }
        connectionTimeoutHandler.postDelayed(connectionTimeoutRunnable!!, CONNECTION_TIMEOUT_MS)
    }

    private fun cancelConnectionTimeout() {
        connectionTimeoutRunnable?.let { connectionTimeoutHandler.removeCallbacks(it) }
        connectionTimeoutRunnable = null
    }

    private fun setupController() {
        callController = CallController(callId, receiverId, isIncoming, isConference, roomId, webRtcClient, object : CallController.Listener {
            override fun onCallAccepted() {
                soundManager.stop()
                cancelConnectionTimeout() // Cancel timeout on successful connection
                viewModel.startTimer()
                runOnUiThread { binding.tvCallStatus.text = getString(R.string.call_status_connected) }
            }

            override fun onCallTerminated(reason: String) {
                runOnUiThread {
                    cancelConnectionTimeout()
                    Toast.makeText(this@CallActivity, reason, Toast.LENGTH_SHORT).show()
                    finish()
                }
            }

            override fun onConferencePresenceUpdated(participants: List<String>, creatorId: String) {
                val myId = GrpcClient.getUserId() ?: GrpcClient.getCurrentUsername()
                runOnUiThread {
                    binding.tvCallStatus.text = getString(R.string.in_conference_format, participants.joinToString(", "))
                    binding.btnEndForAll.visibility = if (myId == creatorId) View.VISIBLE else View.GONE
                }
            }

            override fun onStatusUpdate(status: String) {
                runOnUiThread { binding.tvCallStatus.text = status }
            }

            override fun onIdAssigned(newCallId: String) {
                if (callId.isEmpty() && newCallId.isNotEmpty()) {
                    Log.d(TAG, "Call ID assigned by server: $newCallId")
                    callId = newCallId
                }
            }
        }, this)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newCallId = intent.getStringExtra("CALL_ID") ?: ""
        val newRoomId = intent.getStringExtra("ROOM_ID") ?: ""
        
        if ((newCallId.isNotEmpty() && newCallId != callId) || (newRoomId.isNotEmpty() && newRoomId != roomId)) {
            callId = newCallId
            roomId = newRoomId
            receiverId = intent.getStringExtra("RECEIVER_ID") ?: ""
            isIncoming = intent.getBooleanExtra("IS_INCOMING", false)
            isConference = intent.getBooleanExtra("IS_CONFERENCE", false)
            
            if (isConference) CallManager.joinConference(roomId)
            else CallManager.syncCallState(callId, receiverId, isIncoming)
            
            setupController()
        }
    }

    private fun setupButtons() {
        binding.btnHangup.setOnClickListener {
            viewModel.stopTimer()
            if (isConference) CallManager.leaveConference()
            else if (isIncoming && webRtcClient == null) CallManager.rejectCall()
            else CallManager.hangup()
            finish()
        }

        binding.btnAccept.setOnClickListener {
            soundManager.stop()
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
            updateVideoVisibility()
        }

        binding.btnEndForAll.setOnClickListener {
            CallManager.endConference()
            finish()
        }
    }

    private fun fetchTurnCredentials(callback: (List<PeerConnection.IceServer>) -> Unit) {
        Thread {
            try {
                val url = java.net.URL("http://13.140.25.249:8082/turn-credentials")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                val response = connection.inputStream.bufferedReader().readText()
                val json = org.json.JSONObject(response)
                val iceServers = json.getJSONArray("iceServers")

                val servers = mutableListOf<PeerConnection.IceServer>()
                for (i in 0 until iceServers.length()) {
                    val server = iceServers.getJSONObject(i)
                    val urls = server.getJSONArray("urls")
                    val username = server.getString("username")
                    val credential = server.getString("credential")

                    for (j in 0 until urls.length()) {
                        val urlStr = urls.getString(j)
                        servers.add(
                            PeerConnection.IceServer.builder(urlStr)
                                .setUsername(username)
                                .setPassword(credential)
                                .createIceServer()
                        )
                    }
                }
                callback(servers)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch TURN credentials, using STUN only", e)
                callback(listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()))
            }
        }.start()
    }

    private fun initWebRtc() {
        webRtcClient = WebRtcClient(this, eglBase.eglBaseContext, this)

        // Get TURN credentials from server
        fetchTurnCredentials { iceServers ->
            runOnUiThread {
                webRtcClient?.initPeerConnection(iceServers)
                setupWebRtcListeners()
            }
        }
    }

    private fun setupWebRtcListeners() {
        // Monitor ICE connection state
        webRtcClient?.onIceConnectionStateChange = { state ->
            Log.d(TAG, "ICE connection state: $state")
            when (state) {
                PeerConnection.IceConnectionState.CONNECTED, PeerConnection.IceConnectionState.COMPLETED -> {
                    Log.d(TAG, "WebRTC connection established!")
                    runOnUiThread {
                        cancelConnectionTimeout()
                        binding.tvCallStatus.text = getString(R.string.call_status_connected)
                    }
                }
                PeerConnection.IceConnectionState.FAILED -> {
                    Log.e(TAG, "WebRTC connection FAILED!")
                    runOnUiThread {
                        Toast.makeText(this@CallActivity, getString(R.string.call_connection_error), Toast.LENGTH_SHORT).show()
                        CallManager.hangup()
                        finish()
                    }
                }
                PeerConnection.IceConnectionState.DISCONNECTED -> {
                    Log.w(TAG, "WebRTC connection disconnected")
                    // Don't immediately fail — may recover
                }
                else -> {}
            }
        }

        if (!isLocalViewInitialized) {
            binding.localView.init(eglBase.eglBaseContext, null)
            binding.localView.setEnableHardwareScaler(true)
            binding.localView.setMirror(true)
            binding.localView.setZOrderMediaOverlay(true)
            isLocalViewInitialized = true
        }
        webRtcClient?.startLocalStream(binding.localView)
        webRtcClient?.toggleCamera(isCameraEnabled)
        updateVideoVisibility()
        setupController()
    }

    override fun onLocalStream(stream: MediaStream) {
        runOnUiThread {
            stream.videoTracks.getOrNull(0)?.addSink(binding.localView)
            if (isConference && !isRemoteViewInitialized) {
                if (!binding.remoteView.isVisible) {
                     binding.remoteView.isVisible = true
                     binding.remoteView.init(eglBase.eglBaseContext, null)
                     isRemoteViewInitialized = true
                }
                stream.videoTracks.getOrNull(0)?.addSink(binding.remoteView)
            }
        }
    }

    override fun onRemoteStream(stream: MediaStream) {
        runOnUiThread {
            if (!isRemoteViewInitialized) {
                binding.remoteView.init(eglBase.eglBaseContext, null)
                isRemoteViewInitialized = true
            }
            if (isConference) binding.remoteView.isVisible = true
            stream.videoTracks.getOrNull(0)?.addSink(binding.remoteView)
        }
    }

    override fun onRemoteTrack(track: MediaStreamTrack) {
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
        } else if (track is AudioTrack) {
            track.setEnabled(true)
        }
    }

    override fun onIceCandidate(candidate: IceCandidate) {
        val payload = JSONObject().apply {
            put("sdpMid", candidate.sdpMid); put("sdpMLineIndex", candidate.sdpMLineIndex); put("candidate", candidate.sdp)
        }.toString()
        CallManager.sendWebRtcSignal(receiverId, CallMessageProto.Type.ICE_CANDIDATE, payload)
    }

    override fun onOfferCreated(description: SessionDescription) {
        CallManager.sendWebRtcSignal(receiverId, CallMessageProto.Type.OFFER, description.description)
        runOnUiThread { binding.tvCallStatus.text = getString(R.string.call_status_connecting) }
    }

    override fun onAnswerCreated(description: SessionDescription) {
        CallManager.sendWebRtcSignal(receiverId, CallMessageProto.Type.ANSWER, description.description)
        runOnUiThread { 
            binding.tvCallStatus.text = getString(R.string.call_status_connected) 
            viewModel.startTimer()
        }
    }

    override fun onRemoteDescriptionSet() {
        if (!isConference) runOnUiThread { binding.tvCallStatus.text = getString(R.string.call_status_connecting) }
        if (isIncoming && webRtcClient != null) webRtcClient?.createAnswer()
    }

    private fun loadOtherParticipantAvatar() {
        if (receiverId.isEmpty()) return
        GrpcClient.getUserAvatar(receiverId) { avatarUrl ->
            if (avatarUrl.isNotEmpty()) runOnUiThread {
                Glide.with(this).load(avatarUrl).placeholder(R.drawable.ic_default_avatar).into(binding.imgAvatar)
                Glide.with(this).load(avatarUrl).centerCrop().into(binding.imgBgBlur)
            }
        }
    }

    private fun updateVideoVisibility() {
        runOnUiThread {
            binding.localView.isVisible = isCameraEnabled
            binding.remoteView.isVisible = isCameraEnabled
            binding.imgAvatar.isVisible = !isCameraEnabled
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelConnectionTimeout()
        soundManager.stop()
        audioModeManager.restoreMode()
        CallManager.clearCurrentCall()
        binding.localView.release()
        binding.remoteView.release()
        webRtcClient?.close()
        eglBase.release()
    }

    private fun hasPermissions() = PERMISSIONS.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_CODE && hasPermissions()) {
            if (!isIncoming || isConference) initWebRtc()
        } else if (requestCode == PERMISSION_CODE) {
            Toast.makeText(this, getString(R.string.camera_mic_permissions_required), Toast.LENGTH_LONG).show()
            finish()
        }
    }
}
