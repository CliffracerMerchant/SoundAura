/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.model

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.support.v4.media.session.PlaybackStateCompat.STATE_STOPPED
import androidx.annotation.FloatRange
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ComponentActivity
import com.cliffracertech.soundaura.service.PlayerService
import dagger.hilt.android.scopes.ActivityRetainedScoped
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/** An interface that describes the playback state of a set of simultaneously-
 * playing playlists and an automatic stop timer. The 'is playing' state is
 * read via the [isPlaying] property and manipulated using the [toggleIsPlaying]
 * method. The current stop timer is accessed through the property [stopTime]
 * and changed or cleared using [setTimer] and [clearTimer], respectively. The
 * volume for a particular playlist can be manipulated using [setPlaylistVolume]. */
interface PlaybackState {
    /** The current 'is playing' state of the playback. */
    val isPlaying: Boolean
    /** Toggle the playback state of the sound mix between playing/paused. */
    fun toggleIsPlaying()

    /** The [Instant] at which playback will be automatically stopped, or null if no timer is set. */
    val stopTime: Instant?
    /** Set a timer to automatically stop playback after [duration] has elapsed. */
    fun setTimer(duration: Duration)
    /** Clear any set stop timer. */
    fun clearTimer()

    /** Set the playlist identified by [playlistId]'s volume to [volume]. */
    fun setPlaylistVolume(playlistId: Long, @FloatRange(0.0, 1.0) volume: Float)
}

/**
 * An implementation of [PlaybackState] that is backed by a [PlayerService] instance.
 *
 * The methods [onActivityStart] and [onActivityStop] should be called at the
 * app's main activity's [ComponentActivity.onStart] and [ComponentActivity.onStop]
 * methods, respectively. [onActivityStart] will bind the service if it is
 * already running so that the properties [isPlaying] and [stopTime] will
 * reflect the running service's state.
 */
@ActivityRetainedScoped
class PlayerServicePlaybackState @Inject constructor(): PlaybackState {
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

    override val isPlaying get() = serviceBinder?.isPlaying ?: false
    override fun toggleIsPlaying() {
        val binder = serviceBinder
        if (binder != null)
            binder.toggleIsPlaying()
        else context?.let {
            it.startService(PlayerService.playIntent(it))
            it.bindService()
        }
    }

    override val stopTime get() = serviceBinder?.stopTime
    override fun setTimer(duration: Duration) {
        serviceBinder?.setStopTimer(duration) ?: context?.let {
            it.startService(PlayerService.setTimerIntent(it, duration))
            it.bindService()
        }
    }
    override fun clearTimer() {
        serviceBinder?.clearStopTimer() ?: context?.let {
            it.startService(PlayerService.setTimerIntent(it, null))
            it.bindService()
        }
    }

    override fun setPlaylistVolume(playlistId: Long, volume: Float) {
        // If the service is not bound, we do nothing on the assumption that
        // the volume change will be written to the app's database and will
        // therefore be reflected next time the service is started and the
        // active tracks (and their volumes) are read from the database.
        serviceBinder?.setPlaylistVolume(playlistId, volume)
    }
}

/**
 * An implementation of [PlaybackState] for use in testing.
 *
 * The implementation of [PlaybackState] used in the release version of the
 * app, [PlayerServicePlaybackState], is unreliable during testing due to its
 * reliance on a bound service. [TestPlaybackState] can be used in tests for
 * greater reliability. This implementation is used instead of a mock due to
 * the fact that mocking seems to be incompatible with Compose SnapshotState
 * objects (which [PlayerServicePlaybackState] uses internally).
 */
class TestPlaybackState: PlaybackState {
    override var isPlaying = false
        private set
    override fun toggleIsPlaying() { isPlaying = !isPlaying }

    override var stopTime: Instant? = null
        private set
    override fun setTimer(duration: Duration) { stopTime = Instant.now() + duration }
    override fun clearTimer() { stopTime = null }

    override fun setPlaylistVolume(playlistId: Long, volume: Float) = Unit
}