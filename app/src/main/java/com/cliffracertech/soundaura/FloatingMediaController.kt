/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.graphics.RectF
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.cliffracertech.soundaura.ui.theme.SoundAuraTheme

private val rect = RectF()
private val path = Path()

/**
 * A box that applies the [gradient] [Brush] across its parent's entire width,
 * then clips the gradient down to a rounded rect matching the [size] and
 * [cornerRadius] parameters, aligned according to [alignment]. This allows
 * a part of the [Brush] to show through the box depending on its placement
 * on the screen.
 */
@Composable fun BoxWithConstraintsScope.GradientDialogBox(
    modifier: Modifier = Modifier,
    gradient: Brush,
    size: Size,
    cornerRadius: Float,
    alignment: BiasAlignment,
    content: @Composable BoxScope.() -> Unit
) = Box(modifier.fillMaxSize().drawBehind {
    // BiasAlignment's values are in the range [-1,1] instead of [0,1]
    val horizontalBias = (alignment.horizontalBias + 1f) / 2f
    val verticalBias = (alignment.verticalBias + 1f) / 2f
    rect.left = (constraints.maxWidth - size.width) * horizontalBias
    rect.top = (constraints.maxHeight - size.height) * verticalBias
    rect.right = rect.left + size.width
    rect.bottom = rect.top + size.height
    path.reset()
    path.asAndroidPath().addRoundRect(
        rect, cornerRadius, cornerRadius,
        android.graphics.Path.Direction.CW)
    drawPath(path, gradient)
}) {
    val dpSize = with(LocalDensity.current) { size.toDpSize() }
    Box(Modifier.size(dpSize).align(alignment)
            // This empty clickable prevents clicks from going through
            // the drawn background to components underneath it
            .clickable(remember { MutableInteractionSource() },
                       indication = null, onClick = {}),
        content = content)
}

@Composable private fun MediaControllerToPresetSelectorTitle(
    modifier: Modifier = Modifier,
    expandTransition: Transition<Boolean>,
    transitionProgress: Float,
    isPlaying: Boolean,
    onActivePresetClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onCloseButtonClick: () -> Unit,
    activePreset: Preset?,
    activePresetIsModified: Boolean,
) {
    val playPauseToCloseIconEndPadding by expandTransition.animateDp(
        label = "MediaController play/pause into preset selector close end padding",
        targetValueByState = { if (it) 8.dp else 2.dp })
    val isExpandingOrExpanded = expandTransition.targetState

    Row(modifier = modifier
        .fillMaxWidth()
        .padding(end = playPauseToCloseIconEndPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), Alignment.Center) {
            if (transitionProgress != 0f)
                Text(stringResource(R.string.preset_selector_title),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(start = 16.dp, top = 4.dp,
                                 bottom = 2.dp, end = 8.dp)
                        .graphicsLayer { alpha = transitionProgress },
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.h6)
            if (transitionProgress != 1f)
                Row(Modifier.align(Alignment.Center)
                    .graphicsLayer { alpha = 1f - transitionProgress },
                ) {
                    CurrentPresetButton(
                        orientation = Orientation.Horizontal,
                        currentPreset = activePreset,
                        currentIsModified = activePresetIsModified,
                        modifier = Modifier.weight(1f),
                        onClick = onActivePresetClick)
                    VerticalDivider(heightFraction = 0.8f)
                }
        }
        IconButton(onClick = {
            if (isExpandingOrExpanded) onCloseButtonClick()
            else                       onPlayPauseClick()
        }) {
            PlayPauseCloseIcon(
                showClose = isExpandingOrExpanded,
                isPlaying = isPlaying,
                closeToPlayPause = !isExpandingOrExpanded &&
                                   expandTransition.currentState,
                contentDescriptionProvider = { showClose, isPlaying ->
                    stringResource(when {
                        showClose -> R.string.close_preset_selector_description
                        isPlaying -> R.string.pause_button_description
                        else ->      R.string.play_button_description
                    })
                })
//            Icon(Icons.Default.Close, null)
        }
    }
}

/**
 * A floating button that shows information about the currently playing [Preset]
 * and a play/pause button. When the current preset is clicked, the button will
 * expand into a popup that contains a [PresetList] to allow the user to choose
 * a new preset.
 *
 * @param modifier The [Modifier] to use for the button / popup
 * @param backgroundBrush A [Brush] to use as the background. This is passed
 *     as a separate parameter instead of allowing the caller to accomplish
 *     this through a [Modifier] so that the [Brush] can be applied across the
 *     whole parent size, and then clipped down to the size of the contents.
 * @param cornerRadius The radius of the button's rounded corners
 * @param alignment A BiasAlignment to use for the content. The only supported
 *     alignments are BottomStart and TopEnd. The contents will change their
 *     layout to better suit each alignment.
 * @param isPlaying The is playing state of the media
 * @param onPlayPauseClick The callback that will be invoked when the play/pause button is clicked
 * @param activePreset The actively playing [Preset], if any
 * @param activePresetIsModified Whether or not the active preset has unsaved changes
 * @param presetListProvider A lambda that will return the list of presets when invoked
 * @param onPresetRenameRequest The callback that will be invoked when the user
 *     requests the renaming of the [Preset] parameter to the provided [String] value
 * @param onPresetOverwriteRequest The callback that will be invoked when the
 *     user requests the [Preset] parameter to be overwritten with the currently
 *     playing track / volume combination.
 * @param onPresetDeleteRequest The callback that will be invoked when the user
 *     requests the deletion of the [Preset] parameter
 * @param onPresetClick The callback that will be invoked when the user clicks
 *     on a preset from the list
 */
