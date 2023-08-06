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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** A collection of state and callbacks related to the active naming/renaming of an object. */
interface NamingState {
    /** The proposed name */
    val name: String

    /** A [Validator.Message] concerning the current value
     * of [name], or null if no message is required */
    val message: Validator.Message?

    /** The method that is invoked in response to a
     * desired change in the proposed name to [newName] */
    fun onNameChange(newName: String)

    /** The method that will be invoked when the naming is canceled
     * (e.g. through a cancel button click or a back button/gesture use). */
    fun cancel()

    /** The method that will be invoked when naming is finished. */
    fun finalize()
}

/**
 * A [NamingState] implementation that uses a [Validator]`<String>` to check
 * the current value of [name] and to provide messages regarding the value.
 *
 * @param validator The [Validator] instance to use
 * @param coroutineScope A [coroutineScope] to run async methods on
 * @param onNameValidated The callback that will be invoked
 *     when the current value of [name] is valid and [finalize]
 *     is called. The [String] parameter is the validated name.
 */
class ValidatedNamingState(
    private val validator: Validator<String>,
    private val coroutineScope: CoroutineScope,
    private val onNameValidated: suspend (String) -> Unit,
    private val onCancel: () -> Unit
): NamingState {
    override val name by validator::value
    override val message by validator::message

    override fun onNameChange(newName: String) {
        validator.value = newName
    }

    override fun cancel() = onCancel()

    /** Validate the current value of [name]. If the value is valid,
     * the constructor parameter onNameValidated will be called with
     * the validated value. */
    override fun finalize() { coroutineScope.launch {
        validator.validate()?.let { onNameValidated(it) }
    }}
}

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
    AnimatedVisibility(
        visible = message != null,
        label = "Validator message appearance/disappearance",
        modifier = modifier
    ) {
        Crossfade(
            targetState = message ?: lastMessage,
            label = "Validator message change crossfade"
        ) { ValidatorMessageView(it) }
    }
    message?.let { lastMessage = it }
}

/**
 * Show a dialog to name or rename an object. The 'Confirm' button will
 * call the [state]'s [NamingState.finalize] method, while the 'Cancel'
 * button will call the [state]'s [NamingState.cancel] method.
 *
 * @param state A [NamingState] instance
 * @param modifier The [Modifier] to use for the root layout
 * @param title The title of the dialog
 */
@Composable fun NamingDialog(
    state: NamingState,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.default_rename_dialog_title),
) = SoundAuraDialog(
    modifier = modifier.restrictWidthAccordingToSizeClass(),
    useDefaultWidth = false,
    title = title,
    onDismissRequest = state::cancel,
    confirmButtonEnabled = state.message !is Validator.Message.Error,
    onConfirm = state::finalize,
) {
    TextField(
        onValueChange = state::onNameChange,
        value = state.name,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        isError = state.message is Validator.Message.Error,
        singleLine = true,
        textStyle = MaterialTheme.typography.body1)

    AnimatedValidatorMessage(
        message = state.message,
        modifier = Modifier.padding(horizontal = 16.dp))
}