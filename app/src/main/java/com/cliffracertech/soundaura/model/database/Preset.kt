/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.model.database

import androidx.annotation.FloatRange
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import com.cliffracertech.soundaura.R
import com.cliffracertech.soundaura.model.StringResource
import com.cliffracertech.soundaura.model.Validator
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
    @Query("SELECT EXISTS(SELECT name FROM preset WHERE name = :name)")
    abstract suspend fun exists(name: String?): Boolean

    /** Return a [Flow] that updates with the latest [List] of all [Preset]s. */
    @Query("SELECT name FROM preset")
    abstract fun getPresetList() : Flow<List<Preset>>

    /** Return a [Flow] that updates with the latest [List] of all
     * [com.cliffracertech.soundaura.model.PresetPlaylist]s in the
     * [Preset] whose name matches [presetName]. */
    @Query("SELECT playlistName AS name, playlistVolume AS volume " +
           "FROM presetPlaylist WHERE presetName = :presetName")
    abstract fun getPresetPlaylists(presetName: String):
        Flow<List<com.cliffracertech.soundaura.model.PresetPlaylist>>

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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
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

class PresetNameValidator(
    private val dao: PresetDao,
) : Validator<String>("") {
    override suspend fun messageFor(value: String) = when {
        !valueHasBeenChanged ->
            null
        value.isBlank() ->
            Message.Error(StringResource(R.string.preset_name_cannot_be_blank_error_message))
        dao.exists(value) ->
            Message.Error(StringResource(R.string.preset_name_already_in_use_error_message))
        else -> null
    }
}