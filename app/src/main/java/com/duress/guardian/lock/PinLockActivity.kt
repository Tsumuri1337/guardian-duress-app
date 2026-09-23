package com.duress.guardian.lock

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.duress.guardian.core.ResponseCoordinator
import com.duress.guardian.databinding.ActivityPinLockBinding
import com.duress.guardian.vault.VaultActivity
import com.duress.guardian.watchdog.InactivityWatchdog

/**
 * App-level lock screen with two PINs:
 *   - real PIN   -> opens the real vault
 *   - duress PIN -> silently fires the duress response, then opens the decoy vault
 *
 * This protects data THIS app holds. It is not the system lockscreen: a stock, unrooted app
 * cannot intercept the real device PIN, so the duress entry point has to live inside the app.
 */
class PinLockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPinLockBinding
    private lateinit var pins: PinRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPinLockBinding.inflate(layoutInflater)
        setContentView(binding.root)
        pins = PinRepository(this)

        // First run: no PINs set yet -> send the user to the setup flow to choose their own.
        if (!pins.isConfigured()) {
            startActivity(Intent(this, PinSetupActivity::class.java))
            finish()
            return
        }

        binding.submit.setOnClickListener {
            when (pins.verify(binding.pinInput.text.toString())) {
                PinResult.REAL -> {
                    // A real unlock counts as activity — reset the inactivity dead-man's switch.
                    InactivityWatchdog.onUnlock(applicationContext)
                    openVault(decoy = false)
                }
                PinResult.DURESS -> {
                    // Fire BEFORE opening the decoy, so the response runs even if the phone is
                    // taken the instant the PIN is entered.
                    ResponseCoordinator.fire(applicationContext, source = "decoy-pin")
                    openVault(decoy = true)
                }
                PinResult.INVALID -> {
                    binding.pinInput.text?.clear()
                    Toast.makeText(this, "Wrong PIN", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openVault(decoy: Boolean) {
        binding.pinInput.text?.clear()
        VaultActivity.start(this, decoy)
        finish()
    }
}
