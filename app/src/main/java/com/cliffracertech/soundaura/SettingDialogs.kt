/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.ui.compose.LibrariesContainer

/**
 * Launch a dialog to explain the consequences of the 'Play in background' setting.
 *
 * @param modifier The [Modifier] to use for the dialog window.
 * @param onDismissRequest The callback that will be invoked if the dialog is dismissed.
 */
@Composable fun PlayInBackgroundExplanationDialog(
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit,
) = SoundAuraDialog(
    modifier = modifier.restrictWidthAccordingToSizeClass(),
    useDefaultWidth = false,
    title = stringResource(R.string.play_in_background_setting_title),
    onDismissRequest = onDismissRequest,
    showCancelButton = false,
) {
    Column(Modifier.padding(horizontal = 16.dp), Arrangement.spacedBy(8.dp)) {
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
 * Compose a dialog that requests notification permission and, if necessary, explains
 * why it is needed.
 *
 * @param showExplanationFirst Whether or not a dialog box explaining
 *     why the permission is needed will be shown.
 * @param onShowTileTutorialClick The callback that will be invoked
 *     if the user clicks on the displayed link to the tile tutorial.
 * @param onDismissRequest The callback that will be invoked if the
 *     explanatory dialog is dismissed.
 * @param onPermissionResult The callback that will be invoked when
 *     the user grants or rejects the permission. The Boolean parameter
 *     will be true if the permission was granted, or false otherwise.
 * @param modifier The [Modifier] to use for the dialog window.
 */
@Composable fun NotificationPermissionDialog(
    showExplanationFirst: Boolean,
    onShowTileTutorialClick: () -> Unit,
    onDismissRequest: () -> Unit,
    onPermissionResult: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        onPermissionResult(true)
        return
    }

    var explanationDismissed by rememberSaveable { mutableStateOf(false) }
    if (showExplanationFirst && !explanationDismissed)
        SoundAuraDialog(
            modifier = modifier.restrictWidthAccordingToSizeClass(),
            useDefaultWidth = false,
            title = stringResource(R.string.request_notification_permission_title),
            onDismissRequest = onDismissRequest,
            showCancelButton = false,
            onConfirm = { explanationDismissed = true }
        ) {
            Column(Modifier.padding(horizontal = 16.dp), Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.request_notification_permission_explanation_p1),
                     style = MaterialTheme.typography.body1)

                val linkText = stringResource(R.string.tile_tutorial_link_text)
                TextWithClickableLink(
                    linkText = linkText,
                    completeText = stringResource(
                        R.string.request_notification_permission_explanation_p2, linkText),
                    onLinkClick = onShowTileTutorialClick)
            }
        }
    if (!showExplanationFirst || explanationDismissed) {
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
            onResult = onPermissionResult)
        LaunchedEffect(Unit) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/**
 * Launch a dialog to request the READ_PHONE_STATE permission.
 * @param modifier The [Modifier] to use for the dialog window.
 * @param showExplanationFirst Whether or not a dialog box explaining
 *     why the permission is needed will be shown.
 * @param onDismissRequest The callback that will be invoked if the
 *     explanatory dialog is dismissed.
 * @param onPermissionResult The callback that will be invoked when
 *     the user grants or rejects the permission. The Boolean parameter
 *     will be true if the permission was granted, or false otherwise.
 */
