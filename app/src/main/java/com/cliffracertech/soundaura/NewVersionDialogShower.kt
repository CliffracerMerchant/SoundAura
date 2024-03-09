/*
 * This file is part of SoundAura, which is released under the terms of the Apache
 * License 2.0. See license.md in the project's root directory to see the full license.
 */

package com.cliffracertech.soundaura

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cliffracertech.soundaura.dialog.SoundAuraDialog

/**
 * Show a dialog to explain new features.
 *
 * If the value of [lastLaunchedVersionCode] is less than the current version
 * of the app (as determined by the value of [BuildConfig.VERSION_CODE]), then
 * a dialog will be shown to explain new features. [onDialogDismissed] will be
 * called when the new version dialog is dismissed, and should usually be used
 * to update the stored [lastLaunchedVersionCode] value to the new version code.
 */
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
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        val features: List<Pair<String, String>> = when (BuildConfig.VERSION_CODE) {
            10 -> listOf(
                stringResource(R.string.feature_playlists_title) to
                    stringResource(R.string.feature_playlists_description,
                        stringResource(R.string.create_playlist_title)),
                stringResource(R.string.feature_volume_boost_title) to
                    stringResource(R.string.feature_volume_boost_description,
                        stringResource(R.string.volume_boost_description)))
            else -> emptyList()
        }
        features.forEach { (title, description) ->
            Text(title, Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(4.dp))
            Text(description, textAlign = TextAlign.Justify)
            Spacer(Modifier.height(12.dp))
        }
    }
}