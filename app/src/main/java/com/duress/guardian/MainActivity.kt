package com.duress.guardian

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.duress.guardian.lock.PinLockActivity
import com.duress.guardian.trigger.TriggerService
import com.duress.guardian.watchdog.InactivityWatchdog

/**
 * Launcher entry point. Starts the background trigger listener and hands off to the PIN lock.
 * Has no UI of its own — it finishes immediately after routing to [PinLockActivity].
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TriggerService.start(this)
        InactivityWatchdog.reschedule(this)
        startActivity(Intent(this, PinLockActivity::class.java))
        finish()
    }
}
