/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.model.database

import androidx.annotation.FloatRange
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import com.cliffracertech.soundaura.R
import com.cliffracertech.soundaura.dialog.NameValidator
import com.cliffracertech.soundaura.model.StringResource
import kotlinx.coroutines.flow.*

@Entity(tableName = "preset")
data class Preset(
    @ColumnInfo(name = "name") @PrimaryKey
    val name: String)

@Entity(tableName = "presetPlayable",
        primaryKeys = ["presetName", "playableName"],
        foreignKeys = [
            ForeignKey(entity = Playable::class,
                       parentColumns=["name"],
                       childColumns=["playableName"],
                       onUpdate=ForeignKey.CASCADE,
                       onDelete=ForeignKey.CASCADE),
            ForeignKey(entity = Preset::class,
                       parentColumns=["name"],
                       childColumns=["presetName"],
                       onUpdate=ForeignKey.CASCADE,
                       onDelete=ForeignKey.CASCADE)])
data class PresetPlayable(
    @ColumnInfo(name = "presetName")
    val presetName: String,

    @ColumnInfo(name = "playableName")
    val playableName: String,

    @FloatRange(from = 0.0, to = 1.0)
    @ColumnInfo(name="playableVolume")
    val playableVolume: Float = 1f)

@Dao abstract class PresetDao {
    @Query("SELECT EXISTS(SELECT name FROM preset WHERE name = :presetName)")
    abstract suspend fun exists(presetName: String): Boolean

    @Query("SELECT name FROM preset")
    abstract fun getPresetList() : Flow<List<Preset>>

    @Query("SELECT playableName AS name, playableVolume AS volume " +
           "FROM presetPlayable WHERE presetName = :presetName")
    abstract fun getPresetPlayables(presetName: String):
        Flow<List<com.cliffracertech.soundaura.model.PresetPlayable>>

    @Query("SELECT EXISTS(SELECT name FROM preset WHERE name = :name)")
    abstract suspend fun presetNameIsAlreadyInUse(name: String?): Boolean

    @Query("WITH presetPlayableNames AS " +
           "(SELECT playableName FROM presetPlayable WHERE presetName = :presetName) " +
           "UPDATE playable SET " +
           "isActive = CASE WHEN name IN presetPlayableNames THEN 1 ELSE 0 END, " +
           "volume = CASE WHEN name NOT IN presetPlayableNames THEN volume ELSE " +
               "(SELECT playableVolume FROM presetPlayable " +
                "WHERE presetName = :presetName AND playableName = playable.name LIMIT 1) END")
    abstract suspend fun loadPreset(presetName: String)

    @Query("DELETE FROM preset WHERE name = :presetName")
    protected abstract suspend fun deletePreset(presetName: String)

    @Query("DELETE FROM presetPlayable WHERE presetName = :presetName")
    protected abstract suspend fun deletePresetContents(presetName: String)

    @Query("INSERT OR IGNORE INTO preset (name) VALUES (:presetName)")
    protected abstract suspend fun addPresetName(presetName: String)

    @Query("INSERT INTO presetPlayable " +
           "SELECT :presetName, name, volume FROM playable WHERE isActive")
    protected abstract suspend fun addPresetContents(presetName: String)

    @Transaction
    open suspend fun savePreset(presetName: String) {
        addPresetName(presetName)
        deletePresetContents(presetName)
        addPresetContents(presetName)
    }

    @Query("UPDATE preset SET name = :newName WHERE name = :oldName")
    abstract suspend fun renamePreset(oldName: String, newName: String)
}

/** A [NameValidator] for naming [Preset]s. [PresetNameValidator] will return
 * an appropriate error message for blank or already in use [Preset] names. */
class PresetNameValidator (
    private val presetDao: PresetDao,
) : NameValidator() {
    var target by mutableStateOf<Preset?>(null)

    override suspend fun validateName(proposedName: String?) = when {
        proposedName?.isBlank() == true ->
            StringResource(R.string.preset_name_cannot_be_blank_error_message)
        proposedName == target?.name ->
            null
        presetDao.presetNameIsAlreadyInUse(proposedName) ->
            StringResource(R.string.preset_name_already_in_use_error_message)
        else -> null
    }
}