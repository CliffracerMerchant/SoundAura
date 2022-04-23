/* This file is part of SoundAura, which is released under the terms of the Apache
 * License 2.0. See license.md in the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.Manifest
import android.app.Application
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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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
class SettingsViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val dataStore: DataStore<Preferences>
) : AndroidViewModel(context as Application) {
    private val appThemeKey = intPreferencesKey(
        context.getString(R.string.app_theme_setting_key))

    // The thread must be blocked when reading the first value
    // of the app theme from the DataStore or else the screen
    // can flicker between light and dark themes on startup.
    val appTheme by runBlocking {
        dataStore.awaitEnumPreferenceState<AppTheme>(appThemeKey, viewModelScope)
    }

    fun onAppThemeClick(theme: AppTheme) {
        viewModelScope.launch {
            dataStore.edit { it[appThemeKey] = theme.ordinal }
        }
    }

    private var hasReadPhoneStatePermission by mutableStateOf(
        ContextCompat.checkSelfPermission(
            getApplication(), Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED)

    private val autoPauseDuringCallKey = booleanPreferencesKey(
        context.getString(R.string.auto_pause_during_calls_setting_key))

    private val autoPauseDuringCallPreference by dataStore.preferenceState(
        key = autoPauseDuringCallKey,
        initialValue = false,
        scope = viewModelScope)

    val autoPauseDuringCall by derivedStateOf {
        autoPauseDuringCallPreference && hasReadPhoneStatePermission
    }

    var showingAskForPhoneStatePermissionDialog by mutableStateOf(false)
        private set

    fun onAutoPauseDuringCallClick() {
        if (!autoPauseDuringCall && !hasReadPhoneStatePermission)
            showingAskForPhoneStatePermissionDialog = true
        else viewModelScope.launch {
            dataStore.edit { it[autoPauseDuringCallKey] = !autoPauseDuringCall }
        }
    }

    fun onAskForPhoneStatePermissionDialogDismiss() {
        showingAskForPhoneStatePermissionDialog = false
    }

    fun onAskForPhoneStatePermissionDialogConfirm(permissionGranted: Boolean) {
        if (permissionGranted) viewModelScope.launch {
            dataStore.edit {
                it[autoPauseDuringCallKey] = true
            }
            hasReadPhoneStatePermission = true
        }
        onAskForPhoneStatePermissionDialogDismiss()
    }
}

enum class AppTheme { UseSystem, Light, Dark;

    companion object {
        @Composable fun stringValues() = with(LocalContext.current) {
            remember { arrayOf(getString(R.string.use_system_theme_description),
                               getString(R.string.light_theme_description),
                               getString(R.string.dark_theme_description)) }
        }
    }
}