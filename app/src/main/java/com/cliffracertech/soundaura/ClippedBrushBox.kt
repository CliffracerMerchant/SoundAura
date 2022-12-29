/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.*

val LayoutDirection.isLtr get() = this == LayoutDirection.Ltr

/**
 * Compose a [Box] with a [Brush] defined by the parameter [brush] applied
 * across the maximum allowed size, but then clipped down to [size] with a
 * corner radius matching [cornerRadius]. The parameters [alignment] and
 * [padding] will also be utilized in determining the placement of the box.
 * ClippedBrushBox can be used to, e.g., create a box with a gradient that
 * matches a background screen spanning gradient without having to manually
 * adjust the startX and endX of the gradient depending on the position of
 * the box. Changes to the [size] parameter will automatically be animated.
 *
 * Note that desired padding should only be provided through the [padding]
 * parameter; adding padding to the provided [modifier] will cause it to be
 * applied twice.
 */
@Composable fun ClippedBrushBox(
    modifier: Modifier = Modifier,
    brush: Brush,
    size: DpSize,
    cornerRadius: Dp,
    alignment: BiasAlignment,
    padding: PaddingValues,
    content: @Composable BoxScope.() -> Unit
) {
    val ld = LocalLayoutDirection.current
    val animatedWidth by animateDpAsState(
        targetValue = size.width,
        label = "ClippedBrushBox width animation",
        animationSpec = spring(stiffness = springStiffness))
    val animatedHeight by animateDpAsState(
        targetValue = size.height,
        label = "ClippedBrushBox height animation",
        animationSpec = spring(stiffness = springStiffness))

    BoxWithConstraints(Modifier
        .fillMaxSize()
        .padding(padding)
        .drawBehind {
            val xAlignment = alignment.horizontalBias / 2f + 0.5f
            val yAlignment = alignment.verticalBias / 2f + 0.5f
            val boxSize = Size(animatedWidth.toPx(), animatedHeight.toPx())
            val xOffset = (this.size.width - boxSize.width) * xAlignment
            val yOffset = (this.size.height - boxSize.height) * yAlignment
            val offset = Offset(
                x = if (ld.isLtr) xOffset
                    else size.width.toPx() - xOffset,
                y = yOffset)
            val radius = CornerRadius(cornerRadius.toPx())
            drawRoundRect(brush, offset, boxSize, radius)
        }
    ) {
        // For some reason the Box was allowing gestures to go through
        // it. This empty pointerInput modifier prevents this.
        Box(modifier = modifier
                .align(alignment)
                .size(animatedWidth, animatedHeight)
                .pointerInput(Unit) {},
            content = content)
    }
}

/**
 * Return a [TransformOrigin] that corresponds to the visual center of an
 * instance of [ClippedBrushBox]. Because [ClippedBrushBox] fills the max
 * available size before clipping itself down, effects that rely on a
 * [TransformOrigin], e.g. scaling effects, will not work as expected on
 * a [ClippedBrushBox] unless the transform origin for the effect is set
 * to the one returned by this method.
 */
@Composable
fun BoxWithConstraintsScope.rememberClippedBrushBoxTransformOrigin(
    alignment: BiasAlignment,
    padding: PaddingValues,
    dpSize: DpSize,
): TransformOrigin {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    return remember(dpSize, alignment, padding) {
        val size = with(density) { dpSize.toSize() }
        val maxWidth = constraints.maxWidth.toFloat()
        val maxHeight = constraints.maxHeight.toFloat()

        val xAlignment = alignment.horizontalBias / 2f + 0.5f
        val yAlignment = alignment.verticalBias / 2f + 0.5f
        val alignmentOffset = Offset(
            x = if (layoutDirection.isLtr)
                    (maxWidth - size.width) * xAlignment
                else size.width - (maxWidth - size.width) * xAlignment,
            y = (maxHeight - size.height) * yAlignment)

        val halfSizeOffset = Offset(size.width / 2, size.height / 2)
        val bottomPadding = with(density) { padding.calculateBottomPadding().toPx() }
        val paddingOffset = Offset(0f, bottomPadding)
        val totalOffset = alignmentOffset + halfSizeOffset - paddingOffset
        TransformOrigin(totalOffset.x / maxWidth, totalOffset.y / maxHeight)
    }
}