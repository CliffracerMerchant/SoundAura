/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.documentfile.provider.DocumentFile

@Composable fun CancelOkButtonRow(
    onCancelClick: () -> Unit,
    okButtonEnabled: Boolean = true,
    onOkClick: () -> Unit,
) = Row {
    Spacer(Modifier.weight(1f))
    TextButton(onCancelClick, Modifier.heightIn(48.dp, Dp.Infinity)) {
        Text(text = stringResource(android.R.string.cancel).uppercase(),
             style = MaterialTheme.typography.button,
             color = MaterialTheme.colors.secondary)
    }
    TextButton(
        onClick = onOkClick,
        modifier = Modifier.heightIn(48.dp, Dp.Infinity),
        enabled = okButtonEnabled
    ) {
        Text(stringResource(android.R.string.ok),
             style = MaterialTheme.typography.button,
             color = MaterialTheme.colors.secondary)
    }
}

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
        val focusRequester = FocusRequester()
        val keyboardController = LocalSoftwareKeyboardController.current

        Text(stringResource(R.string.rename_dialog_title, itemName),
             modifier = Modifier.padding(8.dp, 0.dp, 0.dp, 0.dp),
             style = MaterialTheme.typography.body1)
        Spacer(Modifier.height(6.dp))
        TextField(
            value = currentName,
            onValueChange = { currentName = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.body1,
            modifier = Modifier
                .fillMaxWidth(1f)
                .focusRequester(focusRequester)
                .onFocusChanged { if (it.isFocused) keyboardController?.show() })
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        CancelOkButtonRow(onCancelClick = onDismissRequest,
                          onOkClick = { onConfirmRequest(currentName)
                                        onDismissRequest() })
    }
}

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


fun Uri.getDisplayName(context: Context) =
    DocumentFile.fromSingleUri(context, this)?.name?.substringBeforeLast('.', "")

class OpenPersistableDocument : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>) =
        super.createIntent(context, input).setFlags(FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
}

@Composable fun AddTrackFromLocalFileDialog(
    onDismissRequest: () -> Unit,
    onConfirmRequest: (Track) -> Unit
) = Dialog(onDismissRequest) {
    var chosenUri by remember { mutableStateOf<Uri?>(null) }
    var trackName by remember { mutableStateOf("")}
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(OpenPersistableDocument()) {
        chosenUri = it
        trackName = it?.getDisplayName(context) ?: ""
    }

    if ((chosenUri == null))
        LaunchedEffect(true) { launcher.launch(arrayOf("audio/*")) }

    Column(Modifier
        .alpha(if (chosenUri == null) 0f else 1f)
        .background(MaterialTheme.colors.surface,
                    MaterialTheme.shapes.medium)
        .padding(16.dp, 16.dp, 16.dp, 0.dp)
    ) {
        Text(stringResource(R.string.track_name_description),
             modifier = Modifier.padding(8.dp, 0.dp, 0.dp, 0.dp),
             style = MaterialTheme.typography.body1)
        Spacer(Modifier.height(6.dp))
        TextField(
            value = trackName,
            onValueChange = { trackName = it },
            textStyle = MaterialTheme.typography.body1,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(1f))
        CancelOkButtonRow(
            onCancelClick = onDismissRequest,
            okButtonEnabled = chosenUri != null,
            onOkClick = {
                val uri = chosenUri ?: return@CancelOkButtonRow
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                onConfirmRequest(Track(uriString = uri.toString(), name = trackName))
            })
    }
}
