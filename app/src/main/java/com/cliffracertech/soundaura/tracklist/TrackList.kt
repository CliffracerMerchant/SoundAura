/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.tracklist

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
import com.cliffracertech.soundaura.model.database.Track
import com.cliffracertech.soundaura.model.database.TrackDao
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
 * A [LazyColumn] to display all of the provided [Track]s with instances of [TrackView].
 *
 * @param modifier The [Modifier] that will be used for the TrackList.
 * @param state The [LazyListState] used for the TrackList's scrolling state.
 * @param contentPadding The [PaddingValues] instance that will be used as
 *     the content padding for the TrackList's items.
 * @param trackListProvider A method that will return the [ImmutableList] of
 *     [Track]s that will be displayed by the TrackList. If the provided list
 *     is empty, an empty list message will be displayed instead. A null value
 *     is interpreted as a loading state.
 * @param trackViewCallback The instance of [TrackViewCallback] that will
 *     be used for responses to individual TrackView interactions.
 */
@Composable fun TrackList(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues,
    trackListProvider: () -> ImmutableList<Track>?,
    trackViewCallback: TrackViewCallback
) {
    val trackList = trackListProvider()
    Crossfade(trackList?.isEmpty()) { when(it) {
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
                items = trackList ?: emptyList(),
                key = Track::uriString::get
            ) { track ->
                TrackView(track, trackViewCallback,
                          Modifier.animateItemPlacement())
            }
        }
    }}
}

@HiltViewModel
class TrackListViewModel(
    dataStore: DataStore<Preferences>,
    private val trackDao: TrackDao,
    searchQueryState: SearchQueryState,
    coroutineScope: CoroutineScope? = null
) : ViewModel() {

    @Inject constructor(
        dataStore: DataStore<Preferences>,
        trackDao: TrackDao,
        searchQueryState: SearchQueryState,
    ) : this(dataStore, trackDao, searchQueryState, null)

    private val scope = coroutineScope ?: viewModelScope
    private val showActiveTracksFirstKey = booleanPreferencesKey(PrefKeys.showActiveTracksFirst)
    private val showActiveTracksFirst = dataStore.preferenceFlow(showActiveTracksFirstKey, false)
    private val trackSortKey = intPreferencesKey(PrefKeys.trackSort)
    private val trackSort = dataStore.enumPreferenceFlow<Track.Sort>(trackSortKey)

    private val searchQueryFlow = snapshotFlow { searchQueryState.query.value }
    val tracks by combine(trackSort, showActiveTracksFirst, searchQueryFlow, trackDao::getAllTracks)
        .transformLatest { emitAll(it) }
        .map { it.toImmutableList() }
        .collectAsState(null, scope)

    fun onDeleteTrackDialogConfirm(context: Context, uriString: String) {
        scope.launch {
            val uri = Uri.parse(uriString)
            try {
                context.contentResolver.releasePersistableUriPermission(
                    uri, FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {}
            trackDao.delete(uriString)
        }
    }

    fun onTrackAddRemoveButtonClick(uriString: String) {
        scope.launch { trackDao.toggleIsActive(uriString) }
    }

    fun onTrackVolumeChangeRequest(uriString: String, volume: Float) {
        scope.launch { trackDao.setVolume(uriString, volume) }
    }

    fun onTrackRenameDialogConfirm(uriString: String, name: String) {
        scope.launch { trackDao.setName(uriString, name) }
    }
}

/**
 * Compose a [TrackList], using an instance of [TrackListViewModel] to
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
@Composable fun SoundAuraTrackList(
    modifier: Modifier = Modifier,
    padding: PaddingValues,
    state: LazyListState = rememberLazyListState(),
    onVolumeChange: (String, Float) -> Unit,
) = Surface(modifier, color = MaterialTheme.colors.background) {
    val viewModel: TrackListViewModel = viewModel()
    val context = LocalContext.current
    val itemCallback = rememberTrackViewCallback(
        onAddRemoveButtonClick = viewModel::onTrackAddRemoveButtonClick,
        onVolumeChange = onVolumeChange,
        onVolumeChangeFinished = viewModel::onTrackVolumeChangeRequest,
        onRenameRequest = viewModel::onTrackRenameDialogConfirm,
        onDeleteRequest = {
            viewModel.onDeleteTrackDialogConfirm(context, it)
        })
    TrackList(
        modifier = modifier,
        state = state,
        contentPadding = padding,
        trackListProvider = viewModel::tracks::get,
        trackViewCallback = itemCallback)
}