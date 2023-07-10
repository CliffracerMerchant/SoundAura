/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.service

import android.app.*
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.app.Service.STOP_FOREGROUND_REMOVE
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.os.Build
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.MediaMetadataCompat.*
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.session.PlaybackStateCompat.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.cliffracertech.soundaura.MainActivity
import com.cliffracertech.soundaura.R
import com.cliffracertech.soundaura.mediacontroller.toHMMSSstring
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/**
 * A manager for a notification for a foreground media playing service.
 *
 * PlayerNotification can post a notification for a foreground media playing
 * service that contains a string describing a playback state (e.g. playing,
 * paused), a toggle play/pause action, and a stop action. Using the values
 * of [playbackState] and [stopTime] that are provided in its constructor,
 * PlayerNotification will automatically call [Service.startForeground] for
 * the client service during creation. PlayerNotification should be notified
 * of changes to the playback state or the auto stop time afterwards via the
 * method [update]. The notification can be cleared when the service is
 * stopping with the function [remove].

 * @param service The foreground media playing service that PlayerNotification
 *     is serving. Note that this reference to the service is held onto for
 *     PlayerNotification's lifetime; PlayerNotification should therefore never
 *     outlive the service instance used here.
 * @param playIntent The intent that, when fired, will cause the service that
 *     PlayerNotification is serving to start its playback.
 * @param pauseIntent The intent that, when fired, will cause the service that
 *     PlayerNotification is serving to pause its playback.
 * @param stopIntent The intent that, when fired, will cause the service that
 *     PlayerNotification is serving to stop its playback.
 * @param cancelTimerIntent The intent that, when fired, will cause the
 *     cancellation of the current stop timer
 * @param playbackState The initial playback state that will be displayed
 *     in the notification. playbackState can be changed after creation by
 *     passing the new value to the method update.
 * @param stopTime The time at which playback will be automatically stopped,
 *     if any. The duration between now and the stop time will be calculated
 *     and displayed in the notification.
 * @param useMediaSession Whether or not a [MediaSessionCompat] instance should
 *     be tied to the notification. If true, the notification will appear in
 *     the media session section of the status bar. If false, the notification
 *     will appear as a regular notification instead. PlayerNotification's
 *     property of the same name can be used to change this after creation.
 */
