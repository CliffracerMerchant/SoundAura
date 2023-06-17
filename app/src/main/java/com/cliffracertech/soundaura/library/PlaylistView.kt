/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.library

import androidx.annotation.FloatRange
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.with
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.paddingFromBaseline
import androidx.compose.foundation.layout.size
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cliffracertech.soundaura.MarqueeText
import com.cliffracertech.soundaura.R
import com.cliffracertech.soundaura.minTouchTargetSize
import com.cliffracertech.soundaura.ui.theme.SoundAuraTheme
import kotlin.math.roundToInt

/**
 * A data class whose values describe the attributes of a playlist in a library of playlists.
 *
 * @param name The name of the playlist
 * @param isActive Whether or not the playlist is currently playing
 * @param volume The volume of the playlist
 * @param hasError Whether or not there is an error (e.g. a playback
 *     problem) with the playlist
 * @param isSingleTrack Whether or not the playlist has only one track.
 *     This value can be used in case single-track playlists need to
 *     appear differently from multi-track playlists (e.g. by referring
 *     to them as 'tracks' instead of 'playlists').
 */
data class Playlist(
    val name: String,
    val isActive: Boolean,
    val volume: Float,
    val hasError: Boolean,
    val isSingleTrack: Boolean)

/** A collection of callbacks for [PlaylistView] interactions. The first parameter
 * for each of the callbacks is the [Playlist.name] for the [Playlist]. */
interface PlaylistViewCallback {
    /** The callback that will be invoked when the add/remove button is clicked */
    fun onAddRemoveButtonClick(playlist: Playlist)
    /** The callback that will be invoked when the volume slider's value is changing */
    fun onVolumeChange(playlist: Playlist, volume: Float)
    /** The callback that will be invoked when the volume slider's handle is released */
    fun onVolumeChangeFinished(playlist: Playlist, volume: Float)
    /** The callback that will be invoked when the 'rename'
     * option of the playlist's options menu is clicked */
    fun onRenameClick(playlist: Playlist)
    /** The callback that will be invoked when the 'playlist options' or
     * 'create playlist' option of the playlist's options menu is clicked */
    fun onExtraOptionsClick(playlist: Playlist)
    /** The callback that will be invoked when the 'remove'
     * option of the playlist's options menu is clicked */
    fun onRemoveClick(playlist: Playlist)
}

/** Return a remembered [PlaylistViewCallback] implementation. */
@Composable fun rememberPlaylistViewCallback(
    onAddRemoveButtonClick: (Playlist) -> Unit = { _ -> },
    onVolumeChange: (Playlist, Float) -> Unit = { _, _ -> },
    onVolumeChangeFinished: (Playlist, Float) -> Unit = { _, _ -> },
    onRenameClick: (Playlist) -> Unit = {},
    onExtraOptionsClick: (Playlist) -> Unit = {},
    onRemoveClick: (Playlist) -> Unit = {}
) = remember { object: PlaylistViewCallback {
    override fun onAddRemoveButtonClick(playlist: Playlist) = onAddRemoveButtonClick(playlist)
    override fun onVolumeChange(playlist: Playlist, volume: Float) = onVolumeChange(playlist, volume)
    override fun onVolumeChangeFinished(playlist: Playlist, volume: Float) = onVolumeChangeFinished(playlist, volume)
    override fun onRenameClick(playlist: Playlist) = onRenameClick(playlist)
    override fun onExtraOptionsClick(playlist: Playlist) = onExtraOptionsClick(playlist)
    override fun onRemoveClick(playlist: Playlist) = onRemoveClick(playlist)
}}

/**
 * A view that displays an add/remove button, a title, a volume slider, and a
 * more options button for the provided [Playlist] Instance. If the [Playlist.hasError]
 * field is true, then an error icon will be displayed instead of the add/remove
 * button, an error message will be displayed in place of the volume slider, and
 * the more options menu button will be replaced by a delete icon. The more
 * options button will also be replaced by a numerical display of the track's
 * volume when the volume slider is being dragged.
 *
 * @param playlist The [Playlist] instance that is being represented
 * @param callback The [PlaylistViewCallback] that describes how to respond to user interactions
 */
