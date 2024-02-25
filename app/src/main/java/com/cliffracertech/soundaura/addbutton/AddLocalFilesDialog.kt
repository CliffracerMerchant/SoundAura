/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.addbutton

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cliffracertech.soundaura.dialog.AnimatedValidatorMessage
import com.cliffracertech.soundaura.dialog.DialogWidth
import com.cliffracertech.soundaura.dialog.SoundAuraDialog
import com.cliffracertech.soundaura.library.PlaylistOptionsView
import com.cliffracertech.soundaura.ui.HorizontalDivider
import com.cliffracertech.soundaura.ui.SlideAnimatedContent
import com.cliffracertech.soundaura.ui.TextButton
import com.cliffracertech.soundaura.ui.VerticalDivider
import com.cliffracertech.soundaura.ui.bottomEndShape
import com.cliffracertech.soundaura.ui.bottomShape
import com.cliffracertech.soundaura.ui.bottomStartShape
import com.cliffracertech.soundaura.ui.minTouchTargetSize

@Composable private fun ColumnScope.AddLocalFilesDialogButtons(
    step: AddLocalFilesDialogStep
) {
    HorizontalDivider()
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
        step.buttons.forEachIndexed { index, button ->
            TextButton(
                modifier = Modifier.minTouchTargetSize().weight(1f),
                enabled = button.isEnabledProvider(),
                shape = when {
                    step.buttons.size == 1 ->
                        MaterialTheme.shapes.medium.bottomShape()
                    index == 0 ->
                        MaterialTheme.shapes.medium.bottomStartShape()
                    index == step.buttons.lastIndex ->
                        MaterialTheme.shapes.medium.bottomEndShape()
                    else -> RectangleShape
                }, textResId = button.textResId,
                onClick = button.onClick)
            if (index != step.buttons.lastIndex)
                VerticalDivider()
        }
    }
}

/**
 * When [SystemFileChooser] enters the composition a system file picker
 * will be shown to allow the user to pick one or more files.
 *
 * @param fileTypeArgs An [Array] of [String]s that describes which
 *     file MIME types are allowed to be chosen
 * @param onFilesSelected The callback that will be invoked when one
 *     or more files are picked, represented in the [List] argument
 */
@Composable fun SystemFileChooser(
    fileTypeArgs: Array<String> = arrayOf("audio/*", "application/ogg"),
    onFilesSelected: (List<Uri>) -> Unit,
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = onFilesSelected)
    LaunchedEffect(Unit) { launcher.launch(fileTypeArgs) }
}

/** Open a multi-step dialog for the user to select one or more audio files
 * to add to their library. The shown step will change according to [step]. */
@Composable fun AddLocalFilesDialog(step: AddLocalFilesDialogStep) {
    if (step is AddLocalFilesDialogStep.SelectingFiles) {
        SystemFileChooser { uris ->
            if (uris.isEmpty())
                step.onDismissRequest()
            else step.onFilesSelected(uris)
        }
    } else SoundAuraDialog(
        width = DialogWidth.MatchToScreenSize(WindowInsets.ime),
        title = stringResource(step.titleResId),
        onDismissRequest = step.onDismissRequest,
        buttons = { AddLocalFilesDialogButtons(step) }
    ) {
        SlideAnimatedContent(
            targetState = step,
            leftToRight = step.wasNavigatedForwardTo,
        ) { step ->
            // This background modifiers gives a border to the content to
            // improve the appearance of the SlideAnimatedContent animations
            val backgroundModifier = Modifier
                .background(MaterialTheme.colors.surface)
                .padding(horizontal = 16.dp)
            when (step) {
                is AddLocalFilesDialogStep.SelectingFiles -> {}
                is AddLocalFilesDialogStep.AddIndividuallyOrAsPlaylistQuery -> {
                    Box(modifier = backgroundModifier.padding(vertical = 16.dp),
                        // The vertical padding is set to match the TextField decoration box's
                        // vertical padding. This reduces the amount that the dialog box height
                        // has to be animated when switching between steps of the dialog.
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(stringResource(step.textResId))
                    }
                } is AddLocalFilesDialogStep.NameTracks -> {
                    // We have to restrict the LazyColumn's height to prevent
                    // a crash due to nested infinite height scrollables
                    val maxHeight = LocalConfiguration.current.screenHeightDp.dp
                    Column(backgroundModifier.heightIn(max = maxHeight)) {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(step.names.size) { index ->
                                TextField(
                                    value = step.names[index],
                                    onValueChange = { step.onNameChange(index, it) },
                                    textStyle = MaterialTheme.typography.body1,
                                    singleLine = true,
                                    isError = step.errors[index],
                                    modifier = Modifier.fillMaxWidth())
                            }
                        }
                        AnimatedValidatorMessage(step.message)
                    }
                } is AddLocalFilesDialogStep.NamePlaylist -> {
                    Column(backgroundModifier) {
                        TextField(
                            value = step.name,
                            onValueChange = step::onNameChange,
                            textStyle = MaterialTheme.typography.body1,
                            singleLine = true,
                            isError = step.message?.isError == true,
                            modifier = Modifier.fillMaxWidth())
                        AnimatedValidatorMessage(step.message)
                    }
                } is AddLocalFilesDialogStep.PlaylistOptions-> {
                    // PlaylistOptions already has its own horizontal padding, so we avoid
                    // using backgroundModifier here to prevent doubling up on the padding
                    Column(Modifier.background(MaterialTheme.colors.surface)) {
                        PlaylistOptionsView(
                            shuffleEnabled = step.shuffleEnabled,
                            onShuffleClick = step.onShuffleSwitchClick,
                            mutablePlaylist = step.mutablePlaylist,
                            onAddButtonClick = null)
                    }
                }
            }
        }
    }
}