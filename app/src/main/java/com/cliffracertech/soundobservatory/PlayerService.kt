/* This file is part of SoundObservatory, which is released under the
 * Apache License 2.0. See license.md in the project's root directory
 * or use an internet search engine to see the full license. */
package com.cliffracertech.soundobservatory

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class PlayerService: LifecycleService() {
    private val uriPlayerMap = mutableMapOf<String, MediaPlayer>()
    private val _isPlaying = MutableStateFlow(false)
    private lateinit var viewModel: ViewModel

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        viewModel = ViewModel(application)
        lifecycleScope.launch {
            viewModel.playingTracks.collect { updatePlayers(it) }
        }
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

    private fun MediaPlayer.setPaused(paused: Boolean) = if (paused) pause()
                                                         else        start()

    inner class Binder: android.os.Binder() {
        val isPlaying = _isPlaying.asStateFlow()

        fun setIsPlaying(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            uriPlayerMap.forEach { it.value.setPaused(!isPlaying) }
        }

        fun toggleIsPlaying() { setIsPlaying(!_isPlaying.value) }

        fun notifyTrackVolumeChanged(uriString: String, volume: Float) {
            uriPlayerMap[uriString]?.setVolume(volume, volume)
        }
    }
}