/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mikepenz.aboutlibraries.ui.compose.LibrariesContainer

/**
 * Launch a dialog to explain the consequences of the 'Play in background' setting.
 *
 * @param onDismissRequest The callback that will be invoked if the dialog is dismissed.
 */
@Composable fun PlayInBackgroundExplanationDialog(
    onDismissRequest: () -> Unit,
) = SoundAuraDialog(
    windowPadding = PaddingValues(20.dp),
    title = stringResource(R.string.play_in_background_setting_title),
    onDismissRequest = onDismissRequest,
    showCancelButton = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ProvideTextStyle(MaterialTheme.typography.subtitle1) {
            Text(stringResource(R.string.play_in_background_explanation))
            BulletedList(listOf(
                stringResource(R.string.play_in_background_explanation_bullet_1),
                stringResource(R.string.play_in_background_explanation_bullet_2),
                stringResource(R.string.play_in_background_explanation_bullet_3),
                stringResource(R.string.play_in_background_explanation_bullet_4)))
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
    var explanationDismissed by rememberSaveable { mutableStateOf(false) }
    if (showExplanationFirst && !explanationDismissed)
        SoundAuraDialog(
            onDismissRequest = onDismissRequest,
            title = stringResource(R.string.auto_pause_during_calls_setting_title),
            text = stringResource(R.string.request_phone_state_permission_explanation),
            onConfirm = { explanationDismissed = true })
    if (!showExplanationFirst || explanationDismissed) {
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
    pages = listOf(@Composable { pageModifier ->
        Column(pageModifier) {
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
                Row(modifier = Modifier
                    .minTouchTargetSize()
                    .clickable { showingAddTileHelp = !showingAddTileHelp },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.tile_tutorial_add_tile_help_button_text),
                         style = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold))
                    val iconRotation by animateFloatAsState(
                        if (showingAddTileHelp) 180f else 0f)
                    Spacer(Modifier.weight(1f))
                    Icon(imageVector = Icons.Default.ExpandMore,
                         contentDescription = stringResource(
                             if (showingAddTileHelp)
                                 R.string.tile_tutorial_add_tile_help_button_hide_description
                             else R.string.tile_tutorial_add_tile_help_button_show_description),
                         modifier = Modifier.rotate(iconRotation))
                }
                AnimatedContent(showingAddTileHelp) {
                    if (it) Text(stringResource(R.string.tile_tutorial_add_tile_help_text))
                }
            }
        }
//        }
    }, @Composable { pageModifier ->
        Column(pageModifier, Arrangement.spacedBy(16.dp)) {
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
                pushStyle(
                    SpanStyle(color = LocalContentColor.current,
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
    title = stringResource(R.string.privacy_policy_setting_title),
    text = stringResource(R.string.privacy_policy_setting_body),
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
                Text(stringResource(id = R.string.ok))
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
) = Dialog(onDismissRequest) {
    Surface(shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(top = 16.dp)) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                // Title
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painter = painterResource(R.drawable.tile_and_notification_icon),
                         contentDescription = null,
                         modifier = Modifier.size(24.dp),
                         tint = MaterialTheme.colors.primary)
                    Spacer(Modifier.width(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = stringResource(R.string.app_name),
                             modifier = Modifier.alignByBaseline(),
                             style = MaterialTheme.typography.body1)
                        Text(text = stringResource(R.string.app_version),
                             modifier = Modifier.alignByBaseline(),
                             style = MaterialTheme.typography.subtitle1)
                    }
                }

                // Content
                Spacer(Modifier.height(12.dp))
                Text(text = stringResource(R.string.about_app_setting_body),
                     style = MaterialTheme.typography.subtitle1)
            }
            Spacer(Modifier.height(12.dp))

            // Bottom buttons
            Divider()
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val uriHandler = LocalUriHandler.current
                val gitHubLink = stringResource(R.string.github_link)
                TextButton(
                    onClick = { uriHandler.openUri(gitHubLink) },
                    modifier = Modifier.minTouchTargetSize().weight(1f),
                    shape = MaterialTheme.shapes.medium.bottomStartShape()
                ) {
                    Icon(painterResource(R.drawable.github_logo), null,
                         Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(text = stringResource(R.string.view_source_code),
                         textDecoration = TextDecoration.Underline,
                         color = MaterialTheme.colors.primary)
                }
                VerticalDivider()
                TextButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.minTouchTargetSize().weight(0.5f),
                    shape = MaterialTheme.shapes.medium.bottomEndShape(),
                    content = { Text(stringResource(R.string.ok)) })
            }
        }
    }
}