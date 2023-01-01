/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.util.Range
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
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
    var valueOnDragStart by remember { mutableStateOf(0) }
    Column(
        modifier = modifier
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState {
                    val dp = with (density) { it.toDp() }
                    val amountChange = -(dp.value.toInt() / 20)
                    onAmountChangeRequest(valueOnDragStart + amountChange)
                }, onDragStarted = {
                    valueOnDragStart = currentValue
                }),
        Arrangement.SpaceBetween,
        Alignment.CenterHorizontally
    ) {
        IconButton({ onAmountChangeRequest(currentValue + 1) }) {
            Icon(Icons.Default.ArrowDropUp, null)
        }
        Text(formatString.format(currentValue))
        IconButton({ onAmountChangeRequest(currentValue - 1) }) {
            Icon(Icons.Default.ArrowDropDown, null)
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
) = Row(modifier, verticalAlignment = Alignment.CenterVertically) {

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
    val hours = currentDuration.toHours().toInt()
    NumberDial(hours, formatString = "%02d") {
        onDurationChange(currentDuration.setHours(it, bounds))
    }
    Text(":")
    NumberDial(currentDuration.toMinutesPart(), formatString = "%02d") {
        onDurationChange(currentDuration.setMinutes(it, bounds))
    }
    Text(":")
    NumberDial(currentDuration.toSecondsPart(), formatString = "%02d") {
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
        if (description != null)
            Text(description)
        DurationPicker(
            currentDuration = currentDuration,
            onDurationChange = { currentDuration = it },
            bounds = bounds)
    }
}

/**
 * A dialog to pick a [Duration] after which the user's sound mix will
 * automatically stop playing.
 *
 * @param modifier The [Modifier] to use for the dialog
 * @param onDismissRequest The callback that will be invoked when the user
 *     attempts to dismiss or cancel the dialog
 * @param onConfirm The callback that will be invoked when the user taps the ok
 *     button with a [Duration] that is valid (i.e. within the provided [bounds]
 */
@Composable fun SetAutoStopTimeDialog(
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit,
    onConfirm: (Duration) -> Unit,
) = DurationPickerDialog(
    modifier,
    title = stringResource(R.string.set_auto_stop_time_dialog_title),
    description = stringResource(R.string.set_auto_stop_time_dialog_description),
    bounds = Range(Duration.ZERO, Duration.ofHours(100).minusSeconds(1)),
    onDismissRequest, onConfirm)
