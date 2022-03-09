/* This file is part of SoundAura, which is released under the terms of the Apache
 * License 2.0. See license.md in the project's root directory to see the full license.
 *
 * The original Slider AOSP class has been modified in the
 * following ways to make the GradientSlider class:
 * - The SliderRange class has been removed
 * - The steps/ticks functionality has been removed
 * - The SliderColors class has been modified to support gradients for
 *   the slider track and thumb, and the tick colors have been removed
 */
package com.cliffracertech.soundaura

/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.TweenSpec
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.abs
import com.google.android.material.math.MathUtils.lerp

/**
 * <a href="https://material.io/components/sliders" class="external" target="_blank">Material Design slider</a>.
 *
 * Sliders allow users to make selections from a range of values.
 *
 * Sliders reflect a range of values along a bar, from which users may select a single value.
 * They are ideal for adjusting settings such as volume, brightness, or applying image filters.
 *
 * ![Sliders image](https://developer.android.com/images/reference/androidx/compose/material/sliders.png)
 *
 * Use continuous sliders to allow users to make meaningful selections that don’t
 * require a specific value: @sample androidx.compose.material.samples.SliderSample
 *
 * You can allow the user to choose only between predefined set of values by specifying the amount
 * of steps between min and max values: @sample androidx.compose.material.samples.StepsSliderSample
 *
 * GradientSlider acts as the standard Compose Slider, except that it adds support for gradient
 * colors to the slider's thumb and track, and it removes the ticks/steps functionality.
 *
 * @param value current value of the GradientSlider. If outside of [valueRange] provided,
 * value will be coerced to this range.
 * @param onValueChange lambda in which value should be updated
 * @param modifier modifiers for the Slider layout
 * @param enabled whether or not component is enabled and can we interacted with or not
 * @param valueRange range of values that GradientSlider value can take. Passed [value]
 * will be coerced to this range
 * @param onValueChangeFinished lambda to be invoked when value change has ended. This callback
 * shouldn't be used to update the slider value (use [onValueChange] for that), but rather to
 * know when the user has completed selecting a new value by ending a drag or a click.
 * @param interactionSource the [MutableInteractionSource] representing the stream of
 * [Interaction]s for this GradientSlider. You can create and pass in your own remembered
 * [MutableInteractionSource] if you want to observe [Interaction]s and customize the
 * appearance / behavior of this GradientSlider in different [Interaction]s.
 * @param colors [SliderColors] that will be used to determine the color of the GradientSlider
 * parts in different state. See [SliderDefaults.colors] to customize.
 */
@Composable fun GradientSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    colors: SliderColors = SliderDefaults.colors()
) {
    val onValueChangeState = rememberUpdatedState(onValueChange)

    BoxWithConstraints(modifier
        .requiredSizeIn(minWidth = ThumbRadius * 2, minHeight = ThumbRadius * 2)
        .sliderSemantics(value, enabled, onValueChange, valueRange)
        .focusable(enabled, interactionSource)
    ) {
        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        val maxPx = constraints.maxWidth.toFloat()
        val minPx = 0f

        fun scaleToUserValue(offset: Float) =
            scale(minPx, maxPx, offset, valueRange.start, valueRange.endInclusive)

        fun scaleToOffset(userValue: Float) =
            scale(valueRange.start, valueRange.endInclusive, userValue, minPx, maxPx)

        val rawOffset = remember { mutableStateOf(scaleToOffset(value)) }
        val draggableState = remember(minPx, maxPx, valueRange) {
            SliderDraggableState {
                rawOffset.value = (rawOffset.value + it).coerceIn(minPx, maxPx)
                onValueChangeState.value.invoke(scaleToUserValue(rawOffset.value))
            }
        }

        CorrectValueSideEffect(::scaleToOffset, valueRange, rawOffset, value)

        val gestureEndAction = rememberUpdatedState { velocity: Float ->
            if (!draggableState.isDragging) {
                // check ifDragging in case the change is still in progress (touch -> drag case)
                onValueChangeFinished?.invoke()
            }
        }

        val press = Modifier.sliderPressModifier(
            draggableState, interactionSource, maxPx, isRtl, gestureEndAction, enabled
        )

        val drag = Modifier.draggable(
            orientation = Orientation.Horizontal,
            reverseDirection = isRtl,
            enabled = enabled,
            interactionSource = interactionSource,
            onDragStopped = { velocity -> gestureEndAction.value.invoke(velocity) },
            startDragImmediately = draggableState.isDragging,
            state = draggableState
        )

        val coerced = value.coerceIn(valueRange.start, valueRange.endInclusive)
        val fraction = calcFraction(valueRange.start, valueRange.endInclusive, coerced)
        SliderImpl(enabled, fraction, colors, maxPx,
                   interactionSource, press.then(drag))
    }
}

