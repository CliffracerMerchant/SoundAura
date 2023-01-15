/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.cliffracertech.soundaura.ui.theme.SoundAuraTheme
import com.github.skgmn.composetooltip.AnchorEdge
import com.github.skgmn.composetooltip.Tooltip
import com.github.skgmn.composetooltip.rememberTooltipStyle
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

internal const val tweenDuration = 250
internal const val springStiffness = 600f

val Orientation.isHorizontal get() = this == Orientation.Horizontal
val Orientation.isVertical get() = this == Orientation.Vertical

/**
 * A collection of sizes that a [MediaController] uses to determine its
 * overall size. Because these sizes can be used for horizontal and
 * vertical orientations, the terms length and thickness refer to the
 * width and height, respectively, in horizontal orientation, and vice
 * versa for a vertical orientation.
 *
 * The method [collapsedSize] can be used to obtain the size of a
 * [MediaController] given the provided sizes and whether or not the
 * [MediaController] is showing an auto stop time.
 *
 * @param orientation The [Orientation] of the [MediaController]
 * @param minThickness The minimum thickness of the [MediaController] in
 *     its collapsed state. The actual thickness will be the greater of
 *     this value and the auto stop time indicator's height (in horizontal
 *     orientation or width (in vertical orientation).
 * @param activePresetLength The length of the active preset indicator
 *                           in the [MediaController]'s collapsed state
 * @param buttonLength The length of the button. The button's other
 *     dimension will match the derived thickness of the [MediaController].
 * @param stopTimerSize The [DpSize] of the stop timer indicator. This
 *     value is described as a [DpSize] instead of a [Dp] length because the
 *     auto stop time indicator does not change its orientation depending on
 *     the orientation of the [MediaController].
 * @param presetSelectorSize The [DpSize] of the [MediaController] when
 *     its parameter showingPresetSelector is true. Like the stop timer
 *     indicator, the preset selector does not change its orientation
 *     with the rest of the [MediaController].
 */
data class MediaControllerSizes(
    val orientation: Orientation,
    val minThickness: Dp = defaultMinThicknessDp.dp,
    val activePresetLength: Dp,
    val buttonLength: Dp = defaultButtonLengthDp.dp,
    val stopTimerSize: DpSize = DpSize(
        width = defaultStopTimerWidthDp.dp,
        height = defaultStopTimerHeightDp.dp),
    val presetSelectorSize: DpSize,
) {
    val dividerSize get() = dividerThicknessDp.dp
    val stopTimerLength get() =
        if (orientation.isVertical) stopTimerSize.height
        else                        stopTimerSize.width

    val collapsedThickness = maxOf(minThickness,
        if (orientation.isVertical) stopTimerSize.width
        else                        stopTimerSize.height)

    val activePresetSize = DpSize(
        width = if (orientation.isVertical) collapsedThickness else activePresetLength,
        height = if (orientation.isVertical) activePresetLength else collapsedThickness)

    val buttonSize = DpSize(
        width = if (orientation.isVertical) collapsedThickness else buttonLength,
        height = if (orientation.isVertical) buttonLength else collapsedThickness)

    /** Return the size of a collapsed [MediaController] (i.e. when its
     * showingPresetSelector parameter is false) given whether or not the
     * auto stop time is being shown and the orientation. */
    fun collapsedSize(showingStopTimer: Boolean): DpSize {
        val stopTimerLength = if (!showingStopTimer) 0.dp
        else dividerSize + stopTimerLength
        val length = activePresetLength + dividerSize +
                buttonLength + stopTimerLength
        return DpSize(
            width = if (orientation.isVertical) collapsedThickness else length,
            height = if (orientation.isVertical) length else collapsedThickness)
    }

    /** Return a remembered current size of a [MediaController] instance given
     * whether or not the preset selector is being shown and whether an auto
     * stop timer is being displayed. */
    @Composable fun rememberCurrentSize(
        showingPresetSelector: Boolean,
        showingStopTimer: Boolean,
    ) = remember(showingPresetSelector, showingStopTimer) {
        if (showingPresetSelector) presetSelectorSize
        else                       collapsedSize(showingStopTimer)
    }

    val shape = RoundedCornerShape(28.dp)

    val activePresetShape =
        if (orientation.isVertical) shape.topShape()
        else                        shape.startShape()

    val stopTimerShape =
        if (orientation.isVertical) shape.bottomShape()
        else                        shape.endShape()

    fun playPauseButtonShape(showingStopTimer: Boolean) =
        if (showingStopTimer) RectangleShape
        else                  stopTimerShape

    companion object {
        const val defaultButtonLengthDp = 56
        const val defaultStopTimerWidthDp = 72
        const val defaultStopTimerHeightDp = 56
        const val dividerThicknessDp = 1.5f
        const val defaultMinThicknessDp = 56
    }
}

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

