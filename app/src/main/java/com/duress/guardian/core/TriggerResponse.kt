package com.duress.guardian.core

/**
 * The trigger sources a duress response can come from, and the responses each can fire.
 * Each trigger independently selects which responses it performs (see [SettingsRepository]).
 * [source] is the string tag passed to `ResponseCoordinator.fire(...)`.
 */
enum class Trigger(val key: String, val source: String) {
    HARDWARE("hw", "hardware-button"),
    QS_TILE("qs", "qs-tile"),
    DECOY_PIN("pin", "decoy-pin"),
    TIMER("tm", "timer");

    companion object {
        fun fromSource(source: String): Trigger? = entries.firstOrNull { it.source == source }
    }
}

enum class Response { ALERT, CAPTURE, WIPE }

/**
 * How the timed action counts down.
 *  - DEADMAN: fires after the duration of *inactivity*; any unlock resets it.
 *  - FIXED:   fires after the duration of *real time* from when it was armed, regardless of use;
 *             unlocking does not reset it.
 */
enum class TimerMode { DEADMAN, FIXED }

