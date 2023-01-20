/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

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

/** A column of [Text] objects to display a time at which media playback
 * will automatically cease, indicated by the [Instant] value [stopTime]. */
@Composable fun StopTimer(
    stopTime: Instant,
    modifier: Modifier = Modifier,
) = Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
) {
    var durationRemaining = remember(stopTime) {
        stopTime.let { Duration.between(Instant.now(), it) }
    }
    // durationRemainingString is used so that when the stopTime
    // becomes null, the StopTimerDisplay can fade out with the
    // last non-null value of stopTime
    var durationRemainingString by remember {
        mutableStateOf(durationRemaining.toHMMSSstring())
    }
    LaunchedEffect(stopTime) {
        while (durationRemaining != null) {
            delay(1000)
            durationRemaining.minusSeconds(1).let {
                durationRemaining = it
                durationRemainingString = it.toHMMSSstring()
            }
        }
    }
    val style = MaterialTheme.typography.caption
    Text(stringResource(R.string.stop_timer_text), style = style)
    Text(durationRemainingString, style = style)
}