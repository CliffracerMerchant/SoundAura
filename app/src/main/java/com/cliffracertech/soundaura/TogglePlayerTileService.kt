/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.os.Build
import android.service.quicksettings.Tile.STATE_ACTIVE
import android.service.quicksettings.Tile.STATE_INACTIVE
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat

class TogglePlayerTileService: TileService() {

    override fun onStartListening() {
        qsTile.label = getString(R.string.app_name)
        if (PlayerService.isStartedAndPlaying) {
            qsTile.state = STATE_ACTIVE
            qsTile.contentDescription = getString(R.string.tile_active_description)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            qsTile.subtitle = getString(R.string.tile_active_subtitle)
        } else {
            qsTile.state =  STATE_INACTIVE
            qsTile.contentDescription = getString(R.string.tile_inactive_description)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                qsTile.subtitle = getString(R.string.tile_inactive_subtitle)
        }
        qsTile.updateTile()
    }

    override fun onClick() {
        if (PlayerService.isStartedAndPlaying)
            startService(PlayerService.stopIntent(this))
        else ContextCompat.startForegroundService(this, PlayerService.playIntent(this))
    }
}