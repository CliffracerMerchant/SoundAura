/* This file is part of SoundAura, which is released under the terms of the Apache
 * License 2.0. See license.md in the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore by preferencesDataStore(name = "settings")

@Module @InstallIn(SingletonComponent::class)
class PreferencesModule {
    @Singleton @Provides
    fun provideDatastore(@ApplicationContext app: Context) =
        app.dataStore
}

@HiltViewModel
class SettingsViewModel(
    context: Context,
    private val dataStore: DataStore<Preferences>,
    coroutineScope: CoroutineScope? = null
) : ViewModel() {

    @Inject constructor(
        @ApplicationContext context: Context,
        dataStore: DataStore<Preferences>,
    ) : this(context, dataStore, null)

    private val scope = coroutineScope ?: viewModelScope
    private val appThemeKey = intPreferencesKey(
        context.getString(R.string.pref_app_theme_key))
    private val playInBackgroundKey = booleanPreferencesKey(
        context.getString(R.string.pref_play_in_background_key))
    private val autoPauseDuringCallKey = booleanPreferencesKey(
        context.getString(R.string.pref_auto_pause_during_calls_key))
    private val onZeroMediaVolumeAudioDeviceBehaviorKey = intPreferencesKey(
        context.getString(R.string.on_zero_volume_behavior_key))

    // The thread must be blocked when reading the first value
    // of the app theme from the DataStore or else the screen
    // can flicker between light and dark themes on startup.
    val appTheme by runBlocking {
        dataStore.awaitEnumPreferenceState<AppTheme>(appThemeKey, scope)
    }

    fun onAppThemeClick(theme: AppTheme) {
        scope.launch {
            dataStore.edit { it[appThemeKey] = theme.ordinal }
        }
    }

    val playInBackground by dataStore.preferenceState(
        key = playInBackgroundKey,
        initialValue = false,
        scope = scope)

    var showingPlayInBackgroundExplanation by mutableStateOf(false)
        private set

    fun onPlayInBackgroundExplanationDismiss() {
        showingPlayInBackgroundExplanation = false
    }

    fun onPlayInBackgroundTitleClick() {
        showingPlayInBackgroundExplanation = true
    }

    fun onPlayInBackgroundSwitchClick() {
        scope.launch { dataStore.edit {
            val newValue = !playInBackground
            if (!newValue) it[autoPauseDuringCallKey] = false
            it[playInBackgroundKey] = newValue
        }}
    }

    val autoPauseDuringCallSettingVisible by derivedStateOf { playInBackground }

    // This value should always be up to date due to granting or revoking
    // permissions outside of the app causing an app restart. If the user
    // approves the read phone state permission inside the app, the value
    // must be changed to true manually.
    private var hasReadPhoneStatePermission by mutableStateOf(
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED)

    private val autoPauseDuringCallPreference by
        dataStore.preferenceState(
            key = autoPauseDuringCallKey,
            initialValue = false,
            scope = scope)

    val autoPauseDuringCall by derivedStateOf {
        autoPauseDuringCallPreference &&
        hasReadPhoneStatePermission &&
        autoPauseDuringCallSettingVisible
    }

    var showingPhoneStatePermissionDialog by mutableStateOf(false)
        private set

    fun onAutoPauseDuringCallClick() {
        if (!playInBackground)
            return
        if (!autoPauseDuringCall && !hasReadPhoneStatePermission)
            showingPhoneStatePermissionDialog = true
        else scope.launch {
            dataStore.edit { it[autoPauseDuringCallKey] = !autoPauseDuringCallPreference }
        }
    }

    fun onPhoneStatePermissionDialogDismiss() {
        showingPhoneStatePermissionDialog = false
    }

    fun onPhoneStatePermissionDialogConfirm(permissionGranted: Boolean) {
        if (permissionGranted) scope.launch {
            dataStore.edit {
                it[autoPauseDuringCallKey] = true
            }
            hasReadPhoneStatePermission = true
        }
        onPhoneStatePermissionDialogDismiss()
    }

    val onZeroMediaVolumeAudioDeviceBehavior by
        dataStore.enumPreferenceState<OnZeroMediaVolumeAudioDeviceBehavior>(
            onZeroMediaVolumeAudioDeviceBehaviorKey, scope)

    fun onOnZeroMediaVolumeAudioDeviceBehaviorClick(
        behavior: OnZeroMediaVolumeAudioDeviceBehavior
    ) {
        scope.launch {
            dataStore.edit {
                it[onZeroMediaVolumeAudioDeviceBehaviorKey] = behavior.ordinal
            }
        }
    }
}

enum class AppTheme { UseSystem, Light, Dark;
    companion object {
        /** Return an Array<String> containing strings that describe the enum values. */
        @Composable fun valueStrings() =
            with(LocalContext.current) {
                remember { arrayOf(
                    getString(R.string.use_system_theme),
                    getString(R.string.light_theme),
                    getString(R.string.dark_theme)
                )}
            }
    }
}

/** An enum describing the behavior of the application when the current
 * audio device is changed to one with a media audio stream volume of
 * zero. The described behaviors will only be used when the zero media
 * volume is the result of a audio device change. If the zero media
 * volume is a result of the user manually changing it to zero on the
 * current audio device, playback will not be affected. */
enum class OnZeroMediaVolumeAudioDeviceBehavior {
    /** PlayerService will be automatically stopped to conserve battery. */
    AutoStop,
    /** Playback will be automatically paused, and then resumed when another
     * audio device change brings the media volume back up above zero. */
    AutoPause,
    /** Playback will not be affected.*/
    DoNothing;

    companion object {
        /** Return an Array<String> containing strings that describe the enum values. */
        @Composable fun valueStrings() =
            with(LocalContext.current) {
                remember { arrayOf(
                    getString(R.string.stop_playback_on_zero_volume_title),
                    getString(R.string.pause_playback_on_zero_volume_title),
                    getString(R.string.do_nothing_on_zero_volume_title)
                )}
            }

        /** Return an Array<String?> containing strings that further describe the enum values. */
        @Composable fun valueDescriptions() =
            with(LocalContext.current) {
                remember { arrayOf(
                    getString(R.string.stop_playback_on_zero_volume_description),
                    getString(R.string.pause_playback_on_zero_volume_description),
                    getString(R.string.do_nothing_on_zero_volume_description)
                )}
            }
    }
}