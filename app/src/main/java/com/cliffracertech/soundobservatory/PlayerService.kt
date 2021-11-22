/* This file is part of SoundObservatory, which is released under the
 * Apache License 2.0. See license.md in the project's root directory
 * or use an internet search engine to see the full license. */
package com.cliffracertech.soundobservatory

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@ExperimentalCoroutinesApi
@ExperimentalAnimationGraphicsApi
@ExperimentalComposeUiApi
@ExperimentalAnimationApi
class PlayerService: LifecycleService() {
    private val uriPlayerMap = mutableMapOf<String, MediaPlayer>()
    private val _isPlaying = MutableStateFlow(false)
    private lateinit var viewModel: ViewModel

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        viewModel = ViewModel(application)
        lifecycleScope.launch {
            viewModel.playingTracks.collect { updatePlayers(it) }
        }

        startForeground(1, notification(_isPlaying.value))
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent): Binder {
        super.onBind(intent)
        return Binder()
    }

    private fun updatePlayers(tracks: List<Track>) {
        val uris = tracks.map { it.uriString }
        uriPlayerMap.keys.retainAll {
            val result = it in uris
            if (!result) uriPlayerMap[it]?.release()
            result
        }
        tracks.forEachIndexed { index, track ->
            val player = uriPlayerMap.getOrPut(track.uriString) {
                MediaPlayer.create(this, Uri.parse(track.uriString)).apply {
                    isLooping = true
                } ?: return@forEachIndexed
            }
            player.setVolume(track.volume, track.volume)
            if (_isPlaying.value != player.isPlaying)
                player.setPaused(!_isPlaying.value)
        }
    }

    private fun MediaPlayer.setPaused(paused: Boolean) =
        if (paused) pause() else start()

    inner class Binder: android.os.Binder() {
        val isPlaying = _isPlaying.asStateFlow()

        fun setIsPlaying(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            uriPlayerMap.forEach { it.value.setPaused(!isPlaying) }
            notificationManager.notify(1, notification(isPlaying))
        }

        fun toggleIsPlaying() { setIsPlaying(!_isPlaying.value) }

        fun notifyTrackVolumeChanged(uriString: String, volume: Float) {
            uriPlayerMap[uriString]?.setVolume(volume, volume)
        }
    }

    private val notificationManager by lazy {
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }

    private val notificationChannelId  by lazy {
        val channelId = getString(R.string.player_notification_channel_id)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            notificationManager.getNotificationChannel(channelId) == null
        ) {
            val title = getString(R.string.player_notification_channel_name)
            val description = getString(R.string.player_notification_channel_description)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(channelId, title, importance)
            channel.description = description
            notificationManager.createNotificationChannel(channel)
        }
        channelId
    }

    private val notificationBuilder by lazy {
        NotificationCompat.Builder(this, notificationChannelId)
            .setContentTitle(getString(R.string.app_name))
            .setOngoing(true)
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setContentIntent(PendingIntent.getActivity(this, 0,
                Intent(this, MainActivity::class.java).apply {
                    action = Intent.ACTION_MAIN
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }, PendingIntent.FLAG_IMMUTABLE
            ))
    }

//    private val playPauseAction = NotificationCompat.Action(
//        R.drawable.pause_to_play,
//        getString(R.string.play_description),
//        PendingIntent.getService(this, ())
//    )

    private fun notification(playing: Boolean) = notificationBuilder
        .setContentText(getString(if (playing) R.string.playing_description
        else         R.string.paused_description))
        //.addAction(playPauseAction)
        .build()
}