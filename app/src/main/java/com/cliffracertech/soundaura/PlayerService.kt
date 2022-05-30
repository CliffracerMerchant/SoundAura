/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.session.PlaybackState.*
import android.os.Build
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A service to play the set of audio tracks marked as active tracks in the
 * application's database.
 *
 * PlayerService can either be started independently of an activity with a
 * startService call, or can be started bound to an activity if the activity
 * calls bindService. In the latter case, PlayerService will call startService
 * on itself so that it outlives the binding activity. In either case,
 * PlayerService presents a foreground notification to the user that displays
 * its current play/pause state in string form, along with actions to toggle
 * the play/pause state and to close the service. The play/pause action will
 * always be visible, but the close action is hidden when the service is bound
 * to any clients.
 *
 * Changes in the playback state can be listened to by calling the static
 * function addPlaybackChangeListener with a PlaybackChangeListener.
 * PlaybackChangeListener is a functional interface whose single abstract
 * method is called whenever the PlayerService's playback state changes and
 * takes the new PlaybackState value as a parameter.
 *
 * If an audio device change occurs when isPlaying is true and the new media
 * volume after the device change is zero, PlayerService will automatically
 * pause itself to preserve battery life. If another device change brings the
 * media volume back up to above zero and isPlaying has not been called
 * manually since PlayerService was auto-paused, it will also automatically
 * unpause itself. This auto-pause also works for ongoing calls if the
 * READ_PHONE_STATE permission has been granted to the app.
 *
 * To ensure that the volume for already playing tracks is changed without
 * perceptible lag, PlayerService will not respond to track volume changes made
 * at the database level for already playing tracks. Instead, the function
 * setTrackVolume must be called with the Uri (in string form) of the track and
 * the new volume. If a bound activity presents the user with, e.g, a slider to
 * change a track's volume, the slider's onSlide callback should therefore call
 * setTrackVolume.
 */
@AndroidEntryPoint
class PlayerService: LifecycleService() {
    private val playerSet = TrackPlayerSet(this, ::onPlayerCreationFailure)
    private val unpauseLocks = mutableListOf<String>()
    @Inject lateinit var trackDao: TrackDao
    @Inject lateinit var messageHandler: MessageHandler
    private lateinit var audioManager: AudioManager
    private lateinit var telephonyManager: TelephonyManager
    private val notificationManager by lazy {
        PlayerNotification(
            context = this,
            playIntent = playIntent(this),
            pauseIntent = pauseIntent(this),
            stopIntent = stopIntent(this))
    }
    private fun updateNotification() =
        notificationManager.update(playbackState, showStopAction = !boundToActivity)

    private var boundToActivity = false
        set(value) {
            if (value == field) return
            field = value
            updateNotification()
        }

    /** isPlaying is only used so that binding clients have access to a
     * snapshot aware version of playbackState. Its value is updated in
     * setPlaybackState, and should not be changed elsewhere to ensure
     * that mismatched state does not occur.*/
    private var isPlaying by mutableStateOf(false)

    /**
     * Set the companion object's playbackState and ensure that all state
     * derived from the companion's playbackState (e.g. the notification
     * state and the value of isPlaying) is updated. Except for when the
     * service is being created or destroyed, the companion's playback
     * state should not be altered outside of setPlaybackState to ensure
     * that mismatched state does not occur.
     *
     * @param state The desired PlaybackState.
     * @param clearUnpauseLocks Whether or not to reset all unpause locks.
     *     This should only be true when the playback state is being set
     *     to STATE_PAUSED as the result of an autoPauseIf call.
     */
    private fun setPlaybackState(state: Int, clearUnpauseLocks: Boolean = true) {
        if (playbackState == state)
            return
        if (state != STATE_PLAYING && state != STATE_PAUSED && state != STATE_STOPPED)
            return

        val newState = when {
            state == STATE_PLAYING && playerSet.isEmpty && playerSet.isInitialized -> {
                // If there are no active tracks, we want to prevent a change to
                // STATE_PLAYING and show an explanation message so that the user
                // understands why their, e.g., play button tap didn't do anything.
                // If the service was moved directly from a stopped to playing state
                // then the PlayerSet might be empty because the first new value
                // for TrackDao's activeTracks won't have been collected yet.
                // The updatePlayers method will handle this edge case.
                showAutoStopPlaybackExplanation()
                STATE_PAUSED
            } state == STATE_STOPPED && boundToActivity -> {
                // The service is not intended to be stopped when it is bound to an
                // activity, so in this case we will set the state to paused instead.
                STATE_PAUSED
            } else -> state
        }
        if (clearUnpauseLocks)
            unpauseLocks.clear()
        playbackState = newState
        isPlaying = newState == STATE_PLAYING
        updateNotification()
        if (newState != STATE_STOPPED)
            playerSet.setIsPlaying(isPlaying)
        else {
            notificationManager.stopForeground(this)
            stopSelf()
        }
    }

