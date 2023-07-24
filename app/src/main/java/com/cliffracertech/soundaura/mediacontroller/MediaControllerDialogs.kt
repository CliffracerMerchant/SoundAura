/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.mediacontroller

import android.util.Range
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cliffracertech.soundaura.R
import com.cliffracertech.soundaura.dialog.NamingState
import com.cliffracertech.soundaura.dialog.RenameDialog
import com.cliffracertech.soundaura.dialog.SoundAuraDialog
import com.cliffracertech.soundaura.dialog.ValidatedNamingState
import com.cliffracertech.soundaura.model.StringResource
import com.cliffracertech.soundaura.model.Validator
import com.cliffracertech.soundaura.model.database.Preset
import com.cliffracertech.soundaura.model.database.PresetNameValidator
import com.cliffracertech.soundaura.ui.HorizontalDivider
import com.cliffracertech.soundaura.ui.TextButton
import com.cliffracertech.soundaura.ui.bottomShape
import com.cliffracertech.soundaura.ui.minTouchTargetSize
import kotlinx.coroutines.CoroutineScope
import java.time.Duration

/**
 * A sealed class whose subclasses represent the various dialogs that a
 * [MediaController] would be expected to show.
 *
 * @param onDismissRequest The callback that should be invoked when the user
 *     indicates via a back button/gesture, a tap outside the dialog's bounds,
 *     or a cancel button click that they would like to dismiss the dialog
 */
sealed class DialogType(
    val onDismissRequest: () -> Unit,
) {
    /**
     * A [Preset] rename dialog. The property [name] describes the proposed
     * new name. The [message] property describes the current [Validator.Message]
     * regarding the current value of [name] (or null if no message is
     * required). The method [onNameChange] should be invoked when the new
     * name needs to be changed.
     *
     * @param target The [Preset] that is being renamed
     * @param coroutineScope A [CoroutineScope] to run background work on
     * @param validator A [PresetNameValidator] instance to use for validating
     *     the entered name
     * @param onDismissRequest The callback that should be invoked if the
     *     dialog's cancel button is clicked or the back button/gesture
     *     is used
     * @param onNameValidated The callback that will be invoked if the
     *     dialog's confirm button is clicked and the new name is valid.
     */
    class RenamePreset(
        val target: Preset,
        coroutineScope: CoroutineScope,
        validator: PresetNameValidator,
        onDismissRequest: () -> Unit,
        onNameValidated: suspend (String) -> Unit,
    ): DialogType(onDismissRequest),
       NamingState by ValidatedNamingState(
           validator, coroutineScope, onNameValidated, onDismissRequest)

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
    ): DialogType(onDismissRequest)

    /** A dialog that allows the user to set an auto stop timer. */
    class SetAutoStopTimer(
        onDismissRequest: () -> Unit,
        val onConfirmClick: (stopTimerDuration: Duration) -> Unit,
    ) : DialogType(onDismissRequest)

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
    ): DialogType(onDismissRequest)
}

/** Show a [MediaController] dialog depending on the value
 * of [shownDialog], or nothing if [shownDialog] is null */
@Composable fun DialogShower(
    shownDialog: DialogType?,
    modifier: Modifier = Modifier,
) = when (shownDialog) {
    null -> {}
    is DialogType.Confirmatory ->
        SoundAuraDialog(
            modifier = modifier,
            title = shownDialog.title.resolve(LocalContext.current),
            text = shownDialog.text.resolve(LocalContext.current),
            onDismissRequest = shownDialog.onDismissRequest,
            onConfirm = shownDialog.onConfirmClick)
    is DialogType.RenamePreset ->
        RenameDialog(state = shownDialog, modifier = modifier)
    is DialogType.PresetUnsavedChangesWarning ->
        UnsavedPresetChangesWarningDialog(
            modifier = modifier,
            unsavedPresetName = shownDialog.target.name,
            onDismissRequest = shownDialog.onDismissRequest,
            onConfirm = shownDialog.onConfirmClick)
    is DialogType.SetAutoStopTimer ->
        DurationPickerDialog(
            modifier = modifier,
            title = stringResource(R.string.play_pause_button_long_click_description),
            description = stringResource(R.string.set_stop_timer_dialog_description),
            bounds = Range(Duration.ZERO, Duration.ofHours(100).minusSeconds(1)),
            onDismissRequest = shownDialog.onDismissRequest,
            onConfirm = shownDialog.onConfirmClick)
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
    modifier: Modifier = Modifier,
    onConfirm: (saveFirst: Boolean) -> Unit,
) = SoundAuraDialog(
    modifier = modifier,
    title = stringResource(R.string.unsaved_preset_changes_warning_title),
    text = stringResource(R.string.unsaved_preset_changes_warning_message, unsavedPresetName),
    onDismissRequest = onDismissRequest,
    buttons = {
        HorizontalDivider(Modifier.padding(top = 12.dp))
        TextButton(
            modifier = Modifier
                .minTouchTargetSize()
                .fillMaxWidth(),
            shape = RectangleShape,
            textResId = R.string.cancel,
            onClick = onDismissRequest)

        HorizontalDivider()
        TextButton(
            modifier = Modifier
                .minTouchTargetSize()
                .fillMaxWidth(),
            shape = RectangleShape,
            textResId = R.string.unsaved_preset_changes_warning_save_first_option,
            onClick = { onConfirm(true) })

        HorizontalDivider()
        TextButton(
            modifier = Modifier
                .minTouchTargetSize()
                .fillMaxWidth(),
            shape = MaterialTheme.shapes.medium.bottomShape(),
            textResId = R.string.unsaved_preset_changes_warning_load_anyways_option,
            onClick = { onConfirm(false) })
    })