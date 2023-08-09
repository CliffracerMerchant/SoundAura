/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.library

import android.net.Uri
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Undo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.cliffracertech.soundaura.rememberMutableStateOf
import com.cliffracertech.soundaura.ui.HorizontalDivider
import com.cliffracertech.soundaura.ui.MarqueeText
import com.cliffracertech.soundaura.ui.SimpleIconButton
import com.cliffracertech.soundaura.ui.minTouchTargetSize
import com.cliffracertech.soundaura.ui.theme.SoundAuraTheme
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
    val angleMod by animateFloatAsState(
        targetValue = if (added) 90f else 0f,
        label = "playlist add/remove icon rotation transition")
    val angle = if (added) 90f + angleMod
                else       90f - angleMod

    val iconTint by animateColorAsState(
        targetValue = if (added) backgroundColor else tint,
        label = "playlist add/remove icon color transition")
    val minusIcon = painterResource(R.drawable.minus)

    // One minus icon always appears horizontally, while the other
    // can rotate between 0 and 90 degrees so that both minus icons
    // together appear as a plus icon.
    Icon(minusIcon, null, Modifier.rotate(2 * angle), tint)
    Icon(minusIcon, contentDescription, Modifier.rotate(angle), iconTint)
}

/** A representation of a track in a mutable playlist. The Boolean
 * parameter represents whether the track will be removed from the
 * playlist once changes are applied. */
typealias RemovablePlaylistTrack = Pair<Uri, Boolean>
val RemovablePlaylistTrack.uri get() = first
val RemovablePlaylistTrack.markedForRemoval get() = second

/**
 * MutablePlaylist represents the state of a list of reorderable [RemovablePlaylistTrack]s.
 *
 * The current list of tracks can be accessed via [tracks]. The method
 * [moveTrack] can be used to to reorder tracks, while [toggleTrackRemoval]
 * can be used  or to toggle the 'to be removed' state for each track. The
 * method [applyChanges] will return a [List] of the [Uri]s in their new
 * order and without the tracks that were marked for removal.
 */
class MutablePlaylist(trackUris: List<Uri>) {
    private val mutableTracks = trackUris.map { it to false }.toMutableStateList()
    private var trackCount = trackUris.size
    val tracks get() = mutableTracks as List<RemovablePlaylistTrack>

    fun moveTrack(fromIndex: Int, toIndex: Int) {
        if (fromIndex in mutableTracks.indices && toIndex in mutableTracks.indices)
            mutableTracks.add(toIndex, mutableTracks.removeAt(fromIndex))
    }

    /** Toggle the 'to be removed' state for the track at [index]. If [index]
     * points to the last remaining track in [tracks], the operation will fail. */
    fun toggleTrackRemoval(index: Int) {
        if (index !in tracks.indices) return
        val removing = !mutableTracks[index].markedForRemoval
        if (removing && trackCount == 1) return

        if (removing) trackCount--
        else          trackCount++
        val uri = mutableTracks[index].uri
        mutableTracks[index] = uri to removing
    }

    fun applyChanges() = mutableTracks
        .mapNotNull { if (it.markedForRemoval) null else it.uri }
}

/**
 * Show a toggle shuffle switch and a reorderable list of tracks for a playlist.
 *
 * @param shuffleEnabled Whether or not shuffle is currently enabled
 * @param mutablePlaylist A [MutablePlaylist] representing the playlist's contents
 * @param modifier The [Modifier] to use for the root layout
 * @param allowTrackRemoval Whether delete icons for each track should be shown
 * @param onShuffleSwitchClick The callback that will be invoked when
 *     the switch indicating the current shuffleEnabled value is clicked
 */
@Composable fun ColumnScope.PlaylistOptionsView(
    shuffleEnabled: Boolean,
    onShuffleSwitchClick: () -> Unit,
    mutablePlaylist: MutablePlaylist,
    modifier: Modifier = Modifier,
    allowTrackRemoval: Boolean = true,
) {
    HorizontalDivider(Modifier.padding(horizontal = 8.dp))
    Row(modifier = modifier
            .height(56.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(
                onClickLabel = stringResource(
                    R.string.playlist_shuffle_switch_description),
                role = Role.Switch,
                onClick = onShuffleSwitchClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Shuffle, null,
            Modifier.minTouchTargetSize().padding(12.dp))
        Text(stringResource(R.string.playlist_shuffle_switch_title),
             style = MaterialTheme.typography.h6,
             modifier = Modifier.weight(1f))
        Switch(shuffleEnabled, { onShuffleSwitchClick() },
               Modifier.padding(end = 8.dp))
    }
    HorizontalDivider(Modifier.padding(horizontal = 8.dp))

    val reorderableState = rememberReorderableLazyListState(onMove = { from, to ->
        mutablePlaylist.moveTrack(from.index, to.index)
    })
    // The track list ordering must have its height restricted to
    // prevent a crash due to nested infinite height layouts. The
    // dialog's title, shuffle switch, and button row should all
    // have heights of 56.dp, for a total height of 56.dp * 3. An
    // extra 56.dp padding added to it makes it 56.dp * 4 = 224.dp.
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp  - 224.dp
    LazyColumn(
        modifier = Modifier
            .heightIn(max = maxHeight)
            .reorderable(reorderableState),
        state = reorderableState.listState,
    ) {
        itemsIndexed(
            items = mutablePlaylist.tracks,
            key = { _, track -> track.uri }
        ) { index, (uri, markedForRemoval) ->
            ReorderableItem(reorderableState, key = uri) {isDragging ->
                val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp)
                val color by animateColorAsState(
                    if (markedForRemoval) MaterialTheme.colors.error
                    else                  MaterialTheme.colors.surface)
                val shape = MaterialTheme.shapes.small

                Row(modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation, shape)
                    .background(color, shape),
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
                    MarqueeText(lastPathSegment, Modifier.weight(1f))

                    if (!allowTrackRemoval)
                        Spacer(Modifier.width(16.dp))
                    else SimpleIconButton(
                        icon = if (markedForRemoval) Icons.Default.Undo
                               else                  Icons.Default.Delete,
                        contentDescription = stringResource(
                            R.string.playlist_track_delete_description),
                        iconPadding = 14.dp,
                        onClick = { mutablePlaylist.toggleTrackRemoval(index) })
                }
            }
        }
    }
}

@Preview @Composable fun PlaylistOptionsPreview() = SoundAuraTheme {
    var shuffleEnabled by rememberMutableStateOf(false)
    val mutablePlaylist = remember {
        MutablePlaylist(List(5) {
            // For some reason the Uri path segment methods don't work
            // properly if the Uri was created directly from a string
            // instead of from a File
            File("file:/directory/subdirectory/extra_super_duper_really_long_file_$it").toUri()
        })
    }
    Surface { Column(Modifier.padding(vertical = 16.dp)) {
        PlaylistOptionsView(
            shuffleEnabled = shuffleEnabled,
            onShuffleSwitchClick = { shuffleEnabled = !shuffleEnabled },
            mutablePlaylist = mutablePlaylist)
    }}
}