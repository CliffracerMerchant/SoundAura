/*
 * This file is part of SoundAura, which is released under the terms of the Apache
 * License 2.0. See license.md in the project's root directory to see the full license.
 */

package com.cliffracertech.soundaura

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ActionBarViewModel @Inject constructor(
    @ApplicationContext context: Context,
    searchQueryState: SearchQueryState,
) : ViewModel() {
    private val dataStore = context.dataStore

    private val trackSortKey = intPreferencesKey(context.getString(R.string.pref_sort_key))
    val trackSort by dataStore.enumPreferenceState<Track.Sort>(trackSortKey, viewModelScope)

    fun ontrackSortOptionClick(newValue: Track.Sort) {
        viewModelScope.launch {
            dataStore.edit { it[trackSortKey] = newValue.ordinal }
        }
    }

    var searchQuery by searchQueryState.query

    fun onSearchButtonClick() {
        searchQuery = if (searchQuery == null) "" else null
    }
}
