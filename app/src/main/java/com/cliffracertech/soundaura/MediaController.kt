/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import com.cliffracertech.soundaura.ui.theme.SoundAuraTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

private const val springStiffness = 600f
//private const val springStiffness = 10f

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

val Orientation.isHorizontal get() = this == Orientation.Horizontal
val Orientation.isVertical get() = this == Orientation.Vertical

@Composable private fun ActivePresetIndicator(
    modifier: Modifier = Modifier,
    orientation: Orientation,
    maxWidthPx: Int,
    activePresetProvider: () -> Preset?,
    activeIsModified: Boolean,
    onClick: () -> Unit,
) {
    val onClickLabel = stringResource(R.string.preset_button_click_label)
    val columnModifier = remember(modifier, orientation) {
        modifier.fillMaxSize()
            .clickable(true, onClickLabel, Role.Button, onClick)
            .then(if (orientation.isHorizontal)
                      Modifier.padding(start = 12.dp, end = 8.dp)
                  else Modifier.padding(top = 12.dp, bottom = 8.dp)
                               .rotateClockwise())
    }
    Column(columnModifier, Arrangement.Center, Alignment.CenterHorizontally) {
        val style = MaterialTheme.typography.caption
        val activePreset = activePresetProvider()
        Text(text = stringResource(
                 if (activePreset == null) R.string.playing
                 else R.string.playing_preset_description),
             maxLines = 1, style = style, softWrap = false)
        Row {
            MarqueeText(
                text = activePreset?.name ?:
                    stringResource(R.string.unsaved_preset_description),
                maxWidthPx = maxWidthPx,
                modifier = Modifier.weight(1f, false),
                style = style)
            if (activeIsModified)
                Text(" *", maxLines = 1, softWrap = false, style = style)
        }
    }
}

@Composable private fun MediaControllerPlayPauseStopButton(
    expandTransition: Transition<Boolean>,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onCloseButtonClick: () -> Unit,
    modifier: Modifier = Modifier,
) = IconButton(modifier = modifier, onClick = {
    if (expandTransition.targetState)
        onCloseButtonClick()
    else onPlayPauseClick()
}) {
    PlayPauseCloseIcon(
        showClose = expandTransition.targetState,
        isPlaying = isPlaying,
        closeToPlayPause = !expandTransition.targetState &&
                           expandTransition.currentState,
        contentDescriptionProvider = { showClose, isPlaying ->
            stringResource(when {
                showClose -> R.string.close_preset_selector_description
                isPlaying -> R.string.pause_button_description
                else ->      R.string.play_button_description
            })
        })
}

@Composable private fun MediaControllerAndPresetSelectorTitle(
    modifier: Modifier = Modifier,
    orientation: Orientation,
    maxWidthPx: Int,
    expandTransition: Transition<Boolean>,
    transitionProgressProvider: () -> Float,
    playing: Boolean,
    onActivePresetClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onCloseButtonClick: () -> Unit,
    activePresetProvider: () -> Preset?,
    activePresetIsModified: Boolean,
) = Box(modifier = modifier.fillMaxWidth()) {
    val isExpandedOrExpanding = expandTransition.targetState ||
                                expandTransition.isRunning
    if (isExpandedOrExpanding)
        Text(stringResource(R.string.preset_selector_title),
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer { alpha = transitionProgressProvider() },
            maxLines = 1, softWrap = false,
            style = MaterialTheme.typography.h6)

    val isCollapsedOrCollapsing = !expandTransition.targetState ||
                                  expandTransition.isRunning
    val density = LocalDensity.current
    if (isCollapsedOrCollapsing) {
        val maxWidth = maxWidthPx - with (density) { 56.dp.roundToPx() }
        if (orientation == Orientation.Horizontal)
            Row(modifier = Modifier
                .align(Alignment.CenterStart)
                .height(56.dp)
                .padding(end = 52.dp)
                .graphicsLayer { alpha = 1f - transitionProgressProvider() },
            ) {
                ActivePresetIndicator(
                    modifier = Modifier.weight(1f),
                    orientation = Orientation.Horizontal,
                    maxWidthPx = maxWidth,
                    activePresetProvider = activePresetProvider,
                    activeIsModified = activePresetIsModified,
                    onClick = onActivePresetClick)
                VerticalDivider(heightFraction = 0.8f)
            }
        else Column(modifier = Modifier
            .align(Alignment.CenterEnd)
            .width(56.dp)
            .padding(bottom = 52.dp)
            .graphicsLayer { alpha = 1f - transitionProgressProvider() }
        ) {
            ActivePresetIndicator(
                modifier = Modifier.weight(1f),
                orientation = Orientation.Vertical,
                maxWidthPx = maxWidth,
                activePresetProvider = activePresetProvider,
                activeIsModified = activePresetIsModified,
                onClick = onActivePresetClick)
            HorizontalDivider(widthFraction = 0.8f)
        }
    }

    // This extra  when the view is expanded makes the close
    // button align with the more options buttons for each listed preset
    val closeButtonXOffset = remember { with (density) { 4.dp.toPx() } }
    MediaControllerPlayPauseStopButton(
        expandTransition, playing, onPlayPauseClick, onCloseButtonClick,
        Modifier.align(Alignment.BottomEnd).size(56.dp).graphicsLayer {
            translationX = -closeButtonXOffset * transitionProgressProvider()
        })
}

