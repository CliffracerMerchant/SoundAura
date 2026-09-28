/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.service

import android.content.Context
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.util.Log
import androidx.media3.common.C.WAKE_MODE_LOCAL
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player.COMMAND_PLAY_PAUSE
import androidx.media3.common.Player.COMMAND_PREPARE
import androidx.media3.common.Player.COMMAND_SET_REPEAT_MODE
import androidx.media3.common.Player.COMMAND_SET_SHUFFLE_MODE
import androidx.media3.common.Player.COMMAND_SET_VOLUME
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.exoplayer.ExoPlayer
import com.cliffracertech.soundaura.logd

data class ActivePlaylistSummary(
    val id: Long,
    val shuffle: Boolean,
    val volume: Float,
    val volumeBoostDb: Int)

typealias ActivePlaylist = Map.Entry<ActivePlaylistSummary, List<Uri>>
val ActivePlaylist.id get() = key.id
val ActivePlaylist.shuffle get() = key.shuffle
val ActivePlaylist.volume get() = key.volume
val ActivePlaylist.volumeBoostDb get() = key.volumeBoostDb
val ActivePlaylist.tracks get() = value

/**
 * An [ExoPlayer] wrapper that allows for seamless looping of the provided
 * [ActivePlaylist]. The [update] method can be used when the [ActivePlaylist]'s
 * properties change. The property [volume] describes the current volume for
 * both audio channels, and is initialized to the [ActivePlaylist]'s [volume]
 * field.
 *
 * The methods [play], [pause], and [stop] can be used to control playback of
 * the Player. These methods correspond to the [ExoPlayer] methods of the
 * same name, except for [stop]. [Player]'s [stop] method is functionally the
 * same as pausing while seeking to the start of the media.
 *
 * If there is a problem with one or more [Uri]s within the [ActivePlaylist]'s
 * [tracks], playback of the next track will be attempted until one is found
 * that can be played. If player creation fails for all of the tracks, no
 * playback will occur, and calling [play] will have no effect. When one or
 * more tracks fail to play, the provided callback [onPlaybackFailure] will
 * be invoked.
 *
 * @param context A [Context] instance. Note that the provided context instance
 *     is held onto for the lifetime of the Player instance, and so should not
 *     be a [Context] that the Player might outlive.
 * @param playlist The [ActivePlaylist] whose contents will be played
 * @param startImmediately Whether the Player should start playback as soon as
 *     it is ready
 * @param onPlaybackFailure A callback that will be invoked if ExoPlayer
 *     creation fails for one or more [Uri]s in the playlist
 */
class Player(
    private val context: Context,
    playlist: ActivePlaylist,
    startImmediately: Boolean = false,
    private val onPlaybackFailure: (List<Uri>) -> Unit,
) {
    private val _player: ExoPlayer = ExoPlayer.Builder(context)
        .setWakeMode(WAKE_MODE_LOCAL)
        .build()
    private var activePlaylist: ActivePlaylist? = null
    private var volumeBooster: LoudnessEnhancer? = null

    init {
        _player.addListener(object: androidx.media3.common.Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e("", error.message ?: "")
                val uri = _player.currentMediaItem?.localConfiguration?.uri
                onPlaybackFailure(uri?.let(::listOf) ?: emptyList())
            }
            override fun onAudioSessionIdChanged(sessionId: Int) {
                applyVolumeBoost(activePlaylist?.volumeBoostDb ?: 0, forceUpdate = true)
            }
        })
        update(playlist, startImmediately)
    }

    fun play() { _player.play() }

    fun pause() { _player.pause() }

    fun stop() { _player.stop() }

    fun setVolume(volume: Float) { _player.volume = volume }

    fun release() {_player.release() }

    /**
     * Set the player's volume, repeat behavior, and shuffle mode to the
     * corresponding values of [newPlaylist]. If [startImmediately] is true,
     * playback will start immediately.
     */
    fun update(newPlaylist: ActivePlaylist, startImmediately: Boolean = false) {
        val availableCommands = _player.availableCommands
        if (COMMAND_SET_VOLUME in availableCommands) {
            _player.volume = newPlaylist.volume
        } else logd("ExoPlayer instance could not set volume")
        applyVolumeBoost(newPlaylist.volumeBoostDb)

        if (COMMAND_SET_REPEAT_MODE in availableCommands)
            _player.repeatMode = if (newPlaylist.tracks.size < 2) REPEAT_MODE_ONE
                                 else                             REPEAT_MODE_ALL
        else logd("ExoPlayer instance could not set repeat mode")

        if (COMMAND_SET_SHUFFLE_MODE in availableCommands)
            _player.shuffleModeEnabled = newPlaylist.shuffle
        else logd("ExoPlayer instance could not set shuffle mode")

        if (newPlaylist.tracks != activePlaylist?.tracks) {
            _player.clearMediaItems()
            _player.addMediaItems(newPlaylist.tracks.map(MediaItem::fromUri))
            if(COMMAND_PREPARE in availableCommands)
                _player.prepare()
            if (startImmediately && COMMAND_PLAY_PAUSE in availableCommands)
                _player.playWhenReady = true
        }
        activePlaylist = newPlaylist
    }

    /** Apply a loudness boost equal to [volumeBoostDb]. If the new
     * [volumeBoostDb] is the same as the cached value (i.e. activePlaylist.volumeBoostDb),
     * then no change will occur to prevent releasing and reacquiring system
     * resources. If [forceUpdate] is true, then this check will be bypassed. */
    private fun applyVolumeBoost(volumeBoostDb: Int, forceUpdate: Boolean = false) {
        val booster = volumeBooster
        when {
            volumeBoostDb <= 0 -> {
                booster?.release()
                volumeBooster = null
            } _player.audioSessionId == 0 -> {
                // If the Player hasn't been prepared, the creation of a LoudnessEnhancer
                // will fail because the audio session ID will be 0. The internal ExoPlayer's
                // onAudioSessionId callback can instead apply the volume boost once it has a
                // valid audio session ID.
                return
            } forceUpdate || volumeBoostDb != activePlaylist?.volumeBoostDb -> {
                try {
                    volumeBooster?.release()
                    volumeBooster = LoudnessEnhancer(_player.audioSessionId).apply {
                        setTargetGain(volumeBoostDb * 100)
                        enabled = true
                    }
                } catch (e: java.lang.RuntimeException) {
                    logd("Creation of LoudnessEnhancer audio effect failed")
                }
            }
        }
    }
}

/**
 * A collection of [Player] instances.
 *
 * [PlayerMap] manages a collection of [Player] instances for a collection of
 * [ActivePlaylist]s. The collection of [Player]s is updated via the method
 * [update]. Whether the collection of players is empty can be queried with
 * the property [isEmpty].
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
            val existingPlayer = oldMap.remove(playlist.id)
            existingPlayer?.update(playlist, startPlaying)

            playerMap[playlist.id] = existingPlayer ?:
                Player(context, playlist, startPlaying, onPlaybackFailure)
        }
        oldMap.values.forEach(Player::release)
    }
}