/* This file is part of SoundAura, which is released under the terms of the Apache
 * License 2.0. See license.md in the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsViewModelTests {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val coroutineDispatcher = TestCoroutineDispatcher()
    private val coroutineScope = TestCoroutineScope(coroutineDispatcher + Job())
    private val dataStore = PreferenceDataStoreFactory.create(scope = coroutineScope) {
        context.preferencesDataStoreFile("testDatastore")
    }

    private val appThemeKey = intPreferencesKey(
        context.getString(R.string.pref_app_theme_key))
    private val autoPauseDuringCallKey = booleanPreferencesKey(
        context.getString(R.string.pref_auto_pause_during_calls_key))

    private lateinit var instance: SettingsViewModel

    private suspend fun updatedPreferences() = dataStore.data.first().toPreferences()

    @Before fun init() {
        instance = SettingsViewModel(context, dataStore, coroutineScope)
        Dispatchers.setMain(coroutineDispatcher)
    }

    @After fun cleanUp() {
        Dispatchers.resetMain()
        coroutineDispatcher.cleanupTestCoroutines()
        coroutineScope.runBlockingTest {
            dataStore.edit { it.clear() }
        }
        coroutineScope.cancel()
    }

    @Test fun viewModelDefaultValues() {
        assertThat(instance.appTheme).isEqualTo(AppTheme.UseSystem)
        assertThat(instance.autoPauseDuringCall).isFalse()
        assertThat(instance.showingAskForPhoneStatePermissionDialog).isFalse()

    }

    @Test fun onAppThemeClick() = runBlockingTest {
        viewModelDefaultValues()
        instance.onAppThemeClick(AppTheme.Dark)
        assertThat(updatedPreferences()[appThemeKey]).isEqualTo(AppTheme.Dark.ordinal)
        assertThat(instance.appTheme).isEqualTo(AppTheme.Dark)
        instance.onAppThemeClick(AppTheme.Light)
        assertThat(updatedPreferences()[appThemeKey]).isEqualTo(AppTheme.Light.ordinal)
        assertThat(instance.appTheme).isEqualTo(AppTheme.Light)
    }

    @Test fun onAutoPauseDuringCallClickWithoutPermission() = runBlockingTest {
        viewModelDefaultValues()
        instance.onAutoPauseDuringCallClick()
        assertThat(instance.showingAskForPhoneStatePermissionDialog).isTrue()
        assertThat(updatedPreferences()[autoPauseDuringCallKey]).isNotEqualTo(true)
        assertThat(instance.autoPauseDuringCall).isFalse()
    }

    @Test fun onAskForPhoneStatePermissionDialogDismiss() {
        onAutoPauseDuringCallClickWithoutPermission()
        instance.onAskForPhoneStatePermissionDialogDismiss()
        assertThat(instance.showingAskForPhoneStatePermissionDialog).isFalse()
    }

    @Test fun onAskForPhoneStatePermissionDialogConfirm() = runBlockingTest {
        onAutoPauseDuringCallClickWithoutPermission()
        instance.onAskForPhoneStatePermissionDialogConfirm(permissionGranted = false)
        assertThat(instance.showingAskForPhoneStatePermissionDialog).isFalse()
        assertThat(updatedPreferences()[autoPauseDuringCallKey]).isNotEqualTo(true)
        assertThat(instance.autoPauseDuringCall).isFalse()

        onAutoPauseDuringCallClickWithoutPermission()
        instance.onAskForPhoneStatePermissionDialogConfirm(permissionGranted = true)
        assertThat(instance.showingAskForPhoneStatePermissionDialog).isFalse()
        assertThat(updatedPreferences()[autoPauseDuringCallKey]).isTrue()
        assertThat(instance.autoPauseDuringCall).isTrue()
    }

    @Test fun onAutoPauseDuringCallClickWithPermission() = runBlockingTest {
        onAskForPhoneStatePermissionDialogConfirm()
        instance.onAutoPauseDuringCallClick()
        assertThat(instance.showingAskForPhoneStatePermissionDialog).isFalse()
        assertThat(updatedPreferences()[autoPauseDuringCallKey]).isFalse()
        assertThat(instance.autoPauseDuringCall).isFalse()

        instance.onAutoPauseDuringCallClick()
        assertThat(instance.showingAskForPhoneStatePermissionDialog).isFalse()
        assertThat(updatedPreferences()[autoPauseDuringCallKey]).isTrue()
        assertThat(instance.autoPauseDuringCall).isTrue()
    }

    @Test fun autoPauseDuringCallAlwaysFalseWithoutPermission() = runBlockingTest {
        viewModelDefaultValues()
        dataStore.edit {
            it[autoPauseDuringCallKey] = true
        }
        assertThat(updatedPreferences()[autoPauseDuringCallKey]).isTrue()
        assertThat(instance.autoPauseDuringCall).isFalse()
    }
}