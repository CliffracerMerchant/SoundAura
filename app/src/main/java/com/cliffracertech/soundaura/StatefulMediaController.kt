/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.runtime.*
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MediaControllerViewModel(
    private val dao: PresetDao,
    private val dataStore: DataStore<Preferences>,
    coroutineScope: CoroutineScope?
) : ViewModel() {

    @Inject constructor(
        dao: PresetDao,
        dataStore: DataStore<Preferences>
    ) : this(dao, dataStore, null)

    private val scope = coroutineScope ?: viewModelScope
    private val activePresetKey = stringPreferencesKey(pref_key_activePresetName)

    val presetList by dao.getPresetList()
        .collectAsState(emptyList(), scope)

    private val activePresetName by dataStore.preferenceState(activePresetKey, "", scope)
    val activePreset by derivedStateOf {
        presetList.find { it.name == activePresetName }
    }

    var activePresetIsModified by mutableStateOf(false)
        private set

    fun onPresetRenameRequest(preset: Preset, newName: String) {
        scope.launch {
            dao.renamePreset(preset.name, newName)
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
        scope.launch {
            dao.loadPreset(preset.name)
            dataStore.edit {
                it[activePresetKey] = preset.name
            }
        }
    }
}

/** A MediaController */
@Composable fun StatefulMediaController(
    modifier: Modifier = Modifier,
    orientation: Orientation,
    backgroundBrush: Brush,
    contentColor: Color,
    collapsedSize: DpSize,
    expandedSize: DpSize,
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
        showingPresetSelector = showingPresetSelector,
        isPlaying = isPlaying,
        onPlayPauseClick = onPlayPauseClick,
        activePreset = viewModel.activePreset,
        activePresetIsModified = viewModel.activePresetIsModified,
        onActivePresetClick = onActivePresetClick,
        presetListProvider = { viewModel.presetList },
        onPresetRenameRequest = viewModel::onPresetRenameRequest,
        onPresetOverwriteRequest = viewModel::onPresetOverwriteRequest,
        onPresetDeleteRequest = viewModel::onPresetDeleteRequest,
        onCloseButtonClick = onCloseButtonClick,
        onPresetClick = viewModel::onPresetClick)
}