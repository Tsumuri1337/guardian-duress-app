package com.duress.guardian.response

import android.content.Context
import android.util.Log

/**
 * STUB — Phase 2. Captures timestamped evidence (audio and/or periodic photos) to encrypted local
 * storage, optionally uploaded.
 *
 * Planned implementation: a foreground service declaring the `microphone` / `camera` FGS types.
 *
 * Platform limitation to document, not fight: on Android 15 a microphone/camera privacy indicator
 * is shown while capturing and CANNOT be suppressed on a stock device. This is an OS guarantee, so
 * "capture" here is overt-to-the-OS-indicators, covert-to-normal-UI.
 */
object EvidenceCapture {
    fun start(context: Context) {
        Log.i("EvidenceCapture", "TODO: start evidence capture")
    }
}
