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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cliffracertech.soundaura.R
import com.cliffracertech.soundaura.collectAsState
import com.cliffracertech.soundaura.enumPreferenceFlow
import com.cliffracertech.soundaura.model.AndroidUriPermissionHandler
import com.cliffracertech.soundaura.model.MessageHandler
import com.cliffracertech.soundaura.model.PlaybackState
import com.cliffracertech.soundaura.model.SearchQueryState
import com.cliffracertech.soundaura.model.StringResource
import com.cliffracertech.soundaura.model.UriPermissionHandler
import com.cliffracertech.soundaura.model.database.PlaylistDao
import com.cliffracertech.soundaura.model.database.playlistNameValidator
import com.cliffracertech.soundaura.preferenceFlow
import com.cliffracertech.soundaura.settings.PrefKeys
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

private typealias PlaylistSort = com.cliffracertech.soundaura.model.database.Playlist.Sort

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
    dataStore: DataStore<Preferences>,
    private val permissionHandler: UriPermissionHandler,
    private val playlistDao: PlaylistDao,
    private val messageHandler: MessageHandler,
    private val playbackState: PlaybackState,
    searchQueryState: SearchQueryState,
    coroutineScope: CoroutineScope? = null
) : ViewModel() {

    @Inject constructor(
        dataStore: DataStore<Preferences>,
        permissionHandler: AndroidUriPermissionHandler,
        playlistDao: PlaylistDao,
        messageHandler: MessageHandler,
        playbackState: PlaybackState,
        searchQueryState: SearchQueryState,
    ) : this(dataStore, permissionHandler, playlistDao,
        messageHandler, playbackState, searchQueryState, null)

    private val scope = coroutineScope ?: viewModelScope
    private val showActiveTracksFirstKey = booleanPreferencesKey(PrefKeys.showActivePlaylistsFirst)
    private val showActiveTracksFirst = dataStore.preferenceFlow(showActiveTracksFirstKey, false)
    private val playlistSortKey = intPreferencesKey(PrefKeys.playlistSort)
    private val playlistSort = dataStore.enumPreferenceFlow<PlaylistSort>(playlistSortKey)

    private val searchQueryFlow = snapshotFlow { searchQueryState.query.value }
    val playlists by combine(
           playlistSort, showActiveTracksFirst,
           searchQueryFlow, playlistDao::getAllPlaylists
        ).transformLatest { emitAll(it) }
        .map(List<Playlist>::toImmutableList)
        .collectAsState(null, scope)

    var shownDialog by mutableStateOf<PlaylistDialog?>(null)

    val itemCallback = object : PlaylistViewCallback {
        override fun onAddRemoveButtonClick(playlist: Playlist) {
            scope.launch { playlistDao.toggleIsActive(playlist.name) }
        }
        override fun onVolumeChange(playlist: Playlist, volume: Float) {
            playbackState.setPlaylistVolume(playlist.name, volume)
        }
        override fun onVolumeChangeFinished(playlist: Playlist, volume: Float) {
            scope.launch { playlistDao.setVolume(playlist.name, volume) }
        }
        override fun onRenameClick(playlist: Playlist) {
            shownDialog = PlaylistDialog.Rename(
                target = playlist,
                validator = playlistNameValidator(
                    playlistDao, scope, playlist.name,
                    ignoreInitialValue = true),
                coroutineScope = scope,
                onDismissRequest = { shownDialog = null },
                onNameValidated = { validatedName ->
                    if (validatedName != playlist.name)
                        playlistDao.rename(playlist.name, validatedName)
                    shownDialog = null
                })
        }
        override fun onExtraOptionsClick(playlist: Playlist) {
            scope.launch {
                val shuffleEnabled = playlistDao.getPlaylistShuffle(playlist.name)
                val tracks = playlistDao.getPlaylistTracks(playlist.name)
                shownDialog = PlaylistDialog.PlaylistOptions(
                    target = playlist,
                    shuffleEnabled = shuffleEnabled,
                    playlistTracks = tracks,
                    onDismissRequest = { shownDialog = null },
                    onConfirm = { newShuffle, newTracks ->
                        shownDialog = null
                        savePlaylistShuffleAndTracks(playlist.name, newShuffle, tracks, newTracks)
                    })
            }
        }
        override fun onRemoveClick(playlist: Playlist) {
            shownDialog = PlaylistDialog.Remove(
                target = playlist,
                onDismissRequest = { shownDialog = null },
                onConfirmClick = {
                    shownDialog = null
                    scope.launch {
                        val unusedTracks = playlistDao.deletePlaylist(playlist.name)
                        permissionHandler.releasePermissionsFor(unusedTracks)
                    }
                })
        }
    }

    private fun savePlaylistShuffleAndTracks(
        playlistName: String,
        shuffle: Boolean,
        originalTracks: List<Uri>,
        newTracks: List<Uri>
    ) {
        scope.launch {
            val validatedTrackList =
                // If no new tracks were added, we can simply save the
                // new track order. Otherwise, we have to check if there
                // is enough permission space to add all of the tracks
                if (newTracks.size == originalTracks.size)
                    newTracks
                else permissionHandler.acquirePermissionsFor(
                    newTracks, allowPartial = false)
            if (validatedTrackList.isEmpty()) {
                messageHandler.postMessage(StringResource(
                    R.string.cant_add_playlist_tracks_warning,
                    permissionHandler.permissionAllowance))
                // If there isn't enough space, we ignore the new
                // track list and only save the new shuffle value
                playlistDao.setPlaylistShuffle(playlistName, shuffle)
            } else {
                val removedUris = playlistDao.setPlaylistShuffleAndContents(
                    playlistName, shuffle, originalTracks, validatedTrackList)
                permissionHandler.releasePermissionsFor(removedUris)
            }
        }
    }
}

/**
 * Compose a [LibraryView], using an instance of [LibraryViewModel] to
 * obtain the list of tracks and to respond to item related callbacks.
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