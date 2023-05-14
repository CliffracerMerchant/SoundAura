/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.actionbar

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Switch
import androidx.compose.material.TabRowDefaults.Divider
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cliffracertech.soundaura.R
import com.cliffracertech.soundaura.edit
import com.cliffracertech.soundaura.enumPreferenceState
import com.cliffracertech.soundaura.model.NavigationState
import com.cliffracertech.soundaura.model.SearchQueryState
import com.cliffracertech.soundaura.model.StringResource
import com.cliffracertech.soundaura.model.database.Track
import com.cliffracertech.soundaura.preferenceState
import com.cliffracertech.soundaura.settings.PrefKeys
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

@HiltViewModel class ActionBarViewModel(
    private val dataStore: DataStore<Preferences>,
    private val navigationState: NavigationState,
    searchQueryState: SearchQueryState,
    coroutineScope: CoroutineScope? = null
) : ViewModel() {

    @Inject constructor(
        dataStore: DataStore<Preferences>,
        navigationState: NavigationState,
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

    private val showActiveTracksFirstKey = booleanPreferencesKey(PrefKeys.showActiveTracksFirst)
    val showActiveTracksFirst by dataStore.preferenceState(showActiveTracksFirstKey, false, scope)

    fun onShowActiveTracksFirstSwitchClick() =
        dataStore.edit(showActiveTracksFirstKey, !showActiveTracksFirst, scope)

    private val trackSortKey = intPreferencesKey(PrefKeys.trackSort)
    val trackSort by dataStore.enumPreferenceState<Track.Sort>(trackSortKey, scope)

    fun onTrackSortOptionClick(newValue: Track.Sort) =
        dataStore.edit(trackSortKey, newValue.ordinal, scope)

    fun onSearchButtonClick() {
        searchQuery = if (searchQuery == null) "" else null
    }

    fun onSettingsButtonClick() {
        searchQuery = null
        navigationState.showingPresetSelector = false
        navigationState.showingAppSettings = true
    }

    fun onBackButtonClick() = navigationState.onBackButtonClick()
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
    val context = LocalContext.current
    val title by remember { derivedStateOf {
        viewModel.title.resolve(context)
    }}
    ListActionBar(
        modifier = modifier,
        showBackButtonForNavigation = viewModel.showingAppSettings,
        onBackButtonClick = remember{{
            if (!viewModel.onBackButtonClick())
                onUnhandledBackButtonClick()
        }},
        title = title,
        searchQuery = viewModel.searchQuery,
        onSearchQueryChanged = viewModel::searchQuery::set,
        showRightAlignedContent = !viewModel.showingAppSettings,
        onSearchButtonClick = viewModel::onSearchButtonClick,
        sortOptions = Track.Sort.values(),
        sortOptionNames = Track.Sort.stringValues(),
        currentSortOption = viewModel.trackSort,
        onSortOptionClick = viewModel::onTrackSortOptionClick,
        otherSortMenuContent = { onDismissRequest ->
            DropdownMenuItem(onClick = {
                onDismissRequest()
                viewModel.onShowActiveTracksFirstSwitchClick()
            }) {
                Text(stringResource(R.string.show_active_tracks_first),
                     style = MaterialTheme.typography.button)
                Spacer(Modifier.weight(1f).widthIn(12.dp))
                Switch(checked = viewModel.showActiveTracksFirst,
                       onCheckedChange = null)
            }
            Divider()
        }, otherContent = {
            IconButton(viewModel::onSettingsButtonClick) {
                Icon(Icons.Default.Settings, stringResource(R.string.settings))
            }
        })
}
