/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.service

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri

data class ActivePlaylistSummary(
    val id: Long,
    val shuffle: Boolean,
    val volume: Float)

typealias ActivePlaylist = Map.Entry<ActivePlaylistSummary, List<Uri>>
val ActivePlaylist.id get() = key.id
val ActivePlaylist.shuffle get() = key.shuffle
val ActivePlaylist.volume get() = key.volume
val ActivePlaylist.tracks get() = value

/**
 * A [MediaPlayer] wrapper that allows for seamless looping of the provided
 * [ActivePlaylist]. The [update] method can be used when the [ActivePlaylist]'s
 * properties change. The property [volume] describes the current volume for
 * both audio channels, and is initialized to the [ActivePlaylist]'s [volume]
 * field.
 *
 * The methods [play], [pause], and [stop] can be used to control playback of
 * the Player. These methods correspond to the [MediaPlayer] methods of the
 * same name, except for [stop]. [Player]'s [stop] method is functionally the
 * same as pausing while seeking to the start of the media.
 *
 * If there is a problem with one or more [Uri]s within the [ActivePlaylist]'s
 * [tracks], playback of the next track will be attempted until one is found
 * that can be played. If [MediaPlayer] creation fails for all of the tracks,
 * no playback will occur, and calling [play] will have no effect. When one or
 * more tracks fail to play, the provided callback [onPlaybackFailure] will be
 * invoked.
 *
 * @param context A [Context] instance. Note that the provided context instance
 *     is held onto for the lifetime of the Player instance, and so should not
 *     be a component that the Player might outlive.
 * @param playlist The [ActivePlaylist] whose contents will be played
 * @param startPlaying Whether or not the Player should call start upon
 *     successful MediaPlayer creation
 * @param onPlaybackFailure A callback that will be invoked if MediaPlayer
 *     creation fails for one or more [Uri]s. This callback is used instead
 *     of, e.g., a factory method that can return null if creation fails due
 *     to the fact that creation can fail at any point in the future when
 *     the player is looped.
 */
class Player(
    private val context: Context,
    playlist: ActivePlaylist,
    startPlaying: Boolean = false,
    private val onPlaybackFailure: (List<Uri>) -> Unit,
) {
    private val trackCount = playlist.tracks.size
    private var uriIterator = iteratorFor(playlist)

    var volume: Float = playlist.volume
        set(value) {
            field = value
            currentPlayer?.setVolume(volume, volume)
            nextPlayer?.setVolume(volume, volume)
        }

    private var currentPlayer: MediaPlayer? = createPlayerForNextUri()
    private var nextPlayer: MediaPlayer? = null

    init {
        prepareNextPlayer()
        volume = playlist.volume
        if (startPlaying) play()
    }

    fun play() { currentPlayer?.attempt(MediaPlayer::start) }
    fun pause() { currentPlayer?.attempt(MediaPlayer::pause) }
    fun stop() { currentPlayer?.attempt { pause(); seekTo(0) }}

    fun update(newPlaylist: ActivePlaylist, startPlaying: Boolean) {
        volume = newPlaylist.volume
        uriIterator = iteratorFor(newPlaylist)
        prepareNextPlayer()
        if (startPlaying) play()
    }

    fun release() {
        currentPlayer?.release()
        nextPlayer?.release()
    }

    private fun createPlayerForNextUri(): MediaPlayer? {
        // The number of player creation attempts is recorded and compared
        // to the playlist's track count so that we know when we have done
        // one full loop of the playlist's tracks
        var attempts = 0
        var player: MediaPlayer? = null
        var failedUris: MutableList<Uri>? = null

        while (player == null && ++attempts <= trackCount) {
            val uri = uriIterator.next()
            player = MediaPlayer.create(context, uri).also {
                if (it != null) return@also
                if (failedUris == null)
                    failedUris = mutableListOf(uri)
                else failedUris?.add(uri)
            }
        }
        failedUris?.let(onPlaybackFailure)
        return player
    }

    private fun prepareNextPlayer() {
        val currentPlayer = this.currentPlayer ?: return
        nextPlayer = createPlayerForNextUri()
        nextPlayer?.setVolume(volume, volume)
        currentPlayer.setNextMediaPlayer(nextPlayer)
        currentPlayer.setOnCompletionListener {
            it.release()
            this.currentPlayer = nextPlayer
            prepareNextPlayer()
        }
    }

    /** Attempt the [action] on the receiver [MediaPlayer]. In the case of a still-
     * initializing [MediaPlayer] a [MediaPlayer.OnPreparedListener] will automatically
     * be added to execute the [action] when the [MediaPlayer] is ready. */
    private fun MediaPlayer.attempt(action: MediaPlayer.() -> Unit) =
        try { action() }
        catch(e: java.lang.IllegalStateException) {
            setOnPreparedListener {
                action()
                it.setOnPreparedListener(null)
            }
        }

    private fun iteratorFor(playlist: ActivePlaylist): Iterator<Uri> = (
            if (!playlist.shuffle) InfiniteSequence(playlist.tracks)
            else ShuffledInfiniteSequence(playlist.tracks, memorySize = 3)
        ).iterator()
}

/**
 * A collection of [Player] instances.
 *
 * [PlayerMap] manages a collection of [Player] instances for a collection
 * of [ActivePlaylist]s. The collection of [Player]s is updated via the
 * method [update]. Whether or not the collection of players is empty can
 * be queried with the property [isEmpty].
 *
 * The playing/paused/stopped state can be set for all [Player]s at once
 * with the methods [play], [pause], and [stop]. The volume for individual
 * playlists can be set with the method [setPlayerVolume]. The method
 * [releaseAll] should be called before the PlayerMap is destroyed so that
 * all [Player] instances can be released first.
 *
 * @param context A [Context] instance. Note that the context instance
 *     will be held onto for the lifetime of the [PlayerSet].
 * @param onPlaybackFailure The callback that will be invoked
 *     when playback for the provided list of [Uri]s has failed
 */
class PlayerMap(
    private val context: Context,
    private val onPlaybackFailure: (uris: List<Uri>) -> Unit,
) {
    private var playerMap: MutableMap<Long, Player> = hashMapOf()

    val isEmpty get() = playerMap.isEmpty()

    fun play() = playerMap.values.forEach(Player::play)
    fun pause() = playerMap.values.forEach(Player::pause)
    fun stop() = playerMap.values.forEach(Player::stop)

    fun setPlayerVolume(playlistId: Long, volume: Float) {
        playerMap[playlistId]?.volume = volume
    }

    fun releaseAll() = playerMap.values.forEach(Player::release)

    /** Update the PlayerSet with new [Player]s to match the provided [playlists].
     * If [startPlaying] is true, playback will start immediately. Otherwise, the
     * [Player]s will begin paused. */
    fun update(playlists: Map<ActivePlaylistSummary, List<Uri>>, startPlaying: Boolean) {
        val oldMap = playerMap
        playerMap = HashMap(playlists.size)

        for (playlist in playlists) {
            val existingPlayer = oldMap
                .remove(playlist.id)
                ?.apply { update(playlist, startPlaying) }

            playerMap[playlist.id] = existingPlayer ?:
                Player(context, playlist, startPlaying, onPlaybackFailure)
        }
        oldMap.values.forEach(Player::release)
    }
}