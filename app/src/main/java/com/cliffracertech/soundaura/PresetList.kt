/* This file is part of SoundAura, which is released under the terms of the Apache
 * License 2.0. See license.md in the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cliffracertech.soundaura.ui.theme.SoundAuraTheme

/**
 * Show a dialog asking the user to confirm that they would like to overwrite
 * the [Preset] whose name is equal to [currentPresetName] with any current
 * changes. The cancel and ok buttons will invoke [onDismissRequest] and
 * [onConfirm], respectively.
 */
@Composable fun ConfirmPresetOverwriteDialog(
    currentPresetName: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) = SoundAuraDialog(
    title = stringResource(R.string.confirm_overwrite_preset_dialog_title),
    text = stringResource(R.string.confirm_overwrite_preset_dialog_message, currentPresetName),
    onDismissRequest = onDismissRequest,
    onConfirm = {
        onConfirm()
        onDismissRequest()
    })

/**
 * Show a dialog asking the user to confirm that they want
 * to delete the [Preset] whose name equals [presetName].
 */
@Composable fun ConfirmDeletePresetDialog(
    presetName: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit
) = SoundAuraDialog(
    onDismissRequest = onDismissRequest,
    title = stringResource(R.string.confirm_delete_preset_title, presetName),
    text = stringResource(R.string.confirm_delete_preset_message),
    confirmText = stringResource(R.string.delete),
    onConfirm = {
        onConfirm()
        onDismissRequest()
    })

/**
 * A view to display the name and modified status of a [Preset] instance. An
 * options button is also provided that provides options to rename, overwrite,
 * or delete the preset.
 *
 * @param modifier The [Modifier] to use for the view
 * @param preset The [Preset] instance whose name is being displayed
 * @param isModified Whether or not the [Preset] has unsaved changes. This will
 *     be indicated by an asterisk next to the [Preset]'s name.
 * @param onRenameRequest The callback that will be invoked when the user has
 *     requested that the [Preset]'s name be changed to the String parameter
 * @param onOverwriteRequest The callback that will be invoked when the user
 *     has requested that the [Preset] be overwritten with the current track/
 *     volume combination
 * @param onDeleteRequest The callback that will be invoked when the user has
 *     requested that the [Preset] be deleted
 * @param onClick The callback that will be invoked when the user clicks the view
 */
