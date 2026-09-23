package com.duress.guardian.trigger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the trigger listener after reboot so the duress detector survives restarts. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            TriggerService.start(context)
        }
    }
}
