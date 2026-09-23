package com.duress.guardian.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.util.Log

/**
 * Wraps DevicePolicyManager for the factory-reset (wipe) capability.
 *
 * On stock, unrooted Android, wipeData() only succeeds if this app is a Device Owner, provisioned
 * on a freshly reset device with no accounts via:
 *
 *   adb shell dpm set-device-owner com.duress.guardian/.admin.DuressDeviceAdminReceiver
 *
 * Otherwise the call is refused. [wipe] fails safe (returns false) rather than throwing, so the
 * rest of the duress response still runs during development when the app is not Device Owner.
 */
class WipeController(private val context: Context) {

    private val dpm =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin = ComponentName(context, DuressDeviceAdminReceiver::class.java)

    fun isDeviceOwner(): Boolean = dpm.isDeviceOwnerApp(context.packageName)

    /**
     * DESTRUCTIVE: factory-resets the device. Guarded by Device Owner status.
     * @return true if the wipe was issued; false if not permitted.
     */
    fun wipe(): Boolean {
        if (!isDeviceOwner()) {
            Log.w(TAG, "wipe() called but app is not Device Owner — ignoring")
            return false
        }
        return try {
            // WIPE_RESET_PROTECTION_DATA clears Factory Reset Protection as part of the wipe, so the
            // device comes back account-free and immediately re-provisionable for the next test.
            dpm.wipeData(DevicePolicyManager.WIPE_RESET_PROTECTION_DATA)
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "wipeData denied", e)
            false
        }
    }

    companion object {
        private const val TAG = "WipeController"
    }
}
