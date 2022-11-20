/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cliffracertech.soundaura.SoundAura.pref_key_activePresetName
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@ActivityRetainedScoped
class ActivePresetState @Inject constructor(
    dataStore: DataStore<Preferences>,
    trackDao: TrackDao,
    presetDao: PresetDao,
) {
    private val activePresetNameKey = stringPreferencesKey(pref_key_activePresetName)
    private val activePresetName = dataStore.preferenceFlow(activePresetNameKey, "")
    val activePreset = activePresetName
        .map { if (it.isBlank()) null
               else presetDao.getPreset(it) }

    private val activeTracks =
        trackDao.getAllActiveTracks().map { it.toHashSet() }
    private val activePresetTracks = activePresetName
        .transformLatest {
            emitAll(presetDao.getPresetTracks(it))
        }.map { it.toHashSet() }
    val activePresetIsModified =
        combine(activeTracks, activePresetTracks) { activeTracks, activePresetTracks ->
            if (activePresetTracks.isEmpty()) false
            else activeTracks != activePresetTracks
        }
}

@HiltViewModel
class MediaControllerViewModel(
    private val dao: PresetDao,
    private val dataStore: DataStore<Preferences>,
    activePresetState: ActivePresetState,
    coroutineScope: CoroutineScope?
) : ViewModel() {

    @Inject constructor(
        dao: PresetDao,
        dataStore: DataStore<Preferences>,
        activePresetState: ActivePresetState
    ) : this(dao, dataStore, activePresetState, null)

    private val scope = coroutineScope ?: viewModelScope
    private val activePresetNameKey = stringPreferencesKey(pref_key_activePresetName)

    val presetList by dao.getPresetList()
        .map { it.toImmutableList() }
        .collectAsState(listOf<Preset>().toImmutableList(), scope)

    val activePreset by activePresetState.activePreset.collectAsState(null, scope)
    val activePresetIsModified by activePresetState.activePresetIsModified.collectAsState(false, scope)

    fun onPresetRenameRequest(preset: Preset, newName: String) {
        scope.launch {
            dao.renamePreset(preset.name, newName)
            if (activePreset == preset)
                dataStore.edit {
                    it[activePresetNameKey] = newName
                }
        }
    }

    fun onPresetOverwriteRequest(preset: Preset) {
        scope.launch {
            dao.savePreset(preset.name)
        }
    }

    fun onPresetDeleteRequest(preset: Preset) {
        scope.launch {
            dao.deletePreset(preset.name)
        }
    }

    fun onPresetClick(preset: Preset) {
        if (activePreset == preset && !activePresetIsModified)
            return
        scope.launch {
            dao.loadPreset(preset.name)
            dataStore.edit {
                it[activePresetNameKey] = preset.name
            }
        }
    }
}

/** A [MediaController] with state provided by an instance of [MediaControllerViewModel]. */
@Composable fun StatefulMediaController(
    modifier: Modifier = Modifier,
    orientation: Orientation,
    backgroundBrush: Brush,
    contentColor: Color,
    collapsedSize: DpSize,
    expandedSize: DpSize,
    alignment: BiasAlignment,
    padding: PaddingValues,
    showingPresetSelector: Boolean,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onActivePresetClick: () -> Unit,
    onCloseButtonClick: () -> Unit,
) {
    val viewModel: MediaControllerViewModel = viewModel()
    MediaController(
        modifier = modifier,
        orientation = orientation,
        backgroundBrush = backgroundBrush,
        contentColor = contentColor,
        collapsedSize = collapsedSize,
        expandedSize = expandedSize,
        alignment = alignment,
        padding = padding,
        showingPresetSelector = showingPresetSelector,
        isPlaying = isPlaying,
        onPlayPauseClick = onPlayPauseClick,
        activePresetProvider = viewModel::activePreset::get,
        activePresetIsModified = viewModel.activePresetIsModified,
        onActivePresetClick = onActivePresetClick,
        presetListProvider = viewModel::presetList::get,
        onPresetRenameRequest = viewModel::onPresetRenameRequest,
        onPresetOverwriteRequest = viewModel::onPresetOverwriteRequest,
        onPresetDeleteRequest = viewModel::onPresetDeleteRequest,
        onCloseButtonClick = onCloseButtonClick,
        onPresetClick = viewModel::onPresetClick)
}