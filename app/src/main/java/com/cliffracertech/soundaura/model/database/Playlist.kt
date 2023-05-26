/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.model.database

import android.net.Uri
import androidx.annotation.FloatRange
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.room.*
import com.cliffracertech.soundaura.R
import kotlinx.coroutines.flow.Flow
import java.io.File

@Entity(tableName = "playlist")
data class Playlist(
    /** The name of the [Playlist]. */
    @PrimaryKey
    val name: String,

    /** A [List] of the [Uri]s of the tracks in the [Playlist]. */
    val tracks: List<Uri>,

    /** Whether or not shuffle is enabled for the [Playlist]. */
    @ColumnInfo(defaultValue = "0")
    val shuffleEnabled: Boolean = false,

    /** Whether or not the [Playlist] is active (i.e. part of the current sound mix). */
    @ColumnInfo(defaultValue = "0")
    val isActive: Boolean = false,

    /** The volume (in the range `[0f, 1f]`) of the [Playlist] during playback. */
    @FloatRange(from = 0.0, to = 1.0)
    @ColumnInfo(defaultValue = "1.0")
    val volume: Float = 1f,
) {
    val hasError get() = tracks.isEmpty()

    class TracksConverter {
        @TypeConverter fun fromString(string: String) =
            string.split(File.pathSeparatorChar).map(String::toUri)
        @TypeConverter fun toString(tracks: List<Uri>) =
            tracks.joinToString(File.pathSeparator, transform = Uri::toString)
    }

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
    /** Insert the [playlist] into the database. */
    @Insert abstract suspend fun insert(playlist: Playlist): Long

    /** Insert the [Playlist]s in [playlists] into the database. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(playlists: List<Playlist>): List<Long>

    /** Delete the playlist whose name matches [name] from the database. */
    @Query("DELETE FROM playlist WHERE name = :name")
    abstract suspend fun delete(name: String)

    /** Delete the [Playlist]s whose names are in [names] from the database. */
    @Query("DELETE FROM playlist WHERE name in (:names)")
    abstract suspend fun delete(names: List<String>)

    @Query("SELECT * FROM playlist WHERE name LIKE :filter ORDER BY name COLLATE NOCASE ASC")
    protected abstract fun getAllPlaylistsSortedByNameAsc(filter: String): Flow<List<Playlist>>

    @Query("SELECT * FROM playlist WHERE name LIKE :filter ORDER BY name COLLATE NOCASE DESC")
    protected abstract fun getAllPlaylistsSortedByNameDesc(filter: String): Flow<List<Playlist>>

    @Query("SELECT * FROM playlist WHERE name LIKE :filter")
    protected abstract fun getAllPlaylistsSortedByOrderAdded(filter: String): Flow<List<Playlist>>

    @Query("SELECT * FROM playlist WHERE name LIKE :filter ORDER BY isActive DESC, name COLLATE NOCASE ASC")
    protected abstract fun getAllPlaylistsSortedByActiveThenNameAsc(filter: String): Flow<List<Playlist>>

    @Query("SELECT * FROM playlist WHERE name LIKE :filter ORDER BY isActive DESC, name COLLATE NOCASE DESC")
    protected abstract fun getAllPlaylistsSortedByActiveThenNameDesc(filter: String): Flow<List<Playlist>>

    @Query("SELECT * FROM playlist WHERE name LIKE :filter ORDER BY isActive DESC")
    protected abstract fun getAllPlaylistsSortedByActiveThenOrderAdded(filter: String): Flow<List<Playlist>>

    /** Return a [Flow] that updates with the latest [List] of all [Playlist]s
     * in the database. The returned [List] will be sorted according to the
     * values of [sort] and [showActiveFirst], and filtered to [Playlist]s
     * whose names match [searchFilter]. [searchFilter] can be null, in which
     * case no filtering will be done. */
    fun getAllPlaylists(
        sort: Playlist.Sort,
        showActiveFirst: Boolean,
        searchFilter: String?
    ): Flow<List<Playlist>> {
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

    /** Return a [Flow] that updates with the latest [List] of
     * all [Playlist]s whose [Playlist.isActive] field is true. */
    @Query("SELECT * FROM playlist WHERE isActive")
    abstract fun getActivePlaylists(): Flow<List<Playlist>>

    /** Return a [Flow] that updates with the latest [List] of
     * [com.cliffracertech.soundaura.model.PresetPlaylist]s. This represents
     * the contents of a new [Preset], were it to be saved now. */
    @Query("SELECT name, volume FROM playlist WHERE isActive")
    abstract fun getTempPresetPlaylists():
        Flow<List<com.cliffracertech.soundaura.model.PresetPlaylist>>

    @Query("SELECT name FROM playlist")
    abstract fun getPlaylistNames(): Flow<List<String>>

    @Query("SELECT tracks FROM playlist WHERE name = :name")
    abstract suspend fun getPlaylistTracks(name: String): List<Uri>

    @Query("UPDATE playlist SET tracks = :newTracks WHERE name = :name")
    abstract suspend fun setPlaylistTracks(name: String, newTracks: List<Uri>)

    /** Remove the tracks identified by the [Uri]s in [tracks]
     * from the [Playlist] whose name matches [playlistName]. */
    @Transaction
    suspend fun removeTracksFromPlaylist(playlistName: String, tracks: List<Uri>) {
        val newTracks = getPlaylistTracks(playlistName)
                .filterNot { it in tracks }
        setPlaylistTracks(playlistName, newTracks)
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
}