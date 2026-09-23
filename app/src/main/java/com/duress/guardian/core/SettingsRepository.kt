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
    }

    // --- Per-trigger responses ---
    // Each (trigger, response) pair is its own flag. Alert/Capture default on, Wipe defaults off.

    fun getResponse(t: Trigger, r: Response): Boolean {
        val default = r != Response.WIPE
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

    // --- Inactivity auto-wipe (dead-man's switch) ---

    var inactivityWipeEnabled: Boolean
        get() = prefs.getBoolean(KEY_INACTIVITY_ENABLED, DuressConfig.DEFAULT_INACTIVITY_WIPE_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_INACTIVITY_ENABLED, value).apply()

    var inactivityHours: Int
        get() = prefs.getInt(KEY_INACTIVITY_HOURS, DuressConfig.DEFAULT_INACTIVITY_HOURS)
            .coerceIn(DuressConfig.INACTIVITY_MIN_HOURS, DuressConfig.INACTIVITY_MAX_HOURS)
        set(value) = prefs.edit().putInt(KEY_INACTIVITY_HOURS, value).apply()

    /** Grace window (ms) after expiry before the wipe; stored so tests can shorten it. */
    var inactivityGraceMs: Long
        get() = prefs.getLong(KEY_INACTIVITY_GRACE_MS, DuressConfig.DEFAULT_INACTIVITY_GRACE_MS)
        set(value) = prefs.edit().putLong(KEY_INACTIVITY_GRACE_MS, value).apply()

    /** Threshold in milliseconds derived from [inactivityHours]. */
    val inactivityThresholdMs: Long
        get() = inactivityHours.toLong() * 60L * 60L * 1000L

    /** Epoch millis of the last device/app unlock; 0 = never recorded. */
    var lastUnlockAt: Long
        get() = prefs.getLong(KEY_LAST_UNLOCK, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_UNLOCK, value).apply()

    fun recordUnlock() {
        lastUnlockAt = System.currentTimeMillis()
    }

    companion object {
        private const val PREFS = "guardian_settings"
        private const val KEY_PRESS_COUNT = "trigger_press_count"
        private const val KEY_WINDOW_MS = "trigger_window_ms"
        private const val KEY_HW_TRIGGER = "hardware_trigger_enabled"
        private const val KEY_QS_TILE = "qs_tile_enabled"
        private const val KEY_PIN_TRIGGER = "decoy_pin_enabled"
        private const val KEY_ALERT_CONTACT = "alert_contact"
        private const val KEY_ALERT_MESSAGE = "alert_message"
        private const val KEY_INACTIVITY_ENABLED = "inactivity_enabled"
        private const val KEY_INACTIVITY_HOURS = "inactivity_hours"
        private const val KEY_INACTIVITY_GRACE_MS = "inactivity_grace_ms"
        private const val KEY_LAST_UNLOCK = "last_unlock_at"
    }
}
