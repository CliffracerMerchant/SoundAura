/* This file is part of SoundAura, which is released under the terms of the Apache
 * License 2.0. See license.md in the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cliffracertech.soundaura.ui.theme.SoundAuraTheme

@Composable fun animateAlignmentAsState(
    targetAlignment: Alignment,
): State<Alignment> {
    val biased = targetAlignment as BiasAlignment
    val horizontal by animateFloatAsState(biased.horizontalBias)
    val vertical by animateFloatAsState(biased.verticalBias)
    return derivedStateOf { BiasAlignment(horizontal, vertical) }
}

@Composable private fun OpenPresetSelectorButton(
    onClick: () -> Unit,
    selectedPreset: Preset?,
    presetIsModified: Boolean
) = Column(
    modifier = Modifier
        .padding(horizontal = 4.dp)
        .clickable(onClick = onClick, role = Role.Button,
                   onClickLabel = stringResource(R.string.preset_button_click_label)),
    verticalArrangement = Arrangement.Center
) {
    val style = MaterialTheme.typography.caption
    Text(maxLines = 1, style = style, text = stringResource(when {
        selectedPreset == null -> R.string.unsaved_preset_description
        presetIsModified ->       R.string.playing_modified_preset_description
        else ->                   R.string.playing_preset_description
    }))
    if (selectedPreset != null)
        Text(selectedPreset.name, maxLines = 1, style = style)
}

@Composable private fun PresetSelector(
    onDismissRequest: () -> Unit,
    backgroundBrush: Brush,
    selectedPreset: Preset? = null,
    presetListProvider: () -> List<Preset>,
    onPresetClick: (Preset) -> Unit,
    onPresetRenameRequest: (Preset, String) -> Unit,
    onPresetDeleteRequest: (Preset) -> Unit,
) = Column {
    Row(modifier = Modifier.padding(top = 0.dp, bottom = 4.dp,
                                    start = 16.dp, end = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.preset_selector_title),
            Modifier.weight(1f),
            style = MaterialTheme.typography.h6)
        IconButton(onClick = onDismissRequest) {
            Icon(Icons.Default.Close,
                stringResource(R.string.close_preset_selector_description))
        }
    }
    PresetList(
        selectedPreset = selectedPreset,
        selectionBrush = backgroundBrush,
        presetListProvider = presetListProvider,
        onPresetClick = onPresetClick,
        onRenameRequest = onPresetRenameRequest,
        onDeleteRequest = onPresetDeleteRequest)
}

@Composable fun FloatingPresetButton(
    modifier: Modifier = Modifier,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    backgroundBrush: Brush,
    selectedPreset: Preset? = null,
    presetIsModified: Boolean = false,
    presetListProvider: () -> List<Preset>,
    onPresetClick: (Preset) -> Unit,
    onPresetRenameRequest: (Preset, String) -> Unit,
    onPresetDeleteRequest: (Preset) -> Unit,
    onClick: () -> Unit,
) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colors.onPrimary) {
        AnimatedContent(
            targetState = expanded,
            modifier = modifier
                .background(backgroundBrush, MaterialTheme.shapes.large)
                .padding(8.dp)
        ) { expanded ->
            if (expanded) PresetSelector(
                onDismissRequest = onDismissRequest,
                backgroundBrush = backgroundBrush,
                selectedPreset = selectedPreset,
                presetListProvider = presetListProvider,
                onPresetClick = onPresetClick,
                onPresetRenameRequest = onPresetRenameRequest,
                onPresetDeleteRequest = onPresetDeleteRequest)
            else OpenPresetSelectorButton(onClick, selectedPreset, presetIsModified)
        }
    }
}

@Preview @Composable fun FloatingPresetButtonPreview() = SoundAuraTheme {
    var expanded by remember { mutableStateOf(true) }
    val list = List(4) { Preset("Preset $it") }
    var selectedPreset by remember { mutableStateOf(list.first()) }
    Box(Modifier.size(400.dp, 400.dp)) {
        FloatingPresetButton(
            modifier = Modifier.align(Alignment.BottomStart),
            expanded = expanded,
            onDismissRequest = { expanded = false },
            backgroundBrush = Brush.horizontalGradient(
                listOf(MaterialTheme.colors.primaryVariant,
                       MaterialTheme.colors.secondaryVariant)),
            selectedPreset = selectedPreset,
            presetIsModified = false,
            presetListProvider = { list },
            onPresetClick = { selectedPreset = it },
            onPresetRenameRequest = { _, _ -> },
            onPresetDeleteRequest = {},
            onClick = { expanded = true })
    }
}