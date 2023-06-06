package com.developerspace.webrtcsample

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_GET_CONTENT
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.isGone
import androidx.recyclerview.widget.GridLayoutManager
import com.developerspace.webrtcsample.RTCClient.Companion.localDataChannel
import kotlinx.android.synthetic.main.activity_main.audio_output_button
import kotlinx.android.synthetic.main.activity_main.end_call_button
import kotlinx.android.synthetic.main.activity_main.mic_button
import kotlinx.android.synthetic.main.activity_main.recycler
import kotlinx.android.synthetic.main.activity_main.remote_view
import kotlinx.android.synthetic.main.activity_main.remote_view_loading
import kotlinx.android.synthetic.main.activity_main.switch_camera_button
import kotlinx.android.synthetic.main.activity_main.video_button
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SessionDescription
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset


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

    private var meetingID: String = "test-call"

    private var isJoin = false

    private var isMute = false

    private var isVideoPaused = false

    private var inSpeakerMode = true

    val CHUNK_SIZE = 64000 // 128000 // 64000

    var list: ArrayList<Uri>? = null
    var adaptor: ImageDisplayAdapter? = null

    var incomingFileSize = 0
    var togetherIncomingFileSize = 0
    var currentIndexPointer = 0
    lateinit var imageFileBytes: ByteArray
    var receivingFile = false

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
            isJoin = intent.getBooleanExtra("isJoin", false)

        checkCameraAndAudioPermission()
        audioManager.selectAudioDevice(RTCAudioManager.AudioDevice.SPEAKER_PHONE)

        list = ArrayList()
        adaptor = ImageDisplayAdapter(list!!)
        recycler.setLayoutManager(GridLayoutManager(this@RTCActivity, 4))
        recycler.setAdapter(adaptor)

        switch_camera_button.setOnClickListener {
//            rtcClient.switchCamera()
            val intent = Intent(ACTION_GET_CONTENT)
//            intent.setType("image/*")
            intent.setType("*/*")
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
            startActivityForResult(Intent.createChooser(intent, "Select media"), 123)
        }

        audio_output_button.setOnClickListener {
            try {

                val student1 = JSONObject()

                student1.put("id", "3")
                student1.put("name", "NAME OF STUDENT")
                student1.put("year", "3rd")
                student1.put("curriculum", "Arts")
                student1.put("birthday", "5/5/1993")

                val student2 = JSONObject()
                student2.put("id", "2")
//                    student2.put("fileBytes", filePAth_uzeti_byte_na_kraju_toString())
                student2.put("name", "NAME OF STUDENT2")
                student2.put("year", "4rd")
                student2.put("curriculum", "scicence")
                student2.put("birthday", "5/5/1993")

                val jsonArray = JSONArray()

                jsonArray.put(student1)
                jsonArray.put(student2)

                val studentsObj = JSONObject()
                studentsObj.put("Students", jsonArray)

                val jsonStr = studentsObj.toString()

                Log.d(TAG, "jsonString: $jsonStr")

                println("jsonString: $jsonStr")

                val meta: ByteBuffer =
                    jsonToByteBuffer(jsonStr, Charset.defaultCharset())
                Log.d(TAG, "Awesome.. send meta: ${meta}")
                localDataChannel?.send(DataChannel.Buffer(meta, false))

                Log.d(TAG, "Awesome.. DC : ${localDataChannel}")
                Log.d(TAG, "Awesome.. DC bufferedAmount: ${localDataChannel?.bufferedAmount()}")
                Log.d(TAG, "Awesome.. DC id: ${localDataChannel?.id()}")
            } catch (e: Exception) {

                Log.d(TAG, "Awesome.. DC exception is: $e")
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data1: Intent?) {
        super.onActivityResult(requestCode, resultCode, data1)
        if (requestCode == 123 && resultCode == RESULT_OK) {
            val data: Intent? = data1
//            //If multiple image selected
//            if (data?.clipData != null) {
//                val count = data.clipData?.itemCount ?: 0
//
//                for (i in 0 until count) {
//                    val imageUri: Uri? = data.clipData?.getItemAt(i)?.uri
//                    val file = getImageFromUri(imageUri)
//                    file?.let {
//                        selectedPaths.add(it.absolutePath)
//                    }
//                }
//                imageAdapter.addSelectedImages(selectedPaths)
//            }
//            //If single image selected
//            else if (data?.data != null) {
//                val imageUri: Uri? = data.data
//                val file = getImageFromUri(imageUri)
//                file?.let {
//                    selectedPaths.add(it.absolutePath)
//                }
//                imageAdapter.addSelectedImages(selectedPaths)
//            }

            var sendedBytes = 0
            if (data?.clipData != null) {
                val x = data.clipData!!.itemCount
                for (i in 0 until x) {
                    list?.add(data.clipData!!.getItemAt(i).uri)

//                    val imageFile = File(data.clipData!!.getItemAt(i).uri.path)
                    val imageFile = getFile(getApplicationContext(), data.clipData!!.getItemAt(i).uri);
                    val size = imageFile.length().toInt()
                    val bytes: ByteArray = readPickedFileAsBytes(imageFile, size)
                    sendedBytes = sendedBytes + bytes.size
                    sendImage(size, bytes);
                }

                Log.d(TAG, "Awesome.. sended bytes size is: $sendedBytes")
//                adaptor?.notifyDataSetChanged()
//                textView.setText("Image(" + list.size() + ")")
            } else if (data?.data != null) {
                val imgurl = data.data!!.path
                list?.add(Uri.parse(imgurl))

                val imageFile = getFile(getApplicationContext(), data.data);
                val size = imageFile.length().toInt()
                val bytes: ByteArray = readPickedFileAsBytes(imageFile, size)
                sendedBytes = sendedBytes + bytes.size
                sendImage(size, bytes);
                Log.d(TAG, "Awesome.. sended bytes size is: $sendedBytes")
            }
        }
    }

    @Throws(IOException::class)
    fun getFile(context: Context, uri: Uri?): File  {
        val destinationFilename: File =
            File(context.getFilesDir().getPath() + File.separatorChar + queryName(context, uri!!))
        try {
            if (uri != null) {
                context.getContentResolver().openInputStream(uri).use { ins ->
                    if (ins != null) {
                        createFileFromStream(
                            ins,
                            destinationFilename
                        )
                    }
                }
            }
        } catch (ex: java.lang.Exception) {
            Log.e("Save File", ex.message!!)
            ex.printStackTrace()
        }
        return destinationFilename
    }

    private fun queryName(context: Context, uri: Uri): String? {
        val returnCursor: Cursor = context.contentResolver.query(uri, null, null, null, null)!!
        val nameIndex: Int = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        returnCursor.moveToFirst()
        val name: String = returnCursor.getString(nameIndex)
        returnCursor.close()
        return name
    }

    fun createFileFromStream(ins: InputStream, destination: File?) {
        try {
            FileOutputStream(destination).use { os ->
                val buffer = ByteArray(4096)
                var length: Int
                while (ins.read(buffer).also { length = it } > 0) {
                    os.write(buffer, 0, length)
                }
                os.flush()
            }
        } catch (ex: java.lang.Exception) {
            Log.e("Save File", ex.message!!)
            ex.printStackTrace()
        }
    }

    private fun sendImage(size: Int, bytes: ByteArray) {
        val numberOfChunks: Int =
            size /  CHUNK_SIZE
        val meta: ByteBuffer =
            stringToByteBuffer(
                "-i$size", Charset.defaultCharset()
            )
        localDataChannel!!.send(DataChannel.Buffer(meta, false))
        for (i in 0 until numberOfChunks) {
            val wrap = ByteBuffer.wrap(
                bytes,
                i *  CHUNK_SIZE,
                 CHUNK_SIZE
            )
            localDataChannel!!.send(DataChannel.Buffer(wrap, false))
        }
        val remainder: Int =
            size %  CHUNK_SIZE
        if (remainder > 0) {
            val wrap = ByteBuffer.wrap(
                bytes,
                numberOfChunks *  CHUNK_SIZE,
                remainder
            )
            localDataChannel!!.send(DataChannel.Buffer(wrap, false))
        }
    }

    private fun readPickedFileAsBytes(imageFile: File, size: Int): ByteArray {
        val bytes = ByteArray(size)
        try {

            Log.d(TAG, "Awesome.. imageFile is: $imageFile")
            if(!imageFile.mkdir())
                imageFile.mkdir()
            if(!imageFile.exists() )
                imageFile.createNewFile()
            val buf = BufferedInputStream(FileInputStream(imageFile))
            buf.read(bytes, 0, bytes.size)
            buf.close()
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return bytes
    }

    private fun checkCameraAndAudioPermission() {
        if ((ContextCompat.checkSelfPermission(this, CAMERA_PERMISSION)
                    != PackageManager.PERMISSION_GRANTED) &&
            (ContextCompat.checkSelfPermission(this, AUDIO_PERMISSION)
                    != PackageManager.PERMISSION_GRANTED)
        ) {
            requestCameraAndAudioPermission()
        } else {
            onCameraAndAudioPermissionGranted()
        }
    }

    private fun stringToByteBuffer(msg: String, charset: Charset): ByteBuffer {
        return ByteBuffer.wrap(msg.toByteArray(charset))
    }

    private fun jsonToByteBuffer(msg: String, charset: Charset): ByteBuffer {
        return ByteBuffer.wrap(msg.toByteArray(charset))
    }

    fun readIncomingImages(buffer: ByteBuffer) {
        val bytes: ByteArray
        if (buffer.hasArray()) {
            bytes = buffer.array()
        } else {
            bytes = ByteArray(buffer.remaining())
            buffer[bytes]
        }
        if (!receivingFile) {
            val firstMessage = String(bytes, Charset.defaultCharset())
            val type = firstMessage.substring(0, 2)
            if (type == "-i") {
                incomingFileSize = firstMessage.substring(2, firstMessage.length).toInt()
                togetherIncomingFileSize = togetherIncomingFileSize + incomingFileSize
                imageFileBytes = ByteArray(incomingFileSize)
                Log.d(TAG, "readIncomingMessage: incoming file size $incomingFileSize")
                receivingFile = true
            }
//            else if (type == "-s") {
//                runOnUiThread(Runnable {
//                    binding.remoteText.setText(
//                        firstMessage.substring(
//                            2,
//                            firstMessage.length
//                        )
//                    )
//                })
//            }
        } else {
            for (b in bytes) {
                imageFileBytes[currentIndexPointer++] = b
            }
            if (currentIndexPointer == incomingFileSize) {
                Log.d(TAG, "readIncomingMessage: received all bytes.. size of sended bytes is: $togetherIncomingFileSize")
//                val bmp = BitmapFactory.decodeByteArray(imageFileBytes, 0, imageFileBytes.size)
                receivingFile = false
                currentIndexPointer = 0

//                val file = File("/data/data/image_${counterImage}.jpg")
//                if (!file.exists()) {
//                    file.createNewFile()
//                }
//                val fos = FileOutputStream(file)
//                fos.write(imageFileBytes)
//                fos.close()

//                list?.add(file.toUri())
//
//                adaptor?.notifyDataSetChanged()
//                runOnUiThread(Runnable { binding.image.setImageBitmap(bmp) })
            }
        }
    }

    fun readIncomingMessage(buffer: ByteBuffer) {
        val bytes: ByteArray
        if (buffer.hasArray()) {
            bytes = buffer.array()
        } else {
            bytes = ByteArray(buffer.remaining())
            buffer[bytes]
        }
        val receivedMessage = String(bytes, Charset.defaultCharset())

        Log.d(TAG, "Awesome.. received messages is: ${receivedMessage}")
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(applicationContext, "New text is: $receivedMessage", Toast.LENGTH_LONG)
                .show()
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

                    if (p0 == PeerConnection.IceConnectionState.CONNECTED) {
//                            signallingClient.sendChannel.offer("Awesome 33")

                        val meta: ByteBuffer =
                            stringToByteBuffer("Awesome 44", Charset.defaultCharset())
//                            localDataChannel!!.send(DataChannel.Buffer(meta, false))
                        Log.d(TAG, "Awesome.. 1 It is connected")
                    } else if (p0 == PeerConnection.IceConnectionState.COMPLETED) {
                        val meta: ByteBuffer =
                            stringToByteBuffer("awesome 55", Charset.defaultCharset())

//                            signallingClient.sendChannel.offer("Awesome 22")
//                            localDataChannel?.send(DataChannel.Buffer(meta, false))
                        Log.d(TAG, "Awesome.. 2 It is to the end completed connection")
                    } else if (p0 == PeerConnection.IceConnectionState.DISCONNECTED)
                        Log.d(TAG, "Awesome.. 3 Connection is finished")

                }

                override fun onIceConnectionReceivingChange(p0: Boolean) {
                    Log.d(TAG, "Awesome.. 2222 onIceConnectionReceivingChange: $p0")
                }

                override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                    Log.d(TAG, "Awesome.. 3333   onConnectionChange: $newState")

                    if (newState == PeerConnection.PeerConnectionState.CONNECTED) {

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
                            Log.d(TAG, "22 onMes≠≠==sage: got message.. ${buffer.data}")
//                            readIncomingMessage(buffer.data)
                            readIncomingImages(buffer.data)
                        }
                    })

                }

                override fun onStandardizedIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                    Log.d(TAG, "Awesome.. 5555  onStandardizedIceConnectionChange: $newState")
                    if (newState == PeerConnection.IceConnectionState.CONNECTED ||
                        newState == PeerConnection.IceConnectionState.COMPLETED
                    ) {

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
                    Log.d(TAG, "Awesome.. 7777  onTrack: $transceiver")
                }
            }
        )

