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
import com.cliffracertech.soundaura.model.ListValidator
import com.cliffracertech.soundaura.model.StringResource
import com.cliffracertech.soundaura.model.Validator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
            ForeignKey(
                entity = Playlist::class,
                parentColumns=["name"],
                childColumns=["playlistName"],
                onUpdate=ForeignKey.CASCADE,
                onDelete=ForeignKey.CASCADE),
            ForeignKey(
                entity = Track::class,
                parentColumns=["uri"],
                childColumns=["trackUri"],
                onUpdate=ForeignKey.CASCADE,
                onDelete=ForeignKey.CASCADE)])
data class PlaylistTrack(
    val playlistName: String,
    val trackUri: Uri,
    val playlistOrder: Int)

internal fun List<Uri>.toPlaylistTrackList(playlistName: String) =
    mapIndexed { index, uri -> PlaylistTrack(playlistName, uri, index) }

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
    protected abstract suspend fun insertPlaylistName(playlist: Playlist)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertPlaylistNames(playlists: List<Playlist>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertTracks(tracks: List<Track>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertPlaylistTrack(playlistTrack: PlaylistTrack)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertPlaylistTracks(playlistTracks: List<PlaylistTrack>)

    /** Delete the playlist whose name matches [name] from the database. */
    @Query("DELETE FROM playlist WHERE name = :name")
    protected abstract suspend fun deletePlaylistName(name: String)

    @Query("DELETE FROM playlistTrack WHERE playlistName = :playlistName")
    protected abstract suspend fun deleteAllPlaylistTracks(playlistName: String)

    @Query("DELETE FROM playlistTrack WHERE playlistName in (:playlistNames)")
    protected abstract suspend fun deleteAllPlaylistTracks(playlistNames: List<String>)

    @Query("DELETE FROM track WHERE uri IN (:uris)")
    protected abstract suspend fun deleteTracks(uris: List<Uri>)

    @Query("SELECT shuffle FROM playlist " +
            "WHERE playlist.name = :playlistName LIMIT 1")
    abstract suspend fun getPlaylistShuffle(playlistName: String): Boolean

    @Query("UPDATE playlist SET shuffle = :enabled " +
            "WHERE name = :playlistName")
    abstract suspend fun setPlaylistShuffle(playlistName: String, enabled: Boolean)

    /** Insert a single [Playlist] whose [Playlist.name] and [Playlist.shuffle]
     * vales will be equal to [playlistName] and [shuffle], respectively. The
     * [Uri]s in [trackUris] will be added as the contents of the playlist. */
    @Transaction
    open suspend fun insertPlaylist(
        playlistName: String,
        shuffle: Boolean,
        trackUris: List<Uri>
    ) {
        insertPlaylistName(Playlist(playlistName, shuffle))
        insertTracks(trackUris.map(::Track))
        insertPlaylistTracks(trackUris.toPlaylistTrackList(playlistName))
    }

    /** Insert a collection of new single-track [Playlist]. The [Playlist.name]
     * property for each new playlist will be equal to the corresponding
     * [String] in [playlistNames], while the single track will be equal to the
     * corresponding [Uri] in [trackUris]. The [Playlist.shuffle] value for the
     * new playlists will be the default value (i.e. false) due to shuffle
     * having no meaning for single-track playlists. */
    @Transaction
    open suspend fun insertSingleTrackPlaylists(
        playlistNames: List<String>,
        trackUris: List<Uri>
    ) {
        assert(playlistNames.size == trackUris.size)
        insertPlaylistNames(playlistNames.map(::Playlist))
        insertTracks(trackUris.map(::Track))
        insertPlaylistTracks(trackUris.mapIndexed { index, uri ->
            PlaylistTrack(playlistNames[index], uri, index)
        })
    }

    /**
     * Set the [Playlist] whose [Playlist.name] property matches [playlistName]
     * to have a [Playlist.shuffle] value equal to [shuffleEnabled], and
     * overwrite its tracks to be equal to [newTracks]. If the old tracks of
     * the playlist are already on hand, they can be provided as the value of
     * [oldTracks] to prevent needing to read them from the database again.
     *
     * @return The [List] of [Uri]s that are no longer in any [Playlist].
     */
    @Transaction
    open suspend fun setPlaylistShuffleAndContents(
        playlistName: String,
        shuffleEnabled: Boolean,
        newTracks: List<Uri>,
        oldTracks: List<Uri>? = null,
    ): List<Uri> {
        setPlaylistShuffle(playlistName, shuffleEnabled)

        val removableTracks = getUniqueTracksNotIn(newTracks, playlistName)
        deleteTracks(removableTracks)

        val newTracksWereAdded = newTracks.size >
            (oldTracks ?: getPlaylistTracks(playlistName)).size
        if (newTracksWereAdded)
            insertTracks(newTracks.map(::Track))

        deleteAllPlaylistTracks(playlistName)
        insertPlaylistTracks(newTracks.toPlaylistTrackList(playlistName))

        return removableTracks
    }

    /** Return the track uris of the [Playlist] identified by
     * [playlistName] that are not in any other [Playlist]s. */
    @Query("SELECT trackUri FROM playlistTrack " +
           "WHERE playlistName = :playlistName " +
           "GROUP BY trackUri HAVING COUNT(playlistName) = 1")
    protected abstract suspend fun getUniqueTracks(playlistName: String): List<Uri>

    /** Return the track uris of the [Playlist] identified by [playlistName]
     * that are not in any other [Playlist]s and are not in [exceptions]. */
    @Query("SELECT trackUri FROM playlistTrack " +
           "WHERE playlistName = :playlistName AND trackUri NOT IN (:exceptions) " +
           "GROUP BY trackUri HAVING COUNT(playlistName) = 1")
    protected abstract suspend fun getUniqueTracksNotIn(
        exceptions: List<Uri>,
        playlistName: String
    ): List<Uri>

    /** Delete the [Playlist] whose name matches [name] along with its contents.
     * @return the [List] of [Uri]s that are no longer a part of any playlist */
    @Transaction
    open suspend fun deletePlaylist(name: String): List<Uri> {
         val removableTracks = getUniqueTracks(name)
        deletePlaylistName(name)
        // playlistTrack.playlistName has a on delete: cascade policy,
        // so the playlistTrack rows don't need to be deleted manually
        deleteTracks(removableTracks)
        return removableTracks
    }

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
     * [com.cliffracertech.soundaura.model.PresetPlaylist]s. This
     * represents the contents of a [Preset] if it was created now. */
    @Query("SELECT name, volume FROM playlist WHERE isActive")
    abstract fun getTempPresetPlaylists():
        Flow<List<com.cliffracertech.soundaura.model.PresetPlaylist>>

    @Query("SELECT name FROM playlist")
    abstract fun getPlaylistNames(): Flow<List<String>>

    @Query("SELECT trackUri FROM playlistTrack WHERE playlistName = :name ORDER by playlistOrder")
    abstract suspend fun getPlaylistTracks(name: String): List<Uri>

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
}

class TrackNamesValidator(
    private val playlistDao: PlaylistDao,
    coroutineScope: CoroutineScope,
    names: List<String>,
) : ListValidator<String>(names, allowDuplicates = false) {

    private var existingNames: Set<String>? = null
    init { coroutineScope.launch {
        existingNames = playlistDao.getPlaylistNames().first().toSet()
    }}

    override fun hasError(value: String) =
        value.isBlank() || existingNames?.contains(value) == true

    override val errorMessage = Validator.Message.Error(
        StringResource(R.string.add_multiple_tracks_name_error_message))

    override suspend fun validate(): List<String>? {
        val existingNames = playlistDao.getPlaylistNames().first().toSet()
        return when {
            values.intersect(existingNames).isNotEmpty() -> null
            values.containsBlanks() -> null
            else -> super.validate()
        }
    }

    /** Return whether the list contains any strings that are blank
     * (i.e. are either empty or consist of only whitespace characters). */
    private fun List<String>.containsBlanks() = find { it.isBlank() } != null
}

/**
 * Return a [Validator] that validates [Playlist] names.
 *
 * Blank names are not permitted, although no error message will be shown for
 * blank names unless [Validator.value] has been changed at least once. This
 * is to prevent a new [Playlist] name dialog with an initially blank name
 * from immediately showing a 'no blank names' error message before the user
 * has had a chance to change the name.
 *
 * Names that match an existing [Playlist]'s name are not permitted, unless
 * it is equal to the provided [initialName] and [ignoreInitialValue] is true.
 * This is to prevent an error message for a rename [Playlist] dialog from
 * immediately being shown when the dialog is opened. Dialogs to name a new
 * [Playlist] would usually set [ignoreInitialValue] to false so that the
 * initial name will cause an error to be shown immediately if it matches an
 * existing [Playlist]'s name.
 */
fun playlistNameValidator(
    playlistDao: PlaylistDao,
    coroutineScope: CoroutineScope,
    initialName: String,
    ignoreInitialValue: Boolean,
) = Validator(
    initialValue = initialName,
    coroutineScope = coroutineScope,
    messageFor = { name, hasBeenChanged ->  when {
        ignoreInitialValue && name == initialName ->
            null
        name.isBlank() && hasBeenChanged ->
            Validator.Message.Error(R.string.add_playlist_blank_name_error_message)
        playlistDao.exists(name) ->
            Validator.Message.Error(R.string.add_playlist_duplicate_name_error_message)
        else -> null
    }})