/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.annotation.FloatRange
import androidx.compose.animation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cliffracertech.soundaura.ui.theme.SoundAuraTheme
import kotlin.math.roundToInt

/** A collection of callbacks for [TrackView] interactions. The first parameter
 * for each of the callbacks is the uri of the track in [String] form. */
interface TrackViewCallback {
    /** The callback that will be invoked when the add/remove button is clicked */
    fun onAddRemoveButtonClick(uri: String)
    /** The callback that will be invoked when the volume slider's value is changing */
    fun onVolumeChange(uri: String, volume: Float)
    /** The callback that will be invoked when the volume slider's handle is released */
    fun onVolumeChangeFinished(uri: String, volume: Float)
    /** The callback that will be invoked when a rename of the track is requested */
    fun onRenameRequest(uri: String, name: String)
    /** The callback that will be invoked when the deletion of the track is requested */
    fun onDeleteRequest(uri: String)
}

/** Return a remembered [TrackViewCallback] implementation. */
@Composable fun rememberTrackViewCallback(
    onAddRemoveButtonClick: (String) -> Unit = { _ -> },
    onVolumeChange: (String, Float) -> Unit = { _, _ -> },
    onVolumeChangeFinished: (String, Float) -> Unit = { _, _ -> },
    onRenameRequest: (String, String) -> Unit = { _, _ -> },
    onDeleteRequest: (String) -> Unit = { }
) = remember { object: TrackViewCallback {
    override fun onAddRemoveButtonClick(uri: String) = onAddRemoveButtonClick(uri)
    override fun onVolumeChange(uri: String, volume: Float) = onVolumeChange(uri, volume)
    override fun onVolumeChangeFinished(uri: String, volume: Float) = onVolumeChangeFinished(uri, volume)
    override fun onRenameRequest(uri: String, name: String) = onRenameRequest(uri, name)
    override fun onDeleteRequest(uri: String) = onDeleteRequest(uri)
}}

/**
 * A view that displays an add/remove button, a title, a volume slider, and a
 * more options button for the provided [Track] Instance. If the track's hasError
 * field is true, then an error icon will be displayed instead of the add/remove
 * button, an error message will be displayed in place of the volume slider, and
 * the more options menu button will be replaced by a delete icon. The more
 * options button will also be replaced by a numerical display of the track's
 * volume when the volume slider is being dragged.
 *
 * @param track The [Track] instance that is being represented.
 * @param callback The [TrackViewCallback] that describes how to respond to user interactions.
 */
@Composable fun TrackView(
    track: Track,
    callback: TrackViewCallback,
    modifier: Modifier = Modifier
) = Surface(modifier, MaterialTheme.shapes.large) {
    Row(verticalAlignment = Alignment.CenterVertically) {
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

        val volumeSliderInteractionSource = remember { MutableInteractionSource() }
        var volumeSliderValue by remember(track.volume) { mutableStateOf(track.volume) }
        val volumeSliderIsBeingPressed by volumeSliderInteractionSource.collectIsPressedAsState()
        val volumeSliderIsBeingDragged by volumeSliderInteractionSource.collectIsDraggedAsState()

        Box(Modifier.weight(1f)) {
            // 1dp start padding is required to make the text align with the volume icon
            MarqueeText(text = track.name, style = MaterialTheme.typography.h5,
                        modifier = Modifier.padding(start = 1.dp, top = 6.dp)
                                           .paddingFromBaseline(bottom = 48.dp))
            VolumeSliderOrErrorMessage(
                volume = volumeSliderValue,
                onVolumeChange = { volume ->
                    volumeSliderValue = volume
                    callback.onVolumeChange(track.uriString, volume)
                }, onVolumeChangeFinished = {
                    callback.onVolumeChangeFinished(track.uriString, volumeSliderValue)
                }, modifier = Modifier.align(Alignment.BottomStart),
                sliderInteractionSource = volumeSliderInteractionSource,
                errorMessage = if (!track.hasError) null else
                    stringResource(R.string.file_error_message))
        }
        TrackViewEndContentImpl(
            content = when {
                track.hasError ->
                    TrackViewEndContent.DeleteButton
                volumeSliderIsBeingPressed || volumeSliderIsBeingDragged ->
                    TrackViewEndContent.VolumeDisplay
                else ->
                    TrackViewEndContent.MoreOptionsButton
            }, itemName = track.name,
            onRenameRequest = { callback.onRenameRequest(track.uriString, it) },
            onDeleteRequest = { callback.onDeleteRequest(track.uriString) },
            volume = volumeSliderValue,
            tint = MaterialTheme.colors.secondaryVariant)
    }
}