@Composable fun PlaylistView(
    playlist: Playlist,
    callback: PlaylistViewCallback,
    modifier: Modifier = Modifier
) = Surface(modifier, MaterialTheme.shapes.large) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AddRemoveButtonOrErrorIcon(
            showError = playlist.hasError,
            isAdded = playlist.isActive,
            contentDescription = if (playlist.hasError) null else {
                val id = if (playlist.isActive)
                             R.string.remove_track_from_mix_description
                         else R.string.add_track_to_mix_description
                stringResource(id, playlist.name)
            }, onAddRemoveClick = {
                callback.onAddRemoveButtonClick(playlist)
            })

        val volumeSliderInteractionSource = remember { MutableInteractionSource() }
        var volumeSliderValue by remember(playlist.volume) { mutableStateOf(playlist.volume) }
        val volumeSliderIsBeingPressed by volumeSliderInteractionSource.collectIsPressedAsState()
        val volumeSliderIsBeingDragged by volumeSliderInteractionSource.collectIsDraggedAsState()

        Box(Modifier.weight(1f)) {
            // 1dp start padding is required to make the text align with the volume icon
            MarqueeText(text = playlist.name, style = MaterialTheme.typography.h5,
                        modifier = Modifier.padding(start = 1.dp, top = 6.dp)
                                           .paddingFromBaseline(bottom = 48.dp))
            VolumeSliderOrErrorMessage(
                volume = volumeSliderValue,
                onVolumeChange = { volume ->
                    volumeSliderValue = volume
                    callback.onVolumeChange(playlist, volume)
                }, onVolumeChangeFinished = {
                    callback.onVolumeChangeFinished(playlist, volumeSliderValue)
                }, modifier = Modifier.align(Alignment.BottomStart),
                sliderInteractionSource = volumeSliderInteractionSource,
                errorMessage = if (!playlist.hasError) null else
                    stringResource(R.string.file_error_message))
        }
        PlaylistViewEndContent(
            content = when {
                playlist.hasError ->
                    PlaylistViewEndContentType.DeleteButton
                volumeSliderIsBeingPressed || volumeSliderIsBeingDragged ->
                    PlaylistViewEndContentType.VolumeDisplay
                else ->
                    PlaylistViewEndContentType.MoreOptionsButton
            }, playlist = playlist,
            volume = volumeSliderValue,
            onRenameClick = { callback.onRenameClick(playlist) },
            onPlaylistOptionsClick = { callback.onExtraOptionsClick(playlist) },
            onRemoveClick = { callback.onRemoveClick(playlist) },
            tint = MaterialTheme.colors.secondaryVariant)
    }
}

