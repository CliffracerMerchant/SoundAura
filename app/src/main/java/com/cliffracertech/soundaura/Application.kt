/* This file is part of SoundAura, which is released under the terms of the Apache
 * License 2.0. See license.md in the project's root directory to see the full license. */

package com.cliffracertech.soundaura

import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SoundAuraApplication : android.app.Application() {
    override fun onCreate() {
        super.onCreate()

        PlayerService.addPlaybackChangeListener {
            TogglePlaybackTileService.updateState(
                context = applicationContext,
                playbackIsStarted = it == PlayerService.PlaybackState.Playing)
        }
    }
}