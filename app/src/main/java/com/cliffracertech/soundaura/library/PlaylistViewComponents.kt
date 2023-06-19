/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationConstants.DefaultDurationMillis
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.cliffracertech.soundaura.R
import com.cliffracertech.soundaura.dialog.SoundAuraDialog
import com.cliffracertech.soundaura.restrictWidthAccordingToSizeClass
import com.cliffracertech.soundaura.ui.HorizontalDivider
import com.cliffracertech.soundaura.ui.MarqueeText
import com.cliffracertech.soundaura.ui.minTouchTargetSize
import com.cliffracertech.soundaura.ui.theme.SoundAuraTheme
import kotlinx.collections.immutable.ImmutableList
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorder
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable
import java.io.File

@Composable fun <T>overshootTween(
    duration: Int = DefaultDurationMillis,
    delay: Int = 0,
) = tween<T>(duration, delay) {
    val t = it - 1
    t * t * (3 * t + 2) + 1
}

@Composable fun <T>anticipateTween(
    duration: Int = DefaultDurationMillis,
    delay: Int = 0,
) = tween<T>(duration, delay) {
    it * it * (3 * it - 2)
}

/**
 * An [IconButton] that alternates between an empty circle with a plus icon,
 * and a filled circle with a minus icon depending on the parameter [added].
 *
 * @param added The added/removed state of the item the button is
 *     representing. If added is true, the button will display a minus
 *     icon. If added is false, a plus icon will be displayed instead.
 * @param contentDescription The content description of the button.
 * @param backgroundColor The [Color] of the background that the button
 *     is being displayed on. This is used for the inner plus icon
 *     when [added] is true and the background of the button is filled.
 * @param tint The [Color] that will be used for the button.
 * @param onClick The callback that will be invoked when the button is clicked.
 */
@Composable fun AddRemoveButton(
    added: Boolean,
    contentDescription: String? = null,
    backgroundColor: Color = MaterialTheme.colors.background,
    tint: Color = LocalContentColor.current,
    onClick: () -> Unit
) = IconButton(onClick) {
    // Circle background
    // The combination of the larger tinted circle and the smaller
    // background-tinted circle creates the effect of a circle that
    // animates between filled and outlined.
    Box(Modifier.size(24.dp).background(tint, CircleShape))
    AnimatedVisibility(
        visible = !added,
        enter = scaleIn(overshootTween()),
        exit = scaleOut(anticipateTween()),
    ) {
        Box(Modifier.size(20.dp).background(backgroundColor, CircleShape))
    }

    // Plus / minus icon
    // The combination of the two angles allows the icon to always
    // rotate clockwise, instead of alternating between clockwise
    // and counterclockwise.
    val angleMod by animateFloatAsState(if (added) 90f else 0f)
    val angle = if (added) 90f + angleMod
    else       90f - angleMod

    val iconTint by animateColorAsState(
        if (added) backgroundColor else tint)
    val minusIcon = painterResource(R.drawable.minus)

    // One minus icon always appears horizontally, while the other
    // can rotate between 0 and 90 degrees so that both minus icons
    // together appear as a plus icon.
    Icon(minusIcon, null, Modifier.rotate(2 * angle), tint)
    Icon(minusIcon, contentDescription, Modifier.rotate(angle), iconTint)
}

/**
 * Show a toggle shuffle switch and a reorderable list of tracks for a playlist.
 *
 * @param shuffleEnabled Whether or not shuffle is currently enabled
 * @param tracks A [MutableList] of the playlist's tracks' [Uri]s
 * @param modifier The [Modifier] to use for the root layout
 * @param onShuffleSwitchClick The callback that will be invoked when
 *     the switch indicating the current shuffleEnabled value is clicked
 */
@Composable fun ColumnScope.PlaylistOptions(
    shuffleEnabled: Boolean,
    tracks: MutableList<Uri>,
    modifier: Modifier = Modifier,
    onShuffleSwitchClick: () -> Unit,
) {
    HorizontalDivider(Modifier.padding(horizontal = 8.dp))
    Row(modifier = modifier
            .height(56.dp)
            .clickable(role = Role.Switch, onClick = onShuffleSwitchClick)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.playlist_shuffle_switch_title),
             style = MaterialTheme.typography.h6)
        Spacer(modifier = Modifier.weight(1f))
        Switch(shuffleEnabled, { onShuffleSwitchClick() })
    }
    HorizontalDivider(Modifier.padding(horizontal = 8.dp))

    val reorderableState = rememberReorderableLazyListState(onMove = { from, to ->
        tracks.add(to.index, tracks.removeAt(from.index))
    })
    // The track list ordering must have its height restricted to
    // prevent a crash due to nested infinite height layouts
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    LazyColumn(
        modifier = Modifier
            .heightIn(max = screenHeight - 350.dp)
            .reorderable(reorderableState),
        state = reorderableState.listState,
    ) {
        items(tracks, key = { it }) { uri ->
            ReorderableItem(reorderableState, key = uri) {isDragging ->
                val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp)
                val shape = MaterialTheme.shapes.small

                Row(modifier = Modifier
                        .fillMaxWidth()
                        .shadow(elevation, shape)
                        .background(MaterialTheme.colors.surface, shape)
                        .padding(end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Uri.lastPathSegment seems to not work with some Uris for some reason
                    val lastPathSegment = uri.path?.substringAfterLast(File.separatorChar).orEmpty()
                    Icon(imageVector = Icons.Default.DragHandle,
                        contentDescription = stringResource(
                            R.string.playlist_track_handle_description, lastPathSegment),
                        modifier = Modifier
                            .minTouchTargetSize()
                            .detectReorder(reorderableState)
                            .padding(10.dp))

                    if (uri.path.orEmpty().length > lastPathSegment.length)
                        Text("…${File.separatorChar}")
                    MarqueeText(lastPathSegment)
                }
            }
        }
    }
}

