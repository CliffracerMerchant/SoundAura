/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Switch
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable fun AppSettings(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) = Surface(modifier, color = MaterialTheme.colors.background) {
    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item { DisplaySettingsCategory() }
        item { PlaybackSettingsCategory() }
        item { AboutSettingsCategory() }
    }
}

@Composable private fun DisplaySettingsCategory() =
    SettingCategory(stringResource(R.string.display)) {
        val viewModel: SettingsViewModel = viewModel()

        EnumDialogSetting(
            title = stringResource(R.string.app_theme),
            values = AppTheme.values(),
            valueNames = AppTheme.valueStrings(),
            currentValue = viewModel.appTheme,
            onValueClick = viewModel::onAppThemeClick)
    }

@Composable private fun PlayInBackgroundSetting(
    onTileTutorialShowRequest: () -> Unit
) {
    val viewModel: SettingsViewModel = viewModel()

    Setting(
        title = stringResource(R.string.play_in_background_setting_title),
        subtitle = stringResource(R.string.play_in_background_setting_description),
        onClick = viewModel::onPlayInBackgroundTitleClick
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Vertical Divider
            Box(Modifier.width((1.5).dp).height(40.dp)
                .background(MaterialTheme.colors.onSurface.copy(alpha = 0.12f)))

            Spacer(Modifier.width(6.dp))
            Switch(checked = viewModel.playInBackground,
                onCheckedChange = remember {{ viewModel.onPlayInBackgroundSwitchClick() }})
        }
        if (viewModel.showingPlayInBackgroundExplanation)
            PlayInBackgroundExplanationDialog(
                onDismissRequest = viewModel::onPlayInBackgroundExplanationDismiss)
        if (viewModel.showingNotificationPermissionDialog) {
            val context = LocalContext.current
            val showExplanation =
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
                    false
                else ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_DENIED
            NotificationPermissionDialog(
                showExplanationFirst = showExplanation,
                onShowTileTutorialClick = onTileTutorialShowRequest,
                onDismissRequest = viewModel::onNotificationPermissionDialogDismiss,
                onPermissionResult = viewModel::onNotificationPermissionDialogConfirm)
        }
    }
}

@Composable private fun AutoPauseDuringCallSetting() {
    val viewModel: SettingsViewModel = viewModel()

    AnimatedVisibility(
        visible = viewModel.autoPauseDuringCallSettingVisible,
        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
    ) {
        Column {
            Divider()
            Setting(
                title = stringResource(R.string.auto_pause_during_calls_setting_title),
                subtitle = stringResource(R.string.auto_pause_during_calls_setting_subtitle),
                onClick = viewModel::onAutoPauseDuringCallClick
            ) {
                Switch(checked = viewModel.autoPauseDuringCall,
                    onCheckedChange = remember {{ viewModel.onAutoPauseDuringCallClick() }})
            }
            if (viewModel.showingPhoneStatePermissionDialog) {
                val context = LocalContext.current
                val showExplanation = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.READ_PHONE_STATE
                    ) == PackageManager.PERMISSION_DENIED
                PhoneStatePermissionDialog(
                    showExplanationFirst = showExplanation,
                    onDismissRequest = viewModel::onPhoneStatePermissionDialogDismiss,
                    onPermissionResult = viewModel::onPhoneStatePermissionDialogConfirm)
            }
        }
    }
}

@Composable private fun PlaybackSettingsCategory() =
    SettingCategory(stringResource(R.string.playback)) {
        val viewModel: SettingsViewModel = viewModel()
        var showingTileTutorialDialog by rememberSaveable { mutableStateOf(false) }

        PlayInBackgroundSetting(
            onTileTutorialShowRequest = { showingTileTutorialDialog = true })
        AutoPauseDuringCallSetting()

        Divider()
        EnumDialogSetting(
            title = stringResource(R.string.on_zero_volume_behavior_setting_title),
            modifier = Modifier.restrictWidthAccordingToSizeClass(),
            useDefaultWidth = false,
            description = stringResource(R.string.on_zero_volume_behavior_setting_description),
            values = enumValues<OnZeroVolumeAudioDeviceBehavior>(),
            valueNames = OnZeroVolumeAudioDeviceBehavior.valueStrings(),
            valueDescriptions = OnZeroVolumeAudioDeviceBehavior.valueDescriptions(),
            currentValue = viewModel.onZeroVolumeAudioDeviceBehavior,
            onValueClick = viewModel::onOnZeroVolumeAudioDeviceBehaviorClick)
        Divider()
        DialogSetting(
            title = stringResource(R.string.control_playback_using_tile_setting_title),
            dialogVisible = showingTileTutorialDialog,
            onShowRequest = { showingTileTutorialDialog = true },
            onDismissRequest = { showingTileTutorialDialog = false },
            content = { TileTutorialDialog(onDismissRequest = it) })
    }

@Composable private fun AboutSettingsCategory() =
    SettingCategory(stringResource(R.string.about)) {
        DialogSetting(stringResource(R.string.privacy_policy_setting_title)) {
            PrivacyPolicyDialog(onDismissRequest = it)
        }
        Divider()
        DialogSetting(stringResource(R.string.open_source_licenses)) {
            OpenSourceLibrariesUsedDialog(onDismissRequest = it)
        }
        Divider()
        DialogSetting(stringResource(R.string.about_app_setting_title)) {
            AboutAppDialog(onDismissRequest = it)
        }
    }