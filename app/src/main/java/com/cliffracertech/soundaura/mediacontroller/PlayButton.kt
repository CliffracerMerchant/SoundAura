/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.mediacontroller

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.cliffracertech.soundaura.R

/** A collection of callbacks replayed to the display and function
 * of a combination play/pause button with a long click action. */
interface PlayButtonCallback {
    /** A function that returns the media play/pause state that the button should
     * use to determine its icon, which will be the opposite of the current state */
    fun getIsPlaying(): Boolean
    /** The callback that will be invoked when the button is clicked */
    fun onClick()
    /** A method that returns the [Int] resource id for the [String] that will be
     * used as the label for the click action, given [getIsPlaying]'s return value */
    fun getClickLabelResId(isPlaying: Boolean): Int
    /** The callback that will be invoked when the button is long clicked */
    fun onLongClick()
    /** The [Int] resource id for the [String] that will
     * be used as the label for the long click action */
    val longClickLabelResId: Int
}

/**
 * A combination play/pause button with both click and long click actions.
 *
 * @param callback The [PlayButtonCallback] used for the state of the button
 * @param modifier The [Modifier] to use for the button
 */
@Composable fun PlayButton(
    callback: PlayButtonCallback,
    modifier: Modifier = Modifier,
) {
    val isPlaying = callback.getIsPlaying()
    val clickLabel = stringResource(callback.getClickLabelResId(isPlaying))
    val longClickLabel = stringResource(callback.longClickLabelResId)

    Box(contentAlignment = Alignment.Center,
        modifier = modifier.combinedClickable(
            onClick = callback::onClick,
            onClickLabel = clickLabel,
            onLongClick = callback::onLongClick,
            onLongClickLabel = longClickLabel),
    ) {
        val playToPause = AnimatedImageVector.animatedVectorResource(R.drawable.play_to_pause)
        val playToPausePainter = rememberAnimatedVectorPainter(playToPause, atEnd = isPlaying)
        val pauseToPlay = AnimatedImageVector.animatedVectorResource(R.drawable.pause_to_play)
        val pauseToPlayPainter = rememberAnimatedVectorPainter(pauseToPlay, atEnd = !isPlaying)
        val painter = if (isPlaying) playToPausePainter
                      else           pauseToPlayPainter
        Icon(painter, null)
    }
}