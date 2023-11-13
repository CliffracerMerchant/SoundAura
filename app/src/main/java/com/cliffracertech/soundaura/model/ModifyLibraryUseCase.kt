/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.model

import com.cliffracertech.soundaura.R
import com.cliffracertech.soundaura.dialog.ValidatedNamingState
import com.cliffracertech.soundaura.model.database.PlaylistDao
import com.cliffracertech.soundaura.model.database.Track
import com.cliffracertech.soundaura.model.database.playlistRenameValidator
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

/** A container of methods that modify the app's library of playlists. */
class ModifyLibraryUseCase(
    private val permissionHandler: UriPermissionHandler,
    private val messageHandler: MessageHandler,
    private val dao: PlaylistDao,
) {
    @Inject constructor(
        permissionHandler: AndroidUriPermissionHandler,
        messageHandler: MessageHandler,
        dao: PlaylistDao
    ): this(permissionHandler as UriPermissionHandler, messageHandler, dao)

    suspend fun togglePlaylistIsActive(playlistName: String) {
        dao.toggleIsActive(playlistName)
    }

    suspend fun setPlaylistVolume(playlistName: String, newVolume: Float) {
        dao.setVolume(playlistName, newVolume)
    }

    /** Return a [ValidatedNamingState] that can be used to rename the
     * playlist whose old name matches [oldName]. [onFinished] will be
     * called when the renaming ends, successfully or otherwise, and
     * can be used, e.g., to dismiss a rename dialog. */
    fun renameState(
        oldName: String,
        scope: CoroutineScope,
        onFinished: () -> Unit
    ) = ValidatedNamingState(
        validator = playlistRenameValidator(dao, oldName, scope),
        coroutineScope = scope,
        onNameValidated = { newName ->
            if (newName != oldName)
                dao.rename(oldName, newName)
            onFinished()
        })

    /** Set the playlist whose name matches [name] to have a shuffle on/off
     * state matching [shuffle], and its track list to match [tracks]. While
     * the playlist's shuffle state will always be set, the track list update
     * operation can fail if the [UriPermissionHandler] in use indicates that
     * permissions could not be obtained for all of the new tracks (e.g. if
     * the permitted permission allowance has been used up). In this case,
     * an explanatory message will be displayed using the [MessageHandler]
     * provided in the constructor.*/
    suspend fun setPlaylistShuffleAndTracks(
        name: String,
        shuffle: Boolean,
        tracks: List<Track>
    ) {
        val uris = tracks.map(Track::uri)
        val newTracks = dao.filterNewTracks(uris)
        val removableUris = dao.getUniqueUrisNotIn(uris, name)
        val postOpPermissionAllowance = permissionHandler.getRemainingAllowance() +
                                        removableUris.size - newTracks.size
        if (postOpPermissionAllowance < 0) {
            messageHandler.postMessage(StringResource(
                R.string.cant_modify_playlist_tracks_warning,
                permissionHandler.totalAllowance))
            dao.setPlaylistShuffle(name, shuffle)
        } else {
            val removedUris = dao.setPlaylistShuffleAndContents(
                playlistName = name,
                shuffleEnabled = shuffle,
                newTracks = tracks,
                removableUris = removableUris)
            permissionHandler.releasePermissionsFor(removedUris)
            permissionHandler.acquirePermissionsFor(newTracks, allowPartial = false)
        }
    }

    /** Remove the playlist whose name matches [name]. */
    suspend fun removePlaylist(name: String) {
        val unusedTracks = dao.deletePlaylist(name)
        permissionHandler.releasePermissionsFor(unusedTracks)
        dao.deletePlaylist(name)
    }
}