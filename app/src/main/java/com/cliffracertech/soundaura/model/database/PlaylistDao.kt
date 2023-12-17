/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.model.database

import android.net.Uri
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.MapInfo
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.cliffracertech.soundaura.service.ActivePlaylistSummary
import kotlinx.coroutines.flow.Flow

typealias LibraryPlaylist = com.cliffracertech.soundaura.library.Playlist

private const val librarySelect =
    "SELECT id, name, isActive, volume, " +
           "COUNT(NOT track.hasError) = 0 as hasError, " +
           "COUNT(playlistId) = 1 AS isSingleTrack " +
    "FROM playlist " +
    "JOIN playlistTrack ON playlist.id = playlistTrack.playlistId " +
    "JOIN track on playlistTrack.trackUri = track.uri " +
    "WHERE name LIKE :filter " +
    "GROUP BY playlistTrack.playlistId"

@Dao abstract class PlaylistDao {
    @Query("INSERT INTO track (uri) VALUES (:uri)")
    protected abstract suspend fun insertTrack(uri: Uri)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertTracks(tracks: List<Track>)

    @Insert(onConflict = OnConflictStrategy.IGNORE, entity = Playlist::class)
    protected abstract suspend fun insertPlaylist(playlist: Playlist): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE, entity = Playlist::class)
    protected abstract suspend fun insertPlaylists(names: List<Playlist>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertPlaylistTrack(playlistTrack: PlaylistTrack)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertPlaylistTracks(playlistTracks: List<PlaylistTrack>)

    /** Insert a single [Playlist] whose [Playlist.name] and [Playlist.shuffle]
     * vales will be equal to [playlistName] and [shuffle], respectively. The
     * [Uri]s in [tracks] will be added as the contents of the playlist. */
    @Transaction
    open suspend fun insertPlaylist(
        playlistName: String,
        shuffle: Boolean,
        tracks: List<Track>,
        newUris: List<Uri>? = null,
    ) {
        insertTracks(newUris?.map(::Track) ?: tracks)
        val playlist = Playlist(name = playlistName, shuffle = shuffle)
        val id = insertPlaylist(playlist)
        insertPlaylistTracks(tracks.mapIndexed { index, track ->
            PlaylistTrack(id, index, track.uri)
        })
    }

    /** Attempt to add multiple single-track playlists. Each value in
     * [names] will be used as a name for a new [Playlist], while the
     * [Uri] with the same index in [uris] will be used as that [Playlist]'s
     * single track. The [Playlist.shuffle] value for the new [Playlist]s
     * will be the default value (i.e. false) due to shuffle having no
     * meaning for single-track playlists. */
    @Transaction
    open suspend fun insertSingleTrackPlaylists(
        names: List<String>,
        uris: List<Uri>,
        newUris: List<Uri>? = null,
    ) {
        assert(names.size == uris.size)
        insertTracks((newUris ?: uris).map(::Track))
        val playlists = names.map { Playlist(name = it) }
        val ids = insertPlaylists(playlists)
        assert(ids.size == names.size)
        insertPlaylistTracks(ids.mapIndexed { index, id ->
            PlaylistTrack(id, 0, uris[index])
        })
    }

    /** Delete the playlist identified by [id] from the database. */
    @Query("DELETE FROM playlist WHERE id = :id")
    protected abstract suspend fun deletePlaylistName(id: Long)

    @Query("DELETE FROM playlistTrack WHERE playlistId = :playlistId")
    protected abstract suspend fun deletePlaylistTracks(playlistId: Long)

    @Query("DELETE FROM track WHERE uri IN (:uris)")
    protected abstract suspend fun deleteTracks(uris: List<Uri>)

    /** Delete the [Playlist] identified by [id] along with its contents.
     * @return the [List] of [Uri]s that are no longer a part of any playlist */
    @Transaction
    open suspend fun deletePlaylist(id: Long): List<Uri> {
        val removableTracks = getUniqueUris(id)
        deletePlaylistName(id)
        // playlistTrack.playlistName has an 'on delete: cascade' policy,
        // so the playlistTrack rows don't need to be deleted manually
        deleteTracks(removableTracks)
        return removableTracks
    }

    @Query("SELECT shuffle FROM playlist WHERE id = :id LIMIT 1")
    abstract suspend fun getPlaylistShuffle(id: Long): Boolean

    @Query("UPDATE playlist SET shuffle = :shuffle WHERE id = :id")
    abstract suspend fun setPlaylistShuffle(id: Long, shuffle: Boolean)

    /**
     * Set the playlist identified by [playlistId] to have a [Playlist.shuffle]
     * value equal to [shuffle], and overwrite its tracks to be equal to [contents].
     *
     * If the [Uri]s in [contents] that are not already in any other playlists
     * has already been obtained, it can be passed as [newUris] to prevent the
     * database from needing to insert already existing tracks. Likewise, if
     * the [Uri]s that were previously a part of the playlist, but are not in
     * the new [contents] and are not in any other playlist has already been
     * obtained, it can be passed as [removableUris] to prevent the database
     * from needing to recalculate the [Uri]s that are no longer needed.
     *
     * @return The [List] of [Uri]s that are no longer in any [Playlist] after the change.
     */
    @Transaction
    open suspend fun setPlaylistShuffleAndContents(
        playlistId: Long,
        shuffle: Boolean,
        contents: List<Track>,
        newUris: List<Uri>? = null,
        removableUris: List<Uri>? = null,
    ): List<Uri> {
        val removedUris = removableUris ?:
            getUniqueUrisNotIn(contents.map(Track::uri), playlistId)
        deleteTracks(removedUris)
        insertTracks(newUris?.map(::Track) ?: contents)

        deletePlaylistTracks(playlistId)
        insertPlaylistTracks(contents.mapIndexed { index, track ->
            PlaylistTrack(playlistId, index, track.uri)
        })
        setPlaylistShuffle(playlistId, shuffle)
        return removedUris
    }

    /** Return the track uris of the [Playlist] identified by
     * [playlistId] that are not in any other [Playlist]s. */
    @Query("SELECT trackUri FROM playlistTrack " +
           "GROUP BY trackUri HAVING COUNT(playlistId) = 1 " +
                             "AND playlistId = :playlistId")
    protected abstract suspend fun getUniqueUris(playlistId: Long): List<Uri>

    /** Return the track uris of the [Playlist] identified by [playlistId]
     * that are not in any other [Playlist]s and are not in [exceptions]. */
    @Query("SELECT trackUri FROM playlistTrack " +
           "WHERE trackUri NOT IN (:exceptions) " +
           "GROUP BY trackUri HAVING COUNT(playlistId) = 1 " +
                             "AND playlistId = :playlistId")
    abstract suspend fun getUniqueUrisNotIn(exceptions: List<Uri>, playlistId: Long): List<Uri>

    @RawQuery
    protected abstract suspend fun filterNewTracks(query: SupportSQLiteQuery): List<Uri>

    suspend fun filterNewTracks(tracks: List<Uri>): List<Uri> {
        // The following query requires parentheses around each argument. This
        // is not supported by Room, so the query must be made manually.
        val query = StringBuilder()
            .append("WITH newTrack(uri) AS (VALUES ")
            .apply {
                for (i in 0 until tracks.lastIndex)
                    append("(?), ")
            }.append("(?)) ")
            .append("SELECT newTrack.uri FROM newTrack ")
            .append("LEFT JOIN track ON track.uri = newTrack.uri ")
            .append("WHERE track.uri IS NULL;")
            .toString()
        val args = Array(tracks.size) { tracks[it].toString() }
        return filterNewTracks(SimpleSQLiteQuery(query, args))
    }

    /** Return whether or not a [Playlist] whose name matches [name] exists. */
    @Query("SELECT EXISTS(SELECT name FROM playlist WHERE name = :name)")
    abstract suspend fun exists(name: String?): Boolean

    @Query("$librarySelect ORDER BY name COLLATE NOCASE ASC")
    abstract fun getPlaylistsSortedByNameAsc(filter: String): Flow<List<LibraryPlaylist>>

    @Query("$librarySelect ORDER BY name COLLATE NOCASE DESC")
    abstract fun getPlaylistsSortedByNameDesc(filter: String): Flow<List<LibraryPlaylist>>

    @Query("$librarySelect ORDER BY id ASC")
    abstract fun getPlaylistsSortedByOrderAdded(filter: String): Flow<List<LibraryPlaylist>>

    @Query("$librarySelect ORDER BY isActive DESC, name COLLATE NOCASE ASC")
    abstract fun getPlaylistsSortedByActiveThenNameAsc(filter: String): Flow<List<LibraryPlaylist>>

    @Query("$librarySelect ORDER BY isActive DESC, name COLLATE NOCASE DESC")
    abstract fun getPlaylistsSortedByActiveThenNameDesc(filter: String): Flow<List<LibraryPlaylist>>

    @Query("$librarySelect ORDER BY isActive DESC, id ASC")
    abstract fun getPlaylistsSortedByActiveThenOrderAdded(filter: String): Flow<List<LibraryPlaylist>>

    @Query("SELECT EXISTS(SELECT 1 FROM playlist WHERE isActive)")
    abstract fun getAtLeastOnePlaylistIsActive(): Flow<Boolean>

    /** Return a [Flow] that updates with a [Map] of each active
     * [Playlist] (represented as an [ActivePlaylistSummary]
     * mapped to its tracks (represented as a [List] of [Uri]s). */
    @MapInfo(valueColumn = "trackUri")
    @Query("SELECT id, shuffle, volume, trackUri " +
           "FROM playlist " +
           "JOIN playlistTrack ON playlist.id = playlistTrack.playlistId " +
           "WHERE isActive ORDER by playlistOrder")
    abstract fun getActivePlaylistsAndTracks(): Flow<Map<ActivePlaylistSummary, List<Uri>>>

    @Query("SELECT name FROM playlist")
    abstract fun getPlaylistNames(): List<String>

    @Query("SELECT uri, hasError FROM playlistTrack " +
           "JOIN track on playlistTrack.trackUri = track.uri " +
           "WHERE playlistId = :id ORDER by playlistOrder")
    abstract suspend fun getPlaylistTracks(id: Long): List<Track>

    /** Rename the [Playlist] identified by [id] to [newName]. */
    @Query("UPDATE playlist SET name = :newName WHERE id = :id")
    abstract suspend fun rename(id: Long, newName: String)

    /** Toggle the [Playlist.isActive] field of the [Playlist] identified by [id]. */
    @Query("UPDATE playlist set isActive = 1 - isActive WHERE id = :id")
    abstract suspend fun toggleIsActive(id: Long)

    /** Set the [Playlist.volume] field of the [Playlist] identified by [id]. */
    @Query("UPDATE playlist SET volume = :volume WHERE id = :id")
    abstract suspend fun setVolume(id: Long, volume: Float)

    @Query("UPDATE track SET hasError = 1 WHERE uri in (:uris)")
    abstract suspend fun setTracksHaveError(uris: List<Uri>)
}