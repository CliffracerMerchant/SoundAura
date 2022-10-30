/* This file is part of SoundAura, which is released under the terms of the Apache
 * License 2.0. See license.md in the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import com.cliffracertech.soundaura.ui.theme.SoundAuraTheme

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

@Composable private fun CurrentPresetButton(
    orientation: Orientation,
    currentPreset: Preset?,
    currentIsModified: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val onClickLabel = stringResource(R.string.preset_button_click_label)
    val columnModifier = remember(modifier, orientation) {
        modifier.fillMaxSize()
            .clickable(onClick = onClick, role = Role.Button,
                       onClickLabel = onClickLabel)
            .then(if (orientation == Orientation.Horizontal)
                      Modifier.padding(start = 16.dp, end = 8.dp)
                  else Modifier.padding(top = 16.dp, bottom = 8.dp)
                               .rotateClockwise())
    }
    Column(columnModifier, Arrangement.Center) {
        val style = MaterialTheme.typography.caption
        // For some reason setting the column's horizontal alignment to center
        // results in one text being centered, but not the other. Setting both
        // Texts to fill max width and use TextAlign.Center is a workaround.
        Text(text = stringResource(
                 if (currentPreset == null) R.string.playing
                 else R.string.playing_preset_description),
             modifier = Modifier.fillMaxWidth(),
             textAlign = TextAlign.Center,
             maxLines = 1, style = style)
        Text(modifier = Modifier.fillMaxWidth(),
             textAlign = TextAlign.Center,
             maxLines = 1, style = style,
             text = when {
                 currentPreset == null -> stringResource(R.string.unsaved_preset_description)
                 currentIsModified ->     currentPreset.name + " *"
                 else ->                  currentPreset.name
             })
    }
}

/** Create a [GenericShape] clipped to [clipSize] with rounded corners whose radius is
 * equal to [cornerSize]. [clipSize] will be measured from the right in RTL layouts.*/
private fun clippedRoundedCornerShape(
    clipSize: DpSize,
    cornerSize: Dp,
    density: Density,
    alignment: Alignment,
) = with(density) {
    GenericShape { size, ld ->
        val cornerRadius = cornerSize.toPx()
        val endAligned = alignment == Alignment.TopEnd ||
                         alignment == Alignment.BottomEnd
        val flipHorizontally = (ld == LayoutDirection.Rtl) xor endAligned
        val flipVertically = alignment == Alignment.BottomStart ||
                             alignment == Alignment.BottomEnd
        addRoundRect(RoundRect(
            left = if (!flipHorizontally) 0f
                   else size.width - clipSize.width.toPx(),
            right = if (flipHorizontally) size.width
                    else clipSize.width.toPx(),
            top = if (!flipVertically) 0f
                  else size.height - clipSize.height.toPx(),
            bottom = if (flipVertically) size.height
                     else clipSize.height.toPx(),
            radiusX = cornerRadius,
            radiusY = cornerRadius))
    }
}

/** Applies a gradient background matching [gradientBrush] that stretches
 * across the full parent width, but is then clipped to a rounded rectangle
 * with size equal to [clipSize], with rounded corners whose radius equals
 * [cornerRadius]. This can be used to achieve a screen wide gradient that
 * 'shows through' the object using this modifier. */
fun Modifier.horizontalAppGradientBackground(
    gradientBrush: Brush,
    clipSize: DpSize,
    cornerRadius: Dp,
    alignment: Alignment
): Modifier = composed {
    val density = LocalDensity.current
    val shape = remember(clipSize, cornerRadius, density) {
        clippedRoundedCornerShape(clipSize, cornerRadius, density, alignment)
    }
    this.fillMaxWidth().background(gradientBrush, shape)
}

