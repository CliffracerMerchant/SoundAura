/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.model.database

import androidx.annotation.FloatRange
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import com.cliffracertech.soundaura.R
import com.cliffracertech.soundaura.model.Validator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*

@Entity(tableName = "preset")
data class Preset(@PrimaryKey val name: String)

@Entity(tableName = "presetPlaylist",
        primaryKeys = ["presetName", "playlistName"],
        foreignKeys = [
            ForeignKey(entity = Playlist::class,
                       parentColumns=["name"],
                       childColumns=["playlistName"],
                       onUpdate=ForeignKey.CASCADE,
                       onDelete=ForeignKey.CASCADE),
            ForeignKey(entity = Preset::class,
                       parentColumns=["name"],
                       childColumns=["presetName"],
                       onUpdate=ForeignKey.CASCADE,
                       onDelete=ForeignKey.CASCADE)])
data class PresetPlaylist(
    val presetName: String,
    val playlistName: String,
    @FloatRange(from = 0.0, to = 1.0)
    val playlistVolume: Float = 1f)

@Dao abstract class PresetDao {
    /** Return whether or not the [Preset] whose name matches [name] exists. */
    @Query("SELECT EXISTS(SELECT 1 FROM preset WHERE name = :name)")
    abstract suspend fun exists(name: String?): Boolean

    /** Return a [Flow] that updates with the latest [List] of all [Preset]s. */
    @Query("SELECT name FROM preset")
    abstract fun getPresetList() : Flow<List<Preset>>

    /** Return a [List] of the names of all of the playlists in the
     * [Preset] whose name matches [presetName]. */
    @Query("SELECT playlistName FROM presetPlaylist WHERE presetName = :presetName")
    abstract suspend fun getPlaylistNamesFor(presetName: String): List<String>

    // The first select statement selects active playlists that are not
    // part of the preset or are not at their preset volume. The second
    // select statement selects playlists that are in the active preset
    // that are inactive. Because SQLite gives EXCEPT and UNION equal
    // precedence, the two select statements must be done in this order.
    @Query("SELECT EXISTS(" +
           "SELECT name, volume " +
           "FROM playlist WHERE isActive " +
           "EXCEPT SELECT playlistName, playlistVolume " +
                  "FROM presetPlaylist " +
                  "WHERE presetName = :presetName " +
             "UNION " +
           "SELECT name, volume FROM playlist " +
           "JOIN presetPlaylist ON playlistName = name " +
           "WHERE NOT isActive AND presetName = :presetName)")
    abstract fun getPresetIsModified(presetName: String): Flow<Boolean>

    /** Delete the [Preset] identified by [presetName]. */
    @Query("DELETE FROM preset WHERE name = :presetName")
    abstract suspend fun deletePreset(presetName: String)

    /**
     * Update the playlist table according to whether or not each [Playlist]
     * is in the [Preset] identified by [presetName] in the following way:
     * - [Playlist]s in the [Preset]:
     *     The isActive field will be set to true.
     *     The volume field will be set to the value stored in the [Preset].
     * - [Playlist]s not in the [Preset]:
     *     The isActive field will be set to false.
     *     The volume field will not be modified.
     */
    @Query("WITH presetPlaylistNames AS " +
           "(SELECT playlistName FROM presetPlaylist WHERE presetName = :presetName) " +
           "UPDATE playlist SET " +
           "isActive = CASE WHEN name IN presetPlaylistNames THEN 1 ELSE 0 END, " +
           "volume = CASE WHEN name NOT IN presetPlaylistNames THEN volume ELSE " +
               "(SELECT playlistVolume FROM presetPlaylist " +
                "WHERE presetName = :presetName AND playlistName = playlist.name LIMIT 1) END")
    abstract suspend fun loadPreset(presetName: String)

    /** Rename the [Preset] identified by [oldName] to [newName]. */
    @Query("UPDATE preset SET name = :newName WHERE name = :oldName")
    abstract suspend fun renamePreset(oldName: String, newName: String)

    @Query("INSERT OR IGNORE INTO preset VALUES (:presetName)")
    protected abstract suspend fun addPresetName(presetName: String)

    @Query("DELETE FROM presetPlaylist WHERE presetName = :presetName")
    protected abstract suspend fun deletePresetContents(presetName: String)

    @Query("INSERT INTO presetPlaylist " +
           "SELECT :presetName, name, volume FROM playlist WHERE isActive")
    protected abstract suspend fun addPresetContents(presetName: String)

    /** Overwrite the [Preset] identified by [presetName] with the [Playlist]s
     * whose isActive field is true, along with their current volumes. */
    @Transaction
    open suspend fun savePreset(presetName: String) {
        addPresetName(presetName)
        deletePresetContents(presetName)
        addPresetContents(presetName)
    }
}

/**
 * Return a [Validator] that validates a name for a new [Preset].
 *
 * Names that match an existing [Preset] are not permitted. Blank names
 * are also not permitted, although no error message will be shown for
 * blank names unless [Validator.value] has been changed at least once.
 * This is to prevent showing a 'no blank names' error message before
 * the user has had a chance to change the name.
 */
fun newPresetNameValidator(
    dao: PresetDao,
    coroutineScope: CoroutineScope,
) = Validator(
    initialValue = "",
    coroutineScope = coroutineScope,
    messageFor = { name, hasBeenChanged -> when {
        name.isBlank() && hasBeenChanged ->
            Validator.Message.Error(R.string.name_dialog_blank_name_error_message)
        dao.exists(name) ->
            Validator.Message.Error(R.string.name_dialog_duplicate_name_error_message)
        else -> null
    }})

/**
 * Return a [Validator] that validates the renaming of an existing [Preset].
 *
 * Blank names are not permitted. Names that match an existing [Preset]
 * are also not permitted, unless it is equal to the provided [oldName].
 * This is to prevent a 'no duplicate names' error message from being
 * shown immediately when the dialog is opened.
 */
fun presetRenameValidator(
    dao: PresetDao,
    coroutineScope: CoroutineScope,
    oldName: String,
) = Validator(
    initialValue = oldName,
    coroutineScope = coroutineScope,
    messageFor = { name, _ -> when {
        name == oldName ->  null
        name.isBlank() ->   Validator.Message.Error(R.string.name_dialog_blank_name_error_message)
        dao.exists(name) -> Validator.Message.Error(R.string.name_dialog_duplicate_name_error_message)
        else ->             null
    }})