/**
 * A collection of callbacks related to the display of an active preset.
 *
 * @param nameProvider A function that returns the actively playing
 *     [Preset]'s name, or null if there isn't one, when invoked
 * @param isModifiedProvider A function that returns whether or
 *     not the active preset has unsaved changes when invoked
 * @param onClick The callback that will be invoked when the display
 *     of the active preset is clicked
 */
data class ActivePresetCallback(
    val nameProvider: () -> String?,
    val isModifiedProvider: () -> Boolean,
    val onClick: () -> Unit)

@Composable private fun ActivePresetIndicator(
    sizes: MediaControllerSizes,
    callback: ActivePresetCallback,
    modifier: Modifier = Modifier,
) {
    val onClickLabel = stringResource(R.string.preset_button_click_label)
    val columnModifier = remember(modifier, sizes.orientation) {
        modifier.size(sizes.activePresetSize)
                .clip(sizes.activePresetShape)
                .clickable(true, onClickLabel, Role.Button, callback.onClick)
                .then(if (sizes.orientation.isHorizontal)
                          Modifier.padding(start = 12.dp, end = 8.dp)
                    else Modifier.padding(top = 12.dp, bottom = 8.dp)
                                   .rotateClockwise())
    }
    Column(columnModifier, Arrangement.Center, Alignment.CenterHorizontally) {
        val style = MaterialTheme.typography.caption
        val activePresetName = callback.nameProvider()
        Text(text = stringResource(
                 if (activePresetName == null) R.string.playing
                 else R.string.playing_preset_description),
             maxLines = 1, style = style, softWrap = false)
        Row {
            MarqueeText(
                text = activePresetName ?:
                    stringResource(R.string.unsaved_preset_description),
                maxWidth = sizes.activePresetLength,
                modifier = Modifier.weight(1f, false),
                style = style)
            AnimatedVisibility(callback.isModifiedProvider()) {
                Text(" *", style = style)
            }
        }
    }
}


/**
 * A collection of callbacks replayed to the display and function
 * of a combination play/pause button with a long click action.
 *
 * @param isPlayingProvider A function that returns the media play/pause
 *     state that the play/pause button should use to determine its icon,
 *     which will be the opposite of the current state
 * @param onClick The callback that will be invoked when the pla/pause button is clicked
 * @param onLongClick The callback that will be invoked when the pla/pause button is long clicked
 * @param longClickHintProvider A function that returns a nullable [String]
 *     message that should be displayed in a popup tooltip when the button
 *     is clicked. If null is returned, no tooltip will be shown. This long
 *     click hint is provided due to the low discoverability nature of long
 *     click actions.
 */
data class PlayPauseButtonCallback(
    val isPlayingProvider: () -> Boolean,
    val onClick: () -> Unit,
    val onLongClick: () -> Unit,
    val longClickHintProvider: () -> String?)

@Composable private fun PlayPauseButton(
    callback: PlayPauseButtonCallback,
    modifier: Modifier = Modifier,
) {
    val isPlaying = callback.isPlayingProvider()
    var longClickHint by remember { mutableStateOf<String?>(null) }

    Box(contentAlignment = Alignment.Center,
        modifier = modifier
            .combinedClickable(
                onLongClickLabel = stringResource(
                    R.string.play_pause_button_long_click_description),
                onLongClick = callback.onLongClick,
                onClickLabel = stringResource(
                    if (isPlaying) R.string.pause_button_description
                    else           R.string.play_button_description),
                onClick = {
                    callback.onClick()
                    longClickHint = callback.longClickHintProvider()
                }),
    ) {
        PlayPauseIcon(
            isPlaying = isPlaying,
            contentDescription = stringResource(
                if (isPlaying) R.string.pause_button_description
                else           R.string.play_button_description))
        longClickHint?.let {
            Tooltip(
                anchorEdge = AnchorEdge.Top,
                tooltipStyle = rememberTooltipStyle(
                    cornerRadius = 16.dp,
                    tipHeight = 12.dp),
                content = { Text(it) })
        }
    }
}

fun Duration.toHMMSSstring(): String {
    val hours = toHours()
    val minutes = toMinutesPart()
    return when {
        hours == 0L && minutes == 0 ->
            toSecondsPart().toString()
        hours == 0L ->
            "%2d:%02d".format(minutes, toSecondsPart())
        else ->
            "%d:%02d:%02d".format(hours, minutes, toSecondsPart())
    }
}

