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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.cliffracertech.soundaura.ui.theme.SoundAuraTheme
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

const val tweenDuration = 250
const val springStiffness = 600f

/**
 * A collection of sizes that the composable [MediaController] uses to
 * determine its overall size. Because these sizes can be used for
 * horizontal and vertical orientations, the terms length and thickness
 * refer to the width and height, respectively, in horizontal orientation,
 * and vice versa for a vertical orientation.
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
 * @param stopTimeSize The [DpSize] of the auto stop time indicator. This
 *     value is described as a [DpSize] instead of a [Dp] length because the
 *     auto stop time indicator does not change its orientation depending on
 *     the orientation of the [MediaController].
 * @param presetSelectorSize The [DpSize] of the [MediaController] when its
 *     parameter showingPresetSelector is true. Like the auto stop time
 *     indicator, the preset selector does not change its orientation with
 *     the rest of the [MediaController].
 */
data class MediaControllerSizes(
    val orientation: Orientation,
    val minThickness: Dp = defaultMinThicknessDp.dp,
    val activePresetLength: Dp,
    val buttonLength: Dp = defaultButtonLengthDp.dp,
    val stopTimeSize: DpSize = DpSize(defaultStopTimeWidthDp.dp,
                                      defaultStopTimeHeightDp.dp),
    val presetSelectorSize: DpSize,
) {
    val dividerSize get() = dividerThicknessDp.dp
    val stopTimeLength get() = if (orientation.isHorizontal)
                                   stopTimeSize.width
                               else stopTimeSize.height

    val collapsedThickness = maxOf(minThickness,
        if (orientation.isHorizontal) stopTimeSize.height
        else                          stopTimeSize.width)

    val activePresetSize = DpSize(
        width = if (orientation.isVertical) collapsedThickness else activePresetLength,
        height = if (orientation.isHorizontal) collapsedThickness else activePresetLength)

    val buttonSize = DpSize(
        width = if (orientation.isVertical) collapsedThickness else buttonLength,
        height = if (orientation.isHorizontal) collapsedThickness else buttonLength)

    /** Return the size of a collapsed [MediaController] (i.e. when its
     * showingPresetSelector parameter is false) given whether or not the
     * auto stop time is being shown or not and the orientation. */
    fun collapsedSize(showingStopTime: Boolean): DpSize {
        val stopTimeLength = if (!showingStopTime) 0.dp
                             else dividerSize + stopTimeLength
        val length = activePresetLength + dividerSize + buttonLength + stopTimeLength
        return DpSize(if (orientation.isHorizontal) length else collapsedThickness,
                      if (orientation.isVertical) length else collapsedThickness)
    }

    /** Return a remembered current size of a [MediaController] instance given
     * whether or not the preset selector is being shown and whether an auto
     * stop time is being displayed. */
    @Composable fun rememberCurrentSize(
        showingPresetSelector: Boolean,
        showingStopTime: Boolean,
    ) = remember(showingPresetSelector, showingStopTime) {
        if (showingPresetSelector) presetSelectorSize
        else collapsedSize(showingStopTime)
    }

    val activePresetShape =
        if (orientation.isHorizontal)
            RoundedCornerShape(28.dp, 0.dp, 0.dp, 28.dp)
        else RoundedCornerShape(28.dp, 28.dp, 0.dp, 0.dp)

    fun playPauseButtonShape(showingStopTime: Boolean) =
        if (showingStopTime) RectangleShape
        else if (orientation.isHorizontal)
            RoundedCornerShape(0.dp, 28.dp, 28.dp, 0.dp)
        else RoundedCornerShape(0.dp, 0.dp, 28.dp, 28.dp)

    val stopTimeShape =
        if (orientation.isHorizontal)
            RoundedCornerShape(0.dp, 28.dp, 28.dp, 0.dp)
        else RoundedCornerShape(0.dp, 0.dp, 28.dp, 28.dp)

    companion object {
        const val defaultButtonLengthDp = 56
        const val defaultStopTimeWidthDp = 66
        const val defaultStopTimeHeightDp = 56
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

val Orientation.isHorizontal get() = this == Orientation.Horizontal
val Orientation.isVertical get() = this == Orientation.Vertical

@Composable private fun ActivePresetIndicator(
    sizes: MediaControllerSizes,
    activePresetNameProvider: () -> String?,
    activeIsModified: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val onClickLabel = stringResource(R.string.preset_button_click_label)
    val columnModifier = remember(modifier, sizes.orientation) {
        modifier.size(sizes.activePresetSize)
                .clip(sizes.activePresetShape)
                .clickable(true, onClickLabel, Role.Button, onClick)
                .then(if (sizes.orientation.isHorizontal)
                          Modifier.padding(start = 12.dp, end = 8.dp)
                    else Modifier.padding(top = 12.dp, bottom = 8.dp)
                                   .rotateClockwise())
    }
    Column(columnModifier, Arrangement.Center, Alignment.CenterHorizontally) {
        val style = MaterialTheme.typography.caption
        val activePresetName = activePresetNameProvider()
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
            AnimatedVisibility(activeIsModified) {
                Text(" *", style = style)
            }
        }
    }
}

@Composable private fun PlayPauseButton(
    modifier: Modifier = Modifier,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onPlayPauseLongClick: () -> Unit,
) = Box(
    contentAlignment = Alignment.Center,
    modifier = modifier
        .combinedClickable(
            onLongClickLabel = stringResource(
                R.string.play_pause_button_long_click_description),
            onLongClick = onPlayPauseLongClick,
            onClickLabel = stringResource(
                if (isPlaying) R.string.pause_button_description
                else           R.string.play_button_description),
            onClick = onPlayPauseClick),
) {
    PlayPauseIcon(
        isPlaying = isPlaying,
        contentDescription = stringResource(
            if (isPlaying) R.string.pause_button_description
            else           R.string.play_button_description))
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

@Composable private fun StopTimeDisplay(
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
    // becomes null, the StopTimeDisplay can fade out with the
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
    Text("for", style = style)
    Text(durationRemainingString ?: "", style = style)
}

@Composable private fun StopTimeDisplayWithDivider(
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

    val translationPercent = (1f - progress) / 2f
    val size = sizes.stopTimeSize
    val clickLabel = stringResource(R.string.stop_timer_click_label)

    LinearLayout(
        orientation = sizes.orientation,
        modifier = modifier
            .requiredSize(size)
            .clip(sizes.stopTimeShape)
            .clickable(true, clickLabel, Role.Button, onClick)
            .graphicsLayer {
                alpha = progress
                translationX = if (sizes.orientation.isVertical) 0f else
                                   translationPercent * size.width.toPx()
                translationY = if (sizes.orientation.isHorizontal) 0f else
                                   translationPercent * size.height.toPx()
            },
    ) { divider ->
        divider()
        StopTimeDisplay(stopTime, Modifier.fillMaxSize())
    }
}

/**
 * The content of a [MediaController] instance when it is collapsed
 * (i.e. not showing the preset selector.
 *
 * @param sizes The [MediaControllerSizes] instance that describes
 *     the sizes of [MediaController]'s internal elements.
 * @param transitionProgressProvider A method that returns the
 *     current progress of the [MediaController]'s show/hide
 *     preset selector transition when invoked
 * @param activePresetNameProvider A method that will return the active
 *     preset's name when invoked, if any
 * @param activePresetIsModified Whether or not the active preset is modified
 * @param onActivePresetClick The method that will be invoked when the
 *     active preset display to the left/top of the play/pause button
 *     is clicked
 * @param playing Whether or not media is playing, used to determine
 *     the icon displayed in the play/pause button
 * @param onPlayPauseClick The method that will be invoked when the
 *     play/pause button is clicked
 * @param onPlayPauseLongClick The callback that will be invoked when
 *     the play/pause button is long clicked
 * @param stopTime The [Instant] at which media will automatically
 *     stop playing, if any. This value is only used for informational
 *     display; playback is not affected by this value
 * @param onStopTimeClick The callback that will be invoked when
 *     the display of the stop time is clicked
 * @param showStopTime Whether or not the auto stop time should
 *     be shown if it is not null
 * @param modifier The [Modifier] to use for the composable
 */
@Composable fun MediaControllerCollapsedContent(
    sizes: MediaControllerSizes,
    transitionProgressProvider: () -> Float,
    activePresetNameProvider: () -> String?,
    activePresetIsModified: Boolean,
    onActivePresetClick: () -> Unit,
    playing: Boolean,
    onPlayPauseClick: () -> Unit,
    onPlayPauseLongClick: () -> Unit,
    stopTime: Instant?,
    onStopTimeClick: () -> Unit,
    showStopTime: Boolean,
    modifier: Modifier = Modifier,
) = LinearLayout(
    orientation = sizes.orientation,
    modifier = modifier.graphicsLayer { alpha = 1f - transitionProgressProvider() }
) { divider ->
    ActivePresetIndicator(sizes, activePresetNameProvider,
                          activePresetIsModified,
                          onClick = onActivePresetClick)
    divider()
    PlayPauseButton(
        modifier = Modifier
            .size(sizes.buttonSize)
            .clip(sizes.playPauseButtonShape(showStopTime)) ,
        playing, onPlayPauseClick, onPlayPauseLongClick)
    StopTimeDisplayWithDivider(
        showStopTime, sizes, stopTime, onClick = onStopTimeClick)
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
        modifier = Modifier.align(Alignment.Center),
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
 * @param activePresetNameProvider A function that returns the actively
 *     playing [Preset]'s name, or null if there isn't one, when invoked
 * @param activePresetIsModified Whether or not the active preset has unsaved changes
 * @param onActivePresetClick The callback that will be invoked when the active preset is clicked
 * @param playing The media play/pause state that the play/pause button should
 *     use to determine its icon, which will be the opposite of the current state
 * @param onPlayPauseClick The callback that will be invoked when the play/pause button is clicked
 * @param onPlayPauseLongClick The callback that will be invoked the the
 *     play/pause button is long clicked
 * @param stopTime The java.time.Instant at which playback will be automatically
 *     stopped. MediaController does not use this information to affect playback;
 *     the value of stopTime is only used to display this information to the user.
 * @param onStopTimeClick The callback that will be invoked when
 *     the display of the stop time is clicked
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
    activePresetNameProvider: () -> String?,
    activePresetIsModified: Boolean,
    onActivePresetClick: () -> Unit,
    playing: Boolean,
    onPlayPauseClick: () -> Unit,
    onPlayPauseLongClick: () -> Unit,
    stopTime: Instant?,
    onStopTimeClick: () -> Unit,
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

    ClippedBrushBox(
        modifier = modifier,
        brush = backgroundBrush,
        size = sizes.rememberCurrentSize(
            showingPresetSelector = showingPresetSelector,
            showingStopTime = stopTime != null),
        cornerRadius = 28.dp,
        padding = padding,
        alignment = alignment,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val showingStopTime = stopTime != null
            val titleHeight by expandTransition.animateDp(
                transitionSpec = { spring(stiffness = springStiffness) },
                label = "FloatingMediaController title height transition",
            ) { expanded ->
                if (!expanded && sizes.orientation.isVertical)
                    sizes.collapsedSize(showingStopTime).height
                else sizes.minThickness
            }

            Box(modifier.height(titleHeight)) {
                if (expandTransitionProgress > 0f)
                    PresetSelectorTitle(
                        sizes, { expandTransitionProgress }, onCloseButtonClick)
                if (expandTransitionProgress < 1f)
                    MediaControllerCollapsedContent(
                        sizes,
                        transitionProgressProvider = { expandTransitionProgress },
                        activePresetNameProvider, activePresetIsModified, onActivePresetClick,
                        playing, onPlayPauseClick, onPlayPauseLongClick,
                        stopTime, onStopTimeClick,
                        showStopTime = !expandTransition.targetState && showingStopTime)
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
                    activePresetNameProvider = activePresetNameProvider,
                    activePresetIsModified = activePresetIsModified,
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

    Surface(Modifier.size(400.dp, 600.dp), RectangleShape, Color.White) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            MediaController(
                sizes = MediaControllerSizes(
                    activePresetLength = 200.dp - 56.dp,
                    orientation = Orientation.Horizontal,
                    presetSelectorSize = DpSize(388.dp, 250.dp)),
                backgroundBrush = Brush.horizontalGradient(
                    listOf(MaterialTheme.colors.primaryVariant,
                           MaterialTheme.colors.secondaryVariant)),
                contentColor = MaterialTheme.colors.onPrimary,
                alignment = Alignment.BottomStart as BiasAlignment,
                padding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 8.dp),
                activePresetNameProvider = activePresetName::value::get,
                activePresetIsModified = true,
                onActivePresetClick = { expanded = true },
                playing = playing,
                onPlayPauseClick = { playing = !playing },
                onPlayPauseLongClick = {},
                stopTime = null,
                onStopTimeClick = {},
                showingPresetSelector = expanded,
                presetListCallback = callback,
                onCloseButtonClick = { expanded = false })
        }
    }
}