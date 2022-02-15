/* This file is part of SoundAura, which is released under the terms of the Apache
 * License 2.0. See license.md in the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** A LazyColumn to display all of the Tracks provided in @param tracks
 * with an instance of TrackView. The created TrackViews will use the
 * provided @param trackViewCallback for callbacks. */
@Composable fun TrackList(
    modifier: Modifier = Modifier,
    tracks: List<Track>,
    trackViewCallback: TrackViewCallback = TrackViewCallback()
) = LazyColumn(
    modifier = modifier,
    contentPadding = PaddingValues(8.dp, 8.dp, 8.dp, 70.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
) {
    items(tracks, { it.uriString }) {
        TrackView(it, trackViewCallback, Modifier.animateItemPlacement())
    }
}

/**
 * A combination of a TrackList to display all of the provided tracks and
 * and a button to add new tracks.
 *
 * @param modifier The modifier that will be applied to the SoundMixEditor.
 * @param tracks A list of all of the tracks that should be displayed in the TrackList.
 * @param itemCallback A TrackViewCallback instance that contains the callbacks to be
 *     invoked when the TrackView instances inside the TrackList are interacted with.
 * @param onConfirmAddTrackDialog The callback that will be invoked when one of
 *     the various add track dialogs has been confirmed.
 */
@Composable fun SoundMixEditor(
    modifier: Modifier = Modifier,
    tracks: List<Track>,
    itemCallback: TrackViewCallback = TrackViewCallback(),
    onConfirmAddTrackDialog: (Track) -> Unit = { },
) = Surface(
    color = MaterialTheme.colors.background,
    modifier = modifier.fillMaxSize(1f)
) {
    var addButtonExpanded by remember { mutableStateOf(false) }
    var showingAddLocalFileDialog by rememberSaveable { mutableStateOf(false) }
    //var showingDownloadFileDialog by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize(1f)) {
        TrackList(tracks = tracks, trackViewCallback = itemCallback)

        DownloadOrAddLocalFileButton(
            expanded = addButtonExpanded,
            onClick = { addButtonExpanded = !addButtonExpanded },
            modifier = Modifier.padding(16.dp).align(Alignment.BottomEnd),
            onAddDownloadClick = { addButtonExpanded = false },
            //showingDownloadFileDialog = true },
            onAddLocalFileClick = { addButtonExpanded = false
                                    showingAddLocalFileDialog = true })

        //if (showingDownloadFileDialog)

        if (showingAddLocalFileDialog)
            AddTrackFromLocalFileDialog(
                onDismissRequest = { showingAddLocalFileDialog = false },
                onConfirmRequest = { onConfirmAddTrackDialog(it)
                                     showingAddLocalFileDialog = false })
    }

}