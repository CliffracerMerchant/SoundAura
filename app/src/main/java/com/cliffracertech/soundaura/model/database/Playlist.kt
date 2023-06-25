/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.model.database

import android.net.Uri
import androidx.annotation.FloatRange
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.room.*
import com.cliffracertech.soundaura.R
import com.cliffracertech.soundaura.collectAsState
import com.cliffracertech.soundaura.containsBlanks
import com.cliffracertech.soundaura.model.ListValidator
import com.cliffracertech.soundaura.model.StringResource
import com.cliffracertech.soundaura.model.Validator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

typealias LibraryPlaylist = com.cliffracertech.soundaura.library.Playlist

private const val librarySelect =
    "SELECT name, isActive, volume, hasError, " +
           "COUNT(playlistName) = 1 AS isSingleTrack " +
    "FROM playlist " +
    "JOIN playlistTrack ON playlist.name = playlistTrack.playlistName " +
    "WHERE name LIKE :filter " +
    "GROUP BY playlistTrack.playlistName"

@Entity(tableName = "track")
data class Track(
    @ColumnInfo(typeAffinity = ColumnInfo.TEXT)
    @PrimaryKey val uri: Uri,

    @ColumnInfo(defaultValue = "0")
    val hasError: Boolean = false
) {
    class UriStringConverter {
        @TypeConverter fun fromString(string: String) = string.toUri()
        @TypeConverter fun toString(uri: Uri) = uri.toString()
    }
}

@Entity(tableName = "playlistTrack",
        primaryKeys = ["playlistName", "trackUri"],
    foreignKeys = [
        ForeignKey(entity = Playlist::class,
            parentColumns=["name"],
            childColumns=["playlistName"],
            onUpdate=ForeignKey.CASCADE,
            onDelete=ForeignKey.CASCADE),
        ForeignKey(entity = Track::class,
            parentColumns=["uri"],
            childColumns=["trackUri"],
            onUpdate=ForeignKey.CASCADE,
            onDelete=ForeignKey.CASCADE)])
data class PlaylistTrack(
    val playlistName: String,
    val trackUri: Uri,
    val playlistOrder: Int)

@Entity(tableName = "playlist")
data class Playlist(
    /** The name of the [Playlist]. */
    @PrimaryKey
    val name: String,

    /** Whether or not shuffle is enabled for the [Playlist]. */
    @ColumnInfo(defaultValue = "0")
    val shuffle: Boolean = false,

    /** Whether or not the [Playlist] is active (i.e. part of the current sound mix). */
    @ColumnInfo(defaultValue = "0")
    val isActive: Boolean = false,

    /** The volume (in the range `[0f, 1f]`) of the [Playlist] during playback. */
    @FloatRange(from = 0.0, to = 1.0)
    @ColumnInfo(defaultValue = "1.0")
    val volume: Float = 1f,

    /** Whether or not the entire playlist has an error. This should be
     * true if every track in the playlist has an error, but is stored
     * as a separate column instead of being computed to prevent needing
     * a subquery to determine its value. */
    @ColumnInfo(defaultValue = "0")
    val hasError: Boolean = false,
) {
    enum class Sort { NameAsc, NameDesc, OrderAdded;
        companion object {
            @Composable fun stringValues() = with(LocalContext.current) {
                remember { arrayOf(getString(R.string.name_ascending),
                                   getString(R.string.name_descending),
                                   getString(R.string.order_added)) }
            }
        }
    }
}

