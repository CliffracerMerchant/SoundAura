/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * An abstract name validator.
 *
 * NameValidator cna be used to validates names for objects. The currently
 * proposed name can be queried through the [String]? property [proposedName],
 * and set using [setProposedName]. The abstract suspend function [validateName]
 * must be overridden in subclasses to return a [StringResource] that, when
 * resolved, becomes an error message explaining why the current value of
 * [proposedName] is not a valid name, or null if the name is valid. The
 * [Flow]`<StringResource?>` property [errorMessage] can be collected to obtain
 * the error message given the current value of [proposedName].
 *
 * When naming is finished, [onNameConfirm] should be called with the proposed
 * name. [onNameConfirm] will check the provided name one last time and return
 * whether or not the name is valid, and also clears the proposed name if it is
 * valid so that the validator can be reused. This method covers the edge case
 * of an invalid name being input, and then confirmed before the suspend
 * functions underlying [errorMessage] have a chance to update its value to a
 * non-null [StringResource] error message, and allows for the possibility of
 * an always initially null [errorMessage] if the initial text of the, e.g.,
 * rename text field is not passed via [setProposedName]. This might be desired
 * to prevent an initially invalid name from immediately showing an error
 * message before the user has had a chance to change it.
 */
abstract class NameValidator {
    var proposedName = MutableStateFlow<String?>(null)
        private set

    fun setProposedName(value: String) {
        proposedName.value = value
    }

    fun clearProposedName() { proposedName.value = null }

    abstract suspend fun validateName(proposedName: String?): StringResource?

    val errorMessage = proposedName.map(::validateName)

    suspend fun onNameConfirm(newName: String): Boolean {
        proposedName.value = newName
        val message = validateName(newName)
        if (message == null)
            clearProposedName()
        return message == null
    }
}

/**
 * Show a dialog to rename an object.
 *
 * @param initialName The name that will be displayed in the text field initially. This
 *     value will be used when [proposedNameProvider] returns null.
 * @param proposedNameProvider A function that returns the currently proposed name when invoked
 * @param onProposedNameChange The callback that will be invoked when the user attempts
 *     to change the proposed name to the callback's [String] parameter
 * @param errorMessageProvider A function that returns the error message that should
 *     be displayed given the most recently proposed name, or null if the name is valid
 * @param onDismissRequest The callback that will be invoked when the user attempts to dismiss the dialog
 * @param onConfirm The callback that will be invoked when the user clicks the ok button
 */
@Composable fun RenameDialog(
    initialName: String = "",
    proposedNameProvider: () -> String?,
    onProposedNameChange: (String) -> Unit,
    errorMessageProvider: () -> String?,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    val errorMessage = errorMessageProvider()
    val text = proposedNameProvider() ?: initialName
    SoundAuraDialog(
        modifier = Modifier.restrictWidthAccordingToSizeClass(),
        useDefaultWidth = false,
        title = stringResource(R.string.create_new_preset_dialog_title),
        onDismissRequest = onDismissRequest,
        confirmButtonEnabled = errorMessage == null,
        onConfirm = onConfirm
    ) {
        TextField(
            onValueChange = onProposedNameChange,
            value = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            isError = errorMessage != null,
            singleLine = true,
            textStyle = MaterialTheme.typography.body1)

        var previousErrorMessage by remember { mutableStateOf("") }
        AnimatedVisibility(errorMessage != null) {
            Row(Modifier.align(Alignment.CenterHorizontally)
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colors.error)
                AnimatedContent(errorMessage ?: previousErrorMessage) {
                    Text(it, Modifier.weight(1f), MaterialTheme.colors.error)
                }
                errorMessage?.let { previousErrorMessage = it }
            }
        }
    }
}

/** A collection of callbacks related to the active renaming of an existing [Preset]. */
interface RenamePresetCallback {
    val targetProvider: () -> Preset?
    val proposedNameProvider: () -> String?
    val errorMessageProvider: () -> String?
    fun onRenameStart(preset: Preset) {}
    fun onRenameCancel()
    fun onProposedNameChange(newName: String)
    fun onRenameConfirm(preset: Preset)
}