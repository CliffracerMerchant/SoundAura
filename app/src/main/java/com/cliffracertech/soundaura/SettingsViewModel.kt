/* This file is part of SoundAura, which is released under the terms of the Apache
 * License 2.0. See license.md in the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

val Context.dataStore by preferencesDataStore(name = "settings")
val appThemeKey = intPreferencesKey("app_theme")

class SettingsViewModel(private val app: Application) : AndroidViewModel(app) {
    var appTheme by mutableStateOf(
        runBlocking {
            val firstIndex = app.dataStore.data.first()[appThemeKey]
            AppTheme.values()[firstIndex ?: 0]
        })
        private set

    init {
        app.dataStore.data.map { it[appThemeKey] }
            .onEach { appTheme = AppTheme.values()[it ?: 0] }
            .launchIn(viewModelScope)
    }

    fun writePreferences(actions: suspend (MutablePreferences) -> Unit) {
        viewModelScope.launch {
            app.dataStore.edit { actions(it) }
        }
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