//        rtcClient.initSurfaceView(remote_view)
//        rtcClient.initSurfaceView(local_view)
//        rtcClient.startLocalVideoCapture(local_view)
        signallingClient = SignalingClient(meetingID, createSignallingClientListener())
        if (!isJoin)
            rtcClient.call(sdpObserver, meetingID)
    }

    private fun createSignallingClientListener() = object : SignalingClientListener {
        override fun onConnectionEstablished() {
            end_call_button.isClickable = true
        }

        override fun onOfferReceived(description: SessionDescription) {

            Log.d(TAG, "Awesome.. 7777  onTrack: ${rtcClient.peerConnection?.signalingState()}")
            if (isJoin) {
                rtcClient.onRemoteSessionReceived(description)
                Constants.isIntiatedNow = false
                rtcClient.answer(sdpObserver, meetingID)
                remote_view_loading.isGone = true
            }
        }

        override fun onAnswerReceived(description: SessionDescription) {
            Log.d(TAG, "Awesome.. Answer  ${rtcClient.peerConnection?.signalingState()}")

            if (!isJoin
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
            !dialogShown
        ) {
            showPermissionRationaleDialog()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(CAMERA_PERMISSION, AUDIO_PERMISSION),
                CAMERA_AUDIO_PERMISSION_REQUEST_CODE
            )
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
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