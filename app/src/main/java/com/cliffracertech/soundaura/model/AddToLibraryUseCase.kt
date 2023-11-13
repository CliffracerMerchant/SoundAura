/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.model

import android.net.Uri
import com.cliffracertech.soundaura.R
import com.cliffracertech.soundaura.model.database.PlaylistDao
import com.cliffracertech.soundaura.model.database.Track
import com.cliffracertech.soundaura.model.database.TrackNamesValidator
import com.cliffracertech.soundaura.model.database.newPlaylistNameValidator
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

/** A container of methods that adds playlists (single track
 * or multi-track) to the app's library of playlists. */
class AddToLibraryUseCase(
    private val permissionHandler: UriPermissionHandler,
    private val messageHandler: MessageHandler,
    private val dao: PlaylistDao,
) {
    @Inject constructor(
        permissionHandler: AndroidUriPermissionHandler,
        messageHandler: MessageHandler,
        dao: PlaylistDao
    ): this(permissionHandler as UriPermissionHandler, messageHandler, dao)

    fun trackNamesValidator(
        scope: CoroutineScope,
        initialTrackNames: List<String>
    ) = TrackNamesValidator(dao, scope, initialTrackNames)

    /**
     * Attempt to add multiple single-track playlists, with the name for
     * each playlist mapped to the playlist's single track's [Uri] in
     * [uriNameMap]. This operation can fail for all or some portion of
     * the playlists if the attempt to acquire persisted permissions for
     * one or more of the URIs fail.
     */
    suspend fun addSingleTrackPlaylists(uriNameMap: LinkedHashMap<Uri, String>) {
        val newTracks = dao.filterNewTracks(uriNameMap.keys.toList())
        val rejectedTracks = permissionHandler
            .acquirePermissionsFor(newTracks, allowPartial = true)

        if (rejectedTracks.size < uriNameMap.size) {
            rejectedTracks.forEach(uriNameMap::remove)
            dao.insertSingleTrackPlaylists(uriNameMap)
        }
        if (rejectedTracks.isNotEmpty())
            messageHandler.postMessage(StringResource(
                R.string.cant_add_all_tracks_warning,
                rejectedTracks.size,
                permissionHandler.totalAllowance))
    }

    fun newPlaylistNameValidator(
        scope: CoroutineScope,
        initialName: String
    ) = newPlaylistNameValidator(dao, scope, initialName)

    /**
     * Attempt to add a playlist with the given [name] and [shuffle]
     * values and with a track list equal to [tracks]. This operation
     * can fail if the attempt to acquire persisted permissions for
     * all of the new playlist's tracks fails.
     *
     * @return Whether or not the operation succeeded
     */
    suspend fun addPlaylist(
        name: String,
        shuffle: Boolean,
        tracks: List<Track>
    ) {
        val trackUris = tracks.map(Track::uri)
        val newUris = dao.filterNewTracks(trackUris)
        val rejectedTracks = permissionHandler.acquirePermissionsFor(
            uris = newUris, allowPartial = false)
        if (rejectedTracks.isEmpty())
            dao.insertPlaylist(name, shuffle, tracks)
        else messageHandler.postMessage(StringResource(
            R.string.cant_add_playlist_warning,
            permissionHandler.totalAllowance))
    }
}