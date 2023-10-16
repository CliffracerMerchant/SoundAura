/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.library

import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cliffracertech.soundaura.R
import com.cliffracertech.soundaura.collectAsState
import com.cliffracertech.soundaura.model.MessageHandler
import com.cliffracertech.soundaura.model.ModifyLibraryUseCase
import com.cliffracertech.soundaura.model.PlaybackState
import com.cliffracertech.soundaura.model.PlayerServicePlaybackState
import com.cliffracertech.soundaura.model.ReadLibraryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A [LazyColumn] to display all of the provided [Playlist]s with instances of [PlaylistView].
 *
 * @param modifier The [Modifier] that will be used for the TrackList.
 * @param state The [LazyListState] used for the TrackList's scrolling state.
 * @param contentPadding The [PaddingValues] instance that will be used as
 *     the content padding for the TrackList's items.
 * @param libraryContents The [ImmutableList] of [Playlist]s that will be
 *     displayed by the LibraryView. If the list is empty, an empty list
 *     message will be displayed instead. A null value is interpreted as
 *     a loading state.
 * @param playlistViewCallback The instance of [PlaylistViewCallback] that will
 *     be used for responses to individual TrackView interactions.
 */
@Composable fun LibraryView(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues,
    libraryContents: ImmutableList<Playlist>?,
    shownDialog: PlaylistDialog?,
    playlistViewCallback: PlaylistViewCallback
) {
    Crossfade(
        targetState = libraryContents?.isEmpty(),
        label = "LibraryView content/empty crossfade",
    ) {
        when(it) {
            null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(Modifier.size(50.dp))
            } true -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(stringResource(R.string.empty_track_list_message),
                     modifier = Modifier.width(300.dp),
                     textAlign = TextAlign.Justify)
            } else -> LazyColumn(
                modifier, state, contentPadding,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val items = libraryContents ?: emptyList()
                items(items, key = Playlist::name::get) { playlist ->
                    PlaylistView(playlist, playlistViewCallback,
                                 Modifier.animateItemPlacement())
                }
            }
        }
    }
    PlaylistDialogShower(shownDialog)
}

/**
 * A [ViewModel] to provide state and callbacks for an instance of LibraryView.
 *
 * The most recent list of all playlists is provided via the property
 * [playlists]. The [PlaylistViewCallback] that should be used for item
 * interactions is provided via the property [itemCallback]. The state
 * of any dialogs that should be shown are provided via the property
 * [shownDialog].
 */
