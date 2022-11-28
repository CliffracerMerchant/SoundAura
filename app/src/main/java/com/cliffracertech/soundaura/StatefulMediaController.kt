/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MediaControllerViewModel(
    private val presetDao: PresetDao,
    private val navigationState: MainActivityNavigationState,
    private val activePresetState: ActivePresetState,
    private val messageHandler: MessageHandler,
    trackDao: TrackDao,
    coroutineScope: CoroutineScope?
) : ViewModel() {

    @Inject constructor(
        dao: PresetDao,
        navigationState: MainActivityNavigationState,
        activePresetState: ActivePresetState,
        messageHandler: MessageHandler,
        trackDao: TrackDao,
    ) : this(dao, navigationState, activePresetState,
             messageHandler, trackDao, null)

    private val scope = coroutineScope ?: viewModelScope
    private val activePresetNameKey = stringPreferencesKey(pref_key_activePresetName)

    val presetList by presetDao.getPresetList()
        .map { it.toImmutableList() }
        .collectAsState(null, scope)

    val activePreset by activePresetState.activePreset.collectAsState(null, scope)
    val activePresetIsModified by activePresetState.activePresetIsModified.collectAsState(false, scope)

    val showingPresetSelector get() = navigationState.showingPresetSelector

    fun onActivePresetClick() {
        navigationState.showingPresetSelector = true
    }

    fun onPresetSelectorCloseButtonClick() {
        navigationState.showingPresetSelector = false
    }

    fun onPresetRenameRequest(preset: Preset, newName: String) {
        scope.launch {
            presetDao.renamePreset(preset.name, newName)
            if (activePreset == preset)
                activePresetState.setName(newName)
        }
    }

    private val activeTracksIsEmpty by trackDao.getActiveTracks()
        .map { it.isEmpty() }
        .collectAsState(false, scope)

    fun onPresetOverwriteRequest(preset: Preset) { when {
        activeTracksIsEmpty ->
            messageHandler.postMessage(StringResource(
                R.string.overwrite_no_active_tracks_error_message))
        preset == activePreset && !activePresetIsModified -> {
            // This prevents a pointless saving of the unmodified active preset
        } else -> scope.launch {
            presetDao.savePreset(preset.name)
            if (preset != activePreset)
                activePresetState.setName(preset.name)
            onPresetSelectorCloseButtonClick()
        }
    }}

    fun onPresetDeleteRequest(preset: Preset) {
        scope.launch {
            if (preset == activePreset)
                activePresetState.clear()
            presetDao.deletePreset(preset.name)
        }
    }

    private var newPresetAfterUnsavedChangesWarning by mutableStateOf<Preset?>(null)
    val showingUnsavedChangesWarning by derivedStateOf {
        newPresetAfterUnsavedChangesWarning != null
    }

    fun onPresetSelectorPresetClick(preset: Preset) {
        when {
            activePresetIsModified -> {
                newPresetAfterUnsavedChangesWarning = preset
            } preset == activePreset ->
                // This skips a pointless saving of the unmodified active preset
                onPresetSelectorCloseButtonClick()
            else -> loadPreset(preset)
        }
    }

    private fun loadPreset(preset: Preset) {
        scope.launch {
            activePresetState.setName(preset.name)
            presetDao.loadPreset(preset.name)
            onPresetSelectorCloseButtonClick()
        }
    }

    fun onUnsavedChangesWarningCancel() {
        newPresetAfterUnsavedChangesWarning = null
    }

    fun onUnsavedChangesWarningConfirm(saveFirst: Boolean) {
        val newPreset = newPresetAfterUnsavedChangesWarning ?: return
        if (saveFirst)
            activePreset?.let(::onPresetOverwriteRequest)
        loadPreset(newPreset)
        newPresetAfterUnsavedChangesWarning = null
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
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
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
        playing = isPlaying,
        onPlayPauseClick = onPlayPauseClick,
        activePresetProvider = viewModel::activePreset::get,
        activePresetIsModified = viewModel.activePresetIsModified,
        onActivePresetClick = viewModel::onActivePresetClick,
        showingPresetSelector = viewModel.showingPresetSelector,
        presetListProvider = viewModel::presetList::get,
        onPresetRenameRequest = viewModel::onPresetRenameRequest,
        onPresetOverwriteRequest = viewModel::onPresetOverwriteRequest,
        onPresetDeleteRequest = viewModel::onPresetDeleteRequest,
        onPresetSelectorCloseButtonClick = viewModel::onPresetSelectorCloseButtonClick,
        onPresetSelectorPresetClick = viewModel::onPresetSelectorPresetClick)

    if (viewModel.showingUnsavedChangesWarning) {
        viewModel.activePreset?.name?.let { unsavedPresetName ->
            UnsavedPresetChangesWarningDialog(
                unsavedPresetName = unsavedPresetName,
                onDismissRequest = viewModel::onUnsavedChangesWarningCancel,
                onConfirm = viewModel::onUnsavedChangesWarningConfirm)
        }
    }
}

/**
 * Show a dialog warning the user that loading a new preset will cause them
 * to lose all unsaved changes to the [Preset] named [unsavedPresetName].
 * [onDismissRequest] will be invoked when the user backs out of the dialog,
 * taps outside its bounds, or clicks the cancel button. [onConfirm] will be
 * invoked if the user wants to load the new [Preset] anyways, saving unsaved
 * changes to the [Preset] named [unsavedPresetName] first if [onConfirm]'s
 * Boolean parameter is true.
 */
@Composable fun UnsavedPresetChangesWarningDialog(
    unsavedPresetName: String,
    onDismissRequest: () -> Unit,
    onConfirm: (saveFirst: Boolean) -> Unit,
) = SoundAuraDialog(
    title = stringResource(R.string.unsaved_preset_changes_warning_title),
    text = stringResource(R.string.unsaved_preset_changes_warning_message, unsavedPresetName),
    onDismissRequest = onDismissRequest,
    buttons = {
        HorizontalDivider(Modifier.padding(top = 12.dp))
        TextButton(
            onClick = onDismissRequest,
            modifier = Modifier.minTouchTargetSize().fillMaxWidth(),
            shape = RectangleShape,
        ) { Text(stringResource(R.string.cancel)) }

        HorizontalDivider()
        TextButton(
            onClick = { onConfirm(true) },
            modifier = Modifier.minTouchTargetSize().fillMaxWidth(),
            shape = RectangleShape,
        ) { Text(stringResource(R.string.unsaved_preset_changes_warning_save_first_option)) }

        HorizontalDivider()
        TextButton(
            onClick = { onConfirm(false) },
            modifier = Modifier.minTouchTargetSize().fillMaxWidth(),
            shape = MaterialTheme.shapes.medium.bottomShape(),
        ) { Text(stringResource(R.string.unsaved_preset_changes_warning_load_anyways_option)) }
    })