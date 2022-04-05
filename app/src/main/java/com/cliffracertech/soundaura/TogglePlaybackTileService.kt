/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.quicksettings.Tile.STATE_ACTIVE
import android.service.quicksettings.Tile.STATE_INACTIVE
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat

/**
 * A TileService to control an instance of PlayerService.
 *
 * TogglePlaybackTileService allows a user to control the state of the app's
 * PlayerService instance with a quick settings tile. If the PlayerService
 * is either stopped or paused, the tile will present itself as inactive to
 * indicate the lack of audio playback. The tile will appear in its active
 * state only if the PlayerService is both running and playing audio.
 *
 * Clicking the tile in its active state (when the PlayerService is running and
 * playing audio) will send a PlayerService.stopIntent instance to it to stop
 * playback. Note that due to PlayerService's behavior when it is bound to an
 * activity, a stop intent may only pause the playback instead of stopping the
 * service. Clicking the tile in its inactive state (when the PlayerService is
 * either stopped or paused in its audio playback) will sent a PlayerService.playIntent
 * instance to it, starting the service it if it is not already started and
 * playing audio.
 *
 * TogglePlaybackTileService is intended to be used as an active tile (i.e. with
 *     <meta-data
 *         android:name="android.service.quicksettings.ACTIVE_TILE"
 *         android:value="true" />
 * in the app's manifest within the tile service's declaration. This means that
 * it does not update its own state, but must have its active / inactive state
 * set for it by another entity. This should be done by a call to
 * TogglePlaybackTileService.updateState with a context instance and a boolean
 * value indicating whether audio playback is occurring.
 */
class TogglePlaybackTileService: TileService() {

    companion object {
        private var playbackIsStarted = false

        fun updateState(context: Context, playbackIsStarted: Boolean) {
            this.playbackIsStarted = playbackIsStarted
            val tileService = ComponentName(
                context, TogglePlaybackTileService::class.java)
            requestListeningState(context, tileService)
        }
    }

    override fun onStartListening() {
        qsTile.label = getString(R.string.app_name)
        if (playbackIsStarted) {
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
        if (playbackIsStarted)
            startService(PlayerService.stopIntent(this))
        else ContextCompat.startForegroundService(
            this, PlayerService.playIntent(this))
    }
}