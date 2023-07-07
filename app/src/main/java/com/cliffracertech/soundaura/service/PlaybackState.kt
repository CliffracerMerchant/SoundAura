/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.support.v4.media.session.PlaybackStateCompat.STATE_STOPPED
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A wrapper around a [PlayerService] instance that provides
 * methods to get the state of and alter playback.
 *
 * The properties [isPlaying] and [stopTime] will reflect the [PlayerService]'s
 * properties of the same name, or will be false and null, respectively, if the
 * [PlayerService] is unbound. The methods [toggleIsPlaying], [setTimer], and
 * [clearTimer] will call the corresponding [PlayerService.Binder] method if
 * the service is already bound, or will start and bind the service and then
 * enact the desired playback change otherwise. The [setPlaylistVolume] will
 * call the [PlayerService.Binder.setPlaylistVolume] method if the service is
 * already bound, but will otherwise do nothing on the assumption that the
 * volume change will be written to the app's database, and will therefore be
 * acknowledged next time the service does start.
 *
 * The methods [onActivityStart] and [onActivityStop] should be called at the
 * appropriate times in the app's main activity lifecycle. [onActivityStart]
 * will bind the service if it is already running so that the properties
 * [isPlaying] and [stopTime] will reflect the running service's state.
 */
@Singleton
class PlaybackState @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var serviceBinder by mutableStateOf<PlayerService.Binder?>(null)

    private val serviceConnection = object: ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? PlayerService.Binder ?: return
            serviceBinder = binder
        }
        override fun onServiceDisconnected(name: ComponentName?) {}
    }

    private fun startService(
        intent: Intent = Intent(context, PlayerService::class.java)
    ) {
        if (serviceBinder == null)
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        context.startService(intent)
    }

    fun onActivityStart() {
        if (PlayerService.playbackState != STATE_STOPPED)
            startService()
    }
    fun onActivityStop() {
        if (serviceBinder != null)
            context.unbindService(serviceConnection)
    }

    val isPlaying get() = serviceBinder?.isPlaying ?: false
    val stopTime get() = serviceBinder?.stopTime

    fun toggleIsPlaying() {
        serviceBinder?.toggleIsPlaying() ?:
            startService(PlayerService.playIntent(context))
    }

    fun setPlaylistVolume(playlistName: String, volume: Float) {
        serviceBinder?.setPlaylistVolume(playlistName, volume)
    }

    /** Set a timer to automatically stop playback after [duration] has elapsed. */
    fun setTimer(duration: Duration) {
        serviceBinder?.setStopTimer(duration) ?:
            startService(PlayerService.setTimerIntent(context, duration))
    }

    /** Clear any set stop timer. */
    fun clearTimer() {
        serviceBinder?.clearStopTimer() ?:
            startService(PlayerService.setTimerIntent(context, null))
    }
}