/**
 * Show either an add/remove button or an error icon. The error icon can be
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
@Composable private fun AddRemoveButtonOrErrorIcon(
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
 * Show a slider to change a volume level or an error message
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
@Composable private fun VolumeSliderOrErrorMessage(
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
    } else Row(verticalAlignment = Alignment.CenterVertically) {
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

/** An enum detailing the possible content for the end of a [PlaylistView]'s layout. */
private enum class PlaylistViewEndContentType {
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
 * Show the end content for a [PlaylistView]. This content will change
 * according to the value of the parameter [content] to match one of
 * the possible values for the [PlaylistViewEndContentType] enum.
 *
 * @param content The value of [PlaylistViewEndContentType] that describes
 *     what will be displayed. The visible content will be cross-faded
 *     between when this value changes.
 * @param playlist The [Playlist] that is being interacted with
 * @param volume The volume of the [Playlist] that is being interacted with
 * @param onRenameClick The callback that will be invoked when the
 *     playlist's rename option is clicked
 * @param onPlaylistOptionsClick The callback that will be invoked when a
 *     multi-track playlist's 'playlist options' option is clicked or a
 *     single-track playlist's 'create playlist' option is clicked
 * @param onRemoveClick The callback that will be invoked when the playlist's
 *     remove option is clicked, or when the [content] parameter is equal
 *     to [PlaylistViewEndContentType.DeleteButton] and the delete button
 *     is clicked
 * @param tint The tint that will be used for the more options button
 *     and the volume display. The delete button will use the value
 *     of the local theme's MaterialTheme.colors.error value instead.
 */
@Composable private fun PlaylistViewEndContent(
    content: PlaylistViewEndContentType,
    playlist: Playlist,
    @FloatRange(from=0.0, to=1.0)
    volume: Float,
    onRenameClick: () -> Unit,
    onPlaylistOptionsClick: () -> Unit,
    onRemoveClick: () -> Unit,
    tint: Color = LocalContentColor.current,
) = Crossfade(content) { when(it) {
    PlaylistViewEndContentType.MoreOptionsButton -> {
        var showingOptionsMenu by remember {
            mutableStateOf(false)
        }
        IconButton({ showingOptionsMenu = true }) {
            Icon(imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(
                    R.string.item_options_button_description, playlist.name),
                tint = tint)
        }

        DropdownMenu(
            expanded = showingOptionsMenu,
            onDismissRequest = { showingOptionsMenu = false }
        ) {
            DropdownMenuItem(onClick = {
                showingOptionsMenu = false
                onRenameClick()
            }) {
                Text(stringResource(R.string.rename))
            }
            DropdownMenuItem(onClick = {
                showingOptionsMenu = false
                onPlaylistOptionsClick()
            }) {
                Text(stringResource(
                    if (playlist.isSingleTrack)
                        R.string.create_playlist_title
                    else R.string.playlist_options_title))
            }
            DropdownMenuItem({
                showingOptionsMenu = false
                onRemoveClick()
            }) {
                Text(stringResource(R.string.remove))
            }
        }
    }
    PlaylistViewEndContentType.VolumeDisplay -> {
        Box(Modifier.minTouchTargetSize(), Alignment.Center) {
            Text(text = (volume * 100).roundToInt().toString(),
                color = tint,
                style = MaterialTheme.typography.subtitle2)
        }
    }
    PlaylistViewEndContentType.DeleteButton -> {
        IconButton(onRemoveClick) {
            Icon(imageVector = Icons.Default.Delete,
                contentDescription = stringResource(
                    R.string.remove_item_description, playlist.name),
                tint = MaterialTheme.colors.error)
        }
    }
}}

@Preview @Composable
fun LightTrackViewPreview() = SoundAuraTheme(darkTheme = false) {
    PlaylistView(
        callback = rememberPlaylistViewCallback(),
        playlist = Playlist(
            name = "Track",
            isActive = false,
            volume = 0.5f,
            hasError = false,
            isSingleTrack = true))
}

@Preview(showBackground = true) @Composable
fun DarkTrackViewPreview() = SoundAuraTheme(darkTheme = true) {
    PlaylistView(
        callback = rememberPlaylistViewCallback(),
        playlist = Playlist(
            name = "Playlist",
            isActive = true,
            volume = 0.25f,
            hasError = false,
            isSingleTrack = false))
}

@Preview @Composable
fun LightTrackErrorPreview() = SoundAuraTheme(darkTheme = false) {
    PlaylistView(
        callback = rememberPlaylistViewCallback(),
        playlist = Playlist(
            name = "Track 3",
            isActive = false,
            volume = 1.00f,
            hasError = true,
            isSingleTrack = true))
}

@Preview(showBackground = true) @Composable
fun DarkTrackErrorPreview() = SoundAuraTheme(darkTheme = true) {
    PlaylistView(
        callback = rememberPlaylistViewCallback(),
        playlist = Playlist(
            name = "Track 4",
            isActive = false,
            volume = 1.00f,
            hasError = true,
            isSingleTrack = true))
}