/**
 * Compose a horizontal bar that contains information about the currently
 * playing [Preset], if any, and a play pause button.
 *
 * @param isPlaying Whether or not the app is currently playing audio
 * @param onPlayPauseClick The callback that will be invoked when the play/pause button is clicked
 * @param currentPreset The current [Preset] that is playing, if any
 * @param currentPresetIsModified Whether or not the current preset has unsaved changes
 * @param onCurrentPresetClick The callback that will be invoked when the
 *     preset indicator section of the media controller bar is clicked
 * @param backgroundBrush The brush that will be used as the background for the bar
 * @param clipSize The desired visual size of the bar. Internally the bar will stretch
 *     itself to match its parent's width, then clip itself down to this size so that
 *     its background brush appears to be showing through the bar.
 * @param cornerRadius The radius of the bar's rounded corners
 * @param modifier The [Modifier] to use for the entire bar
 */
@Composable fun MediaController(
    alignment: Alignment,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    currentPreset: Preset?,
    currentPresetIsModified: Boolean,
    onCurrentPresetClick: () -> Unit,
    backgroundBrush: Brush,
    clipSize: DpSize,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
) = Box(modifier
    .fillMaxSize()
    .horizontalAppGradientBackground(
        backgroundBrush, clipSize, cornerRadius, alignment)
) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colors.onPrimary) {
        if (alignment == Alignment.BottomStart) Row(
            modifier = Modifier
                .sizeIn(maxWidth = clipSize.width, maxHeight = clipSize.height)
                .padding(end = 2.dp)
                .align(alignment),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            CurrentPresetButton(
                orientation = Orientation.Horizontal,
                currentPreset = currentPreset,
                currentIsModified = currentPresetIsModified,
                modifier = Modifier.weight(1f),
                onClick = onCurrentPresetClick)
            VerticalDivider(heightFraction = 0.8f)
            IconButton(onPlayPauseClick) {
                PlayPauseIcon(isPlaying)
            }
        } else Column (
            modifier = Modifier
                .sizeIn(maxWidth = clipSize.width, maxHeight = clipSize.height)
                .padding(bottom = 2.dp)
                .align(alignment),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CurrentPresetButton(
                orientation = Orientation.Vertical,
                currentPreset = currentPreset,
                currentIsModified = currentPresetIsModified,
                modifier = Modifier.weight(1f),
                onClick = onCurrentPresetClick)
            HorizontalDivider(widthFraction = 0.8f)
            IconButton(onPlayPauseClick) {
                PlayPauseIcon(isPlaying)
            }
        }
    }
}

@Preview @Composable
fun HorizontalMediaControllerPreview() = SoundAuraTheme {
    var isPlaying by remember { mutableStateOf(false) }
    Box(Modifier.size(600.dp, 300.dp)) {
        MediaController(
            alignment = Alignment.BottomStart,
            isPlaying = isPlaying,
            onPlayPauseClick = { isPlaying = !isPlaying },
            currentPreset = Preset("Unnamed preset"),
            currentPresetIsModified = true,
            onCurrentPresetClick = {},
            backgroundBrush = Brush.horizontalGradient(listOf(
                MaterialTheme.colors.primaryVariant,
                MaterialTheme.colors.secondaryVariant)),
            clipSize = DpSize(208.dp, 56.dp),
            cornerRadius = 28.dp)
    }
}

@Preview @Composable
fun VerticalMediaControllerPreview() = SoundAuraTheme {
    var isPlaying by remember { mutableStateOf(false) }
    Box(Modifier.size(300.dp, 600.dp)) {
        MediaController(
            alignment = Alignment.TopEnd,
            isPlaying = isPlaying,
            onPlayPauseClick = { isPlaying = !isPlaying },
            currentPreset = Preset("Unnamed preset"),
            currentPresetIsModified = true,
            onCurrentPresetClick = {},
            backgroundBrush = Brush.horizontalGradient(listOf(
                MaterialTheme.colors.primaryVariant,
                MaterialTheme.colors.secondaryVariant)),
            clipSize = DpSize(56.dp, 208.dp),
            cornerRadius = 28.dp)
    }
}