/**
 * Compose either an add/remove button or an error icon. The error icon can be
 * shown when some error prevents the added/removed state from being changed.
 *
 * @param showError Whether an error icon will be shown instead of the
 *     add/remove button.
 * @param isAdded Whether the add/remove button will display itself as already
 *     added when it is shown at all.
 * @param contentDescription The content description that will be used for the
 *     error icon or add/remove button.
 * @param backgroundColor The color that is being displayed behind the
 *     [AddRemoveButtonOrErrorIcon]. This is used so that the button can appear
 *     empty when isAdded is false.
 * @param onAddRemoveClick The callback that will be invoked when [showError] is
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
        tint = MaterialTheme.colors.primaryVariant,
        onClick = onAddRemoveClick)
}

/**
 * Compose a slider to change a volume level or an error message
 * that explains why the volume level can not be changed.
 *
 * @param volume The current volume level, in the range of 0.0f to 1.0f.
 * @param onVolumeChange The callback that will be invoked as the slider
 *     is being dragged.
 * @param modifier The modifier for the slider/error message.
 * @param sliderInteractionSource The MutableInteractionSource that will
 *     be used for the volume slider. If not provided, will default to a
 *     remembered new instance of MutableInteractionSource.
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
    sliderInteractionSource: MutableInteractionSource =
        remember { MutableInteractionSource() },
    errorMessage: String? = null,
    onVolumeChangeFinished: (() -> Unit)? = null,
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Default.VolumeUp,
                 contentDescription = null,
                 modifier = Modifier.size(20.dp),
                 tint = MaterialTheme.colors.primaryVariant)
            GradientSlider(
                value = volume,
                onValueChange = onVolumeChange,
                onValueChangeFinished = { onVolumeChangeFinished?.invoke() },
                interactionSource = sliderInteractionSource,
                colors = GradientSliderDefaults.colors(
                    thumbColor = MaterialTheme.colors.primaryVariant,
                    thumbColorEnd = MaterialTheme.colors.secondaryVariant,
                    activeTrackBrush = Brush.horizontalGradient(listOf(
                        MaterialTheme.colors.primaryVariant,
                        MaterialTheme.colors.secondaryVariant))
                ))

        }
    }
}

/** An enum detailing the possible content for the end of a [TrackView]'s layout. */
enum class TrackViewEndContent {
    /** The TrackView will display a more options
     * button that opens a popup menu when clicked. */
    MoreOptionsButton,
    /** The TrackView will display a delete button that calls
     * the TrackView's callback's onDeleteRequest when clicked. */
    DeleteButton,
    /** The TrackView will display the track's volume
     * as an integer string in the range of 0 - 100. */
    VolumeDisplay
}

/**
 * Compose the end content for a TrackView. This content will change
 * according to the value of the parameter content to match one of
 * the possible values for the TrackViewEndContent enum.
 *
 * @param content The value of TrackViewEndContent that describes what
 *     will be displayed. The visible content will be crossfaded between
 *     when this value changes.
 * @param itemName The name of the item that is being interacted with
 * @param onRenameRequest The callback that will be invoked when the
 *     user requests through the rename dialog that they wish to change
 *     the item's name to the callback's string parameter
 * @param onDeleteRequest The callback that will be invoked when the user
 *     requests through the delete dialog that they wish to delete the
 *     item, or when showAsDelete is true and the button is clicked
 * @param tint The tint that will be used for the more options button
 *     and the volume display. The delete button will use the value
 *     of the local theme's MaterialTheme.colors.error value instead.
 */