/**
 * Object to hold defaults used by [GradientSlider]
 */
object SliderDefaults {

    /**
     * Creates a [SliderColors] that represents the different colors used in parts of the
     * [GradientSlider] in different states.
     *
     * For the name references below the words "active" and "inactive" are used. Active part of
     * the slider is filled with progress, so if slider's progress is 30% out of 100%, left (or
     * right in RTL) 30% of the track will be active, the rest is not active.
     *
     * @param thumbColor thumb color when enabled
     * @param disabledThumbColor thumb colors when disabled
     * @param activeTrackColor color of the track in the part that is "active", meaning that the
     * thumb is ahead of it
     * @param inactiveTrackColor color of the track in the part that is "inactive", meaning that the
     * thumb is before it
     * @param disabledActiveTrackColor color of the track in the "active" part when the Slider is
     * disabled
     * @param disabledInactiveTrackColor color of the track in the "inactive" part when the
     * Slider is disabled
     */
    @Composable fun colors(
        thumbColor: Color = MaterialTheme.colors.primary,
        thumbColorEnd: Color = MaterialTheme.colors.primaryVariant,
        disabledThumbColor: Color = MaterialTheme.colors.onSurface
            .copy(alpha = ContentAlpha.disabled)
            .compositeOver(MaterialTheme.colors.surface),
        activeTrackColor: Color = MaterialTheme.colors.primary,
        inactiveTrackColor: Color = activeTrackColor.copy(alpha = InactiveTrackAlpha),
        activeTrackBrush: Brush? = Brush.horizontalGradient(listOf(
            MaterialTheme.colors.primary, MaterialTheme.colors.primaryVariant)),
        disabledActiveTrackColor: Color =
            MaterialTheme.colors.onSurface.copy(alpha = DisabledActiveTrackAlpha),
        disabledInactiveTrackColor: Color =
            disabledActiveTrackColor.copy(alpha = DisabledInactiveTrackAlpha)
    ): SliderColors = DefaultSliderColors(
        thumbColor = thumbColor,
        thumbColorEnd = thumbColorEnd,
        disabledThumbColor = disabledThumbColor,
        activeTrackColor = activeTrackColor,
        inactiveTrackColor = inactiveTrackColor,
        activeTrackBrush = activeTrackBrush,
        disabledActiveTrackColor = disabledActiveTrackColor,
        disabledInactiveTrackColor = disabledInactiveTrackColor,
    )

    /**
     * Default alpha of the inactive part of the track
     */
    const val InactiveTrackAlpha = 0.24f

    /**
     * Default alpha for the track when it is disabled but active
     */
    const val DisabledInactiveTrackAlpha = 0.12f

    /**
     * Default alpha for the track when it is disabled and inactive
     */
    const val DisabledActiveTrackAlpha = 0.32f
}

/**
 * Represents the colors used by a [GradientSlider] and its parts in different states
 *
 * See [SliderDefaults.colors] for the default implementation that follows Material
 * specifications.
 */
@Stable
interface SliderColors {

    /**
     * Represents the color used for the slider's thumb, depending on [enabled].
     *
     * @param enabled whether the [GradientSlider] is enabled or not
     */
    @Composable
    fun thumbColor(enabled: Boolean): State<Color>

    /**
     * Represents the color used for the thumb if it is at the end position and is
     * enabled. Positions in between the start and end will use a color interpolated
     * between thumbColor and thumbColorEnd. If null, the thumb will use the color
     * returned by thumbColor for all positions.
     */
    @Composable
    fun thumbColorEnd(): State<Color?>

