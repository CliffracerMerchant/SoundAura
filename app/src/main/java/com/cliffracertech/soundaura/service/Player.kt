/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.service

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import java.io.IOException

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
 *     be a [Context] that the Player might outlive.
 * @param playlist The [ActivePlaylist] whose contents will be played
 * @param startImmediately Whether or not the Player should start playback
 *     as soon as it is ready
 * @param onPlaybackFailure A callback that will be invoked if MediaPlayer
 *     creation fails for one or more [Uri]s in the playlist
 */
class Player(
    private val context: Context,
    playlist: ActivePlaylist,
    startImmediately: Boolean = false,
    private val onPlaybackFailure: (List<Uri>) -> Unit,
) {
    private var mediaPlayer: MediaPlayer? = null
    private var uriIterator = uriIterator(playlist)
    private var tracks = playlist.tracks
    private var shuffle = playlist.shuffle
    // Without tracking the intended playing/paused state in this property,
    // an issue can occur if an attempt to pause is made when the internal
    // MediaPlayer is in the process of switching to the next track in a
    // playlist that will cause the pause command to be ignored. This is
    // resolved by using the value of isPlaying in the on completion listener.
    private var isPlaying = startImmediately

    private val onCompletionListener = MediaPlayer.OnCompletionListener {
        initializePlayerForNextUri(startImmediately = isPlaying)
    }

    init {
        initializePlayerForNextUri(startImmediately)
        mediaPlayer?.initializeFor(playlist)
    }

    fun play() {
        isPlaying = true
        mediaPlayer?.start()
    }
    fun pause() {
        isPlaying = false
        mediaPlayer?.pause()
    }
    fun stop() {
        isPlaying = false
        if (tracks.size < 2) {
            mediaPlayer?.pause()
            mediaPlayer?.seekTo(0)
        } else {
            uriIterator = uriIterator(tracks, shuffle)
            initializePlayerForNextUri(startImmediately = false)
        }
    }
    fun setVolume(volume: Float) {
        mediaPlayer?.setVolume(volume, volume)
    }

    /** Reset the Player to play the [newPlaylist]*/
    fun update(newPlaylist: ActivePlaylist, startImmediately: Boolean) {
        isPlaying = startImmediately
        setVolume(newPlaylist.volume)
        if (newPlaylist.shuffle != shuffle || newPlaylist.tracks != tracks) {
            shuffle = newPlaylist.shuffle
            tracks = newPlaylist.tracks
            uriIterator = uriIterator(newPlaylist)
            initializePlayerForNextUri(startImmediately)
            mediaPlayer?.initializeFor(newPlaylist)
            // initializePlayerForNextUri will start the player if startImmediately
            // is true, so we don't need to call start manually here
        } else if (startImmediately)
            mediaPlayer?.start()
    }

    fun release() {
        mediaPlayer?.reset()
        mediaPlayer?.release()
    }

    private fun uriIterator(uris: List<Uri>, shuffle: Boolean) = (
            if (!shuffle) InfiniteSequence(uris)
            else ShuffledInfiniteSequence(
                unshuffledValues = uris,
                memorySize = maxOf(1, uris.size / 3))
        ).iterator()
    private fun uriIterator(playlist: ActivePlaylist) =
        uriIterator(playlist.tracks, playlist.shuffle)

    /**
     * Determine the next target [Uri], and either create a new [MediaPlayer]
     * instance if [mediaPlayer] is null, or attempt to reset the existing
     * player to use the target [Uri] as a data source. When the new or
     * existing player is prepared, playback will start immediately if
     * [startImmediately] is true.
     *
     * initializePlayerForNextUri must be called once for each [Uri] that is
     * to be played. A single track playlist only needs to call it once, but
     * a multi-track playlist will need to have it called for each track.
     */
    private fun initializePlayerForNextUri(startImmediately: Boolean) {
        // The number of player creation/data source setting attempts is
        // recorded and compared to the playlist's track count so that we
        // know when we have done one full loop of the playlist's tracks
        var attempts = 0
        var failedUris: MutableList<Uri>? = null
        var newPlayer: MediaPlayer? = null

        while (newPlayer == null && ++attempts <= tracks.size) {
            val uri = uriIterator.next()
            newPlayer = mediaPlayer.let {
                if (it == null)
                    MediaPlayer.create(context, uri)
                else try {
                    it.reset()
                    it.setDataSource(context, uri)
                    it.prepare(); it
                } catch(e: IOException) { null }
            }
            if (newPlayer == null) {
                if (failedUris == null)
                    failedUris = mutableListOf(uri)
                else failedUris.add(uri)
            } else if (startImmediately)
                newPlayer.start()
        }
        failedUris?.let(onPlaybackFailure)
        mediaPlayer = newPlayer
    }

    /**
     * Set the receiver's volume, [MediaPlayer.isLooping] property, and
     * [MediaPlayer.setOnCompletionListener] to their appropriate values
     * to play the content of [playlist]. init must be called only once
     * for each [ActivePlaylist].
     */
    private fun MediaPlayer.initializeFor(playlist: ActivePlaylist) {
        setVolume(playlist.volume, playlist.volume)
        isLooping = playlist.tracks.size < 2
        setOnCompletionListener(
            if (playlist.tracks.size < 2) null
            else onCompletionListener)
    }
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

    fun setPlayerVolume(playlistId: Long, volume: Float) =
        playerMap[playlistId]?.setVolume(volume)

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