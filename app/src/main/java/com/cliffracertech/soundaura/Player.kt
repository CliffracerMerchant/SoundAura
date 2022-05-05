/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri

/** A MediaPlayer wrapper that prepares a pair of MediaPlayers. The
 * pair of players allows for seamless looping of the provided uri. */
class Player(
    private val context: Context,
    uriString: String,
) {
    private val uri = Uri.parse(uriString)
    private lateinit var nextPlayer: MediaPlayer
    private var currentPlayer = MediaPlayer.create(context, uri)?.prepareNext()
        ?: throw IllegalArgumentException("Media player creation for the" +
                                          "provided uri string $uriString failed.")

    private fun MediaPlayer.prepareNext(): MediaPlayer = apply {
        nextPlayer = MediaPlayer.create(context, uri)
        setNextMediaPlayer(nextPlayer)
        setOnCompletionListener {
            it.release()
            currentPlayer = nextPlayer.prepareNext()
        }
    }

    /** The isPlaying state of the player. Setting the property
     * to false will pause the player rather than stopping it. */
    var isPlaying get() = currentPlayer.isPlaying
        set(value) {
            if (value == currentPlayer.isPlaying) return
            if (value) currentPlayer.start()
            else       currentPlayer.pause()
        }

    /** Set the volume for both audio channels at once. */
    fun setMonoVolume(volume: Float) = currentPlayer.setVolume(volume, volume)

    fun release() {
        currentPlayer.release()
        nextPlayer.release()
    }
}