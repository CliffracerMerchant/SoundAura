/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.app.*
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.session.PlaybackStateCompat.*
import androidx.core.app.NotificationCompat

/**
 * A manager for a notification for a foreground media playing service.
 *
 * PlayerNotification can post a notification that contains a string describing
 * a playback state (e.g. playing, paused), a toggle play/pause action, and an
 * optional stop action. This can be accomplished by calling the methods update
 * or startForeground with values for the current playback state in the form of
 * a PlaybackStateCompat value, and whether or not the stop action should be
 * shown. Generally startForeground should be called once in the onCreate of
 * the client service, and update should be called thereafter whenever the
 * playback state or the visibility of the stop action changes. The notification
 * can be cleared when the service is stopping with the function stopForeground.
 *
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
 * @param useMediaSession Whether or not a MediaSessionCompat instance should
 *     be tied to the notification. If true, the notification will appear in
 *     the media session section of the status bar. If false, the notification
 *     will appear as a regular notification instead. PlayerNotification's
 *     property of the same name can be used to change this after creation.
 */
class PlayerNotification(
    private val service: Service,
    private val playIntent: Intent,
    private val pauseIntent: Intent,
    private val stopIntent: Intent,
    useMediaSession: Boolean
) {
    private val playActionRequestCode = 1
    private val pauseActionRequestCode = 2
    private val stopActionRequestCode = 3
    private val notificationId get() = if (useMediaSession) 1 else 2
    private val notificationManager =
        service.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

    private var playbackState = STATE_STOPPED
    private var showStopAction = false
    private lateinit var notificationStyle: androidx.media.app.NotificationCompat.MediaStyle

    private fun updateNotificationStyle() {
        notificationStyle = androidx.media.app.NotificationCompat.MediaStyle()
        mediaSession?.let { notificationStyle.setMediaSession(it.sessionToken) }
        notificationBuilder.setStyle(notificationStyle)
    }

    private var mediaSession: MediaSessionCompat? = null
    var useMediaSession: Boolean = false
        set(value) {
            // Because the notification is being used for a foreground service,
            // Service.stopForeground and Service.startForeground must be used
            // instead of NotificationManager.cancel to get the notification to
            // reappear in the correct location.
            service.stopForeground(true)
            field = value
            val newMediaSession = if (!value) null else
                MediaSessionCompat(service, PlayerNotification::class.toString())
            mediaSession = newMediaSession
            updateNotificationStyle()
            service.startForeground(notificationId, updatedNotification())
        }

    private val channelId = run {
        val channelId = service.getString(R.string.player_notification_channel_id)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            notificationManager.getNotificationChannel(channelId) == null
        ) {
            val title = service.getString(R.string.player_notification_channel_name)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(channelId, title, importance)
            channel.description = service.getString(
                R.string.player_notification_channel_description)
            channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            notificationManager.createNotificationChannel(channel)
        }
        channelId
    }

    private val notificationBuilder =
        NotificationCompat.Builder(service, channelId)
            .setOngoing(true)
            .setSmallIcon(R.drawable.tile_and_notification_icon)
            .setContentTitle(service.getString(R.string.app_name))
            .setContentIntent(PendingIntent.getActivity(service, 0,
                Intent(service, MainActivity::class.java).apply {
                    action = Intent.ACTION_MAIN
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }, FLAG_IMMUTABLE))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

    init {
        // useMediaSession is set again here so that the custom setter will run
        this.useMediaSession = useMediaSession
        mediaSession?.isActive = true
        mediaSession?.setCallback(object: MediaSessionCompat.Callback() {
            override fun onPlay() { service.startService(playIntent) }
            override fun onPause() { service.startService(pauseIntent) }
            override fun onStop() { service.startService(stopIntent) }
        })
        updateNotificationStyle()
    }

    fun startForeground(
        playbackState: Int,
        showStopAction: Boolean
    ) {
        val notification = updatedNotification(playbackState, showStopAction)
        service.startForeground(notificationId, notification)
        mediaSession?.setPlaybackState(
            updatedPlaybackState(playbackState, showStopAction))
        this.playbackState = playbackState
        this.showStopAction = showStopAction
    }

    fun stopForeground() {
        service.stopForeground(true)
        mediaSession?.isActive = false
        mediaSession?.release()
    }

    fun update(
        playbackState: Int,
        showStopAction: Boolean
    ) {
        val notification = updatedNotification(playbackState, showStopAction)
        notificationManager.notify(notificationId, notification)
        mediaSession?.setPlaybackState(
            updatedPlaybackState(playbackState, showStopAction))
        this.playbackState = playbackState
        this.showStopAction = showStopAction
    }

    /** A notification action that will fire a PendingIntent.getService call
     * when triggered. The started service will be provided the provided playIntent. */
    private val playAction = NotificationCompat.Action(
        R.drawable.ic_baseline_play_24,
        service.getString(R.string.play),
        PendingIntent.getService(
            service, playActionRequestCode,
            playIntent, FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT))

    /** A notification action that will fire a PendingIntent.getService call
     * when triggered. The started service will be provided the provided pauseIntent. */
    private val pauseAction = NotificationCompat.Action(
        R.drawable.ic_baseline_pause_24,
        service.getString(R.string.pause),
        PendingIntent.getService(
            service, pauseActionRequestCode,
            pauseIntent, FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT))

    /** Return the playAction or pauseAction depending on the value of the
     * parameter isPlaying. */
    private fun togglePlayPauseAction(isPlaying: Boolean) =
        if (isPlaying) pauseAction
        else           playAction

    /** A notification action that will fire a PendingIntent.getService call
     * when triggered. The started service will be provided the provided stopIntent. */
    private val stopAction = NotificationCompat.Action(
        R.drawable.ic_baseline_close_24,
        service.getString(R.string.close),
        PendingIntent.getService(
            service, stopActionRequestCode,
            stopIntent, FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT))

    private fun updatedNotification(
        playbackState: Int = this.playbackState,
        showStopAction: Boolean = this.showStopAction
    ): Notification {
        val description = service.getString(when(playbackState) {
            STATE_PLAYING -> R.string.playing
            STATE_PAUSED ->  R.string.paused
            else ->          R.string.stopped
        })
        val playPauseAction = togglePlayPauseAction(
            isPlaying = playbackState == STATE_PLAYING)
        val builder = notificationBuilder
            .setContentText(description)
            .clearActions()
            .addAction(playPauseAction)

        if (showStopAction) {
            builder.addAction(stopAction)
            notificationStyle.setShowActionsInCompactView(0, 1)
        } else notificationStyle.setShowActionsInCompactView(0)

        return builder.build()
    }

    private val playbackStateBuilder = PlaybackStateCompat.Builder()

    private fun updatedPlaybackState(
        playbackState: Int,
        showStopAction: Boolean
    ) = playbackStateBuilder
        .setState(playbackState, PLAYBACK_POSITION_UNKNOWN, 1f)
        .setActions(ACTION_PLAY_PAUSE or
            if (showStopAction) ACTION_STOP else 0L)
        .build()
}