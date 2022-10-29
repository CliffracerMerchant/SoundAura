/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cliffracertech.soundaura.ui.theme.SoundAuraTheme

@Composable fun PresetSelector(
    modifier: Modifier = Modifier,
    onCloseButtonClick: () -> Unit,
    backgroundBrush: Brush,
    currentPreset: Preset? = null,
    currentIsModified: Boolean,
    presetListProvider: () -> List<Preset>,
    onPresetClick: (Preset) -> Unit,
    onPresetRenameRequest: (Preset, String) -> Unit,
    onPresetDeleteRequest: (Preset) -> Unit,
) = Column(modifier.background(backgroundBrush, MaterialTheme.shapes.large)) {
    Row(modifier = Modifier.padding(top = 4.dp, bottom = 2.dp,
                                    start = 24.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.preset_selector_title),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.h6)
        IconButton(onClick = onCloseButtonClick) {
            Icon(Icons.Default.Close,
                stringResource(R.string.close_preset_selector_description))
        }
    }
    PresetList(
        modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
        shape = MaterialTheme.shapes.medium,
        currentPreset = currentPreset,
        selectionBrush = backgroundBrush,
        presetListProvider = presetListProvider,
        onPresetClick = onPresetClick,
        onRenameRequest = onPresetRenameRequest,
        onDeleteRequest = onPresetDeleteRequest)
}

@Preview @Composable fun PresetSelectorPreview() = SoundAuraTheme {
    val list = List(4) { Preset("Preset $it") }
    var currentPreset by remember { mutableStateOf(list.first()) }
    Box(Modifier.size(400.dp, 400.dp)) {
        PresetSelector(
            modifier = Modifier.align(Alignment.BottomStart),
            onCloseButtonClick = {},
            backgroundBrush = Brush.horizontalGradient(
                listOf(MaterialTheme.colors.primaryVariant,
                       MaterialTheme.colors.secondaryVariant)),
            currentPreset = currentPreset,
            currentIsModified = false,
            presetListProvider = { list },
            onPresetClick = { currentPreset = it },
            onPresetRenameRequest = { _, _ -> },
            onPresetDeleteRequest = {})
    }
}