/* This file is part of SoundAura, which is released under the terms of the Apache
 * License 2.0. See license.md in the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Switch
import androidx.compose.material.TabRowDefaults.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cliffracertech.soundaura.SoundAura.pref_key_trackSort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ActionBarViewModel(
    private val dataStore: DataStore<Preferences>,
    private val navigationState: MainActivityNavigationState,
    searchQueryState: SearchQueryState,
    coroutineScope: CoroutineScope? = null
) : ViewModel() {

    @Inject constructor(
        dataStore: DataStore<Preferences>,
        navigationState: MainActivityNavigationState,
        searchQueryState: SearchQueryState
    ) : this(dataStore, navigationState, searchQueryState, null)

    private val scope = coroutineScope ?: viewModelScope
    val showingAppSettings get() = navigationState.showingAppSettings
    var searchQuery by searchQueryState.query

    val title by derivedStateOf {
        StringResource(
            if (showingAppSettings)
                R.string.app_settings_description
            else R.string.app_name)
    }

    private val trackSortKey = intPreferencesKey(pref_key_trackSort)
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

/**
 * Compose a [ListActionBar] with state provided by an instance of [ActionBarViewModel].
 *
 * @param onUnhandledBackButtonClick The callback that will
 *     be invoked if a back button click is not handled.
 * @param modifier The [Modifier] that will be used for the action bar.
 */
@Composable fun SoundAuraActionBar(
    onUnhandledBackButtonClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ActionBarViewModel = viewModel()
    var showActiveTracksFirst by rememberSaveable { mutableStateOf(false) }

    ListActionBar(
        modifier = modifier,
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
        otherSortMenuContent = { onDismissRequest ->
            DropdownMenuItem(onClick = {
                onDismissRequest()
                showActiveTracksFirst = !showActiveTracksFirst
            }) {
                Text(stringResource(R.string.show_active_tracks_first),
                     style = MaterialTheme.typography.button)
                Spacer(Modifier.weight(1f).widthIn(12.dp))
                Switch(checked = showActiveTracksFirst,
                       onCheckedChange = null)
            }
            Divider()
        }, otherContent = {
            SettingsButton(viewModel::onSettingsButtonClick)
        })
}
