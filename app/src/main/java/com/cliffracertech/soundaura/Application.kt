/* This file is part of SoundAura, which is released under the terms of the Apache
 * License 2.0. See license.md in the project's root directory to see the full license. */

package com.cliffracertech.soundaura

import androidx.core.content.ContextCompat
import dagger.hilt.android.HiltAndroidApp

enum class PlaybackState { Playing, Paused, Stopped }

@HiltAndroidApp
class SoundAuraApplication : android.app.Application() {
    override fun onCreate() {
        super.onCreate()

        PlayerService.addPlaybackChangeListener {
            TogglePlaybackTileService.updateState(
                context = applicationContext, state = it)
        }

        TogglePlaybackTileService.addPlaybackStateRequestListener {
            if (it == PlaybackState.Playing)
                ContextCompat.startForegroundService(
                    this, PlayerService.playIntent(this))
            else startService(PlayerService.stopIntent(this))
        }
    }
}