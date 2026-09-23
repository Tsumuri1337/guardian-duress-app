package com.duress.guardian.watchdog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InactivityPolicyTest {

    private val hour = 60L * 60L * 1000L

    @Test fun notExpired_wellWithinThreshold() {
        assertFalse(InactivityPolicy.isExpired(nowMs = 10 * hour, lastUnlockAtMs = 9 * hour, thresholdMs = 3 * hour))
    }

    @Test fun expired_exactlyAtThreshold() {
        assertTrue(InactivityPolicy.isExpired(nowMs = 12 * hour, lastUnlockAtMs = 9 * hour, thresholdMs = 3 * hour))
    }

    @Test fun expired_pastThreshold() {
        assertTrue(InactivityPolicy.isExpired(nowMs = 100 * hour, lastUnlockAtMs = 9 * hour, thresholdMs = 3 * hour))
    }

    @Test fun neverExpires_whenNoUnlockRecorded() {
        // lastUnlockAt == 0 means "never seen an unlock" — must not fire.
        assertFalse(InactivityPolicy.isExpired(nowMs = 1_000 * hour, lastUnlockAtMs = 0L, thresholdMs = hour))
    }

    @Test fun deadline_isLastUnlockPlusThreshold() {
        assertEquals(9 * hour + 3 * hour, InactivityPolicy.deadline(lastUnlockAtMs = 9 * hour, thresholdMs = 3 * hour))
    }
}
