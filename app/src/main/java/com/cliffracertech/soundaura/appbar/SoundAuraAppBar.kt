/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.appbar

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalMinimumInteractiveComponentEnforcement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
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
import com.cliffracertech.soundaura.model.database.Playlist
import com.cliffracertech.soundaura.preferenceState
import com.cliffracertech.soundaura.settings.PrefKeys
import com.cliffracertech.soundaura.ui.SimpleIconButton
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

/**
 * A [ViewModel] that contains state and callbacks for the application's top app bar.
 *
 * The properties [onBackButtonClick], [title], [showIconButtons],
 * [searchQueryViewState], and [sortMenuState] can be used as the same-named
 * parameters in a [ListAppBar]. In addition to these properties, the property
 * [showActivePlaylistsFirstSwitchState] should be used as the state for a
 * 'Show active playlists first' switch within the [ListAppBar]'s sort popup
 * menu (i.e. through its otherSortMenuContent parameter). The otherIconButtons
 * parameter should contain a settings icon button that uses the property
 * [onSettingsButtonClick] as its onClick action.
 */
@HiltViewModel class AppBarViewModel(
    private val dataStore: DataStore<Preferences>,
    private val navigationState: NavigationState,
    private val searchQuery: SearchQueryState,
    coroutineScope: CoroutineScope? = null
) : ViewModel() {

    @Inject constructor(
        dataStore: DataStore<Preferences>,
        navigationState: NavigationState,
        searchQueryState: SearchQueryState
    ) : this(dataStore, navigationState, searchQueryState, null)

    private val scope = coroutineScope ?: viewModelScope

    val onBackButtonClick get() = when {
        navigationState.willConsumeBackButtonClick ->
            navigationState::onBackButtonClick
        searchQuery.isActive ->
            searchQuery::clear
        else -> null
    }

    val title get() = StringResource(
        if (navigationState.showingAppSettings)
            R.string.app_settings_description
        else R.string.app_name)

    val showIconButtons get() = !navigationState.showingAppSettings

    val searchQueryViewState = SearchQueryViewState(
        getQuery = searchQuery::value,
        onQueryChange = searchQuery::set,
        onButtonClick = searchQuery::toggleIsActive,
        getIcon = {
            if (searchQuery.isActive) SearchQueryViewState.Icon.Close
            else                      SearchQueryViewState.Icon.Search
        })

    private val playlistSortKey = intPreferencesKey(PrefKeys.playlistSort)
    private val playlistSort by dataStore.enumPreferenceState<Playlist.Sort>(playlistSortKey, scope)
    private val playlistSortOptions = Playlist.Sort.entries
    val sortMenuState = SortMenuState(
        optionNames = @Composable { context ->
            // The locale is used as a key so that configuration changes won't
            // leave the sort option names as their previous-locale values
            remember(context.resources.configuration.locales.get(0)) {
                List(playlistSortOptions.size) {
                    playlistSortOptions[it].name(context)
                }.toImmutableList()
            }
        }, getCurrentOptionIndex = { playlistSort.ordinal },
        onOptionClick = { dataStore.edit(playlistSortKey, it, scope) })

    private val showActivePlaylistsFirstKey = booleanPreferencesKey(PrefKeys.showActivePlaylistsFirst)
    private val showActivePlaylistsFirst by dataStore.preferenceState(showActivePlaylistsFirstKey, false, scope)
    val showActivePlaylistsFirstSwitchState = SwitchState(
        getChecked = ::showActivePlaylistsFirst,
        onClick = {
            sortMenuState.onPopupDismissRequest()
            dataStore.edit(showActivePlaylistsFirstKey, !showActivePlaylistsFirst, scope)
        })

    fun onSettingsButtonClick() {
        if (searchQuery.isActive)
            searchQuery.clear()
        navigationState.showingPresetSelector = false
        navigationState.showingAppSettings = true
    }
}

/** Compose a [ListAppBar] with state provided by an instance of [AppBarViewModel]. */
@Composable fun SoundAuraAppBar(
    modifier: Modifier = Modifier,
) {
    val viewModel: AppBarViewModel = viewModel()
    val context = LocalContext.current
    val title = remember(viewModel.title) {
        viewModel.title.resolve(context)
    }
    ListAppBar(
        modifier = modifier,
        // horizontal window insets must be taken into account even though the bar
        // is intended to be at the top of the screen because the navigation bar
        // will appear at the end of the screen when 3-button navigation is in use
        // for a landscape orientation
        insets = WindowInsets.systemBars.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        onBackButtonClick = viewModel.onBackButtonClick,
        title = title,
        showIconButtons = viewModel.showIconButtons,
        searchQueryState = viewModel.searchQueryViewState,
        sortMenuState = viewModel.sortMenuState,
        otherSortMenuContent = {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.show_active_playlists_first)) },
                onClick = viewModel.showActivePlaylistsFirstSwitchState.onClick,
                trailingIcon = {
                    CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
                        Switch(checked = viewModel.showActivePlaylistsFirstSwitchState.checked,
                               onCheckedChange = null,
                               modifier = Modifier.scale(0.8f).padding(start = 8.dp))
                    }
                })
            HorizontalDivider()
        }, otherIconButtons = {
            SimpleIconButton(
                icon = Icons.Default.Settings,
                contentDescription = stringResource(R.string.app_settings_description),
                tint = MaterialTheme.colorScheme.onPrimary,
                onClick = viewModel::onSettingsButtonClick)
        })
}
