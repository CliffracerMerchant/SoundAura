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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import com.cliffracertech.soundaura.R
import com.cliffracertech.soundaura.dialog.AnimatedValidatorMessage
import com.cliffracertech.soundaura.dialog.SoundAuraDialog
import com.cliffracertech.soundaura.library.PlaylistOptions
import com.cliffracertech.soundaura.restrictWidthAccordingToSizeClass
import com.cliffracertech.soundaura.ui.HorizontalDivider
import com.cliffracertech.soundaura.ui.SlideAnimatedContent
import com.cliffracertech.soundaura.ui.TextButton
import com.cliffracertech.soundaura.ui.VerticalDivider
import com.cliffracertech.soundaura.ui.bottomEndShape
import com.cliffracertech.soundaura.ui.bottomStartShape
import com.cliffracertech.soundaura.ui.minTouchTargetSize

@Composable private fun AddLocalFilesDialogTitle(
    step: AddLocalFilesDialogStep
) = when (step) {
    is AddLocalFilesDialogStep.AddIndividuallyOrAsPlaylistQuery ->
        stringResource(R.string.add_local_files_dialog_title)
    is AddLocalFilesDialogStep.NameTracks ->
        stringResource(R.string.add_local_files_as_tracks_dialog_title)
    is AddLocalFilesDialogStep.NamePlaylist ->
        stringResource(R.string.add_local_files_as_playlist_dialog_title)
    is AddLocalFilesDialogStep.PlaylistOptions ->
        stringResource(R.string.configure_playlist_dialog_title)
    is AddLocalFilesDialogStep.SelectingFiles -> ""
}

@Composable private fun ColumnScope.AddLocalFilesDialogButtons(
    step: AddLocalFilesDialogStep
) {
    HorizontalDivider(Modifier.padding(top = 12.dp))
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
        if (step is AddLocalFilesDialogStep.AddIndividuallyOrAsPlaylistQuery) {
            TextButton(
                modifier = Modifier.minTouchTargetSize().weight(1f),
                shape = MaterialTheme.shapes.medium.bottomStartShape(),
                textResId = R.string.cancel,
                onClick = step::onBackClick)

            VerticalDivider()
            TextButton(
                modifier = Modifier.minTouchTargetSize().weight(1f),
                shape = RectangleShape,
                textResId = R.string.add_local_files_individually_option,
                onClick = step.onAddIndividuallyClick)

            VerticalDivider()
            TextButton(
                modifier = Modifier.minTouchTargetSize().weight(1f),
                shape = MaterialTheme.shapes.medium.bottomEndShape(),
                textResId = R.string.add_local_files_as_playlist_option,
                onClick = step.onAddAsPlaylistClick)
        } else {
            val namingSingleTrack = step is AddLocalFilesDialogStep.NameTracks &&
                    step.namesAndErrors.size == 1
            TextButton(
                modifier = Modifier.minTouchTargetSize().weight(1f),
                shape = MaterialTheme.shapes.medium.bottomStartShape(),
                textResId = if (namingSingleTrack) R.string.cancel
                            else                   R.string.back,
                onClick = step::onBackClick)

            VerticalDivider()

            val nextButtonText =
                if (step.isPlaylistOptions || step.isNameTracks) R.string.finish
                else                                             R.string.next
            val nextButtonEnabled =
                if (step is AddLocalFilesDialogStep.NameTracks)
                    step.message?.isError ?: true
                else if (step is AddLocalFilesDialogStep.NamePlaylist)
                    step.message?.isError ?: true
                else true
            TextButton(
                modifier = Modifier.minTouchTargetSize().weight(1f),
                enabled = nextButtonEnabled,
                shape = MaterialTheme.shapes.medium.bottomStartShape(),
                textResId = nextButtonText,
                onClick = step::onNextClick)
        }
    }
}

@Composable private fun AddLocalFilesDialogContent(
    step: AddLocalFilesDialogStep
) = SlideAnimatedContent(
    targetState = step,
    leftToRight = (step as? AddLocalFilesDialogStep.NamePlaylist)?.goingForward != false,
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
                Text(stringResource(R.string.add_local_files_as_playlist_or_tracks_question))
            }
        } is AddLocalFilesDialogStep.NameTracks -> {
        // We have to restrict the LazyColumn's height to prevent
        // a crash due to nested infinite height scrollables
            val maxHeight = LocalConfiguration.current.screenHeightDp.dp
            LazyColumn(
                modifier = backgroundModifier.heightIn(max = maxHeight),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = {
                    items(step.namesAndErrors.size) { index ->
                        val value = step.namesAndErrors.getOrNull(index)
                        TextField(
                            value = value?.first ?: "",
                            onValueChange = { step.onNameChange(index, it) },
                            textStyle = MaterialTheme.typography.body1,
                            singleLine = true,
                            isError = value?.second ?: false,
                            modifier = Modifier.fillMaxWidth())
                    }})
        } is AddLocalFilesDialogStep.NamePlaylist -> {
            Column(backgroundModifier) {
                TextField(
                    value = step.name,
                    onValueChange = step::onNameChange,
                    textStyle = MaterialTheme.typography.body1,
                    singleLine = true,
                    isError = step.message?.isError == true,
                    modifier = Modifier.fillMaxWidth())
                AnimatedValidatorMessage(
                    message = step.message,
                    modifier = Modifier.padding(horizontal = 16.dp))
            }
        } is AddLocalFilesDialogStep.PlaylistOptions-> {
            Column(backgroundModifier) {
                PlaylistOptions(
                    shuffleEnabled = step.shuffleEnabled,
                    tracks = step.trackOrder,
                    onShuffleSwitchClick = step::onShuffleSwitchClick,
                    modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * When [FileChooser] enters the composition, a system file picker
 * will be shown to allow the user to pick one or more files.
 *
 * @param fileTypeArgs An [Array] of [String]s that describes which
 *     file MIME types are allowed to be chosen
 * @param onFilesSelected The callback that will be invoked when one
 *     or more files are picked, represented in the [List] argument
 */
@Composable fun FileChooser(
    fileTypeArgs: Array<String> = arrayOf("audio/*", "application/ogg"),
    onFilesSelected: (List<Uri>) -> Unit,
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = onFilesSelected)
    LaunchedEffect(Unit) { launcher.launch(fileTypeArgs) }
}

/**
 * Open a dialog for the user to select one or more audio files to add
 * to their library.
 *
 * @param step A [AddLocalFilesDialogStep] instance that represents the
 *     current step of the dialog
 * @param onDismissRequest The callback that will be invoked when the user
 *     clicks outside the dialog or taps the cancel button
 */
@Composable fun AddLocalFilesDialog(
    step: AddLocalFilesDialogStep,
    onDismissRequest: () -> Unit,
) {
    if (step is AddLocalFilesDialogStep.SelectingFiles) {
        FileChooser { uris ->
            if (uris.isEmpty())
                onDismissRequest()
            step.onFilesSelected(uris)
        }
    } else SoundAuraDialog(
        modifier = Modifier.restrictWidthAccordingToSizeClass(),
        useDefaultWidth = false,
        title = AddLocalFilesDialogTitle(step),
        onDismissRequest = onDismissRequest,
        buttons = { AddLocalFilesDialogButtons(step) },
        content = { AddLocalFilesDialogContent(step) })
}