@Dao abstract class PlaylistDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertTracks(tracks: List<Track>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun addPlaylistTrack(playlistTrack: PlaylistTrack)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertPlaylists(playlists: List<Playlist>)

    @Query("DELETE FROM playlistTrack WHERE playlistName in (:playlistNames)")
    protected abstract suspend fun deletePlaylistContents(playlistNames: List<String>)

    @Transaction
    open suspend fun insert(playlistContentMap: Map<Playlist, List<Track>>) {
        val playlists = playlistContentMap.keys.toList()
        insertPlaylists(playlists)
        deletePlaylistContents(playlists.map(Playlist::name))
        for (playlist in playlists) {
            val tracks = playlistContentMap[playlist] ?: emptyList()
            insertTracks(tracks)
            tracks.forEachIndexed { index, track ->
                addPlaylistTrack(PlaylistTrack(playlist.name, track.uri, index))
            }
        }
    }

    /** Delete the playlist whose name matches [name] from the database. */
    @Query("DELETE FROM playlist WHERE name = :name")
    abstract suspend fun delete(name: String)

    /** Delete the [Playlist]s whose names are in [names] from the database. */
    @Query("DELETE FROM playlist WHERE name in (:names)")
    abstract suspend fun delete(names: List<String>)

    /** Return whether or not a [Playlist] whose name matches [name] exists. */
    @Query("SELECT EXISTS(SELECT name FROM playlist WHERE name = :name)")
    abstract suspend fun exists(name: String?): Boolean

    @Query("$librarySelect ORDER BY name COLLATE NOCASE ASC")
    protected abstract fun getAllPlaylistsSortedByNameAsc(filter: String): Flow<List<LibraryPlaylist>>

    @Query("$librarySelect ORDER BY name COLLATE NOCASE DESC")
    protected abstract fun getAllPlaylistsSortedByNameDesc(filter: String): Flow<List<LibraryPlaylist>>

    @Query(librarySelect)
    protected abstract fun getAllPlaylistsSortedByOrderAdded(filter: String): Flow<List<LibraryPlaylist>>

    @Query("$librarySelect ORDER BY isActive DESC, name COLLATE NOCASE ASC")
    protected abstract fun getAllPlaylistsSortedByActiveThenNameAsc(filter: String): Flow<List<LibraryPlaylist>>

    @Query("$librarySelect ORDER BY isActive DESC, name COLLATE NOCASE DESC")
    protected abstract fun getAllPlaylistsSortedByActiveThenNameDesc(filter: String): Flow<List<LibraryPlaylist>>

    @Query("$librarySelect ORDER BY isActive DESC")
    protected abstract fun getAllPlaylistsSortedByActiveThenOrderAdded(filter: String): Flow<List<LibraryPlaylist>>

    /** Return a [Flow] that updates with the latest [List] of all [Playlist]s
     * in the database. The returned [List] will be sorted according to the
     * values of [sort] and [showActiveFirst], and filtered to [Playlist]s
     * whose names match [searchFilter]. [searchFilter] can be null, in which
     * case no filtering will be done. */
    fun getAllPlaylists(
        sort: Playlist.Sort,
        showActiveFirst: Boolean,
        searchFilter: String?
    ): Flow<List<LibraryPlaylist>> {
        val filter = "%${searchFilter ?: ""}%"
        return if (showActiveFirst) when (sort) {
            Playlist.Sort.NameAsc ->    getAllPlaylistsSortedByActiveThenNameAsc(filter)
            Playlist.Sort.NameDesc ->   getAllPlaylistsSortedByActiveThenNameDesc(filter)
            Playlist.Sort.OrderAdded -> getAllPlaylistsSortedByActiveThenOrderAdded(filter)
        } else when (sort) {
            Playlist.Sort.NameAsc ->    getAllPlaylistsSortedByNameAsc(filter)
            Playlist.Sort.NameDesc ->   getAllPlaylistsSortedByNameDesc(filter)
            Playlist.Sort.OrderAdded -> getAllPlaylistsSortedByOrderAdded(filter)
        }
    }

    @Query("SELECT EXISTS(SELECT name FROM playlist WHERE isActive LIMIT 1)")
    abstract fun getAtLeastOnePlaylistIsActive(): Flow<Boolean>

    /** Return a [Flow] that updates with a [Map] of each
     * active [Playlist] mapped to its list of track [Uri]s. */
    @MapInfo(valueColumn = "trackUri")
    @Query("SELECT * FROM playlist " +
           "JOIN playlistTrack ON playlist.name = playlistTrack.playlistName " +
           "WHERE isActive ORDER by playlistOrder")
    abstract fun getActivePlaylistsAndContents(): Flow<Map<Playlist, List<Uri>>>

    /** Return a [Flow] that updates with the latest [List] of
     * [com.cliffracertech.soundaura.model.PresetPlaylist]s. This represents
     * the contents of a new [Preset], were it to be saved now. */
    @Query("SELECT name, volume FROM playlist WHERE isActive")
    abstract fun getTempPresetPlaylists():
        Flow<List<com.cliffracertech.soundaura.model.PresetPlaylist>>

    @Query("SELECT name FROM playlist")
    abstract fun getPlaylistNames(): Flow<List<String>>

    @Query("SELECT trackUri FROM playlistTrack WHERE playlistName = :name ORDER by playlistOrder")
    abstract suspend fun getPlaylistTracks(name: String): List<Uri>

    @Transaction
    open suspend fun setPlaylistTracks(name: String, newTracks: List<Uri>) {
        deletePlaylistContents(listOf(name))
        newTracks.forEachIndexed { index, uri ->
            addPlaylistTrack(PlaylistTrack(name, uri, index))
        }
    }

    /** Rename the [Playlist] whose name matches [oldName] to [newName]. */
    @Query("UPDATE playlist SET name = :newName WHERE name = :oldName")
    abstract suspend fun rename(oldName: String, newName: String)

    /** Toggle the [Playlist.isActive] field of the [Playlist] identified by [name]. */
    @Query("UPDATE playlist set isActive = 1 - isActive WHERE name = :name")
    abstract suspend fun toggleIsActive(name: String)

    /** Set the [Playlist.volume] field of the [Playlist] identified by [name]. */
    @Query("UPDATE playlist SET volume = :volume WHERE name = :name")
    abstract suspend fun setVolume(name: String, volume: Float)

    @Query("UPDATE track SET hasError = 1 WHERE uri in (:uris)")
    protected abstract suspend fun setTracksHaveError(uris: List<Uri>)

    @Query("SELECT NOT EXISTS(SELECT playlistName FROM playlistTrack " +
                             "JOIN track ON playlistTrack.trackUri = track.uri " +
                             "WHERE playlistName = :playlistName AND " +
                                   "track.hasError = 0 LIMIT 1)")
    protected abstract suspend fun playlistHasNoValidTracks(playlistName: String): Boolean

    @Query("UPDATE playlist SET hasError = 1 WHERE name = :playlistName")
    protected abstract suspend fun setPlaylistHasError(playlistName: String)

    @Transaction
    open suspend fun setPlaylistTrackHasError(playlistName: String, trackUris: List<Uri>) {
        setTracksHaveError(trackUris)
        if (playlistHasNoValidTracks(playlistName))
            setPlaylistHasError(playlistName)
    }

    @Query("SELECT shuffle FROM playlist " +
           "WHERE playlist.name = :playlistName LIMIT 1")
    abstract suspend fun getPlaylistShuffle(playlistName: String): Boolean

    @Query("UPDATE playlist SET shuffle = :enabled " +
           "WHERE name = :playlistName")
    abstract suspend fun setPlaylistShuffle(playlistName: String, enabled: Boolean)

    @Transaction
    open suspend fun setPlaylistShuffleAndTracks(
        playlistName: String,
        shuffleEnabled: Boolean,
        tracks: List<Uri>
    ) {
        setPlaylistShuffle(playlistName, shuffleEnabled)
        setPlaylistTracks(playlistName, tracks)
    }
}

