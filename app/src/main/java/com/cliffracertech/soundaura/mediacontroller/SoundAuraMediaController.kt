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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cliffracertech.soundaura.R
import com.cliffracertech.soundaura.collectAsState
import com.cliffracertech.soundaura.dialog.RenameDialog
import com.cliffracertech.soundaura.dialog.SoundAuraDialog
import com.cliffracertech.soundaura.edit
import com.cliffracertech.soundaura.model.ActivePresetState
import com.cliffracertech.soundaura.model.MessageHandler
import com.cliffracertech.soundaura.model.NavigationState
import com.cliffracertech.soundaura.model.StringResource
import com.cliffracertech.soundaura.model.Validator
import com.cliffracertech.soundaura.model.database.PlaylistDao
import com.cliffracertech.soundaura.model.database.Preset
import com.cliffracertech.soundaura.model.database.PresetDao
import com.cliffracertech.soundaura.model.database.PresetNameValidator
import com.cliffracertech.soundaura.preferenceState
import com.cliffracertech.soundaura.settings.PrefKeys
import com.cliffracertech.soundaura.ui.HorizontalDivider
import com.cliffracertech.soundaura.ui.bottomShape
import com.cliffracertech.soundaura.ui.minTouchTargetSize
import com.cliffracertech.soundaura.ui.tweenDuration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/**
 * A sealed class whose subclasses the various dialogs that a [MediaController]
 * would be expected to show.
 *
 * @param onDismissRequest The callback that should be invoked when the user
 *     indicates via a back button/gesture, a tap outside the dialog's bounds,
 *     or a cancel button click that they would like to dismiss the dialog
 */
sealed class MediaControllerDialog(
    val onDismissRequest: () -> Unit,
) {
    /**
     * A [Preset] rename dialog
     *
     * @param target The [Preset] that is being renamed
     * @param onConfirmClick The callback that should be invoked if the dialog's
     *     confirm button is clicked
     * @param newNameProvider A lambda that returns the proposed new name for the
     *     [target] when invoked
     * @param messageProvider A lambda that returns a nullable Validator.Message
     *     that describes any problems with the currently proposed new name for
     *     the [target], or null if the name is valid
     * @param onNameChange The callback that should be invoked when the user
     *     attempts to change the proposed new name for the [target]
     */
    class RenamePreset(
        onDismissRequest: () -> Unit,
        val target: Preset,
        val onConfirmClick: () -> Unit,
        val newNameProvider: () -> String,
        val messageProvider: () -> Validator.Message?,
        val onNameChange: (String) -> Unit,
    ): MediaControllerDialog(onDismissRequest)

    /**
     * A dialog that presents choices regarding the unsaved changes
     * for a [Preset] that is about to switched away from.
     *
     * @param target The active [Preset] that has unsaved changes
     * @param onConfirmClick The callback that should be invoked if the dialog's
     *     confirm button is clicked along with whether or not the user requested
     *     for the active preset to be saved first provided
     */
    class PresetUnsavedChangesWarning(
        onDismissRequest: () -> Unit,
        val target: Preset,
        val onConfirmClick: (saveFirst: Boolean) -> Unit,
    ): MediaControllerDialog(onDismissRequest)

    /** A dialog that allows the user to set an auto stop timer. */
    class SetAutoStopTimer(onDismissRequest: () -> Unit) :
        MediaControllerDialog(onDismissRequest)

    /**
     * A confirmatory dialog with cancel and confirm buttons
     *
     * @param title The [StringResource] that, when resolved, should be used
     *     as the dialog's title
     * @param text The [StringResource] that, when resolved, should be
     *     used as the dialog's body text
     * @param onConfirmClick The callback that should be invoked when the
     *     dialog's confirm button is clicked
     */
    class Confirmatory(
        onDismissRequest: () -> Unit,
        val title: StringResource,
        val text: StringResource,
        val onConfirmClick: () -> Unit,
    ): MediaControllerDialog(onDismissRequest)
}

