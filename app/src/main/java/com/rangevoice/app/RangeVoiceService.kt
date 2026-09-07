package com.rangevoice.app

import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.util.Log
import java.io.IOException
import java.util.UUID
import java.util.Locale

class RangeVoiceService : Service() {

    companion object {
        const val CHANNEL_ID = "range_voice_channel"
        const val NOTIF_ID = 1
        val APP_UUID: UUID = UUID.fromString("7a2f9c10-4b3e-4a2a-9b1d-1234567890ab")
        const val TAG = "RangeVoiceService"

        const val REPEAT_INTERVAL_MS = 1000L
        const val RETRY_DELAY_MS = 1500L
    }

    private lateinit var adapter: BluetoothAdapter
    private lateinit var prefs: SharedPreferences
    private lateinit var tts: TextToSpeech

    @Volatile private var running = false
    private var serverThread: Thread? = null
    private var clientThread: Thread? = null
    private var serverSocket: BluetoothServerSocket? = null

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("range_voice_prefs", MODE_PRIVATE)
        val btManager = getSystemService(BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
        adapter = btManager.adapter

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale("hi", "IN")
            }
        }

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running) {
            running = true
            startForeground(NOTIF_ID, buildNotification("Service chal raha hai..."))
            startServerLoop()
            startClientLoop()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        try { serverSocket?.close() } catch (e: IOException) { }
        serverThread?.interrupt()
        clientThread?.interrupt()
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startServerLoop() {
        serverThread = Thread {
            while (running) {
                var socket: BluetoothSocket? = null
                try {
                    serverSocket = adapter.listenUsingInsecureRfcommWithServiceRecord(
                        "RangeVoiceServer", APP_UUID
                    )
                    Log.d(TAG, "Server listening...")
                    socket = serverSocket!!.accept()
                    updateNotification("Connected - sun raha hoon...")
                    val buffer = ByteArray(2048)
                    while (running) {
                        val bytesRead = socket.inputStream.read(buffer)
                        if (bytesRead <= 0) break
                        val message = String(buffer, 0, bytesRead, Charsets.UTF_8)
                        Log.d(TAG, "Received: $message")
                        speak(message)
                        updateNotification("Bol raha hoon: $message")
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Server loop error: ${e.message}")
                } finally {
                    try { socket?.close() } catch (e: IOException) { }
                    try { serverSocket?.close() } catch (e: IOException) { }
                    if (running) updateNotification("Range se bahar - dobara sun raha hoon...")
                }
            }
        }
        serverThread?.start()
    }

    private fun startClientLoop() {
        clientThread = Thread {
            while (running) {
                val peerAddress = prefs.getString("peer_address", null)
                val myMessage = prefs.getString("my_message", null)

                if (peerAddress != null && !myMessage.isNullOrBlank()) {
                    var socket: BluetoothSocket? = null
                    try {
                        val device = adapter.getRemoteDevice(peerAddress)
                        adapter.cancelDiscovery()
                        socket = device.createInsecureRfcommSocketToServiceRecord(APP_UUID)
                        socket.connect()
                        updateNotification("Range me mila - message bhej raha hoon...")

                        while (running) {
                            val currentMessage = prefs.getString("my_message", myMessage) ?: myMessage
                            socket.outputStream.write(currentMessage.toByteArray(Charsets.UTF_8))
                            socket.outputStream.flush()
                            Thread.sleep(REPEAT_INTERVAL_MS)
                        }
                    } catch (e: IOException) {
                        // peer not in range, or connection dropped
                    } catch (e: InterruptedException) {
                        break
                    } finally {
                        try { socket?.close() } catch (e: IOException) { }
                    }
                }
                try {
                    Thread.sleep(RETRY_DELAY_MS)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }
        clientThread?.start()
    }

    private fun speak(text: String) {
        if (::tts.isInitialized) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "range_voice_msg")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Range Voice Service", NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return notificationBuilder(this, text).build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIF_ID, notificationBuilder(this, text).build())
    }
}

private fun notificationBuilder(service: Service, text: String): Notification.Builder {
    val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Notification.Builder(service, RangeVoiceService.CHANNEL_ID)
    } else {
        Notification.Builder(service)
    }
    return builder
        .setContentTitle("Range Voice Messenger")
        .setContentText(text)
        .setSmallIcon(android.R.drawable.ic_btn_speak_now)
        .setOngoing(true)
}
