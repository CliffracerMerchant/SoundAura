/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.annotation.FloatRange
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cliffracertech.soundaura.ui.theme.SoundAuraTheme

/**
 * A pseudo-interface that contains callbacks for TrackView interactions.
 *
 * @param onPlayPauseButtonClick The callback that will be invoked when the play/pause button is clicked.
 * @param onVolumeChange The callback that will be invoked when the volume slider's value is changing.
 * @param onVolumeChangeFinished The callback that will be invoked when the volume slider's handle is released.
 * @param onRenameRequest The callback that will be invoked when a rename of the track is requested.
 * @param onDeleteRequest The callback that will be invoked when the deletion of the track is requested.
 */
class TrackViewCallback(
    val onPlayPauseButtonClick: (String) -> Unit = { _ -> },
    val onVolumeChange: (String, Float) -> Unit = { _, _ -> },
    val onVolumeChangeFinished: (String, Float) -> Unit = { _, _ -> },
    val onRenameRequest: (String, String) -> Unit = { _, _ -> },
    val onDeleteRequest: (String) -> Unit = { }
)

/**
 * A view that displays a play/pause icon, a title, a volume slider, and a
 * more options button for the provided Track Instance.
 *
 * @param track The Track instance that is being represented.
 * @param callback The TrackViewCallback that describes how to respond to user interactions.
 */
@Composable
fun TrackView(
    track: Track,
    callback: TrackViewCallback,
    modifier: Modifier = Modifier
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier.fillMaxWidth(1f).largeSurfaceBackground()
){
    val addRemoveContentDescription = stringResource(
        if (track.playing) R.string.remove_track_from_mix_description
        else               R.string.add_track_to_mix_description, track.name)
    AddRemoveButton(
        added = track.playing,
        contentDescription = addRemoveContentDescription,
        backgroundColor = MaterialTheme.colors.surface,
        tint = MaterialTheme.colors.primary,
        onClick = { callback.onPlayPauseButtonClick(track.uriString) })

    var volume by remember { mutableStateOf(track.volume) }

    Box(Modifier.weight(1f)) {
        Text(text = track.name, style = MaterialTheme.typography.h6,
             maxLines = 1, overflow = TextOverflow.Ellipsis,
             modifier = Modifier.padding(start = 1.dp, top = 6.dp)
                                .paddingFromBaseline(bottom = 48.dp))
        VolumeSlider(
            volume = volume,
            onVolumeChange = {
                volume = it
                callback.onVolumeChange(track.uriString, it)
            }, onVolumeChangeFinished = {
                callback.onVolumeChangeFinished(track.uriString, volume)
            }, modifier = Modifier.align(Alignment.BottomStart))
    }

    ItemMoreOptionsButton(
        itemName = track.name,
        onRenameRequest = { id -> callback.onRenameRequest(track.uriString, id) },
        onDeleteRequest = { callback.onDeleteRequest(track.uriString) })
}

/**
 * A slider to change a volume level.
 *
 * @param volume The current volume level, in the range of 0.0f to 1.0f
 * @param onVolumeChange The callback that will be invoked as the slider is being dragged
 * @param modifier The modifier for the slider
 * @param onVolumeChangeFinished The callback that will be invoked when the slider's
 *                               thumb has been released after a drag event
 */
@Composable fun VolumeSlider(
    @FloatRange(from=0.0, to=1.0)
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onVolumeChangeFinished: (() -> Unit)? = null,
) = Row(modifier, verticalAlignment = Alignment.CenterVertically) {

    Icon(imageVector = Icons.Default.VolumeUp,
         contentDescription = null,
         modifier = Modifier.size(20.dp),
         tint = MaterialTheme.colors.primary)
    GradientSlider(value = volume, onValueChange = onVolumeChange,
                   modifier = Modifier.padding(start = 2.dp, end = 0.dp),
                   onValueChangeFinished = onVolumeChangeFinished)
}

/**
 * A more options button for an item in a list view.
 *
 * ItemMoreOptionsButton displays as a overflow icon button that, when clicked,
 * opens an options menu for the item that in turn displays rename and delete
 * options. The user tapping one of these options will open an appropriate
 * dialog.
 *
 * @param itemName The name of the item that is being interacted with.
 * @param onRenameRequest The callback that will be invoked when the
 *     user requests through the rename dialog that they wish to change
 *     the item's name to the callback's string parameter.
 * @param onDeleteRequest The callback that will be invoked when the user
 *     requests through the delete dialog that they wish to delete the item.
 */
@Composable fun ItemMoreOptionsButton(
    itemName: String,
    onRenameRequest: (String) -> Unit,
    onDeleteRequest: () -> Unit,
) {
    var showingOptionsMenu by rememberSaveable { mutableStateOf(false) }
    var showingRenameDialog by rememberSaveable { mutableStateOf(false) }
    var showingDeleteDialog by rememberSaveable { mutableStateOf(false) }

    IconButton(onClick = { showingOptionsMenu = !showingOptionsMenu }) {
        val description = stringResource(R.string.item_options_button_description, itemName)
        Icon(imageVector = Icons.Default.MoreVert,
             tint = MaterialTheme.colors.primaryVariant,
             contentDescription = description)

        DropdownMenu(
            expanded = showingOptionsMenu,
            onDismissRequest = { showingOptionsMenu = false }
        ) {
            DropdownMenuItem(onClick = {
                showingRenameDialog = true
                showingOptionsMenu = false
            }) {
                Text(text = stringResource(R.string.rename_description),
                     style = MaterialTheme.typography.button)
            }
            DropdownMenuItem(onClick = {
                showingDeleteDialog = true
                showingOptionsMenu = false
            }) {
                Text(text = stringResource(R.string.remove_description),
                     style = MaterialTheme.typography.button)
            }
        }
    }

    if (showingRenameDialog)
        RenameDialog(itemName, { showingRenameDialog = false }, onRenameRequest)

    if (showingDeleteDialog)
        ConfirmRemoveDialog(itemName, { showingDeleteDialog = false }, onDeleteRequest)
}

@Preview @Composable
fun LightTrackViewPreview() = SoundAuraTheme(darkTheme =  false) {
    TrackView(Track("", "Track 1", volume = 0.5f), TrackViewCallback())
}

@Preview @Composable
fun DarkTrackViewPreview() = SoundAuraTheme(darkTheme =  true) {
    TrackView(Track("", "Track 1", true, volume = 0.5f), TrackViewCallback())
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