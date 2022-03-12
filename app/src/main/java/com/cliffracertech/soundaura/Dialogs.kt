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
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.ColorUtils
import androidx.documentfile.provider.DocumentFile

/**
 * A row containing an ok text button and, optionally, a cancel text
 * button, for use in a dialog box.
 *
 * @param onCancelClick The callback that will be invoked when the cancel
 *     button is clicked. If null, the cancel button will not appear.
 * @param okButtonEnabled Whether or not the ok button is enabled.
 * @param onOkClick The callback that will be invoked when the ok button is clicked.
 */
@Composable fun CancelOkButtonRow(
    onCancelClick: (() -> Unit)? = null,
    okButtonEnabled: Boolean = true,
    onOkClick: () -> Unit,
) = Row {
    Spacer(Modifier.weight(1f))
    if (onCancelClick != null)
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

/** Show a dialog displaying the app's privacy policy to the user. */
@Composable fun PrivacyPolicyDialog(
    onDismissRequest: () -> Unit
) = AlertDialog(
    onDismissRequest = onDismissRequest,
    buttons = { CancelOkButtonRow(onOkClick = onDismissRequest) },
    title = { Text(stringResource(R.string.privacy_policy_description)) },
    text = { Text(stringResource(R.string.privacy_policy_text)) })

/** Show a dialog displaying information about the app to the user. */
@Composable fun AboutAppDialog(
    onDismissRequest: () -> Unit
) = AlertDialog(
    onDismissRequest = onDismissRequest,
    buttons = { CancelOkButtonRow(onOkClick = onDismissRequest) },
    title = { Text(stringResource(R.string.app_name)) },
    text = {
        val text = stringResource(R.string.about_app_body)
        val linkifiedText = buildAnnotatedString {
            // ClickableText seems to not follow the local text style by default
            val backgroundColor = MaterialTheme.colors.surface
            val localContentColor = LocalContentColor.current
            val localContentAlpha = LocalContentAlpha.current
            val bodyTextColor = remember(backgroundColor, localContentColor, localContentAlpha) {
                Color(ColorUtils.blendARGB(backgroundColor.toArgb(),
                                           localContentColor.toArgb(),
                                           localContentAlpha))
            }
            pushStyle(SpanStyle(color = bodyTextColor))
            append(text)
            val urlRange = text.indexOf("https://")..text.lastIndex
            val urlStyle = SpanStyle(color = MaterialTheme.colors.secondary,
                                     textDecoration = TextDecoration.Underline)
            addStyle(style = urlStyle, start = urlRange.first, end = urlRange.last + 1)
            addStringAnnotation(tag = "URL", annotation = text.substring(urlRange),
                                start = urlRange.first, end = urlRange.last + 1)
        }
        val uriHandler = LocalUriHandler.current

        ClickableText(
            text = linkifiedText,
            style = MaterialTheme.typography.body2
        ) {
            val annotations = linkifiedText.getStringAnnotations("URL", it, it)
            for (annotation in annotations)
                uriHandler.openUri(annotation.item)
        }
    })