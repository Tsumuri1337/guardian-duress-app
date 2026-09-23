package com.duress.guardian.core

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.duress.guardian.R
import com.duress.guardian.databinding.ActivitySettingsBinding
import com.duress.guardian.databinding.DialogAlertBinding
import com.duress.guardian.databinding.DialogHardwareBinding
import com.duress.guardian.databinding.DialogResponsesBinding
import com.duress.guardian.databinding.DialogTimerBinding
import com.duress.guardian.watchdog.InactivityWatchdog

/**
 * Settings as a compact list of feature toggles. Toggling a feature on reveals a "Configure" button
 * that opens a dialog for that feature. Each trigger independently selects which responses it fires
 * (alert / capture / wipe), so e.g. the hardware button can be set to wipe.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settings: SettingsRepository

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settings = SettingsRepository(this)

        // --- toggles wire enabled-state + Configure visibility ---
        bindToggle(binding.swHardware, binding.btnConfigHardware, settings.hardwareTriggerEnabled) {
            settings.hardwareTriggerEnabled = it
        }
        bindToggle(binding.swQsTile, binding.btnConfigQs, settings.qsTileEnabled) {
            settings.qsTileEnabled = it
        }
        bindToggle(binding.swDecoy, binding.btnConfigDecoy, settings.decoyPinEnabled) {
            settings.decoyPinEnabled = it
        }
        bindToggle(binding.swInactivity, binding.btnConfigInactivity, settings.timerEnabled) {
            settings.timerEnabled = it
            if (it) settings.timerArmedAt = 0L   // arm fresh on enable
            InactivityWatchdog.reschedule(this)
        }

        // --- Configure buttons open dialogs ---
        binding.btnConfigHardware.setOnClickListener { configureHardware() }
        binding.btnConfigQs.setOnClickListener { configureResponses(Trigger.QS_TILE, R.string.section_tile) }
        binding.btnConfigDecoy.setOnClickListener { configureResponses(Trigger.DECOY_PIN, R.string.section_decoy) }
        binding.btnConfigAlert.setOnClickListener { configureAlert() }
        binding.btnConfigInactivity.setOnClickListener { configureTimer() }
    }

    private fun bindToggle(
        sw: com.google.android.material.switchmaterial.SwitchMaterial,
        configBtn: View,
        initial: Boolean,
        onChange: (Boolean) -> Unit
    ) {
        sw.isChecked = initial
        configBtn.visibility = if (initial) View.VISIBLE else View.GONE
        sw.setOnCheckedChangeListener { _, checked ->
            onChange(checked)
            configBtn.visibility = if (checked) View.VISIBLE else View.GONE
        }
    }

    // --- dialogs ---

    private fun configureHardware() {
        val d = DialogHardwareBinding.inflate(layoutInflater)
        d.etPressCount.setText(settings.triggerPressCount.toString())
        d.etWindow.setText((settings.triggerWindowMs / 1000L).toString())
        loadResponses(d.cbAlert, d.cbCapture, d.cbWipe, Trigger.HARDWARE)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.section_hardware)
            .setView(d.root)
            .setPositiveButton(R.string.save_short, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val count = d.etPressCount.text.toString().toIntOrNull()
                val windowSec = d.etWindow.text.toString().toIntOrNull()
                if (count == null || count !in DuressConfig.PRESS_COUNT_MIN..DuressConfig.PRESS_COUNT_MAX) {
                    toast(getString(R.string.err_press_count, DuressConfig.PRESS_COUNT_MIN, DuressConfig.PRESS_COUNT_MAX)); return@setOnClickListener
                }
                if (windowSec == null || windowSec !in DuressConfig.WINDOW_SECONDS_MIN..DuressConfig.WINDOW_SECONDS_MAX) {
                    toast(getString(R.string.err_window, DuressConfig.WINDOW_SECONDS_MIN, DuressConfig.WINDOW_SECONDS_MAX)); return@setOnClickListener
                }
                settings.triggerPressCount = count
                settings.triggerWindowMs = windowSec * 1000L
                saveResponses(d.cbAlert, d.cbCapture, d.cbWipe, Trigger.HARDWARE)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun configureResponses(trigger: Trigger, titleRes: Int) {
        val d = DialogResponsesBinding.inflate(layoutInflater)
        loadResponses(d.cbAlert, d.cbCapture, d.cbWipe, trigger)
        AlertDialog.Builder(this)
            .setTitle(titleRes)
            .setView(d.root)
            .setPositiveButton(R.string.save_short) { _, _ ->
                saveResponses(d.cbAlert, d.cbCapture, d.cbWipe, trigger)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun configureAlert() {
        val d = DialogAlertBinding.inflate(layoutInflater)
        d.etContact.setText(settings.alertContact)
        d.etMessage.setText(settings.alertMessage)
        AlertDialog.Builder(this)
            .setTitle(R.string.section_alert_delivery)
            .setView(d.root)
            .setPositiveButton(R.string.save_short) { _, _ ->
                settings.alertContact = d.etContact.text.toString().trim()
                settings.alertMessage = d.etMessage.text.toString()
                // If a contact is set, make sure the alert's permissions are granted.
                if (settings.alertContact.isNotBlank()) requestAlertPermissionsIfNeeded()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun configureTimer() {
        val d = DialogTimerBinding.inflate(layoutInflater)
        // mode
        if (settings.timerMode == TimerMode.FIXED) d.rbFixed.isChecked = true else d.rbDeadman.isChecked = true
        // duration -> d:h:m:s
        var s = settings.timerDurationMs / 1000L
        d.etDays.setText((s / 86400L).toString()); s %= 86400L
        d.etHours.setText((s / 3600L).toString()); s %= 3600L
        d.etMinutes.setText((s / 60L).toString())
        d.etSeconds.setText((s % 60L).toString())
        loadResponses(d.cbAlert, d.cbCapture, d.cbWipe, Trigger.TIMER)

        d.btnReset.setOnClickListener {
            d.etDays.text?.clear(); d.etHours.text?.clear(); d.etMinutes.text?.clear(); d.etSeconds.text?.clear()
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.timer_title)
            .setView(d.root)
            .setPositiveButton(R.string.save_short, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                fun v(cb: android.widget.EditText) = cb.text.toString().toLongOrNull() ?: 0L
                val durationMs = (((v(d.etDays) * 24 + v(d.etHours)) * 60 + v(d.etMinutes)) * 60 + v(d.etSeconds)) * 1000L
                if (durationMs !in DuressConfig.TIMER_MIN_MS..DuressConfig.TIMER_MAX_MS) {
                    toast(getString(R.string.err_timer_range)); return@setOnClickListener
                }
                settings.timerMode = if (d.rbFixed.isChecked) TimerMode.FIXED else TimerMode.DEADMAN
                settings.timerDurationMs = durationMs
                saveResponses(d.cbAlert, d.cbCapture, d.cbWipe, Trigger.TIMER)
                settings.timerArmedAt = 0L // re-arm fresh with the new settings
                InactivityWatchdog.reschedule(this)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    // --- helpers ---

    private fun loadResponses(alert: CheckBox, capture: CheckBox, wipe: CheckBox, t: Trigger) {
        alert.isChecked = settings.getResponse(t, Response.ALERT)
        capture.isChecked = settings.getResponse(t, Response.CAPTURE)
        wipe.isChecked = settings.getResponse(t, Response.WIPE)
    }

    private fun saveResponses(alert: CheckBox, capture: CheckBox, wipe: CheckBox, t: Trigger) {
        settings.setResponse(t, Response.ALERT, alert.isChecked)
        settings.setResponse(t, Response.CAPTURE, capture.isChecked)
        settings.setResponse(t, Response.WIPE, wipe.isChecked)
    }

    private fun requestAlertPermissionsIfNeeded() {
        val needed = listOf(Manifest.permission.SEND_SMS, Manifest.permission.ACCESS_FINE_LOCATION)
            .filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) {
            Toast.makeText(this, R.string.perm_rationale, Toast.LENGTH_LONG).show()
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
