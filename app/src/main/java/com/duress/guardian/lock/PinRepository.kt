package com.duress.guardian.lock

import android.content.Context
import com.duress.guardian.core.CryptoBox
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Stores salted PBKDF2 hashes of the real PIN and the duress PIN — never the PINs themselves — and
 * additionally encrypts those hashes at rest with a hardware-bound Android Keystore key ([CryptoBox]),
 * so they cannot be lifted off a rooted/imaged device and brute-forced offline. Verification checks
 * both PINs regardless of which one matches, to avoid leaking via timing which PIN was entered.
 */
class PinRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isConfigured(): Boolean =
        prefs.contains(KEY_REAL_HASH) && prefs.contains(KEY_DURESS_HASH)

    /** Wipes stored PIN hashes so the setup flow can run again (useful for testing). */
    fun clear() {
        prefs.edit().clear().apply()
    }

    fun setPins(realPin: String, duressPin: String) {
        require(realPin != duressPin) { "Real and duress PIN must differ" }
        val realSalt = newSalt()
        val duressSalt = newSalt()
        prefs.edit()
            .putString(KEY_REAL_SALT, enc(realSalt))
            .putString(KEY_REAL_HASH, enc(hash(realPin, realSalt)))
            .putString(KEY_DURESS_SALT, enc(duressSalt))
            .putString(KEY_DURESS_HASH, enc(hash(duressPin, duressSalt)))
            .apply()
    }

    fun verify(pin: String): PinResult {
        val realSalt = prefs.getString(KEY_REAL_SALT, null)?.let(::dec)
        val realHash = prefs.getString(KEY_REAL_HASH, null)?.let(::dec)
        val duressSalt = prefs.getString(KEY_DURESS_SALT, null)?.let(::dec)
        val duressHash = prefs.getString(KEY_DURESS_HASH, null)?.let(::dec)

        val matchesReal = realSalt != null && realHash != null &&
            hash(pin, realSalt).contentEquals(realHash)
        val matchesDuress = duressSalt != null && duressHash != null &&
            hash(pin, duressSalt).contentEquals(duressHash)

        return when {
            matchesReal -> PinResult.REAL
            matchesDuress -> PinResult.DURESS
            else -> PinResult.INVALID
        }
    }

    private fun newSalt(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

    private fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun enc(b: ByteArray): String = CryptoBox.encrypt(b)
    private fun dec(s: String): ByteArray? = runCatching { CryptoBox.decrypt(s) }.getOrNull()

    companion object {
        private const val PREFS = "guardian_lock"
        private const val KEY_REAL_HASH = "real_hash"
        private const val KEY_REAL_SALT = "real_salt"
        private const val KEY_DURESS_HASH = "duress_hash"
        private const val KEY_DURESS_SALT = "duress_salt"
        private const val ITERATIONS = 120_000
        private const val KEY_LENGTH_BITS = 256
    }
}
