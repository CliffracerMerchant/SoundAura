/* This file is part of SoundObservatory, which is released under the Apache License 2.0. See
 * license.md in the project's root directory or use an internet search engine to see the full license. */
package com.cliffracertech.soundobservatory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cliffracertech.soundobservatory.ui.theme.SoundObservatoryTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalAnimationApi
@ExperimentalComposeUiApi
@ExperimentalCoroutinesApi
@ExperimentalAnimationGraphicsApi
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: TrackViewModel = viewModel()
            val tracks = viewModel.tracks.collectAsState()
            MainActivityContent(tracks.value)
        }
    }
}

@ExperimentalComposeUiApi
@ExperimentalAnimationGraphicsApi
@ExperimentalAnimationApi
@Preview(showBackground = true)
@Composable fun MainActivityPreview() = MainActivityContent(listOf(
    Track(path = "", name = "Audio clip 1", volume = 0.3f),
    Track(path = "", name = "Audio clip 2", volume = 0.8f)
))

@ExperimentalComposeUiApi
@ExperimentalAnimationGraphicsApi
@ExperimentalAnimationApi
@Composable fun MainActivityContent(tracks: List<Track>) {
    val title = stringResource(R.string.app_name)

    SoundObservatoryTheme(true) {
        Surface(
            color = MaterialTheme.colors.background,
            modifier = Modifier.fillMaxSize(1f)
        ) {
            var actionModeTitle by remember { mutableStateOf<String?>(null) }
            var searchQuery by remember { mutableStateOf<String?>(null) }
            var sortOption by remember { mutableStateOf(Track.Sort.NameAsc) }

            Column {
                var addButtonExpanded by remember { mutableStateOf(false) }
                ListViewActionBar(
                    backButtonVisible = false,
                    onBackButtonClick = { },
                    title, actionModeTitle, searchQuery,
                    onSearchQueryChanged = { searchQuery = it },
                    sortOption = sortOption,
                    onSortOptionChanged = { sortOption = it},
                    sortOptionNameFunc = { string(it) },
                    onSearchButtonClicked = {
                        searchQuery = if (searchQuery == null) "" else null
                    })
                Box(Modifier.fillMaxSize(1f)) {
                    TrackList(tracks)
                    DownloadOrAddLocalFileButton(
                        expanded = addButtonExpanded,
                        onClick = { addButtonExpanded = !addButtonExpanded },
                        onAddDownloadClick = { addButtonExpanded = false },
                        onAddLocalFileClick = { addButtonExpanded = false },
                        modifier = Modifier.padding(8.dp).align(Alignment.BottomEnd))
                }
            }
        }
    }
}

@ExperimentalComposeUiApi
@ExperimentalAnimationGraphicsApi
@Composable fun TrackList(tracks: List<Track>) =
    LazyColumn(
        modifier = Modifier.padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(tracks) { TrackView(it) }
    }

@ExperimentalComposeUiApi
@ExperimentalAnimationGraphicsApi
@Composable fun TrackView(track: Track) = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
        .fillMaxWidth(1f)
        .background(MaterialTheme.colors.surface, MaterialTheme.shapes.large)
){
    var playing by remember { mutableStateOf(false) }
    PlayPauseButton(playing, track.name,
                    MaterialTheme.colors.primary)
                    { playing = !playing }

    var volume by remember { mutableStateOf(track.volume) }
    SliderBox(value = volume, onValueChange = { volume = it },
              modifier = Modifier.height(66.dp).weight(1f),
              sliderPadding = PaddingValues(top = 28.dp)) {
        Text(text = track.name, style = MaterialTheme.typography.h6,
             modifier = Modifier.padding(8.dp, 6.dp, 0.dp, 0.dp))
    }

    var showingOptionsMenu by remember { mutableStateOf(false) }
    var showingRenameDialog by remember { mutableStateOf(false) }
    var showingDeleteDialog by remember { mutableStateOf(false) }

    IconButton(onClick = { showingOptionsMenu = !showingOptionsMenu }) {
        val description = stringResource(R.string.item_options_button_description, track.name)
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
                Text(text = stringResource(R.string.item_delete_desrciption),
                     style = MaterialTheme.typography.button)
            }
        }
    }

    if (showingRenameDialog)
        RenameDialog(
            track.name,
            onDismissRequest = { showingRenameDialog = false },
            onConfirmRequest = {  })

    if (showingDeleteDialog)
        ConfirmDeleteDialog(
            track.name,
            onDismissRequest = { showingDeleteDialog = false },
            onConfirmRequest = { })
}

@ExperimentalAnimationGraphicsApi
@Composable fun PlayPauseButton(playing: Boolean, itemName: String,
                                tint: Color, onClick: () -> Unit) =
    IconButton(onClick) {
        val playToPause = animatedVectorResource(R.drawable.play_to_pause).painterFor(playing)
        val pauseToPlay = animatedVectorResource(R.drawable.pause_to_play).painterFor(!playing)
        val vector = if (playing) playToPause
                     else         pauseToPlay

        val description = if (playing) stringResource(R.string.item_pause_description, itemName)
                          else         stringResource(R.string.item_play_description, itemName)
        Icon(vector, description, tint = tint)
    }


