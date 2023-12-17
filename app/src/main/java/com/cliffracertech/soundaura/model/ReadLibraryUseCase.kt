/*
 * This file is part of SoundAura, which is released under the terms of the Apache
 * License 2.0. See license.md in the project's root directory to see the full license.
 */

package com.cliffracertech.soundaura.model

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import com.cliffracertech.soundaura.enumPreferenceFlow
import com.cliffracertech.soundaura.library.Playlist
import com.cliffracertech.soundaura.model.database.PlaylistDao
import com.cliffracertech.soundaura.model.database.Track
import com.cliffracertech.soundaura.preferenceFlow
import com.cliffracertech.soundaura.settings.PrefKeys
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import javax.inject.Inject

private typealias PlaylistSort = com.cliffracertech.soundaura.model.database.Playlist.Sort

/**
 * A container of methods that read the app's library of
 * playlists, or read properties of individual playlists.
 *
 * The property [playlistsFlow] can be used to access the library's
 * entire list of playlists, while the methods [getPlaylistTracks]
 * and [getPlaylistShuffle] can be used to obtain the list of tracks
 * or the shuffle enabled value for a given playlist.
 */
class ReadLibraryUseCase @Inject constructor(
    dataStore: DataStore<Preferences>,
    searchQuery: SearchQueryState,
    private val dao: PlaylistDao,
) {
    private val showActivePlaylistsFirstKey = booleanPreferencesKey(PrefKeys.showActivePlaylistsFirst)
    private val showActivePlaylistsFirst = dataStore.preferenceFlow(showActivePlaylistsFirstKey, false)
    private val playlistSortKey = intPreferencesKey(PrefKeys.playlistSort)
    private val playlistSort = dataStore.enumPreferenceFlow<PlaylistSort>(playlistSortKey)

    /** A [Flow] whose latest value will be an [ImmutableList] of [Playlist]s
     * representing all of the playlists in the app's library. The returned
     * list will take into account the chosen sorting method, the 'show active
     * playlists first' option's enabled state, and any current search filter. */
    val playlistsFlow = combine(
            playlistSort, showActivePlaylistsFirst, searchQuery.flow
        ) { sort, showActiveFirst, searchQuery ->
            val filter = "%$searchQuery%"
            if (showActiveFirst) when (sort) {
                PlaylistSort.NameAsc ->    dao.getPlaylistsSortedByActiveThenNameAsc(filter)
                PlaylistSort.NameDesc ->   dao.getPlaylistsSortedByActiveThenNameDesc(filter)
                PlaylistSort.OrderAdded -> dao.getPlaylistsSortedByActiveThenOrderAdded(filter)
            } else when (sort) {
                PlaylistSort.NameAsc ->    dao.getPlaylistsSortedByNameAsc(filter)
                PlaylistSort.NameDesc ->   dao.getPlaylistsSortedByNameDesc(filter)
                PlaylistSort.OrderAdded -> dao.getPlaylistsSortedByOrderAdded(filter)
            }
        }.transformLatest { emitAll(it) }
        .map(List<Playlist>::toImmutableList)

    /** Return a [List] of the [Track]s in the [Playlist] identified by [playlistId]. */
    suspend fun getPlaylistTracks(playlistId: Long): List<Track> =
        dao.getPlaylistTracks(playlistId)

    /** Return whether or not the [Playlist] identified
     * by [playlistId] has shuffle playback enabled. */
    suspend fun getPlaylistShuffle(playlistId: Long): Boolean =
        dao.getPlaylistShuffle(playlistId)
}