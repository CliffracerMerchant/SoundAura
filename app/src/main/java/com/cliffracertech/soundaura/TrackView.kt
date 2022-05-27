/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.annotation.FloatRange
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
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
 * A pseudo-interface that contains callbacks for TrackView interactions. The
 * first parameter for each of the callbacks is the uri of the track in String
 * form.
 *
 * @param onAddRemoveButtonClick The callback that will be invoked when the add/remove button is clicked.
 * @param onVolumeChange The callback that will be invoked when the volume slider's value is changing.
 * @param onVolumeChangeFinished The callback that will be invoked when the volume slider's handle is released.
 * @param onRenameRequest The callback that will be invoked when a rename of the track is requested.
 * @param onDeleteRequest The callback that will be invoked when the deletion of the track is requested.
 */
class TrackViewCallback(
    val onAddRemoveButtonClick: (String) -> Unit = { _ -> },
    val onVolumeChange: (String, Float) -> Unit = { _, _ -> },
    val onVolumeChangeFinished: (String, Float) -> Unit = { _, _ -> },
    val onRenameRequest: (String, String) -> Unit = { _, _ -> },
    val onDeleteRequest: (String) -> Unit = { }
)

/**
 * A view that displays an add/remove button, a title, a volume slider, and a
 * more options button for the provided Track Instance. If the track's hasError
 * field is true, then an error icon will be displayed instead of the add/remove
 * button.
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
    modifier = modifier.fillMaxWidth().largeSurfaceBackground()
){
    AddRemoveButtonOrErrorIcon(
        showError = track.hasError,
        isAdded = track.isActive,
        contentDescription = if (track.hasError) null else {
            val id = if (track.isActive)
                         R.string.remove_track_from_mix_description
                     else R.string.add_track_to_mix_description
            stringResource(id, track.name)
        }, onAddRemoveClick = {
            callback.onAddRemoveButtonClick(track.uriString)
        })

    Box(Modifier.weight(1f)) {
        // 0.5dp start padding is required to make the text align with the volume icon
        Text(text = track.name, style = MaterialTheme.typography.h6,
             maxLines = 1, overflow = TextOverflow.Ellipsis,
             modifier = Modifier.padding(start = (0.5).dp, top = 6.dp)
                                .paddingFromBaseline(bottom = 48.dp))
        VolumeSliderOrErrorMessage(
            volume = track.volume,
            onVolumeChange = { volume ->
                callback.onVolumeChange(track.uriString, volume)
            }, onVolumeChangeFinished = { volume ->
                callback.onVolumeChangeFinished(track.uriString, volume)
            }, modifier = Modifier.align(Alignment.BottomStart),
            errorMessage = if (!track.hasError) null else
                stringResource(R.string.file_error_message))
    }

    ItemMoreOptionsButton(
        itemName = track.name,
        onRenameRequest = { id -> callback.onRenameRequest(track.uriString, id) },
        onDeleteRequest = { callback.onDeleteRequest(track.uriString) })
}

/**
 * Compose either an add/remove button or an error icon. The error icon can be
 * shown when some error prevents the add/removed state from being changed.
 *
 * @param showError Whether an error icon will be shown instead of the
 *     add/remove button.
 * @param isAdded Whether the add/remove button will display itself as already
 *     added when it is shown at all.
 * @param contentDescription The content description that will be used for the
 *     error icon or add/remove button.
 * @param backgroundColor The color that is being displayed behind the
 *     AddRemoveButtonOrErrorIcon. This is used so that the button can appear
 *     empty when isAdded is false.
 * @param onAddRemoveClick The callback that will be invoked when showError is
 *     false and the add/remove button is clicked.
 */
@Composable fun AddRemoveButtonOrErrorIcon(
    showError: Boolean,
    isAdded: Boolean,
    contentDescription: String? = null,
    backgroundColor: Color = MaterialTheme.colors.surface,
    onAddRemoveClick: () -> Unit
) = AnimatedContent(showError) {
    if (it) Icon(
        imageVector = Icons.Default.Error,
        contentDescription = contentDescription,
        modifier = Modifier.size(48.dp).padding(10.dp),
        tint = MaterialTheme.colors.error)
    else AddRemoveButton(
        added = isAdded,
        contentDescription = contentDescription,
        backgroundColor = backgroundColor,
        tint = MaterialTheme.colors.primary,
        onClick = onAddRemoveClick)
}

/**
 * Compose a slider to change a volume level or an error message
 * that explains why the volume level can not be changed.
 *
 * @param volume The current volume level, in the range of 0.0f to 1.0f.
 * @param onVolumeChange The callback that will be invoked as the slider
 *      is being dragged.
 * @param modifier The modifier for the slider/error message.
 * @param errorMessage The error message that will be shown
 *     if not null instead of the volume slider.
 * @param onVolumeChangeFinished The callback that will be invoked
 *     when the slider's thumb has been released after a drag event.
 */
@Composable fun VolumeSliderOrErrorMessage(
    @FloatRange(from=0.0, to=1.0)
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
    onVolumeChangeFinished: ((Float) -> Unit)? = null,
) = AnimatedContent(
    targetState = errorMessage != null,
    modifier = modifier,
    transitionSpec = { fadeIn() with fadeOut() },
) { hasError ->
    if (hasError) {
        // 0.5dp start padding is required to make the text
        // align with where the volume icon would appear if
        // there were no error message.
        Text(text = errorMessage ?: "",
             color = MaterialTheme.colors.error,
             style = MaterialTheme.typography.body1,
             maxLines = 1, overflow = TextOverflow.Ellipsis,
             modifier = Modifier.padding(start = (0.5).dp)
                                .paddingFromBaseline(bottom = 18.dp))
    } else {
        var currentVolume by remember(volume) { mutableStateOf(volume) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Default.VolumeUp,
                 contentDescription = null,
                 modifier = Modifier.size(20.dp),
                 tint = MaterialTheme.colors.primary)
            GradientSlider(
                value = currentVolume,
                onValueChange = {
                    currentVolume = it
                    onVolumeChange(it)
                }, onValueChangeFinished = {
                    onVolumeChangeFinished?.invoke(currentVolume)
                })
        }
    }
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
             tint = MaterialTheme.colors.secondary,
             contentDescription = description)

        DropdownMenu(
            expanded = showingOptionsMenu,
            onDismissRequest = { showingOptionsMenu = false }
        ) {
            DropdownMenuItem(onClick = {
                showingRenameDialog = true
                showingOptionsMenu = false
            }) {
                Text(text = stringResource(R.string.rename),
                     style = MaterialTheme.typography.button)
            }
            DropdownMenuItem(onClick = {
                showingDeleteDialog = true
                showingOptionsMenu = false
            }) {
                Text(text = stringResource(R.string.remove),
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
        confirmText = stringResource(R.string.rename),
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
    confirmText = stringResource(R.string.remove),
    onConfirm = {
        onConfirm()
        onDismissRequest()
    })