/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.annotation.FloatRange
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
    @Query("SELECT name FROM preset WHERE name = :presetName")
    abstract suspend fun getPreset(presetName: String) : Preset

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

/**
 * A validator for new [Preset] names
 *
 * PresetNameValidator allows for the validation of potential [Preset] names, both
 * for entirely new [Preset]s and for new names of existing [Preset]s. When the
 * naming / renaming of a possible [Preset] is starting, [clearNewPresetName]
 * should be called. Thereafter, the name validator should be notified of changes
 * to the name input using [onNewPresetNameChange]. The [Flow]`<StringResource?>`
 * property [nameValidatorMessage] can be collected to indicate whether or not the
 * most recent name input using [onNewPresetNameChange] is valid or not. Non-null
 * [nameValidatorMessage] values should be displayed to the user so that they know
 * why their name is invalid.
 *
 * When the user indicates that the naming / renaming is finished, [onPresetNameConfirm]
 * should be called. If [onPresetNameConfirm] returns null, the name is not valid.
 * It will otherwise return the final validated name as a [String], and save this
 * name to the app's database.
 */
class PresetNameValidator(
    private val presetDao: PresetDao,
) {
    private val newPresetName = MutableStateFlow<String?>(null)

    fun onNewPresetNameChange(newName: String) {
        newPresetName.value = newName
    }
    fun clearNewPresetName() {
        newPresetName.value = null
    }

    private suspend fun nameValidator(newPresetName: String?) = when {
        newPresetName == null -> null
        newPresetName.isBlank() -> null
        else -> presetDao.presetNameIsAlreadyInUse(newPresetName)
    }

    private fun nameValidatorResultToMessage(nameIsAlreadyInUse: Boolean?) = when {
        newPresetName.value?.isBlank() == true ->
            StringResource(R.string.preset_name_cannot_be_blank_warning_message)
        nameIsAlreadyInUse == true ->
            StringResource(R.string.preset_name_already_in_use_warning_message)
        else -> null
    }

    /** A [Flow]`<StringResource?>` whose latest value represents the error
     * message that should be displayed for the most recently input new name,
     * or null if the name is valid. */
    val nameValidatorMessage = newPresetName
        .map(::nameValidator)
        .map(::nameValidatorResultToMessage)

    /** Returns null if the last preset name entered via [onNewPresetNameChange]
     * is not valid, or the final validated name otherwise. If the return value
     * is not null, the validated name will be saved to the app database. */
    suspend fun onPresetNameConfirm(): String? {
        // nameValidatorResultToMessage intentionally returns null right after
        // onClick is called, even though the newPresetName at that time is
        // null (i.e. an invalid name). This allows the user to start entering
        // a name before the no blank names warning message is displayed. This
        // check sets newPresetName.value to a blank string instead of null so
        // that the no blank names warning message is still displayed if they
        // attempt to confirm the dialog with this initial blank name.
        if (newPresetName.value == null) {
            newPresetName.value = ""
            return null
        } else {
            // Although other entities should prevent a preset from having a name
            // that nameValidatorMessage returns a non-null message for, we still
            // have to check the entered name here before we actually add it to the
            // database. This prevents the case where the user changes the input to
            // an invalid name, and then confirms the new name before the suspend
            // functions underlying nameValidatorMessage have a chance to return a
            // non-null message.
            val name = newPresetName.value ?: ""
            val nameValidatorResult = nameValidator(name)
            val message = nameValidatorResultToMessage(nameValidatorResult)
            if (message != null)
                return null
            clearNewPresetName()
            return name
        }
    }
}

/**
 * ActivePresetState holds the state of a currently active [Preset].
 * The currently active [Preset] can be collected from the [Flow]`<Preset>`
 * property [activePreset]. Whether or not the latest [Preset] emitted by
 * [activePreset] is modified can be collected from the [Flow]`<Boolean>`
 * property [activePresetIsModified]. The active [Preset] can be changed
 * or cleared with the methods [setName] and [clear].
 */
@ActivityRetainedScoped
class ActivePresetState @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    trackDao: TrackDao,
    presetDao: PresetDao,
) {
    private val activePresetNameKey = stringPreferencesKey(pref_key_activePresetName)

    /** A [Flow]`<Preset>` whose latest value is equal to the [Preset] current
     * marked as the active one. */
    val activePreset = dataStore.data
        .map { it[activePresetNameKey] }
        .map { if (it.isNullOrBlank()) null
        else presetDao.getPreset(it) }

    private val activeTracks =
        trackDao.getActiveTracks().map(List<ActiveTrack>::toHashSet)

    private val activePresetTracks = activePreset
        .transformLatest {
            if (it == null) emptyList<ActiveTrack>()
            else emitAll(presetDao.getPresetTracks(it.name))
        }.map { it.toHashSet() }

    /** A [Flow]`<Boolean>` whose latest value represents whether or not the
     * active preset is modified. */
    val activePresetIsModified =
        combine(activeTracks, activePresetTracks) { activeTracks, activePresetTracks ->
            if (activePresetTracks.isEmpty()) false
            else activeTracks != activePresetTracks
        }

    /** Set the active preset to the one whose name matches [name]. */
    suspend fun setName(name: String) {
        dataStore.edit { it[activePresetNameKey] = name }
    }

    /** Clear the active preset. */
    suspend fun clear() {
        dataStore.edit { it.remove(activePresetNameKey) }
    }
}