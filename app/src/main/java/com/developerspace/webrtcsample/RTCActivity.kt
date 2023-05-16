package com.developerspace.webrtcsample

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import com.developerspace.webrtcsample.RTCClient.Companion.localDataChannel
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_start.*
import kotlinx.coroutines.*
import org.webrtc.*
import java.lang.Exception
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.*


@ExperimentalCoroutinesApi
class RTCActivity : AppCompatActivity() {

    companion object {
        private const val CAMERA_AUDIO_PERMISSION_REQUEST_CODE = 1
        private const val CAMERA_PERMISSION = Manifest.permission.CAMERA
        private const val AUDIO_PERMISSION = Manifest.permission.RECORD_AUDIO
    }

    private lateinit var rtcClient: RTCClient
    private lateinit var signallingClient: SignalingClient

    private val audioManager by lazy { RTCAudioManager.create(this) }

    val TAG = "MainActivity"

    private var meetingID : String = "test-call"

    private var isJoin = false

    private var isMute = false

    private var isVideoPaused = false

    private var inSpeakerMode = true

    private val sdpObserver = object : AppSdpObserver() {
        override fun onCreateSuccess(p0: SessionDescription?) {
            super.onCreateSuccess(p0)
//            signallingClient.send(p0)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (intent.hasExtra("meetingID"))
            meetingID = intent.getStringExtra("meetingID")!!
        if (intent.hasExtra("isJoin"))
            isJoin = intent.getBooleanExtra("isJoin",false)

        checkCameraAndAudioPermission()
        audioManager.selectAudioDevice(RTCAudioManager.AudioDevice.SPEAKER_PHONE)
        switch_camera_button.setOnClickListener {
            rtcClient.switchCamera()
        }

        audio_output_button.setOnClickListener {
            try {
                val meta: ByteBuffer =
                    stringToByteBuffer("Awesome 10", Charset.defaultCharset())
                Log.d(TAG, "Awesome.. send meta: ${meta}")
                localDataChannel?.send(DataChannel.Buffer( meta,false))

                Log.d(TAG, "Awesome.. DC : ${localDataChannel}")
                Log.d(TAG, "Awesome.. DC bufferedAmount: ${localDataChannel?.bufferedAmount()}")
                Log.d(TAG, "Awesome.. DC id: ${localDataChannel?.id()}")
            }
            catch (e: Exception) {

                Log.d(TAG, "Awesome.. 1111 exception is: $e")
            }

//            if (inSpeakerMode) {
//                inSpeakerMode = false
//                audio_output_button.setImageResource(R.drawable.ic_baseline_hearing_24)
//                audioManager.setDefaultAudioDevice(RTCAudioManager.AudioDevice.EARPIECE)
//            } else {
//                inSpeakerMode = true
//                audio_output_button.setImageResource(R.drawable.ic_baseline_speaker_up_24)
//                audioManager.setDefaultAudioDevice(RTCAudioManager.AudioDevice.SPEAKER_PHONE)
//            }
        }
        video_button.setOnClickListener {
            if (isVideoPaused) {
                isVideoPaused = false
                video_button.setImageResource(R.drawable.ic_baseline_videocam_off_24)
            } else {
                isVideoPaused = true
                video_button.setImageResource(R.drawable.ic_baseline_videocam_24)
            }
            rtcClient.enableVideo(isVideoPaused)
        }
        mic_button.setOnClickListener {
            if (isMute) {
                isMute = false
                mic_button.setImageResource(R.drawable.ic_baseline_mic_off_24)
            } else {
                isMute = true
                mic_button.setImageResource(R.drawable.ic_baseline_mic_24)
            }
            rtcClient.enableAudio(isMute)
        }
        end_call_button.setOnClickListener {
            rtcClient.endCall(meetingID)
            remote_view.isGone = false
            Constants.isCallEnded = true
            finish()
            startActivity(Intent(this@RTCActivity, MainActivity::class.java))
        }
    }

    private fun checkCameraAndAudioPermission() {
        if ((ContextCompat.checkSelfPermission(this, CAMERA_PERMISSION)
                    != PackageManager.PERMISSION_GRANTED) &&
            (ContextCompat.checkSelfPermission(this,AUDIO_PERMISSION)
                    != PackageManager.PERMISSION_GRANTED)) {
            requestCameraAndAudioPermission()
        } else {
            onCameraAndAudioPermissionGranted()
        }
    }

    private fun stringToByteBuffer(msg: String, charset: Charset): ByteBuffer {
        return ByteBuffer.wrap(msg.toByteArray(charset))
    }

    fun readIncomingMessage(buffer: ByteBuffer) {
        val bytes: ByteArray
        if (buffer.hasArray()) {
            bytes = buffer.array()
        } else {
            bytes = ByteArray(buffer.remaining())
            buffer[bytes]
        }
        val firstMessage = String(bytes, Charset.defaultCharset())
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(applicationContext, "New text is: $firstMessage", Toast.LENGTH_LONG).show()
        }
    }


