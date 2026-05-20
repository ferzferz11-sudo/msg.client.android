package lavender.client.android.data.calls

import android.content.Context
import android.util.Log
import org.webrtc.*
import java.util.*

class WebRtcClient(
    private val context: Context,
    private val observer: Observer
) {
    interface Observer {
        fun onLocalStream(stream: MediaStream)
        fun onRemoteStream(stream: MediaStream)
        fun onIceCandidate(candidate: IceCandidate)
        fun onOfferCreated(description: SessionDescription)
        fun onAnswerCreated(description: SessionDescription)
    }

    private val rootEglBase: EglBase = EglBase.create()
    private var peerConnectionFactory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private var localAudioSource: AudioSource
    private var localVideoSource: VideoSource
    private var videoCapturer: VideoCapturer? = null
    
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null

    init {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val factoryOptions = PeerConnectionFactory.Options()
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(factoryOptions)
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(rootEglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(rootEglBase.eglBaseContext))
            .createPeerConnectionFactory()

        localAudioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
        localVideoSource = peerConnectionFactory.createVideoSource(false)
    }

    fun initPeerConnection(iceServers: List<PeerConnection.IceServer>) {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(candidate: IceCandidate) {
                observer.onIceCandidate(candidate)
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream) {
                observer.onRemoteStream(stream)
            }
            override fun onRemoveStream(stream: MediaStream) {}
            override fun onDataChannel(dataChannel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
        })
    }

    fun startLocalStream(localView: SurfaceViewRenderer) {
        localView.init(rootEglBase.eglBaseContext, null)
        localView.setEnableHardwareScaler(true)
        localView.setMirror(true)

        videoCapturer = createVideoCapturer()
        videoCapturer?.initialize(SurfaceTextureHelper.create("CaptureThread", rootEglBase.eglBaseContext), context, localVideoSource.capturerObserver)
        videoCapturer?.startCapture(1280, 720, 30)

        localVideoTrack = peerConnectionFactory.createVideoTrack("ARDAMSv0", localVideoSource)
        localAudioTrack = peerConnectionFactory.createAudioTrack("ARDAMSa0", localAudioSource)

        val localStream = peerConnectionFactory.createLocalMediaStream("ARDAMS")
        localStream.addTrack(localVideoTrack)
        localStream.addTrack(localAudioTrack)
        
        peerConnection?.addStream(localStream)
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
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        }, description)
    }

    fun addIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
    }

    fun toggleMic(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
    }

    fun toggleCamera(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
    }

    fun close() {
        videoCapturer?.stopCapture()
        videoCapturer?.dispose()
        peerConnection?.close()
        peerConnectionFactory.dispose()
        rootEglBase.release()
    }
}
