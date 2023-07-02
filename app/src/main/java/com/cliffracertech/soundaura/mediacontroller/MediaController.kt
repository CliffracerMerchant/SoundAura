/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.mediacontroller

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.cliffracertech.soundaura.R
import com.cliffracertech.soundaura.model.database.Preset
import com.cliffracertech.soundaura.ui.MarqueeText
import com.cliffracertech.soundaura.ui.theme.SoundAuraTheme
import com.cliffracertech.soundaura.ui.tweenDuration
import kotlinx.collections.immutable.toImmutableList
import java.time.Instant

val Orientation.isHorizontal get() = this == Orientation.Horizontal
val Orientation.isVertical get() = this == Orientation.Vertical

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

/** A collection of callbacks related to the display of an active preset. */
interface ActivePresetCallback {
    /** A function that returns the actively playing [Preset]'s
    * name, or null if there isn't one, when invoked */
    fun getName(): String?
    /** A function that returns whether or not the
     * active preset has unsaved changes when invoked */
    fun getIsModified(): Boolean
    /** The callback that will be invoked when
     * the display of the active preset is clicked */
    fun onClick()
}

@Composable private fun ActivePresetDisplay(
    sizes: MediaControllerSizes,
    callback: ActivePresetCallback,
    modifier: Modifier = Modifier,
) {
    val onClickLabel = stringResource(R.string.preset_button_click_label)
    val columnModifier = remember(modifier, sizes.orientation) {
        modifier.size(sizes.activePresetSize)
                .clip(sizes.activePresetShape)
                .clickable(true, onClickLabel, Role.Button, callback::onClick)
                .then(if (sizes.orientation.isHorizontal)
                          Modifier.padding(start = 12.dp, end = 8.dp)
                    else Modifier.padding(top = 12.dp, bottom = 8.dp)
                                   .rotateClockwise())
    }
    Column(columnModifier, Arrangement.Center, Alignment.CenterHorizontally) {
        val style = MaterialTheme.typography.caption
        val activePresetName = callback.getName()
        Text(text = stringResource(R.string.playing),
             maxLines = 1, style = style, softWrap = false)
        Row {
            MarqueeText(
                text = activePresetName ?:
                    stringResource(R.string.unsaved_preset_description),
                maxWidth = sizes.activePresetLength,
                modifier = Modifier.weight(1f, false),
                style = style)
            AnimatedVisibility(callback.getIsModified()) {
                Text(" *", style = style)
            }
        }
    }
}

/** A wrapper around [StopTimer] that animates its appearance and
 * disappearance as it changes between null and non-null values. */
@Composable private fun MediaControllerStopTimer(
    sizes: MediaControllerSizes,
    stopTimeProvider: () -> Instant?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val stopTime = stopTimeProvider()
    // lastNonNullStopTime is used so that the StopTimerDisplay will
    // fade out with its last non-null value when it changes to null.
    var lastNonNullStopTime by remember { mutableStateOf(Instant.MIN) }
    stopTime?.let { lastNonNullStopTime = it }

    val appearanceProgress by animateFloatAsState(
        targetValue = if (stopTime != null) 1f else 0f,
        animationSpec = tween(tweenDuration, 0, LinearOutSlowInEasing),
        label = "Auto stop time appearance transition")
    if (appearanceProgress == 0f) return

    val translationPercent = (1f - appearanceProgress) / -2f
    val size = sizes.stopTimerSize
    val clickLabel = stringResource(R.string.stop_timer_click_label)

    LinearLayout(
        orientation = sizes.orientation,
        modifier = modifier
            .requiredSize(size)
            .graphicsLayer {
                alpha = appearanceProgress
                translationX = if (sizes.orientation.isVertical) 0f else
                                   translationPercent * size.width.toPx()
                translationY = if (sizes.orientation.isHorizontal) 0f else
                                   translationPercent * size.height.toPx()
            }.clip(sizes.stopTimerShape)
            .clickable(true, clickLabel, Role.Button, onClick),
    ) {
        Divider(sizes.orientation, sizeFraction = 0.8f)
        StopTimer(lastNonNullStopTime, Modifier.fillMaxSize())
    }
}

/**
 * The content of a [MediaController] instance when it is collapsed
 * (i.e. not showing the preset selector).
 *
 * @param sizes The [MediaControllerSizes] instance that describes
 *     the sizes of [MediaController]'s internal elements.
 * @param transitionProgressProvider A method that returns the
 *     current progress of the [MediaController]'s show/hide
 *     preset selector transition when invoked
 * @param activePresetCallback The [ActivePresetCallback] that will be used
 *     to determine the display and function of the active preset indicator
 * @param playButtonCallback The [PlayButtonCallback] that will be
 *     used to determine the display and function of the play/pause button
 * @param stopTimeProvider A method that will return the [Instant] at which
 *     media will automatically stop playing, if any, when invoked. This value
 *     is only used for informational display; playback is not affected by
 *     this value.
 * @param onStopTimerClick The callback that will be invoked when
 *     the display of the stop time is clicked
 * @param modifier The [Modifier] to use for the composable
 */