    /**
     * Represents the color used for the slider's track, depending on [enabled] and [active].
     *
     * Active part is filled with progress, so if sliders progress is 30% out of 100%, left (or
     * right in RTL) 30% of the track will be active, the rest is not active.
     *
     * @param enabled whether the [GradientSlider] is enabled or not
     * @param active whether the part of the track is active of not
     */
    @Composable
    fun trackColor(enabled: Boolean, active: Boolean): State<Color>

    /**
     * Represents the brush used for the active part of the slider's track while it is enabled.
     * Will override trackColor for enabled and active parts of the track if not null.
     */
    @Composable
    fun activeTrackBrush(): State<Brush?>
}

@Composable
private fun SliderImpl(
    enabled: Boolean,
    positionFraction: Float,
    colors: SliderColors,
    width: Float,
    interactionSource: MutableInteractionSource,
    modifier: Modifier,
) {
    Box(modifier.then(DefaultSliderConstraints)) {
        val trackStrokeWidth: Float
        val thumbPx: Float
        val widthDp: Dp
        with(LocalDensity.current) {
            trackStrokeWidth = TrackHeight.toPx()
            thumbPx = ThumbRadius.toPx()
            widthDp = width.toDp()
        }

        val thumbSize = ThumbRadius * 2
        val offset = (widthDp - thumbSize) * positionFraction
        val center = Modifier.align(Alignment.CenterStart)

        Track(center.fillMaxSize(), colors, enabled,
              0f, positionFraction, thumbPx, trackStrokeWidth)
        SliderThumb(center, offset, interactionSource, colors,
                    enabled, thumbSize, positionFraction)
    }
}

@Composable
private fun SliderThumb(
    modifier: Modifier,
    offset: Dp,
    interactionSource: MutableInteractionSource,
    colors: SliderColors,
    enabled: Boolean,
    thumbSize: Dp,
    positionFraction: Float,
) {
    Box(modifier.padding(start = offset)) {
        val interactions = remember { mutableStateListOf<Interaction>() }
        LaunchedEffect(interactionSource) {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> interactions.add(interaction)
                    is PressInteraction.Release -> interactions.remove(interaction.press)
                    is PressInteraction.Cancel -> interactions.remove(interaction.press)
                    is DragInteraction.Start -> interactions.add(interaction)
                    is DragInteraction.Stop -> interactions.remove(interaction.start)
                    is DragInteraction.Cancel -> interactions.remove(interaction.start)
                }
            }
        }

        val elevation = if (interactions.isNotEmpty()) {
            ThumbPressedElevation
        } else {
            ThumbDefaultElevation
        }
        val thumbColor = if (!enabled) colors.thumbColor(false).value else {
            val thumbColorEnd = colors.thumbColorEnd().value
            val thumbColorStart = colors.thumbColor(true).value
            if (thumbColorEnd == null) thumbColorStart
            else Color(ColorUtils.blendARGB(thumbColorStart.toArgb(),
                                            thumbColorEnd.toArgb(),
                                            positionFraction))
        }
        Box(Modifier
            .size(thumbSize, thumbSize)
            .indication(interactionSource,
                rememberRipple(bounded = false, radius = ThumbRippleRadius))
            .shadow(if (enabled) elevation else 0.dp, CircleShape, clip = false)
            .background(thumbColor, CircleShape))
    }
}

@Composable
private fun Track(
    modifier: Modifier,
    colors: SliderColors,
    enabled: Boolean,
    positionFractionStart: Float,
    positionFractionEnd: Float,
    thumbPx: Float,
    trackStrokeWidth: Float
) {
    val inactiveTrackColor = colors.trackColor(enabled, active = false)
    val activeTrackColor = colors.trackColor(enabled, active = true)
    val activeTrackBrush = colors.activeTrackBrush().value

    Canvas(modifier) {
        val isRtl = layoutDirection == LayoutDirection.Rtl
        val sliderLeft = Offset(thumbPx, center.y)
        val sliderRight = Offset(size.width - thumbPx, center.y)
        val sliderStart = if (isRtl) sliderRight else sliderLeft
        val sliderEnd = if (isRtl) sliderLeft else sliderRight
        drawLine(
            inactiveTrackColor.value,
            sliderStart,
            sliderEnd,
            trackStrokeWidth,
            StrokeCap.Round
        )
        val sliderValueEnd = Offset(
            sliderStart.x + (sliderEnd.x - sliderStart.x) * positionFractionEnd,
            center.y)

        val sliderValueStart = Offset(
            sliderStart.x + (sliderEnd.x - sliderStart.x) * positionFractionStart,
            center.y)

        if (activeTrackBrush == null)
            drawLine(activeTrackColor.value, sliderValueStart,
                     sliderValueEnd, trackStrokeWidth, StrokeCap.Round)
        else drawLine(activeTrackBrush, sliderValueStart,
                sliderValueEnd, trackStrokeWidth, StrokeCap.Round)
    }
}

