/* This file is part of SoundAura, which is released under the terms of the Apache
 * License 2.0. See license.md in the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import com.cliffracertech.soundaura.ui.theme.SoundAuraTheme

@Composable private fun CurrentPresetButton(
    currentPreset: Preset?,
    currentIsModified: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) = Column(
    modifier = modifier.clickable(
        onClick = onClick, role = Role.Button,
        onClickLabel = stringResource(R.string.preset_button_click_label)),
    verticalArrangement = Arrangement.Center
) {
    val style = MaterialTheme.typography.caption
    // For some reason setting the column's horizontal alignment to center
    // results in one text being centered, but not the other. Setting both
    // Texts to fill max width and use TextAlign.Center is a workaround.
    Text(modifier = Modifier.fillMaxWidth(),
         textAlign = TextAlign.Center,
         maxLines = 1, style = style,
         text = stringResource(when {
             currentPreset == null -> R.string.unsaved_preset_description
             currentIsModified ->     R.string.playing_modified_preset_description
             else ->                  R.string.playing_preset_description
         }))
    if (currentPreset != null)
        Text(currentPreset.name,
             modifier = Modifier.fillMaxWidth(),
             textAlign = TextAlign.Center,
             maxLines = 1, style = style)
}

/** Create a [GenericShape] clipped to [clipSize] with rounded corners whose radius is
 * equal to [cornerSize]. [clipSize] will be measured from the right in RTL layouts.*/
private fun clippedRoundedCornerShape(
    clipSize: DpSize, cornerSize: Dp, density: Density
) = with(density) {
    GenericShape { size, ld ->
        val cornerRadius = cornerSize.toPx()
        addRoundRect(RoundRect(
            left = if (ld == LayoutDirection.Ltr) 0f
                   else size.width - clipSize.width.toPx(),
            right = if (ld == LayoutDirection.Ltr)
                        clipSize.width.toPx()
                    else size.width,
            top = 0f,
            bottom = clipSize.height.toPx(),
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
    gradientBrush: Brush, clipSize: DpSize, cornerRadius: Dp,
): Modifier = composed {
    val density = LocalDensity.current
    val shape = remember(clipSize, cornerRadius, density) {
        clippedRoundedCornerShape(clipSize, cornerRadius, density)
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
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    currentPreset: Preset?,
    currentPresetIsModified: Boolean,
    onCurrentPresetClick: () -> Unit,
    backgroundBrush: Brush,
    clipSize: DpSize,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
) = Row(
    modifier = modifier
        .height(clipSize.height)
        .horizontalAppGradientBackground(
            backgroundBrush, clipSize, cornerRadius),
    verticalAlignment = Alignment.CenterVertically,
) {
    Row(modifier = Modifier
            .sizeIn(maxWidth = clipSize.width,
                    maxHeight = clipSize.height)
            .padding(end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(Modifier.weight(1f).padding(start = 16.dp, end = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            CurrentPresetButton(
                currentPreset = currentPreset,
                currentIsModified = currentPresetIsModified,
                onClick = onCurrentPresetClick)
        }
        VerticalDivider()
        IconButton(onPlayPauseClick) {
            PlayPauseIcon(isPlaying)
        }
    }
}

@Preview @Composable
fun MediaControllerPreview() = SoundAuraTheme {
    var isPlaying by remember { mutableStateOf(false) }
        MediaController(
            isPlaying = isPlaying,
            onPlayPauseClick = { isPlaying = !isPlaying },
            currentPreset = Preset("Unnamed preset"),
            currentPresetIsModified = true,
            onCurrentPresetClick = {},
            backgroundBrush = Brush.horizontalGradient(listOf(
                MaterialTheme.colors.primaryVariant,
                MaterialTheme.colors.secondaryVariant)),
            clipSize = DpSize(208.dp, 48.dp),
            cornerRadius = 28.dp)
}