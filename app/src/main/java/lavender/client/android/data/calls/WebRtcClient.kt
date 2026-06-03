package lavender.client.android.data.calls

import android.content.Context
import android.util.Log
import org.webrtc.*
import java.util.*

class WebRtcClient(
    private val context: Context,
    private val eglContext: EglBase.Context,
    private val observer: Observer
) {
    interface Observer {
        fun onLocalStream(stream: MediaStream)
        fun onRemoteStream(stream: MediaStream)
        fun onRemoteTrack(track: MediaStreamTrack)
        fun onIceCandidate(candidate: IceCandidate)
        fun onOfferCreated(description: SessionDescription)
        fun onAnswerCreated(description: SessionDescription)
        fun onRemoteDescriptionSet()
    }

    private var peerConnectionFactory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private var localAudioSource: AudioSource
    private var localVideoSource: VideoSource
    private var videoCapturer: VideoCapturer? = null
    
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null

    private val iceCandidateQueue = mutableListOf<IceCandidate>()
    private var isRemoteDescriptionSet = false

    // ICE connection state callback
    var onIceConnectionStateChange: ((PeerConnection.IceConnectionState) -> Unit)? = null

    init {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val factoryOptions = PeerConnectionFactory.Options()
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(factoryOptions)
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglContext))
            .createPeerConnectionFactory()

        localAudioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
        localVideoSource = peerConnectionFactory.createVideoSource(false)
    }

    fun initPeerConnection(iceServers: List<PeerConnection.IceServer>) {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                Log.d("WebRtcClient", "Signaling state: $state")
            }
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d("WebRtcClient", "Ice connection: $state")
                state?.let { onIceConnectionStateChange?.invoke(it) }
            }
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(candidate: IceCandidate) {
                observer.onIceCandidate(candidate)
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream) {
                Log.d("WebRtcClient", "Remote stream added")
                observer.onRemoteStream(stream)
            }
            override fun onRemoveStream(stream: MediaStream) {}
            override fun onDataChannel(dataChannel: DataChannel?) {}
            override fun onRenegotiationNeeded() {
                Log.d("WebRtcClient", "Renegotiation needed")
            }
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                Log.d("WebRtcClient", "onAddTrack: ${receiver?.track()?.kind()}")
                receiver?.track()?.let { track ->
                    observer.onRemoteTrack(track)
                }
                streams?.getOrNull(0)?.let {
                    observer.onRemoteStream(it)
                }
            }
        })
    }

    fun startLocalStream(localView: SurfaceViewRenderer) {
        if (!localView.isActivated) { // Check if initialized indirectly
             // localView initialization handled in CallActivity
        }

        videoCapturer = createVideoCapturer()
        videoCapturer?.initialize(SurfaceTextureHelper.create("CaptureThread", eglContext), context, localVideoSource.capturerObserver)
        videoCapturer?.startCapture(1280, 720, 30)

        localVideoTrack = peerConnectionFactory.createVideoTrack("ARDAMSv0", localVideoSource)
        localAudioTrack = peerConnectionFactory.createAudioTrack("ARDAMSa0", localAudioSource)

        val localStream = peerConnectionFactory.createLocalMediaStream("ARDAMS")
        localStream.addTrack(localVideoTrack)
        localStream.addTrack(localAudioTrack)
        
        val streamIds = listOf("ARDAMS")
        localVideoTrack?.let { peerConnection?.addTrack(it, streamIds) }
        localAudioTrack?.let { peerConnection?.addTrack(it, streamIds) }

        observer.onLocalStream(localStream)
    }

    private fun createVideoCapturer(): VideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames

        // Try to find front facing camera
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }

        // Fallback to first available camera
        for (deviceName in deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }

        return null
    }

    fun createOffer() {
        val constraints = MediaConstraints()
        constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(description: SessionDescription) {
                peerConnection?.setLocalDescription(this, description)
                observer.onOfferCreated(description)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        }, constraints)
    }

    fun createAnswer() {
        val constraints = MediaConstraints()
        constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(description: SessionDescription) {
                peerConnection?.setLocalDescription(this, description)
                observer.onAnswerCreated(description)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        }, constraints)
    }

    fun setRemoteDescription(description: SessionDescription) {
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d("WebRtcClient", "Remote description set success")
                isRemoteDescriptionSet = true
                drainIceCandidateQueue()
                observer.onRemoteDescriptionSet()
            }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {
                Log.e("WebRtcClient", "Remote description set failure: $p0")
            }
        }, description)
    }

    fun addIceCandidate(candidate: IceCandidate) {
        if (isRemoteDescriptionSet) {
            peerConnection?.addIceCandidate(candidate)
        } else {
            Log.d("WebRtcClient", "Queuing ICE candidate")
            iceCandidateQueue.add(candidate)
        }
    }

    private fun drainIceCandidateQueue() {
        Log.d("WebRtcClient", "Draining ICE candidate queue: ${iceCandidateQueue.size}")
        for (candidate in iceCandidateQueue) {
            peerConnection?.addIceCandidate(candidate)
        }
        iceCandidateQueue.clear()
    }

    fun toggleMic(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
    }

    fun toggleCamera(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
    }

    fun close() {
        try {
            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
            videoCapturer = null
            
            localVideoTrack?.setEnabled(false)
            localVideoTrack?.dispose()
            localVideoTrack = null
            
            localAudioTrack?.setEnabled(false)
            localAudioTrack?.dispose()
            localAudioTrack = null
            
            localVideoSource.dispose()
            localAudioSource.dispose()
            
            peerConnection?.close()
            peerConnection?.dispose()
            peerConnection = null
            
            peerConnectionFactory.dispose()
        } catch (e: Exception) {
            Log.e("WebRtcClient", "Error during close", e)
        }
    }
}