@Composable private fun StopTimerDisplay(
    stopTime: Instant?,
    modifier: Modifier = Modifier,
) = Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
) {
    var durationRemaining = remember(stopTime) {
        stopTime?.let { Duration.between(Instant.now(), it) }
    }
    // durationRemainingString is used so that when the stopTime
    // becomes null, the StopTimerDisplay can fade out with the
    // last non-null value of stopTime
    var durationRemainingString by remember {
        mutableStateOf(durationRemaining?.toHMMSSstring())
    }
    LaunchedEffect(stopTime) {
        while (durationRemaining != null) {
            delay(1000)
            durationRemaining?.minusSeconds(1)?.let {
                durationRemaining = it
                durationRemainingString = it.toHMMSSstring()
            }
        }
    }
    val style = MaterialTheme.typography.caption
    Row(Modifier, Arrangement.spacedBy((-1).dp), Alignment.CenterVertically) {
        Icon(Icons.Default.Stop, null, Modifier.size(16.dp))
        Text(stringResource(R.string.stop_timer_text), style = style)
    }
    Text(durationRemainingString ?: "", style = style)
}

@Composable private fun HideableStopTimerDisplay(
    showing: Boolean,
    sizes: MediaControllerSizes,
    stopTime: Instant?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val progress by animateFloatAsState(
        targetValue = if (showing) 1f else 0f,
        animationSpec = spring(stiffness = springStiffness),
        label = "Auto stop time appearance transition")
    if (progress == 0f) return

    val translationPercent = (1f - progress) / -2f
    val size = sizes.stopTimerSize
    val clickLabel = stringResource(R.string.stop_timer_click_label)

    LinearLayout(
        orientation = sizes.orientation,
        modifier = modifier
            .requiredSize(size)
            .graphicsLayer {
                alpha = progress
                translationX = if (sizes.orientation.isVertical) 0f else
                                   translationPercent * size.width.toPx()
                translationY = if (sizes.orientation.isHorizontal) 0f else
                                   translationPercent * size.height.toPx()
            }.clip(sizes.stopTimerShape)
            .clickable(true, clickLabel, Role.Button, onClick),
    ) {
        Divider(sizes.orientation, sizeFraction = 0.8f)
        StopTimerDisplay(stopTime, Modifier.fillMaxSize())
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
 * @param playPauseButtonCallback The [PlayPauseButtonCallback] that will be
 *     used to determine the display and function of the play/pause button
 * @param stopTime The [Instant] at which media will automatically
 *     stop playing, if any. This value is only used for informational
 *     display; playback is not affected by this value
 * @param showStopTimer Whether or not the stop timer should be shown
 *     if the stop time is not null
 * @param onStopTimerClick The callback that will be invoked when
 *     the display of the stop time is clicked
 * @param modifier The [Modifier] to use for the composable
 */
@Composable fun MediaControllerCollapsedContent(
    sizes: MediaControllerSizes,
    transitionProgressProvider: () -> Float,
    activePresetCallback: ActivePresetCallback,
    playPauseButtonCallback: PlayPauseButtonCallback,
    stopTime: Instant?,
    showStopTimer: Boolean,
    onStopTimerClick: () -> Unit,
    modifier: Modifier = Modifier,
) = LinearLayout(
    orientation = sizes.orientation,
    modifier = modifier.graphicsLayer { alpha = 1f - transitionProgressProvider() }
) {
    ActivePresetIndicator(sizes, activePresetCallback)
    Divider(sizes.orientation, sizeFraction = 0.8f)
    PlayPauseButton(
        callback = playPauseButtonCallback,
        modifier = Modifier
            .size(sizes.buttonSize)
            .clip(sizes.playPauseButtonShape(showStopTimer)))
    HideableStopTimerDisplay(
        showing = showStopTimer,
        sizes, stopTime,
        onClick = onStopTimerClick)
}

/**
 * The content of a [MediaController] instance when it is expanded
 * (i.e. showing the preset selector.
 *
 * @param sizes The [MediaControllerSizes] instance that describes
 *     the sizes of [MediaController]'s internal elements.
 * @param transitionProgressProvider A method that returns the
 *     current progress of the [MediaController]'s show/hide
 *     preset selector transition when invoked
 * @param onCloseButtonClick The method that will be invoked when
 *     the close button of the preset selector title is clicked
 * @param modifier The [Modifier] to use for the composable
 */
@Composable fun PresetSelectorTitle(
    sizes: MediaControllerSizes,
    transitionProgressProvider: () -> Float,
    onCloseButtonClick: () -> Unit,
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
 * @param playPauseButtonCallback The [PlayPauseButtonCallback] that will be
 *     used to determine the display and function of the play/pause button
 * @param stopTime The java.time.Instant at which playback will be automatically
 *     stopped. MediaController does not use this information to affect playback;
 *     the value of stopTime is only used to display this information to the user.
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
    contentColor: Color,
    alignment: BiasAlignment,
    padding: PaddingValues,
    activePresetCallback: ActivePresetCallback,
    playPauseButtonCallback: PlayPauseButtonCallback,
    stopTime: Instant?,
    onStopTimerClick: () -> Unit,
    showingPresetSelector: Boolean,
    presetListCallback: PresetListCallback,
    onCloseButtonClick: () -> Unit,
) = CompositionLocalProvider(LocalContentColor provides contentColor) {
    val isExpanded = remember { MutableTransitionState(showingPresetSelector) }
    isExpanded.targetState = showingPresetSelector

    val expandTransition = updateTransition(
        isExpanded, "FloatingMediaController transition")
    val expandTransitionProgress by expandTransition.animateFloat(
        transitionSpec = { spring(stiffness = springStiffness) },
        label = "FloatingMediaController expand transition progress",
        targetValueByState = { if (it) 1f else 0f })
    val transitionProgressProvider = remember {{ expandTransitionProgress }}

    ClippedBrushBox(
        modifier = modifier,
        brush = backgroundBrush,
        size = sizes.rememberCurrentSize(
            showingPresetSelector = showingPresetSelector,
            showingStopTimer = stopTime != null),
        cornerRadius = 28.dp,
        padding = padding,
        alignment = alignment,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val titleHeight by expandTransition.animateDp(
                transitionSpec = { spring(stiffness = springStiffness) },
                label = "FloatingMediaController title height transition",
            ) { expanded ->
                if (!expanded && sizes.orientation.isVertical)
                    sizes.collapsedSize(stopTime != null).height
                else sizes.minThickness
            }

            Box(modifier.height(titleHeight)) {
                if (expandTransitionProgress > 0f)
                    PresetSelectorTitle(sizes, transitionProgressProvider, onCloseButtonClick)

                val showStopTimer = !expandTransition.targetState && stopTime != null
                if (expandTransitionProgress < 1f)
                    MediaControllerCollapsedContent(
                        sizes, transitionProgressProvider,
                        activePresetCallback, playPauseButtonCallback,
                        stopTime, showStopTimer, onStopTimerClick)
            }

            val listPadding = 8.dp
            val presetListSize = remember(sizes) {
                val expandedTitleHeight = sizes.minThickness
                val expandedWidth = sizes.presetSelectorSize.width
                val expandedHeight = sizes.presetSelectorSize.height
                DpSize(expandedWidth - listPadding * 2,
                       expandedHeight - expandedTitleHeight - listPadding)
            }
            val minScaleX = remember(sizes, stopTime == null) {
                val collapsedWidth = sizes.collapsedSize(stopTime != null).width
                (collapsedWidth - listPadding * 2) / presetListSize.width
            }
            if (expandTransitionProgress > 0f)
                PresetList(
                    modifier = Modifier
                        .requiredSize(presetListSize)
                        .graphicsLayer {
                            alpha = expandTransitionProgress
                            scaleY = expandTransitionProgress
                            scaleX = minScaleX + (1f - minScaleX) * expandTransitionProgress
                        }.background(MaterialTheme.colors.surface,
                                     MaterialTheme.shapes.large),
                    contentPadding = PaddingValues(bottom = 64.dp),
                    activePresetCallback = activePresetCallback,
                    selectionBrush = backgroundBrush,
                    callback = presetListCallback)
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
        override val listProvider = { list }
        override val renameTargetProvider = { null }
        override val proposedNameProvider = { null }
        override val renameErrorMessageProvider = { null }
        override fun onProposedNameChange(newName: String) {}
        override fun onRenameStart(preset: Preset) {}
        override fun onRenameCancel() {}
        override fun onRenameConfirm() {}
        override fun onOverwriteConfirm(preset: Preset) {}
        override fun onDeleteConfirm(preset: Preset) {}
        override fun onPresetClick(preset: Preset) {
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
        activePresetCallback = ActivePresetCallback(
            nameProvider = activePresetName::value::get,
            isModifiedProvider = { true },
            onClick = { expanded = true }),
        playPauseButtonCallback = PlayPauseButtonCallback(
            isPlayingProvider = { playing },
            onClick = { playing = !playing },
            onLongClick = {},
            longClickHintProvider = { null }),
        stopTime = null,
        onStopTimerClick = {},
        showingPresetSelector = expanded,
        presetListCallback = callback,
        onCloseButtonClick = { expanded = false })
}