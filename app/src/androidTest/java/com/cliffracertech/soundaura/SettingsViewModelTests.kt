/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.Manifest.permission.READ_PHONE_STATE
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cliffracertech.soundaura.settings.AppTheme
import com.cliffracertech.soundaura.settings.OnZeroVolumeAudioDeviceBehavior
import com.cliffracertech.soundaura.settings.PrefKeys
import com.cliffracertech.soundaura.settings.SettingsViewModel
import com.google.common.truth.Truth.assertThat
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
 * using UI coordinator.
 */
class SettingsViewModelTests {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = TestCoroutineScope()
    private val dataStore = PreferenceDataStoreFactory
        .create(scope = scope) { context.preferencesDataStoreFile("testDatastore") }
    private val appThemeKey = intPreferencesKey(PrefKeys.appTheme)
    private val playInBackgroundKey = booleanPreferencesKey(PrefKeys.playInBackground)
    private val autoPauseDuringCallKey = booleanPreferencesKey(PrefKeys.autoPauseDuringCalls)
    private val onZeroVolumeAudioDeviceBehaviorKey = intPreferencesKey(PrefKeys.onZeroVolumeAudioDeviceBehavior)

    private lateinit var instance: SettingsViewModel

    private suspend fun updatedPreferences() = dataStore.data.first().toPreferences()
    private val hasReadPhoneStatePermission get() =
        context.checkSelfPermission(READ_PHONE_STATE) == PERMISSION_GRANTED

    @Before fun init() {
        instance = SettingsViewModel(context, dataStore, scope)
    }

    @After fun cleanUp() {
        runTest { dataStore.edit { it.clear() } }
        scope.cancel()
    }

    @Test fun default_values() {
        assertThat(instance.appTheme).isEqualTo(AppTheme.UseSystem)
        assertThat(instance.playInBackground).isFalse()
        assertThat(instance.showingPlayInBackgroundExplanation).isFalse()
        assertThat(instance.autoPauseDuringCallSettingVisible).isFalse()
        assertThat(instance.autoPauseDuringCall).isFalse()
        assertThat(instance.showingPhoneStatePermissionDialog).isFalse()
        assertThat(instance.onZeroVolumeAudioDeviceBehavior).isEqualTo(
            OnZeroVolumeAudioDeviceBehavior.values()[0])
    }

    @Test fun on_app_theme_click() = runTest {
        default_values()
        instance.onAppThemeClick(AppTheme.Dark)
        assertThat(updatedPreferences()[appThemeKey]).isEqualTo(AppTheme.Dark.ordinal)
        assertThat(instance.appTheme).isEqualTo(AppTheme.Dark)
        instance.onAppThemeClick(AppTheme.Light)
        assertThat(updatedPreferences()[appThemeKey]).isEqualTo(AppTheme.Light.ordinal)
        assertThat(instance.appTheme).isEqualTo(AppTheme.Light)
    }

    @Test fun on_play_in_background_title_click() {
        default_values()
        instance.onPlayInBackgroundTitleClick()
        assertThat(instance.showingPlayInBackgroundExplanation).isTrue()
    }

    @Test fun on_play_in_background_explanation_dialog_dismiss() = runTest {
        on_play_in_background_title_click()
        instance.onPlayInBackgroundExplanationDismiss()
        assertThat(instance.showingPlayInBackgroundExplanation).isFalse()
    }

    @Test fun on_play_in_background_switch_click() = runTest {
        default_values()
        instance.onPlayInBackgroundSwitchClick()
        assertThat(updatedPreferences()[playInBackgroundKey]).isTrue()
        assertThat(instance.playInBackground).isTrue()
        assertThat(instance.autoPauseDuringCallSettingVisible).isTrue()

        instance.onPlayInBackgroundSwitchClick()
        assertThat(updatedPreferences()[playInBackgroundKey]).isFalse()
        assertThat(instance.playInBackground).isFalse()
        assertThat(instance.autoPauseDuringCallSettingVisible).isFalse()

        instance.onAutoPauseDuringCallClick()
        assertThat(instance.showingPhoneStatePermissionDialog).isFalse()
    }

    @Test fun on_auto_pause_during_call_click_with_permission() = runTest {
        if (!hasReadPhoneStatePermission)
            return@runTest
        default_values()

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

    @Test fun on_auto_pause_during_call_click_without_permission() = runTest {
        if (hasReadPhoneStatePermission)
            return@runTest
        default_values()
        instance.onPlayInBackgroundSwitchClick()
        instance.onAutoPauseDuringCallClick()
        assertThat(instance.showingPhoneStatePermissionDialog).isTrue()
        assertThat(updatedPreferences()[autoPauseDuringCallKey]).isNotEqualTo(true)
        assertThat(instance.autoPauseDuringCall).isFalse()
    }

    @Test fun on_ask_for_phone_state_permission_dialog_dismiss() {
        if (hasReadPhoneStatePermission) return
        on_auto_pause_during_call_click_without_permission()
        instance.onPhoneStatePermissionDialogDismiss()
        assertThat(instance.showingPhoneStatePermissionDialog).isFalse()
    }

    @Test fun on_phone_state_permission_dialog_confirm() = runTest {
        if (hasReadPhoneStatePermission)
            return@runTest
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

    @Test fun auto_pause_during_call_always_false_without_permission_and_ignore_audio_focus() = runTest {
        default_values()
        dataStore.edit { it[autoPauseDuringCallKey] = true }
        assertThat(updatedPreferences()[autoPauseDuringCallKey]).isTrue()
        assertThat(instance.autoPauseDuringCall).isFalse()

        instance.onPlayInBackgroundSwitchClick()
        assertThat(instance.autoPauseDuringCall).isEqualTo(hasReadPhoneStatePermission)
    }

    @Test fun on_zero_audio_volume_audio_device_behavior_click() = runTest {
        default_values()
        instance.onOnZeroVolumeAudioDeviceBehaviorClick(OnZeroVolumeAudioDeviceBehavior.DoNothing)
        assertThat(updatedPreferences()[onZeroVolumeAudioDeviceBehaviorKey])
            .isEqualTo(OnZeroVolumeAudioDeviceBehavior.DoNothing.ordinal)
        assertThat(instance.onZeroVolumeAudioDeviceBehavior)
            .isEqualTo(OnZeroVolumeAudioDeviceBehavior.DoNothing)

        instance.onOnZeroVolumeAudioDeviceBehaviorClick(OnZeroVolumeAudioDeviceBehavior.AutoPause)
        assertThat(updatedPreferences()[onZeroVolumeAudioDeviceBehaviorKey])
            .isEqualTo(OnZeroVolumeAudioDeviceBehavior.AutoPause.ordinal)
        assertThat(instance.onZeroVolumeAudioDeviceBehavior)
            .isEqualTo(OnZeroVolumeAudioDeviceBehavior.AutoPause)
    }
}