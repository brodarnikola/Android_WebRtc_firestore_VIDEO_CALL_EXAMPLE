package com.developerspace.webrtcsample

import android.app.Application
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.webrtc.*
import java.nio.ByteBuffer
import java.nio.charset.Charset


class RTCClient(
        context: Application,
        observer: PeerConnection.Observer
) {

    companion object {
        private const val LOCAL_TRACK_ID = "local_track"
        private const val LOCAL_STREAM_ID = "local_track"
        var localDataChannel: DataChannel? = null

    }

    private val rootEglBase: EglBase = EglBase.create()

    private var localAudioTrack : AudioTrack? = null
    private var localVideoTrack : VideoTrack? = null
    val TAG = "RTCClient"

    var remoteSessionDescription : SessionDescription? = null

    val db = Firebase.firestore

    init {
        initPeerConnectionFactory(context)
    }

    private val iceServer = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302")
                    .createIceServer()
    )

    private val peerConnectionFactory by lazy { buildPeerConnectionFactory() }
    private val videoCapturer by lazy { getVideoCapturer(context) }

    private val audioSource by lazy { peerConnectionFactory.createAudioSource(MediaConstraints())}
    private val localVideoSource by lazy { peerConnectionFactory.createVideoSource(false) }
    val peerConnection by lazy { buildPeerConnection(observer) }

    private fun initPeerConnectionFactory(context: Application) {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
                .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
    }

    private fun buildPeerConnectionFactory(): PeerConnectionFactory {
        return PeerConnectionFactory
                .builder()
                .setVideoDecoderFactory(DefaultVideoDecoderFactory(rootEglBase.eglBaseContext))
                .setVideoEncoderFactory(DefaultVideoEncoderFactory(rootEglBase.eglBaseContext, true, true))
                .setOptions(PeerConnectionFactory.Options().apply {
                    disableEncryption = false // true
                    disableNetworkMonitor = true
                })
                .createPeerConnectionFactory()
    }

    private fun stringToByteBuffer(msg: String, charset: Charset): ByteBuffer {
        return ByteBuffer.wrap(msg.toByteArray(charset))
    }

    private fun buildPeerConnection(observer: PeerConnection.Observer) : PeerConnection? {

        val localPeerConnection = peerConnectionFactory.createPeerConnection(
            iceServer,
            observer
        )


        Log.d(TAG, "Awesome.. 5 localPeerConnection is ${localPeerConnection}")

        val dcInit = DataChannel.Init()
        localDataChannel =
            localPeerConnection!!.createDataChannel("sendDataChannel", dcInit)
        /*if( localDataChannel != null ) {
            localDataChannel!!.registerObserver(object : DataChannel.Observer {

                override fun onBufferedAmountChange(p0: Long) {

                }


                override fun onStateChange() {
                    Log.d(TAG,
                        "11 onStateChange: remote data channel state: " + localDataChannel!!.state()
                            .toString()
                    )
                    if( localDataChannel!!.state() == DataChannel.State.OPEN ) {
                        CoroutineScope(Dispatchers.IO).launch {
                            delay(2000)
                            val meta: ByteBuffer =
                                stringToByteBuffer("Awesome 99", Charset.defaultCharset())
                            localDataChannel!!.send(DataChannel.Buffer(meta, false))
                        }
                    }
                }

                override fun onMessage(buffer: DataChannel.Buffer) {
                    Log.d(TAG, "onMessage: got message ${buffer.data}")
                    val bytes: ByteArray
                    if (buffer.data.hasArray()) {
                        bytes = buffer.data.array()
                    } else {
                        bytes = ByteArray(buffer.data.remaining())
                        buffer.data[bytes]
                    }
                    val firstMessage = String(bytes, Charset.defaultCharset())
                    Log.d(TAG, "New text is: $firstMessage")
                    // Toast.makeText(, "New text is: $firstMessage", Toast.LENGTH_LONG).show()
                }
            })
        }*/

        Log.d(TAG, "Awesome.. 7 localDataChannel is ${localDataChannel}")

        return localPeerConnection
    }
    private fun getVideoCapturer(context: Context) =
            Camera2Enumerator(context).run {
                deviceNames.find {
                    isFrontFacing(it)
                }?.let {
                    createCapturer(it, null)
                } ?: throw IllegalStateException()
            }

    fun initSurfaceView(view: SurfaceViewRenderer) = view.run {
        setMirror(true)
        setEnableHardwareScaler(true)
        init(rootEglBase.eglBaseContext, null)
    }

    fun startLocalVideoCapture(localVideoOutput: SurfaceViewRenderer) {
        val surfaceTextureHelper = SurfaceTextureHelper.create(Thread.currentThread().name, rootEglBase.eglBaseContext)
        (videoCapturer as VideoCapturer).initialize(surfaceTextureHelper, localVideoOutput.context, localVideoSource.capturerObserver)
        videoCapturer.startCapture(320, 240, 60)
        localAudioTrack = peerConnectionFactory.createAudioTrack(LOCAL_TRACK_ID + "_audio", audioSource);
        localVideoTrack = peerConnectionFactory.createVideoTrack(LOCAL_TRACK_ID, localVideoSource)
        localVideoTrack?.addSink(localVideoOutput)
        val localStream = peerConnectionFactory.createLocalMediaStream(LOCAL_STREAM_ID)
        localStream.addTrack(localVideoTrack)
        localStream.addTrack(localAudioTrack)
        peerConnection?.addStream(localStream)
    }

    private fun PeerConnection.call(sdpObserver: SdpObserver, meetingID: String) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }

        createOffer(object : SdpObserver by sdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                setLocalDescription(object : SdpObserver {
                    override fun onSetFailure(p0: String?) {
                        Log.e(TAG, "onSetFailure: $p0")
                    }

                    override fun onSetSuccess() {
                        val offer = hashMapOf(
                                "sdp" to desc?.description,
                                "type" to desc?.type
                        )
                        db.collection("calls").document(meetingID)
                                .set(offer)
                                .addOnSuccessListener {
                                    Log.e(TAG, "DocumentSnapshot added")
                                }
                                .addOnFailureListener { e ->
                                    Log.e(TAG, "Error adding document", e)
                                }
                        Log.e(TAG, "onSetSuccess")
                    }

                    override fun onCreateSuccess(p0: SessionDescription?) {
                        Log.e(TAG, "onCreateSuccess: Description $p0")
                    }

                    override fun onCreateFailure(p0: String?) {
                        Log.e(TAG, "onCreateFailure: $p0")
                    }
                }, desc)
                sdpObserver.onCreateSuccess(desc)
            }

            override fun onSetFailure(p0: String?) {
                Log.e(TAG, "onSetFailure: $p0")
            }

            override fun onCreateFailure(p0: String?) {
                Log.e(TAG, "onCreateFailure: $p0")
            }
        }, constraints)
    }

    private fun PeerConnection.answer(sdpObserver: SdpObserver, meetingID: String) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        createAnswer(object : SdpObserver by sdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                val answer = hashMapOf(
                        "sdp" to desc?.description,
                        "type" to desc?.type
                )
                db.collection("calls").document(meetingID)
                        .set(answer)
                        .addOnSuccessListener {
                            Log.e(TAG, "DocumentSnapshot added")
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Error adding document", e)
                        }
                setLocalDescription(object : SdpObserver {
                    override fun onSetFailure(p0: String?) {
                        Log.e(TAG, "onSetFailure: $p0")
                    }

                    override fun onSetSuccess() {
                        Log.e(TAG, "onSetSuccess")
                    }

                    override fun onCreateSuccess(p0: SessionDescription?) {
                        Log.e(TAG, "onCreateSuccess: Description $p0")
                    }

                    override fun onCreateFailure(p0: String?) {
                        Log.e(TAG, "onCreateFailureLocal: $p0")
                    }
                }, desc)
                sdpObserver.onCreateSuccess(desc)
            }

            override fun onCreateFailure(p0: String?) {
                Log.e(TAG, "onCreateFailureRemote: $p0")
            }
        }, constraints)
    }

    fun call(sdpObserver: SdpObserver, meetingID: String) = peerConnection?.call(sdpObserver, meetingID)

    fun answer(sdpObserver: SdpObserver, meetingID: String) = peerConnection?.answer(sdpObserver, meetingID)

    fun onRemoteSessionReceived(sessionDescription: SessionDescription) {
        remoteSessionDescription = sessionDescription
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetFailure(p0: String?) {
                Log.e(TAG, "onSetFailure: $p0")
            }

            override fun onSetSuccess() {
                Log.e(TAG, "onSetSuccessRemoteSession")
            }

            override fun onCreateSuccess(p0: SessionDescription?) {
                Log.e(TAG, "onCreateSuccessRemoteSession: Description $p0")
            }

            override fun onCreateFailure(p0: String?) {
                Log.e(TAG, "onCreateFailure")
            }
        }, sessionDescription)

    }

    fun addIceCandidate(iceCandidate: IceCandidate?) {
        peerConnection?.addIceCandidate(iceCandidate)
    }

    fun endCall(meetingID: String) {
        db.collection("calls").document(meetingID).collection("candidates")
                .get().addOnSuccessListener {
                    val iceCandidateArray: MutableList<IceCandidate> = mutableListOf()
                    for ( dataSnapshot in it) {
                        if (dataSnapshot.contains("type") && dataSnapshot["type"]=="offerCandidate") {
                            val offerCandidate = dataSnapshot
                            iceCandidateArray.add(IceCandidate(offerCandidate["sdpMid"].toString(), Math.toIntExact(offerCandidate["sdpMLineIndex"] as Long), offerCandidate["sdp"].toString()))
                        } else if (dataSnapshot.contains("type") && dataSnapshot["type"]=="answerCandidate") {
                            val answerCandidate = dataSnapshot
                            iceCandidateArray.add(IceCandidate(answerCandidate["sdpMid"].toString(), Math.toIntExact(answerCandidate["sdpMLineIndex"] as Long), answerCandidate["sdp"].toString()))
                        }
                    }
                    peerConnection?.removeIceCandidates(iceCandidateArray.toTypedArray())
                }
        val endCall = hashMapOf(
                "type" to "END_CALL"
        )
        db.collection("calls").document(meetingID)
                .set(endCall)
                .addOnSuccessListener {
                    Log.e(TAG, "DocumentSnapshot added")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error adding document", e)
                }

        peerConnection?.close()
    }

    fun enableVideo(videoEnabled: Boolean) {
        if (localVideoTrack !=null)
            localVideoTrack?.setEnabled(videoEnabled)
    }

    fun enableAudio(audioEnabled: Boolean) {
        if (localAudioTrack != null)
            localAudioTrack?.setEnabled(audioEnabled)
    }
    fun switchCamera() {
        videoCapturer.switchCamera(null)
    }
}