@Composable fun PresetView(
    modifier: Modifier = Modifier,
    preset: Preset,
    isModified: Boolean,
    onRenameRequest: (String) -> Unit,
    onOverwriteRequest: () -> Unit,
    onDeleteRequest: () -> Unit,
    onClick: () -> Unit
) = Row(
    modifier = modifier
        .minTouchTargetSize()
        .clickable (
            onClickLabel = stringResource(R.string.preset_click_label, preset.name),
            role = Role.Button,
            onClick = onClick
        ).padding(vertical = 2.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    Row(Modifier.weight(1f).padding(start = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MarqueeText(preset.name, Modifier.weight(1f, false))
        if (isModified)
            Text(" *", style = LocalTextStyle.current.copy(fontSize = 18.sp))
    }
    var showingOptionsMenu by rememberSaveable { mutableStateOf(false) }
    var showingRenameDialog by rememberSaveable { mutableStateOf(false) }
    var showingOverwriteDialog by rememberSaveable { mutableStateOf(false) }
    var showingDeleteDialog by rememberSaveable { mutableStateOf(false) }

    IconButton({ showingOptionsMenu = true }) {
        Icon(imageVector = Icons.Default.MoreVert,
             contentDescription = stringResource(
                 R.string.item_options_button_description, preset.name))

        DropdownMenu(
            expanded = showingOptionsMenu,
            onDismissRequest = { showingOptionsMenu = false }
        ) {
            DropdownMenuItem(onClick = {
                showingRenameDialog = true
                showingOptionsMenu = false
            }) { Text(stringResource(R.string.rename)) }

            DropdownMenuItem(onClick = {
                showingOverwriteDialog = true
                showingOptionsMenu = false
            }) { Text(stringResource(R.string.overwrite)) }

            DropdownMenuItem(onClick = {
                showingDeleteDialog = true
                showingOptionsMenu = false
            }) { Text(stringResource(R.string.delete)) }
        }
    }

    if (showingRenameDialog)
        RenameDialog(preset.name,
            onDismissRequest = { showingRenameDialog = false },
            onConfirm = onRenameRequest)

    if (showingOverwriteDialog)
        ConfirmPresetOverwriteDialog(
            currentPresetName = preset.name,
            onDismissRequest = { showingOverwriteDialog = false },
            onConfirm = onOverwriteRequest)

    if (showingDeleteDialog)
        ConfirmDeletePresetDialog(preset.name,
            onDismissRequest = { showingDeleteDialog = false },
            onConfirm = onDeleteRequest)
}

/**
 * Display a list of [Preset]s, with an options menu for each [Preset]
 * to allow for renaming, overwriting, and deletion. Optionally, a single
 * [Preset] identified as the active one will have the [selectionBrush]
 * applied to it to indicate this status.
 *
 * @param modifier The [Modifier] to use for the [Preset] list
 * @param activePreset The [Preset], if any, currently marked as the active one
 * @param activePresetIsModified Whether or not the [Preset] marked as the
 *     active one has been modified. An asterisk will be placed next to its
 *     name in this case to indicate this to the user.
 * @param selectionBrush The [Brush] that will be applied to the active
 *     [Preset] to indicate its status as the active [Preset]
 * @param presetListProvider A lambda that returns the list of presets when invoked
 * @param onPresetClick The callback that will be invoked when the user clicks on a [Preset]
 * @param onPresetRenameRequest The callback that will be invoked when the user
 *     has requested that the [Preset] parameter be renamed to the [String] parameter
 * @param onPresetOverwriteRequest The callback that will be invoked when the
 *     user has requested the current track / volume combination to overwrite
 *     the one currently associated with the [Preset] parameter
 * @param onPresetDeleteRequest The callback that will be invoked when the user
 *     has requested that the [Preset] parameter be deleted
 */
@Composable fun PresetList(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    activePreset: Preset? = null,
    activePresetIsModified: Boolean,
    selectionBrush: Brush,
    presetListProvider: () -> List<Preset>,
    onPresetRenameRequest: (Preset, String) -> Unit,
    onPresetOverwriteRequest: (Preset) -> Unit,
    onPresetDeleteRequest: (Preset) -> Unit,
    onPresetClick: (Preset) -> Unit,
) {
    LazyColumn(modifier, contentPadding = contentPadding) {
        val list = presetListProvider()
        items(list, key = { it.name }) { preset ->
            val isSelected = preset == activePreset
            val itemModifier = remember(isSelected) {
                if (!isSelected) Modifier
                else Modifier.background(selectionBrush, alpha = 0.5f)
            }
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colors.onSurface) {
                PresetView(
                    modifier = itemModifier,
                    preset = preset,
                    isModified = preset == activePreset && activePresetIsModified,
                    onRenameRequest = { onPresetRenameRequest(preset, it) },
                    onOverwriteRequest = { onPresetOverwriteRequest(preset) },
                    onDeleteRequest = { onPresetDeleteRequest(preset) },
                    onClick = { onPresetClick(preset) })
                Divider()
            }
        }
    }
}

@Preview @Composable
fun PresetListPreview() = SoundAuraTheme {
    val list = List(4) { Preset("Super Duper Extra Really Long Preset Name$it") }
    var selectedPreset by remember { mutableStateOf(list.first()) }
    PresetList(
        activePreset = selectedPreset,
        activePresetIsModified = true,
        selectionBrush = Brush.horizontalGradient(
            listOf(MaterialTheme.colors.primaryVariant,
                   MaterialTheme.colors.secondaryVariant)),
        presetListProvider = { list },
        onPresetClick = { selectedPreset = it },
        onPresetRenameRequest = { _, _ -> },
        onPresetDeleteRequest = {},
        onPresetOverwriteRequest = {})
}