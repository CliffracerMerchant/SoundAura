/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cliffracertech.soundaura.edit
import com.cliffracertech.soundaura.model.database.Preset
import com.cliffracertech.soundaura.model.database.PresetDao
import com.cliffracertech.soundaura.settings.PrefKeys
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import javax.inject.Inject

@ActivityRetainedScoped
class NavigationState @Inject constructor() {
    var showingAppSettings by mutableStateOf(false)
    var showingPresetSelector by mutableStateOf(false)

    val willConsumeBackButtonClick get() =
        showingAppSettings || showingPresetSelector

    fun onBackButtonClick() { when {
        showingAppSettings -> {
            showingAppSettings = false
        } showingPresetSelector -> {
            showingPresetSelector = false
        }
    }}
}

/** A state holder for a search query entry. */
@ActivityRetainedScoped
class SearchQueryState @Inject constructor() {
    var query by mutableStateOf<String?>(null)
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
    private val presetDao: PresetDao,
) {
    private val nameKey = stringPreferencesKey(PrefKeys.activePresetName)

    /** A [Flow]`<Preset>` whose latest value is equal to the [Preset] current
     * marked as the active one. */
    val name = dataStore.data.map { prefs ->
        val value = prefs[nameKey]
        if (value.isNullOrBlank() || !presetDao.exists(value))
            null
        else value
    }

    val isModified = name.transformLatest { activePresetName ->
        if (activePresetName == null) emit(false)
        else emitAll(presetDao.getPresetIsModified(activePresetName))
    }

    /** Set the active preset to the one whose name matches [name]. */
    suspend fun setName(name: String) = dataStore.edit(nameKey, name)

    /** Clear the active preset. */
    suspend fun clear() {
        dataStore.edit { it.remove(nameKey) }
    }
}