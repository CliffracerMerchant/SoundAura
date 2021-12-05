/* This file is part of SoundAura, which is released under the terms of the Apache
 * License 2.0. See license.md in the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

fun Context.isSystemInDarkTheme() = Configuration.UI_MODE_NIGHT_YES ==
    (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)

@Composable fun <T> EnumRadioButtonGroup(
    values: Array<T>,
    valueNames: Array<String>,
    currentValue: T,
    onValueSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) = Box(modifier) {
    values.forEachIndexed { index, value ->
        Row(Modifier.clickable { onValueSelected(value) }) {
            val name = valueNames.getOrNull(index) ?: "Error"
            Text(text = name, style = MaterialTheme.typography.button)
            Spacer(Modifier.weight(1f))
            val vector = if (value == currentValue) Icons.Default.RadioButtonChecked
                         else                       Icons.Default.RadioButtonUnchecked
            Icon(vector, name, Modifier.size(36.dp).padding(8.dp))
        }
    }
}

@Composable
fun AppSettings() = Surface(
    color = MaterialTheme.colors.background,
    modifier = Modifier.fillMaxSize(1f)
) {
    val viewModel: SettingsViewModel = viewModel()
    val preferences by viewModel.prefs.collectAsState()
    val appTheme by derivedStateOf {
        AppTheme.values()[preferences?.get(appThemeKey) ?: 0]
    }

    Text(text = stringResource(R.string.app_theme_description))
    EnumRadioButtonGroup(
        values = AppTheme.values(),
        valueNames = AppTheme.stringValues(),
        currentValue = appTheme,
        onValueSelected = { theme ->
            viewModel.writePreference {
                it[appThemeKey] = theme.ordinal
            }
        }, modifier = Modifier.padding(24.dp, 0.dp, 0.dp, 0.dp))
}

@Preview @Composable fun AppSettingsPreview() = AppSettings()
