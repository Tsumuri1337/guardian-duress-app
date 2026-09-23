package com.duress.guardian.response

import android.content.Context
import android.util.Log

/**
 * Starts covert evidence capture on a duress trigger. Currently records a short audio clip via
 * [CaptureService] to app-internal storage. (Periodic photo capture via Camera2 is a planned
 * addition; audio is the highest-value, most reliable evidence and ships first.)
 *
 * Note: Android 15 shows a microphone privacy indicator while recording — it cannot be suppressed
 * on a stock device, so capture is covert to ordinary UI, not to the OS indicators.
 */
object EvidenceCapture {
    fun start(context: Context) {
        Log.i("EvidenceCapture", "Starting evidence capture")
        CaptureService.start(context)
    }
}
