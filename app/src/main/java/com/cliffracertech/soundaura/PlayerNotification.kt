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
import android.media.session.PlaybackState.STATE_PAUSED
import android.media.session.PlaybackState.STATE_PLAYING
import android.os.Build
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
 * visibility of the stop action changes.

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
    private val playPauseActionRequestCode = 1
    private val stopActionRequestCode = 2
    val id = 1

    private val notificationManager =
        context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

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

    private val notificationStyle = androidx.media.app.NotificationCompat.MediaStyle()

    private val notificationBuilder =
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

    /** A notification action that will toggle the service's isPlaying state. */
    private fun togglePlayPauseAction(isPlaying: Boolean): NotificationCompat.Action {
        val icon = if (isPlaying) R.drawable.ic_baseline_pause_24
                   else           R.drawable.ic_baseline_play_24
        val description = context.getString(if (isPlaying) R.string.pause
                                            else           R.string.play)
        val intent = if (isPlaying) pauseIntent
                     else           playIntent
        val pendingIntent = PendingIntent.getService(
            context, playPauseActionRequestCode, intent,
            FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE)

        return NotificationCompat.Action.Builder(
            icon, description, pendingIntent
        ).build()
    }

    /** A notification action that will stop the service when triggered. */
    private val stopAction = run {
        val icon = R.drawable.ic_baseline_close_24
        val description = context.getString(R.string.close)
        val pendingIntent = PendingIntent.getService(
            context, stopActionRequestCode, stopIntent, FLAG_IMMUTABLE)

        NotificationCompat.Action.Builder(
            icon, description, pendingIntent
        ).build()
    }

    private fun updatedNotification(
        playbackState: Int,
        showStopAction: Boolean
    ): Notification {
        val description = context.getString(when(playbackState) {
            STATE_PLAYING -> R.string.playing
            STATE_PAUSED ->  R.string.paused
            else ->          R.string.stopped
        })

        val builder = notificationBuilder
            .setContentTitle(description)
            .clearActions()
            .addAction(togglePlayPauseAction(
                isPlaying = playbackState == STATE_PLAYING))

        if (showStopAction) {
            builder.addAction(stopAction)
            notificationStyle.setShowActionsInCompactView(0, 1)
        } else notificationStyle.setShowActionsInCompactView(0)

        return builder.build()
    }

    fun startForeground(
        service: Service,
        playbackState: Int,
        showStopAction: Boolean
    ) {
        val notification = updatedNotification(playbackState, showStopAction)
        service.startForeground(id, notification)
    }

    fun update(playbackState: Int, showStopAction: Boolean) {
        val notification = updatedNotification(playbackState, showStopAction)
        notificationManager.notify(id, notification)
    }
}