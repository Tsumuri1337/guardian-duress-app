package com.duress.guardian.core

import android.content.Context
import android.util.Log
import com.duress.guardian.admin.WipeController
import com.duress.guardian.response.AlertSender
import com.duress.guardian.response.EvidenceCapture

/**
 * Single entry point for "a duress trigger fired". Every trigger source funnels through here.
 * Which responses run is decided per-trigger via [SettingsRepository.responsesFor], so each trigger
 * (hardware button, QS tile, decoy PIN) can independently fire alert / capture / wipe.
 */
object ResponseCoordinator {

    private const val TAG = "ResponseCoordinator"

    fun fire(context: Context, source: String) {
        val settings = SettingsRepository(context)
        val responses = settings.responsesFor(source)
        Log.i(TAG, "Duress trigger fired from: $source -> $responses")

        if (Response.ALERT in responses) {
            AlertSender.send(context, source)
        }
        if (Response.CAPTURE in responses) {
            EvidenceCapture.start(context)
        }
        if (Response.WIPE in responses) {
            val wiped = WipeController(context).wipe()
            Log.i(TAG, "wipe requested, executed=$wiped")
        }
    }
}