private fun snapValueToTick(
    current: Float,
    tickFractions: List<Float>,
    minPx: Float,
    maxPx: Float
): Float {
    // target is a closest anchor to the `current`, if exists
    return tickFractions
        .minByOrNull { abs(lerp(minPx, maxPx, it) - current) }
        ?.run { lerp(minPx, maxPx, this) }
        ?: current
}

private suspend fun AwaitPointerEventScope.awaitSlop(
    id: PointerId
): Pair<PointerInputChange, Float>? {
    var initialDelta = 0f
    val postTouchSlop = { pointerInput: PointerInputChange, offset: Float ->
        pointerInput.consumePositionChange()
        initialDelta = offset
    }
    val afterSlopResult = awaitHorizontalTouchSlopOrCancellation(id, postTouchSlop)
    return if (afterSlopResult != null) afterSlopResult to initialDelta else null
}

private fun stepsToTickFractions(steps: Int): List<Float> {
    return if (steps == 0) emptyList() else List(steps + 2) { it.toFloat() / (steps + 1) }
}

// Scale x1 from a1..b1 range to a2..b2 range
private fun scale(a1: Float, b1: Float, x1: Float, a2: Float, b2: Float) =
    lerp(a2, b2, calcFraction(a1, b1, x1))

// Scale x.start, x.endInclusive from a1..b1 range to a2..b2 range
private fun scale(a1: Float, b1: Float, x: ClosedFloatingPointRange<Float>, a2: Float, b2: Float) =
    scale(a1, b1, x.start, a2, b2)..scale(a1, b1, x.endInclusive, a2, b2)

// Calculate the 0..1 fraction that `pos` value represents between `a` and `b`
private fun calcFraction(a: Float, b: Float, pos: Float) =
    (if (b - a == 0f) 0f else (pos - a) / (b - a)).coerceIn(0f, 1f)

@Composable
private fun CorrectValueSideEffect(
    scaleToOffset: (Float) -> Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueState: MutableState<Float>,
    value: Float
) {
    SideEffect {
        val error = (valueRange.endInclusive - valueRange.start) / 1000
        val newOffset = scaleToOffset(value)
        if (abs(newOffset - valueState.value) > error)
            valueState.value = newOffset
    }
}

private fun Modifier.sliderSemantics(
    value: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
): Modifier {
    val coerced = value.coerceIn(valueRange.start, valueRange.endInclusive)
    return semantics(mergeDescendants = true) {
        if (!enabled) disabled()
        setProgress(
            action = { targetValue ->
                val newValue = targetValue.coerceIn(valueRange.start, valueRange.endInclusive)
                // This is to keep it consistent with AbsSeekbar.java: return false if no
                // change from current.
                if (newValue == coerced)
                    false
                else {
                    onValueChange(newValue)
                    true
                }
            }
        )
    }.progressSemantics(value, valueRange)
}

private fun Modifier.sliderPressModifier(
    draggableState: DraggableState,
    interactionSource: MutableInteractionSource,
    maxPx: Float,
    isRtl: Boolean,
//    rawOffset: State<Float>,
    gestureEndAction: State<(Float) -> Unit>,
    enabled: Boolean
): Modifier = if (!enabled) this else
    pointerInput(draggableState, interactionSource, maxPx, isRtl) {
        detectTapGestures(
            onPress = { pos ->
//                draggableState.drag(MutatePriority.UserInput) {
//                    val to = if (isRtl) maxPx - pos.x else pos.x
//                    dragBy(to - rawOffset.value)
//                }
                val interaction = PressInteraction.Press(pos)
                interactionSource.emit(interaction)
                val finishInteraction = try {
                    val success = tryAwaitRelease()
                    gestureEndAction.value.invoke(0f)
                    if (success) PressInteraction.Release(interaction)
                    else         PressInteraction.Cancel(interaction)
                } catch (c: CancellationException) {
                    PressInteraction.Cancel(interaction)
                }
                interactionSource.emit(finishInteraction)
            }
        )
    }

