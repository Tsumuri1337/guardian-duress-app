package com.duress.guardian.watchdog

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.duress.guardian.R
import com.duress.guardian.admin.WipeController
import com.duress.guardian.core.SettingsRepository

/**
 * The inactivity auto-wipe ("dead-man's switch"). If the device isn't unlocked for
 * [SettingsRepository.inactivityHours], it wipes — but only after a grace window during which a
 * warning is shown and any unlock aborts it, and only if the app is Device Owner.
 *
 * Two AlarmManager phases:
 *  - MAIN  : fires at lastUnlock + threshold. If still idle, shows the warning and arms GRACE.
 *  - GRACE : fires after the grace window. If still idle (no unlock happened), performs the wipe.
 *
 * Any unlock ([onUnlock]) records the time, cancels a pending warning/grace, and re-arms MAIN.
 * Alarms are inexact-but-Doze-friendly (setAndAllowWhileIdle), so no exact-alarm permission is
 * needed — minute-level drift is irrelevant for a timer measured in hours.
 */
object InactivityWatchdog {

    private const val TAG = "InactivityWatchdog"
    private const val WARN_CHANNEL = "guardian_warning"
    private const val WARN_NOTIF_ID = 2001

    private const val ACTION_ALARM = "com.duress.guardian.INACTIVITY_ALARM"
    private const val EXTRA_PHASE = "phase"
    private const val PHASE_MAIN = "main"
    private const val PHASE_GRACE = "grace"

    private const val RC_MAIN = 1001
    private const val RC_GRACE = 1002

    /** Record an unlock and re-arm the timer from now. Called on device/app unlock. */
    fun onUnlock(context: Context) {
        SettingsRepository(context).recordUnlock()
        cancelWarning(context)
        reschedule(context)
    }

    /** Arm (or cancel) the MAIN alarm to match current settings. Safe to call repeatedly. */
    fun reschedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val settings = SettingsRepository(context)

        cancelAlarm(context, am, RC_GRACE, PHASE_GRACE)
        if (!settings.inactivityWipeEnabled) {
            cancelAlarm(context, am, RC_MAIN, PHASE_MAIN)
            Log.i(TAG, "Inactivity wipe disabled — timer cancelled")
            return
        }

        val last = settings.lastUnlockAt
        if (last == 0L) {
            // No unlock seen yet — anchor to now so the switch can't fire prematurely.
            settings.recordUnlock()
        }
        val deadline = InactivityPolicy.deadline(settings.lastUnlockAt, settings.inactivityThresholdMs)
        scheduleAlarm(context, am, RC_MAIN, PHASE_MAIN, deadline)
        Log.i(TAG, "Inactivity MAIN armed for ${deadline} (in ${(deadline - System.currentTimeMillis()) / 1000}s)")
    }

    /** Alarm callback, dispatched from [InactivityReceiver]. */
    fun onAlarm(context: Context, phase: String) {
        val settings = SettingsRepository(context)
        if (!settings.inactivityWipeEnabled) return
        val now = System.currentTimeMillis()
        val expired = InactivityPolicy.isExpired(now, settings.lastUnlockAt, settings.inactivityThresholdMs)

        when (phase) {
            PHASE_MAIN -> {
                if (expired) {
                    Log.w(TAG, "Inactivity threshold reached — entering ${settings.inactivityGraceMs / 1000}s grace")
                    showWarning(context, settings.inactivityGraceMs)
                    val am = context.getSystemService(AlarmManager::class.java)
                    scheduleAlarm(context, am, RC_GRACE, PHASE_GRACE, now + settings.inactivityGraceMs)
                } else {
                    reschedule(context) // unlocked since arming — push the deadline out
                }
            }
            PHASE_GRACE -> {
                cancelWarning(context)
                if (expired) {
                    val wiped = WipeController(context).wipe()
                    Log.w(TAG, "Inactivity grace elapsed — wipe requested, executed=$wiped")
                } else {
                    Log.i(TAG, "Unlocked during grace — wipe aborted")
                    reschedule(context)
                }
            }
        }
    }

    // --- alarms ---

    private fun alarmIntent(context: Context, phase: String) =
        Intent(context, InactivityReceiver::class.java).setAction(ACTION_ALARM).putExtra(EXTRA_PHASE, phase)

    private fun pending(context: Context, requestCode: Int, phase: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode, alarmIntent(context, phase),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun scheduleAlarm(context: Context, am: AlarmManager, rc: Int, phase: String, triggerAt: Long) {
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending(context, rc, phase))
    }

    private fun cancelAlarm(context: Context, am: AlarmManager, rc: Int, phase: String) {
        am.cancel(pending(context, rc, phase))
    }

    // --- warning notification ---

    private fun showWarning(context: Context, graceMs: Long) {
        val mgr = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mgr.createNotificationChannel(
                NotificationChannel(WARN_CHANNEL, "Security warnings", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val minutes = (graceMs / 60000L).coerceAtLeast(1)
        val notif = NotificationCompat.Builder(context, WARN_CHANNEL)
            .setContentTitle(context.getString(R.string.inactivity_warn_title))
            .setContentText(context.getString(R.string.inactivity_warn_body, minutes))
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .build()
        mgr.notify(WARN_NOTIF_ID, notif)
    }

    private fun cancelWarning(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(WARN_NOTIF_ID)
    }
}