    private fun onCameraAndAudioPermissionGranted() {
        rtcClient = RTCClient(
                application,
                object : PeerConnectionObserver() {
                    override fun onIceCandidate(p0: IceCandidate?) {
                        super.onIceCandidate(p0)
                        Log.d(TAG, "Awesome.. --1111 onAddStream: $p0")
                        signallingClient.sendIceCandidate(p0, isJoin)
                        rtcClient.addIceCandidate(p0)
                    }

                    override fun onAddStream(p0: MediaStream?) {
                        super.onAddStream(p0)
                        Log.d(TAG, "Awesome.. 0000 onAddStream: $p0")
                        p0?.videoTracks?.get(0)?.addSink(remote_view)
                    }

                    override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {

                        if( p0 == PeerConnection.IceConnectionState.CONNECTED ) {
//                            signallingClient.sendChannel.offer("Awesome 33")

                            val meta: ByteBuffer =
                                stringToByteBuffer("Awesome 44", Charset.defaultCharset())
//                            localDataChannel!!.send(DataChannel.Buffer(meta, false))
                            Log.d(TAG, "Awesome.. 1 It is connected")
                        }
                        else if( p0 == PeerConnection.IceConnectionState.COMPLETED ) {
                            val meta: ByteBuffer =
                                stringToByteBuffer("awesome 55", Charset.defaultCharset())

//                            signallingClient.sendChannel.offer("Awesome 22")
//                            localDataChannel?.send(DataChannel.Buffer(meta, false))
                            Log.d(TAG, "Awesome.. 2 It is to the end completed connection")
                        }
                        else if( p0 == PeerConnection.IceConnectionState.DISCONNECTED )
                            Log.d(TAG, "Awesome.. 3 Connection is finished")

                    }

                    override fun onIceConnectionReceivingChange(p0: Boolean) {
                        Log.d(TAG, "Awesome.. 2222 onIceConnectionReceivingChange: $p0")
                    }

                    override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                        Log.d(TAG, "Awesome.. 3333   onConnectionChange: $newState")

                        if( newState == PeerConnection.PeerConnectionState.CONNECTED  ) {

//                            val meta: ByteBuffer =
//                                stringToByteBuffer("Awesome 88", Charset.defaultCharset())
//                            localDataChannel!!.send(DataChannel.Buffer(meta, false))

//                            signallingClient.sendChannel.offer("Awesome 22")
                        }
                    }

                    override fun onDataChannel(p0: DataChannel?) {

                        Log.d(TAG, "Awesome.. 4444  onDataChannel: $p0")
                        Log.d(TAG, "Awesome.. 4444  localDataChannel: $localDataChannel")

                        val test9 = localDataChannel?.state()
                        Log.d(TAG, "Awesome.. 4444  localDataChannel state: ${test9}")
                        p0?.registerObserver(object : DataChannel.Observer {
                                override fun onBufferedAmountChange(l: Long) {}
                                override fun onStateChange() {
                                    Log.d(
                                        TAG,
                                        "22 onStateChange: remote data channel state: " + localDataChannel!!.state()
                                            .toString()
                                    )
                                }

                                override fun onMessage(buffer: DataChannel.Buffer) {
                                    Log.d(TAG, "22 onMes≠≠==sage: got message")
                                    readIncomingMessage(buffer.data)
                                }
                            })

                    }

                    override fun onStandardizedIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                        Log.d(TAG, "Awesome.. 5555  onStandardizedIceConnectionChange: $newState")
                        if( newState == PeerConnection.IceConnectionState.CONNECTED ||
                                newState == PeerConnection.IceConnectionState.COMPLETED) {

                            val meta: ByteBuffer =
                                stringToByteBuffer("Awesome 44", Charset.defaultCharset())
//                            localDataChannel!!.send(DataChannel.Buffer(meta, false))

//                            signallingClient.sendChannel.offer("Awesome 22")
                        }
                    }

                    override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {
                        Log.d(TAG, "Awesome.. 6666  onAddTrack: $p0 \n $p1")
                    }

                    override fun onTrack(transceiver: RtpTransceiver?) {
                        Log.d(TAG, "Awesome.. 7777  onTrack: $transceiver" )
                    }
                }
        )