private suspend fun animateToTarget(
    draggableState: DraggableState,
    current: Float,
    target: Float,
    velocity: Float
) {
    draggableState.drag {
        var latestValue = current
        Animatable(initialValue = current).animateTo(target, SliderToTickAnimation, velocity) {
            dragBy(this.value - latestValue)
            latestValue = this.value
        }
    }
}

private fun Modifier.rangeSliderPressDragModifier(
    startInteractionSource: MutableInteractionSource,
    endInteractionSource: MutableInteractionSource,
    rawOffsetStart: State<Float>,
    rawOffsetEnd: State<Float>,
    enabled: Boolean,
    isRtl: Boolean,
    maxPx: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    gestureEndAction: State<(Boolean) -> Unit>,
    onDrag: (Boolean, Float) -> Unit,
): Modifier =
    if (enabled) {
        pointerInput(startInteractionSource, endInteractionSource, maxPx, isRtl, valueRange) {
            val rangeSliderLogic = RangeSliderLogic(
                startInteractionSource,
                endInteractionSource,
                rawOffsetStart,
                rawOffsetEnd,
                onDrag
            )
            coroutineScope {
                forEachGesture {
                    awaitPointerEventScope {
                        var thumbCaptured = false
                        // If we are dragging the start thumb, false if we are dragging end thumb.
                        var draggingStart = true
                        val pointerEvent = awaitFirstDown(requireUnconsumed = false)
                        val interaction = PressInteraction.Press(pointerEvent.position)
                        val slop = viewConfiguration.touchSlop
                        val posX =
                            if (isRtl) maxPx - pointerEvent.position.x else pointerEvent.position.x

                        if (abs(rawOffsetEnd.value - posX) > slop ||
                            abs(rawOffsetStart.value - posX) > slop
                        ) {
                            // We have enough distance we can start dragging right away
                            draggingStart = rangeSliderLogic.shouldCaptureStartThumb(posX)
                            rangeSliderLogic.captureThumb(
                                draggingStart,
                                posX,
                                interaction,
                                this@coroutineScope
                            )
                            thumbCaptured = true
                        }

                        awaitSlop(pointerEvent.id)?.let {
                            if (thumbCaptured) {
                                onDrag(draggingStart, if (isRtl) -it.second else it.second)
                            } else {
                                // Determine which thumb to drag based on the direction the user
                                // is dragging
                                val dir = it.second
                                draggingStart = if (isRtl) dir >= 0f else dir < 0f
                            }
                        }

                        if (!thumbCaptured) {
                            rangeSliderLogic.captureThumb(
                                draggingStart,
                                posX,
                                interaction,
                                this@coroutineScope
                            )
                        }

                        val finishInteraction = try {
                            val success = horizontalDrag(pointerId = pointerEvent.id) {
                                val deltaX = it.positionChange().x
                                onDrag(draggingStart, if (isRtl) -deltaX else deltaX)
                            }
                            if (success) {
                                PressInteraction.Release(interaction)
                            } else {
                                PressInteraction.Cancel(interaction)
                            }
                        } catch (e: CancellationException) {
                            PressInteraction.Cancel(interaction)
                        }

                        gestureEndAction.value.invoke(draggingStart)
                        launch {
                            rangeSliderLogic
                                .activeInteraction(draggingStart)
                                .emit(finishInteraction)
                        }
                    }
                }
            }
        }
    } else {
        this
    }

