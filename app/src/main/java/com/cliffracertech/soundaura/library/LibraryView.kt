/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.library

import android.content.Context
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.cliffracertech.soundaura.model.SearchQueryState
import com.cliffracertech.soundaura.model.database.Playlist
import com.cliffracertech.soundaura.model.database.PlaylistDao
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
    playlistViewCallback: PlaylistViewCallback
) {
    Crossfade(libraryContents?.isEmpty()) { when(it) {
        null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator(Modifier.size(50.dp))
        } true -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text(stringResource(R.string.empty_track_list_message),
                 modifier = Modifier.width(300.dp),
                 textAlign = TextAlign.Justify)
        } else -> LazyColumn(
            modifier = modifier,
            state = state,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = libraryContents ?: emptyList(),
                key = Playlist::name::get
            ) { track ->
                PlaylistView(track, playlistViewCallback,
                             Modifier.animateItemPlacement())
            }
        }
    }}
}

@HiltViewModel class LibraryViewModel(
    dataStore: DataStore<Preferences>,
    private val playlistDao: PlaylistDao,
    searchQueryState: SearchQueryState,
    coroutineScope: CoroutineScope? = null
) : ViewModel() {

    @Inject constructor(
        dataStore: DataStore<Preferences>,
        playlistDao: PlaylistDao,
        searchQueryState: SearchQueryState,
    ) : this(dataStore, playlistDao, searchQueryState, null)

    private val scope = coroutineScope ?: viewModelScope
    private val showActiveTracksFirstKey = booleanPreferencesKey(PrefKeys.showActivePlaylistsFirst)
    private val showActiveTracksFirst = dataStore.preferenceFlow(showActiveTracksFirstKey, false)
    private val playlistSortKey = intPreferencesKey(PrefKeys.playlistSort)
    private val playlistSort = dataStore.enumPreferenceFlow<Playlist.Sort>(playlistSortKey)

    private val searchQueryFlow = snapshotFlow { searchQueryState.query.value }
    val tracks by combine(playlistSort, showActiveTracksFirst, searchQueryFlow, playlistDao::getAllPlaylists)
        .transformLatest { emitAll(it) }
        .map(List<Playlist>::toImmutableList)
        .collectAsState(null, scope)

    fun onDeletePlaylistDialogConfirm(context: Context, uriString: String) {
        scope.launch {
            val uri = Uri.parse(uriString)
            try {
                context.contentResolver.releasePersistableUriPermission(
                    uri, FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {}
            playlistDao.delete(uriString)
        }
    }

    fun onPlaylistAddRemoveButtonClick(uriString: String) {
        scope.launch { playlistDao.toggleIsActive(uriString) }
    }

    fun onPlaylistVolumeChangeRequest(uriString: String, volume: Float) {
        scope.launch { playlistDao.setVolume(uriString, volume) }
    }

    fun onPlaylistRenameDialogConfirm(oldName: String, newName: String) {
        scope.launch { playlistDao.rename(oldName, newName) }
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
 * @param onVolumeChange The callback that will be invoked when
 *     a TrackView's volume slider is moved.
 */
@Composable fun SoundAuraLibraryView(
    modifier: Modifier = Modifier,
    padding: PaddingValues,
    state: LazyListState = rememberLazyListState(),
    onVolumeChange: (String, Float) -> Unit,
) = Surface(modifier, color = MaterialTheme.colors.background) {
    val viewModel: LibraryViewModel = viewModel()
    val context = LocalContext.current
    val itemCallback = rememberPlaylistViewCallback(
        onAddRemoveButtonClick = viewModel::onPlaylistAddRemoveButtonClick,
        onVolumeChange = onVolumeChange,
        onVolumeChangeFinished = viewModel::onPlaylistVolumeChangeRequest,
        onRenameRequest = viewModel::onPlaylistRenameDialogConfirm,
        onDeleteRequest = {
            viewModel.onDeletePlaylistDialogConfirm(context, it)
        })
    LibraryView(
        modifier = modifier,
        state = state,
        contentPadding = padding,
        libraryContents = viewModel.tracks,
        playlistViewCallback = itemCallback)
}