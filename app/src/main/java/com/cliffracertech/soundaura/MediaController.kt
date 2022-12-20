/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import com.cliffracertech.soundaura.ui.theme.SoundAuraTheme
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

const val tweenDuration = 250

/**
 * A collection of sizes that the composable [MediaController] uses to
 * determine its overall size. The method [collapsedWidth] can be used to
 * obtain the width of a [MediaController] given the provided sizes and whether
 * or not the [MediaController] is showing an auto stop time. The method
 * [rememberCurrentSize]
 *
 * @param collapsedHeight The height of the [MediaController] in its collapsed
 *     state. This value will also be used as the width of the play/pause/stop
 *     button.
 * @param activePresetWidth The width of the active preset indicator
 *     in the [MediaController]'s collapsed state. Because the active preset
 *     indicator is given a weight of 1 within the layout, the value passed
 *     here is not guaranteed if the width of the layout would exceed
 *     [maxTotalCollapsedWidth].
 * @param autoStopTimeWidth The width of the auto stop time indicator
 * @param
 */
data class MediaControllerSizes(
    val collapsedHeight: Dp = defaultHeightDp.dp,
    val activePresetWidth: Dp,
    val autoStopTimeWidth: Dp = defaultAutoStopTimeWidthDp.dp,
    val maxCollapsedWidth: Dp = Dp.Infinity,
    val presetSelectorSize: DpSize,
) {
    val playPauseButtonSize get() = collapsedHeight

    /** Return the size of a collapsed [MediaController] (i.e. when its
     * showingPresetSelector parameter is false) given whether or not the
     * auto stop time is being shown or not. */
    fun collapsedWidth(showingAutoStopTime: Boolean): Dp {
        val buttonWidth = collapsedHeight
        val stopTimeActualWidth = if (!showingAutoStopTime) 0.dp
                                  else autoStopTimeWidth
        return (activePresetWidth + buttonWidth + stopTimeActualWidth)
                .coerceAtMost(maxCollapsedWidth)
    }

    /** Return the [DpSize] that the active preset indicator should match. This
     * value's width might be different than the [activePresetWidth] parameter
     * due to the [maxCollapsedWidth] restriction. */
    fun activePresetSize(showingAutoStopTime: Boolean): DpSize {
        val collapsedWidth = collapsedWidth(showingAutoStopTime)
        val stopTimeActualWidth = if (!showingAutoStopTime) 0.dp
                                  else autoStopTimeWidth
        val width = collapsedWidth - playPauseButtonSize - stopTimeActualWidth
        return DpSize(width, collapsedHeight)
    }

    /** Return the [DpSize] that the auto stop time indicator should match. */
    fun autoStopTimeSize() = DpSize(autoStopTimeWidth, collapsedHeight)

    /** Return a remembered current size of a [MediaController] instance given
     * whether or not the preset selector is being shown and whether an auto
     * stop time is being displayed. */
    @Composable fun rememberCurrentSize(
        showingPresetSelector: Boolean,
        showingAutoStopTime: Boolean,
    ) = remember(showingPresetSelector, showingAutoStopTime) {
        if (showingPresetSelector)
            presetSelectorSize
        else DpSize(collapsedWidth(showingAutoStopTime), collapsedHeight)
    }

    companion object {
        const val defaultHeightDp = 56
        const val defaultAutoStopTimeWidthDp = 66
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
    modifier: Modifier = Modifier,
    orientation: Orientation,
    width: Dp,
    activePresetNameProvider: () -> String?,
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
        val activePresetName = activePresetNameProvider()
        Text(text = stringResource(
                 if (activePresetName == null) R.string.playing
                 else R.string.playing_preset_description),
             maxLines = 1, style = style, softWrap = false)
        Row {
            MarqueeText(
                text = activePresetName ?:
                    stringResource(R.string.unsaved_preset_description),
                maxWidth = width,
                modifier = Modifier.weight(1f, false),
                style = style)
            if (activeIsModified)
                Text(" *", maxLines = 1, softWrap = false, style = style)
        }
    }
}

