/* This file is part of SoundAura, which is released under the terms of the Apache
 * License 2.0. See license.md in the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.compose.runtime.mutableStateOf
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SoundMixEditorViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val trackDao: TrackDao,
    private val messageHandler: MessageHandler,
    searchQueryState: SearchQueryState
) : ViewModel() {

    private val trackSortKey = intPreferencesKey(context.getString(R.string.pref_sort_key))
    private val trackSort = context.dataStore.enumPreferenceFlow<Track.Sort>(trackSortKey)

    var tracks by mutableStateOf<List<Track>>(emptyList())
        private set

    init {
        combine(trackSort, searchQueryState.query, trackDao::getAllTracks)
            .transformLatest { emitAll(it) }
            .onEach { tracks = it }.launchIn(viewModelScope)
    }

    fun onConfirmAddTrackDialog(track: Track) {
        viewModelScope.launch {
            try { trackDao.insert(track) }
            catch(e: SQLiteConstraintException) {
                messageHandler.postMessage(StringResource(R.string.track_already_exists_error_message))
            }
        }
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