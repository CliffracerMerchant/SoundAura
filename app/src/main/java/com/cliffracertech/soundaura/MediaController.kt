/* This file is part of SoundAura, which is released under the terms of the Apache
 * License 2.0. See license.md in the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.*

fun Modifier.rotateClockwise() = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(
        minWidth = constraints.minHeight, maxWidth = constraints.maxHeight,
        minHeight = constraints.minWidth, maxHeight = constraints.maxWidth))
    layout(placeable.height, placeable.width) {
        placeable.place(
            x = -(placeable.width / 2 - placeable.height / 2),
            y = -(placeable.height / 2 - placeable.width / 2))
    }
}.rotate(90f)

@Composable fun ActivePresetIndicator(
    modifier: Modifier = Modifier,
    orientation: Orientation,
    activePreset: Preset?,
    activeIsModified: Boolean,
    onClick: () -> Unit,
) {
    val onClickLabel = stringResource(R.string.preset_button_click_label)
    val columnModifier = remember(modifier, orientation) {
        modifier.fillMaxSize()
            .clickable(true, onClickLabel, Role.Button, onClick)
            .then(if (orientation == Orientation.Horizontal)
                      Modifier.padding(start = 12.dp, end = 8.dp)
                  else Modifier.padding(top = 12.dp, bottom = 8.dp)
                               .rotateClockwise())
    }
    Column(columnModifier, Arrangement.Center, Alignment.CenterHorizontally) {
        val style = MaterialTheme.typography.caption
        Text(text = stringResource(
                 if (activePreset == null) R.string.playing
                 else R.string.playing_preset_description),
             maxLines = 1, style = style, softWrap = false)
        Row {
            MarqueeText(
                text = activePreset?.name ?:
                    stringResource(R.string.unsaved_preset_description),
                modifier = Modifier.weight(1f, false),
                style = style)
            if (activeIsModified)
                Text(" *", maxLines = 1, softWrap = false,
                     style = style.copy(fontSize = 14.sp))
        }
    }
}