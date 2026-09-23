package com.duress.guardian.admin

import android.app.admin.DeviceAdminReceiver

/**
 * Device admin component. Referenced by res/xml/device_admin.xml and required as the target of
 * Device Owner provisioning:
 *
 *   adb shell dpm set-device-owner com.duress.guardian/.admin.DuressDeviceAdminReceiver
 *
 * Kept intentionally minimal — the wipe logic lives in [WipeController].
 */
class DuressDeviceAdminReceiver : DeviceAdminReceiver()
