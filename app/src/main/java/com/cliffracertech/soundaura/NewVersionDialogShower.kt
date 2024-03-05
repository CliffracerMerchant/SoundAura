/*
 * This file is part of SoundAura, which is released under the terms of the Apache
 * License 2.0. See license.md in the project's root directory to see the full license.
 */

package com.cliffracertech.soundaura

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.cliffracertech.soundaura.dialog.SoundAuraDialog

@Composable fun NewVersionDialogShower(
    lastLaunchedVersionCode: Int,
    onDialogDismissed: () -> Unit
) {
    val currentVersion = BuildConfig.VERSION_CODE

    if (lastLaunchedVersionCode == 0 ||
        lastLaunchedVersionCode == currentVersion ||
        currentVersion < 10 // Version code 10 was the first one for which a new version
    ) return                // dialog was added, so there will be no dialog to show before this

    var showingDialog by rememberMutableStateOf(true)
    if (showingDialog) NewVersionDialog {
        showingDialog = false
        onDialogDismissed()
    }
}

@Composable fun NewVersionDialog(
    onDismissRequest: () -> Unit
) = SoundAuraDialog(
    title = stringResource(R.string.new_version_dialog_title, BuildConfig.VERSION_NAME),
    onDismissRequest = onDismissRequest,
    showCancelButton = false,
) {
    when (BuildConfig.VERSION_CODE) {
        else -> {}
    }
}