package com.developerspace.webrtcsample

import org.webrtc.DataChannel
import java.nio.ByteBuffer

class DataChanelObserver : DataChannel.Observer {

    override fun onMessage(buffer: DataChannel.Buffer) {
        val data: ByteBuffer = buffer.data
        val bytes = ByteArray(data.remaining())
        data.get(bytes)
        val command = String(bytes)

//        executor.execute(Runnable { events.onReceivedData(command) })
    }

    override fun onBufferedAmountChange(p0: Long) {
        TODO("Not yet implemented")
    }

    override fun onStateChange() {
        TODO("Not yet implemented")
    }

//    override fun onStateChange() {
//        Log.d(TAG, "DataChannel: onStateChange: " + dataChannel.state())
//    }

}