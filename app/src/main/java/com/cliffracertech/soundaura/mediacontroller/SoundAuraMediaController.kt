/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.mediacontroller

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.MaterialTheme
import androidx.compose.material.SnackbarDuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cliffracertech.soundaura.R
import com.cliffracertech.soundaura.collectAsState
import com.cliffracertech.soundaura.edit
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
import com.cliffracertech.soundaura.ui.tweenDuration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

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

    private val activePresetName by activePresetState.name.collectAsState(null, scope)
    private val activePresetIsModified by activePresetState.isModified.collectAsState(false, scope)
    private fun activePresetCallback() = object: ActivePresetCallback {
        override fun getName() = activePresetName
        override fun getIsModified() = activePresetIsModified
        override fun onClick() { navigationState.showingPresetSelector = true }
    }

    private val playButtonLongClickHintShownKey =
        booleanPreferencesKey(PrefKeys.playButtonLongClickHintShown)
    private val playButtonLongClickHintShown by
    dataStore.preferenceState(playButtonLongClickHintShownKey, false, scope)
    private val activePlaylistsIsEmpty by playlistDao
        .getAtLeastOnePlaylistIsActive()
        .collectAsState(true, scope)
    private fun playButtonCallback() = object: PlayButtonCallback {
        override fun getIsPlaying() = false
        override fun onClick() {
            if (playButtonLongClickHintShown || activePlaylistsIsEmpty)
                return // We don't want to show the hint if there are no active tracks
            // because the PlayerService should show a message about there
            // being no active tracks
            val stringRes = StringResource(R.string.play_button_long_click_hint_text)
            messageHandler.postMessage(stringRes, SnackbarDuration.Long)
            dataStore.edit(playButtonLongClickHintShownKey, true, scope)
        }
        override fun onLongClick() {
            shownDialog = DialogType.SetAutoStopTimer(
                onDismissRequest = ::dismissDialog,
                onConfirmClick = { dismissDialog() })
        }
        override fun getClickLabelResId(isPlaying: Boolean) =
            if (isPlaying) R.string.pause_button_description
            else           R.string.play_button_description
        override val longClickLabelResId = R.string.play_pause_button_long_click_description
    }

    private val presetList by presetDao.getPresetList()
        .map { it.toImmutableList() }
        .collectAsState(null, scope)
    private fun presetListCallback() = object : PresetListCallback {
        override fun getList() = presetList
        override fun onRenameClick(preset: Preset) {
            nameValidator.reset(preset.name)
            shownDialog = DialogType.RenamePreset(
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
            shownDialog = DialogType.Confirmatory(
                title = StringResource(R.string.confirm_overwrite_preset_dialog_title),
                text = StringResource(R.string.confirm_overwrite_preset_dialog_message, preset.name),
                onDismissRequest = ::dismissDialog,
                onConfirmClick = {
                    overwritePreset(preset.name)
                    dismissDialog()
                })
        }
        override fun onDeleteClick(preset: Preset) {
            shownDialog = DialogType.Confirmatory(
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
                shownDialog = DialogType.PresetUnsavedChangesWarning(
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

    val callback = object: MediaControllerCallback {
        override val activePresetCallback = activePresetCallback()
        override val playButtonCallback = playButtonCallback()
        override val presetListCallback = presetListCallback()
        override fun getStopTime(): Instant? = null
        override fun onStopTimerClick() {
            shownDialog = DialogType.Confirmatory(
                onDismissRequest = ::dismissDialog,
                title = StringResource(R.string.cancel_stop_timer_dialog_title),
                text = StringResource(R.string.cancel_stop_timer_dialog_text),
                onConfirmClick = { dismissDialog() })
        }
        override fun getShowingPresetSelector() = navigationState.showingPresetSelector
        override fun onCloseButtonClick() = this@MediaControllerViewModel.onCloseButtonClick()
    }

    fun onCloseButtonClick() {
        navigationState.showingPresetSelector = false
    }

    private val nameValidator = PresetNameValidator(presetDao, scope)
    var shownDialog by mutableStateOf<DialogType?>(null)
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
    val hasStopTime by remember { derivedStateOf {
        viewModel.callback.getStopTime() != null
    }}
    val transformOrigin = rememberClippedBrushBoxTransformOrigin(
        alignment, padding, dpSize = sizes.collapsedSize(hasStopTime))

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
            callback = viewModel.callback)
    }
    DialogShower(viewModel.shownDialog)
}