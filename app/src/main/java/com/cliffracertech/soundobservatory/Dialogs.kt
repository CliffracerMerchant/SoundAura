/* This file is part of SoundObservatory, which is released under the Apache License 2.0.
 * See license.md in the project's root directory or use an internet search engine to see the full license. */
package com.cliffracertech.soundobservatory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextField
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

        Text("Rename $itemName", style = MaterialTheme.typography.body1)
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
        Row {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismissRequest,
                       modifier = Modifier.heightIn(48.dp, Dp.Infinity)) {
                Text(text = stringResource(android.R.string.cancel).uppercase(),
                     style = MaterialTheme.typography.button,
                     color = MaterialTheme.colors.secondary)
            }
            TextButton(onClick = { onConfirmRequest(currentName); onDismissRequest() },
                       modifier = Modifier.heightIn(48.dp, Dp.Infinity)) {
                Text(stringResource(android.R.string.ok),
                     style = MaterialTheme.typography.button,
                     color = MaterialTheme.colors.secondary)
            }
        }
    }
}

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
        Text("Are you sure you want to delete $itemName? This cannot be undone.",
             style = MaterialTheme.typography.body1)
        Row {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismissRequest,
                       modifier = Modifier.heightIn(48.dp, Dp.Infinity)) {
                Text(stringResource(android.R.string.cancel).uppercase(),
                     style = MaterialTheme.typography.button,
                     color = MaterialTheme.colors.secondary)
            }
            TextButton(onClick = { onConfirmRequest(); onDismissRequest() },
                       modifier = Modifier.heightIn(48.dp, Dp.Infinity)) {
                Text(stringResource(android.R.string.ok),
                     style = MaterialTheme.typography.button,
                     color = MaterialTheme.colors.secondary)
            }
        }
    }
}

@ExperimentalComposeUiApi
@Preview @Composable
fun RenameDialogPreview() = RenameDialog("Renameable thing", { }, { })

@Preview @Composable
fun ConfirmDeleteDialogPreview() = ConfirmDeleteDialog("Deleteable thing", { }, { })


