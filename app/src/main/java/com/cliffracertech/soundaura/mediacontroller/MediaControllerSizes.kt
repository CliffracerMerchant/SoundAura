/*
 * This file is part of SoundAura, which is released under the terms of the Apache
 * License 2.0. See license.md in the project's root directory to see the full license.
 */

package com.cliffracertech.soundaura.mediacontroller

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.cliffracertech.soundaura.ui.theme.bottomShape
import com.cliffracertech.soundaura.ui.theme.endShape
import com.cliffracertech.soundaura.ui.theme.startShape
import com.cliffracertech.soundaura.ui.theme.topShape

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
 * @param playButtonLength The length of the play/pause button. The button's
 *     other dimension will match the derived thickness of the [MediaController].
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
    val playButtonLength: Dp = defaultPlayButtonLengthDp.dp,
    val stopTimerSize: DpSize = DpSize(
        width = defaultStopTimerWidthDp.dp,
        height = defaultStopTimerHeightDp.dp),
    val presetSelectorSize: DpSize,
) {
    val dividerSize get() = dividerThicknessDp.dp
    val stopTimeLength get() =
        if (orientation.isVertical) stopTimerSize.height
        else                        stopTimerSize.width

    val collapsedThickness = maxOf(minThickness,
        if (orientation.isVertical) stopTimerSize.width
        else                        stopTimerSize.height)

    val activePresetSize = DpSize(
        width = if (orientation.isVertical) collapsedThickness else activePresetLength,
        height = if (orientation.isVertical) activePresetLength else collapsedThickness)

    val buttonSize = DpSize(
        width = if (orientation.isVertical) collapsedThickness else playButtonLength,
        height = if (orientation.isVertical) playButtonLength else collapsedThickness)

    /** Return the size of a collapsed [MediaController] (i.e. when its
     * showingPresetSelector parameter is false) given whether or not the
     * auto stop time is being shown and the orientation. */
    fun collapsedSize(showingStopTimer: Boolean): DpSize {
        val stopTimerLength = if (!showingStopTimer) 0.dp
        else dividerSize + stopTimeLength
        val length = activePresetLength + dividerSize +
                     playButtonLength + stopTimerLength
        return DpSize(
            width = if (orientation.isVertical) collapsedThickness else length,
            height = if (orientation.isVertical) length else collapsedThickness)
    }

    /** Return a remembered current size of a [MediaController] instance given
     * whether or not the preset selector is being shown and whether an auto
     * stop timer is set. */
    @Composable fun rememberCurrentSize(
        showingPresetSelector: Boolean,
        hasStopTime: Boolean,
    ) = remember(showingPresetSelector, hasStopTime) {
        if (showingPresetSelector) presetSelectorSize
        else                       collapsedSize(hasStopTime)
    }

    val shape = RoundedCornerShape(28.dp)

    val activePresetShape =
        if (orientation.isVertical) shape.topShape()
        else                        shape.startShape()

    val stopTimerShape =
        if (orientation.isVertical) shape.bottomShape()
        else                        shape.endShape()

    fun playButtonShape(showingStopTimer: Boolean) =
        if (showingStopTimer) RectangleShape
        else                  stopTimerShape

    companion object {
        const val defaultPlayButtonLengthDp = 56
        const val defaultStopTimerWidthDp = 72
        const val defaultStopTimerHeightDp = 56
        const val dividerThicknessDp = 1.5f
        const val defaultMinThicknessDp = 56
    }
}