private class RangeSliderLogic(
    val startInteractionSource: MutableInteractionSource,
    val endInteractionSource: MutableInteractionSource,
    val rawOffsetStart: State<Float>,
    val rawOffsetEnd: State<Float>,
    val onDrag: (Boolean, Float) -> Unit,
) {
    fun activeInteraction(draggingStart: Boolean): MutableInteractionSource =
        if (draggingStart) startInteractionSource else endInteractionSource

    fun shouldCaptureStartThumb(eventX: Float): Boolean {
        val diffStart = abs(rawOffsetStart.value - eventX)
        val diffEnd = abs(rawOffsetEnd.value - eventX)
        return if (diffEnd == diffStart)
            rawOffsetStart.value > eventX
        else diffStart < diffEnd
    }

    fun captureThumb(
        draggingStart: Boolean,
        posX: Float,
        interaction: Interaction,
        scope: CoroutineScope
    ) {
        onDrag(
            draggingStart,
            posX - if (draggingStart) rawOffsetStart.value else rawOffsetEnd.value
        )
        scope.launch {
            activeInteraction(draggingStart).emit(interaction)
        }
    }
}

@Immutable
private class DefaultSliderColors(
    private val thumbColor: Color,
    private val thumbColorEnd: Color?,
    private val disabledThumbColor: Color,
    private val activeTrackColor: Color,
    private val inactiveTrackColor: Color,
    private val activeTrackBrush: Brush?,
    private val disabledActiveTrackColor: Color,
    private val disabledInactiveTrackColor: Color,
) : SliderColors {

    @Composable
    override fun thumbColor(enabled: Boolean): State<Color> {
        return rememberUpdatedState(if (enabled) thumbColor else disabledThumbColor)
    }

    @Composable override fun thumbColorEnd() = rememberUpdatedState(thumbColorEnd)

    @Composable
    override fun trackColor(enabled: Boolean, active: Boolean): State<Color> {
        return rememberUpdatedState(
            if (enabled) {
                if (active) activeTrackColor else inactiveTrackColor
            } else {
                if (active) disabledActiveTrackColor else disabledInactiveTrackColor
            }
        )
    }

    @Composable override fun activeTrackBrush() = rememberUpdatedState(activeTrackBrush)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as DefaultSliderColors

        if (thumbColor != other.thumbColor) return false
        if (disabledThumbColor != other.disabledThumbColor) return false
        if (activeTrackColor != other.activeTrackColor) return false
        if (inactiveTrackColor != other.inactiveTrackColor) return false
        if (disabledActiveTrackColor != other.disabledActiveTrackColor) return false
        if (disabledInactiveTrackColor != other.disabledInactiveTrackColor) return false

        return true
    }

    override fun hashCode(): Int {
        var result = thumbColor.hashCode()
        result = 31 * result + disabledThumbColor.hashCode()
        result = 31 * result + activeTrackColor.hashCode()
        result = 31 * result + inactiveTrackColor.hashCode()
        result = 31 * result + disabledActiveTrackColor.hashCode()
        result = 31 * result + disabledInactiveTrackColor.hashCode()
        return result
    }
}

// Internal to be referred to in tests
internal val ThumbRadius = 10.dp
private val ThumbRippleRadius = 24.dp
private val ThumbDefaultElevation = 1.dp
private val ThumbPressedElevation = 6.dp

// Internal to be referred to in tests
internal val TrackHeight = 10.dp
private val SliderHeight = 48.dp
private val SliderMinWidth = 144.dp
private val DefaultSliderConstraints =
    Modifier
        .widthIn(min = SliderMinWidth)
        .heightIn(max = SliderHeight)

private val SliderToTickAnimation = TweenSpec<Float>(durationMillis = 100)

private class SliderDraggableState(
    val onDelta: (Float) -> Unit
) : DraggableState {

    var isDragging by mutableStateOf(false)
        private set

    private val dragScope: DragScope = object : DragScope {
        override fun dragBy(pixels: Float): Unit = onDelta(pixels)
    }

    private val scrollMutex = MutatorMutex()

    override suspend fun drag(
        dragPriority: MutatePriority,
        block: suspend DragScope.() -> Unit
    ): Unit = coroutineScope {
        isDragging = true
        scrollMutex.mutateWith(dragScope, dragPriority, block)
        isDragging = false
    }

    override fun dispatchRawDelta(delta: Float) {
        return onDelta(delta)
    }
}