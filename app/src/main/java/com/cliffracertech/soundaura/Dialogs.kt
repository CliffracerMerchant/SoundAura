/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    onConfirm: (String) -> Unit
) {
    var currentName by rememberSaveable { mutableStateOf(itemName) }
    SoundAuraDialog(
        title = stringResource(R.string.rename_dialog_title, itemName),
        confirmButtonEnabled = currentName.isNotBlank(),
        confirmText = stringResource(R.string.rename_description),
        onConfirm = { onConfirm(currentName)
                      onDismissRequest() },
        onDismissRequest = onDismissRequest,
        content = { TextField(
            value = currentName,
            onValueChange = { currentName = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.body1)
        })
}

@Composable fun ConfirmRemoveDialog(
    itemName: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit
) = SoundAuraDialog(
    onDismissRequest = onDismissRequest,
    title = stringResource(R.string.confirm_remove_title, itemName),
    text = stringResource(R.string.confirm_remove_message),
    confirmText = stringResource(R.string.remove_description),
    onConfirm = {
        onConfirm()
        onDismissRequest()
    })

/** Return a suitable display name for a file uri (i.e. the file name minus
 * the file type extension, and with underscores replaced with spaces. */
fun Uri.getDisplayName(context: Context) =
    DocumentFile.fromSingleUri(context, this)?.name
        ?.substringBeforeLast('.')
        ?.replace('_', ' ')

/** Return whether the List<String> contains any strings that are blank
 * (i.e. are either empty or consist of only whitespace characters). */
val List<String>.containsBlanks get() =
    find { it.isBlank() } != null

/** Compose a LazyColumn of TextFields to edit the
 * strings of the the receiver MutableList<NewTrack>. */
@Composable private fun MutableList<String>.Editor(
    modifier: Modifier = Modifier
) = LazyColumn(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(8.dp),
) {
    items(size) { index ->
        TextField(
            value = get(index),
            onValueChange = { this@Editor[index] = it },
            textStyle = MaterialTheme.typography.body1,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(1f))
    }
}

/**
 * Open a dialog for the user to select one or more audio files to add to
 * their library.
 *
 * @param onDismissRequest The callback that will be invoked when the user
 *     clicks outside the dialog or taps the cancel button.
 * @param onConfirmRequest The callback that will be invoked when the user
 *     taps the dialog's confirm button after having selected one or more files.
 *     The first parameter is the list of Uris representing the files to add,
 *     while the second parameter is the list of names for each of these uris.
 */
@Composable fun AddTracksFromLocalFilesDialog(
    onDismissRequest: () -> Unit,
    onConfirmRequest: (List<Uri>, List<String>) -> Unit,
) {
    val context = LocalContext.current
    var chosenUris by rememberSaveable { mutableStateOf<List<Uri>?>(null) }
    val trackNames = rememberSaveable<SnapshotStateList<String>>(
        saver = listSaver(
            save = { it },
            restore = { it.toMutableStateList() }),
        init = { mutableStateListOf() })

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty())
            onDismissRequest()
        chosenUris = uris
        trackNames.clear()
        for (uri in uris)
            trackNames.add(uri.getDisplayName(context) ?: "")
    }

    if (chosenUris == null)
        LaunchedEffect(Unit) { launcher.launch(arrayOf("audio/*")) }
    else SoundAuraDialog(
        title = stringResource(R.string.add_local_files_dialog_title),
        onDismissRequest = onDismissRequest,
        confirmButtonEnabled = chosenUris != null &&
                               !trackNames.containsBlanks,
        onConfirm = {
            val uris = chosenUris ?: return@SoundAuraDialog
            onConfirmRequest(uris, trackNames)
        }, content = {
            trackNames.Editor(Modifier.heightIn(max = 400.dp))
        })
}

