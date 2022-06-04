/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cliffracertech.soundaura.ui.theme.SoundAuraTheme

/** A settings category displayed on a large surface background.
 * @param title The title of the category
 * @param content A composable lambda that contains the category's content. */
@Composable fun SettingCategory(
    title: String,
    content: @Composable () -> Unit
) = Surface(shape = MaterialTheme.shapes.large) {
    Column(Modifier.padding(20.dp, 16.dp, 20.dp, 6.dp)) {
        Text(text = title,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            style = MaterialTheme.typography.h6)
        Spacer(Modifier.height(8.dp))
        Divider()
        content()
    }
}

@Preview @Composable
fun LightSettingCategoryPreview() = SoundAuraTheme(false) {
    SettingCategory("Setting Category A") {
        Setting("Setting 1") { Checkbox(false, { }) }
        Divider()
        Setting("Setting 2") { Switch(true, { }) }
        Divider()
        DialogSetting("Setting 3") { }
    }
}

@Preview(showBackground = true) @Composable
fun DarkSettingCategoryPreview() = SoundAuraTheme(true) {
    SettingCategory("Setting Category B6") {
        Setting("Setting 1") { Checkbox(false, { }) }
        Divider()
        Setting("Setting 2") { Switch(true, { }) }
        Divider()
        DialogSetting("Setting 3") { }
    }
}

/**
 * A radio button group to select a particular value of an enum.
 *
 * @param modifier The modifier to apply to the entire radio button group.
 * @param values An array of all possible enum values.
 * @param valueNames An array containing names for each of the enum values.
 * @param currentValue The enum value that should be marked as checked.
 * @param onValueClick The callback that will be invoked when an enum
 *                     value is clicked.
 */
@Composable fun <T> EnumRadioButtonGroup(
    modifier: Modifier = Modifier,
    values: Array<T>,
    valueNames: Array<String>,
    currentValue: T,
    onValueClick: (T) -> Unit,
) = Column(modifier) {
    values.forEachIndexed { index, value ->
        Row(Modifier.height(48.dp).clickable { onValueClick(value) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RadioButton(checked = value == currentValue,
                        Modifier.size(36.dp).padding(8.dp))
            val name = valueNames.getOrNull(index) ?: "Error"
            Text(text = name, style = MaterialTheme.typography.body1)
        }
    }
}

/**
 * A setting layout, with room for an icon, title, subtitle, and
 * setting content (e.g. a switch to turn the setting on and off.
 *
 * @param icon The icon to represent the setting. Will not be displayed if null.
 * @param title The string title for the setting.
 * @param subtitle Additional text to be displayed below the title with
 *                 a lower emphasis. Will not be displayed if null.
 * @param onClick The callback, if any, that will be invoked when
 *                the setting is clicked. Defaults to null.
 * @param content A composable containing the content used to change the setting.
 */
@Composable fun Setting(
    title: String,
    icon: (@Composable () -> Unit)? = null,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) = Row(
    modifier = Modifier.minTouchTargetSize().then(
        if (onClick == null) Modifier
        else Modifier.clickable(onClick = onClick)),
    verticalAlignment = Alignment.CenterVertically
) {
    if (icon != null)
        Box(Modifier.size(48.dp)) { icon() }

    Column(Modifier.weight(1f), Arrangement.Center) {
        Text(text = title,
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.body1)
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(text = subtitle, style = MaterialTheme.typography.body2)
        }
        Spacer(Modifier.height(8.dp))
    }
    content()
}

/**
 * Compose a Setting instance with empty content, whose title will open
 * a dialog when clicked.
 *
 * @param icon The icon to represent the setting. Will not be displayed if null.
 * @param title The string title for the setting.
 * @param description A longer description of the setting. Will not be displayed if null.
 * @param content The composable lambda containing the dialog that will be shown
 *     when the title is clicked. The provided () -> Unit lambda argument should
 *     be used as the onDismissRequest for the inner dialog.
 */
@Composable fun DialogSetting(
    title: String,
    icon: (@Composable () -> Unit)? = null,
    description: String? = null,
    content: @Composable (onDismissRequest: () -> Unit) -> Unit,
) {
    var showingDialog by rememberSaveable { mutableStateOf(false) }
    Setting(
        title = title,
        icon = icon,
        subtitle = description,
        onClick = { showingDialog = true },
    ) {
        Icon(imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = LocalContentColor.current.copy(ContentAlpha.medium))
    }
    if (showingDialog)
        content { showingDialog = false }
}