/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.model

import android.net.Uri
import com.cliffracertech.soundaura.R
import com.cliffracertech.soundaura.model.database.PlaylistDao
import com.cliffracertech.soundaura.model.database.playlistRenameValidator
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

/** A container of methods that modify the app's library of playlists. */
class ModifyLibraryUseCase @Inject constructor(
    private val permissionHandler: UriPermissionHandler,
    private val messageHandler: MessageHandler,
    private val dao: PlaylistDao,
) {
    suspend fun togglePlaylistIsActive(playlistName: String) {
        dao.toggleIsActive(playlistName)
    }

    suspend fun setPlaylistVolume(playlistName: String, newVolume: Float) {
        dao.setVolume(playlistName, newVolume)
    }

    fun renameValidator(oldName: String, scope: CoroutineScope) =
        playlistRenameValidator(dao, scope, oldName)

    suspend fun renamePlaylist(from: String, to: String) {
        if (from != to) dao.rename(from, to)
    }

    suspend fun setPlaylistShuffleAndTracks(
        name: String,
        shuffle: Boolean,
        tracks: List<Uri>
    ) {
        val newTracks = dao.filterNewTracks(tracks)
        val removableTracks = dao.getUniqueTracksNotIn(tracks, name)
        val postOpPermissionAllowance =
            permissionHandler.remainingPermissionAllowance() +
            removableTracks.size - newTracks.size
        if (postOpPermissionAllowance < 0) {
            messageHandler.postMessage(StringResource(
                R.string.cant_modify_playlist_tracks_warning,
                permissionHandler.totalPermissionAllowance))
            dao.setPlaylistShuffle(name, shuffle)
        } else {
            val removedUris = dao.setPlaylistShuffleAndContents(
                playlistName = name,
                shuffleEnabled = shuffle,
                newTracks = tracks)
            permissionHandler.releasePermissionsFor(removedUris)
            permissionHandler.acquirePermissionsFor(newTracks, allowPartial = false)
        }
    }

    /** Remove the playlist whose name matches
     * [name]. This operation always succeeds. */
    suspend fun removePlaylist(name: String) {
        val unusedTracks = dao.deletePlaylist(name)
        permissionHandler.releasePermissionsFor(unusedTracks)
        dao.deletePlaylist(name)
    }
}