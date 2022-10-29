/* This file is part of SoundAura, which is released under the terms of the Apache
 * License 2.0. See license.md in the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cliffracertech.soundaura.ui.theme.SoundAuraTheme

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

@Composable fun PresetView(
    modifier: Modifier = Modifier,
    preset: Preset,
    onRenameRequest: (String) -> Unit,
    onDeleteRequest: () -> Unit,
    onClick: () -> Unit
) = Row(
    modifier = modifier.minTouchTargetSize().clickable (
            onClickLabel = stringResource(R.string.preset_click_label, preset.name),
            role = Role.Button,
            onClick = onClick
        ).padding(2.dp),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    Text(preset.name, Modifier.weight(1f).padding(start = 18.dp), maxLines = 1)
    var showingOptionsMenu by rememberSaveable { mutableStateOf(false) }
    var showingRenameDialog by rememberSaveable { mutableStateOf(false) }
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
                showingDeleteDialog = true
                showingOptionsMenu = false
            }) { Text(stringResource(R.string.remove)) }
        }
    }

    if (showingRenameDialog)
        RenameDialog(preset.name,
            onDismissRequest = { showingRenameDialog = false },
            onConfirm = onRenameRequest)

    if (showingDeleteDialog)
        ConfirmDeletePresetDialog(preset.name,
            onDismissRequest = { showingDeleteDialog = false },
            onConfirm = onDeleteRequest)
}

@Composable fun PresetList(
    modifier: Modifier = Modifier,
    shape: CornerBasedShape = MaterialTheme.shapes.large,
    currentPreset: Preset? = null,
    selectionBrush: Brush,
    presetListProvider: () -> List<Preset>,
    onPresetClick: (Preset) -> Unit,
    onRenameRequest: (Preset, String) -> Unit,
    onDeleteRequest: (Preset) -> Unit
) {
    LazyColumn(modifier.background(MaterialTheme.colors.surface, shape)) {
        val list = presetListProvider()
        itemsIndexed(
            items = list,
            key = { _, preset -> preset.name },
            contentType = { _, _ -> }
        ) { index, preset ->
            val isSelected = preset == currentPreset
            val itemModifier = remember(list.size, index, isSelected) {
                if (!isSelected) Modifier
                else {
                    val itemShape = when {
                        list.size == 1 ->          shape
                        index == 0 ->              shape.topShape()
                        index == list.lastIndex -> shape.bottomShape()
                        else ->                    RectangleShape
                    }
                    Modifier.background(selectionBrush, itemShape, 0.5f)
                }
            }
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colors.onSurface) {
                PresetView(preset = preset,
                    modifier = itemModifier,
                    onRenameRequest = { onRenameRequest(preset, it) },
                    onDeleteRequest = { onDeleteRequest(preset) },
                    onClick = { onPresetClick(preset) })
                if (index != list.lastIndex)
                    Divider()
            }
        }
    }
}

@Preview @Composable
fun PresetListPreview() = SoundAuraTheme {
    val list = List(4) { Preset("Preset $it") }
    var selectedPreset by remember { mutableStateOf(list.first()) }
    PresetList(
        currentPreset = selectedPreset,
        selectionBrush = Brush.horizontalGradient(
            listOf(MaterialTheme.colors.primaryVariant,
                   MaterialTheme.colors.secondaryVariant)),
        onRenameRequest = { _, _ -> },
        onDeleteRequest = {},
        presetListProvider = { list },
        onPresetClick = { selectedPreset = it })
}