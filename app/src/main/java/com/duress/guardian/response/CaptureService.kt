package com.duress.guardian.response

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.duress.guardian.R
import java.io.File

/**
 * Records a short audio clip of the surroundings after a duress trigger, to app-internal storage
 * (`filesDir/evidence/`, private to this app). Runs as a foreground service with the `microphone`
 * type, as Android 14+ requires.
 *
 * Platform limitation, documented not fought: while recording, Android shows a microphone privacy
 * indicator that cannot be suppressed on a stock device. Capture is covert to ordinary UI, not to
 * the OS indicators.
 */
class CaptureService : Service() {

    private var recorder: MediaRecorder? = null
    private val stopHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startRecording()
        stopHandler.postDelayed({ stopSelf() }, CLIP_MS)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopHandler.removeCallbacksAndMessages(null)
        recorder?.let { r ->
            runCatching { r.stop() }
            r.release()
        }
        recorder = null
        super.onDestroy()
    }

    private fun startRecording() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "No RECORD_AUDIO permission — cannot capture")
            stopSelf()
            return
        }
        val dir = File(filesDir, "evidence").apply { mkdirs() }
        val file = File(dir, "audio_${System.currentTimeMillis()}.m4a")
        try {
            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this)
            else @Suppress("DEPRECATION") MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            Log.i(TAG, "Evidence capture started -> ${file.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start capture", e)
            stopSelf()
        }
    }

    private fun startAsForeground() {
        val mgr = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL, "Background service", NotificationManager.IMPORTANCE_MIN)
            )
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), type)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle(getString(R.string.service_notification_title))
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()

    companion object {
        private const val TAG = "CaptureService"
        private const val CHANNEL = "guardian_bg"
        private const val NOTIF_ID = 1002
        private const val CLIP_MS = 120_000L // record ~2 minutes

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, CaptureService::class.java))
        }
    }
}