//        rtcClient.initSurfaceView(remote_view)
//        rtcClient.initSurfaceView(local_view)
//        rtcClient.startLocalVideoCapture(local_view)
        signallingClient =  SignalingClient(meetingID,createSignallingClientListener())
        if (!isJoin)
            rtcClient.call(sdpObserver,meetingID)
    }

    private fun createSignallingClientListener() = object : SignalingClientListener {
        override fun onConnectionEstablished() {
            end_call_button.isClickable = true
        }

        override fun onOfferReceived(description: SessionDescription) {

            Log.d(TAG, "Awesome.. 7777  onTrack: ${   rtcClient.peerConnection?.signalingState()}" )
            if( isJoin ) {
                rtcClient.onRemoteSessionReceived(description)
                Constants.isIntiatedNow = false
                rtcClient.answer(sdpObserver, meetingID)
                remote_view_loading.isGone = true
            }
        }

        override fun onAnswerReceived(description: SessionDescription) {
            Log.d(TAG, "Awesome.. Answer  ${   rtcClient.peerConnection?.signalingState()}" )

            if( !isJoin
                && rtcClient.peerConnection?.signalingState() != PeerConnection.SignalingState.STABLE
            ) {
                rtcClient.onRemoteSessionReceived(description)
                Constants.isIntiatedNow = false
                remote_view_loading.isGone = true
            }
        }

        override fun onIceCandidateReceived(iceCandidate: IceCandidate) {
            rtcClient.addIceCandidate(iceCandidate)
        }

        override fun onCallEnded() {
            if (!Constants.isCallEnded) {
                Constants.isCallEnded = true
                rtcClient.endCall(meetingID)
                finish()
                startActivity(Intent(this@RTCActivity, MainActivity::class.java))
            }
        }
    }

    private fun requestCameraAndAudioPermission(dialogShown: Boolean = false) {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, CAMERA_PERMISSION) &&
            ActivityCompat.shouldShowRequestPermissionRationale(this, AUDIO_PERMISSION) &&
            !dialogShown) {
            showPermissionRationaleDialog()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(CAMERA_PERMISSION, AUDIO_PERMISSION), CAMERA_AUDIO_PERMISSION_REQUEST_CODE)
        }
    }

    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
                .setTitle("Camera And Audio Permission Required")
                .setMessage("This app need the camera and audio to function")
                .setPositiveButton("Grant") { dialog, _ ->
                    dialog.dismiss()
                    requestCameraAndAudioPermission(true)
                }
                .setNegativeButton("Deny") { dialog, _ ->
                    dialog.dismiss()
                    onCameraPermissionDenied()
                }
                .show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_AUDIO_PERMISSION_REQUEST_CODE && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            onCameraAndAudioPermissionGranted()
        } else {
            onCameraPermissionDenied()
        }
    }

    private fun onCameraPermissionDenied() {
        Toast.makeText(this, "Camera and Audio Permission Denied", Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        signallingClient.destroy()
        super.onDestroy()
    }
}