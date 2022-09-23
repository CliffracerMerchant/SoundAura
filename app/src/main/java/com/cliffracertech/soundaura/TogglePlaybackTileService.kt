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
import android.support.v4.media.session.PlaybackStateCompat

/**
 * A [TileService] to control the app's audio playback.
 *
 * In order to update the tile's visual state, TogglePlaybackTileService must
 * be informed of changes to the app's playback state by calling the static
 * function [updateState] with a [Context] instance and a [PlaybackStateCompat]
 * value. The tile will appear in its active state if the app's playback state
 * is equal to [PlaybackStateCompat.STATE_PLAYING], and will appear inactive
 * otherwise.
 *
 * As TogglePlaybackTileService itself does not control the app's playback
 * state, clicking the tile will request a playback state change. These
 * requests to change the playback state can be listened to by registering
 * a [PlaybackStateRequestListener] using the static function
 * [addPlaybackStateRequestListener]. The requested state will be
 * [PlaybackStateCompat.STATE_PLAYING] if the tile is clicked when in its
 * inactive state, and [PlaybackStateCompat.STATE_STOPPED] if the tile is
 * clicked in its active state.
 */
class TogglePlaybackTileService: TileService() {

    fun interface PlaybackStateRequestListener {
        fun onPlaybackStateRequest(@PlaybackStateCompat.State requestedState: Int)
    }

    companion object {
        private var playbackState = PlaybackStateCompat.STATE_STOPPED

        fun updateState(context: Context, @PlaybackStateCompat.State state: Int) {
            playbackState = state
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

        private fun requestState(@PlaybackStateCompat.State state: Int) {
            playbackStateRequestListeners.forEach {
                it.onPlaybackStateRequest(state)
            }
        }
    }

    override fun onStartListening() {
        qsTile.label = getString(R.string.app_name)
        if (playbackState == PlaybackStateCompat.STATE_PLAYING) {
            qsTile.state = STATE_ACTIVE
            qsTile.contentDescription = getString(R.string.tile_active_description)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                qsTile.subtitle = getString(R.string.playing)
        } else {
            qsTile.state = STATE_INACTIVE
            qsTile.contentDescription = getString(R.string.tile_inactive_description)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                qsTile.subtitle = getString(
                    if (playbackState == PlaybackStateCompat.STATE_PAUSED)
                        R.string.paused
                    else R.string.stopped)
        }
        qsTile.updateTile()
    }

    override fun onClick() {
        if (playbackState == PlaybackStateCompat.STATE_PLAYING)
            requestState(PlaybackStateCompat.STATE_STOPPED)
        else requestState(PlaybackStateCompat.STATE_PLAYING)
    }
}