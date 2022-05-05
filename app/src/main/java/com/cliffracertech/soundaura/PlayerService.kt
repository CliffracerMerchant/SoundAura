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
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.Action
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
 * takes the new PlaybackStateCompat value as a parameter.
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
    private lateinit var audioManager: AudioManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var telephonyManager: TelephonyManager

    private var boundToActivity = false
        set(value) {
            if (value == field) return
            field = value
            notificationManager.notify(notificationId, notification)
        }

    /** isPlaying is updated in playbackState's custom setter and should not be
     * changed otherwise. A value of true indicates a PlaybackStateCompat value
     * of STATE_PLAYING, while false indicates another PlaybackStateCompat value. */
    private var isPlaying by mutableStateOf(false)
    private val playbackStateBuilder = PlaybackStateCompat.Builder()
    private var playbackState
        get() = Companion.playbackState
        set(value) {
            if (playbackState == value ||
                    (value != PlaybackStateCompat.STATE_PLAYING &&
                     value != PlaybackStateCompat.STATE_PAUSED &&
                     value != PlaybackStateCompat.STATE_STOPPED))
                return

            Companion.playbackState = value
            unpauseLocks.clear()
            isPlaying = value == PlaybackStateCompat.STATE_PLAYING

            if (value != PlaybackStateCompat.STATE_STOPPED) {
                uriPlayerMap.forEach { it.value.isPlaying = isPlaying }
                playbackStateBuilder.setState(Companion.playbackState, 0, 1f)
                val actions = PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    if (boundToActivity) 0
                    else PlaybackStateCompat.ACTION_STOP
                playbackStateBuilder.setActions(actions)
                mediaSession.setPlaybackState(playbackStateBuilder.build())
                notificationManager.notify(notificationId, notification)
            } else {
                stopForeground(true)
                stopSelf()
            }
        }

    fun interface PlaybackChangeListener {
        fun onPlaybackStateChange(@PlaybackStateCompat.State newState: Int)
    }

    companion object {
        private const val playPauseActionRequestCode = 1
        private const val stopActionRequestCode = 2
        private const val notificationId = 1
        private const val autoPauseAudioDeviceChangeKey = "auto_pause_audio_device_change"
        private const val autoPauseOngoingCallKey = "auto_pause_ongoing_call"

        private fun setPlaybackStateIntent(
            context: Context,
            @PlaybackStateCompat.State state: Int
        ) : Intent {
            val key = context.getString(R.string.set_playback_action)
            return Intent(context, PlayerService::class.java)
                .setAction(key).putExtra(key, state)
        }

        fun playIntent(context: Context) =
            setPlaybackStateIntent(context, PlaybackStateCompat.STATE_PLAYING)

        fun pauseIntent(context: Context) =
            setPlaybackStateIntent(context, PlaybackStateCompat.STATE_PAUSED)

        fun stopIntent(context: Context) =
            setPlaybackStateIntent(context, PlaybackStateCompat.STATE_STOPPED)

        private val playbackChangeListeners = mutableListOf<PlaybackChangeListener>()

        fun addPlaybackChangeListener(listener: PlaybackChangeListener) {
            playbackChangeListeners.add(listener)
        }

        fun removePlaybackChangeListener(listener: PlaybackChangeListener) {
            playbackChangeListeners.remove(listener)
        }

        var playbackState = PlaybackStateCompat.STATE_STOPPED
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
        mediaSession = MediaSessionCompat(
            this, PlayerService::class.toString(), null,
            PendingIntent.getActivity(this, 0,
                Intent(this, MainActivity::class.java).apply {
                    action = Intent.ACTION_MAIN
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }, FLAG_IMMUTABLE))
        mediaSession.isActive = true
        mediaSession.setCallback(mediaSessionCallback)
        playbackState = PlaybackStateCompat.STATE_PAUSED
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
            }.launchIn(this)
        }
    }

    override fun onDestroy() {
        uriPlayerMap.forEach { it.value.release() }
        playbackState = PlaybackStateCompat.STATE_STOPPED
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val setPlaybackKey = getString(R.string.set_playback_action)
        if (intent?.action == setPlaybackKey) {
            val targetState = intent.extras?.getInt(setPlaybackKey)
            if (targetState == PlaybackStateCompat.STATE_STOPPED && boundToActivity)
                playbackState = PlaybackStateCompat.STATE_PAUSED
            else playbackState = targetState ?: playbackState
        }
        startForeground(notificationId, notification)
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
     * Manually setting the isPlaying state using setIsPlaying will reset all
     * unpause locks.
     */
    private fun autoPauseIf(condition: Boolean, key: String) {
        if (condition) {
            playbackState = PlaybackStateCompat.STATE_PAUSED
            unpauseLocks.add(key)
        } else if (unpauseLocks.remove(key) && unpauseLocks.isEmpty())
            playbackState = PlaybackStateCompat.STATE_PLAYING
    }

    inner class Binder: android.os.Binder() {
        val isPlaying get() = this@PlayerService.isPlaying

        fun toggleIsPlaying() {
            playbackState = if (isPlaying) PlaybackStateCompat.STATE_PAUSED
                            else           PlaybackStateCompat.STATE_PLAYING
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

    private val mediaSessionCallback = object: MediaSessionCompat.Callback() {
        override fun onPlay() {
            playbackState = PlaybackStateCompat.STATE_PLAYING
        }
        override fun onPause() {
            playbackState = PlaybackStateCompat.STATE_PAUSED
        }
        override fun onStop() {
            playbackState = PlaybackStateCompat.STATE_STOPPED
        }
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
        notificationStyle.setMediaSession(mediaSession.sessionToken)
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
    private val togglePlayPauseAction: Action get() {
        val icon = if (isPlaying) R.drawable.ic_baseline_pause_24
                   else           R.drawable.ic_baseline_play_24
        val description = getString(
            if (isPlaying) R.string.pause_description
            else           R.string.play_description)

        val intent = if (isPlaying) pauseIntent(this)
                     else           playIntent(this)
        val pendingIntent = PendingIntent.getService(
            this, playPauseActionRequestCode, intent,
            FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE)
        return Action(icon, description, pendingIntent)
    }

    /** A notification action that will stop the service when triggered. */
    private val stopAction by lazy {
        val pendingIntent = PendingIntent.getService(
            this, stopActionRequestCode, stopIntent(this), FLAG_IMMUTABLE)
        Action(R.drawable.ic_baseline_close_24,
               getString(R.string.close_description),
               pendingIntent)
    }

    /** A notification to use as the foreground notification for the service */
    private val notification: Notification get() {
        val description = getString(when(playbackState) {
            PlaybackStateCompat.STATE_PLAYING -> R.string.playing_description
            PlaybackStateCompat.STATE_PAUSED ->  R.string.paused_description
            PlaybackStateCompat.STATE_STOPPED -> R.string.stopped_description
            else -> R.string.stopped_description
        })

        val builder = notificationBuilder
            .setContentTitle(description)
            .clearActions()
            .addAction(togglePlayPauseAction)

        if (!boundToActivity && playbackState != PlaybackStateCompat.STATE_STOPPED) {
            builder.addAction(stopAction)
            notificationStyle.setShowActionsInCompactView(0, 1)
        } else notificationStyle.setShowActionsInCompactView(0)

        return builder.build()
    }

    private var telephonyCallback: TelephonyCallback? = null
    private var phoneStateListener: PhoneStateListener? = null

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    private fun setAutoPauseDuringCallEnabled(enabled: Boolean) {
        if (!enabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyCallback?.let { telephonyManager.unregisterTelephonyCallback(it) }
                telephonyCallback = null
            } else {
                phoneStateListener?.let { telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE)}
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
                override fun onCallStateChanged(state: Int) =
                    onCallStateChange(state)
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