/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Intent
import android.database.ContentObserver
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.Settings.System.CONTENT_URI
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.Action
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import javax.inject.Inject

/**
 * A service to play the set of audio tracks marked as playing tracks in the
 * application's database.
 *
 * PlayerService runs as a foreground service, and presents a notification to
 * the user that displays its current play/pause state in string form, along
 * with actions to toggle the play/pause state and to close the service. The
 * play/pause action will always be visible, but the close action is hidden
 * when the service is bound to an activity.
 *
 * PlayerService is designed to be started as a bound service, with the binding
 * activity receiving an instance of its Binder class, through which they can
 * read and manipulate the play/pause state through the Binder instance's
 * isPlaying property and its toggleIsPlaying function. Bound activities should
 * call unbind when they are paused. If the service's play/pause state or a
 * track volume was changed through the binder instance at least once before
 * the unbind occurred, then PlayerService will automatically start itself as a
 * started service, meaning that it will outlive the activity. If one of these
 * changes did not occur before the activity was unbound, then the PlayerService
 * will stop when the activity does as per normal for bound services. This
 * behavior is intended so that if the user accidentally starts the app and
 * closes it immediately without interacting with it, they won't also have to
 * close the PlayerService through its notification.
 *
 * If the media volume is changed to zero when isPlaying is true (whether it is
 * due to volume buttons or sliders, or due to a audio device change), PlayerService
 * will automatically pause itself to preserve battery life. If the media
 * volume is thereafter changed to be above zero, and isPlaying has not been
 * called manually since PlayerService was auto-paused, it will also
 * automatically unpause itself.
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

    private var boundToActivity = false
    private var runWithoutActivity = false
        set(value) {
            if (value && !field)
                ContextCompat.startForegroundService(
                    this, Intent(this, this::class.java))
            field = value
        }

    private var isPlaying by mutableStateOf(false)
    private var wasAutoPaused = false
    private lateinit var audioManager: AudioManager

    companion object {
        private const val requestCode = 1
        private const val playPauseKey = "playPause"
        private const val actionPlayPause = "com.cliffracertech.soundaura.action.playPause"
        private const val actionStop = "com.cliffracertech.soundaura.action.stop"
        private const val notificationId = 342654432
    }

    override fun onDestroy() {
        uriPlayerMap.forEach { it.value.release() }
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == actionStop && !boundToActivity) {
            stopForeground(true)
            stopSelf()
        } else if (action == actionPlayPause) {
            val wasPlaying = intent.extras?.getBoolean(playPauseKey)
            wasPlaying?.let { setIsPlaying(it) }
        }

        startForeground(notificationId, notification)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        contentResolver.registerContentObserver(CONTENT_URI, true, object: ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                checkAutoPause()
            }
        })
        audioManager.registerAudioDeviceCallback(object: AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                super.onAudioDevicesAdded(addedDevices)
                checkAutoPause()
            }
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                super.onAudioDevicesRemoved(removedDevices)
                checkAutoPause()
            }
        }, null)

        repeatWhenStarted {
            trackDao.getAllPlayingTracks().collect { tracks ->
                val uris = tracks.map { it.uriString }
                uriPlayerMap.keys.retainAll {
                    val result = it in uris
                    if (!result) uriPlayerMap[it]?.release()
                    result
                }
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
            }
        }
    }

    private fun checkAutoPause() {
        val volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (volume == 0 && isPlaying) {
            setIsPlaying(false)
            wasAutoPaused = true
        } else if (volume > 0 && !isPlaying && wasAutoPaused) {
            setIsPlaying(true)
            wasAutoPaused = false
        }
    }

    override fun onBind(intent: Intent): Binder {
        super.onBind(intent)
        boundToActivity = true
        notificationManager.notify(notificationId, notification)
        return Binder()
    }

    override fun onRebind(intent: Intent?) {
        boundToActivity = true
        notificationManager.notify(notificationId, notification)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        boundToActivity = false
        if (!runWithoutActivity) notificationManager.cancel(notificationId)
        else notificationManager.notify(notificationId, notification)
        return true
    }

    fun setIsPlaying(isPlaying: Boolean) {
        this.isPlaying = isPlaying
        runWithoutActivity = true
        uriPlayerMap.forEach { it.value.setPaused(!isPlaying) }
        notificationManager.notify(notificationId, notification)
        wasAutoPaused = false
    }

    private fun MediaPlayer.setPaused(paused: Boolean) =
        if (paused) pause() else start()

    inner class Binder: android.os.Binder() {
        val isPlaying get() = this@PlayerService.isPlaying

        fun toggleIsPlaying() =
            setIsPlaying(!this@PlayerService.isPlaying)

        fun setTrackVolume(uriString: String, volume: Float) {
            uriPlayerMap[uriString]?.setVolume(volume, volume)
            runWithoutActivity = true
        }
    }

    private val notificationManager by lazy {
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager
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
            notificationManager.createNotificationChannel(channel)
        }
        channelId
    }

    private val notificationBuilder by lazy {
        NotificationCompat.Builder(this, notificationChannelId)
            .setOngoing(true)
            .setSmallIcon(R.mipmap.ic_launcher_transparent_bg)
            .setContentIntent(PendingIntent.getActivity(this, 0,
                Intent(this, MainActivity::class.java).apply {
                    action = Intent.ACTION_MAIN
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }, FLAG_IMMUTABLE))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
    }

    /** A notification action that will toggle the service's isPlaying state. */
    private val togglePlayPauseAction: Action get() {
        val icon = if (isPlaying) R.drawable.pause_to_play
                   else           R.drawable.play_to_pause
        val description = getString(if (isPlaying) R.string.pause_description
                                    else           R.string.play_description)
        val intent = Intent(this, PlayerService::class.java)
                        .setAction(actionPlayPause)
                        .putExtra(playPauseKey, !isPlaying)
        val pendingIntent = PendingIntent.getService(this, requestCode, intent,
                                                     FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE)
        return Action(icon, description, pendingIntent)
    }

    /** A notification action that will stop the service when triggered. */
    private val stopServiceAction: Action get() {
        val intent = Intent(this, PlayerService::class.java).setAction(actionStop)
        return Action(R.drawable.ic_baseline_close_24,
                      getString(R.string.close_description),
                      PendingIntent.getService(this, requestCode, intent, FLAG_IMMUTABLE))
    }

    /** A notification to use as the foreground notification for the service */
    private val notification: Notification get() {
        val description = getString(if (isPlaying) R.string.playing_description
                                    else           R.string.paused_description)
        val builder = notificationBuilder
            .setContentTitle(description)
            .clearActions()
            .addAction(togglePlayPauseAction)

        val style = androidx.media.app.NotificationCompat.MediaStyle()
        if (runWithoutActivity && !boundToActivity) {
            builder.addAction(stopServiceAction)
            style.setShowActionsInCompactView(0, 1)
        } else style.setShowActionsInCompactView(0)

        return builder.setStyle(style).build()
    }
}