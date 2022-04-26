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
import android.media.MediaPlayer
import android.net.Uri
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
 * A service to play the set of audio tracks marked as playing tracks in the
 * application's database.
 *
 * PlayerService runs as a foreground service, and presents a notification to
 * the user that displays its current play/pause state in string form, along
 * with actions to toggle the play/pause state and to close the service. The
 * play/pause action will always be visible, but the close action is hidden
 * when the service is bound to any clients via Context.bindService.
 *
 * PlayerService is designed to be started as a bound service, with the binding
 * client receiving an instance of its Binder class, through which they can
 * read and manipulate the play/pause state through the Binder instance's
 * isPlaying property and its toggleIsPlaying function. Bound activities should
 * call unbind when they are paused. If the service's play/pause state or a
 * track volume was changed through the Binder instance at least once before
 * the unbind occurred, then PlayerService will automatically start itself as a
 * started service, meaning that it will outlive its bound clients. If one of
 * these changes did not occur after PlayerService was started but before all
 * clients were unbound, then the PlayerService will stop when all clients are
 * unbound as per normal for bound services. This behavior is intended so that
 * if the user accidentally starts the app and closes it immediately without
 * interacting with it, they won't also have to close the PlayerService through
 * its notification.
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
 * To ensure that the volume for already playing tracks is changed seamlessly
 * and without perceptible lag, PlayerService will not respond to track volume
 * updates for already playing tracks that are received through the view
 * model's playingTracks property. Instead, the function setTrackVolume must be
 * called with the Uri (in string form) of the track and the new volume. If a
 * bound activity presents the user with, e.g, a slider to change a track's
 * volume, the slider's onSlide callback should therefore call setTrackVolume.
 */
@AndroidEntryPoint
class PlayerService: LifecycleService() {
    private val uriPlayerMap = mutableMapOf<String, MediaPlayer>()
    @Inject lateinit var trackDao: TrackDao
    private lateinit var audioManager: AudioManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var mediaSession: MediaSessionCompat
    private val playbackStateBuilder = PlaybackStateCompat.Builder()
    private lateinit var telephonyManager: TelephonyManager

    private var isPlaying by mutableStateOf(false)
    private val unpauseLocks = mutableListOf<String>()
    private var currentlyBound = false
    private var runAsStartedService = false
        set(value) {
            if (value && !field)
                ContextCompat.startForegroundService(
                    this, Intent(this, this::class.java))
            field = value
        }

    fun interface PlaybackChangeListener {
        fun onPlaybackStateChange(@PlaybackStateCompat.State newState: Int)
    }