@Composable fun TrackViewEndContentImpl(
    content: TrackViewEndContent,
    itemName: String,
    onRenameRequest: (String) -> Unit,
    onDeleteRequest: () -> Unit,
    @FloatRange(from=0.0, to=1.0)
    volume: Float,
    tint: Color = LocalContentColor.current,
) = Crossfade(content) { when(it) {
    TrackViewEndContent.MoreOptionsButton -> {
        var showingOptionsMenu by rememberSaveable { mutableStateOf(false) }
        IconButton({ showingOptionsMenu = true }) {
            Icon(imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(
                    R.string.item_options_button_description, itemName),
                tint = tint)
        }

        var showingRenameDialog by rememberSaveable { mutableStateOf(false) }
        var showingDeleteDialog by rememberSaveable { mutableStateOf(false) }
        DropdownMenu(
            expanded = showingOptionsMenu,
            onDismissRequest = { showingOptionsMenu = false }
        ) {
            DropdownMenuItem(onClick = {
                showingRenameDialog = true
                showingOptionsMenu = false
            }) {
                Text(stringResource(R.string.rename))
            }
            DropdownMenuItem(onClick = {
                showingDeleteDialog = true
                showingOptionsMenu = false
            }) {
                Text(stringResource(R.string.remove))
            }
        }

        if (showingRenameDialog)
            TrackRenameDialog(itemName, { showingRenameDialog = false }, onRenameRequest)
        if (showingDeleteDialog)
            ConfirmRemoveDialog(itemName, { showingDeleteDialog = false }, onDeleteRequest)
    }
    TrackViewEndContent.VolumeDisplay -> {
        Box(Modifier.minTouchTargetSize(), Alignment.Center) {
            Text(text = (volume * 100).roundToInt().toString(),
                color = tint,
                style = MaterialTheme.typography.subtitle2)
        }
    }
    TrackViewEndContent.DeleteButton -> {
        IconButton(onDeleteRequest) {
            Icon(imageVector = Icons.Default.Delete,
                contentDescription = stringResource(
                    R.string.remove_item_description, itemName),
                tint = MaterialTheme.colors.error)
        }
    }
}}

@Composable fun TrackRenameDialog(
    itemName: String,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var currentName by rememberSaveable { mutableStateOf(itemName) }
    val errorMessage = if (currentName.isNotBlank()) null
                       else stringResource(R.string.track_name_cannot_be_blank_error_message)
    RenameDialog(
        initialName = itemName,
        proposedNameProvider = { currentName },
        onProposedNameChange = { currentName = it },
        errorMessageProvider = { errorMessage },
        onDismissRequest = onDismissRequest,
        onConfirm = {
            onConfirm(currentName)
            onDismissRequest()
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

@Preview @Composable
fun LightTrackViewPreview() = SoundAuraTheme(darkTheme = false) {
    TrackView(
        callback = rememberTrackViewCallback(),
        track = Track(
            uriString = "",
            name = "Track 1",
            volume = 0.5f))
    }

@Preview(showBackground = true) @Composable
fun DarkTrackViewPreview() = SoundAuraTheme(darkTheme = true) {
    TrackView(
        callback = rememberTrackViewCallback(),
        track = Track(
            uriString = "",
            name = "Track 2",
            isActive = true,
            volume = 0.25f))
}

@Preview @Composable
fun LightTrackErrorPreview() = SoundAuraTheme(darkTheme = false) {
    TrackView(
        callback = rememberTrackViewCallback(),
        track = Track(
            uriString = "",
            name = "Track 3",
            volume = 1.00f,
            hasError = true))
}

@Preview(showBackground = true) @Composable
fun DarkTrackErrorPreview() = SoundAuraTheme(darkTheme = true) {
    TrackView(
        callback = rememberTrackViewCallback(),
        track = Track(
            uriString = "",
            name = "Track 4",
            volume = 1.00f,
            hasError = true))
}