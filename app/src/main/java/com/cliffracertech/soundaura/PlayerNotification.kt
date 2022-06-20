/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.app.*
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Context
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
 * or startForeground (which also takes a service parameter) with values for
 * the current playback state in the form of a android.media.session.PlaybackState
 * value, and whether or not the stop action should be shown. Generally
 * startForeground should be called once in the onCreate of the client service,
 * and update should be called thereafter whenever the playback state or the
 * visibility of the stop action changes. The notification can be cleared when
 * the service is stopping by calling the action stopForeground with the service.

 * @param context A context instance. Note that this context is held onto for
 *     PlayerNotification's lifetime, an so may result in memory leaks if used
 *     improperly (e.g. using an activity as the context, and then holding onto
 *     the PlayerNotification instance after the activity should have been destroyed.
 * @param playIntent The intent that, when fired, will cause the service that
 *     PlayerNotification is serving to start its playback.
 * @param pauseIntent The intent that, when fired, will cause the service that
 *     PlayerNotification is serving to pause its playback.
 * @param stopIntent The intent that, when fired, will cause the service that
 *     PlayerNotification is serving to stop its playback.
 */
class PlayerNotification(
    private val context: Context,
    private val playIntent: Intent,
    private val pauseIntent: Intent,
    private val stopIntent: Intent,
) {
    private val playActionRequestCode = 1
    private val pauseActionRequestCode = 2
    private val stopActionRequestCode = 3
    val id = 1
    private val mediaSession = MediaSessionCompat(context, PlayerService::class.toString())

    private val notificationManager =
        context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

    init {
        mediaSession.isActive = true
        mediaSession.setCallback(object: MediaSessionCompat.Callback() {
            override fun onPlay() { context.startService(playIntent) }
            override fun onPause() { context.startService(pauseIntent) }
            override fun onStop() { context.startService(stopIntent) }
        })
    }

    fun startForeground(
        service: Service,
        playbackState: Int,
        showStopAction: Boolean
    ) {
        val notification = updatedNotification(playbackState, showStopAction)
        service.startForeground(id, notification)
        val newPlaybackState = updatedPlaybackState(playbackState, showStopAction)
        mediaSession.setPlaybackState(newPlaybackState)
    }

    fun stopForeground(service: Service) {
        service.stopForeground(true)
        mediaSession.isActive = false
        mediaSession.release()
    }

    fun update(playbackState: Int, showStopAction: Boolean) {
        val notification = updatedNotification(playbackState, showStopAction)
        notificationManager.notify(id, notification)
        val newPlaybackState = updatedPlaybackState(playbackState, showStopAction)
        mediaSession.setPlaybackState(newPlaybackState)
    }

    private val channelId = run {
        val channelId = context.getString(R.string.player_notification_channel_id)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            notificationManager.getNotificationChannel(channelId) == null
        ) {
            val title = context.getString(R.string.player_notification_channel_name)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(channelId, title, importance)
            channel.description = context.getString(
                R.string.player_notification_channel_description)
            channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            notificationManager.createNotificationChannel(channel)
        }
        channelId
    }

    private val notificationStyle =
        androidx.media.app.NotificationCompat.MediaStyle()
            .setMediaSession(mediaSession.sessionToken)

    private val notificationBuilder = run {
        NotificationCompat.Builder(context, channelId)
            .setOngoing(true)
            .setSmallIcon(R.drawable.tile_and_notification_icon)
            .setContentIntent(PendingIntent.getActivity(context, 0,
                Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_MAIN
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }, FLAG_IMMUTABLE))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(notificationStyle)
    }

    /** A notification action that will fire a PendingIntent.getService call
     * when triggered. The started service will be provided the provided playIntent. */
    private val playAction = NotificationCompat.Action(
        R.drawable.ic_baseline_play_24,
        context.getString(R.string.play),
        PendingIntent.getService(
            context, playActionRequestCode,
            playIntent, FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT))

    /** A notification action that will fire a PendingIntent.getService call
     * when triggered. The started service will be provided the provided pauseIntent. */
    private val pauseAction = NotificationCompat.Action(
        R.drawable.ic_baseline_pause_24,
        context.getString(R.string.pause),
        PendingIntent.getService(
            context, pauseActionRequestCode,
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
        context.getString(R.string.close),
        PendingIntent.getService(
            context, stopActionRequestCode,
            stopIntent, FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT))

    private fun updatedNotification(
        playbackState: Int,
        showStopAction: Boolean
    ): Notification {
        val description = context.getString(when(playbackState) {
            STATE_PLAYING -> R.string.playing
            STATE_PAUSED ->  R.string.paused
            else ->          R.string.stopped
        })
        val playPauseAction = togglePlayPauseAction(
            isPlaying = playbackState == STATE_PLAYING)
        val builder = notificationBuilder.setContentTitle(description)
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