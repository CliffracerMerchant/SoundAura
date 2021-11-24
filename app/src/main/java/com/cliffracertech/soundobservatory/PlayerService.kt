/* This file is part of SoundObservatory, which is released under the
 * Apache License 2.0. See license.md in the project's root directory
 * or use an internet search engine to see the full license. */
package com.cliffracertech.soundobservatory

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.Action
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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
    private val viewModel by lazy { ViewModel(application) }

    private var boundToActivity = false
    private var playedAtLeastOnce = false

    val isPlaying = _isPlaying.asStateFlow()

    companion object {
        private const val requestCode = 1
        private const val playPauseKey = "playPause"
        private const val actionStop = "stop service"
        private const val actionSetIsPlaying = "set paused/playing"
        private const val notificationId = 342654432
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val extras = intent?.extras
        if (action == actionStop) {
            Log.d("sounds", "stop request received")
            cleanupAndStopSelf()
        } else if (action == actionSetIsPlaying)
            extras?.getBoolean(playPauseKey)?.let { setIsPlaying(it); Log.d("sounds", "isPlaying set to $it")}
        else Log.d("sounds", "isPlaying initialized to false")

        startForeground(notificationId, notification)

        return super.onStartCommand(intent, flags, startId)
    }

    init {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Log.d("sounds", "viewModel.playingTracks collected")
                viewModel.playingTracks.collect { updatePlayers(it) }
            }
        }
    }

    override fun onBind(intent: Intent): Binder {
        super.onBind(intent)
        boundToActivity = true
        notificationManager.notify(notificationId, notification)
        return Binder()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        boundToActivity = false
        if (!isPlaying.value && !playedAtLeastOnce)
            cleanupAndStopSelf()
        else notificationManager.notify(notificationId, notification)
        return super.onUnbind(intent)
    }

    private fun cleanupAndStopSelf() {
        uriPlayerMap.forEach { it.value.release() }
        stopSelf()
    }

    fun setIsPlaying(isPlaying: Boolean) {
        _isPlaying.value = isPlaying
        if (isPlaying) playedAtLeastOnce = true
        uriPlayerMap.forEach { it.value.setPaused(!isPlaying) }
        notificationManager.notify(notificationId, notification)
    }

    fun toggleIsPlaying() = setIsPlaying(!_isPlaying.value)

    fun setTrackVolume(uriString: String, volume: Float) {
        uriPlayerMap[uriString]?.setVolume(volume, volume)
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
                val player = MediaPlayer.create(this, Uri.parse(track.uriString))
                player?.isLooping = true
                player ?: return@forEachIndexed
            }
            player.setVolume(track.volume, track.volume)
            if (_isPlaying.value != player.isPlaying)
                player.setPaused(!_isPlaying.value)
        }
    }

    private fun MediaPlayer.setPaused(paused: Boolean) =
        if (paused) pause() else start()

    inner class Binder: android.os.Binder() {
        val isPlaying get() = this@PlayerService.isPlaying

        fun setIsPlaying(isPlaying: Boolean) =
            this@PlayerService.setIsPlaying(isPlaying)

        fun toggleIsPlaying() =
            this@PlayerService.toggleIsPlaying()

        fun setTrackVolume(uriString: String, volume: Float) =
            this@PlayerService.setTrackVolume(uriString, volume)
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
                }, FLAG_IMMUTABLE
            ))
    }

    /** A notification action that will toggle the service's isPlaying state. */
    private val togglePlayPauseAction: Action  get() {
        val icon = if (isPlaying.value) R.drawable.pause_to_play
                   else                 R.drawable.play_to_pause
        val description = getString(if (isPlaying.value) R.string.pause_description
                                    else                 R.string.play_description)
        val intent = Intent(this, PlayerService::class.java)
                        .setAction(actionSetIsPlaying)
                        .putExtra(playPauseKey, !isPlaying.value)
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
        val description = getString(if (isPlaying.value) R.string.playing_description
                                    else                 R.string.paused_description)
        val builder = notificationBuilder
            .setContentText(description)
            .clearActions()
            .addAction(togglePlayPauseAction)
        if (!boundToActivity)
            builder.addAction(stopServiceAction)
        return builder.build()
    }
}