@Composable fun MultiStepDialog(
    title: String,
    titleContentSpacing: Dp = 12.dp,
    contentButtonSpacing: Dp = 8.dp,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit = onDismissRequest,
    pages: List<@Composable () -> Unit>
) = Dialog(onDismissRequest, DialogProperties(usePlatformDefaultWidth = false)) {
    require(pages.isNotEmpty())

    Surface(Modifier.padding(24.dp), MaterialTheme.shapes.medium) {
        Column(Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp)) {
            var previousPage by rememberSaveable { mutableStateOf(0) }
            var currentPage by rememberSaveable { mutableStateOf(0) }

            Row {
                Text(title, style = MaterialTheme.typography.body1)
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.multi_step_dialog_indicator, currentPage + 1, pages.size),
                     style = MaterialTheme.typography.subtitle1)
            }

            Spacer(Modifier.height(titleContentSpacing))

            CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.subtitle1) {
                SlideAnimatedContent(
                    targetState = currentPage,
                    modifier = Modifier.animateContentSize(tween()),
                    leftToRight = currentPage >= previousPage
                ) { pages[it]() }
            }

            Spacer(Modifier.height(contentButtonSpacing))

            Row {
                val firstButtonText: String
                val secondButtonText: String
                val firstButtonOnClick: () -> Unit
                val secondButtonOnClick: () -> Unit

                if (currentPage == 0) {
                    firstButtonText = "Cancel"
                    firstButtonOnClick = onDismissRequest
                } else {
                    firstButtonText = "Previous"
                    firstButtonOnClick = {
                        previousPage = currentPage
                        currentPage -= 1
                    }
                }
                if (currentPage == pages.lastIndex) {
                    secondButtonText = "Finish"
                    secondButtonOnClick = onConfirm
                } else {
                    secondButtonText = "Next"
                    secondButtonOnClick = {
                        previousPage = currentPage
                        currentPage += 1
                    }
                }

                TextButton(firstButtonOnClick) { Text(firstButtonText) }
                Spacer(Modifier.weight(1f))
                TextButton(secondButtonOnClick) { Text(secondButtonText) }
            }
        }
    }
}

/**
 * Launch a dialog to request the READ_PHONE_STATE permission.
 * @param showExplanationFirst Whether or not a dialog box explaining
 *     why the permission is needed will be shown.
 * @param onDismissRequest The callback that will be invoked if the
 *     explanatory dialog is dismissed.
 * @param onPermissionResult The callback that will be invoked when
 *     the user grants or rejects the permission. The Boolean parameter
 *     will be true if the permission was granted, or false otherwise.
 */
@Composable fun PhoneStatePermissionDialog(
    showExplanationFirst: Boolean,
    onDismissRequest: () -> Unit,
    onPermissionResult: (Boolean) -> Unit
) {
    var userSawExplanation by rememberSaveable { mutableStateOf(false) }
    if (showExplanationFirst && !userSawExplanation) {
        SoundAuraDialog(
            onDismissRequest = onDismissRequest,
            title = stringResource(R.string.auto_pause_during_calls_setting_title),
            text = stringResource(R.string.request_phone_state_permission_explanation),
            onConfirm = { userSawExplanation = true })
    }
    if (!showExplanationFirst || userSawExplanation) {
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
            onResult = onPermissionResult)
        LaunchedEffect(Unit) {
            launcher.launch(Manifest.permission.READ_PHONE_STATE)
        }
    }
}