@Composable fun PhoneStatePermissionDialog(
    modifier: Modifier = Modifier,
    showExplanationFirst: Boolean,
    onDismissRequest: () -> Unit,
    onPermissionResult: (Boolean) -> Unit
) {
    var explanationDismissed by rememberSaveable { mutableStateOf(false) }
    if (showExplanationFirst && !explanationDismissed)
        SoundAuraDialog(
            modifier = modifier,
            title = stringResource(R.string.auto_pause_during_calls_setting_title),
            text = stringResource(R.string.request_phone_state_permission_explanation),
            onDismissRequest = onDismissRequest,
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

/** Compose a button that will prompt the user to add the app's quick settings
 * tile to their status bar when clicked. RequestAddTileServiceButton is non-
 * functional if called when [Build.VERSION.SDK_INT] < 33. */
@Composable private fun RequestAddTileServiceButton(
    modifier: Modifier = Modifier,
    onSuccess: () -> Unit
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    val onAddTileButtonClick = {
        val statusBarManager = context.getSystemService(
            StatusBarManager::class.java) as StatusBarManager
        statusBarManager.requestAddTileService(
            ComponentName(context, TogglePlaybackTileService::class.java),
            context.getString(R.string.app_name),
            android.graphics.drawable.Icon.createWithResource(
                context, R.drawable.tile_and_notification_icon),
            { it.run() }
        ) {
            if (it == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED)
                onSuccess()
        }
    }
    Button(
        onClick = onAddTileButtonClick,
        modifier = modifier,
        elevation = ButtonDefaults.elevation(
            defaultElevation = 8.dp,
            pressedElevation = 4.dp),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(MaterialTheme.colors.primaryVariant),
        contentPadding = PaddingValues(vertical = 16.dp, horizontal = 24.dp),
    ) {
        Text(stringResource(R.string.tile_tutorial_add_tile_button_text))
    }
}

/** Compose an explanation for the user about how to add the app's quick
 * settings tile to their status bar. */
@Composable private fun PreApi33TileTutorial() {
    Spacer(Modifier.size(16.dp))
    Text(stringResource(R.string.tile_tutorial_add_tile_text))
    Column {
        var showingAddTileHelp by rememberSaveable { mutableStateOf(false) }
        Row(modifier = Modifier
                .minTouchTargetSize()
                .clickable { showingAddTileHelp = !showingAddTileHelp },
            verticalAlignment = Alignment.CenterVertically
        ) {
            val localTextStyle = LocalTextStyle.current
            val style = remember(localTextStyle) {
                localTextStyle.copy(fontWeight = FontWeight.Bold)
            }
            Text(stringResource(R.string.tile_tutorial_add_tile_help_button_text),
                 style = style)
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
        AnimatedVisibility(showingAddTileHelp) {
            Text(stringResource(R.string.tile_tutorial_add_tile_help_text))
        }
    }
}

/** Compose a [MultiStepDialog] that contains a tutorial explaining
 * to the user how to add and use the app's quick settings tile. */
@Composable fun TileTutorialDialog(
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit
) {
    var currentPageIndex by rememberSaveable { mutableStateOf(0) }
    MultiStepDialog(
        modifier = modifier.restrictWidthAccordingToSizeClass(),
        useDefaultWidth = false,
        title = stringResource(R.string.tile_tutorial_title),
        onDismissRequest = onDismissRequest,
        numPages = 2,
        currentPageIndex = currentPageIndex,
        onCurrentPageIndexChange = { currentPageIndex = it },
    ) { pageModifier, currentIndex ->
        if (currentIndex == 0)
            Column(pageModifier) {
                Text(stringResource(R.string.tile_tutorial_intro_text))
                if (Build.VERSION.SDK_INT >= 33)
                    RequestAddTileServiceButton(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 12.dp, bottom = 6.dp),
                        onSuccess = { currentPageIndex++ })
                else PreApi33TileTutorial()
            }
        else Column(pageModifier, Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.tile_tutorial_tile_usage_text))
            val context = LocalContext.current

            val linkText = stringResource(R.string.tile_tutorial_hide_notification_link_text)
            TextWithClickableLink(
                linkText = linkText,
                completeText = stringResource(
                    R.string.tile_tutorial_hide_notification_text, linkText),
            ) {
                onDismissRequest()
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.fromParts("package", context.packageName, null)
                context.startActivity(intent)
            }
        }
    }
}

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
@Composable fun OpenSourceLibrariesUsedDialog(
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit
) = SoundAuraDialog(
    modifier = modifier.restrictWidthAccordingToSizeClass(),
    useDefaultWidth = false,
    title = stringResource(R.string.open_source_licenses),
    onDismissRequest = onDismissRequest,
    showCancelButton = false,
) {
    val config = LocalConfiguration.current
    // Because SoundAuraDialog places its content inside a scrollable container,
    // and LibrariesContainer apparently uses a LazyColumn, restricting the max
    // height prevents a java.lang.IllegalStateException: Vertically scrollable
    // component was measured with an infinity maximum height constraints crash.
    LibrariesContainer(Modifier
        .padding(horizontal = 16.dp)
        .heightIn(max = config.screenHeightDp.dp))
}

/** Show a dialog displaying information about the app to the user. */
@Composable fun AboutAppDialog(
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit
) = SoundAuraDialog(
    modifier = modifier,
    titleLayout = {
        Row(modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
                               .align(Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(painter = painterResource(R.drawable.tile_and_notification_icon),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colors.primary)
            Spacer(Modifier.width(8.dp))
            Text(text = stringResource(R.string.app_name),
                modifier = Modifier.alignByBaseline(),
                style = MaterialTheme.typography.h6)
            Spacer(Modifier.width(6.dp))
            Text(text = stringResource(R.string.app_version),
                modifier = Modifier.alignByBaseline(),
                style = MaterialTheme.typography.subtitle1)
        }
    }, text = stringResource(R.string.about_app_setting_body),
    onDismissRequest = onDismissRequest,
    buttons = {
        Divider(Modifier.padding(top = 12.dp))
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
            val uriHandler = LocalUriHandler.current
            val gitHubLink = stringResource(R.string.github_link)
            TextButton(
                onClick = { uriHandler.openUri(gitHubLink) },
                modifier = Modifier.weight(1f).fillMaxSize(),
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
            ) {
                Text(text = stringResource(R.string.ok),
                    color = MaterialTheme.colors.primary)
            }
        }
    })