@Composable fun FloatingMediaController(
    modifier: Modifier = Modifier,
    backgroundBrush: Brush,
    cornerRadius: Dp = 28.dp,
    alignment: BiasAlignment,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    activePreset: Preset?,
    activePresetIsModified: Boolean,
    presetListProvider: () -> List<Preset>,
    onPresetRenameRequest: (Preset, String) -> Unit,
    onPresetOverwriteRequest: (Preset) -> Unit,
    onPresetDeleteRequest: (Preset) -> Unit,
    onPresetClick: (Preset) -> Unit,
) = BoxWithConstraints(modifier.fillMaxSize()) {
    require(alignment == Alignment.BottomStart || alignment == Alignment.TopEnd)
    var isExpanded by remember { mutableStateOf(false) }
    val expandTransition = updateTransition(isExpanded,
        label = "MediaController into PresetSelector transition")
    val density = LocalDensity.current
    val collapsedDpSize = remember(constraints, alignment, density) {
        with (density) { DpSize(
            // The goal is to have the media controller bar have such a
            // width/height that the play/pause icon is centered in the
            // screen's width/height. The main content area's width/height
            // is divided by two, then 28dp is added to to account for the
            // media controller bar's rounded corner radius, then because
            // the constraints do not account for it, either the entire
            // start padding (for bottom aligned layouts in portrait) or
            // half of the top padding (for end aligned layouts in landscape)
            // is added. Only half the top padding is added because space is
            // more constrained when the media controller bar is being
            // aligned to the screen's end.
            height = if (alignment == Alignment.BottomStart) 56.dp
                     else (constraints.maxHeight / 2f).toDp() + 28.dp,
            width = if (alignment == Alignment.TopEnd) 56.dp
                    else (constraints.maxWidth / 2f).toDp() + 28.dp)
        }
    }
    val cornerRadiusF = with (density) { cornerRadius.toPx() }
    val collapsedSize = with(density) { collapsedDpSize.toSize() }
    val presetSelectorSize = remember { Size(
        width = if (alignment == Alignment.TopEnd)
                    constraints.maxWidth / 2.5f
                else constraints.maxWidth.toFloat(),
        height = minOf(with(density) { 350.dp.toPx() },
                       constraints.maxHeight.toFloat())
    )}
    val animatedSize by expandTransition.animateSize(
//        transitionSpec = { tween(2000, 0) },
        label = "MediaController into PresetSelector size transition",
        targetValueByState = { if (it) presetSelectorSize else collapsedSize })
    val transitionProgress by expandTransition.animateFloat(
//        transitionSpec = { tween(2000, 0) },
        label = "MediaController into PresetSelector transition progress",
        targetValueByState = { if (it) 1f else 0f })
    GradientDialogBox(
        gradient = backgroundBrush,
        size = animatedSize,
        cornerRadius = cornerRadiusF,
        alignment = alignment,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MediaControllerToPresetSelectorTitle(
                modifier = Modifier.height(collapsedDpSize.height),
                expandTransition = expandTransition,
                transitionProgress = transitionProgress,
                isPlaying = isPlaying,
                onActivePresetClick = { isExpanded = true },
                onPlayPauseClick = onPlayPauseClick,
                onCloseButtonClick = { isExpanded = false },
                activePreset = activePreset,
                activePresetIsModified = activePresetIsModified)
            if (transitionProgress > 0f) PresetList(
                modifier = Modifier
                    .fillMaxSize().padding(8.dp, 0.dp, 8.dp, 8.dp)
                    .background(MaterialTheme.colors.surface, MaterialTheme.shapes.large),
                contentPadding = PaddingValues(bottom = 72.dp),
                activePreset = activePreset,
                activePresetIsModified = activePresetIsModified,
                selectionBrush = backgroundBrush,
                presetListProvider = presetListProvider,
                onPresetRenameRequest = onPresetRenameRequest,
                onPresetOverwriteRequest = {
                    onPresetOverwriteRequest(it)
                    isExpanded = false
                }, onPresetDeleteRequest = onPresetDeleteRequest,
                onPresetClick = {
                    onPresetClick(it)
                    isExpanded = false
                })
        }
    }
}

@Preview @Composable
fun FloatingMediaControllerPreview() = SoundAuraTheme {
    var isPlaying by remember { mutableStateOf(false) }
    val list = remember { mutableStateListOf(
        Preset("Super duper extra really long preset name 0"),
        Preset("Super duper extra really long preset name 1"),
        Preset("Super duper extra really long preset name 2"),
        Preset("Super duper extra really long preset name 3")
    ) }
    var activePreset by remember { mutableStateOf(list.first()) }
    Surface(Modifier.size(400.dp, 600.dp), RectangleShape, Color.White) {
        FloatingMediaController(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
            backgroundBrush = Brush.horizontalGradient(
                listOf(MaterialTheme.colors.primaryVariant,
                       MaterialTheme.colors.secondaryVariant)
            ), cornerRadius = 28.dp,
            alignment = Alignment.BottomStart as BiasAlignment,
            isPlaying = isPlaying,
            onPlayPauseClick = { isPlaying = !isPlaying },
            activePreset = activePreset,
            activePresetIsModified = true,
            presetListProvider = { list },
            onPresetRenameRequest = { preset, newName ->
                list.replaceAll { if (it != preset) it
                                  else Preset(newName) }
            },
            onPresetOverwriteRequest = {},
            onPresetDeleteRequest = list::remove,
            onPresetClick = { activePreset = it })
    }
}

