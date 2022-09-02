/* This file is part of SoundAura, which is released under
   the terms of the Apache License 2.0. See license.md in
   the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cliffracertech.soundaura.SoundAura.pref_key_trackSort
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InstrumentedActionBarViewModelTests {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val coroutineDispatcher = StandardTestDispatcher()
    private val coroutineScope = TestCoroutineScope()
    private val dataStore = PreferenceDataStoreFactory.create(scope = coroutineScope) {
        context.preferencesDataStoreFile("testDatastore")
    }
    private val trackSortKey = intPreferencesKey(pref_key_trackSort)
    private lateinit var instance: ActionBarViewModel

    private suspend fun updatedPreferences() = dataStore.data.first().toPreferences()

    @Before fun init() {
        instance = ActionBarViewModel(dataStore, MainActivityNavigationState(),
                                      SearchQueryState(), coroutineScope)
        Dispatchers.setMain(coroutineDispatcher)
    }

    @After fun cleanUp() {
        runTest { dataStore.edit { it.clear() } }
        coroutineScope.cancel()
        Dispatchers.resetMain()
    }

    @Test fun trackSort() {
        assertThat(instance.trackSort).isEqualTo(Track.Sort.values().first())
        runTest { dataStore.edit { it[trackSortKey] = Track.Sort.NameDesc.ordinal } }
        assertThat(instance.trackSort).isEqualTo(Track.Sort.NameDesc)
        runTest { dataStore.edit { it[trackSortKey] = Track.Sort.NameAsc.ordinal } }
        assertThat(instance.trackSort).isEqualTo(Track.Sort.NameAsc)
    }

    @Test fun onTrackSortOptionClick() {
        assertThat(instance.trackSort).isEqualTo(Track.Sort.values().first())
        instance.onTrackSortOptionClick(Track.Sort.NameDesc)
        assertThat(instance.trackSort).isEqualTo(Track.Sort.NameDesc)
        runTest {
            assertThat(updatedPreferences()[trackSortKey]).isEqualTo(Track.Sort.NameDesc.ordinal)
        }
        instance.onTrackSortOptionClick(Track.Sort.NameAsc)
        assertThat(instance.trackSort).isEqualTo(Track.Sort.NameAsc)
        runTest {
            assertThat(updatedPreferences()[trackSortKey]).isEqualTo(Track.Sort.NameAsc.ordinal)
        }
    }
}