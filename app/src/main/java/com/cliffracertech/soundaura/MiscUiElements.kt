/* This file is part of SoundAura, which is released under the terms of the Apache
 * License 2.0. See license.md in the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.compose.animation.*
import androidx.compose.animation.core.AnimationConstants.DefaultDurationMillis
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

fun Modifier.minTouchTargetSize() =
    sizeIn(minWidth = 48.dp, minHeight = 48.dp)

/** Return a radio button icon with its checked state set according to the value of @param checked. */
@Composable fun RadioButton(checked: Boolean, modifier: Modifier) {
    val vector = if (checked) Icons.Default.RadioButtonChecked
                 else         Icons.Default.RadioButtonUnchecked
    val desc = stringResource(if (checked) R.string.checked
                              else         R.string.unchecked)
    Icon(vector, desc, modifier)
}

/**
 * A button that alternates between an empty circle with a plus icon, and
 * a filled circle with a minus icon depending on the parameter checked.
 *
 * @param added The added/removed state of the item the button is
 *     representing. If added == true, the button will display a minus
 *     icon. If added == false, a plus icon will be displayed instead.
 * @param contentDescription The content description of the button.
 * @param backgroundColor The color of the background that the button
 *     is being displayed on. This is used for the inner plus icon
 *     when added == true and the background of the button is filled.
 * @param tint The tint that will be used for the button.
 * @param onClick The callback that will be invoked when the button is clicked.
 */
@Composable fun AddRemoveButton(
    added: Boolean,
    contentDescription: String? = null,
    backgroundColor: Color = MaterialTheme.colors.background,
    tint: Color = LocalContentColor.current,
    onClick: () -> Unit
) = IconButton(onClick) {
    // Circle background
    // The combination of the larger tinted circle and the smaller
    // background-tinted circle creates the effect of a circle that
    // animates between filled and outlined.
    Box(Modifier.size(24.dp).background(tint, CircleShape))
    AnimatedVisibility(
        visible = !added,
        enter = scaleIn(overshootTweenSpec()),
        exit = scaleOut(anticipateTweenSpec()),
    ) {
        Box(Modifier.size(20.dp).background(backgroundColor, CircleShape))
    }

    // Plus / minus icon
    // The combination of the two angles allows the icon to always
    // rotate clockwise, instead of alternating between clockwise
    // and counterclockwise.
    val cwAngle by animateFloatAsState(if (added) 0f else 90f)
    val ccwAngle by animateFloatAsState(if (added) 180f else 90f)
    val angle = if (added) ccwAngle else cwAngle

    val iconTint by animateColorAsState(
        if (added) backgroundColor else tint)
    val minusIcon = painterResource(R.drawable.minus)

    // One minus icon always appears horizontally, while the other
    // can rotate between 0 and 90 degrees so that both minus icons
    // together appear as a plus icon.
    Icon(minusIcon, null, Modifier.rotate(2 * angle), tint)
    Icon(minusIcon, contentDescription, Modifier.rotate(angle), iconTint)
}


@Composable fun PlayPauseIcon(
    playing: Boolean,
    contentDescription: String =
        if (playing) stringResource(R.string.pause)
        else         stringResource(R.string.play),
    tint: Color,
) {
    val playToPause = AnimatedImageVector.animatedVectorResource(R.drawable.play_to_pause)
    val playToPausePainter = rememberAnimatedVectorPainter(playToPause, atEnd = playing)
    val pauseToPlay = AnimatedImageVector.animatedVectorResource(R.drawable.pause_to_play)
    val pauseToPlayPainter = rememberAnimatedVectorPainter(pauseToPlay, atEnd = !playing)
    Icon(painter = if (playing) playToPausePainter
                   else         pauseToPlayPainter,
         contentDescription = contentDescription,
         tint = tint)
}

/** A simple back arrow IconButton for when only the onClick needs changed. */
@Composable fun BackButton(onClick: () -> Unit) = IconButton(onClick) {
    Icon(Icons.Default.ArrowBack, stringResource(R.string.back))
}

/** A simple settings IconButton for when only the onClick needs changed. */
@Composable fun SettingsButton(onClick: () -> Unit) = IconButton(onClick) {
    Icon(Icons.Default.Settings, stringResource(R.string.settings))
}

@Composable fun <T>overshootTweenSpec(
    duration: Int = DefaultDurationMillis,
    delay: Int = 0,
) = tween<T>(duration, delay) {
    val t = it - 1
    t * t * (3 * t + 2) + 1
}

@Composable fun <T>anticipateTweenSpec(
    duration: Int = DefaultDurationMillis,
    delay: Int = 0,
) = tween<T>(duration, delay) {
    it * it * (3 * it - 2)
}

/**
 * An AnimatedContent with predefined slide left/right transitions.
 * @param targetState The key that will cause a change in the SlideAnimatedContent's
 *     content when its value changes.
 * @param modifier The modifier that will be applied to the content.
 * @param leftToRight Whether the existing content should be slid off screen
 *     to the left with the new content sliding in from the right, or the
 *     other way around.
 * @param content The composable that itself composes the contents depending
 *     on the value of targetState, e.g. if (targetState) A() else B().
 */
@Composable fun<S> SlideAnimatedContent(
    targetState: S,
    modifier: Modifier = Modifier,
    leftToRight: Boolean,
    content: @Composable (AnimatedVisibilityScope.(S) -> Unit)
) {
    val transition = remember(leftToRight) {
        val enterOffset = { size: Int -> size / if (leftToRight) 1 else -1 }
        val exitOffset = { size: Int -> size / if (leftToRight) -4 else 4 }
        slideInHorizontally(initialOffsetX = enterOffset) with
        slideOutHorizontally(targetOffsetX = exitOffset)
    }
    AnimatedContent(targetState, modifier, { transition }, content = content)
}

private val pointCorner = CornerSize(0.dp)

/** Return a shape that matches just the bottomStart corner
 * of the receiver shape, with otherwise sharp corners.*/
@Composable fun CornerBasedShape.bottomStartShape() =
    RoundedCornerShape(
        topStart = pointCorner,
        topEnd = pointCorner,
        bottomEnd = pointCorner,
        bottomStart = bottomStart)

/** Return a shape that matches just the bottomEnd corner
 * of the receiver shape, with otherwise sharp corners.*/
@Composable fun CornerBasedShape.bottomEndShape() =
    RoundedCornerShape(
        topStart = pointCorner,
        topEnd = pointCorner,
        bottomStart = pointCorner,
        bottomEnd = bottomEnd)

/** Return a shape that matches the bottom edge
 * of the receiver shape, with sharp top corners.*/
@Composable fun CornerBasedShape.bottomShape() =
    RoundedCornerShape(
        topStart = pointCorner,
        topEnd = pointCorner,
        bottomStart = bottomStart,
        bottomEnd = bottomEnd)

@Composable fun VerticalDivider() =
    Box(Modifier.fillMaxHeight().width(1.dp)
        .background(MaterialTheme.colors.onSurface.copy(alpha = 0.12f)))