@Preview @Composable fun PlaylistOptionsPreview() = SoundAuraTheme {
    var tempShuffleEnabled by remember { mutableStateOf(false) }
    val tempTrackOrder: MutableList<Uri> = remember {
        val tracks = List(5) {
            "directory/subdirectory/extra_super_duper_really_long_file_$it".toUri()
        }.toTypedArray()
        mutableStateListOf(*tracks)
    }
    Surface { Column(Modifier.padding(vertical = 16.dp)) {
        PlaylistOptions(tempShuffleEnabled, tempTrackOrder) {
            tempShuffleEnabled = !tempShuffleEnabled
        }
    }}
}

/**
 * Show a dialog that contains an inner [PlaylistOptions] section to alter the
 * [playlist]'s shuffle and track order. If [playlist]'s [Playlist.isSingleTrack]
 * property is true, then a system file picker will show first to allow the
 * user to choose extra files to add to the single track in order to create a
 * multi-track playlist.
 *
 * @param playlist The [Playlist] whose shuffle and track order
 *     are being adjusted
 * @param shuffleEnabled Whether or not the playlist has shuffle enabled
 * @param tracks An [ImmutableList] containing the [Uri]s of the playlist's tracks
 * @param onDismissRequest The callback that will be invoked
 *     when the back button or gesture is activated or the
 *     dialog's cancel button is clicked
 * @param modifier The [Modifier] to use for the dialog window
 * @param onConfirmClick The callback that will be invoked when the dialog's
 *     confirm button is clicked. The Boolean and List<Uri> parameters
 *     are the playlist's requested shuffle value and track order.
 */
@Composable fun PlaylistOptionsDialog(
    playlist: Playlist,
    shuffleEnabled: Boolean,
    tracks: ImmutableList<Uri>,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    onConfirmClick: (shuffleEnabled: Boolean, newTrackOrder: List<Uri>) -> Unit,
) {
    var chosenUris by remember { mutableStateOf<List<Uri>?>(null) }

    if (playlist.isSingleTrack && chosenUris == null) {
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments()
        ) { uris ->
            if (uris.isEmpty())
                onDismissRequest()
            chosenUris = uris
        }
        LaunchedEffect(Unit) {
            launcher.launch(arrayOf("audio/*", "application/ogg"))
        }
    } else {
        var tempShuffleEnabled by rememberSaveable { mutableStateOf(shuffleEnabled) }
        val tempTrackOrder: MutableList<Uri> = rememberSaveable(
                /* inputs =*/ tracks,
                saver = listSaver({ it }, List<Uri>::toMutableStateList)
            ) {
                val existingTracks = tracks.toTypedArray()
                val newTracks = chosenUris?.toTypedArray() ?: emptyArray()
                mutableStateListOf(*existingTracks, *newTracks)
            }
        SoundAuraDialog(
            modifier = modifier.restrictWidthAccordingToSizeClass(),
            useDefaultWidth = false,
            titleLayout = @Composable {
                Row(modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 12.dp,
                                 start = 16.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    val style = MaterialTheme.typography.h6
                    MarqueeText(playlist.name, style = style)
                    Text(stringResource(R.string.playlist_options_dialog_title), style = style)
                }
            }, onDismissRequest = onDismissRequest,
            onConfirm = { onConfirmClick(tempShuffleEnabled, tempTrackOrder) }
        ) {
            PlaylistOptions(
                shuffleEnabled = tempShuffleEnabled,
                tracks = tempTrackOrder,
                onShuffleSwitchClick = {
                    tempShuffleEnabled = !tempShuffleEnabled
                })
        }
    }
}

@Composable fun ConfirmRemoveDialog(
    itemName: String,
    onDismissRequest: () -> Unit,
    onConfirmClick: () -> Unit
) = SoundAuraDialog(
    onDismissRequest = onDismissRequest,
    title = stringResource(R.string.confirm_remove_title, itemName),
    text = stringResource(R.string.confirm_remove_message),
    confirmText = stringResource(R.string.remove),
    onConfirm = {
        onConfirmClick()
        onDismissRequest()
    })