@Composable private fun MediaControllerCollapsedContent(
    sizes: MediaControllerSizes,
    transitionProgressProvider: () -> Float,
    activePresetCallback: ActivePresetCallback,
    playButtonCallback: PlayButtonCallback,
    stopTimeProvider: () -> Instant?,
    onStopTimerClick: () -> Unit,
    modifier: Modifier = Modifier,
) = LinearLayout(
    orientation = sizes.orientation,
    modifier = modifier
        .graphicsLayer { alpha = 1f - transitionProgressProvider() }
) {
    ActivePresetDisplay(sizes, activePresetCallback)
    Divider(sizes.orientation, sizeFraction = 0.8f)
    PlayButton(
        callback = playButtonCallback,
        modifier = Modifier
            .size(sizes.buttonSize)
            .clip(sizes.playButtonShape(stopTimeProvider() != null)))
    MediaControllerStopTimer(sizes, stopTimeProvider,
                             onClick = onStopTimerClick)
}

/**
 * The content of a [MediaController] instance when it is expanded
 * (i.e. showing the preset selector.
 *
 * @param sizes The [MediaControllerSizes] instance that describes
 *     the sizes of [MediaController]'s internal elements.
 * @param onCloseButtonClick The method that will be invoked when
 *     the close button of the preset selector title is clicked
 * @param transitionProgressProvider A method that returns the
 *     current progress of the [MediaController]'s show/hide
 *     preset selector transition when invoked
 * @param modifier The [Modifier] to use for the composable
 */
@Composable private fun PresetSelectorTitle(
    sizes: MediaControllerSizes,
    onCloseButtonClick: () -> Unit,
    transitionProgressProvider: () -> Float,
    modifier: Modifier = Modifier,
) = Box(modifier
    .height(sizes.minThickness)
    .fillMaxWidth()      // This end padding makes the close button align
    .padding(end = 8.dp) // with the more options buttons for each preset
    .graphicsLayer { alpha = transitionProgressProvider() }
) {
    Text(text = stringResource(R.string.preset_selector_title),
        modifier = Modifier.align(Alignment.Center)
            .requiredWidth(sizes.presetSelectorSize.width),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.h6)
    IconButton(onCloseButtonClick, Modifier.align(Alignment.CenterEnd)) {
        Icon(Icons.Default.Close,
            stringResource(R.string.close_preset_selector_description))
    }
}

/** A wrapper around [PresetList] that scales its size
 * with the [MediaController]'s expansion progress */
@Composable private fun MediaControllerPresetList(
    sizes: MediaControllerSizes,
    hasStopTimer: Boolean,
    backgroundBrush: Brush,
    transitionProgressProvider: () -> Float,
    activePresetCallback: ActivePresetCallback,
    presetListCallback: PresetListCallback,
) {
    val listPadding = 8.dp
    val presetListSize = remember(sizes) {
        val expandedTitleHeight = sizes.minThickness
        val expandedWidth = sizes.presetSelectorSize.width
        val expandedHeight = sizes.presetSelectorSize.height
        DpSize(expandedWidth - listPadding * 2,
               expandedHeight - expandedTitleHeight - listPadding)
    }
    val minScaleX = remember(sizes, hasStopTimer) {
        val collapsedWidth = sizes.collapsedSize(hasStopTimer).width
        (collapsedWidth - listPadding * 2) / presetListSize.width
    }
    if (transitionProgressProvider() > 0f)
        PresetList(
            modifier = Modifier
                .requiredSize(presetListSize)
                .graphicsLayer {
                    alpha = transitionProgressProvider()
                    scaleY = transitionProgressProvider()
                    scaleX = minScaleX + (1f - minScaleX) * transitionProgressProvider()
                }.background(MaterialTheme.colors.surface,
                             MaterialTheme.shapes.large),
            contentPadding = PaddingValues(bottom = 64.dp),
            activePresetCallback = activePresetCallback,
            selectionBrush = backgroundBrush,
            callback = presetListCallback)
}

/**
 * A floating button that shows information about the currently playing [Preset]
 * and a play/pause button. When the parameter [showingPresetSelector] is true,
 * the button will animate its transformation into a preset selector. In this
 * state it will contain a [PresetList] to allow the user to choose a new preset.
 *
 * @param modifier The [Modifier] to use for the button / popup
 * @param sizes The [MediaControllerSizes] instance that describes the sizes
 *     of [MediaController]'s internal elements.
 * @param backgroundBrush A [Brush] to use as the background. This is passed
 *     as a separate parameter instead of allowing the caller to accomplish
 *     this through a [Modifier] so that the [Brush] can be applied across the
 *     whole parent size, and then clipped down to the size of the contents.
 * @param alignment The [BiasAlignment] to use for placement. This alignment should
 *     not be applied through the [modifier] parameter or it will be applied twice.
 * @param padding The [PaddingValues] to use for placement. This padding should not
 *     be applied through the [modifier] parameter or it will be applied twice.
 * @param activePresetCallback The [ActivePresetCallback] that will be used
 *     to determine the display and function of the active preset indicator
 * @param playButtonCallback The [PlayButtonCallback] that will be used
 *     to determine the display and function of the play/pause button
 * @param stopTimeProvider A method that will return the java.time.Instant at
 *     which playback will be automatically stopped when invoked. MediaController
 *     does not use this information to affect playback; the returned value is
 *     only used to display this information to the user.
 * @param onStopTimerClick The callback that will be invoked when
 *     the display of the stop timer is clicked
 * @param showingPresetSelector Whether or not the floating button should be
 *     expanded to show the preset selector
 * @param presetListCallback The [PresetListCallback] that will be used for user
 *     interactions with the [Preset]s displayed when [showingPresetSelector] is true
 * @param onCloseButtonClick The callback that will be invoked when
 *     [showingPresetSelector] is true and the user clicks the close button
 */
