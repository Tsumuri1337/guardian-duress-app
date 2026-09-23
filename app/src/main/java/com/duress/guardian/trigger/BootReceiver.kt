package com.duress.guardian.trigger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.duress.guardian.watchdog.InactivityWatchdog

/** Restarts the trigger listener and re-arms the inactivity timer after reboot (alarms clear on boot). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            TriggerService.start(context)
            InactivityWatchdog.reschedule(context)
        }
    }
}
