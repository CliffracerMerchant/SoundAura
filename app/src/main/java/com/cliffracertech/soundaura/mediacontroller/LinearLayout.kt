/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.mediacontroller

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** A layout that acts as either a [Row] (when [orientation] is
 * [Orientation.Horizontal] or a [Column] (when [orientation] is
 * [Orientation.Vertical]. A divider within the linear layout can
 * be created within the [content] lambda with the Composable
 * lambda divider that is passed into it. The row/column's
 * alignment will always be Alignment.CenterVertically or
 * Alignment.CenterHorizontally, respectively.*/
@Composable fun LinearLayout(
    orientation: Orientation,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) = if (orientation.isHorizontal)
        Row(modifier, verticalAlignment = Alignment.CenterVertically) { content() }
    else Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) { content() }

@Composable fun Divider(
    orientation: Orientation,
    modifier: Modifier = Modifier,
    sizeFraction: Float = 1f,
) = Box(modifier
    .background(LocalContentColor.current.copy(alpha = 0.2f))
    .then(if (orientation.isHorizontal)
        Modifier.width((1.5).dp).fillMaxHeight(sizeFraction)
    else Modifier.fillMaxWidth(sizeFraction).height((1.5).dp)))