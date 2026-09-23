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
import com.duress.guardian.core.ResponseCoordinator
import com.duress.guardian.core.Response
import com.duress.guardian.core.SettingsRepository
import com.duress.guardian.core.TimerMode
import com.duress.guardian.core.Trigger

/**
 * Drives the timed action — either a **dead-man's switch** (fires after inactivity; any unlock
 * resets it) or a **fixed timer** (fires after real time from when armed; unlocking does not reset
 * it). When it fires it runs the responses configured for [Trigger.TIMER] (alert / capture / wipe),
 * after a grace window with a warning. In dead-man mode, unlocking during grace aborts.
 *
 * Two AlarmManager phases: MAIN fires at anchor + duration; if still due it shows the warning and
 * arms GRACE, which then fires the response. Alarms use setAndAllowWhileIdle (no exact-alarm
 * permission needed). It is one-shot: after firing it disables itself so it can't loop.
 */
object InactivityWatchdog {

    private const val TAG = "InactivityWatchdog"
    private const val WARN_CHANNEL = "guardian_warning"
    private const val WARN_NOTIF_ID = 2001

    private const val EXTRA_PHASE = "phase"
    private const val PHASE_MAIN = "main"
    private const val PHASE_GRACE = "grace"
    private const val RC_MAIN = 1001
    private const val RC_GRACE = 1002

    /** Record an unlock. In dead-man mode this resets the countdown; a fixed timer ignores it. */
    fun onUnlock(context: Context) {
        val settings = SettingsRepository(context)
        settings.recordUnlock()
        if (settings.timerMode == TimerMode.DEADMAN) {
            cancelWarning(context)
            reschedule(context)
        }
    }

    /** Arm (or cancel) the MAIN alarm to match current settings. Safe to call repeatedly. */
    fun reschedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val settings = SettingsRepository(context)

        cancelAlarm(context, am, RC_GRACE, PHASE_GRACE)
        if (!settings.timerEnabled) {
            cancelAlarm(context, am, RC_MAIN, PHASE_MAIN)
            Log.i(TAG, "Timer disabled — cancelled")
            return
        }

        // Make sure the anchor exists so we never fire prematurely.
        when (settings.timerMode) {
            TimerMode.DEADMAN -> if (settings.lastUnlockAt == 0L) settings.recordUnlock()
            TimerMode.FIXED -> if (settings.timerArmedAt == 0L) settings.timerArmedAt = System.currentTimeMillis()
        }
        val deadline = InactivityPolicy.deadline(settings.timerAnchor(), settings.timerDurationMs)
        scheduleAlarm(context, am, RC_MAIN, PHASE_MAIN, deadline)
        Log.i(TAG, "Timer(${settings.timerMode}) armed for $deadline (in ${(deadline - System.currentTimeMillis()) / 1000}s)")
    }

    /** Alarm callback, dispatched from [InactivityReceiver]. */
    fun onAlarm(context: Context, phase: String) {
        val settings = SettingsRepository(context)
        if (!settings.timerEnabled) return
        val now = System.currentTimeMillis()
        val due = InactivityPolicy.isExpired(now, settings.timerAnchor(), settings.timerDurationMs)

        when (phase) {
            PHASE_MAIN -> {
                if (due) {
                    val wipe = settings.getResponse(Trigger.TIMER, Response.WIPE)
                    Log.w(TAG, "Timer due — ${settings.timerGraceMs / 1000}s grace (wipe=$wipe)")
                    showWarning(context, settings.timerGraceMs, wipe)
                    val am = context.getSystemService(AlarmManager::class.java)
                    scheduleAlarm(context, am, RC_GRACE, PHASE_GRACE, now + settings.timerGraceMs)
                } else {
                    reschedule(context) // used since arming (dead-man) — push out
                }
            }
            PHASE_GRACE -> {
                cancelWarning(context)
                val abort = settings.timerMode == TimerMode.DEADMAN && !due
                if (abort) {
                    Log.i(TAG, "Unlocked during grace — aborted")
                    reschedule(context)
                } else {
                    Log.w(TAG, "Grace elapsed — firing timer responses")
                    ResponseCoordinator.fire(context, Trigger.TIMER.source)
                    // One-shot: disable so a non-wipe timer can't loop. Re-enable to re-arm.
                    settings.timerEnabled = false
                    settings.timerArmedAt = 0L
                    val am = context.getSystemService(AlarmManager::class.java)
                    cancelAlarm(context, am, RC_MAIN, PHASE_MAIN)
                }
            }
        }
    }

    // --- alarms ---

    private fun alarmIntent(context: Context, phase: String) =
        Intent(context, InactivityReceiver::class.java).putExtra(EXTRA_PHASE, phase)

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

    private fun showWarning(context: Context, graceMs: Long, wipe: Boolean) {
        val mgr = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mgr.createNotificationChannel(
                NotificationChannel(WARN_CHANNEL, "Security warnings", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val minutes = (graceMs / 60000L).coerceAtLeast(1)
        val title = context.getString(if (wipe) R.string.inactivity_warn_title else R.string.timer_warn_title)
        val body = context.getString(
            if (wipe) R.string.inactivity_warn_body else R.string.timer_warn_body, minutes
        )
        val notif = NotificationCompat.Builder(context, WARN_CHANNEL)
            .setContentTitle(title)
            .setContentText(body)
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
