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
import com.cliffracertech.soundaura.model.database.Playable
import com.cliffracertech.soundaura.model.database.PlayableDao
import com.cliffracertech.soundaura.model.database.Preset
import com.cliffracertech.soundaura.model.database.PresetDao
import com.cliffracertech.soundaura.settings.PrefKeys
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import javax.inject.Inject

@ActivityRetainedScoped
class NavigationState @Inject constructor() {
    var showingAppSettings by mutableStateOf(false)
    var showingPresetSelector by mutableStateOf(false)

    fun onBackButtonClick() = when {
        showingAppSettings -> {
            showingAppSettings = false
            true
        } showingPresetSelector -> {
            showingPresetSelector = false
            true
        } else -> false
    }
}

/** A reference to a [Playable] within a [Preset]. */
class PresetPlayable(
    val name: String,
    val volume: Float)

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
    playableDao: PlayableDao,
    presetDao: PresetDao,
) {
    private val nameKey = stringPreferencesKey(PrefKeys.activePresetName)

    /** A [Flow]`<Preset>` whose latest value is equal to the [Preset] current
     * marked as the active one. */
    val name = dataStore.data.map { it[nameKey] }.map { when {
        it.isNullOrBlank() -> null
        presetDao.exists(it) -> it
        else -> null
    }}

    private val allActiveTracks = playableDao
            .getCurrentPresetPlayables()
            .map(List<PresetPlayable>::toHashSet)

    private val presetTracks = name.transformLatest {
        if (it == null) emptyList<PresetPlayable>()
        else emitAll(presetDao.getPresetPlayables(it))
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