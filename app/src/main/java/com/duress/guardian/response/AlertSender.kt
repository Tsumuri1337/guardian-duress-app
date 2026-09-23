package com.duress.guardian.response

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.duress.guardian.core.SettingsRepository

/**
 * Sends a covert alert (preset message + last-known location) to the configured emergency contact
 * over SMS. No visible UI by design.
 *
 * Delivery is intentionally isolated here: to switch to an HTTPS backend later, replace [deliver]
 * with an HTTP POST — nothing else changes. Location uses the platform [LocationManager] so we
 * avoid a Google Play Services dependency.
 *
 * Permissions (SEND_SMS, ACCESS_FINE_LOCATION) are requested up front in Settings, because a duress
 * trigger cannot show a permission dialog. If a permission is missing at fire-time we log and skip
 * rather than crash — failing safe.
 */
object AlertSender {

    private const val TAG = "AlertSender"

    fun send(context: Context, source: String) {
        val settings = SettingsRepository(context)
        val contact = settings.alertContact
        if (contact.isBlank()) {
            Log.w(TAG, "No emergency contact configured — skipping alert (source=$source)")
            return
        }

        val locationText = lastKnownLocationText(context)
        val body = formatMessage(settings.alertMessage, locationText)
        deliver(context, contact, body)
    }

    private fun formatMessage(template: String, locationText: String): String =
        if (template.contains("%s")) template.format(locationText)
        else "$template $locationText"

    /** A Google Maps link for the last known fix, or a "location unavailable" note. */
    private fun lastKnownLocationText(context: Context): String {
        if (!hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)) {
            Log.w(TAG, "No location permission — sending without coordinates")
            return "(location unavailable)"
        }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val best = bestLastKnown(lm)
        return if (best != null) {
            "https://maps.google.com/?q=${best.latitude},${best.longitude}"
        } else {
            "(location unavailable)"
        }
    }

    private fun bestLastKnown(lm: LocationManager): Location? {
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        var best: Location? = null
        for (p in providers) {
            val loc = try {
                @Suppress("MissingPermission") lm.getLastKnownLocation(p)
            } catch (e: SecurityException) {
                null
            }
            if (loc != null && (best == null || loc.time > best!!.time)) best = loc
        }
        return best
    }

    private fun deliver(context: Context, contact: String, body: String) {
        if (!hasPermission(context, Manifest.permission.SEND_SMS)) {
            Log.w(TAG, "No SEND_SMS permission — cannot deliver alert")
            return
        }
        try {
            val sms = smsManager(context)
            // Long messages are split so the location link is never truncated.
            val parts = sms.divideMessage(body)
            sms.sendMultipartTextMessage(contact, null, parts, null, null)
            Log.i(TAG, "Covert alert sent to $contact (${parts.size} part(s))")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send alert SMS", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun smsManager(context: Context): SmsManager =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            SmsManager.getDefault()
        }

    private fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
