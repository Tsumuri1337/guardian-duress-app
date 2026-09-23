package com.duress.guardian.lock

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.duress.guardian.R
import com.duress.guardian.core.DuressConfig
import com.duress.guardian.core.SettingsRepository
import com.duress.guardian.databinding.ActivityPinSetupBinding

/**
 * First-run (and "change PINs") setup. The user chooses their own real and duress PINs, subject to
 * the length policy in [DuressConfig]. Nothing is hardcoded — the demo PINs are gone.
 */
class PinSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPinSetupBinding
    private lateinit var pins: PinRepository

    // After the runtime-permission dialogs finish, follow up with the battery-optimization request.
    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            requestBatteryExemptionIfNeeded()
        }

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

        maybeRequestFirstRunPermissions()
    }

    /** On genuine first launch, request every permission the app may need, in one pass. */
    private fun maybeRequestFirstRunPermissions() {
        val settings = SettingsRepository(this)
        if (settings.permissionsRequested) return
        settings.permissionsRequested = true

        val toRequest = neededRuntimePermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (toRequest.isNotEmpty()) {
            permLauncher.launch(toRequest.toTypedArray())   // battery request follows in the callback
        } else {
            requestBatteryExemptionIfNeeded()
        }
    }

    private fun neededRuntimePermissions(): List<String> {
        val list = mutableListOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Note: RECORD_AUDIO / CAMERA are requested with EvidenceCapture once that ships.
        return list
    }

    /** Ask the OS to exempt the app from battery optimization — vital on OxygenOS for persistence. */
    private fun requestBatteryExemptionIfNeeded() {
        val pm = getSystemService(PowerManager::class.java) ?: return
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        try {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (e: Exception) {
            // Some OEMs block this intent; the user can grant it manually in system settings.
        }
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
