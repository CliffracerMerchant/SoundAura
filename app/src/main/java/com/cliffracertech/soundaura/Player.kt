/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri

/**
 * A MediaPlayer wrapper that allows for seamless looping of the provided uri.
 * If there is a problem with the provided uri, then the inner MediaPlayer
 * instance creation can fail. In this case, isPlaying will always return false
 * and setting isPlaying will have no effect.
 *
 * @param context A context instance. Note that the provided context instance
 *     is held onto for the lifetime of the Player instance, and so should not
 *     be a component that the Player might outlive.
 * @param uriString A string describing the uri that the Player will play.
 * @param startPlaying The initial isPlaying state of the Player. If true, the
 *     player will have start called on it after a successful creation.
 * @param initialVolume The initial volume of the player. This volume will call
 *     setMonoVolume for the player, and so will apply to both audio channels.
 * @param onFail A callback that will be invoked if the MediaPlayer creation
 *     fails. The parameter is the uri, in string form, of the file being
 *     read. This callback is used instead of, e.g., a factory method that
 *     can return null if creation fails due to the fact that creation can
 *     fail at any point in the future when the player is looped.
 */
class Player(
    private val context: Context,
    private val uriString: String,
    startPlaying: Boolean = false,
    initialVolume: Float = 1f,
    private val onFail: (String) -> Unit,
) {
    private val uri = Uri.parse(uriString)
    private var nextPlayer: MediaPlayer? = null
    private var currentPlayer: MediaPlayer? = createPlayer()

    /** The isPlaying state of the player. Setting the property
     * to false will pause the player rather than stopping it. */
    var isPlaying
        get() = currentPlayer?.isPlaying ?: false
        set(value) {
            if (value == currentPlayer?.isPlaying) return
            if (value) currentPlayer?.start()
            else currentPlayer?.pause()
        }

    init {
        prepareNextPlayer()
        if (startPlaying)
            isPlaying = true
        setMonoVolume(initialVolume)
    }

    private fun prepareNextPlayer() {
        val currentPlayer = this.currentPlayer ?: return
        nextPlayer = createPlayer()
        currentPlayer.setNextMediaPlayer(nextPlayer)
        currentPlayer.setOnCompletionListener {
            it.release()
            this.currentPlayer = nextPlayer
            prepareNextPlayer()
        }
    }

    private fun createPlayer() =
        MediaPlayer.create(context, uri) ?: run {
            onFail(uriString)
            null
        }

    /** Set the volume for both audio channels at once. */
    fun setMonoVolume(volume: Float) {
        currentPlayer?.setVolume(volume, volume)
        nextPlayer?.setVolume(volume, volume)
    }

    fun release() {
        currentPlayer?.release()
        nextPlayer?.release()
    }
}