/**
 * A floating button that shows information about the currently playing [Preset]
 * and a play/pause button. When the parameter [showingPresetSelector] is true,
 * the button will animate its transformation into a preset selector. In this
 * state it will contain a [PresetList] to allow the user to choose a new preset.
 *
 * @param modifier The [Modifier] to use for the button / popup
 * @param orientation An [Orientation] value that indicates how the media
 *     controller should orient itself
 * @param backgroundBrush A [Brush] to use as the background. This is passed
 *     as a separate parameter instead of allowing the caller to accomplish
 *     this through a [Modifier] so that the [Brush] can be applied across the
 *     whole parent size, and then clipped down to the size of the contents.
 * @param collapsedSize The size of the media controller when [showingPresetSelector] is false
 * @param expandedSize The size of the media controller when [showingPresetSelector] is true
 * @param alignment The [BiasAlignment] to use for placement. This alignment should
 *     not be applied through the [modifier] parameter or it will be applied twice.
 * @param padding The [PaddingValues] to use for placement. This padding should not
 *     be applied through the [modifier] parameter or it will be applied twice.
 * @param showingPresetSelector Whether or not the floating button should be
 *     expanded to show the preset selector
 * @param playing The media play/pause state that the play/pause button should
 *     use to determine its icon, which will be the opposite of the current state
 * @param onPlayPauseClick The callback that will be invoked when the play/pause button is clicked
 * @param activePresetProvider A function that returns the actively
 *     playing [Preset], or null if there isn't one, when invoked
 * @param activePresetIsModified Whether or not the active preset has unsaved changes
 * @param onActivePresetClick The callback that will be invoked when the active preset is clicked
 * @param presetListProvider A lambda that will return the list of presets when invoked
 * @param onPresetRenameRequest The callback that will be invoked when the user
 *     requests the renaming of the [Preset] parameter to the provided [String] value
 * @param onPresetOverwriteRequest The callback that will be invoked when the
 *     user requests the [Preset] parameter to be overwritten with the currently
 *     playing track / volume combination.
 * @param onPresetDeleteRequest The callback that will be invoked when the user
 *     requests the deletion of the [Preset] parameter
 * @param onPresetSelectorCloseButtonClick The callback that will be invoked when
 *     [showingPresetSelector] is true and the user clicks the close button
 * @param onPresetSelectorPresetClick The callback that will be invoked when the user clicks
 *     on a preset from the [PresetList]
 */