@Composable fun MediaController(
    modifier: Modifier = Modifier,
    sizes: MediaControllerSizes,
    backgroundBrush: Brush,
    contentColor: Color = MaterialTheme.colors.onPrimary,
    alignment: BiasAlignment,
    padding: PaddingValues,
    activePresetCallback: ActivePresetCallback,
    playButtonCallback: PlayButtonCallback,
    stopTimeProvider: () -> Instant?,
    onStopTimerClick: () -> Unit,
    showingPresetSelector: Boolean,
    presetListCallback: PresetListCallback,
    onCloseButtonClick: () -> Unit,
) = CompositionLocalProvider(LocalContentColor provides contentColor) {

    val isExpanded = remember { MutableTransitionState(showingPresetSelector) }
    isExpanded.targetState = showingPresetSelector

    val expandTransition = updateTransition(
        isExpanded, "MediaController expand transition")
    val expandTransitionProgress by expandTransition.animateFloat(
        transitionSpec = { tween(tweenDuration, 0, LinearOutSlowInEasing) },
        label = "MediaController expand transition progress",
        targetValueByState = { if (it) 1f else 0f })
    val transitionProgressProvider = remember {{ expandTransitionProgress }}
    val hasStopTime by remember { derivedStateOf {
        stopTimeProvider() != null
    }}

    ClippedBrushBox(
        modifier = modifier,
        brush = backgroundBrush,
        size = sizes.rememberCurrentSize(showingPresetSelector, hasStopTime),
        cornerRadius = 28.dp,
        padding = padding,
        alignment = alignment,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val titleHeight by expandTransition.animateDp(
                transitionSpec = { tween(tweenDuration, 0, LinearOutSlowInEasing) },
                label = "MediaController/preset selector title height transition",
            ) { expanded ->
                if (!expanded && sizes.orientation.isVertical)
                    sizes.collapsedSize(hasStopTime).height
                else sizes.minThickness
            }
            Box(Modifier.height(titleHeight)) {
                if (expandTransitionProgress > 0f)
                    PresetSelectorTitle(
                        sizes, onCloseButtonClick,
                        transitionProgressProvider)

                if (expandTransitionProgress < 1f)
                    MediaControllerCollapsedContent(
                        sizes, transitionProgressProvider,
                        activePresetCallback, playButtonCallback,
                        stopTimeProvider, onStopTimerClick)
            }
            MediaControllerPresetList(
                sizes, hasStopTime, backgroundBrush,
                transitionProgressProvider,
                activePresetCallback, presetListCallback)
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
    val activePresetName = remember { mutableStateOf<String?>(list.first().name) }
    val callback = remember { object: PresetListCallback {
        override fun getList() = list
        override fun onRenameClick(preset: Preset) {}
        override fun onOverwriteClick(preset: Preset) {}
        override fun onDeleteClick(preset: Preset) {}
        override fun onClick(preset: Preset) {
            activePresetName.value = preset.name
        }
    }}
    MediaController(
        sizes = MediaControllerSizes(
            activePresetLength = 200.dp - 56.dp,
            orientation = Orientation.Horizontal,
            presetSelectorSize = DpSize(0.dp, 0.dp)),
        backgroundBrush = Brush.horizontalGradient(
            listOf(MaterialTheme.colors.primaryVariant,
                   MaterialTheme.colors.secondaryVariant)),
        contentColor = MaterialTheme.colors.onPrimary,
        alignment = Alignment.BottomStart as BiasAlignment,
        padding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 8.dp),
        activePresetCallback = object : ActivePresetCallback {
            override fun getName() = activePresetName.value
            override fun getIsModified() = true
            override fun onClick() { expanded = true }
        }, playButtonCallback = object : PlayButtonCallback {
            override fun getIsPlaying() = playing
            override fun onClick() { playing = !playing }
            override fun getClickLabelResId(isPlaying: Boolean) = 0
            override fun onLongClick() {}
            override val longClickLabelResId = 0
        }, stopTimeProvider = { null },
        onStopTimerClick = {},
        showingPresetSelector = expanded,
        presetListCallback = callback,
        onCloseButtonClick = { expanded = false })
}