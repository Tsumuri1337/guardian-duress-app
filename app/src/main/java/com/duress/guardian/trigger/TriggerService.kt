package com.duress.guardian.trigger

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.duress.guardian.R
import com.duress.guardian.core.ResponseCoordinator
import com.duress.guardian.core.SettingsRepository

/**
 * Foreground service that detects the hardware-button duress pattern.
 *
 * On stock Android an app cannot read the power key directly. The reliable, documented technique
 * is to count rapid SCREEN_ON / SCREEN_OFF broadcasts — each power-button press toggles the
 * screen. [DuressConfig.TRIGGER_PRESS_COUNT] toggles within [DuressConfig.TRIGGER_WINDOW_MS] fire
 * the trigger. These broadcasts are only delivered to receivers registered at runtime, not to
 * manifest-declared ones, which is why the receiver is registered here in the service.
 */
class TriggerService : Service() {

    private val timestamps = ArrayDeque<Long>()
    private lateinit var settings: SettingsRepository

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON, Intent.ACTION_SCREEN_OFF -> registerPress(context)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
        startForeground(NOTIF_ID, buildNotification())
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(
            this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
        Log.i(TAG, "TriggerService started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerPress(context: Context) {
        // Read live so changes in Settings apply without restarting the service.
        if (!settings.hardwareTriggerEnabled) {
            timestamps.clear()
            return
        }
        val windowMs = settings.triggerWindowMs
        val pressCount = settings.triggerPressCount

        val now = System.currentTimeMillis()
        timestamps.addLast(now)
        while (timestamps.isNotEmpty() && now - timestamps.first() > windowMs) {
            timestamps.removeFirst()
        }
        if (timestamps.size >= pressCount) {
            timestamps.clear()
            ResponseCoordinator.fire(context, source = "hardware-button")
        }
    }

    private fun buildNotification(): Notification {
        val mgr = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    "Background service",
                    NotificationManager.IMPORTANCE_MIN
                )
            )
        }
        // For a covert build the title/icon here would be made to look like a benign system service.
        return NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle(getString(R.string.service_notification_title))
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "TriggerService"
        private const val CHANNEL = "guardian_bg"
        private const val NOTIF_ID = 1001

        fun start(context: Context) {
            val i = Intent(context, TriggerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }
    }
}
