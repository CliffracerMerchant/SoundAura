/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.service

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import androidx.core.net.toUri
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import java.io.File

/** An active (i.e. part of the current sound mix) [Playable]. */
class Playable(
    val name: String,
    /** Whether or not a playlist has shuffle enabled. This value
     * has no meaning for a track. */
    val shuffleEnabled: Boolean,
    /** The volume of the [Playable]. */
    val volume: Float,
    uriStrings: String,
) {
    /** The list of [Uri]s in the [Playable]. If the [Playable] is
     * a track instead of a playlist, uris will consist of only the
     * [Uri] for that track. */
    val uris: ImmutableList<Uri> = uriStrings
        .split(File.pathSeparatorChar)
        .map(String::toUri).toImmutableList()
}

/**
 * A [MediaPlayer] wrapper that allows for seamless looping of the provided uri.
 * If there is a problem with the provided uri, then the inner MediaPlayer
 * instance creation can fail. In this case, calling [play] will have no effect.
 * The current volume for both audio channels can also be retrieved or set via
 * the property [volume].
 *
 * The methods [play], [pause], and [stop] can be used to control playback of
 * the [Player]. These methods correspond to the [MediaPlayer] methods of the
 * same name, except for [stop]. [Player]'s [stop] method is functionally the
 * same as pausing while seeking to the start of the media.
 *
 * @param context A [Context] instance. Note that the provided context instance
 *     is held onto for the lifetime of the Player instance, and so should not
 *     be a component that the Player might outlive.
 * @param uriString A string describing the uri that the Player will play.
 * @param startPlaying The initial isPlaying state of the Player. If true, the
 *     player will have start called on it after a successful creation.
 * @param initialVolume The initial volume of the player. This value will be
 *     used to set the volume property for the Player, and so will apply to
 *     both audio channels.
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

    var volume: Float = initialVolume
        set(value) {
            field = value
            currentPlayer?.setVolume(volume, volume)
            nextPlayer?.setVolume(volume, volume)
        }

    private fun MediaPlayer.attempt(action: MediaPlayer.() -> Unit) {
        try { action() }
        // An IllegalStateException can be thrown here if the pause is called
        // immediately after creation when the player is still initializing
        catch(e: java.lang.IllegalStateException) {
            setOnPreparedListener {
                action()
                setOnPreparedListener(null)
            }
        }
    }

    fun play() { currentPlayer?.attempt(MediaPlayer::start) }
    fun pause() { currentPlayer?.attempt(MediaPlayer::pause) }
    fun stop() { currentPlayer?.attempt { pause(); seekTo(0) }}

    init {
        prepareNextPlayer()
        if (startPlaying)
            play()
        volume = initialVolume
    }

    private fun prepareNextPlayer() {
        val currentPlayer = this.currentPlayer ?: return
        nextPlayer = createPlayer()
        nextPlayer?.setVolume(volume, volume)
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

    fun release() {
        currentPlayer?.release()
        nextPlayer?.release()
    }
}

/**
 * A collection of [Player] instances.
 *
 * [PlayerSet] manages a collection of [Player] instances for a list of
 * [Track]s. The collection of players is updated by calling the function [update]
 * with the new [List]`<Track>` instance and a boolean value indicating whether
 * newly added tracks should start playing immediately.
 *
 * Whether or not the collection of players is empty can be queried with the
 * property [isEmpty]. The property [isInitialized], which will start as false but
 * will be set to true after the first call to update, is also provided so that
 * the [PlayerSet] being empty because the provided [List]`<Track>` is empty can
 * be differentiated from the [PlayerSet] being empty because update hasn't
 * been called yet (this might happen for instance if update is called in
 * response to a asynchronous database access method).
 *
 * The playing/paused/stopped state can be set for all players at once with the
 * methods [play], [pause], and [stop], respectively. The volume for individual
 * tracks can be set with the method [setPlayerVolume]. The function [releaseAll]
 * should be called before the PlayerSet is destroyed so that all Player
 * instances can be released first.
 *
 * @param context A [Context] instance. Note that the context instance will be
 *     held onto for the lifetime of the [PlayerSet].
 * @param onCreatePlayerFailure The callback that will be invoked when the
 *     [Player] creation for a particular [Track] fails. The single string
 *     parameter is the uri string of the [Track] whose [Player] creation failed.
 */
class PlayerSet(
    private val context: Context,
    private val onCreatePlayerFailure: (String) -> Unit
) {
    var isInitialized = false
        private set
    private val uriPlayerMap = mutableMapOf<String, Player>()

    val isEmpty get() = uriPlayerMap.isEmpty()

    fun play() = uriPlayerMap.values.forEach(Player::play)
    fun pause() = uriPlayerMap.values.forEach(Player::pause)
    fun stop() = uriPlayerMap.values.forEach(Player::stop)

    fun setPlayerVolume(uriString: String, volume: Float) =
        uriPlayerMap[uriString]?.run {
            this.volume = volume
        } ?: Unit

    fun releaseAll() = uriPlayerMap.values.forEach { it.release() }

    fun update(tracks: List<ActiveTrack>, startPlayingNewTracks: Boolean) {
        isInitialized = true

        // remove players whose track is no longer in the track list
        val uris = tracks.map { it.uriString }
        uriPlayerMap.keys.retainAll {
            val inNewList = it in uris
            if (!inNewList)
                uriPlayerMap[it]?.release()
            inNewList
        }
        // add players for tracks newly added to the track list
        tracks.forEach { track ->
            uriPlayerMap.getOrPut(track.uriString) {
                Player(context = context,
                       uriString = track.uriString,
                       startPlaying = startPlayingNewTracks,
                       initialVolume = track.volume,
                       onFail = onCreatePlayerFailure)
            }
        }
    }
}