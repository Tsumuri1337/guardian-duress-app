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

    /** Whether entering the duress PIN fires a response (it always opens the decoy vault). */
    var decoyPinEnabled: Boolean
        get() = prefs.getBoolean(KEY_PIN_TRIGGER, true)
        set(value) = prefs.edit().putBoolean(KEY_PIN_TRIGGER, value).apply()

    /** Whether a trigger source is enabled at all. */
    fun triggerEnabled(t: Trigger): Boolean = when (t) {
        Trigger.HARDWARE -> hardwareTriggerEnabled
        Trigger.QS_TILE -> qsTileEnabled
        Trigger.DECOY_PIN -> decoyPinEnabled
        Trigger.TIMER -> timerEnabled
    }

    // --- Per-trigger responses ---
    // Each (trigger, response) pair is its own flag. Alert/Capture default on, Wipe defaults off.

    fun getResponse(t: Trigger, r: Response): Boolean {
        // The timer defaults to wipe-only (its original purpose); other triggers default to
        // alert + capture on, wipe off.
        val default = if (t == Trigger.TIMER) r == Response.WIPE else r != Response.WIPE
        return prefs.getBoolean(responseKey(t, r), default)
    }

    fun setResponse(t: Trigger, r: Response, value: Boolean) {
        prefs.edit().putBoolean(responseKey(t, r), value).apply()
    }

    /** The responses configured for the given trigger source (empty if the trigger is disabled). */
    fun responsesFor(source: String): Set<Response> {
        val t = Trigger.fromSource(source) ?: return emptySet()
        if (!triggerEnabled(t)) return emptySet()
        return Response.entries.filter { getResponse(t, it) }.toSet()
    }

    private fun responseKey(t: Trigger, r: Response) = "resp_${t.key}_${r.name.lowercase()}"

    // --- Alert delivery ---

    /** Emergency contact phone number the covert alert is sent to. Empty = not configured. */
    var alertContact: String
        get() = prefs.getString(KEY_ALERT_CONTACT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ALERT_CONTACT, value.trim()).apply()

    /** Message body sent with the location. */
    var alertMessage: String
        get() = prefs.getString(KEY_ALERT_MESSAGE, null) ?: DuressConfig.DEFAULT_ALERT_MESSAGE
        set(value) = prefs.edit().putString(KEY_ALERT_MESSAGE, value).apply()

    // --- Timed action (dead-man's switch or fixed timer) ---

    var timerEnabled: Boolean
        get() = prefs.getBoolean(KEY_TIMER_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_TIMER_ENABLED, value).apply()

    /** Countdown length in milliseconds (entered as days:hours:minutes:seconds in the UI). */
    var timerDurationMs: Long
        get() = prefs.getLong(KEY_TIMER_DURATION_MS, DuressConfig.DEFAULT_TIMER_DURATION_MS)
            .coerceIn(DuressConfig.TIMER_MIN_MS, DuressConfig.TIMER_MAX_MS)
        set(value) = prefs.edit().putLong(KEY_TIMER_DURATION_MS, value).apply()

    var timerMode: TimerMode
        get() = runCatching { TimerMode.valueOf(prefs.getString(KEY_TIMER_MODE, null) ?: "") }
            .getOrDefault(TimerMode.DEADMAN)
        set(value) = prefs.edit().putString(KEY_TIMER_MODE, value.name).apply()

    /** When a FIXED timer was armed (epoch ms). The countdown runs from here regardless of use. */
    var timerArmedAt: Long
        get() = prefs.getLong(KEY_TIMER_ARMED_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_TIMER_ARMED_AT, value).apply()

    /** Grace window (ms) after expiry before firing; stored so tests can shorten it. */
    var timerGraceMs: Long
        get() = prefs.getLong(KEY_TIMER_GRACE_MS, DuressConfig.DEFAULT_TIMER_GRACE_MS)
        set(value) = prefs.edit().putLong(KEY_TIMER_GRACE_MS, value).apply()

    /** The anchor time the countdown is measured from, per mode. */
    fun timerAnchor(): Long = when (timerMode) {
        TimerMode.DEADMAN -> lastUnlockAt
        TimerMode.FIXED -> timerArmedAt
    }

    /** Epoch millis of the last device/app unlock; 0 = never recorded. */
    var lastUnlockAt: Long
        get() = prefs.getLong(KEY_LAST_UNLOCK, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_UNLOCK, value).apply()

    fun recordUnlock() {
        lastUnlockAt = System.currentTimeMillis()
    }

    /** True once the first-run permission request has been shown, so we don't nag on every launch. */
    var permissionsRequested: Boolean
        get() = prefs.getBoolean(KEY_PERMS_REQUESTED, false)
        set(value) = prefs.edit().putBoolean(KEY_PERMS_REQUESTED, value).apply()

    companion object {
        private const val PREFS = "guardian_settings"
        private const val KEY_PRESS_COUNT = "trigger_press_count"
        private const val KEY_WINDOW_MS = "trigger_window_ms"
        private const val KEY_HW_TRIGGER = "hardware_trigger_enabled"
        private const val KEY_QS_TILE = "qs_tile_enabled"
        private const val KEY_PIN_TRIGGER = "decoy_pin_enabled"
        private const val KEY_ALERT_CONTACT = "alert_contact"
        private const val KEY_ALERT_MESSAGE = "alert_message"
        private const val KEY_TIMER_ENABLED = "timer_enabled"
        private const val KEY_TIMER_DURATION_MS = "timer_duration_ms"
        private const val KEY_TIMER_MODE = "timer_mode"
        private const val KEY_TIMER_ARMED_AT = "timer_armed_at"
        private const val KEY_TIMER_GRACE_MS = "timer_grace_ms"
        private const val KEY_LAST_UNLOCK = "last_unlock_at"
        private const val KEY_PERMS_REQUESTED = "permissions_requested"
    }
}
