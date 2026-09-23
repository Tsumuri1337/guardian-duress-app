package com.duress.guardian.watchdog

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Receives the inactivity AlarmManager callbacks and hands them to [InactivityWatchdog]. */
class InactivityReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val phase = intent.getStringExtra("phase") ?: return
        InactivityWatchdog.onAlarm(context, phase)
    }
}