@Composable private fun PlayPauseCloseButton(
    modifier: Modifier = Modifier,
    showClose: Boolean,
    exitingClose: Boolean,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onPlayPauseLongClick: () -> Unit,
    onCloseButtonClick: () -> Unit,
) = Box(
    contentAlignment = Alignment.Center,
    modifier = modifier.clip(CircleShape).combinedClickable(
        onLongClickLabel = stringResource(
            R.string.play_pause_button_long_click_description),
        onLongClick = {
            if (!showClose) onPlayPauseLongClick()
        }, onClickLabel = stringResource(when {
            showClose -> R.string.close_preset_selector_description
            isPlaying -> R.string.pause_button_description
            else ->      R.string.play_button_description
        }), onClick = {
            if (showClose) onCloseButtonClick()
            else           onPlayPauseClick()
        }),
) {
    PlayPauseCloseIcon(
        showClose = showClose,
        isPlaying = isPlaying,
        exitingClose = exitingClose,
        contentDescriptionProvider = { showClose, isPlaying ->
            stringResource(when {
                showClose -> R.string.close_preset_selector_description
                isPlaying -> R.string.pause_button_description
                else ->      R.string.play_button_description
            })
        })
}

fun Duration.toHMMSSstring() = String.format(
    "%2d:%02d:%02d", toHoursPart(), toMinutesPart(), toSecondsPart())

