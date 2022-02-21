/*
 * This file is part of SoundAura, which is released under the terms of the Apache
 * License 2.0. See license.md in the project's root directory to see the full license.
 */

package com.cliffracertech.soundaura

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
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

/** Compose a ListActionBar that switches between a normal state with all
 * buttons enabled, and an alternative state with most buttons disabled
 * according to the value of @param showingAppSettings. Back and settings
 * button clicks will invoke @param onBackButtonClick and @param
 * onSettingsButtonClick, respectively. */
@Composable fun SoundAuraActionBar(
    showingAppSettings: Boolean,
    onBackButtonClick: () -> Unit,
    onSettingsButtonClick: () -> Unit,
) {
    val viewModel: ActionBarViewModel = viewModel()
    val title = if (!showingAppSettings) stringResource(R.string.app_name)
                else stringResource(R.string.app_settings_description)
    ListActionBar(
        showBackButtonForNavigation = showingAppSettings,
        onBackButtonClick = onBackButtonClick,
        title = title,
        searchQuery = viewModel.searchQuery,
        onSearchQueryChanged = { viewModel.searchQuery = it },
        showSearchAndChangeSortButtons = !showingAppSettings,
        onSearchButtonClick = viewModel::onSearchButtonClick,
        sortOptions = Track.Sort.values(),
        sortOptionNames = Track.Sort.stringValues(),
        currentSortOption = viewModel.trackSort,
        onSortOptionClick = viewModel::ontrackSortOptionClick,
    ) {
        SettingsButton(onClick = onSettingsButtonClick)
    }
}
