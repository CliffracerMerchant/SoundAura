/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.annotation.FloatRange
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.*
import com.cliffracertech.soundaura.SoundAura.pref_key_activePresetName
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@Entity(tableName = "preset")
data class Preset(
    @ColumnInfo(name = "name") @PrimaryKey
    val name: String)

@Entity(tableName = "presetTrack",
        primaryKeys = ["presetName", "trackUriString"],
        foreignKeys = [
            ForeignKey(entity = Track::class,
                       parentColumns=["uriString"],
                       childColumns=["trackUriString"],
                       onUpdate=ForeignKey.CASCADE,
                       onDelete=ForeignKey.CASCADE),
            ForeignKey(entity = Preset::class,
                       parentColumns=["name"],
                       childColumns=["presetName"],
                       onUpdate=ForeignKey.CASCADE,
                       onDelete=ForeignKey.CASCADE)])
data class PresetTrack(
    @ColumnInfo(name = "presetName")
    val presetName: String,

    @ColumnInfo(name = "trackUriString")
    val trackUriString: String,

    @FloatRange(from = 0.0, to = 1.0)
    @ColumnInfo(name="trackVolume")
    val trackVolume: Float = 1f)

@Dao abstract class PresetDao {
    @Query("SELECT EXISTS(SELECT name FROM preset WHERE name = :presetName)")
    abstract suspend fun exists(presetName: String): Boolean

    @Query("SELECT name FROM preset")
    abstract fun getPresetList() : Flow<List<Preset>>

    @Query("SELECT trackUriString AS uriString, trackVolume AS volume " +
            "FROM presetTrack WHERE presetName = :presetName")
    abstract fun getPresetTracks(presetName: String) : Flow<List<ActiveTrack>>

    @Query("SELECT EXISTS(SELECT name FROM preset WHERE name = :name)")
    abstract suspend fun presetNameIsAlreadyInUse(name: String?): Boolean

    @Query("WITH presetUriStrings AS " +
            "(SELECT trackUriString FROM presetTrack WHERE presetName = :presetName) " +
            "UPDATE track SET " +
            "isActive = CASE WHEN uriString IN presetUriStrings THEN 1 ELSE 0 END, " +
            "volume = CASE WHEN uriString NOT IN presetUriStrings THEN volume ELSE " +
            "(SELECT trackVolume FROM presetTrack " +
            "WHERE presetName = :presetName AND trackUriString = uriString LIMIT 1) END")
    abstract suspend fun loadPreset(presetName: String)

    @Query("DELETE FROM preset WHERE name = :presetName")
    protected abstract suspend fun deletePresetName(presetName: String)

    @Query("DELETE FROM presetTrack WHERE presetName = :presetName")
    protected abstract suspend fun deletePresetContents(presetName: String)

    @Transaction
    open suspend fun deletePreset(presetName: String) {
        deletePresetContents(presetName)
        deletePresetName(presetName)
    }

    @Query("INSERT OR IGNORE INTO preset (name) VALUES (:presetName)")
    protected abstract suspend fun addPresetName(presetName: String)

    @Query("INSERT INTO presetTrack " +
            "SELECT :presetName, uriString, volume FROM track WHERE isActive")
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
class PresetNameValidator(
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

/**
 * ActivePresetState holds the state of a currently active [Preset]. The name
 * of the currently active [Preset] can be collected from the [Flow]`<String?>`
 * property [name]. Whether or not the active [Preset] is modified can be
 * collected from the [Flow]`<Boolean>` property [isModified]. The active
 * [Preset] can be changed or cleared with the methods [setName] and [clear].
 */
@ActivityRetainedScoped
class ActivePresetState @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    trackDao: TrackDao,
    presetDao: PresetDao,
) {
    private val nameKey = stringPreferencesKey(pref_key_activePresetName)

    /** A [Flow]`<Preset>` whose latest value is equal to the [Preset] current
     * marked as the active one. */
    val name = dataStore.data.map { it[nameKey] }.map { when {
        it.isNullOrBlank() -> null
        presetDao.exists(it) -> it
        else -> null
    }}

    private val allActiveTracks =
        trackDao.getActiveTracks().map(List<ActiveTrack>::toHashSet)

    private val presetTracks = name.transformLatest {
            if (it == null) emptyList<ActiveTrack>()
            else emitAll(presetDao.getPresetTracks(it))
        }.map { it.toHashSet() }

    /** A [Flow]`<Boolean>` whose latest value represents whether or not the
     * active preset is modified. */
    val isModified = combine(allActiveTracks, presetTracks) { activeTracks, presetTracks ->
        if (presetTracks.isEmpty()) false
        else activeTracks != presetTracks
    }

    /** Set the active preset to the one whose name matches [name]. */
    suspend fun setName(name: String) {
        dataStore.edit { it[nameKey] = name }
    }

    /** Clear the active preset. */
    suspend fun clear() {
        dataStore.edit { it.remove(nameKey) }
    }
}