package com.duress.guardian.lock

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.duress.guardian.R
import com.duress.guardian.core.DuressConfig
import com.duress.guardian.databinding.ActivityPinSetupBinding

/**
 * First-run (and "change PINs") setup. The user chooses their own real and duress PINs, subject to
 * the length policy in [DuressConfig]. Nothing is hardcoded — the demo PINs are gone.
 */
class PinSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPinSetupBinding
    private lateinit var pins: PinRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPinSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        pins = PinRepository(this)

        binding.rules.text = getString(
            R.string.pin_rules,
            DuressConfig.PIN_MIN_LENGTH,
            DuressConfig.PIN_MAX_LENGTH
        )

        binding.save.setOnClickListener { onSave() }
    }

    private fun onSave() {
        val real = binding.realPin.text.toString()
        val realConfirm = binding.realPinConfirm.text.toString()
        val duress = binding.duressPin.text.toString()
        val duressConfirm = binding.duressPinConfirm.text.toString()

        val error = validate(real, realConfirm, duress, duressConfirm)
        if (error != null) {
            binding.error.text = error
            binding.error.visibility = View.VISIBLE
            return
        }

        pins.setPins(realPin = real, duressPin = duress)
        Toast.makeText(this, R.string.pins_saved, Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, PinLockActivity::class.java))
        finish()
    }

    /** @return an error message to show, or null if the input is valid. */
    private fun validate(
        real: String,
        realConfirm: String,
        duress: String,
        duressConfirm: String
    ): String? {
        val min = DuressConfig.PIN_MIN_LENGTH
        val max = DuressConfig.PIN_MAX_LENGTH

        if (real.length !in min..max || duress.length !in min..max) {
            return getString(R.string.pin_length_error, min, max)
        }
        if (real != realConfirm) return getString(R.string.pin_real_mismatch)
        if (duress != duressConfirm) return getString(R.string.pin_duress_mismatch)
        if (real == duress) return getString(R.string.pin_must_differ)
        return null
    }
}