class PlayerNotification(
    private val service: LifecycleService,
    private val playIntent: Intent,
    private val pauseIntent: Intent,
    private val stopIntent: Intent,
    private val cancelTimerIntent: Intent,
    private var playbackState: Int,
    stopTime: Instant?,
    useMediaSession: Boolean
) {
    private val playActionRequestCode = 1
    private val pauseActionRequestCode = 2
    private val stopActionRequestCode = 3
    private val cancelTimerRequestCode = 4
    private val notificationId get() = if (useMediaSession) 1 else 2
    private val notificationManager =
        service.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    private val playbackStateBuilder = PlaybackStateCompat.Builder()
    private lateinit var notificationStyle: androidx.media.app.NotificationCompat.MediaStyle

    private var updateTimeLeftJob: Job? = null
    private var timeUntilStop: Duration? =
        stopTime?.let { Duration.between(Instant.now(), it) }

    private var mediaSession: MediaSessionCompat? = null
    var useMediaSession: Boolean = useMediaSession
        set(value) {
            if (field == value) return
            field = value
            rebuildMediaStyleAndNotificationBuilder()
        }
    private val usingTiramisuMediaControls get() = useMediaSession &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    private val channelId = service.getString(
            R.string.player_notification_channel_id
        ).also { channelId ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                    notificationManager.getNotificationChannel(channelId) != null)
                return@also

            val title = service.getString(R.string.player_notification_channel_name)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(channelId, title, importance)
            channel.description = service.getString(
                R.string.player_notification_channel_description)
            channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            notificationManager.createNotificationChannel(channel)
        }

    private val notificationBuilder: NotificationCompat.Builder =
        NotificationCompat.Builder(service, channelId)
            .setOngoing(true)
            .setColorized(true)
            .setSmallIcon(R.drawable.tile_and_notification_icon)
            .setContentIntent(PendingIntent.getActivity(service, 0,
                Intent(service, MainActivity::class.java).apply {
                    action = Intent.ACTION_MAIN
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }, FLAG_IMMUTABLE))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

    /** A notification action that will fire a [PendingIntent.getService] call
     * when triggered. The started service will be provided the [playIntent]. */
    private val playAction = NotificationCompat.Action(
        R.drawable.ic_baseline_play_24,
        service.getString(R.string.play),
        PendingIntent.getService(
            service, playActionRequestCode, playIntent,
            FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT))

    /** A notification action that will fire a [PendingIntent.getService] call
     * when triggered. The started service will be provided the [pauseIntent]. */
    private val pauseAction = NotificationCompat.Action(
        R.drawable.ic_baseline_pause_24,
        service.getString(R.string.pause),
        PendingIntent.getService(
            service, pauseActionRequestCode, pauseIntent,
            FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT))

    /** Return the [playAction] or [pauseAction] depending on the value of the parameter [isPlaying]. */
    private fun togglePlayPauseAction(isPlaying: Boolean) =
        if (isPlaying) pauseAction
        else           playAction

    /** A notification action that will fire a [PendingIntent.getService] call
     * when triggered. The started service will be provided the provided stopIntent. */
    private val stopAction = NotificationCompat.Action(
        R.drawable.ic_baseline_close_24,
        service.getString(R.string.close),
        PendingIntent.getService(
            service, stopActionRequestCode,
            stopIntent, FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT))

    /** A notification action that will fire a [PendingIntent.getService] call
     * when triggered. The started service will be provided the [cancelTimerIntent] */
    private val cancelTimerAction = NotificationCompat.Action(
        R.drawable.ic_baseline_alarm_off_24,
        service.getString(R.string.cancel_stop_timer_action),
        PendingIntent.getService(
            service, cancelTimerRequestCode, cancelTimerIntent,
            FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT))

    init {
        rebuildMediaStyleAndNotificationBuilder()
    }

    private fun rebuildMediaStyleAndNotificationBuilder() {
        // Because the notification is being used for a foreground service,
        // Service.stopForeground and Service.startForeground must be used
        // instead of NotificationManager.cancel to get the notification to
        // reappear in the correct location.
        service.stopForeground(STOP_FOREGROUND_REMOVE)
        mediaSession?.isActive = false

        notificationStyle = androidx.media.app.NotificationCompat.MediaStyle()
        mediaSession = if (!useMediaSession) null else
            MediaSessionCompat(service, PlayerNotification::class.toString()).apply {
                // We use an off center zoomed out app icon for the API 33+
                // media notification because it is used as the background
                // for the media controls card. For pre-API 33 media controls
                // we use the standard app icon because it is displayed in a
                // box at the starting edge of the media controls .
                val background = ContextCompat.getDrawable(
                        service,
                        if (usingTiramisuMediaControls)
                            R.drawable.media_controls_background
                        else R.drawable.ic_launcher_foreground
                    )?.toBitmap()
                setMetadata(MediaMetadataCompat.Builder()
                    .putBitmap(METADATA_KEY_ART, background)
                    .build())
                isActive = true
                notificationStyle.setMediaSession(sessionToken)
                setCallback(object: MediaSessionCompat.Callback() {
                    override fun onPlay() { service.startService(playIntent) }
                    override fun onPause() { service.startService(pauseIntent) }
                    override fun onStop() { service.startService(stopIntent) }
                })
            }
        notificationBuilder.setStyle(notificationStyle)
        service.startForeground(notificationId, updatedNotification())
    }

    fun remove() {
        service.stopForeground(STOP_FOREGROUND_REMOVE)
        mediaSession?.isActive = false
        mediaSession?.release()
    }

    fun update(playbackState: Int, stopTime: Instant?) {
        this.playbackState = playbackState

        timeUntilStop = stopTime?.let { Duration.between(Instant.now(), it) }
        updateTimeLeftJob?.cancel()
        if (stopTime != null)
            updateTimeLeftJob = service.lifecycleScope.launch {
                while (timeUntilStop != null) {
                    delay(1000)
                    timeUntilStop?.minusSeconds(1)?.let {
                        timeUntilStop = it
                        val notification = notificationBuilder
                            .updateText(timeUntilStop).build()
                        notificationManager.notify(notificationId, notification)
                    }
                }
            }

        val notification = updatedNotification(playbackState, timeUntilStop)
        notificationManager.notify(notificationId, notification)
        mediaSession?.setPlaybackState(updatedPlaybackState(playbackState))
    }

    private fun NotificationCompat.Builder.updateText(
        timeUntilStop: Duration?
    ): NotificationCompat.Builder = apply {
        val stateString = service.getString(when(playbackState) {
            STATE_PLAYING -> R.string.playing
            STATE_PAUSED ->  R.string.paused
            else ->          R.string.stopped
        })
        setContentTitle(stateString)

        // Starting with API level 33, the media controls use a text
        // animation that looks really bad with the timer countdown.
        // It also doesn't allow custom actions like the cancel timer
        // action, so we just don't show the timer in this case.
        if (timeUntilStop == null || usingTiramisuMediaControls)
            setContentText(null)
        else setContentText(service.getString(
            R.string.stop_timer_description, timeUntilStop.toHMMSSstring()))
    }

    private fun updatedNotification(
        playbackState: Int = this.playbackState,
        timeUntilStop: Duration? = this.timeUntilStop,
    ): Notification {
        val builder = notificationBuilder
            .updateText(timeUntilStop)
            .clearActions()

        builder.addAction(togglePlayPauseAction(
            isPlaying = playbackState == STATE_PLAYING))
        builder.addAction(stopAction)
        if (timeUntilStop != null)
            builder.addAction(cancelTimerAction)

        // We only show a maximum of two actions in the compact
        // view to prevent the actions from clipping the text
        if (timeUntilStop != null)
            notificationStyle.setShowActionsInCompactView(0, 1)
        else notificationStyle.setShowActionsInCompactView(0)

        return builder.build()
    }

    private fun updatedPlaybackState(playbackState: Int) = playbackStateBuilder
        .setState(playbackState, PLAYBACK_POSITION_UNKNOWN, 1f)
        .setActions(ACTION_PLAY_PAUSE or ACTION_PLAY or
                    ACTION_PAUSE or ACTION_STOP)
        .build()
}