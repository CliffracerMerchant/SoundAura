/* This file is part of SoundObservatory, which is released under the
 * Apache License 2.0. See license.md in the project's root directory
 * or use an internet search engine to see the full license. */

package com.cliffracertech.soundobservatory

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * A pseudo-interface that contains callbacks for track view interactions.
 *
 * @param onPlayPauseButtonClick The callback that will be invoked when the user clicks the play/pause button.
 * @param onVolumeChange The callback that will be invoked when the user is changing the volume slider's value.
 * @param onVolumeChangeFinished The callback that will be invoked when the user has
 *     finished changing the volume slider's value by releasing the slider handle.
 * @param onRenameRequest The callback that will be invoked when the user requests a rename of the track.
 * @param onDeleteRequest The callback that will be invoked when the user requests that the track be deleted.
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
    callback: TrackViewCallback
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
        .fillMaxWidth(1f)
        .background(MaterialTheme.colors.surface, MaterialTheme.shapes.large)
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
        modifier = Modifier.height(66.dp).weight(1f),
        sliderPadding = PaddingValues(top = 28.dp)
    ) {
        Text(text = track.name, style = MaterialTheme.typography.h6,
            modifier = Modifier.padding(8.dp, 6.dp, 0.dp, 0.dp))
    }

    ItemMoreOptionsButton(
        itemName = track.name,
        onRenameRequest = { id -> callback.onRenameRequest(track.uriString, id) },
        onDeleteRequest = { callback.onDeleteRequest(track.uriString) })
}

@Composable fun PlayPauseIcon(
    playing: Boolean,
    contentDescription: String,
    tint: Color = LocalContentColor.current.copy(alpha = LocalContentAlpha.current)
) {
    val playToPause = animatedVectorResource(R.drawable.play_to_pause).painterFor(playing)
    val pauseToPlay = animatedVectorResource(R.drawable.pause_to_play).painterFor(!playing)
    val vector = if (playing) playToPause
                 else         pauseToPlay
    Icon(vector, contentDescription, tint = tint)
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

