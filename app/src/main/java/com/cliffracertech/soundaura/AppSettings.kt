/* This file is part of SoundAura, which is released under the terms of the Apache
 * License 2.0. See license.md in the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
/**
 * A radio button group to select a particular value of an enum.
 *
 * @param modifier The modifier to apply to the entire radio button group.
 * @param values An array of all possible enum values.
 * @param valueNames An array containing names for each of the enum values.
 * @param currentValue The enum value that should be marked as checked.
 * @param onValueSelected The callback that will be invoked when an enum
 *                        value is selected.
 */
@Composable fun <T> EnumRadioButtonGroup(
    modifier: Modifier = Modifier,
    values: Array<T>,
    valueNames: Array<String>,
    currentValue: T,
    onValueSelected: (T) -> Unit,
) = Column(modifier) {
    values.forEachIndexed { index, value ->
        Row(Modifier.height(48.dp).clickable { onValueSelected(value) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RadioButton(value == currentValue,
                        Modifier.size(36.dp).padding(8.dp))
            val name = valueNames.getOrNull(index) ?: "Error"
            Text(text = name, style = MaterialTheme.typography.body1)
        }
    }
}

/**
 * A setting layout, with room for an icon, title, and setting
 * content (e.g. a switch to turn the setting on and off.
 *
 * @param icon The icon to represent the setting. Will not be displayed if null.
 * @param title The string title for the setting.
 * @param description A longer description of the setting. Will not be displayed if null.
 * @param content A composable containing the content used to change the setting.
 */
@Composable fun Setting(
    icon:  (@Composable () -> Unit)? = null,
    title: String,
    description: String? = null,
    content: @Composable () -> Unit,
) = Row(verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.largeSurfaceBackground()
                           .padding(0.dp, 8.dp, 24.dp, 8.dp),
) {
    if (icon != null) Box(Modifier.size(48.dp)) { icon() }
    else              Spacer(Modifier.width(24.dp))

    Column(Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.h6)
        if (description != null)
            Text(description, style = MaterialTheme.typography.body2)
    }
    content()
}

@Composable
fun AppSettings() = Surface(
    color = MaterialTheme.colors.background,
    modifier = Modifier.fillMaxSize(1f)
) {
    Column(Modifier.padding(12.dp, 8.dp)) {
        val viewModel: SettingsViewModel = viewModel()

        Setting(title = stringResource(R.string.app_theme_description)) {
            EnumRadioButtonGroup(
                modifier = Modifier.padding(end = 16.dp),
                values = AppTheme.values(),
                valueNames = AppTheme.stringValues(),
                currentValue = viewModel.appTheme,
                onValueSelected = viewModel::onAppThemeSelected)
        }
    }
}