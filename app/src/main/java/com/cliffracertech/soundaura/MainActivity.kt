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
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
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
        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val preferences = settingsViewModel.prefs.collectAsState()
            val usingDarkTheme by derivedStateOf {
                 val theme = AppTheme.values()[preferences.value?.get(appThemeKey) ?: 0]
                 (theme == AppTheme.UseSystem && isSystemInDarkTheme()) ||
                     theme == AppTheme.Dark
            }
            SoundAuraTheme(usingDarkTheme) { Column {
                var showingAppSettings by remember { mutableStateOf(false) }
                val trackViewModel: TrackViewModel = viewModel()
                LaunchedEffect(usingDarkTheme) {
                    Log.d("theme", "window background set")
                    window.setBackgroundDrawable(
                        ContextCompat.getDrawable(this@MainActivity, R.drawable.background_gradient))
                }

                var searchQuery by remember { mutableStateOf<String?>(null) }
                val trackSort by trackViewModel.trackSort.collectAsState()
                ListActionBar(
                    backButtonShouldBeVisible = showingAppSettings,
                    onBackButtonClick = { showingAppSettings = false },
                    title = if (!showingAppSettings) stringResource(R.string.app_name)
                            else stringResource(R.string.app_settings_description),
                    searchQuery = searchQuery,
                    onSearchQueryChanged = { searchQuery = it },
                    showSearchAndChangeSortButtons = !showingAppSettings,
                    onSearchButtonClicked = {
                        searchQuery = if (searchQuery == null) "" else null
                    }, sortOptions = Track.Sort.values(),
                    sortOptionNames = Track.Sort.stringValues(),
                    currentSortOption = trackSort,
                    onSortOptionChanged = { trackViewModel.trackSort.value = it }
                ) {
                    SettingsButton{ showingAppSettings = true }
                }

                val enterOffset = remember { { size: Int -> size * if (showingAppSettings) -1 else 1 } }
                val exitOffset = remember { { size: Int -> size * if (showingAppSettings) 1 else -1 } }
                AnimatedContent(showingAppSettings, transitionSpec = {
                    slideInHorizontally(tween(), enterOffset) with
                    slideOutHorizontally(tween(), exitOffset)
                }) { showingSettings ->
                    if(showingSettings)
                        AppSettings()
                    else {
                        val tracks by trackViewModel.tracks.collectAsState()

                        val isPlaying by produceState(false, boundPlayerService) {
                            val service = boundPlayerService
                            if (service == null) value = false
                            else service.isPlaying.collect { value = it }
                        }

                        val itemCallback = remember { TrackViewCallback(
                            onPlayPauseButtonClick = { uri, trackIsPlaying -> trackViewModel.updatePlaying(uri, trackIsPlaying) },
                            onVolumeChange = { uri, volume -> boundPlayerService?.setTrackVolume(uri, volume) },
                            onVolumeChangeFinished = { uri, volume -> trackViewModel.updateVolume(uri, volume) },
                            onRenameRequest = { uri, name -> trackViewModel.updateName(uri, name) },
                            onDeleteRequest = { uri -> trackViewModel.delete(uri) }) }
                        SoundMixEditor(
                            tracks = tracks,
                            playing = isPlaying,
                            itemCallback = itemCallback,
                            onAddItemRequest = { trackViewModel.add(it) },
                            onPlayPauseRequest = { boundPlayerService?.toggleIsPlaying() })
                    }
                }
            }}
        }
    }
}

@Preview(showBackground = true)
@Composable fun MainActivityPreview() =
    SoundAuraTheme {
        Column {
            ListActionBar(
                backButtonShouldBeVisible = false,
                onBackButtonClick = { },
                title = stringResource(R.string.app_name),
                searchQuery = null,
                onSearchQueryChanged = { },
                sortOptions = Track.Sort.values(),
                sortOptionNames = Track.Sort.stringValues(),
                currentSortOption = Track.Sort.NameAsc,
                onSortOptionChanged = { },
                onSearchButtonClicked = { }
            ) { SettingsButton{ } }

            SoundMixEditor(playing = true, tracks = listOf(
                Track(uriString = "0", name = "Audio clip 1", volume = 0.3f),
                Track(uriString = "1", name = "Audio clip 2", volume = 0.8f)))
        }
    }
/**
 * A combination of a ListActionBar to search and filter the available tracks,
 * a TrackList to display all tracks that match the sorting and filtering
 * options selected in the ListActionBar, and controls to play/pause the sound
 * mix and add new tracks.
 *
 * @param tracks A list of all of the tracks that should be displayed in the TrackList.
 * @param playing Whether the play/pause button will display its
 *                pause icon (as opposed to its play icon).
 * @param itemCallback A TrackViewCallback instance that contains the callbacks
 *                     to be invoked when the TrackView instances inside the
 *                     TrackList are interacted with.
 * @param onAddItemRequest A callback that will be invoked when a new Track
 *                         is added through the various add track dialogs.
 * @param onPlayPauseRequest A callback that will be invoked when the play /
 *                           pause button is clicked.
 */
@Composable fun SoundMixEditor(
    tracks: List<Track>,
    playing: Boolean,
    itemCallback: TrackViewCallback = TrackViewCallback(),
    onAddItemRequest: (Track) -> Unit = { },
    onPlayPauseRequest: () -> Unit = { },
) = Surface(
    color = MaterialTheme.colors.background,
    modifier = Modifier.fillMaxSize(1f)
) {
    var showingAddLocalFileDialog by rememberSaveable { mutableStateOf(false) }
    //var showingDownloadFileDialog by rememberSaveable { mutableStateOf(false) }
    var addButtonExpanded by remember { mutableStateOf(false) }

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

@Composable fun TrackList(tracks: List<Track>, trackViewCallback: TrackViewCallback) =
    LazyColumn(
        modifier = Modifier.padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(tracks, { it.uriString }) {
            TrackView(it, trackViewCallback, Modifier.animateItemPlacement())
        }
    }