@HiltViewModel class MediaControllerViewModel(
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

    val visible get() = !navigationState.showingAppSettings
    val showingPresetSelector get() = navigationState.showingPresetSelector

    private val activePresetName by activePresetState.name.collectAsState(null, scope)
    private val activePresetIsModified by activePresetState.isModified.collectAsState(false, scope)
    val activePresetCallback = object: ActivePresetCallback {
        override fun getName() = activePresetName
        override fun getIsModified() = activePresetIsModified
        override fun onClick() { navigationState.showingPresetSelector = true }
    }

    private val presetList by presetDao.getPresetList()
        .map { it.toImmutableList() }
        .collectAsState(null, scope)
    val presetListCallback = object : PresetListCallback {
        override fun getList() = presetList
        override fun onRenameClick(preset: Preset) {
            nameValidator.reset(preset.name)
            shownDialog = MediaControllerDialog.RenamePreset(
                target = preset,
                newNameProvider = nameValidator::value,
                onNameChange = { nameValidator.value = it },
                messageProvider = nameValidator::message,
                onDismissRequest = ::dismissDialog,
                onConfirmClick = {
                    scope.launch {
                        val newName = nameValidator.validate() ?: return@launch
                        presetDao.renamePreset(preset.name, newName)
                    }
                    dismissDialog()
                })
        }
        override fun onOverwriteClick(preset: Preset) {
            shownDialog = MediaControllerDialog.Confirmatory(
                title = StringResource(R.string.confirm_overwrite_preset_dialog_title),
                text = StringResource(R.string.confirm_overwrite_preset_dialog_message, preset.name),
                onDismissRequest = ::dismissDialog,
                onConfirmClick = {
                    overwritePreset(preset.name)
                    dismissDialog()
                })
        }
        override fun onDeleteClick(preset: Preset) {
            shownDialog = MediaControllerDialog.Confirmatory(
                title = StringResource(R.string.confirm_delete_preset_title, preset.name),
                text = StringResource(R.string.confirm_delete_preset_message),
                onDismissRequest = ::dismissDialog,
                onConfirmClick = {
                    scope.launch {
                        if (preset.name == activePresetName)
                            activePresetState.clear()
                        presetDao.deletePreset(preset.name)
                    }
                    dismissDialog()
                })
        }
        override fun onClick(preset: Preset) { when {
            activePresetIsModified -> {
                shownDialog = MediaControllerDialog.PresetUnsavedChangesWarning(
                    target = preset,
                    onDismissRequest = ::dismissDialog,
                    onConfirmClick = { saveFirst ->
                        if (saveFirst)
                            activePresetName?.let(::overwritePreset)
                        loadPreset(preset)
                        dismissDialog()
                    })
            } preset.name == activePresetName ->
            // This skips a pointless loading of the unmodified active preset
            onCloseButtonClick()
            else -> loadPreset(preset)
        }}
    }

    private val playButtonLongClickHintShownKey =
        booleanPreferencesKey(PrefKeys.playButtonLongClickHintShown)
    private val playButtonLongClickHintShown by
        dataStore.preferenceState(playButtonLongClickHintShownKey, false, scope)
    private val activePlaylistsIsEmpty by playlistDao
        .getAtLeastOnePlaylistIsActive()
        .collectAsState(true, scope)
    val playButtonCallback = object: PlayButtonCallback {
        override fun getIsPlaying() = false
        override fun onClick() {
            if (playButtonLongClickHintShown || activePlaylistsIsEmpty)
                return // We don't want to show the hint if there are no active tracks
            // because the PlayerService should show a message about there
            // being no active tracks
            messageHandler.postMessage(
                StringResource(R.string.play_button_long_click_hint_text),
                SnackbarDuration.Long)
            dataStore.edit(playButtonLongClickHintShownKey, true, scope)
        }
        override fun onLongClick() {
            shownDialog = MediaControllerDialog.SetAutoStopTimer(::dismissDialog)
        }
        override fun getClickLabelResId(isPlaying: Boolean) =
            if (isPlaying) R.string.pause_button_description
            else           R.string.play_button_description
        override val longClickLabelResId = R.string.play_pause_button_long_click_description
    }

    fun onAutoStopTimerClick() {
        shownDialog = MediaControllerDialog.Confirmatory(
            onDismissRequest = ::dismissDialog,
            title = StringResource(R.string.cancel_stop_timer_dialog_title),
            text = StringResource(R.string.cancel_stop_timer_dialog_text),
            onConfirmClick = {})
    }

    fun onCloseButtonClick() {
        navigationState.showingPresetSelector = false
    }

    private val nameValidator = PresetNameValidator(presetDao, scope)
    var shownDialog by mutableStateOf<MediaControllerDialog?>(null)
    private fun dismissDialog() { shownDialog = null }

    private fun overwritePreset(presetName: String) {
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

    // TODO: Figure out why UI stutters when selecting new preset
    private fun loadPreset(preset: Preset) {
        scope.launch {
            activePresetState.setName(preset.name)
            presetDao.loadPreset(preset.name)
            onCloseButtonClick()
        }
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

    val startColor = MaterialTheme.colors.primaryVariant
    val endColor = MaterialTheme.colors.secondaryVariant
    val backgroundBrush = remember(startColor, endColor) {
        Brush.horizontalGradient(colors = listOf(startColor, endColor))
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
        visible = viewModel.visible,
        enter = fadeIn(enterSpec) + scaleIn(enterSpec, 0.8f, transformOrigin),
        exit = fadeOut(exitSpec) + scaleOut(exitSpec, 0.8f, transformOrigin)
    ) {
        MediaController(
            modifier = modifier,
            sizes = sizes,
            backgroundBrush = backgroundBrush,
            alignment = alignment,
            padding = padding,
            activePresetCallback = viewModel.activePresetCallback,
            playButtonCallback = viewModel.playButtonCallback,
            stopTimeProvider = stopTimeProvider,
            onStopTimerClick = viewModel::onAutoStopTimerClick,
            showingPresetSelector = viewModel.showingPresetSelector,
            presetListCallback = viewModel.presetListCallback,
            onCloseButtonClick = viewModel::onCloseButtonClick)
    }

    when (val shownDialog = viewModel.shownDialog) {
        null -> {}
        is MediaControllerDialog.Confirmatory ->
            SoundAuraDialog(
                title = shownDialog.title.resolve(LocalContext.current),
                text = shownDialog.text.resolve(LocalContext.current),
                onDismissRequest = shownDialog.onDismissRequest,
                onConfirm = shownDialog.onConfirmClick)
        is MediaControllerDialog.RenamePreset ->
            RenameDialog(
                title = stringResource(R.string.default_rename_dialog_title),
                newNameProvider = shownDialog.newNameProvider,
                onNewNameChange = shownDialog.onNameChange,
                errorMessageProvider = shownDialog.messageProvider,
                onDismissRequest = shownDialog.onDismissRequest,
                onConfirmClick = shownDialog.onConfirmClick)
        is MediaControllerDialog.PresetUnsavedChangesWarning ->
            UnsavedPresetChangesWarningDialog(
                unsavedPresetName = shownDialog.target.name,
                onDismissRequest = shownDialog.onDismissRequest,
                onConfirm = shownDialog.onConfirmClick)
        is MediaControllerDialog.SetAutoStopTimer ->
            SetStopTimerDialog(
                onDismissRequest = shownDialog.onDismissRequest,
                onConfirm = {
                    onNewStopTimerRequest(it)
                    shownDialog.onDismissRequest()
                })
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