@Composable fun AutoStopTimeDisplay(
    autoStopTime: Instant?,
    modifier: Modifier = Modifier,
) = Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
) {
    var durationRemaining by remember(autoStopTime) {
        mutableStateOf(autoStopTime?.let {
            Duration.between(Instant.now(), it)
        })
    }
    var durationRemainingString by remember {
        mutableStateOf(durationRemaining?.toHMMSSstring())
    }
    LaunchedEffect(autoStopTime) {
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

/**
 * Show either the active [Preset] information and the play/pause button,
 * or the title and close button of the preset selector.
 *
 * @param modifier The [Modifier] to use for the composable
 * @param orientation The [Orientation] value that determines whether the
 *     composable will be shown horizontally or vertically
 * @param transition The [Transition]`<Boolean>` whose state indicates
 *     the current showing / not showing preset selector state
 * @param transitionProgressProvider A method that returns the current
 *     progress of the transition when invoked
 * @param playing Whether or not media is playing, used to determine the
 *     icon displayed in the play/pause button
 * @param autoStopTime The [Instant] at which media will automatically
 *     stop playing, if any. This value is only used for informational
 *     display; playback is not affected by this value
 * @param onActivePresetClick The method that will be invoked when the
 *     active preset display to the left/top of the play/pause button
 *     is clicked
 * @param onPlayPauseClick The method that will be invoked when the
 *     play/pause button is clicked
 * @param onCloseButtonClick The method that will be invoked when the
 *     close button of the preset selector title is clicked
 * @param activePresetNameProvider A method that will return the active
 *     preset's name when invoked, if any
 * @param activePresetIsModified Whether or not the active preset is modified
 */
@Composable private fun MediaControllerAndPresetSelectorTitle(
    modifier: Modifier = Modifier,
    orientation: Orientation,
    transition: Transition<Boolean>,
    transitionProgressProvider: () -> Float,
    sizes: MediaControllerSizes,
    playing: Boolean,
    autoStopTime: Instant?,
    onActivePresetClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onPlayPauseLongClick: () -> Unit,
    onCloseButtonClick: () -> Unit,
    activePresetNameProvider: () -> String?,
    activePresetIsModified: Boolean,
) = Box(modifier = modifier.fillMaxWidth()) {
    val fullyCollapsed = !transition.currentState && !transition.targetState
    val fullyExpanded = transition.currentState && transition.targetState

    if (!fullyCollapsed)
        Text(stringResource(R.string.preset_selector_title),
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer { alpha = transitionProgressProvider() },
            maxLines = 1, softWrap = false,
            style = MaterialTheme.typography.h6)

    val stopTimeAppearanceTransition = updateTransition(
        targetState = !transition.targetState && autoStopTime != null,
        label = "AUto stop time appearance transition")
    val showingAutoStopTime = stopTimeAppearanceTransition.targetState
    val autoStopTimeWidth = if (!showingAutoStopTime) 0.dp
                            else sizes.autoStopTimeWidth
    val density = LocalDensity.current

    if (!fullyExpanded) {
        val size = sizes.activePresetSize(showingAutoStopTime)
        Row(modifier = Modifier
            .align(Alignment.CenterStart)
            .size(size)
            .graphicsLayer { alpha = 1f - transitionProgressProvider() },
        ) {
            ActivePresetIndicator(
                modifier = Modifier.weight(1f),
                orientation = Orientation.Horizontal,
                width = size.width - (1.5).dp,
                activePresetNameProvider = activePresetNameProvider,
                activeIsModified = activePresetIsModified,
                onClick = onActivePresetClick)
            VerticalDivider(heightFraction = 0.8f)
        }
    }

    val buttonXOffset by animateFloatAsState(
        animationSpec = tween(tweenDuration),
        targetValue = when {
            transition.targetState ->
                // This extra x offset when the view is expanded makes the close
                // button align with the more options buttons for each listed preset
                with(density) { 4.dp.toPx() }
            stopTimeAppearanceTransition.targetState ->
                with(density) { autoStopTimeWidth.toPx() }
            else -> 0f
        }, label = "Preset selector close button x offset animation")

    PlayPauseCloseButton(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .size(sizes.collapsedHeight)
            .graphicsLayer { translationX = -buttonXOffset },
        showClose = transition.targetState,
        exitingClose = !transition.targetState && transition.currentState,
        playing, onPlayPauseClick, onPlayPauseLongClick, onCloseButtonClick)

    val stopTimeAlpha by stopTimeAppearanceTransition.animateFloat(
        transitionSpec = { tween(tweenDuration) },
        targetValueByState = { if (it) 1f else 0f },
        label = "Auto stop time fade in/out animation")
    if (stopTimeAlpha > 0f) {
        Row(modifier = Modifier
                .align(Alignment.BottomEnd)
                .requiredSize(sizes.autoStopTimeSize())
                .graphicsLayer { alpha = stopTimeAlpha },
            verticalAlignment = Alignment.CenterVertically
        ) {
            VerticalDivider(heightFraction = 0.8f)
            AutoStopTimeDisplay(autoStopTime,
                Modifier.weight(1f).padding(end = 6.dp))
        }
    }
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
 * @param showingPresetSelector Whether or not the floating button should be
 *     expanded to show the preset selector
 * @param playing The media play/pause state that the play/pause button should
 *     use to determine its icon, which will be the opposite of the current state
 * @param autoStopTime The java.time.Instant at which playback will be automatically
 *     stopped. MediaController does not use this information to affect playback; the
 *     value of autoStopTime is only used to display this information to the user.
 * @param onPlayPauseClick The callback that will be invoked when the play/pause button is clicked
 * @param onPlayPauseLongClick The callback that will be invoked the the
 *     play/pause button is long clicked
 * @param activePresetNameProvider A function that returns the actively
 *     playing [Preset]'s name, or null if there isn't one, when invoked
 * @param activePresetIsModified Whether or not the active preset has unsaved changes
 * @param onActivePresetClick The callback that will be invoked when the active preset is clicked
 * @param presetListCallback The [PresetListCallback] that will be used for user
 *     interactions with the [Preset]s displayed when [showingPresetSelector] is true
 * @param onCloseButtonClick The callback that will be invoked when
 *     [showingPresetSelector] is true and the user clicks the close button
 */
@Composable fun MediaController(
    modifier: Modifier = Modifier,
    orientation: Orientation,
    sizes: MediaControllerSizes,
    backgroundBrush: Brush,
    contentColor: Color,
    alignment: BiasAlignment,
    padding: PaddingValues,
    playing: Boolean,
    autoStopTime: Instant?,
    onPlayPauseClick: () -> Unit,
    onPlayPauseLongClick: () -> Unit,
    activePresetNameProvider: () -> String?,
    activePresetIsModified: Boolean,
    onActivePresetClick: () -> Unit,
    showingPresetSelector: Boolean,
    presetListCallback: PresetListCallback,
    onCloseButtonClick: () -> Unit,
) = CompositionLocalProvider(LocalContentColor provides contentColor) {
    val isExpanded = remember { MutableTransitionState(showingPresetSelector) }
    isExpanded.targetState = showingPresetSelector

    val expandTransition = updateTransition(
        isExpanded, "FloatingMediaController transition")
    val expandTransitionProgress by expandTransition.animateFloat(
        transitionSpec = { tween(tweenDuration) },
        label = "FloatingMediaController expand transition progress",
        targetValueByState = { if (it) 1f else 0f })

    ClippedBrushBox(
        modifier = modifier,
        brush = backgroundBrush,
        size = sizes.rememberCurrentSize(
            showingPresetSelector = showingPresetSelector,
            showingAutoStopTime = autoStopTime != null),
        cornerRadius = 28.dp,
        padding = padding,
        alignment = alignment,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val showingAutoStopTime = autoStopTime != null
            val titleHeight by expandTransition.animateDp(
                transitionSpec = { tween(tweenDuration) },
                label = "FloatingMediaController title height transition",
            ) { expanded ->
                if (!expanded && orientation.isVertical)
                    sizes.collapsedWidth(showingAutoStopTime)
                else sizes.collapsedHeight
            }
            MediaControllerAndPresetSelectorTitle(
                modifier = Modifier.height(titleHeight),
                orientation = orientation,
                transition = expandTransition,
                transitionProgressProvider = { expandTransitionProgress },
                sizes = sizes,
                playing = playing,
                autoStopTime = autoStopTime,
                onActivePresetClick = onActivePresetClick,
                onPlayPauseClick = onPlayPauseClick,
                onPlayPauseLongClick = onPlayPauseLongClick,
                onCloseButtonClick = onCloseButtonClick,
                activePresetNameProvider = activePresetNameProvider,
                activePresetIsModified = activePresetIsModified)

            val listPadding = 8.dp
            val presetListSize = remember(sizes) {
                val expandedTitleHeight = sizes.collapsedHeight
                DpSize(sizes.presetSelectorSize.width - listPadding * 2,
                       sizes.presetSelectorSize.height - expandedTitleHeight - listPadding)
            }
            val minScaleX = remember(sizes, autoStopTime == null) {
                val collapsedWidth = sizes.collapsedWidth(autoStopTime != null)
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
                orientation = Orientation.Horizontal,
                backgroundBrush = Brush.horizontalGradient(
                    listOf(MaterialTheme.colors.primaryVariant,
                           MaterialTheme.colors.secondaryVariant)),
                contentColor = MaterialTheme.colors.onPrimary,
                sizes = MediaControllerSizes(
                    activePresetWidth = 200.dp - 56.dp,
                    presetSelectorSize = DpSize(388.dp, 250.dp)),
                alignment = Alignment.BottomStart as BiasAlignment,
                padding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 8.dp),
                showingPresetSelector = expanded,
                playing = playing,
                autoStopTime = null,
                onPlayPauseClick = { playing = !playing },
                onPlayPauseLongClick = {},
                activePresetNameProvider = activePresetName::value::get,
                activePresetIsModified = true,
                onActivePresetClick = { expanded = true },
                presetListCallback = callback,
                onCloseButtonClick = { expanded = false })
        }
    }
}