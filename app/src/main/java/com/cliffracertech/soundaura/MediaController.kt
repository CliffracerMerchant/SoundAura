/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cliffracertech.soundaura.ui.theme.SoundAuraTheme

private const val springStiffness = 700f

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
    activePreset: Preset?,
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
    expandTransition: Transition<Boolean>,
    transitionProgress: Float,
    isPlaying: Boolean,
    onActivePresetClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onCloseButtonClick: () -> Unit,
    activePreset: Preset?,
    activePresetIsModified: Boolean,
) {
    // This extra end padding when the view is expanded makes the close
    // button align with the more options buttons for each listed preset
    val endPadding by expandTransition.animateDp(
        transitionSpec = { spring(stiffness = springStiffness) },
        label = "MediaController play/pause into preset selector close end padding",
        targetValueByState = { if (it) 4.dp else 0.dp })

    Box(modifier = modifier.fillMaxWidth().padding(end = endPadding)) {
        if (transitionProgress > 0f)
            Text(stringResource(R.string.preset_selector_title),
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer { alpha = transitionProgress },
                textAlign = TextAlign.Center,
                maxLines = 1, softWrap = false,
                style = MaterialTheme.typography.h6)
        if (transitionProgress < 1f) {
            if (orientation == Orientation.Horizontal)
                Row(modifier = Modifier
                    .align(Alignment.CenterStart)
                    .height(56.dp)
                    .padding(end = 52.dp)
                    .graphicsLayer { alpha = 1f - transitionProgress },
                ) {
                    ActivePresetIndicator(
                        orientation = Orientation.Horizontal,
                        activePreset = activePreset,
                        activeIsModified = activePresetIsModified,
                        modifier = Modifier.weight(1f),
                        onClick = onActivePresetClick)
                    VerticalDivider(heightFraction = 0.8f)
                }
            else Column(modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(56.dp)
                .padding(bottom = 52.dp)
                .graphicsLayer { alpha = 1f - transitionProgress }
            ) {
                ActivePresetIndicator(
                    orientation = Orientation.Vertical,
                    activePreset = activePreset,
                    activeIsModified = activePresetIsModified,
                    modifier = Modifier.weight(1f),
                    onClick = onActivePresetClick)
                HorizontalDivider(widthFraction = 0.8f)
            }
        }
        MediaControllerPlayPauseStopButton(
            expandTransition, isPlaying,
            onPlayPauseClick, onCloseButtonClick,
            Modifier.align(Alignment.BottomEnd).size(56.dp))
    }
}

/**
 * A floating button that shows information about the currently playing [Preset]
 * and a play/pause button. When the current preset is clicked, the button will
 * expand into a popup that contains a [PresetList] to allow the user to choose
 * a new preset.
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
 * @param showingPresetSelector Whether or not the floating button should be
 *     expanded to show the preset selector
 * @param isPlaying The is playing state of the media
 * @param onPlayPauseClick The callback that will be invoked when the play/pause button is clicked
 * @param activePreset The actively playing [Preset], if any
 * @param activePresetIsModified Whether or not the active preset has unsaved changes
 * @param onActivePresetClick The callback that will be invoked when the active
 *     preset is clicked
 * @param presetListProvider A lambda that will return the list of presets when invoked
 * @param onPresetRenameRequest The callback that will be invoked when the user
 *     requests the renaming of the [Preset] parameter to the provided [String] value
 * @param onPresetOverwriteRequest The callback that will be invoked when the
 *     user requests the [Preset] parameter to be overwritten with the currently
 *     playing track / volume combination.
 * @param onPresetDeleteRequest The callback that will be invoked when the user
 *     requests the deletion of the [Preset] parameter
 * @param onCloseButtonClick The callback that will be invoked when
 *     [showingPresetSelector] is true and the user clicks the close button
 * @param onPresetClick The callback that will be invoked when the user clicks
 *     on a preset from the list
 */
@Composable fun MediaController(
    modifier: Modifier = Modifier,
    orientation: Orientation,
    backgroundBrush: Brush,
    contentColor: Color,
    collapsedSize: DpSize,
    expandedSize: DpSize,
    showingPresetSelector: Boolean,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    activePreset: Preset?,
    activePresetIsModified: Boolean,
    onActivePresetClick: () -> Unit,
    presetListProvider: () -> List<Preset>,
    onPresetRenameRequest: (Preset, String) -> Unit,
    onPresetOverwriteRequest: (Preset) -> Unit,
    onPresetDeleteRequest: (Preset) -> Unit,
    onCloseButtonClick: () -> Unit,
    onPresetClick: (Preset) -> Unit,
) {
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
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Column(
            modifier = modifier
                .size(animatedWidth, animatedHeight)
                .background(backgroundBrush, RoundedCornerShape(28.dp)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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
                expandTransition = expandTransition,
                transitionProgress = transitionProgress,
                isPlaying = isPlaying,
                onActivePresetClick = onActivePresetClick,
                onPlayPauseClick = onPlayPauseClick,
                onCloseButtonClick = onCloseButtonClick,
                activePreset = activePreset,
                activePresetIsModified = activePresetIsModified)

            if (transitionProgress > 0f)
                PresetList(
                    modifier = Modifier
                        .fillMaxWidth().weight(1f)
                        .padding(8.dp, 0.dp, 8.dp, 8.dp)
                        .background(MaterialTheme.colors.surface,
                                    MaterialTheme.shapes.large),
                    contentPadding = PaddingValues(bottom = 64.dp),
                    activePreset = activePreset,
                    activePresetIsModified = activePresetIsModified,
                    selectionBrush = backgroundBrush,
                    presetListProvider = presetListProvider,
                    onPresetRenameRequest = onPresetRenameRequest,
                    onPresetOverwriteRequest = {
                        onPresetOverwriteRequest(it)
                        onCloseButtonClick()
                    }, onPresetDeleteRequest = onPresetDeleteRequest,
                    onPresetClick = {
                        onPresetClick(it)
                        onCloseButtonClick()
                    })
        }
    }
}

@Preview @Composable
fun FloatingMediaControllerPreview() = SoundAuraTheme {
    var isExpanded by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    val list = remember { mutableStateListOf(
        Preset("Super duper extra really long preset name 0"),
        Preset("Super duper extra really long preset name 1"),
        Preset("Super duper extra really long preset name 2"),
        Preset("Super duper extra really long preset name 3")
    ) }
    var activePreset by remember { mutableStateOf(list.first()) }
    Surface(Modifier.size(400.dp, 600.dp), RectangleShape, Color.White) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            MediaController(
                modifier = Modifier
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                    .align(Alignment.BottomStart),
                orientation = Orientation.Horizontal,
                backgroundBrush = Brush.horizontalGradient(
                    listOf(MaterialTheme.colors.primaryVariant,
                           MaterialTheme.colors.secondaryVariant)),
                contentColor = MaterialTheme.colors.onPrimary,
                collapsedSize = DpSize(220.dp, 56.dp),
                expandedSize = DpSize(388.dp, 250.dp),
                showingPresetSelector = isExpanded,
                isPlaying = isPlaying,
                onPlayPauseClick = { isPlaying = !isPlaying },
                activePreset = activePreset,
                activePresetIsModified = true,
                onActivePresetClick = { isExpanded = true },
                presetListProvider = { list },
                onPresetRenameRequest = { preset, newName ->
                    list.replaceAll { if (it != preset) it
                                      else Preset(newName) }
                },
                onPresetOverwriteRequest = {},
                onPresetDeleteRequest = list::remove,
                onCloseButtonClick = { isExpanded = false },
                onPresetClick = { activePreset = it })
        }
    }
}