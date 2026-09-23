package com.duress.guardian.core

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.duress.guardian.R
import com.duress.guardian.databinding.ActivitySettingsBinding
import com.duress.guardian.watchdog.InactivityWatchdog

/**
 * In-app settings for the trigger and response policy, plus alert delivery (contact + message).
 * Reads current values from [SettingsRepository], validates edits against the bounds in
 * [DuressConfig], writes them back, and requests the runtime permissions the alert needs — here,
 * because a duress trigger can't show a permission dialog. Reached only after a real unlock.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settings: SettingsRepository

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            // Regardless of the grant result, the settings are already saved. At fire-time the
            // sender logs and skips anything still missing, so we just confirm and close.
            Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settings = SettingsRepository(this)

        binding.swHardware.isChecked = settings.hardwareTriggerEnabled
        binding.etPressCount.setText(settings.triggerPressCount.toString())
        binding.etWindow.setText((settings.triggerWindowMs / 1000L).toString())
        binding.swQsTile.isChecked = settings.qsTileEnabled
        binding.swAlert.isChecked = settings.alertEnabled
        binding.swCapture.isChecked = settings.captureEnabled
        binding.swWipe.isChecked = settings.wipeEnabled
        binding.etContact.setText(settings.alertContact)
        binding.etMessage.setText(settings.alertMessage)
        binding.swInactivity.isChecked = settings.inactivityWipeEnabled
        binding.etInactivityHours.setText(settings.inactivityHours.toString())

        binding.save.setOnClickListener { onSave() }
    }

    private fun onSave() {
        val count = binding.etPressCount.text.toString().toIntOrNull()
        val windowSec = binding.etWindow.text.toString().toIntOrNull()
        val contact = binding.etContact.text.toString().trim()
        val alertOn = binding.swAlert.isChecked

        if (count == null || count !in DuressConfig.PRESS_COUNT_MIN..DuressConfig.PRESS_COUNT_MAX) {
            return showError(getString(R.string.err_press_count, DuressConfig.PRESS_COUNT_MIN, DuressConfig.PRESS_COUNT_MAX))
        }
        if (windowSec == null || windowSec !in DuressConfig.WINDOW_SECONDS_MIN..DuressConfig.WINDOW_SECONDS_MAX) {
            return showError(getString(R.string.err_window, DuressConfig.WINDOW_SECONDS_MIN, DuressConfig.WINDOW_SECONDS_MAX))
        }
        if (alertOn && contact.isBlank()) {
            return showError(getString(R.string.err_contact_needed))
        }
        val inactivityOn = binding.swInactivity.isChecked
        val inactivityHours = binding.etInactivityHours.text.toString().toIntOrNull()
        if (inactivityOn && (inactivityHours == null ||
                inactivityHours !in DuressConfig.INACTIVITY_MIN_HOURS..DuressConfig.INACTIVITY_MAX_HOURS)) {
            return showError(getString(R.string.err_inactivity_hours, DuressConfig.INACTIVITY_MIN_HOURS, DuressConfig.INACTIVITY_MAX_HOURS))
        }

        settings.hardwareTriggerEnabled = binding.swHardware.isChecked
        settings.triggerPressCount = count
        settings.triggerWindowMs = windowSec * 1000L
        settings.qsTileEnabled = binding.swQsTile.isChecked
        settings.alertEnabled = alertOn
        settings.captureEnabled = binding.swCapture.isChecked
        settings.wipeEnabled = binding.swWipe.isChecked
        settings.alertContact = contact
        settings.alertMessage = binding.etMessage.text.toString()
        settings.inactivityWipeEnabled = inactivityOn
        if (inactivityHours != null) settings.inactivityHours = inactivityHours
        // Re-arm (or cancel) the dead-man's switch to match the new settings.
        InactivityWatchdog.reschedule(this)

        binding.error.visibility = View.GONE

        // If the alert is on but its permissions aren't granted yet, ask now (the only safe time).
        val needed = if (alertOn) missingAlertPermissions() else emptyList()
        if (needed.isNotEmpty()) {
            Toast.makeText(this, R.string.perm_rationale, Toast.LENGTH_LONG).show()
            permissionLauncher.launch(needed.toTypedArray())
        } else {
            Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun missingAlertPermissions(): List<String> =
        listOf(Manifest.permission.SEND_SMS, Manifest.permission.ACCESS_FINE_LOCATION)
            .filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }

    private fun showError(msg: String) {
        binding.error.text = msg
        binding.error.visibility = View.VISIBLE
    }
}
