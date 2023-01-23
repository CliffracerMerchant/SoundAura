/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/**
 * A collection of callbacks replayed to the display and function
 * of a combination play/pause button with a long click action.
 *
 * @param isPlayingProvider A function that returns the media play/pause
 *     state that the button should use to determine its icon, which will
 *     be the opposite of the current state
 * @param onClick The callback that will be invoked when the button is clicked
 * @param clickLabelResIdProvider A method that returns the [Int] resource id
 *       for the [String] that will be used as the label for the click action,
 *       given [isPlayingProvider]'s return value
 * @param onLongClick The callback that will be invoked when the button is long clicked
 * @param longClickLabelResId The [Int] resource id for the [String] that will
 *                            be used as the label for the long click action
 */
data class PlayButtonCallback(
    val isPlayingProvider: () -> Boolean,
    val onClick: () -> Unit,
    val clickLabelResIdProvider: (isPlaying: Boolean) -> Int,
    val onLongClick: () -> Unit,
    val longClickLabelResId: Int)

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
    val isPlaying = callback.isPlayingProvider()
    val clickLabel = stringResource(callback.clickLabelResIdProvider(isPlaying))
    val longClickLabel = stringResource(callback.longClickLabelResId)

    Box(contentAlignment = Alignment.Center,
        modifier = modifier.combinedClickable(
            onClick = callback.onClick,
            onClickLabel = clickLabel,
            onLongClick = callback.onLongClick,
            onLongClickLabel = longClickLabel),
        content = { PlayPauseIcon(isPlaying, null) })
}