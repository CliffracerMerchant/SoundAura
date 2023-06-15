/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.mediacontroller

import android.util.Range
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.SnackbarDuration
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cliffracertech.soundaura.HorizontalDivider
import com.cliffracertech.soundaura.R
import com.cliffracertech.soundaura.bottomShape
import com.cliffracertech.soundaura.collectAsState
import com.cliffracertech.soundaura.dialog.SoundAuraDialog
import com.cliffracertech.soundaura.edit
import com.cliffracertech.soundaura.minTouchTargetSize
import com.cliffracertech.soundaura.model.ActivePresetState
import com.cliffracertech.soundaura.model.MessageHandler
import com.cliffracertech.soundaura.model.NavigationState
import com.cliffracertech.soundaura.model.StringResource
import com.cliffracertech.soundaura.model.database.PlaylistDao
import com.cliffracertech.soundaura.model.database.Preset
import com.cliffracertech.soundaura.model.database.PresetDao
import com.cliffracertech.soundaura.model.database.PresetNameValidator
import com.cliffracertech.soundaura.preferenceState
import com.cliffracertech.soundaura.settings.PrefKeys
import com.cliffracertech.soundaura.tweenDuration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class MediaControllerViewModel(
    private val presetDao: PresetDao,
    private val navigationState: NavigationState,
    private val activePresetState: ActivePresetState,
    private val messageHandler: MessageHandler,
    private val dataStore: DataStore<Preferences>,
    playlistDao: PlaylistDao,
    coroutineScope: CoroutineScope?
) : ViewModel() {

    @Inject constructor(
        dao: PresetDao,
        navigationState: NavigationState,
        activePresetState: ActivePresetState,
        messageHandler: MessageHandler,
        dataStore: DataStore<Preferences>,
        playlistDao: PlaylistDao,
    ) : this(dao, navigationState, activePresetState,
             messageHandler, dataStore, playlistDao, null)

    private val scope = coroutineScope ?: viewModelScope

    val presetList by presetDao.getPresetList()
        .map { it.toImmutableList() }
        .collectAsState(null, scope)

    val activePresetName by activePresetState.name.collectAsState(null, scope)
    val activePresetIsModified by activePresetState.isModified.collectAsState(false, scope)
    private val Preset.isActive get() = name == activePresetName

    private val playButtonLongClickHintShownKey =
        booleanPreferencesKey(PrefKeys.playButtonLongClickHintShown)
    private val playButtonLongClickHintShown by
        dataStore.preferenceState(playButtonLongClickHintShownKey, false, scope)

    fun onPlayButtonClick() {
        if (playButtonLongClickHintShown || activePlaylistsIsEmpty)
            return // We don't want to show the hint if there are no active tracks
                   // because the PlayerService should show a message about there
                   // being no active tracks
        messageHandler.postMessage(
            StringResource(R.string.play_button_long_click_hint_text),
            SnackbarDuration.Long)
        dataStore.edit(playButtonLongClickHintShownKey, true, scope)
    }

    val showingMediaController get() = !navigationState.showingAppSettings
    val showingPresetSelector get() = navigationState.showingPresetSelector

    fun onActivePresetClick() {
        navigationState.showingPresetSelector = true
    }

    fun onCloseButtonClick() {
        navigationState.showingPresetSelector = false
    }

    private val nameValidator = PresetNameValidator(presetDao)
    var renameDialogTarget by mutableStateOf<Preset?>(null)
        private set
    val proposedPresetName by nameValidator::value
    val proposedPresetNameMessage by nameValidator.message.collectAsState(null, scope)

    fun onPresetRenameClick(preset: Preset) { renameDialogTarget = preset }

    fun onPresetRenameCancel() {
        renameDialogTarget = null
        nameValidator.clear()
    }

    fun onProposedPresetNameChange(newName: String) {
        nameValidator.value = newName
    }

    fun onPresetRenameConfirm() {
        val preset = renameDialogTarget ?: return
        scope.launch {
            val name = nameValidator.validate()
                ?: return@launch
            if (name != renameDialogTarget?.name)
                presetDao.renamePreset(preset.name, name)
            renameDialogTarget = null
            if (preset.isActive)
                activePresetState.setName(name)
        }
    }

    private val activePlaylistsIsEmpty by playlistDao
        .getAtLeastOnePlaylistIsActive()
        .collectAsState(true, scope)

    private fun onPresetOverwriteRequest(presetName: String) {
        val isActive = presetName == activePresetName
        when {
            activePlaylistsIsEmpty ->
                messageHandler.postMessage(StringResource(
                    R.string.overwrite_no_active_tracks_error_message))
            isActive && !activePresetIsModified -> {
                // This prevents a pointless saving of the unmodified active preset
            } else -> scope.launch {
                presetDao.savePreset(presetName)
                // Since the current sound mix is being saved to the preset
                // whose name == presetName, we want to make it the active
                // preset if it isn't currently.
                if (!isActive)
                    activePresetState.setName(presetName)
                onCloseButtonClick()
            }
        }
    }

    fun onPresetOverwriteRequest(preset: Preset) =
        onPresetOverwriteRequest(preset.name)

    fun onPresetDeleteRequest(preset: Preset) {
        scope.launch {
            if (preset.isActive)
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
            } preset.isActive ->
                // This skips a pointless loading of the unmodified active preset
                onCloseButtonClick()
            else -> loadPreset(preset)
        }
    }
    // TODO: Figure out why UI stutters when selecting new preset
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
            activePresetName?.let(::onPresetOverwriteRequest)
        loadPreset(newPreset)
        newPresetAfterUnsavedChangesWarning = null
    }
}