class TrackNamesValidator(
    private val playlistDao: PlaylistDao,
    coroutineScope: CoroutineScope,
    names: List<String>,
) : ListValidator<String>(coroutineScope, names) {

    private val existingNames by playlistDao
        .getPlaylistNames()
        .map(List<String>::toSet)
        .collectAsState(emptySet(), coroutineScope)

    override fun isValid(value: String) = value !in existingNames || value.isBlank()

    override suspend fun messageFor(values: List<Pair<String, Boolean>>) =
        if (values.find { it.second } == null) null
        else Validator.Message.Error(
            StringResource(
            R.string.add_multiple_tracks_name_error_message)
        )

    override suspend fun validate(): List<String>? {
        val existingNames = playlistDao.getPlaylistNames().first().toSet()
        val names = values.map { it.first }
        return when {
            names.intersect(existingNames).isNotEmpty() -> null
            names.containsBlanks() -> null
            else -> names
        }
    }
}

class PlaylistNameValidator(
    private val playlistDao: PlaylistDao,
    private val initialName: String
) : Validator<String>(initialName) {
    private val blankNameErrorMessage = Message.Error(
        StringResource(R.string.add_playlist_blank_name_error_message))
    private val duplicateNameErrorMessage = Message.Error(
        StringResource(R.string.add_playlist_duplicate_name_error_message))

    override suspend fun messageFor(value: String) = when {
        !valueHasBeenChanged ->      null
        value == initialName ->      null
        value.isBlank() ->           blankNameErrorMessage
        playlistDao.exists(value) -> duplicateNameErrorMessage
        else ->                      null
    }
}