/* This file is part of SoundAura, which is released under the terms of the Apache
 * License 2.0. See license.md in the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.Manifest.permission.READ_PHONE_STATE
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.util.Log
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
/**
 * Many of these tests require the read phone state permission to be either
 * granted or not granted to work properly. They are designed to always pass
 * by returning early when the read phone state permission is not in the state
 * needed for the test, but all of the tests should pass both with and without
 * the read phone state permission to ensure that all functionality is working
 * properly. Unfortunately there doesn't seem to be a way to easily revoke
 * permissions during testing, and no way to grant them within the app without
 * using UI coordinator.7
 */
class SettingsViewModelTests {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val coroutineDispatcher = TestCoroutineDispatcher()
    private val coroutineScope = TestCoroutineScope(coroutineDispatcher + Job())
    private val dataStore = PreferenceDataStoreFactory.create(scope = coroutineScope) {
        context.preferencesDataStoreFile("testDatastore")
    }

    private val appThemeKey = intPreferencesKey(
        context.getString(R.string.pref_app_theme_key))
    private val ignoreAudioFocusKey = booleanPreferencesKey(
        context.getString(R.string.pref_play_in_background_key))
    private val autoPauseDuringCallKey = booleanPreferencesKey(
        context.getString(R.string.pref_auto_pause_during_calls_key))

    private lateinit var instance: SettingsViewModel

    private suspend fun updatedPreferences() = dataStore.data.first().toPreferences()
    private val hasReadPhoneStatePermission get() =
        context.checkSelfPermission(READ_PHONE_STATE) == PERMISSION_GRANTED

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

    @Test fun defaultValues() {
        assertThat(instance.appTheme).isEqualTo(AppTheme.UseSystem)
        assertThat(instance.playInBackground).isFalse()
        assertThat(instance.showingPlayInBackgroundExplanation).isFalse()
        assertThat(instance.autoPauseDuringCallSettingVisible).isFalse()
        assertThat(instance.autoPauseDuringCall).isFalse()
        assertThat(instance.showingPhoneStatePermissionDialog).isFalse()

    }

    @Test fun onAppThemeClick() = runBlockingTest {
        defaultValues()
        instance.onAppThemeClick(AppTheme.Dark)
        assertThat(updatedPreferences()[appThemeKey]).isEqualTo(AppTheme.Dark.ordinal)
        assertThat(instance.appTheme).isEqualTo(AppTheme.Dark)
        instance.onAppThemeClick(AppTheme.Light)
        assertThat(updatedPreferences()[appThemeKey]).isEqualTo(AppTheme.Light.ordinal)
        assertThat(instance.appTheme).isEqualTo(AppTheme.Light)
    }

    @Test fun onPlayInBackgroundTitleClick() {
        defaultValues()
        instance.onPlayInBackgroundTitleClick()
        assertThat(instance.showingPlayInBackgroundExplanation).isTrue()
    }

    @Test fun onPlayInBackgroundExplanationDialogDismiss() = runBlockingTest {
        onPlayInBackgroundTitleClick()
        instance.onPlayInBackgroundExplanationDismiss()
        assertThat(instance.showingPlayInBackgroundExplanation).isFalse()
    }

    @Test fun onPlayInBackgroundSwitchClick() = runBlockingTest {
        defaultValues()
        instance.onPlayInBackgroundSwitchClick()
        assertThat(updatedPreferences()[ignoreAudioFocusKey]).isTrue()
        assertThat(instance.playInBackground).isTrue()
        assertThat(instance.autoPauseDuringCallSettingVisible).isTrue()

        instance.onPlayInBackgroundSwitchClick()
        assertThat(updatedPreferences()[ignoreAudioFocusKey]).isFalse()
        assertThat(instance.playInBackground).isFalse()
        assertThat(instance.autoPauseDuringCallSettingVisible).isFalse()

        instance.onAutoPauseDuringCallClick()
        assertThat(instance.showingPhoneStatePermissionDialog).isFalse()
    }

    @Test fun onAutoPauseDuringCallClickWithPermission() = runBlockingTest {
        if (!hasReadPhoneStatePermission)
            return@runBlockingTest
        defaultValues()

        instance.onPlayInBackgroundSwitchClick()
        instance.onAutoPauseDuringCallClick()
        assertThat(instance.showingPhoneStatePermissionDialog).isFalse()
        assertThat(updatedPreferences()[autoPauseDuringCallKey]).isTrue()
        assertThat(instance.autoPauseDuringCall).isTrue()

        instance.onAutoPauseDuringCallClick()
        assertThat(instance.showingPhoneStatePermissionDialog).isFalse()
        assertThat(updatedPreferences()[autoPauseDuringCallKey]).isFalse()
        assertThat(instance.autoPauseDuringCall).isFalse()
    }

    @Test fun onAutoPauseDuringCallClickWithoutPermission() = runBlockingTest {
        if (hasReadPhoneStatePermission)
            return@runBlockingTest
        defaultValues()
        instance.onPlayInBackgroundSwitchClick()
        instance.onAutoPauseDuringCallClick()
        assertThat(instance.showingPhoneStatePermissionDialog).isTrue()
        assertThat(updatedPreferences()[autoPauseDuringCallKey]).isNotEqualTo(true)
        assertThat(instance.autoPauseDuringCall).isFalse()
    }

    @Test fun onAskForPhoneStatePermissionDialogDismiss() {
        if (hasReadPhoneStatePermission) return
        onAutoPauseDuringCallClickWithoutPermission()
        instance.onPhoneStatePermissionDialogDismiss()
        assertThat(instance.showingPhoneStatePermissionDialog).isFalse()
    }

    @Test fun onPhoneStatePermissionDialogConfirm() = runBlockingTest {
        if (hasReadPhoneStatePermission)
            return@runBlockingTest
        instance.onPlayInBackgroundSwitchClick()
        assertThat(instance.autoPauseDuringCall).isFalse()
        assertThat(instance.showingPhoneStatePermissionDialog).isFalse()

        instance.onAutoPauseDuringCallClick()
        assertThat(instance.showingPhoneStatePermissionDialog).isTrue()
        assertThat(updatedPreferences()[autoPauseDuringCallKey]).isNotEqualTo(true)
        assertThat(instance.autoPauseDuringCall).isFalse()

        instance.onPhoneStatePermissionDialogConfirm(permissionGranted = false)
        assertThat(instance.showingPhoneStatePermissionDialog).isFalse()
        assertThat(updatedPreferences()[autoPauseDuringCallKey]).isNotEqualTo(true)
        assertThat(instance.autoPauseDuringCall).isFalse()

        instance.onAutoPauseDuringCallClick()
        instance.onPhoneStatePermissionDialogConfirm(permissionGranted = true)
        assertThat(instance.showingPhoneStatePermissionDialog).isFalse()
        assertThat(updatedPreferences()[autoPauseDuringCallKey]).isTrue()
        assertThat(instance.autoPauseDuringCall).isTrue()
    }

    @Test fun autoPauseDuringCallAlwaysFalseWithoutPermissionAndIgnoreAudioFocus() = runBlockingTest {
        defaultValues()
        dataStore.edit {
            it[autoPauseDuringCallKey] = true
        }
        assertThat(updatedPreferences()[autoPauseDuringCallKey]).isTrue()
        assertThat(instance.autoPauseDuringCall).isFalse()

        instance.onPlayInBackgroundSwitchClick()
        assertThat(instance.autoPauseDuringCall).isEqualTo(hasReadPhoneStatePermission)
    }
}