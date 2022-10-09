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

/**
 * A settings category displayed on a large surface background.
 *
 * @param title The title of the category
 * @param content A composable lambda that contains the category's content.
 */
@Composable fun SettingCategory(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) = Surface(
    modifier = modifier.restrictWidthAccordingToSizeClass(),
    shape = MaterialTheme.shapes.large
) {
    Column(Modifier.padding(20.dp, 10.dp, 20.dp, 6.dp)) {
        Text(text = title,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            style = MaterialTheme.typography.h5)
        Spacer(Modifier.height(8.dp))
        Divider()
        content()
    }
}

@Preview @Composable
fun LightSettingCategoryPreview() = SoundAuraTheme(false) {
    SettingCategory("Setting Category A") {
        Setting("Checkbox setting") { Checkbox(false, { }) }
        Divider()
        Setting("Switch setting") { Switch(true, { }) }
        Divider()
        DialogSetting("Dialog setting", description = "Current value") {}
    }
}

@Preview(showBackground = true) @Composable
fun DarkSettingCategoryPreview() = SoundAuraTheme(true) {
    SettingCategory("Setting Category B") {
        Setting("Checkbox setting") { Checkbox(false, { }) }
        Divider()
        Setting("Switch setting") { Switch(true, { }) }
        Divider()
        DialogSetting("Dialog setting", description = "Current value") {}
    }
}

/**
 * A radio button group to select a particular value of an enum.
 *
 * @param modifier The [Modifier] to apply to the entire radio button group.
 * @param values An [Array] of all possible enum values.
 * @param valueNames An [Array] containing names for each of the enum values.
 * @param valueDescriptions An optional array of [String]s, the individual
 *     values of which will be displayed beneath each enum value to
 *     describe it. A blank [String] will not be displayed at all in case
 *     a description is desired for only certain items.
 * @param currentValue The enum value that should be marked as checked.
 * @param onValueClick The callback that will be invoked when an enum
 *                     value is clicked.
 */
@Composable fun <T> EnumRadioButtonGroup(
    modifier: Modifier = Modifier,
    values: Array<T>,
    valueNames: Array<String>,
    valueDescriptions: Array<String>? = null,
    currentValue: T,
    onValueClick: (T) -> Unit,
) = Column(modifier) {
    values.forEachIndexed { index, value ->
        TextButton(
            onClick = { onValueClick(value) },
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colors.onSurface)
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // The RadioButton's padding is slightly asymmetrical so
                // that it appears more inline with the title next to it.
                RadioButton(
                    checked = value == currentValue,
                    Modifier.size(36.dp).padding(top = 7.dp, bottom = 5.dp))
                Column {
                    val name = valueNames.getOrNull(index) ?: "Error"
                    Text(name, style = MaterialTheme.typography.body1)

                    val description = valueDescriptions?.getOrNull(index)
                    if (!description.isNullOrEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        Text(description, style = MaterialTheme.typography.body2)
                    }
                }
            }
        }
    }
}

/**
 * A setting layout with room for an icon, title, subtitle, and
 * setting content (e.g. a switch to turn the setting on and off.
 *
 * @param icon An optional composable lambda that will be used as the
 *     icon to represent the setting. Will not be displayed if null.
 * @param title The [String] title for the setting.
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
    Column(
        modifier = Modifier.weight(1f).padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(title, style = MaterialTheme.typography.h6)

        if (subtitle != null)
            Text(subtitle, style = MaterialTheme.typography.body2)
    }
    content()
}

/**
 * Compose a [Setting] instance with empty content, whose title will open
 * a dialog when clicked.
 *
 * @param icon An optional composable lambda that will be used as the
 *     icon to represent the setting. Will not be displayed if null.
 * @param title The [String] title for the setting.
 * @param description A longer description of the setting. Will not be displayed if null.
 * @param dialogVisible Whether or not the dialog will be displayed.
 * @param onShowRequest The callback that will be invoked when the user requests
 * @param content The composable lambda containing the dialog that will be shown
 *     when the title is clicked. The provided () -> Unit lambda argument should
 *     be used as the onDismissRequest for the inner dialog.
 */
@Composable fun DialogSetting(
    title: String,
    icon: (@Composable () -> Unit)? = null,
    description: String? = null,
    dialogVisible: Boolean,
    onShowRequest: () -> Unit,
    onDismissRequest: () -> Unit,
    content: @Composable (onDismissRequest: () -> Unit) -> Unit,
) {
    Setting(
        title = title,
        icon = icon,
        subtitle = description,
        onClick = onShowRequest,
    ) {
        Icon(imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = LocalContentColor.current.copy(ContentAlpha.medium))
    }
    if (dialogVisible)
        content(onDismissRequest)
}

/**
 * Compose a [DialogSetting] that contains its dialogVisible visible state
 * internally. If state hoisting the dialogVisible state is necessary, use
 * the overload of [DialogSetting] that accepts the dialogVisible parameter.
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
    DialogSetting(
        title = title,
        icon = icon,
        description = description,
        dialogVisible = showingDialog,
        onShowRequest = { showingDialog = true },
        onDismissRequest = { showingDialog = false },
        content = content)
}

/**
 * Compose a [DialogSetting] that displays the name of a setting that has
 * several discrete values described by the enum type T. The current value
 * of the setting will be displayed beneath the title. When clicked, a
 * dialog displaying all of the values of the enum will be displayed to
 * allow the user to change the value.
 *
 * @param title The title of the setting. This will be displayed in the
 *     setting layout, and will also be used as the title of the dialog
 *     window.
 * @param modifier The [Modifier] to use for the layout.
 * @param useDefaultWidth Whether or not to use the platform default
 *     size for the width of the popup dialog window.
 * @param description An optional description of the setting. This will
 *     only be displayed in the dialog window, below the title but before
 *     the enum value's radio buttons.
 * @param values An array containing all possible values for the enum
 *     setting, usually obtained with an [enumValues]<T>() call.
 * @param valueNames An [Array] the same length as values that
 *     contains string titles for each of the enum values.
 * @param valueDescriptions An optional [Array] containing further
 *     descriptions for each enum value. These descriptions will
 *     not be displayed if valueDescriptions is null.
 * @param currentValue The current enum value of the setting.
 * @param onValueClick The callback that will be invoked when an
 *     option is clicked.
 */
@Composable fun <T: Enum<T>> EnumDialogSetting(
    title: String,
    modifier: Modifier = Modifier,
    useDefaultWidth: Boolean = true,
    description: String? = null,
    values: Array<T>,
    valueNames: Array<String>,
    valueDescriptions: Array<String>? = null,
    currentValue: T,
    onValueClick: (T) -> Unit
) = DialogSetting(
    title = title,
    icon = null,
    description = valueNames[currentValue.ordinal]
) { onDismissRequest ->
    SoundAuraDialog(
        modifier = modifier,
        useDefaultWidth = useDefaultWidth,
        title = title,
        onDismissRequest = onDismissRequest,
        buttons = {}
    ) {
        if (description != null) {
            Text(text = description, style = MaterialTheme.typography.body1,
                 modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(6.dp))
        }
        EnumRadioButtonGroup(
            modifier = Modifier.padding(bottom = 12.dp),
            values = values,
            valueNames = valueNames,
            valueDescriptions = valueDescriptions,
            currentValue = currentValue,
            onValueClick = {
                onValueClick(it)
                onDismissRequest()
            })
    }
}