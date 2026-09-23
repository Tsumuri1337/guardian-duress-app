package com.duress.guardian.core

import android.content.Context
import android.util.Log
import com.duress.guardian.admin.WipeController
import com.duress.guardian.response.AlertSender
import com.duress.guardian.response.EvidenceCapture

/**
 * Single entry point for "a duress trigger fired". Every trigger source — decoy PIN, hardware
 * button pattern, Quick Settings tile — funnels through here, so the response policy lives in
 * exactly one place and is easy to audit. Which actions fire is read live from [SettingsRepository].
 */
object ResponseCoordinator {

    private const val TAG = "ResponseCoordinator"

    fun fire(context: Context, source: String) {
        val settings = SettingsRepository(context)
        Log.i(TAG, "Duress trigger fired from: $source")

        if (settings.alertEnabled) {
            AlertSender.send(context, source)
        }
        if (settings.captureEnabled) {
            EvidenceCapture.start(context)
        }
        if (settings.wipeEnabled) {
            val wiped = WipeController(context).wipe()
            Log.i(TAG, "wipe requested, executed=$wiped")
        }
    }
}