    companion object {
        private const val autoPauseAudioDeviceChangeKey = "auto_pause_audio_device_change"
        private const val autoPauseOngoingCallKey = "auto_pause_ongoing_call"

        private fun setPlaybackIntent(context: Context, state: Int): Intent {
            val key = context.getString(R.string.set_playback_action)
            return Intent(context, PlayerService::class.java)
                .setAction(key).putExtra(key, state)
        }
        fun playIntent(context: Context) = setPlaybackIntent(context, STATE_PLAYING)
        fun pauseIntent(context: Context) = setPlaybackIntent(context, STATE_PAUSED)
        fun stopIntent(context: Context) = setPlaybackIntent(context, STATE_STOPPED)

        fun interface PlaybackChangeListener {
            fun onPlaybackStateChange(newState: Int)
        }
        private val playbackChangeListeners = mutableListOf<PlaybackChangeListener>()

        fun addPlaybackChangeListener(listener: PlaybackChangeListener) {
            playbackChangeListeners.add(listener)
        }

        fun removePlaybackChangeListener(listener: PlaybackChangeListener) {
            playbackChangeListeners.remove(listener)
        }

        var playbackState = STATE_STOPPED
            private set(value) {
                field = value
                playbackChangeListeners.forEach {
                    it.onPlaybackStateChange(value)
                }
            }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager

        playbackState = STATE_PAUSED
        val intent = Intent(this, PlayerService::class.java)
        ContextCompat.startForegroundService(this, intent)

        audioManager.registerAudioDeviceCallback(object: AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                val volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                autoPauseIf(volume == 0, autoPauseAudioDeviceChangeKey)
            }
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                val volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                autoPauseIf(volume == 0, autoPauseAudioDeviceChangeKey)
            }
        }, null)

        repeatWhenStarted {
            val autoPauseDuringCallsKey = booleanPreferencesKey(
                getString(R.string.pref_auto_pause_during_calls_key))
            dataStore.preferenceFlow(autoPauseDuringCallsKey, false)
                .onEach(::setAutoPauseDuringCallEnabled)
                .launchIn(this)
            trackDao.getAllActiveTracks()
                .onEach(::updatePlayers)
                .launchIn(this)
        }
    }

    override fun onDestroy() {
        playbackState = STATE_STOPPED
        notificationManager.stopForeground(this)
        playerSet.releaseAll()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val setPlaybackKey = getString(R.string.set_playback_action)
        if (intent?.action == setPlaybackKey) {
            val targetState = intent.extras?.getInt(setPlaybackKey)
            targetState?.let { setPlaybackState(it) }
        }
        notificationManager.startForeground(
            service = this,
            playbackState = playbackState,
            showStopAction = !boundToActivity)
        return super.onStartCommand(intent, flags, startId)
    }

    inner class Binder: android.os.Binder() {
        val isPlaying get() = this@PlayerService.isPlaying

        fun toggleIsPlaying() {
            setPlaybackState(if (isPlaying) STATE_PAUSED
                             else           STATE_PLAYING)
        }

        fun setTrackVolume(uriString: String, volume: Float) =
            playerSet.setPlayerVolume(uriString, volume)
    }

    override fun onBind(intent: Intent): Binder {
        super.onBind(intent)
        boundToActivity = true
        return Binder()
    }

    override fun onUnbind(intent: Intent): Boolean {
        boundToActivity = false
        return true
    }

    override fun onRebind(intent: Intent) {
        boundToActivity = true
    }

    private fun onPlayerCreationFailure(uriString: String) {
        lifecycleScope.launch {
            trackDao.notifyOfError(uriString)
        }
    }

    private fun showAutoStopPlaybackExplanation() {
        // It is assumed here that if the service is bound to an activity, then
        // the activity will display messages posted to an injected MessageHandler
        // instance through, e.g., a snack bar. If the service is not bound to an
        // activity, then the message will be displayed via a Toast instead.
        val stringResId = R.string.player_no_sounds_warning_message
        if (boundToActivity)
            messageHandler.postMessage(StringResource(stringResId))
        else Toast.makeText(this@PlayerService, stringResId, Toast.LENGTH_SHORT).show()
    }

    private fun updatePlayers(tracks: List<Track>) {
        val firstUpdate = !playerSet.isInitialized
        playerSet.update(tracks, isPlaying)

        // If the new track list is empty when isPlaying is true, we want
        // to pause playback because there are no tracks to play.
        if (isPlaying && tracks.isEmpty()) {
            setPlaybackState(STATE_PAUSED)
            // If this playback auto pause happened implicitly due to the user making
            // the last active track inactive, no user feedback should be necessary.
            // If this playback auto pause happened following an explicit attempt by
            // the user to start playback when there were no active tracks, then we
            // want to display a message to the user in this case explaining why the
            // explicit attempt to start playback failed. Normally this case would be
            // caught by playbackState's custom setter, but if the service is moved
            // directly from a stopped to playing state, then the first value of
            // trackDao's activeTracks won't have been collected yet and playbackState's
            // custom setter therefore won't know if it should prevent the change to
            // STATE_PLAYING. This check will show the explanation in this edge case.
            if (firstUpdate) showAutoStopPlaybackExplanation()
        }
    }

    /**
     * Automatically pause playback if the parameter condition is true and
     * isPlaying is true. If this auto-pause succeeds, an unpause lock will be
     * added to the player with a key equal to the parameter key. Calling
     * autoPauseIf with the same key with a false condition will remove the
     * corresponding unpause lock, and, if there are no other unpause locks,
     * resume playback. Different causes of the auto-pause event should
     * therefore utilize unique keys (e.g. one for auto-pausing when a call is
     * started, and another for auto-pausing when other media starts playing).
     * Manually setting playbackState will reset all unpause locks so that the
     * user can override this behavior.
     */
    private fun autoPauseIf(condition: Boolean, key: String) {
        if (condition) {
            setPlaybackState(STATE_PAUSED, clearUnpauseLocks = false)
            unpauseLocks.add(key)
        } else if (unpauseLocks.remove(key) && unpauseLocks.isEmpty())
            setPlaybackState(STATE_PLAYING)
    }

    @Suppress("DEPRECATION")
    private var phoneStateListener: android.telephony.PhoneStateListener? = null
    private var telephonyCallback: TelephonyCallback? = null

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    private fun setAutoPauseDuringCallEnabled(enabled: Boolean) {
        if (!enabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyCallback?.let {
                    telephonyManager.unregisterTelephonyCallback(it)
                }
                telephonyCallback = null
            } else {
                phoneStateListener?.let {
                    telephonyManager.listen(it, android.telephony.PhoneStateListener.LISTEN_NONE)
                }
                phoneStateListener = null
            }
            return
        }

        val readPhoneState = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_PHONE_STATE)
        if (readPhoneState != PackageManager.PERMISSION_GRANTED && enabled)
            return

        val onCallStateChange = { state: Int ->
            autoPauseIf(key = autoPauseOngoingCallKey, condition =
                state == TelephonyManager.CALL_STATE_RINGING ||
                state == TelephonyManager.CALL_STATE_OFFHOOK)
        }

        val id = android.os.Binder.clearCallingIdentity()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) = onCallStateChange(state)
            }
            telephonyManager.registerTelephonyCallback(mainExecutor, callback)
        } else {
            val listener = object: android.telephony.PhoneStateListener() {
                override fun onCallStateChanged(state: Int, phoneNumber: String?) =
                    onCallStateChange(state)
            }
            telephonyManager.listen(listener, android.telephony.PhoneStateListener.LISTEN_CALL_STATE)
        }

        android.os.Binder.restoreCallingIdentity(id)
    }
}