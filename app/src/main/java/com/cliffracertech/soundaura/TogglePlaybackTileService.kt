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

/**
 * A TileService to control control the app's audio playback.
 *
 * In order to update the tile's visual state, TogglePlaybackTileService must
 * be informed of changes to the app's playback state by calling the function
 * TogglePlaybackTileService.updateState with a context instance and a
 * PlaybackState value. The tile will appear in its active state if the app's
 * playback state is equal to PlaybackState.Playing, and will appear inactive
 * if the app's playback state is either PlaybackState.Paused or
 * PlaybackState.Stopped.
 *
 * As TogglePlaybackTileService itself does not control the app's playback
 * state, clicking the tile will request a playback state change. These
 * requests to change the playback state can be listened to by registering a
 * PlaybackStateRequestListener using the function addPlaybackStateRequestListener.
 * The requested state will be PlaybackState.Playing if the tile is clicked
 * when in its inactive state, and PlaybackState.Stopped if the tile is clicked
 * in its active state.
 */
class TogglePlaybackTileService: TileService() {

    fun interface PlaybackStateRequestListener {
        fun onPlaybackStateRequest(requestedState: PlaybackState)
    }

    companion object {
        private var newestPlaybackState = PlaybackState.Stopped

        fun updateState(context: Context, state: PlaybackState) {
            newestPlaybackState = state
            val tileService = ComponentName(
                context, TogglePlaybackTileService::class.java)
            requestListeningState(context, tileService)
        }

        private val playbackStateRequestListeners = mutableListOf<PlaybackStateRequestListener>()

        fun addPlaybackStateRequestListener(listener: PlaybackStateRequestListener) {
            playbackStateRequestListeners.add(listener)
        }

        fun removePlaybackStateRequestListener(listener: PlaybackStateRequestListener) {
            playbackStateRequestListeners.remove(listener)
        }

        private fun requestState(state: PlaybackState) {
            playbackStateRequestListeners.forEach {
                it.onPlaybackStateRequest(state)
            }
        }
    }

    override fun onStartListening() {
        qsTile.label = getString(R.string.app_name)
        if (newestPlaybackState == PlaybackState.Playing) {
            qsTile.state = STATE_ACTIVE
            qsTile.contentDescription = getString(R.string.tile_active_description)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                qsTile.subtitle = getString(R.string.playing_description)
        } else {
            qsTile.state = STATE_INACTIVE
            qsTile.contentDescription = getString(R.string.tile_inactive_description)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                qsTile.subtitle = getString(
                    if (newestPlaybackState == PlaybackState.Paused)
                        R.string.paused_description
                    else R.string.stopped_description)
        }
        qsTile.updateTile()
    }

    override fun onClick() {
        if (newestPlaybackState == PlaybackState.Playing)
            requestState(PlaybackState.Stopped)
        else requestState(PlaybackState.Playing)
    }
}