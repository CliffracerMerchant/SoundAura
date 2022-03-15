/* This file is part of SoundAura, which is released under the terms of the Apache
 * License 2.0. See license.md in the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/** A settings category displayed on a large surface background.
 * @param title The title of the category
 * @param content A list of composables containing each setting item. */
@Composable fun SettingCategory(
    title: String,
    content: List<@Composable () -> Unit>
) = Surface(shape = MaterialTheme.shapes.large) {
    Column(Modifier.padding(20.dp, 16.dp, 20.dp, 0.dp)) {
        Text(title, style = MaterialTheme.typography.h6)
        Spacer(Modifier.height(8.dp))
        Divider()
        Column {
            content.forEachIndexed { index, item ->
                item()
                if (index != content.lastIndex)
                    Divider()
            }
        }
    }
}

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
    title: String,
    icon: (@Composable () -> Unit)? = null,
    onTitleClick: (() -> Unit)? = null,
    description: String? = null,
    content: @Composable () -> Unit,
) = Row(verticalAlignment = Alignment.CenterVertically) {

    if (icon != null)
        Box(Modifier.size(48.dp)) { icon() }

    val titleModifier = Modifier.weight(1f).heightIn(min = 48.dp).then(
        if (onTitleClick == null) Modifier
        else Modifier.clickable(onClick = onTitleClick))

    Column(titleModifier, verticalArrangement = Arrangement.Center) {
        Text(title, style = MaterialTheme.typography.body1)

        if (description != null)
            Text(description, style = MaterialTheme.typography.body2)
    }
    content()
}

@Composable fun AppSettings() = Surface(
    color = MaterialTheme.colors.background,
    modifier = Modifier.fillMaxSize(1f)
) {
    LazyColumn(
        modifier = Modifier.padding(horizontal = 8.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { DisplaySettingsCategory() }
        item { AboutSettingsCategory() }
    }
}

@Composable private fun DisplaySettingsCategory() {
    val titleString = stringResource(R.string.display_category_description)
    val appThemeSetting = @Composable {
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
    SettingCategory(titleString, listOf(appThemeSetting))
}

@Composable private fun AboutSettingsCategory() {
    val titleString = stringResource(R.string.about_category_description)
    val privacyPolicySetting = @Composable {
        var showingPrivacyPolicy by rememberSaveable { mutableStateOf(false) }
        Setting(title = stringResource(R.string.privacy_policy_description),
                onTitleClick = { showingPrivacyPolicy = true }) {}
        if (showingPrivacyPolicy)
            PrivacyPolicyDialog { showingPrivacyPolicy = false }
    }
    val openSourceLicensesSetting = @Composable {
        var showingLicenses by rememberSaveable { mutableStateOf(false) }
        Setting(title = stringResource(R.string.open_source_licenses_description),
                onTitleClick = { showingLicenses = true }) {}
        if (showingLicenses)
            OpenSourceLibrariesUsedDialog { showingLicenses = false }
    }
    val aboutAppSetting = @Composable {
        var showingAboutApp by rememberSaveable { mutableStateOf(false) }
        Setting(title = stringResource(R.string.about_app_description),
                onTitleClick = { showingAboutApp = true }) {}
        if (showingAboutApp)
            AboutAppDialog { showingAboutApp = false }
    }
    SettingCategory(titleString, listOf(
        privacyPolicySetting,
        openSourceLicensesSetting,
        aboutAppSetting))
}
