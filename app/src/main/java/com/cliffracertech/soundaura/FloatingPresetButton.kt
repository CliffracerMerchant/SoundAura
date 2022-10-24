/* This file is part of SoundAura, which is released under the terms of the Apache
 * License 2.0. See license.md in the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding

import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import com.cliffracertech.soundaura.ui.theme.SoundAuraTheme

private class FloatingPopupButtonPositionProvider(
    var expanded: Boolean,
    var alignment: Alignment
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        return IntOffset(0, 0)
    }
}

@Composable fun rememberFloatingPopupButtonPositionProvider(
    expanded: Boolean,
    alignment: Alignment
) : PopupPositionProvider {
    val provider = remember {
        FloatingPopupButtonPositionProvider(expanded, alignment)
    }
    provider.expanded = expanded
    provider.alignment = alignment
    return provider
}

@Composable fun FloatingPresetButton(
    modifier: Modifier = Modifier,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    collapsedAlignment: Alignment,
    backgroundColor: Color,
    selectedPreset: Preset? = null,
    presetIsModified: Boolean = false,
    presetListProvider: () -> List<Preset>,
    onPresetClick: (Preset) -> Unit,
    onPresetRenameRequest: (Preset, String) -> Unit,
    onPresetDeleteRequest: (Preset) -> Unit,
    onClick: () -> Unit,
) {
    val alignment = if (expanded) Alignment.Center
                    else          collapsedAlignment
    Popup(
        rememberFloatingPopupButtonPositionProvider(expanded, alignment),
        onDismissRequest = onDismissRequest
    ) {
        Surface(
            modifier = modifier.padding(18.dp).clickable(
                onClickLabel = "Change current preset",
                role = Role.Button,
                onClick = onClick),
            shape = MaterialTheme.shapes.large,
            color = backgroundColor
        ) {
            AnimatedContent(
                targetState = expanded,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220, delayMillis = 90)) +
                        scaleIn(tween(220, delayMillis = 90), initialScale = 0.92f) with
                    fadeOut(animationSpec = tween(90))
            }) { expanded ->
                if (!expanded) Column {
                    Text(when {
                        selectedPreset == null -> "Unsaved preset"
                        presetIsModified ->       "Playing modified preset"
                        else ->                   "Playing preset"
                    })
                    if (selectedPreset != null)
                        Text(selectedPreset.name)
                } else Column {
                    Row(modifier = Modifier.padding(2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Select a preset", Modifier.padding(start = 18.dp))
                        IconButton(onClick = onDismissRequest) {
                            Icon(Icons.Default.Close, "Close preset selection window")
                        }
                    }
                    val gradientStartColor = MaterialTheme.colors.primaryVariant
                    val gradientEndColor = MaterialTheme.colors.secondaryVariant
                    val selectionBrush = remember(gradientStartColor, gradientEndColor) {
                        Brush.horizontalGradient(listOf(gradientStartColor, gradientEndColor))
                    }
                    PresetList(
                        selectedPreset = selectedPreset,
                        selectionBrush = selectionBrush,
                        presetListProvider = presetListProvider,
                        onPresetClick = onPresetClick,
                        onRenameRequest = onPresetRenameRequest,
                        onDeleteRequest = onPresetDeleteRequest)
                }
            }
        }
    }
}

@Preview @Composable
fun FloatingPresetButtonPreview() = SoundAuraTheme {
    var expanded by remember { mutableStateOf(false) }
    val list = List(4) { Preset("Preset $it") }
    var selectedPreset by remember { mutableStateOf(list.first()) }

    FloatingPresetButton(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        collapsedAlignment = Alignment.BottomStart,
        backgroundColor = MaterialTheme.colors.primaryVariant,
        selectedPreset = selectedPreset,
        presetIsModified = false,
        presetListProvider = { list },
        onPresetClick = { selectedPreset = it },
        onPresetRenameRequest = { _, _ -> },
        onPresetDeleteRequest = {},
        onClick = { if (!expanded) expanded = true })
}