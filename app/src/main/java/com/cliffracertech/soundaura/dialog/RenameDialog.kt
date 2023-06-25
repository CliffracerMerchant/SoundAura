/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.dialog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cliffracertech.soundaura.R
import com.cliffracertech.soundaura.model.StringResource
import com.cliffracertech.soundaura.model.Validator
import com.cliffracertech.soundaura.restrictWidthAccordingToSizeClass

/** Create a view that displays an icon appropriate for the
 * type of [Validator.Message] alongside its text. */
@Composable fun ValidatorMessageView(
    message: Validator.Message,
    modifier: Modifier = Modifier
) = Row(
    modifier = modifier.fillMaxWidth().height(48.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    val vector = when {
        message.isInformational -> Icons.Default.Info
        message.isWarning ->       Icons.Default.Warning
        else ->/*message.isError*/ Icons.Default.Error
    }
    val tint = when {
        message.isInformational -> Color.Blue
        message.isWarning ->       Color.Yellow
        else ->/*message.isError*/ MaterialTheme.colors.error
    }
    Icon(vector, null, tint = tint)
    Spacer(Modifier.width(4.dp))
    Text(message.stringResource.resolve(LocalContext.current))
}

/** A display of a single nullable [Validator.Message], with appearance and/or
 * disappearance animations for when the message changes or becomes null. */
@Composable fun ColumnScope.AnimatedValidatorMessage(
    message: Validator.Message?,
    modifier: Modifier = Modifier
) {
    var lastMessage: Validator.Message = remember {
        Validator.Message.Error(StringResource(""))
    }
    AnimatedVisibility(message != null, modifier) {
        Crossfade(message ?: lastMessage) {
            ValidatorMessageView(it)
        }
    }
    message?.let { lastMessage = it }
}

/**
 * Show a dialog to rename an object.
 *
 * @param title The title of the dialog
 * @param newNameProvider A method that returns the currently proposed name when invoked
 * @param onNewNameChange The callback that will be invoked when the user attempts
 *     to change the proposed name to the callback's [String] parameter
 * @param errorMessageProvider A function that returns the error message that should
 *     be displayed given the most recently proposed name, or null if the name is valid
 * @param onDismissRequest The callback that will be invoked when the
 *     user attempts to dismiss the dialog
 * @param onConfirmClick The callback that will be invoked when the user clicks the ok button
 */
@Composable fun RenameDialog(
    title: String = stringResource(R.string.default_rename_dialog_title),
    newNameProvider: () -> String,
    onNewNameChange: (String) -> Unit,
    errorMessageProvider: () -> Validator.Message?,
    onDismissRequest: () -> Unit,
    onConfirmClick: () -> Unit,
) {
    val errorMessage = errorMessageProvider()
    SoundAuraDialog(
        modifier = Modifier.restrictWidthAccordingToSizeClass(),
        useDefaultWidth = false,
        title = title,
        onDismissRequest = onDismissRequest,
        confirmButtonEnabled = errorMessage == null,
        onConfirm = onConfirmClick
    ) {
        TextField(
            onValueChange = onNewNameChange,
            value = newNameProvider(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            isError = errorMessage != null,
            singleLine = true,
            textStyle = MaterialTheme.typography.body1)

        AnimatedValidatorMessage(
            message = errorMessageProvider(),
            modifier = Modifier.padding(horizontal = 16.dp))
    }
}