@HiltViewModel class LibraryViewModel(
    readLibrary: ReadLibraryUseCase,
    private val modifyLibrary: ModifyLibraryUseCase,
    private val messageHandler: MessageHandler,
    private val playbackState: PlaybackState,
    coroutineScope: CoroutineScope? = null
) : ViewModel() {

    @Inject constructor(
        readLibraryUseCase: ReadLibraryUseCase,
        modifyLibraryUseCase: ModifyLibraryUseCase,
        messageHandler: MessageHandler,
        playbackState: PlayerServicePlaybackState,
    ) : this(readLibraryUseCase, modifyLibraryUseCase,
             messageHandler, playbackState, null)

    private val scope = coroutineScope ?: viewModelScope

    val playlists by readLibrary.playlistsFlow.collectAsState(null, scope)

    var shownDialog by mutableStateOf<PlaylistDialog?>(null)

    private fun dismissDialog() { shownDialog = null }

    val itemCallback = object : PlaylistViewCallback {
        override fun onAddRemoveButtonClick(playlist: Playlist) {
            scope.launch { modifyLibrary.togglePlaylistIsActive(playlist.name) }
        }
        override fun onVolumeChange(playlist: Playlist, volume: Float) {
            playbackState.setPlaylistVolume(playlist.name, volume)
        }
        override fun onVolumeChangeFinished(playlist: Playlist, volume: Float) {
            scope.launch { modifyLibrary.setPlaylistVolume(playlist.name, volume) }
        }
        override fun onRenameClick(playlist: Playlist) {
            shownDialog = PlaylistDialog.Rename(
                target = playlist,
                validator = modifyLibrary.renameValidator(playlist.name, scope),
                coroutineScope = scope,
                onDismissRequest = ::dismissDialog,
                onNameValidated = { validatedName ->
                    dismissDialog()
                    modifyLibrary.renamePlaylist(
                        from = playlist.name,
                        to = validatedName)
                })
        }
        override fun onExtraOptionsClick(playlist: Playlist) {
            scope.launch {
                val existingTracks = readLibrary.getPlaylistTracks(playlist.name)
                assert((existingTracks.size == 1) == playlist.isSingleTrack)
                if (playlist.isSingleTrack)
                    showFileChooser(playlist, existingTracks)
                else showPlaylistOptions(
                    target = playlist,
                    existingTracks = existingTracks,
                    shuffleEnabled = readLibrary.getPlaylistShuffle(playlist.name))
            }
        }
        override fun onRemoveClick(playlist: Playlist) {
            shownDialog = PlaylistDialog.Remove(
                target = playlist,
                onDismissRequest = ::dismissDialog,
                onConfirmClick = {
                    dismissDialog()
                    scope.launch { modifyLibrary.removePlaylist(playlist.name) }
                })
        }
    }

    private fun showFileChooser(
        target: Playlist,
        existingTracks: List<Uri>,
        shuffleEnabled: Boolean = false,
    ) {
        shownDialog = PlaylistDialog.FileChooser(
            target, messageHandler, existingTracks,
            onDismissRequest = {
                // If the file chooser was arrived at by selecting the 'create playlist'
                // option for a single track playlist, we want the back button/gesture to
                // completely dismiss the dialog. If the file chooser was arrived at by
                // selecting the 'add more files' button in the playlist options dialog
                // of an existing multi-track playlist, then we want the back button/
                // gesture to go back to the playlist options dialog for that playlist.
                if (existingTracks.size == 1)
                    dismissDialog()
                else showPlaylistOptions(target, existingTracks, shuffleEnabled)
            }, onChosenFilesValidated = { validatedFiles ->
                showPlaylistOptions(target, existingTracks + validatedFiles, shuffleEnabled)
            }
        )
    }

    private fun showPlaylistOptions(
        target: Playlist,
        existingTracks: List<Uri>,
        shuffleEnabled: Boolean,
    ) {
        shownDialog = PlaylistDialog.PlaylistOptions(
            target, existingTracks, shuffleEnabled, ::dismissDialog,
            onAddFilesClick = {
                showFileChooser(target, existingTracks, shuffleEnabled)
            }, onConfirm = { newShuffle, newTrackList ->
                dismissDialog()
                scope.launch {
                    modifyLibrary.setPlaylistShuffleAndTracks(
                        target.name, newShuffle, newTrackList)
                }
            }
        )
    }
}

/**
 * Show a [LibraryView] that uses an instance of [LibraryViewModel] for its state.
 *
 * @param modifier The [Modifier] that will be used for the TrackList.
 * @param padding A [PaddingValues] instance whose values will be
 *     as the contentPadding for the TrackList
*  @param state The [LazyListState] used for the TrackList. state
 *     defaults to an instance of LazyListState returned from a
 *     [rememberLazyListState] call, but can be overridden here in
 *     case, e.g., the scrolling position needs to be remembered
 *     even when the SoundAuraTrackList leaves the composition.
 */
@Composable fun SoundAuraLibraryView(
    modifier: Modifier = Modifier,
    padding: PaddingValues,
    state: LazyListState = rememberLazyListState(),
) = Surface(modifier, color = MaterialTheme.colors.background) {
    val viewModel: LibraryViewModel = viewModel()
    LibraryView(
        modifier = modifier,
        state = state,
        contentPadding = padding,
        libraryContents = viewModel.playlists,
        shownDialog = viewModel.shownDialog,
        playlistViewCallback = viewModel.itemCallback)
}