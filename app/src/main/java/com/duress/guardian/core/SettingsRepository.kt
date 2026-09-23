package com.duress.guardian.core

import android.content.Context

/**
 * Live, user-editable settings backed by SharedPreferences. Every consumer (trigger service,
 * response coordinator, QS tile) reads through here, so changes made in the Settings screen take
 * effect without restarting the app. Defaults and bounds come from [DuressConfig].
 */
class SettingsRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // --- Hardware-button trigger ---

    var triggerPressCount: Int
        get() = prefs.getInt(KEY_PRESS_COUNT, DuressConfig.DEFAULT_TRIGGER_PRESS_COUNT)
            .coerceIn(DuressConfig.PRESS_COUNT_MIN, DuressConfig.PRESS_COUNT_MAX)
        set(value) = prefs.edit().putInt(KEY_PRESS_COUNT, value).apply()

    var triggerWindowMs: Long
        get() = prefs.getLong(KEY_WINDOW_MS, DuressConfig.DEFAULT_TRIGGER_WINDOW_MS)
        set(value) = prefs.edit().putLong(KEY_WINDOW_MS, value).apply()

    var hardwareTriggerEnabled: Boolean
        get() = prefs.getBoolean(KEY_HW_TRIGGER, DuressConfig.DEFAULT_HARDWARE_TRIGGER_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_HW_TRIGGER, value).apply()

    var qsTileEnabled: Boolean
        get() = prefs.getBoolean(KEY_QS_TILE, DuressConfig.DEFAULT_QS_TILE_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_QS_TILE, value).apply()

    // --- Response actions ---

    var alertEnabled: Boolean
        get() = prefs.getBoolean(KEY_ALERT, DuressConfig.DEFAULT_ALERT_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_ALERT, value).apply()

    var captureEnabled: Boolean
        get() = prefs.getBoolean(KEY_CAPTURE, DuressConfig.DEFAULT_CAPTURE_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_CAPTURE, value).apply()

    var wipeEnabled: Boolean
        get() = prefs.getBoolean(KEY_WIPE, DuressConfig.DEFAULT_WIPE_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_WIPE, value).apply()

    // --- Alert delivery ---

    /** Emergency contact phone number the covert alert is sent to. Empty = not configured. */
    var alertContact: String
        get() = prefs.getString(KEY_ALERT_CONTACT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ALERT_CONTACT, value.trim()).apply()

    /** Message body sent with the location. */
    var alertMessage: String
        get() = prefs.getString(KEY_ALERT_MESSAGE, null) ?: DuressConfig.DEFAULT_ALERT_MESSAGE
        set(value) = prefs.edit().putString(KEY_ALERT_MESSAGE, value).apply()

    companion object {
        private const val PREFS = "guardian_settings"
        private const val KEY_PRESS_COUNT = "trigger_press_count"
        private const val KEY_WINDOW_MS = "trigger_window_ms"
        private const val KEY_HW_TRIGGER = "hardware_trigger_enabled"
        private const val KEY_QS_TILE = "qs_tile_enabled"
        private const val KEY_ALERT = "alert_enabled"
        private const val KEY_CAPTURE = "capture_enabled"
        private const val KEY_WIPE = "wipe_enabled"
        private const val KEY_ALERT_CONTACT = "alert_contact"
        private const val KEY_ALERT_MESSAGE = "alert_message"
    }
}
