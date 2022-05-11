/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.session.PlaybackState.*
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.lifecycle.LifecycleService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
    private val uriPlayerMap = mutableMapOf<String, Player>()
    private val unpauseLocks = mutableListOf<String>()
    @Inject lateinit var trackDao: TrackDao
    @Inject lateinit var messageHandler: MessageHandler
    private lateinit var audioManager: AudioManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var telephonyManager: TelephonyManager

    private var boundToActivity = false
        set(value) {
            if (value == field) return
            field = value
            updateNotification()
        }

    /** isPlaying is only used so that binding clients have access to a
     * snapshot aware version of playbackState. Its value is updated in
     * playbackState's setter, and should not be changed elsewhere.*/
    private var isPlaying by mutableStateOf(false)

    private var playbackState
        get() = Companion.playbackState
        set(value) {
            if (playbackState == value || (value != STATE_PLAYING &&
                                           value != STATE_PAUSED &&
                                           value != STATE_STOPPED))
                return
            // If there are no active tracks, we want to prevent a change to
            // STATE_PLAYING and show an explanation message so that the user
            // understands why their, e.g., play button tap didn't do anything.
            // If the service was moved directly from a stopped to playing state
            // then the uriPlayerMap might be null because the first new value
            // for TrackDao's activeTracks won't have been collected yet. In
            // this case the body of activeTracks.collect will set the playback
            // state back to STATE_PAUSED if activeTracks' first value is an
            // empty list.
            if (value == STATE_PLAYING && uriPlayerMap.isEmpty()) {
                val stringResId = R.string.player_no_sounds_warning_message
                // It is assumed here that if the service is bound to an
                // activity, then the activity will display messages posted
                // to an injected MessageHandler instance through, e.g., a
                // snack bar. If the service is not bound to an activity,
                // then the message will be displayed via a Toast instead.
                if (boundToActivity)
                    messageHandler.postMessage(StringResource(stringResId))
                else Toast.makeText(this, stringResId, Toast.LENGTH_SHORT).show()
                Companion.playbackState = STATE_PAUSED
                updateNotification()
                return
            }

            Companion.playbackState = value
            isPlaying = value == STATE_PLAYING
            unpauseLocks.clear()
            updateNotification()

            if (value != STATE_STOPPED)
                uriPlayerMap.forEach { it.value.isPlaying = isPlaying }
            else {
                stopForeground(true)
                stopSelf()
            }
        }

    fun interface PlaybackChangeListener {
        fun onPlaybackStateChange(newState: Int)
    }

    companion object {
        private const val playPauseActionRequestCode = 1
        private const val stopActionRequestCode = 2
        private const val notificationId = 1
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
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager

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

            trackDao.getAllActiveTracks().onEach { tracks ->
                // remove players whose track is no longer in the track list
                val uris = tracks.map { it.uriString }
                uriPlayerMap.keys.retainAll {
                    val inNewList = it in uris
                    if (!inNewList)
                        uriPlayerMap[it]?.release()
                    inNewList
                }
                // add players for tracks newly added to the track list
                tracks.forEach {
                    val player = uriPlayerMap.getOrPut(it.uriString) {
                        Player(this@PlayerService, it.uriString)
                    }
                    player.setMonoVolume(it.volume)
                    player.isPlaying = isPlaying
                }
                // if there are no active tracks in the new list, this will pause playback
                // and show the user a message explaining why the playback was paused
                if (isPlaying && tracks.isEmpty()) {
                    val stringResId = R.string.player_no_sounds_warning_message
                    if (boundToActivity)
                        messageHandler.postMessage(StringResource(stringResId))
                    else Toast.makeText(this@PlayerService, stringResId, Toast.LENGTH_SHORT).show()
                    playbackState = STATE_PAUSED
                }
            }.launchIn(this)
        }
    }

    override fun onDestroy() {
        uriPlayerMap.values.forEach(Player::release)
        playbackState = STATE_STOPPED
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val setPlaybackKey = getString(R.string.set_playback_action)
        if (intent?.action == setPlaybackKey) {
            val targetState = intent.extras?.getInt(setPlaybackKey)
            targetState?.let { playbackState = it }
        } else startForeground(notificationId, updatedNotification())
        return super.onStartCommand(intent, flags, startId)
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
            playbackState = STATE_PAUSED
            unpauseLocks.add(key)
        } else if (unpauseLocks.remove(key) && unpauseLocks.isEmpty())
            playbackState = STATE_PLAYING
    }

    inner class Binder: android.os.Binder() {
        val isPlaying get() = this@PlayerService.isPlaying

        fun toggleIsPlaying() {
            playbackState = if (isPlaying) STATE_PAUSED
                            else           STATE_PLAYING
        }

        fun setTrackVolume(uriString: String, volume: Float) {
            uriPlayerMap[uriString]?.setMonoVolume(volume)
        }
    }

    override fun onBind(intent: Intent): Binder {
        super.onBind(intent)
        boundToActivity = true
        return Binder()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        boundToActivity = false
        return false
    }

    private val notificationChannelId by lazy {
        val channelId = getString(R.string.player_notification_channel_id)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            notificationManager.getNotificationChannel(channelId) == null
        ) {
            val title = getString(R.string.player_notification_channel_name)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(channelId, title, importance)
            channel.description = getString(R.string.player_notification_channel_description)
            channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            notificationManager.createNotificationChannel(channel)
        }
        channelId
    }

    private val notificationStyle = androidx.media.app.NotificationCompat.MediaStyle()

    private val notificationBuilder by lazy {
        NotificationCompat.Builder(this, notificationChannelId)
            .setOngoing(true)
            .setSmallIcon(R.drawable.tile_and_notification_icon)
            .setContentIntent(PendingIntent.getActivity(this, 0,
                Intent(this, MainActivity::class.java).apply {
                    action = Intent.ACTION_MAIN
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }, FLAG_IMMUTABLE))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(notificationStyle)
    }

    /** A notification action that will toggle the service's isPlaying state. */
    private val togglePlayPauseAction: NotificationCompat.Action get() {
        val icon = if (isPlaying) R.drawable.ic_baseline_pause_24
                   else           R.drawable.ic_baseline_play_24
        val description = getString(if (isPlaying) R.string.pause
                                    else           R.string.play)
        val intent = if (isPlaying) pauseIntent(this)
                     else           playIntent(this)
        val pendingIntent = PendingIntent.getService(
            this, playPauseActionRequestCode, intent,
            FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE)

        return NotificationCompat.Action.Builder(
            icon, description, pendingIntent
        ).build()
    }

    /** A notification action that will stop the service when triggered. */
    private val stopAction by lazy {
        val icon = R.drawable.ic_baseline_close_24
        val description = getString(R.string.close)
        val pendingIntent = PendingIntent.getService(
            this, stopActionRequestCode, stopIntent(this), FLAG_IMMUTABLE)

        NotificationCompat.Action.Builder(
            icon, description, pendingIntent
        ).build()
    }

    private fun updatedNotification(): Notification {
        val description = getString(when(playbackState) {
            STATE_PLAYING -> R.string.playing
            STATE_PAUSED ->  R.string.paused
            else ->          R.string.stopped
        })

        val builder = notificationBuilder
            .setContentTitle(description)
            .clearActions()
            .addAction(togglePlayPauseAction)

        if (!boundToActivity) {
            builder.addAction(stopAction)
            notificationStyle.setShowActionsInCompactView(0, 1)
        } else notificationStyle.setShowActionsInCompactView(0)

        return builder.build()
    }

    private fun updateNotification() {
        notificationManager.notify(notificationId, updatedNotification())
    }

    private var phoneStateListener: PhoneStateListener? = null
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
                    telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE)
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
            val telephonyCallback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) = onCallStateChange(state)
            }
            telephonyManager.registerTelephonyCallback(
                mainExecutor, telephonyCallback)
        } else telephonyManager.listen(object: PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) =
                onCallStateChange(state)
        }, PhoneStateListener.LISTEN_CALL_STATE)

        android.os.Binder.restoreCallingIdentity(id)
    }
}