@Composable fun TileTutorialDialog(
    onDismissRequest: () -> Unit
) = MultiStepDialog(
    title = stringResource(R.string.tile_tutorial_title),
    onDismissRequest = onDismissRequest,
    pages = listOf(@Composable {
        Column {
            Text(stringResource(R.string.tile_tutorial_intro_text))

//            if (Build.VERSION.SDK_INT >= 33) {
//                val onButtonClick = { StatusBarManager.requestAddTileService(
//                    ComponentName(context, TogglePlaybackTileService::class.java),
//                    stringResource(R.string.app_name),
//                    icon =,
//                    resultExecutor =,
//                    resultCallback =,)
//                }
//                TextButton(onClick = onButtonClick) {
//                    Text(stringResource(R.string.tile_tutorial_add_tile_button_text))
//                }
//            } else {
            Spacer(Modifier.size(16.dp))
            Text(stringResource(R.string.tile_tutorial_add_tile_text))
            Column {
                var showingAddTileHelp by rememberSaveable { mutableStateOf(false) }
                Row(modifier = Modifier.minTouchTargetSize()
                        .clickable { showingAddTileHelp = !showingAddTileHelp },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.tile_tutorial_add_tile_help_button_text),
                         style = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold))
                    val iconRotation by animateFloatAsState(if (showingAddTileHelp) 180f else 0f)
                    Spacer(Modifier.weight(1f))
                    Icon(imageVector = Icons.Default.ExpandMore,
                         contentDescription = stringResource(
                             if (showingAddTileHelp)
                                 R.string.tile_tutorial_add_tile_help_button_hide_description
                             else R.string.tile_tutorial_add_tile_help_button_show_description),
                         modifier = Modifier.rotate(iconRotation))
                }
                AnimatedVisibility(showingAddTileHelp) {
                    Text(stringResource(R.string.tile_tutorial_add_tile_help_text))
                }
            }
        }
//        }
    }, @Composable {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.tile_tutorial_tile_usage_text))
            val context = LocalContext.current
            val hideNotificationLinkText = stringResource(
                R.string.tile_tutorial_hide_notification_link_text)
            val hideNotificationText = stringResource(
                R.string.tile_tutorial_hide_notification_text,
                hideNotificationLinkText)
            val linkTextStartIndex = hideNotificationText.indexOf(hideNotificationLinkText)
            val linkTextLastIndex = linkTextStartIndex + hideNotificationLinkText.length

            val linkifiedText = buildAnnotatedString {
                // ClickableText seems to not follow the local text style by default
                pushStyle(SpanStyle(color = LocalContentColor.current,
                                    fontSize = LocalTextStyle.current.fontSize))
                append(hideNotificationText)
                val urlStyle = SpanStyle(color = MaterialTheme.colors.primary,
                                         textDecoration = TextDecoration.Underline)
                addStyle(urlStyle, linkTextStartIndex, linkTextLastIndex)
            }

            ClickableText(
                text = linkifiedText,
                modifier = Modifier.alpha(LocalContentAlpha.current),
                style = MaterialTheme.typography.subtitle1
            ) {
                if (it in linkTextStartIndex..linkTextLastIndex) {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.fromParts("package", context.packageName, null)
                    context.startActivity(intent)
                }
            }
        }
    }))

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
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(painter = painterResource(R.drawable.tile_and_notification_icon),
                 contentDescription = null,
                 modifier = Modifier.size(30.dp),
                 tint = MaterialTheme.colors.primary)
            Spacer(Modifier.width(2.dp))
            Text(text = title, style = MaterialTheme.typography.body1)
            Text(text = stringResource(R.string.app_version),
                 style = MaterialTheme.typography.subtitle1)
        }
    }, showCancelButton = false,
    onDismissRequest = onDismissRequest,
    onConfirm = onDismissRequest,
    contentButtonSpacing = 0.dp
) {
    val uriHandler = LocalUriHandler.current
    val gitHubLink = stringResource(R.string.github_link)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = stringResource(R.string.about_app_body),
             style = MaterialTheme.typography.subtitle1)
        Row(Modifier.clickable { uriHandler.openUri(gitHubLink) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(painter = painterResource(R.drawable.github_logo),
                 contentDescription = null,
                 modifier = Modifier.padding(top = 16.dp, bottom = 16.dp, end = 12.dp))
            Text(text = stringResource(R.string.source_code_description),
                 textDecoration = TextDecoration.Underline,
                 color = MaterialTheme.colors.primary)
        }
    }
}