/** A [MediaController] with state provided by an instance of [MediaControllerViewModel]. */
@Composable fun BoxWithConstraintsScope.SoundAuraMediaController(
    modifier: Modifier = Modifier,
    sizes: MediaControllerSizes,
    alignment: BiasAlignment,
    padding: PaddingValues,
    isPlayingProvider: () -> Boolean,
    onPlayButtonClick: () -> Unit,
    stopTimeProvider: () -> Instant?,
    onNewStopTimerRequest: (Duration) -> Unit,
    onCancelStopTimerRequest: () -> Unit,
) {
    val viewModel: MediaControllerViewModel = viewModel()
    var showingSetStopTimerDialog by rememberSaveable { mutableStateOf(false) }
    var showingCancelStopTimerDialog by rememberSaveable { mutableStateOf(false) }

    val startColor = MaterialTheme.colors.primaryVariant
    val endColor = MaterialTheme.colors.secondaryVariant
    val backgroundBrush = remember(startColor, endColor) {
        Brush.horizontalGradient(colors = listOf(startColor, endColor))
    }

    val presetListCallback = remember { object: PresetListCallback {
        override val listProvider = viewModel::presetList::get
        override val renameTargetProvider = viewModel::renameDialogTarget::get
        override val proposedNameProvider = viewModel::proposedPresetName::get
        override val renameErrorMessageProvider = viewModel::proposedPresetNameMessage::get
        override fun onProposedNameChange(newName: String) = viewModel.onProposedPresetNameChange(newName)
        override fun onRenameStart(preset: Preset) = viewModel.onPresetRenameClick(preset)
        override fun onRenameCancel() = viewModel.onPresetRenameCancel()
        override fun onRenameConfirm() = viewModel.onPresetRenameConfirm()
        override fun onOverwriteConfirm(preset: Preset) = viewModel.onPresetOverwriteRequest(preset)
        override fun onDeleteConfirm(preset: Preset) = viewModel.onPresetDeleteRequest(preset)
        override fun onPresetClick(preset: Preset) = viewModel.onPresetSelectorPresetClick(preset)
    }}

    val playButtonCallback = remember(isPlayingProvider, onPlayButtonClick) {
        PlayButtonCallback(
            isPlayingProvider,
            onClick = {
                viewModel.onPlayButtonClick()
                onPlayButtonClick()
            }, clickLabelResIdProvider = {isPlaying ->
                if (isPlaying) R.string.pause_button_description
                else           R.string.play_button_description
            }, onLongClick = {
                showingSetStopTimerDialog = true
            }, longClickLabelResId = R.string.play_pause_button_long_click_description)
    }

    val enterSpec = tween<Float>(
        durationMillis = tweenDuration,
        delayMillis = tweenDuration / 3,
        easing = LinearOutSlowInEasing)
    val exitSpec = tween<Float>(
        durationMillis = tweenDuration,
        easing = LinearOutSlowInEasing)
    val transformOrigin = rememberClippedBrushBoxTransformOrigin(
        alignment, padding,
        dpSize = sizes.collapsedSize(stopTimeProvider() != null))

    AnimatedVisibility(
        visible = viewModel.showingMediaController,
        enter = fadeIn(enterSpec) + scaleIn(enterSpec, 0.8f, transformOrigin),
        exit = fadeOut(exitSpec) + scaleOut(exitSpec, 0.8f, transformOrigin)
    ) {
        MediaController(
            modifier = modifier,
            sizes = sizes,
            backgroundBrush = backgroundBrush,
            contentColor = MaterialTheme.colors.onPrimary,
            alignment = alignment,
            padding = padding,
            activePresetCallback = remember {
                ActivePresetCallback(
                    nameProvider = viewModel::activePresetName::get,
                    isModifiedProvider = viewModel::activePresetIsModified::get,
                    onClick = viewModel::onActivePresetClick)
            }, playButtonCallback = playButtonCallback,
            stopTimeProvider = stopTimeProvider,
            onStopTimerClick = { showingCancelStopTimerDialog = true },
            showingPresetSelector = viewModel.showingPresetSelector,
            presetListCallback = presetListCallback,
            onCloseButtonClick = viewModel::onCloseButtonClick)
    }

    if (viewModel.showingUnsavedChangesWarning)
        viewModel.activePresetName?.let { unsavedPresetName ->
            UnsavedPresetChangesWarningDialog(
                unsavedPresetName = unsavedPresetName,
                onDismissRequest = viewModel::onUnsavedChangesWarningCancel,
                onConfirm = viewModel::onUnsavedChangesWarningConfirm)
        }

    if (showingSetStopTimerDialog)
        SetStopTimerDialog(
            onDismissRequest = {
                showingSetStopTimerDialog = false
            }, onConfirm = {
                onNewStopTimerRequest(it)
                showingSetStopTimerDialog = false
            })

    if (showingCancelStopTimerDialog)
        CancelStopTimerDialog(
            onDismissRequest = {
                showingCancelStopTimerDialog = false
            }, onConfirm = {
                onCancelStopTimerRequest()
                showingCancelStopTimerDialog = false
            })
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
            modifier = Modifier
                .minTouchTargetSize()
                .fillMaxWidth(),
            shape = RectangleShape,
        ) { Text(stringResource(R.string.cancel)) }

        HorizontalDivider()
        TextButton(
            onClick = { onConfirm(true) },
            modifier = Modifier
                .minTouchTargetSize()
                .fillMaxWidth(),
            shape = RectangleShape,
        ) { Text(stringResource(R.string.unsaved_preset_changes_warning_save_first_option)) }

        HorizontalDivider()
        TextButton(
            onClick = { onConfirm(false) },
            modifier = Modifier
                .minTouchTargetSize()
                .fillMaxWidth(),
            shape = MaterialTheme.shapes.medium.bottomShape(),
        ) { Text(stringResource(R.string.unsaved_preset_changes_warning_load_anyways_option)) }
    })

/**
 * A dialog to pick a [Duration] after which the user's sound mix will
 * automatically stop playing.
 *
 * @param modifier The [Modifier] to use for the dialog
 * @param onDismissRequest The callback that will be invoked when the user
 *     attempts to dismiss or cancel the dialog
 * @param onConfirm The callback that will be invoked when the user taps the ok
 *     button with a [Duration] that is valid (i.e. within the provided [bounds]
 */
@Composable fun SetStopTimerDialog(
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit,
    onConfirm: (Duration) -> Unit,
) = DurationPickerDialog(
    modifier,
    title = stringResource(R.string.play_pause_button_long_click_description),
    description = stringResource(R.string.set_stop_timer_dialog_description),
    bounds = Range(Duration.ZERO, Duration.ofHours(100).minusSeconds(1)),
    onDismissRequest, onConfirm)

/** A dialog that asks the user to confirm that they would
 * like to cancel the previously set auto stop timer. */
@Composable fun CancelStopTimerDialog(
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) = SoundAuraDialog(
    modifier = modifier,
    title = stringResource(R.string.cancel_stop_timer_dialog_title),
    text = stringResource(R.string.cancel_stop_timer_dialog_text),
    onDismissRequest = onDismissRequest,
    onConfirm = onConfirm)