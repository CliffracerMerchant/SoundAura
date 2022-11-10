/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.cliffracertech.soundaura.ui.theme.SoundAuraTheme

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
        }
    }
}

val Orientation.isHorizontal get() = this == Orientation.Horizontal
val Orientation.isVertical get() = this == Orientation.Vertical

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
@Composable fun FloatingMediaController(
    modifier: Modifier = Modifier,
    orientation: Orientation,
    backgroundBrush: Brush,
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
        transitionSpec = { spring(stiffness = 700f) },
        label = "FloatingMediaController transition progress",
        targetValueByState = { if (it) 1f else 0f })
    val animatedWidth by expandTransition.animateDp(
        transitionSpec = { spring(stiffness = 700f) },
        label = "FloatingMediaController width transition",
        targetValueByState = { if (it) expandedSize.width
                               else    collapsedSize.width })
    val animatedHeight by expandTransition.animateDp(
        transitionSpec = { spring(stiffness = 700f) },
        label = "FloatingMediaController height transition",
        targetValueByState = { if (it) expandedSize.height
                               else    collapsedSize.height })

    Column(
        modifier = modifier
            .size(animatedWidth, animatedHeight)
            .background(backgroundBrush, RoundedCornerShape(28.dp)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MediaControllerToPresetSelectorTitle(
            modifier = Modifier.height(collapsedSize.height),
            expandTransition = expandTransition,
            transitionProgress = transitionProgress,
            isPlaying = isPlaying,
            onActivePresetClick = onActivePresetClick,
            onPlayPauseClick = onPlayPauseClick,
            onCloseButtonClick = onCloseButtonClick,
            activePreset = activePreset,
            activePresetIsModified = activePresetIsModified)
        if (transitionProgress > 0f) PresetList(
            modifier = Modifier
                .fillMaxSize().padding(8.dp, 0.dp, 8.dp, 8.dp)
                .background(MaterialTheme.colors.surface, MaterialTheme.shapes.large),
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
            FloatingMediaController(
                modifier = Modifier
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                    .align(Alignment.BottomStart),
                orientation = Orientation.Horizontal,
                backgroundBrush = Brush.horizontalGradient(
                    listOf(MaterialTheme.colors.primaryVariant,
                           MaterialTheme.colors.secondaryVariant)),
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

