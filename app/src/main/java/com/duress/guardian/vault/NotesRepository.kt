package com.duress.guardian.vault

import android.content.Context
import org.json.JSONArray

/**
 * Stores two completely separate note lists: the real vault and the decoy vault. The decoy list is
 * seeded with innocuous, plausible entries the first time it is opened, so a coerced unlock shows a
 * believable, ordinary notes app — never an empty or obviously-fake screen.
 *
 * The two lists never mix: real notes are only ever read/written in real mode, decoy notes only in
 * decoy mode, so someone holding the phone under the duress PIN can't see or reach the real data.
 */
class NotesRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getNotes(decoy: Boolean): List<String> {
        val key = keyFor(decoy)
        if (decoy && !prefs.contains(key)) {
            saveNotes(decoy = true, notes = DECOY_SEED)
            return DECOY_SEED
        }
        val raw = prefs.getString(key, null) ?: return emptyList()
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { arr.getString(it) }
    }

    fun addNote(decoy: Boolean, text: String) {
        val updated = getNotes(decoy) + text
        saveNotes(decoy, updated)
    }

    private fun saveNotes(decoy: Boolean, notes: List<String>) {
        val arr = JSONArray()
        notes.forEach { arr.put(it) }
        prefs.edit().putString(keyFor(decoy), arr.toString()).apply()
    }

    private fun keyFor(decoy: Boolean) = if (decoy) KEY_DECOY else KEY_REAL

    companion object {
        private const val PREFS = "guardian_notes"
        private const val KEY_REAL = "real_notes"
        private const val KEY_DECOY = "decoy_notes"

        // Plausible, mundane decoy content.
        private val DECOY_SEED = listOf(
            "Groceries: milk, eggs, bread, coffee",
            "Call dentist to reschedule Thursday",
            "Mom's birthday gift ideas",
            "Wifi: guest network password is on the router",
            "Return library books before the 15th"
        )
    }
}
