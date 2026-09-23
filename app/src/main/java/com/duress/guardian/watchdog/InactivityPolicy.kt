package com.duress.guardian.watchdog

/**
 * Pure decision logic for the inactivity auto-wipe, with no Android dependencies so it can be
 * unit-tested directly. [InactivityWatchdog] does the scheduling and side effects around it.
 */
object InactivityPolicy {

    /**
     * True once the device has gone untouched for at least [thresholdMs] since [lastUnlockAtMs].
     * A [lastUnlockAtMs] of 0 (never recorded) is treated as "not expired" so the switch never
     * fires before we've observed a single unlock.
     */
    fun isExpired(nowMs: Long, lastUnlockAtMs: Long, thresholdMs: Long): Boolean =
        lastUnlockAtMs > 0L && (nowMs - lastUnlockAtMs) >= thresholdMs

    /** Absolute epoch-ms time the inactivity timer next expires. */
    fun deadline(lastUnlockAtMs: Long, thresholdMs: Long): Long = lastUnlockAtMs + thresholdMs
}