@Composable fun MediaController(
    modifier: Modifier = Modifier,
    orientation: Orientation,
    backgroundBrush: Brush,
    contentColor: Color,
    collapsedSize: DpSize,
    expandedSize: DpSize,
    alignment: BiasAlignment,
    padding: PaddingValues,
    playing: Boolean,
    onPlayPauseClick: () -> Unit,
    activePresetProvider: () -> Preset?,
    activePresetIsModified: Boolean,
    onActivePresetClick: () -> Unit,
    showingPresetSelector: Boolean,
    presetListProvider: () -> ImmutableList<Preset>?,
    onPresetRenameRequest: (Preset, String) -> Unit,
    onPresetOverwriteRequest: (Preset) -> Unit,
    onPresetDeleteRequest: (Preset) -> Unit,
    onPresetSelectorCloseButtonClick: () -> Unit,
    onPresetSelectorPresetClick: (Preset) -> Unit,
) = CompositionLocalProvider(LocalContentColor provides contentColor) {
    val expandTransition = updateTransition(
        targetState = showingPresetSelector,
        label = "FloatingMediaController transition")
    val transitionProgress by expandTransition.animateFloat(
        transitionSpec = { spring(stiffness = springStiffness) },
        label = "FloatingMediaController transition progress",
        targetValueByState = { if (it) 1f else 0f })
    val animatedWidth by expandTransition.animateDp(
        transitionSpec = { spring(stiffness = springStiffness) },
        label = "FloatingMediaController width transition",
        targetValueByState = { if (it) expandedSize.width
                               else    collapsedSize.width })
    val animatedHeight by expandTransition.animateDp(
        transitionSpec = { spring(stiffness = springStiffness) },
        label = "FloatingMediaController height transition",
        targetValueByState = { if (it) expandedSize.height
                               else    collapsedSize.height })

    val density = LocalDensity.current
    val cornerRadius = remember {
        val radius = with (density) { 28.dp.toPx() }
        CornerRadius(radius, radius)
    }
    ClippedBrushBox(
        modifier = modifier,
        brush = backgroundBrush,
        width = animatedWidth,
        height = animatedHeight,
        cornerRadius = cornerRadius,
        padding = padding,
        alignment = alignment,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val titleHeight by expandTransition.animateDp(
                transitionSpec = { spring(stiffness = springStiffness) },
                label = "FloatingMediaController title height transition",
            ) { expanded ->
                if (expanded && orientation.isVertical)
                    collapsedSize.width
                else collapsedSize.height
            }
            MediaControllerAndPresetSelectorTitle(
                modifier = Modifier.height(titleHeight),
                orientation = orientation,
                maxWidthPx = with(LocalDensity.current) {
                    collapsedSize.width.roundToPx()
                }, expandTransition = expandTransition,
                transitionProgressProvider = { transitionProgress },
                playing = playing,
                onActivePresetClick = onActivePresetClick,
                onPlayPauseClick = onPlayPauseClick,
                onCloseButtonClick = onPresetSelectorCloseButtonClick,
                activePresetProvider = activePresetProvider,
                activePresetIsModified = activePresetIsModified)

            val listPadding = 8.dp
            val shape = MaterialTheme.shapes.large
            val presetListSize = remember {
                val expandedTitleHeight =
                    if (orientation.isVertical) collapsedSize.width
                    else                        collapsedSize.height
                DpSize(expandedSize.width - listPadding * 2,
                       expandedSize.height - expandedTitleHeight - listPadding)
            }
            val minScaleX = remember {
                (collapsedSize.width - listPadding * 2) /
                (expandedSize.width - listPadding * 2)
            }
            if (expandTransition.run { currentState || isRunning })
                PresetList(
                    modifier = Modifier
                        .requiredSize(presetListSize)
                        .graphicsLayer {
                            alpha = transitionProgress
                            scaleY = transitionProgress
                            scaleX = minScaleX + (1f - minScaleX) * transitionProgress
                        }.background(MaterialTheme.colors.surface, shape),
                    contentPadding = PaddingValues(bottom = 64.dp),
                    activePresetProvider = activePresetProvider,
                    activePresetIsModified = activePresetIsModified,
                    selectionBrush = backgroundBrush,
                    presetListProvider = presetListProvider,
                    onPresetRenameRequest = onPresetRenameRequest,
                    onPresetOverwriteRequest = onPresetOverwriteRequest,
                    onPresetDeleteRequest = onPresetDeleteRequest,
                    onPresetClick = onPresetSelectorPresetClick)
        }
    }
}

@Preview @Composable
fun MediaControllerPreview() = SoundAuraTheme {
    var expanded by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    val list = remember { listOf(
        Preset("Super duper extra really long preset name 0"),
        Preset("Super duper extra really long preset name 1"),
        Preset("Super duper extra really long preset name 2"),
        Preset("Super duper extra really long preset name 3")
    ).toImmutableList() }
    var activePreset by remember { mutableStateOf<Preset?>(list.first()) }
    Surface(Modifier.size(400.dp, 600.dp), RectangleShape, Color.White) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            MediaController(
                orientation = Orientation.Horizontal,
                backgroundBrush = Brush.horizontalGradient(
                    listOf(MaterialTheme.colors.primaryVariant,
                           MaterialTheme.colors.secondaryVariant)),
                contentColor = MaterialTheme.colors.onPrimary,
                collapsedSize = DpSize(220.dp, 56.dp),
                expandedSize = DpSize(388.dp, 250.dp),
                alignment = Alignment.BottomStart as BiasAlignment,
                padding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 8.dp),
                showingPresetSelector = expanded,
                playing = playing,
                onPlayPauseClick = { playing = !playing },
                activePresetProvider = { activePreset },
                activePresetIsModified = true,
                onActivePresetClick = { expanded = true },
                presetListProvider = { list },
                onPresetRenameRequest = { _, _ -> },
                onPresetOverwriteRequest = {},
                onPresetDeleteRequest = {},
                onPresetSelectorCloseButtonClick = { expanded = false },
                onPresetSelectorPresetClick = { activePreset = it })
        }
    }
}