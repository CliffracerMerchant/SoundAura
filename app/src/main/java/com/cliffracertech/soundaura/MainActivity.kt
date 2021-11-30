/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.FloatingActionButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cliffracertech.soundaura.ui.theme.SoundAuraTheme
import kotlinx.coroutines.flow.collect

class MainActivity : ComponentActivity() {
    private var boundPlayerService by mutableStateOf<PlayerService.Binder?>(null)

    private val serviceConnection = object: ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            boundPlayerService = service as? PlayerService.Binder
        }
        override fun onServiceDisconnected(name: ComponentName?) {}
    }

    override fun onResume() {
        super.onResume()
        val intent = Intent(this, PlayerService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onPause() {
        super.onPause()
        unbindService(serviceConnection)
        boundPlayerService = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(
            ContextCompat.getDrawable(this, R.drawable.background_gradient))

        setContent {
            val viewModel: ViewModel = viewModel()
            val tracks by viewModel.tracks.collectAsState()
            val trackSort by viewModel.trackSort.collectAsState()
            val isPlaying by produceState(false, boundPlayerService) {
                val service = boundPlayerService
                if (service == null) value = false
                else service.isPlaying.collect { value = it }
            }

            val itemCallback = TrackViewCallback(
                onPlayPauseButtonClick = { uri, trackIsPlaying -> viewModel.updatePlaying(uri, trackIsPlaying) },
                onVolumeChange = { uri, volume -> boundPlayerService?.setTrackVolume(uri, volume) },
                onVolumeChangeFinished = { uri, volume -> viewModel.updateVolume(uri, volume) },
                onRenameRequest = { uri, name -> viewModel.updateName(uri, name) },
                onDeleteRequest = { uri -> viewModel.delete(uri) })
            MainActivityContent(
                tracks = tracks,
                trackSort = trackSort,
                playing = isPlaying,
                itemCallback = itemCallback,
                onSortingChanged = { viewModel.trackSort.value = it },
                onAddItemRequest = { viewModel.add(it) },
                onPlayPauseRequest = { boundPlayerService?.toggleIsPlaying() })
        }
    }
}

@Preview(showBackground = true)
@Composable fun MainActivityPreview() = MainActivityContent(
    listOf(Track(uriString = "", name = "Audio clip 1", volume = 0.3f),
           Track(uriString = "", name = "Audio clip 2", volume = 0.8f)),
    playing = true)

@Composable fun MainActivityContent(
    tracks: List<Track>,
    trackSort: Track.Sort = Track.Sort.NameAsc,
    playing: Boolean,
    itemCallback: TrackViewCallback = TrackViewCallback(),
    onSortingChanged: (Track.Sort) -> Unit = { },
    onAddItemRequest: (Track) -> Unit = { },
    onPlayPauseRequest: () -> Unit = { },
) {
    val title = stringResource(R.string.app_name)

    SoundAuraTheme {
        Surface(
            color = MaterialTheme.colors.background,
            modifier = Modifier.fillMaxSize(1f)
        ) {
            var searchQuery by rememberSaveable { mutableStateOf<String?>(null) }
            var showingAddLocalFileDialog by rememberSaveable { mutableStateOf(false) }
            //var showingDownloadFileDialog by rememberSaveable { mutableStateOf(false) }
            Column {
                var addButtonExpanded by remember { mutableStateOf(false) }
                ListActionBar(
                    backButtonVisible = false,
                    onBackButtonClick = { },
                    title, null, searchQuery,
                    onSearchQueryChanged = { searchQuery = it },
                    sortOption = trackSort,
                    onSortOptionChanged = onSortingChanged,
                    sortOptionNameFunc = { string(it) },
                    onSearchButtonClicked = {
                        searchQuery = if (searchQuery == null) "" else null
                    })
                Box(Modifier.fillMaxSize(1f)) {
                    TrackList(tracks, itemCallback)
                    DownloadOrAddLocalFileButton(
                        expanded = addButtonExpanded,
                        onClick = { addButtonExpanded = !addButtonExpanded },
                        onAddDownloadClick = { addButtonExpanded = false },
                                                //showingDownloadFileDialog = true },
                        onAddLocalFileClick = { addButtonExpanded = false
                                                showingAddLocalFileDialog = true },
                        modifier = Modifier.padding(16.dp).align(Alignment.BottomEnd))
                    FloatingActionButton(
                        onClick = onPlayPauseRequest,
                        modifier = Modifier.padding(16.dp).align(Alignment.BottomCenter),
                        backgroundColor = lerp(MaterialTheme.colors.primary,
                                               MaterialTheme.colors.primaryVariant, 0.5f),
                        elevation = FloatingActionButtonDefaults.elevation(6.dp, 3.dp)
                    ) {
                        val description = if (playing) stringResource(R.string.pause_description)
                                          else         stringResource(R.string.play_description)
                        PlayPauseIcon(playing, description, MaterialTheme.colors.onPrimary)
                    }
                }
                //if (showingDownloadFileDialog)
                if (showingAddLocalFileDialog)
                    AddTrackFromLocalFileDialog(
                        onDismissRequest = { showingAddLocalFileDialog = false },
                        onConfirmRequest = { onAddItemRequest(it)
                                             showingAddLocalFileDialog = false })
            }
        }
    }
}

@Composable fun TrackList(tracks: List<Track>, trackViewCallback: TrackViewCallback) =
    LazyColumn(
        modifier = Modifier.padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(tracks, { it.uriString }) {
            TrackView(it, trackViewCallback, Modifier.animateItemPlacement())
        }
    }




