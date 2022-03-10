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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
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
        Text(text = stringResource(android.R.string.cancel),
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
             color = if (okButtonEnabled)
                         MaterialTheme.colors.secondary
                     else Color.Unspecified)
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
                          okButtonEnabled = currentName.isNotBlank(),
                          onOkClick = { onConfirmRequest(currentName)
                                        onDismissRequest() })
    }
}

@Composable fun ConfirmDeleteDialog(
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

fun Uri.getDisplayName(context: Context) =
    DocumentFile.fromSingleUri(context, this)?.name?.substringBeforeLast('.', "")

class OpenPersistableDocument : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>) =
        super.createIntent(context, input).setFlags(FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
}

@Composable fun AddTrackFromLocalFileDialog(
    onDismissRequest: () -> Unit,
    onConfirmRequest: (Track) -> Unit
) {
    var chosenUri by remember { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(OpenPersistableDocument()) {
        chosenUri = it
        if (it == null)
            onDismissRequest()
    }

    if ((chosenUri == null))
        LaunchedEffect(true) { launcher.launch(arrayOf("audio/*")) }
    else Dialog(onDismissRequest) {
        Column(Modifier
            .background(MaterialTheme.colors.surface,
                        MaterialTheme.shapes.medium)
            .padding(16.dp, 16.dp, 16.dp, 0.dp)
        ) {
            Text(text = stringResource(R.string.track_name_description),
                 modifier = Modifier.padding(8.dp, 0.dp, 0.dp, 0.dp),
                 style = MaterialTheme.typography.body1)
            Spacer(Modifier.height(6.dp))

            val context = LocalContext.current
            var trackName by remember {
                mutableStateOf(chosenUri?.getDisplayName(context) ?: "")
            }
            TextField(
                value = trackName,
                onValueChange = { trackName = it },
                textStyle = MaterialTheme.typography.body1,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(1f))
            CancelOkButtonRow(
                onCancelClick = onDismissRequest,
                okButtonEnabled = chosenUri != null && trackName.isNotBlank(),
                onOkClick = {
                    val uri = chosenUri ?: return@CancelOkButtonRow
                    context.contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    onConfirmRequest(Track(uri.toString(), trackName))
                })
        }
    }
}

/**
 * A simplified alert dialog, for when only a title, body text,
 * and an ok button need to be displayed.
 *
 * @param text The body text of the dialog
 * @param title The title text of the dialog, if any.
 * @param onDismissRequest The callback that will be invoked when the user
 *     tries to close the dialog, either by tapping the ok button or when
 *     a tap outside the bounds of the dialog is performed
 */
@Composable fun SimpleAlertDialog(
    text: String,
    title: String? = null,
    onDismissRequest: () -> Unit
) = AlertDialog(
    onDismissRequest,
    title = { if (title != null) Text(title) },
    text = { Text(text) },
    buttons = { Row {
        Spacer(Modifier.weight(1f))
        TextButton(
            onClick = onDismissRequest,
            modifier = Modifier.heightIn(48.dp, Dp.Infinity),
        ) {
            Text(stringResource(android.R.string.ok),
                 style = MaterialTheme.typography.button,
                 color = MaterialTheme.colors.secondary)
        }
    }})

/** Show a dialog displaying the app's privacy policy to the user. */
@Composable fun PrivacyPolicyDialog(
    onDismissRequest: () -> Unit
) = SimpleAlertDialog(
    title = stringResource(R.string.privacy_policy_description),
    text = stringResource(R.string.privacy_policy_text),
    onDismissRequest = onDismissRequest)

/** Show a dialog displaying information about the app to the user. */
@Composable fun AboutAppDialog(
    onDismissRequest: () -> Unit
) = SimpleAlertDialog(
    title = stringResource(R.string.app_name),
    text = stringResource(R.string.about_app_text),
    onDismissRequest = onDismissRequest)

