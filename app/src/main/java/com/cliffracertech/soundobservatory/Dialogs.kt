/* This file is part of SoundObservatory, which is released under the Apache License 2.0.
 * See license.md in the project's root directory or use an internet search engine to see the full license. */
package com.cliffracertech.soundobservatory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable fun CancelOkButtonRow(
    onCancelClick: () -> Unit,
    onOkClick: () -> Unit
) = Row {
    Spacer(Modifier.weight(1f))
    TextButton(onCancelClick, Modifier.heightIn(48.dp, Dp.Infinity)) {
        Text(text = stringResource(android.R.string.cancel).uppercase(),
             style = MaterialTheme.typography.button,
             color = MaterialTheme.colors.secondary)
    }
    TextButton(onOkClick, Modifier.heightIn(48.dp, Dp.Infinity)) {
        Text(stringResource(android.R.string.ok),
             style = MaterialTheme.typography.button,
             color = MaterialTheme.colors.secondary)
    }
}

@ExperimentalComposeUiApi
@Composable fun RenameDialog(
    itemName: String,
    onDismissRequest: () -> Unit,
    onConfirmRequest: (String) -> Unit
) = Dialog(onDismissRequest = onDismissRequest) {
    Column(Modifier
        .background(MaterialTheme.colors.surface,
                    MaterialTheme.shapes.medium)
        .padding(16.dp, 16.dp, 16.dp, 0.dp)
    ) {
        var currentName by remember { mutableStateOf(itemName) }
        val focusManager = LocalFocusManager.current
        val focusRequester = FocusRequester()
        val keyboardController = LocalSoftwareKeyboardController.current

        Text(stringResource(R.string.rename_dialog_title, itemName),
             style = MaterialTheme.typography.body1)
        Spacer(Modifier.height(6.dp))
        TextField(
            value = currentName,
            onValueChange = { currentName = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.body1,
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            modifier = Modifier
                .fillMaxWidth(1f)
                .focusRequester(focusRequester)
                .onFocusChanged { if (it.isFocused) keyboardController?.show() })

        DisposableEffect(Unit) {
            focusRequester.requestFocus()
            onDispose { }
        }
        CancelOkButtonRow(onCancelClick = onDismissRequest,
                          onOkClick = { onConfirmRequest(currentName)
                                        onDismissRequest() })
    }
}

@ExperimentalComposeUiApi
@Preview @Composable
fun RenameDialogPreview() = RenameDialog("Renameable thing", { }, { })

@Composable
fun ConfirmDeleteDialog(
    itemName: String,
    onDismissRequest: () -> Unit,
    onConfirmRequest: () -> Unit
) = Dialog(onDismissRequest = onDismissRequest) {
    Column(Modifier
        .background(MaterialTheme.colors.surface,
                    MaterialTheme.shapes.medium)
        .padding(16.dp, 16.dp, 16.dp, 0.dp)
    ) {
        Text(stringResource(R.string.confirm_delete_message, itemName),
             style = MaterialTheme.typography.body1)
        CancelOkButtonRow(onCancelClick = onDismissRequest,
                          onOkClick = { onConfirmRequest()
                                        onDismissRequest() })
    }
}

@Preview @Composable
fun ConfirmDeleteDialogPreview() = ConfirmDeleteDialog("Deleteable thing", { }, { })


