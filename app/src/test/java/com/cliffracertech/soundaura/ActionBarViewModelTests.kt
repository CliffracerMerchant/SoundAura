/* This file is part of SoundAura, which is released under the terms of the Apache
 * License 2.0. See license.md in the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth
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

    @Before fun init() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        searchQueryState = SearchQueryState()
        instance = ActionBarViewModel(context, context.dataStore, searchQueryState)
    }

    @Test fun searchButtonClicks() {
        Truth.assertThat(instance.searchQuery).isNull()
        instance.onSearchButtonClick()
        Truth.assertThat(instance.searchQuery).isEqualTo("")
        instance.onSearchButtonClick()
        Truth.assertThat(instance.searchQuery).isNull()
        instance.searchQuery = "test query"
        Truth.assertThat(instance.searchQuery).isEqualTo("test query")
        instance.onSearchButtonClick()
        Truth.assertThat(instance.searchQuery).isNull()
    }

    @Test fun searchQueryMatchesUnderlyingState() {
        Truth.assertThat(instance.searchQuery).isNull()
        Truth.assertThat(searchQueryState.query.value).isNull()
        instance.searchQuery = "test query"
        Truth.assertThat(instance.searchQuery).isEqualTo("test query")
        Truth.assertThat(searchQueryState.query.value).isEqualTo("test query")
        instance.onSearchButtonClick()
        Truth.assertThat(instance.searchQuery).isNull()
        Truth.assertThat(searchQueryState.query.value).isNull()
        searchQueryState.query.value = "test query"
        Truth.assertThat(instance.searchQuery).isEqualTo("test query")
    }
}