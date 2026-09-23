package com.duress.guardian.tile

import android.service.quicksettings.TileService
import com.duress.guardian.core.ResponseCoordinator
import com.duress.guardian.core.SettingsRepository

/**
 * Quick Settings tile trigger. Tapping the tile fires the duress response — but only if the tile
 * trigger is enabled in Settings. No confirmation dialog by design: under duress the user wants a
 * single, deniable tap from the shade. The destructive wipe stays gated behind its own setting.
 */
class DuressTileService : TileService() {
    override fun onClick() {
        super.onClick()
        if (SettingsRepository(applicationContext).qsTileEnabled) {
            ResponseCoordinator.fire(applicationContext, source = "qs-tile")
        }
    }
}
