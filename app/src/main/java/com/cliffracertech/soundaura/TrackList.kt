/* This file is part of SoundAura, which is released under the terms of the Apache
 * License 2.0. See license.md in the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A LazyColumn to display all of the Tracks provided in @param tracks
 * with instances of TrackView. The created TrackViews will use the
 * provided @param trackViewCallback for callbacks. */
@Composable
fun TrackList(
    modifier: Modifier = Modifier,
    bottomPadding: Dp,
    tracks: List<Track>,
    trackViewCallback: TrackViewCallback = TrackViewCallback()
) = LazyColumn(
    modifier = modifier,
    contentPadding = PaddingValues(8.dp, 8.dp, 8.dp, 8.dp + bottomPadding),
    verticalArrangement = Arrangement.spacedBy(8.dp)
) {
    items(items = tracks, key = { it.uriString }) {
        TrackView(it, trackViewCallback, Modifier.animateItemPlacement())
    }
}

@HiltViewModel
class TrackListViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val trackDao: TrackDao,
    searchQueryState: SearchQueryState
) : ViewModel() {

    private val trackSortKey = intPreferencesKey(context.getString(R.string.pref_sort_key))
    private val trackSort = context.dataStore.enumPreferenceFlow<Track.Sort>(trackSortKey)

    var tracks by mutableStateOf<List<Track>>(emptyList())
        private set

    init {
        val searchQueryFlow = snapshotFlow { searchQueryState.query.value }
        combine(trackSort, searchQueryFlow, trackDao::getAllTracks)
            .transformLatest { emitAll(it) }
            .onEach { tracks = it }.launchIn(viewModelScope)
    }

    fun onDeleteTrackDialogConfirmation(uriString: String) {
        viewModelScope.launch { trackDao.delete(uriString) }
    }

    fun onTrackPlayPauseClick(uriString: String, playing: Boolean) {
        viewModelScope.launch { trackDao.updatePlaying(uriString, playing) }
    }

    fun onTrackVolumeChangeRequest(uriString: String, volume: Float) {
        viewModelScope.launch { trackDao.updateVolume(uriString, volume) }
    }

    fun onTrackRenameRequest(uriString: String, name: String) {
        viewModelScope.launch { trackDao.updateName(uriString, name) }
    }
}

/** Compose a TrackList, using an instance of TrackListViewModel to
 * obtain the list of tracks and to respond to item related callbacks.
 * @param onVolumeChange The callback that will be invoked when
 *                       a TrackView's volume slider is moved. */
@Composable fun StatefulTrackList(
    bottomPadding: Dp,
    onVolumeChange: (String, Float) -> Unit,
) {
    val viewModel: TrackListViewModel = viewModel()
    val itemCallback = remember {
        TrackViewCallback(
            onPlayPauseButtonClick = viewModel::onTrackPlayPauseClick,
            onVolumeChange = onVolumeChange,
            onVolumeChangeFinished = viewModel::onTrackVolumeChangeRequest,
            onRenameRequest = viewModel::onTrackRenameRequest,
            onDeleteRequest = viewModel::onDeleteTrackDialogConfirmation)
    }
    TrackList(
        tracks = viewModel.tracks,
        bottomPadding = bottomPadding,
        trackViewCallback = itemCallback)
}