/* This file is part of SoundAura, which is released under the terms of the Apache
 * License 2.0. See license.md in the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ActionBarViewModel(
    context: Context,
    private val dataStore: DataStore<Preferences>,
    private val navigationState: MainActivityNavigationState,
    searchQueryState: SearchQueryState,
    coroutineScope: CoroutineScope? = null
) : ViewModel() {

    @Inject constructor(
        @ApplicationContext context: Context,
        dataStore: DataStore<Preferences>,
        navigationState: MainActivityNavigationState,
        searchQueryState: SearchQueryState
    ) : this(context, dataStore, navigationState, searchQueryState, null)

    private val scope = coroutineScope ?: viewModelScope
    val showingAppSettings get() = navigationState.showingAppSettings
    var searchQuery by searchQueryState.query

    val title by derivedStateOf {
        StringResource(
            if (showingAppSettings)
                R.string.app_settings_description
            else R.string.app_name)
    }

    private val trackSortKey = intPreferencesKey(context.getString(R.string.pref_sort_key))
    val trackSort by dataStore.enumPreferenceState<Track.Sort>(trackSortKey, scope)

    fun onTrackSortOptionClick(newValue: Track.Sort) {
        scope.launch { dataStore.edit {
            it[trackSortKey] = newValue.ordinal
        }}
    }

    fun onSearchButtonClick() {
        searchQuery = if (searchQuery == null) "" else null
    }

    fun onSettingsButtonClick() {
        searchQuery = null
        navigationState.showingAppSettings = true
    }

    fun onBackButtonClick() =
        if (showingAppSettings) {
            navigationState.showingAppSettings = false
            true
        } else false
}

/** Compose a ListActionBar that switches between a normal state with all
 * buttons enabled, and an alternative state with most buttons disabled.
 * @param onUnhandledBackButtonClick The callback that will be invoked if
 *                                   a back button click is not handled. */
@Composable fun SoundAuraActionBar(onUnhandledBackButtonClick: () -> Unit) {
    val viewModel: ActionBarViewModel = viewModel()

    ListActionBar(
        showBackButtonForNavigation = viewModel.showingAppSettings,
        onBackButtonClick = {
            if (!viewModel.onBackButtonClick())
                onUnhandledBackButtonClick()
        }, title = viewModel.title.resolve(LocalContext.current),
        searchQuery = viewModel.searchQuery,
        onSearchQueryChanged = { viewModel.searchQuery = it },
        showRightAlignedContent = !viewModel.showingAppSettings,
        onSearchButtonClick = viewModel::onSearchButtonClick,
        sortOptions = Track.Sort.values(),
        sortOptionNames = Track.Sort.stringValues(),
        currentSortOption = viewModel.trackSort,
        onSortOptionClick = viewModel::onTrackSortOptionClick,
        otherContent = { SettingsButton(viewModel::onSettingsButtonClick) })
}
