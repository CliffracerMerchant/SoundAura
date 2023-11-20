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
import kotlinx.coroutines.flow.MutableStateFlow
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

/**
 * A state holder for a search query entry.
 *
 * The property [isActive] reflects whether or not the search query is active.
 * The query can be accessed through the property [value] or as a [Flow]
 * through the [flow] property. The current value can be accessed even if it
 * is inactive in case, e.g., a UI component needs to crossfade the newly
 * inactive search query to another piece of text.
 *
 * The method [toggleIsActive] toggles the search query's active state.
 * The [value] will be reset to a blank string [toggleIsActive] causes the
 * isActive state to become true. The method [set] can be used to update an
 * active search query. Note that [set] will no-op is [isActive] is false.
 */
@ActivityRetainedScoped
class SearchQueryState @Inject constructor() {
    // Originally the value was stored only in a MutableState, and entities that
    // required the value as a Flow could use a snapshotFlow. This caused tests
    // to fail for some reason, so we just store the value in both a MutableState
    // and a MutableStateFlow for convenience.

    var isActive by mutableStateOf(false)
        private set
    var value by mutableStateOf("")
        private set
    val flow = MutableStateFlow("")

    fun toggleIsActive() {
        if (!isActive) {
            value = ""
            flow.value = ""
        }
        isActive = !isActive
    }

    fun set(newQuery: String) {
        if (!isActive) return
        value = newQuery
        flow.value = newQuery
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