    companion object {
        private const val requestCode = 1
        private const val notificationId = 1
        private const val autoPauseOnAudioDeviceChangeKey = "auto_pause_audio_device_change"
        private const val autoPauseOngoingCallKey = "auto_pause_ongoing_call"

        fun playIntent(context: Context) =
            Intent(context, PlayerService::class.java)
                .setAction(context.getString(R.string.set_playback_action))
                .putExtra(context.getString(R.string.set_playback_key), true)

        fun stopIntent(context: Context) =
            Intent(context, PlayerService::class.java)
                .setAction(context.getString(R.string.stop_playback_action))

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
                playbackChangeListeners.forEach { it.onPlaybackStateChange(value) }
            }
    }

    private var playbackState
        get() = Companion.playbackState
        set(value) {
            Companion.playbackState = value
            updatePlaybackStateAndNotification()
        }

    private fun updatePlaybackStateAndNotification() {
        playbackStateBuilder.setState(Companion.playbackState, 0, 1f)
        val actions = PlaybackStateCompat.ACTION_PLAY_PAUSE or
            if (currentlyBound) 0
            else PlaybackStateCompat.ACTION_STOP
        playbackStateBuilder.setActions(actions)
        mediaSession.setPlaybackState(playbackStateBuilder.build())
        notificationManager.notify(notificationId, notification)
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
        updatePlaybackStateAndNotification()

        audioManager.registerAudioDeviceCallback(object: AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                super.onAudioDevicesAdded(addedDevices)
                val volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                autoPauseIf(volume == 0, autoPauseOnAudioDeviceChangeKey)
            }
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                super.onAudioDevicesRemoved(removedDevices)
                val volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                autoPauseIf(volume == 0, autoPauseOnAudioDeviceChangeKey)
            }
        }, null)

        repeatWhenStarted {
            val autoPauseDuringCallsKey = booleanPreferencesKey(
                getString(R.string.pref_auto_pause_during_calls_key))
            dataStore.preferenceFlow(autoPauseDuringCallsKey, false)
                .onEach(::setAutoPauseDuringCallEnabled)
                .launchIn(this)

            trackDao.getAllPlayingTracks().onEach { tracks ->
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
                        MediaPlayer.create(this@PlayerService, Uri.parse(it.uriString))
                            ?.apply { isLooping = true }
                            ?: return@forEach
                    }
                    player.setVolume(it.volume, it.volume)
                    if (player.isPlaying != isPlaying)
                        player.setPaused(!isPlaying)
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
        when (intent?.action) {
            getString(R.string.stop_playback_action) -> {
                if (!currentlyBound) {
                    playbackState = PlaybackStateCompat.STATE_STOPPED
                    stopForeground(true)
                    stopSelf()
                } else setIsPlaying(false)
            } getString(R.string.set_playback_action) -> {
                // The service should be in a foreground state if a play
                // pause action is invoked, but setIsPlaying will cause
                // runAsStartedService to be set to true, which will cause
                // startForeground to be called if it hasn't been already.
                val key = getString(R.string.set_playback_key)
                val targetState = intent.extras?.getBoolean(key)
                targetState?.let { setIsPlaying(it) }
            } else -> startForeground(notificationId, notification)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    inner class Binder: android.os.Binder() {
        val isPlaying get() = this@PlayerService.isPlaying

        fun toggleIsPlaying() =
            setIsPlaying(!this@PlayerService.isPlaying)

        fun setTrackVolume(uriString: String, volume: Float) {
            uriPlayerMap[uriString]?.setVolume(volume, volume)
            runAsStartedService = true
        }
    }

    override fun onBind(intent: Intent): Binder {
        super.onBind(intent)
        currentlyBound = true
        updatePlaybackStateAndNotification()
        return Binder()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        currentlyBound = false
        if (!runAsStartedService)
            notificationManager.cancel(notificationId)
        else updatePlaybackStateAndNotification()
        return false
    }

    private fun MediaPlayer.setPaused(paused: Boolean) =
        if (paused) pause() else start()

    /** Set the isPlaying state, returning whether or not the change was
     * successful. setIsPlaying will return false if the playing state
     * already matched the provided value. */
    fun setIsPlaying(isPlaying: Boolean): Boolean {
        if (this.isPlaying == isPlaying)
            return false
        if (isPlaying && unpauseLocks.isNotEmpty())
            return false

        this.isPlaying = isPlaying
        runAsStartedService = true
        playbackState = if (isPlaying) PlaybackStateCompat.STATE_PLAYING
                        else           PlaybackStateCompat.STATE_PAUSED
        uriPlayerMap.forEach { it.value.setPaused(!isPlaying) }
        updatePlaybackStateAndNotification()
        return true
    }

    /**
     * Automatically pause playback if the parameter condition is true and
     * isPlaying is true. If this auto-pause succeeds, an unpause lock will be
     * added to the player with a key equal to the parameter key. Calling
     * autoPauseIf with the same key with a false condition will remove the
     * corresponding unpause lock and, if there are no other unpause locks,
     * resume playback. Different causes of the auto-pause event should
     * therefore utilize unique keys (e.g. one for auto-pausing when a call is
     * started, and another for auto-pausing when other media starts playing).
     * Manually setting the isPlaying state using setIsPlaying will reset all
     * unpause locks.
     */
    private fun autoPauseIf(condition: Boolean, key: String) {
        if (condition && isPlaying && setIsPlaying(false)) {
            unpauseLocks.add(key)
        } else if (!condition && unpauseLocks.remove(key))
            // setIsPlaying will return false and not change the
            // state if there are still other unpause locks remaining
            setIsPlaying(true)
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
    private val togglePlayPauseAction: Action get() {
        val icon = if (isPlaying) R.drawable.ic_baseline_pause_24
                   else           R.drawable.ic_baseline_play_24
        val description = getString(
            if (isPlaying) R.string.pause_description
            else           R.string.play_description)

        val intent = Intent(this, PlayerService::class.java)
            .setAction(getString(R.string.set_playback_action))
            .putExtra(getString(R.string.set_playback_key), !isPlaying)
        val pendingIntent = PendingIntent.getService(
            this, requestCode, intent, FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE)
        return Action(icon, description, pendingIntent)
    }

    /** A notification action that will stop the service when triggered. */
    private val stopServiceAction by lazy {
        val pendingIntent = PendingIntent.getService(
            this, requestCode, stopIntent(this), FLAG_IMMUTABLE)
        Action(R.drawable.ic_baseline_close_24,
               getString(R.string.close_description),
               pendingIntent)
    }

    /** A notification to use as the foreground notification for the service */
    private val notification: Notification get() {
        val description = getString(
            if (isPlaying) R.string.playing_description
            else           R.string.paused_description)

        val builder = notificationBuilder
            .setContentTitle(description)
            .clearActions()
            .addAction(togglePlayPauseAction)

        if (runAsStartedService && !currentlyBound) {
            builder.addAction(stopServiceAction)
            notificationStyle.setShowActionsInCompactView(0, 1)
        } else notificationStyle.setShowActionsInCompactView(0)
        notificationStyle.setMediaSession(mediaSession.sessionToken)

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