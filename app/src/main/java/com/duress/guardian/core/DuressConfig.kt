package com.duress.guardian.core

/**
 * Compile-time DEFAULTS and hard bounds. The live, user-editable values are held by
 * [SettingsRepository]; these are only the factory defaults and the validation limits.
 *
 * SAFETY: the wipe default is false so a fresh install can never factory-reset the device until
 * the user deliberately turns it on in Settings (and the app is Device Owner).
 */
object DuressConfig {

    // PIN policy
    const val PIN_MIN_LENGTH = 4
    const val PIN_MAX_LENGTH = 12

    // Hardware-button trigger defaults (screen on/off toggles = power-button presses)
    const val DEFAULT_TRIGGER_PRESS_COUNT = 5
    const val DEFAULT_TRIGGER_WINDOW_MS = 3000L

    // Bounds for the configurable press count / window
    const val PRESS_COUNT_MIN = 2
    const val PRESS_COUNT_MAX = 10
    const val WINDOW_SECONDS_MIN = 1
    const val WINDOW_SECONDS_MAX = 10

    // Which trigger sources are active by default
    const val DEFAULT_HARDWARE_TRIGGER_ENABLED = true
    const val DEFAULT_QS_TILE_ENABLED = true

    // Which response actions fire by default
    const val DEFAULT_ALERT_ENABLED = true
    const val DEFAULT_CAPTURE_ENABLED = true
    const val DEFAULT_WIPE_ENABLED = false

    // Default covert-alert message. "%s" is replaced with a Google Maps link to the last location.
    const val DEFAULT_ALERT_MESSAGE = "I am in danger and need help. My location: %s"
}
