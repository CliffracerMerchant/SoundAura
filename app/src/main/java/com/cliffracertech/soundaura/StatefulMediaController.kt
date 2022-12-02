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
import androidx.compose.ui.platform.LocalContext
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
    private val presetNameValidator = PresetNameValidator(presetDao)

    val presetList by presetDao.getPresetList()
        .map { it.toImmutableList() }
        .collectAsState(null, scope)

    val activePreset by activePresetState.activePreset.collectAsState(null, scope)
    val activePresetIsModified by activePresetState.activePresetIsModified.collectAsState(false, scope)

    val showingPresetSelector get() = navigationState.showingPresetSelector

    fun onActivePresetClick() {
        navigationState.showingPresetSelector = true
    }

    fun onCloseButtonClick() {
        navigationState.showingPresetSelector = false
    }

    fun onProposedPresetNameChange(newName: String) =
        presetNameValidator.setProposedName(newName)

    var renameDialogTarget by mutableStateOf<Preset?>(null)
        private set
    val proposedPresetName by presetNameValidator.proposedName.collectAsState(null, scope)
    val proposedPresetRenameErrorMessage by presetNameValidator.errorMessage.collectAsState(null, scope)

    fun onPresetRenameClick(preset: Preset) { renameDialogTarget = preset }
    fun onPresetRenameCancel() {
        renameDialogTarget = null
        presetNameValidator.clearProposedName()
    }

    fun onPresetRenameRequest(preset: Preset) {
        scope.launch {
            val name = proposedPresetName ?: preset.name
            if (presetNameValidator.onNameConfirm(name)) {
                renameDialogTarget = null
                presetDao.renamePreset(preset.name, name)
                if (activePreset == preset)
                    activePresetState.setName(name)
            }
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
            onCloseButtonClick()
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
                onCloseButtonClick()
            else -> loadPreset(preset)
        }
    }

    private fun loadPreset(preset: Preset) {
        scope.launch {
            activePresetState.setName(preset.name)
            presetDao.loadPreset(preset.name)
            onCloseButtonClick()
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
    val context = LocalContext.current
    val renameErrorMessage = remember { derivedStateOf {
        viewModel.proposedPresetRenameErrorMessage?.resolve(context)
    }}

    val presetListCallback = remember { object: PresetListCallback {
        override val listProvider = viewModel::presetList::get
        override val renameCallback = object: RenamePresetCallback {
            override val targetProvider = viewModel::renameDialogTarget::get
            override val proposedNameProvider = viewModel::proposedPresetName::get
            override val errorMessageProvider = renameErrorMessage::value::get
            override fun onProposedNameChange(newName: String) = viewModel.onProposedPresetNameChange(newName)
            override fun onRenameStart(preset: Preset) = viewModel.onPresetRenameClick(preset)
            override fun onRenameCancel() = viewModel.onPresetRenameCancel()
            override fun onRenameConfirm(preset: Preset) = viewModel.onPresetRenameRequest(preset)
        }
        override fun onOverwriteConfirm(preset: Preset) = viewModel.onPresetOverwriteRequest(preset)
        override fun onDeleteConfirm(preset: Preset) = viewModel.onPresetDeleteRequest(preset)
        override fun onPresetClick(preset: Preset) = viewModel.onPresetSelectorPresetClick(preset)
    }}

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
        presetListCallback = presetListCallback,
        onCloseButtonClick = viewModel::onCloseButtonClick)

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