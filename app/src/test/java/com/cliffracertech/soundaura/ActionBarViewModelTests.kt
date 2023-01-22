/* This file is part of SoundAura, which is released under the terms of the Apache
 * License 2.0. See license.md in the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(sdk=[30])
@RunWith(RobolectricTestRunner::class)
class ActionBarViewModelTests {
    private lateinit var instance: ActionBarViewModel
    private lateinit var searchQueryState: SearchQueryState
    private lateinit var navigationState: MainActivityNavigationState

    @Before fun init() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        navigationState = MainActivityNavigationState()
        searchQueryState = SearchQueryState()
        instance = ActionBarViewModel(context.dataStore, navigationState, searchQueryState)
    }

    @Test fun title() {
        assertThat(navigationState.showingAppSettings).isFalse()
        assertThat(instance.title.stringResId).isEqualTo(R.string.app_name)
        navigationState.showingAppSettings = true
        assertThat(instance.title.stringResId).isEqualTo(R.string.app_settings_description)
        navigationState.showingAppSettings = false
        assertThat(instance.title.stringResId).isEqualTo(R.string.app_name)
    }

    @Test fun search_button_clicks() {
        assertThat(instance.searchQuery).isNull()
        instance.onSearchButtonClick()
        assertThat(instance.searchQuery).isEqualTo("")
        instance.onSearchButtonClick()
        assertThat(instance.searchQuery).isNull()
        instance.searchQuery = "test query"
        assertThat(instance.searchQuery).isEqualTo("test query")
        instance.onSearchButtonClick()
        assertThat(instance.searchQuery).isNull()
    }

    @Test fun search_query_property_updates() {
        assertThat(instance.searchQuery).isNull()
        assertThat(searchQueryState.query.value).isNull()
        instance.searchQuery = "test query"
        assertThat(instance.searchQuery).isEqualTo("test query")
        assertThat(searchQueryState.query.value).isEqualTo("test query")
        instance.onSearchButtonClick()
        assertThat(instance.searchQuery).isNull()
        assertThat(searchQueryState.query.value).isNull()
        searchQueryState.query.value = "test query"
        assertThat(instance.searchQuery).isEqualTo("test query")
    }

    @Test fun settings_button_affects_underlying_state() {
        assertThat(navigationState.showingAppSettings).isFalse()
        instance.onSettingsButtonClick()
        assertThat(navigationState.showingAppSettings).isTrue()
        instance.onSettingsButtonClick()
        assertThat(navigationState.showingAppSettings).isTrue()
    }

    @Test fun back_button_exits_app_settings() {
        settings_button_affects_underlying_state()
        assertThat(instance.onBackButtonClick()).isTrue()
        assertThat(instance.showingAppSettings).isFalse()
        assertThat(instance.onBackButtonClick()).isFalse()
        assertThat(instance.showingAppSettings).isFalse()
    }

    @Test fun settings_button_clears_search_query() {
        searchQueryState.query.value = "test query"
        assertThat(instance.searchQuery).isEqualTo("test query")
        instance.onSettingsButtonClick()
        assertThat(instance.searchQuery).isNull()
        instance.onBackButtonClick()
        assertThat(instance.searchQuery).isNull()
    }
}