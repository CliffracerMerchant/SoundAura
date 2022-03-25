/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.content.Context
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.ColorUtils
import androidx.documentfile.provider.DocumentFile
import com.mikepenz.aboutlibraries.ui.compose.LibrariesContainer

fun Modifier.minTouchTargetSize() =
    sizeIn(minWidth = 48.dp, minHeight = 48.dp)

/**
 * A row containing a confirm text button and, optionally, a cancel text
 * button, for use in a dialog box.
 *
 * @param onCancel The callback that will be invoked when the cancel
 *     button is tapped. If null, the cancel button will not appear.
 * @param confirmEnabled Whether or not the confirm button is enabled.
 * @param onConfirm The callback that will be invoked when the confirm button is tapped.
 */
@Composable fun CancelConfirmButtonRow(
    onCancel: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    confirmText: String = stringResource(android.R.string.ok),
    onConfirm: () -> Unit,
) = Row {
    Spacer(Modifier.weight(1f))
    if (onCancel != null)
        TextButton(onCancel, Modifier.minTouchTargetSize()) {
            Text(stringResource(android.R.string.cancel))
        }
    TextButton(onConfirm, Modifier.minTouchTargetSize(), confirmEnabled) {
        Text(confirmText)
    }
}

// SoundAuraDialog was created to have more control over the layout of the dialog
// than the stock Compose AlertDialog does, and due to the fact that the standard
// AlertDialog was not adding space in between the title and the content TextField
// of the rename dialog, despite trying to add spacers and/or padding to both the
// title and the TextField.
/**
 * Compose an alert dialog.
 *
 * @param title The string representing the dialog's title. Can be null, in
 *     which case the title will not be displayed.
 * @param titleLayout The layout that will be used for the dialog's title.
 *     Will default to a composable Text using the value of the title parameter.
 * @param text The string representing the dialog's message. Will only be used
 *     if the content parameter is not overridden, in which case it will default
 *     to a composable Text containing the value of this parameter.
 * @param titleContentSpacing The spacing, in Dp, in between the title and the content.
 * @param contentButtonSpacing The spacing, in Dp, in between the content and the buttons.
 * @param onDismissRequest The callback that will be invoked when the user taps
 *     the cancel button, if shown, or when they tap outside the dialog or the
 *     back button is pressed.
 * @param showCancelButton Whether or not the cancel button will be shown.
 * @param confirmButtonEnabled Whether the confirm button is enabled.
 * @param confirmText The string used for the confirm button.
 * @param onConfirm The callback that will be invoked when the confirm button is tapped.
 * @param content The composable lambda used for the dialog's content area.
 *     content will default to a composable Text object that contains the text
 *     described by the text parameter.
 */
@Composable fun SoundAuraDialog(
    title: String? = null,
    titleLayout: @Composable (String) -> Unit = @Composable {
        val textStyle = MaterialTheme.typography.body1
        ProvideTextStyle(textStyle) { Text(it) }
    }, text: String? = null,
    titleContentSpacing: Dp = 12.dp,
    contentButtonSpacing: Dp = 8.dp,
    onDismissRequest: () -> Unit,
    showCancelButton: Boolean = true,
    confirmButtonEnabled: Boolean = true,
    confirmText: String = stringResource(android.R.string.ok),
    onConfirm: () -> Unit = onDismissRequest,
    content: @Composable () -> Unit = @Composable {
        CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.high) {
            val textStyle = MaterialTheme.typography.subtitle1
            ProvideTextStyle(textStyle) { Text(text ?: "") }
        }
    },
) = Dialog(onDismissRequest) {
    Surface(shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp)) {
            if (title != null) {
                titleLayout(title)
                Spacer(Modifier.height(titleContentSpacing))
            }
            content()
            Spacer(Modifier.height(contentButtonSpacing))
            val cancelCallback = if (!showCancelButton) null
                                 else onDismissRequest
            CancelConfirmButtonRow(
                cancelCallback, confirmButtonEnabled, confirmText, onConfirm)
        }
    }
}

@Composable fun RenameDialog(
    itemName: String,
    onDismissRequest: () -> Unit,
    onConfirmRequest: (String) -> Unit
) {
    var currentName by rememberSaveable { mutableStateOf(itemName) }
    SoundAuraDialog(
        title = stringResource(R.string.rename_dialog_title, itemName),
        confirmButtonEnabled = currentName.isNotBlank(),
        confirmText = stringResource(R.string.rename_description),
        onConfirm = { onConfirmRequest(currentName)
                      onDismissRequest() },
        onDismissRequest = onDismissRequest,
        content = { TextField(
            value = currentName,
            onValueChange = { currentName = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.body1)
        })
}

@Composable fun ConfirmDeleteDialog(
    itemName: String,
    onDismissRequest: () -> Unit,
    onConfirmRequest: () -> Unit
) = SoundAuraDialog(
    onDismissRequest = onDismissRequest,
    title = stringResource(R.string.confirm_delete_title, itemName),
    text = stringResource(R.string.confirm_delete_message),
    confirmText = stringResource(R.string.delete_description),
    onConfirm = { onConfirmRequest()
                  onDismissRequest() })

fun Uri.getDisplayName(context: Context) =
    DocumentFile.fromSingleUri(context, this)?.name?.substringBeforeLast('.')

@Composable fun AddTrackFromLocalFileDialog(
    onDismissRequest: () -> Unit,
    onConfirmRequest: (Track) -> Unit
) {
    var chosenUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        chosenUri = it
        if (it == null)
            onDismissRequest()
    }
    val context = LocalContext.current
    var trackName by rememberSaveable(chosenUri) {
        mutableStateOf(chosenUri?.getDisplayName(context) ?: "")
    }

    if (chosenUri == null)
        LaunchedEffect(Unit) { launcher.launch(arrayOf("audio/*")) }
    else SoundAuraDialog(
        title = stringResource(R.string.track_name_description),
        onDismissRequest = onDismissRequest,
        confirmButtonEnabled = chosenUri != null && trackName.isNotBlank(),
        onConfirm = {
            val uri = chosenUri ?: return@SoundAuraDialog
            context.contentResolver.takePersistableUriPermission(
                uri, FLAG_GRANT_READ_URI_PERMISSION)
            onConfirmRequest(Track(uri.toString(), trackName))
        }, content = { TextField(
            value = trackName,
            onValueChange = { trackName = it },
            textStyle = MaterialTheme.typography.body1,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(1f))
        })
}

