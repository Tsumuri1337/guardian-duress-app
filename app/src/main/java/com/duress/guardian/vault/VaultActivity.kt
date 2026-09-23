package com.duress.guardian.vault

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.duress.guardian.core.SettingsActivity
import com.duress.guardian.databinding.ActivityVaultBinding
import com.duress.guardian.lock.PinSetupActivity

/**
 * The app's protected content: a simple notes vault. Launched in one of two modes via [EXTRA_DECOY]:
 *
 *  - REAL  (decoy=false): the user's real notes, plus the Change PINs / Settings controls.
 *  - DECOY (decoy=true):  a separate, seeded, plausible note list with NO management controls, so a
 *                         coerced unlock reveals an ordinary-looking notes app and no path to the
 *                         real data or configuration.
 *
 * Both modes look and behave like a normal notes app, so the decoy is indistinguishable at a glance.
 */
class VaultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVaultBinding
    private lateinit var notes: NotesRepository
    private var decoy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVaultBinding.inflate(layoutInflater)
        setContentView(binding.root)
        notes = NotesRepository(this)
        decoy = intent.getBooleanExtra(EXTRA_DECOY, false)

        // Management controls exist only in the real vault.
        if (!decoy) {
            binding.manageRow.visibility = android.view.View.VISIBLE
            binding.changePins.setOnClickListener {
                startActivity(Intent(this, PinSetupActivity::class.java)); finish()
            }
            binding.settings.setOnClickListener {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
        }

        binding.addNote.setOnClickListener {
            val text = binding.newNote.text.toString().trim()
            if (text.isNotEmpty()) {
                notes.addNote(decoy, text)
                binding.newNote.text?.clear()
                render()
            }
        }

        render()
    }

    private fun render() {
        binding.notesContainer.removeAllViews()
        for (note in notes.getNotes(decoy)) {
            val tv = TextView(this).apply {
                text = note
                textSize = 16f
                gravity = Gravity.START
                setPadding(0, 24, 0, 24)
            }
            binding.notesContainer.addView(tv)
        }
    }

    companion object {
        const val EXTRA_DECOY = "extra_decoy"

        fun start(context: android.content.Context, decoy: Boolean) {
            context.startActivity(
                Intent(context, VaultActivity::class.java).putExtra(EXTRA_DECOY, decoy)
            )
        }
    }
}
