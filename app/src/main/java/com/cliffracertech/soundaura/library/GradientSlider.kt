/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license.
 *
 * The original Slider AOSP class has been modified in the
 * following ways to make the GradientSlider class:
 * - The SliderRange class has been removed
 * - The steps/ticks functionality has been removed
 * - The SliderColors class has been changed to GradientSliderColors
 *   to support gradients for the slider track and thumb, and the tick
 *   colors have been removed
 * - The slider no longer jumps to the tap location when the slider is
 *   tapped outside the thumb.
 */
package com.cliffracertech.soundaura.library

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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.DragScope
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.SliderColors
import androidx.compose.material.SliderDefaults
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.google.android.material.math.MathUtils.lerp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlin.math.abs

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
    colors: GradientSliderColors = GradientSliderDefaults.colors()
) {
    BoxWithConstraints(modifier.then(DefaultSliderConstraints)) {
        val coerced = value.coerceIn(valueRange.start, valueRange.endInclusive)
        val fraction = calcFraction(valueRange.start, valueRange.endInclusive, coerced)

        val pixelRange = 0f..constraints.maxWidth.toFloat()
        val trackStrokeWidth: Float
        val thumbPx: Float
        val widthDp: Dp
        with(LocalDensity.current) {
            trackStrokeWidth = TrackHeight.toPx()
            thumbPx = ThumbRadius.toPx()
            widthDp = pixelRange.endInclusive.toDp()
        }

        val center = Modifier.align(Alignment.CenterStart)
        Track(center.fillMaxSize(), colors, enabled,
              0f, fraction, thumbPx, trackStrokeWidth)

        val thumbSize = ThumbRadius * 2
        val offset = (widthDp - thumbSize) * fraction
        SliderThumb(center, offset, interactionSource, colors,
                    enabled, thumbSize, fraction, pixelRange, value,
                    valueRange, onValueChange, onValueChangeFinished)
    }
}

/**
 * Object to hold defaults used by [GradientSlider]
 */
object GradientSliderDefaults {
    /**
     * Creates a [GradientSliderColors] that represents the different colors used in parts of the
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
        thumbColorEnd: Color = MaterialTheme.colors.secondary,
        disabledThumbColor: Color = MaterialTheme.colors.onSurface
            .copy(alpha = ContentAlpha.disabled)
            .compositeOver(MaterialTheme.colors.surface),
        activeTrackColor: Color = MaterialTheme.colors.primary,
        inactiveTrackColor: Color = activeTrackColor.copy(alpha = InactiveTrackAlpha),
        activeTrackBrush: Brush? = Brush.horizontalGradient(listOf(
            MaterialTheme.colors.primary, MaterialTheme.colors.secondary)),
        disabledActiveTrackColor: Color =
            MaterialTheme.colors.onSurface.copy(alpha = DisabledActiveTrackAlpha),
        disabledInactiveTrackColor: Color =
            disabledActiveTrackColor.copy(alpha = DisabledInactiveTrackAlpha)
    ): GradientSliderColors = DefaultGradientSliderColors(
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
interface GradientSliderColors {
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
private fun SliderThumb(
    modifier: Modifier,
    offset: Dp,
    interactionSource: MutableInteractionSource,
    colors: GradientSliderColors,
    enabled: Boolean,
    thumbSize: Dp,
    positionFraction: Float,
    pixelRange: ClosedFloatingPointRange<Float>,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    fun scaleToOffset(userValue: Float) = scale(userValue, valueRange, pixelRange)
    val rawOffsetState = remember { mutableFloatStateOf(scaleToOffset(value)) }
    var rawOffset by rawOffsetState
    CorrectValueSideEffect(::scaleToOffset, valueRange, rawOffsetState, value)

    fun scaleToUserValue(offset: Float) =
        scale(pixelRange.start, pixelRange.endInclusive, offset,
              valueRange.start, valueRange.endInclusive)

    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val onValueChangeState = rememberUpdatedState(onValueChange)
    val draggableState = remember(pixelRange, valueRange) {
        SliderDraggableState {
            rawOffset = (rawOffset + it).coerceIn(pixelRange)
            onValueChangeState.value.invoke(scaleToUserValue(rawOffset))
        }
    }
    val gestureEndAction = rememberUpdatedState { velocity: Float ->
        // check ifDragging in case the change is still in progress (touch -> drag case)
        if (!draggableState.isDragging)
            onValueChangeFinished?.invoke()
    }

    Box(modifier
        .padding(start = offset)
        .sliderSemantics(value, enabled, onValueChange, valueRange)
        .focusable(enabled, interactionSource)
        .sliderPressModifier(
            draggableState, interactionSource, pixelRange.endInclusive,
            isRtl, gestureEndAction, enabled)
        .draggable(
            orientation = Orientation.Horizontal,
            reverseDirection = isRtl,
            enabled = enabled,
            interactionSource = interactionSource,
            onDragStopped = { velocity -> gestureEndAction.value.invoke(velocity) },
            startDragImmediately = draggableState.isDragging,
            state = draggableState)
        .indication(interactionSource,
            rememberRipple(bounded = false, radius = ThumbRippleRadius))
    ) {
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

        val elevation = when { !enabled ->               0.dp
                               interactions.isEmpty() -> ThumbDefaultElevation
                               else ->                   ThumbPressedElevation }
        val thumbColorStart = colors.thumbColor(enabled).value
        val thumbColorEnd = colors.thumbColorEnd().value
        val thumbColor = if (thumbColorEnd == null || !enabled)
                             thumbColorStart
                         else Color(ColorUtils.blendARGB(thumbColorStart.toArgb(),
                                                         thumbColorEnd.toArgb(),
                                                         positionFraction))
        Box(Modifier
            .size(thumbSize, thumbSize)
            .shadow(elevation, CircleShape, clip = false)
            .background(thumbColor, CircleShape))
    }
}

@Composable
private fun Track(
    modifier: Modifier,
    colors: GradientSliderColors,
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

// Scale x1 from a1..b1 range to a2..b2 range
private fun scale(a1: Float, b1: Float, x1: Float, a2: Float, b2: Float) =
    lerp(a2, b2, calcFraction(a1, b1, x1))

// Scale x1 from range1 to range2
private fun scale(
    x1: Float,
    range1: ClosedFloatingPointRange<Float>,
    range2: ClosedFloatingPointRange<Float>
) = scale(range1.start, range1.endInclusive, x1, range2.start, range2.endInclusive)

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
    gestureEndAction: State<(Float) -> Unit>,
    enabled: Boolean
): Modifier = if (!enabled) this else
    pointerInput(draggableState, interactionSource, maxPx, isRtl) {
        detectTapGestures(onPress = { pos ->
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
        })
    }

@Immutable
private class DefaultGradientSliderColors(
    private val thumbColor: Color,
    private val thumbColorEnd: Color?,
    private val disabledThumbColor: Color,
    private val activeTrackColor: Color,
    private val inactiveTrackColor: Color,
    private val activeTrackBrush: Brush?,
    private val disabledActiveTrackColor: Color,
    private val disabledInactiveTrackColor: Color,
) : GradientSliderColors {

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

        other as DefaultGradientSliderColors

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