private class NewTrack(file: DocumentFile) {
    val uri = file.uri
    var name = file.name?.substringBeforeLast('.') ?: ""

    fun toTrack() = Track(uri.toString(), name)
}

private fun List<NewTrack>.containsNoBlankNames() =
    find { it.name.isBlank() } == null

/** Compose a LazyColumn of TextFields to edit the names of the
 * NewTrack instances in the receiver List<NewTrack>. */
@Composable private fun List<NewTrack>.Editor(
    modifier: Modifier = Modifier
) = LazyColumn(modifier) {
    items(size) { index ->
        TextField(
            value = get(index).name,
            onValueChange = { get(index).name = it },
            textStyle = MaterialTheme.typography.body1,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(1f))
    }
}

@Composable fun AddTracksFromLocalDirectoryDialog(
    onDismissRequest: () -> Unit,
    onConfirmRequest: (List<Track>) -> Unit
) {
    var chosenUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
        chosenUri = it
        if (it == null)
            onDismissRequest()
    }
    val context = LocalContext.current
    val trackNameList = rememberSaveable(chosenUri) {
        val parentUri = chosenUri
        if (parentUri == null)
            SnapshotStateList()
        else {
            // This app's min SDK version is 23, so
            // DocumentFile.fromTreeUri should never return null.
            val file = DocumentFile.fromTreeUri(context, parentUri)!!
            file.listFiles().filter {
                it.type?.startsWith("audio/") == true
            }.map(::NewTrack).toMutableStateList()
        }
    }

    if (chosenUri == null)
        LaunchedEffect(Unit) {
            launcher.launch(/*starting uri = */ null)
        }
    else SoundAuraDialog(
        title = stringResource(R.string.track_name_description),
        onDismissRequest = onDismissRequest,
        confirmButtonEnabled =
            trackNameList.isNotEmpty() &&
            trackNameList.containsNoBlankNames(),
        onConfirm = {
            trackNameList.forEach {
                context.contentResolver.takePersistableUriPermission(
                    it.uri, FLAG_GRANT_READ_URI_PERMISSION)
            }
            onConfirmRequest(trackNameList.map { it.toTrack() })
        }, content = {
            trackNameList.Editor()
        })
}

/** Show a dialog displaying the app's privacy policy to the user. */
@Composable fun PrivacyPolicyDialog(
    onDismissRequest: () -> Unit
) = SoundAuraDialog(
    title = stringResource(R.string.privacy_policy_description),
    text = stringResource(R.string.privacy_policy_text),
    showCancelButton = false,
    onDismissRequest = onDismissRequest,
    onConfirm = onDismissRequest)

/** Show a dialog to display all of the open source libraries used
 * in the app, as well as their licenses. */
@Composable fun OpenSourceLibrariesUsedDialog(onDismissRequest: () -> Unit) =
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(id = android.R.string.ok))
            }
        }, modifier = Modifier.padding(16.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        text = { Box(Modifier.fillMaxSize()) { LibrariesContainer() }})
        // Putting the LibrariesContainer inside the box prevents a
        // visual bug where the dialog appears at a smaller size at
        // first, and then changes to its full size.

/** Show a dialog displaying information about the app to the user. */
@Composable fun AboutAppDialog(
    onDismissRequest: () -> Unit
) = SoundAuraDialog(
    title = stringResource(R.string.app_name),
    titleLayout = { title ->
        Row(verticalAlignment = Alignment.Bottom) {
            Text(title, style = MaterialTheme.typography.body1)
            CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.high) {
                val textStyle = MaterialTheme.typography.subtitle1
                Spacer(Modifier.width(6.dp))
                ProvideTextStyle(textStyle) {
                    Text(stringResource(R.string.app_version))
                }
            }
        }
    }, showCancelButton = false,
    onDismissRequest = onDismissRequest,
    onConfirm = onDismissRequest,
) {
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
        val urlStyle = SpanStyle(color = MaterialTheme.colors.primary,
                                 textDecoration = TextDecoration.Underline)
        addStyle(style = urlStyle, start = urlRange.first, end = urlRange.last + 1)
        addStringAnnotation(tag = "URL", annotation = text.substring(urlRange),
                            start = urlRange.first, end = urlRange.last + 1)
    }
    val uriHandler = LocalUriHandler.current

    ClickableText(
        text = linkifiedText,
        style = MaterialTheme.typography.subtitle1
    ) {
        val annotations = linkifiedText.getStringAnnotations("URL", it, it)
        for (annotation in annotations)
            uriHandler.openUri(annotation.item)
    }
}