/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.util.Range
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cliffracertech.soundaura.ui.theme.SoundAuraTheme
import java.time.Duration

/**
 * A dial to pick a number.
 *
 * @param modifier The [Modifier] to use for the dial
 * @param currentValue The value to be shown
 * @param formatString The [String] that will be formatted to
 *     display the current value. This [String] should contain
 *     one %d [String] argument for the current value.
 * @param onAmountChangeRequest The callback that will be
 *     invoked when the value should be changed to the
 *     provided value based on the user's drag gesture
 */
@Composable fun NumberDial(
    currentValue: Int,
    modifier: Modifier = Modifier,
    formatString: String = "%d",
    onAmountChangeRequest: (Int) -> Unit,
) {
    val density = LocalDensity.current
    var dragPx by remember { mutableStateOf(0f) }
    var lastRequestedValue by remember { mutableStateOf(currentValue) }
    val pxThreshold = remember { with (density) { 20.dp.roundToPx() }}

    Box(modifier
        .widthIn(min = 48.dp)
        .heightIn(min = 96.dp)
        .draggable(
            orientation = Orientation.Vertical,
            state = rememberDraggableState {
                dragPx += it
                val steps = dragPx.toInt() / pxThreshold
                // steps is subtracted instead of added so that negative
                // drag amounts (i.e. upward drags) increase the value
                // and positive drags (i.e. downward drags) decrease it.
                val targetValue = currentValue - steps
                if (lastRequestedValue != targetValue) {
                    lastRequestedValue = targetValue
                    onAmountChangeRequest(targetValue)
                    dragPx -= steps * pxThreshold
                }
            }, onDragStarted = { dragPx = 0f }),
    ) {
        IconButton(
            onClick = { onAmountChangeRequest(currentValue + 1) },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            // The icons use asymmetrical padding to give
            // the text display of the value more room
            Icon(imageVector = Icons.Default.ArrowDropUp,
                 contentDescription = null,
                 modifier = Modifier.padding(bottom = 16.dp))
        }

        Text(text = formatString.format(currentValue),
             modifier = Modifier.align(Alignment.Center))

        IconButton(
            onClick = { onAmountChangeRequest(currentValue - 1) },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Icon(imageVector = Icons.Default.ArrowDropDown,
                 contentDescription = null,
                 modifier = Modifier.padding(top = 16.dp))
        }
    }
}

/**
 * A row of three two digit [NumberDial]s that collectively allow a duration
 * to be picked by the user.
 *
 * @param modifier The [Modifier] to use for the [DurationPicker]
 * @param currentDuration The current [Duration] that will be shown in the [NumberDial]s
 * @param bounds A [Range]`<Duration>` that describes the acceptable range for the [Duration]
 * @param onDurationChange The callback that will be invoked when the user attempts
 *     to change the [Duration] to the provided value through the [NumberDial]s.
 */
@Composable fun DurationPicker(
    modifier: Modifier = Modifier,
    currentDuration: Duration,
    bounds: Range<Duration>? = null,
    onDurationChange: (Duration) -> Unit,
) = Row(
    modifier = modifier
        .border(width = 1.5.dp,
                color = LocalContentColor.current.copy(alpha = 0.2f),
                shape = MaterialTheme.shapes.medium)
        .padding(horizontal = 6.dp),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    fun Duration.setHours(hours: Int, bounds: Range<Duration>? = null): Duration {
        val result = plusHours(hours.toLong() - toHours().toInt())
        return if (bounds?.contains(result) != false)
            result else this
    }
    fun Duration.setMinutes(minutes: Int, bounds: Range<Duration>? = null): Duration {
        val result = plusMinutes(minutes.toLong() - toMinutesPart())
        return if (bounds?.contains(result) != false)
            result else this
    }
    fun Duration.setSeconds(seconds: Int, bounds: Range<Duration>? = null): Duration {
        val result = plusSeconds(seconds.toLong() - toSecondsPart())
        return if (bounds?.contains(result) != false)
            result else this
    }
    val dividerColor = LocalContentColor.current.copy(alpha = 0.2f)

    NumberDial( currentDuration.toHours().toInt(), formatString = "%02d h") {
        onDurationChange(currentDuration.setHours(it, bounds))
    }

    Box(Modifier.size(1.5.dp, 96.dp).background(dividerColor))

    NumberDial(currentDuration.toMinutesPart(), formatString = "%02d m") {
        onDurationChange(currentDuration.setMinutes(it, bounds))
    }

    Box(Modifier.size(1.5.dp, 96.dp).background(dividerColor))

    NumberDial(currentDuration.toSecondsPart(), formatString = "%02d s") {
        onDurationChange(currentDuration.setSeconds(it, bounds))
    }
}

@Composable @Preview
fun DurationPickerPreview() = SoundAuraTheme {
    var currentDuration by remember { mutableStateOf(Duration.ZERO) }
    val bounds = Range(Duration.ZERO, Duration.ofHours(100).minusSeconds(1))

    DurationPicker(
        currentDuration = currentDuration,
        onDurationChange = { currentDuration = it },
        bounds = bounds)
}

/**
 * A dialog to allow the user to pick a [Duration] using a [DurationPicker].
 *
 * @param modifier The [Modifier] to use for the dialog
 * @param title The [String] title to use for the dialog
 * @param description An optional [String] that will be displayed before
 *     the [DurationPicker] if not null
 * @param bounds A [Range]`<Duration>` that describes the acceptable range for the [Duration]
 * @param onDismissRequest The callback that will be invoked when the user
 *     attempts to dismiss or cancel the dialog
 * @param onConfirm The callback that will be invoked when the user taps the ok
 *     button with a [Duration] that is valid (i.e. within the provided [bounds]
 */
@Composable fun DurationPickerDialog(
    modifier: Modifier = Modifier,
    title: String,
    description: String? = null,
    bounds: Range<Duration>? = null,
    onDismissRequest: () -> Unit,
    onConfirm: (Duration) -> Unit,
) {
    var currentDuration by remember { mutableStateOf(Duration.ZERO) }

    SoundAuraDialog(
        modifier = modifier,
        title = title,
        onDismissRequest = onDismissRequest,
        confirmButtonEnabled = currentDuration > Duration.ZERO,
        onConfirm = {
            if (bounds?.contains(currentDuration) != false)
                onConfirm(currentDuration)
        }
    ) {
        if (description != null) {
            Text(text = description,
                modifier = Modifier.padding(horizontal = 16.dp),
                textAlign = TextAlign.Justify)
            Spacer(Modifier.height(12.dp))
        }
        DurationPicker(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .align(Alignment.CenterHorizontally),
            currentDuration = currentDuration,
            onDurationChange = { currentDuration = it },
            bounds = bounds)
    }
}
