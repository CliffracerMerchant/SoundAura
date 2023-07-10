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
import androidx.core.app.ComponentActivity
import dagger.hilt.android.scopes.ActivityRetainedScoped
import java.time.Duration
import javax.inject.Inject

/**
 * A wrapper around a [PlayerService] instance that provides
 * methods to get and alter playback state.
 *
 * The properties [isPlaying] and [stopTime] will reflect the [PlayerService]'s
 * properties of the same name, or will be false and null, respectively, if the
 * [PlayerService] is unbound. The methods [toggleIsPlaying], [setTimer], and
 * [clearTimer] will call the corresponding [PlayerService.Binder] method if
 * the service is already bound, or will start and bind the service and then
 * enact the desired playback change otherwise. The [setPlaylistVolume] will
 * call the [PlayerService.Binder.setPlaylistVolume] method if the service is
 * already bound, but will otherwise do nothing on the assumption that the
 * volume change will be written to the app's database.
 *
 * The methods [onActivityStart] and [onActivityStop] should be called at the
 * app's main activity's [ComponentActivity.onStart] and [ComponentActivity.onStop]
 * methods, respectively. [onActivityStart] will bind the service if it is
 * already running so that the properties [isPlaying] and [stopTime] will
 * reflect the running service's state.
 */
@ActivityRetainedScoped
class PlaybackState @Inject constructor() {
    private var context: Context? = null

    private var serviceBinder by mutableStateOf<PlayerService.Binder?>(null)

    private val connection = object: ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            serviceBinder = service as? PlayerService.Binder
        }
        override fun onServiceDisconnected(name: ComponentName?) {}
    }

    private fun Context.bindService() =
        bindService(Intent(this, PlayerService::class.java), connection, 0)

    private fun unbindService() {
        if (serviceBinder != null)
            context?.unbindService(connection)
        serviceBinder = null
    }

    init {
        // This playbackChangeListener ensures that if the services starts,
        // stops, or changes state outside of the activity (e.g. through the
        // tile or notification) when the activity is already running, the
        // change will be reflected in PlaybackState.
        PlayerService.addPlaybackChangeListener {
            if (it == STATE_STOPPED)
                unbindService()
            else if (serviceBinder == null)
                context?.bindService()
        }
    }

    fun onActivityStart(context: Context) {
        this.context = context
        if (PlayerService.playbackState != STATE_STOPPED)
            context.bindService()
    }

    fun onActivityStop() {
        unbindService()
        this.context = null
    }

    val isPlaying get() = serviceBinder?.isPlaying ?: false
    val stopTime get() = serviceBinder?.stopTime

    /** Toggle the playback state of the sound mix between playing/paused. */
    fun toggleIsPlaying() {
        serviceBinder?.toggleIsPlaying() ?: context?.let {
            it.startService(PlayerService.playIntent(it))
            it.bindService()
        }
    }

    /** Set the playlist whose name matches [playlistName]'s volume to [volume]. */
    fun setPlaylistVolume(playlistName: String, volume: Float) {
        serviceBinder?.setPlaylistVolume(playlistName, volume)
    }

    /** Set a timer to automatically stop playback after [duration] has elapsed. */
    fun setTimer(duration: Duration) {
        serviceBinder?.setStopTimer(duration) ?: context?.let {
            it.startService(PlayerService.setTimerIntent(it, duration))
            it.bindService()
        }
    }

    /** Clear any set stop timer. */
    fun clearTimer() {
        serviceBinder?.clearStopTimer() ?: context?.let {
            it.startService(PlayerService.setTimerIntent(it, null))
            it.bindService()
        }
    }
}