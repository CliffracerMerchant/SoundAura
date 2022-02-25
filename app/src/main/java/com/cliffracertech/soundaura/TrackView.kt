/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

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
    val onPlayPauseButtonClick: (String, Boolean) -> Unit = { _, _ -> },
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
    PlayPauseButton(track.playing, track.name, MaterialTheme.colors.primary) {
        callback.onPlayPauseButtonClick(track.uriString, !track.playing)
    }

    var volume by remember { mutableStateOf(track.volume) }
    SliderBox(
        value = volume,
        onValueChange = {
            volume = it
            callback.onVolumeChange(track.uriString, it)
        },
        onValueChangeFinished = { callback.onVolumeChangeFinished(track.uriString, volume) },
        modifier = Modifier.height(68.dp).weight(1f),
        sliderPadding = PaddingValues(top = 30.dp),
        sliderThumbContents = {
            Icon(contentDescription = null, imageVector = when {
                volume == 0f ->   Icons.Default.VolumeMute
                volume <= 0.5f -> Icons.Default.VolumeDown
                else ->           Icons.Default.VolumeUp
            }, tint = MaterialTheme.colors.surface)
        }
    ) {
        Text(text = track.name, style = MaterialTheme.typography.h6,
             maxLines = 1, overflow = TextOverflow.Ellipsis,
             modifier = Modifier.padding(8.dp, 8.dp, 0.dp, 0.dp))
    }

    ItemMoreOptionsButton(
        itemName = track.name,
        onRenameRequest = { id -> callback.onRenameRequest(track.uriString, id) },
        onDeleteRequest = { callback.onDeleteRequest(track.uriString) })
}

@Composable fun PlayPauseIcon(
    playing: Boolean,
    contentDescription: String =
        if (playing) stringResource(R.string.pause_description)
        else         stringResource(R.string.play_description),
    tint: Color = LocalContentColor.current.copy(alpha = LocalContentAlpha.current)
) {
    val playToPause = AnimatedImageVector.animatedVectorResource(R.drawable.play_to_pause)
    val playToPausePainter = rememberAnimatedVectorPainter(playToPause, atEnd = playing)
    val pauseToPlay = AnimatedImageVector.animatedVectorResource(R.drawable.pause_to_play)
    val pauseToPlayPainter = rememberAnimatedVectorPainter(pauseToPlay, atEnd = !playing)
    Icon(painter = if (playing) playToPausePainter
                   else         pauseToPlayPainter,
         contentDescription = contentDescription,
         tint = tint)
}

/**
 * A button that alternates between a pause icon and a playing icon depending on
 * the parameter playing.
 *
 * @param playing The playing/paused state of the button. If playing == true,
 *     the button will display a pause icon. If playing == false, a play icon
 *     will be displayed instead.
 * @param itemName The name of the item whose play/pause state is being
 *     manipulated. This is used for accessibility labeling.
 * @param tint The tint that will be used for the icon.
 * @param onClick The callback that will be invoked when the button is clicked.
 */
@Composable fun PlayPauseButton(
    playing: Boolean,
    itemName: String,
    tint: Color = LocalContentColor.current.copy(alpha = LocalContentAlpha.current),
    onClick: () -> Unit
) = IconButton(onClick) {
    val description = if (playing) stringResource(R.string.item_pause_description, itemName)
                      else         stringResource(R.string.item_play_description, itemName)
    PlayPauseIcon(playing, description, tint)
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
    var showingOptionsMenu by remember { mutableStateOf(false) }
    var showingRenameDialog by remember { mutableStateOf(false) }
    var showingDeleteDialog by remember { mutableStateOf(false) }

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
                Text(text = stringResource(R.string.item_rename_description),
                     style = MaterialTheme.typography.button)
            }
            DropdownMenuItem(onClick = {
                showingDeleteDialog = true
                showingOptionsMenu = false
            }) {
                Text(text = stringResource(R.string.item_delete_description),
                     style = MaterialTheme.typography.button)
            }
        }
    }

    if (showingRenameDialog)
        RenameDialog(itemName, { showingRenameDialog = false }, onRenameRequest)

    if (showingDeleteDialog)
        ConfirmDeleteDialog(itemName, { showingDeleteDialog = false }, onDeleteRequest)
}

@Preview(showBackground = true, backgroundColor = 0xFF00FF00)
@Composable fun TrackViewPreview() =
    TrackView(Track(uriString = "", name = "Track 1", volume = 0.5f), TrackViewCallback())