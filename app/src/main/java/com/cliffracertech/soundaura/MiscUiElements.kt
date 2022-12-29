/* This file is part of SoundAura, which is released under the terms of the Apache
 * License 2.0. See license.md in the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.compose.animation.*
import androidx.compose.animation.core.AnimationConstants.DefaultDurationMillis
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

fun Modifier.minTouchTargetSize() =
    sizeIn(minWidth = 48.dp, minHeight = 48.dp)

/** Compose a radio button icon. */
@Composable fun RadioButton(
    checked: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colors.primary
) {
    val vector = if (checked) Icons.Default.RadioButtonChecked
                 else         Icons.Default.RadioButtonUnchecked
    val desc = stringResource(if (checked) R.string.checked
                              else         R.string.unchecked)
    Icon(vector, desc, modifier, tint)
}

/**
 * An [IconButton] that alternates between an empty circle with a plus icon,
 * and a filled circle with a minus icon depending on the parameter checked.
 *
 * @param added The added/removed state of the item the button is
 *     representing. If added == true, the button will display a minus
 *     icon. If added == false, a plus icon will be displayed instead.
 * @param contentDescription The content description of the button.
 * @param backgroundColor The [Color] of the background that the button
 *     is being displayed on. This is used for the inner plus icon
 *     when [added] == true and the background of the button is filled.
 * @param tint The [Color] that will be used for the button.
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
        enter = scaleIn(overshootTween()),
        exit = scaleOut(anticipateTween()),
    ) {
        Box(Modifier.size(20.dp).background(backgroundColor, CircleShape))
    }

    // Plus / minus icon
    // The combination of the two angles allows the icon to always
    // rotate clockwise, instead of alternating between clockwise
    // and counterclockwise.
    val angleMod by animateFloatAsState(if (added) 90f else 0f)
    val angle = if (added) 90f + angleMod
                else       90f - angleMod

    val iconTint by animateColorAsState(
        if (added) backgroundColor else tint)
    val minusIcon = painterResource(R.drawable.minus)

    // One minus icon always appears horizontally, while the other
    // can rotate between 0 and 90 degrees so that both minus icons
    // together appear as a plus icon.
    Icon(minusIcon, null, Modifier.rotate(2 * angle), tint)
    Icon(minusIcon, contentDescription, Modifier.rotate(angle), iconTint)
}

/**
 * A two-state animated play/pause icon.
 *
 * @param isPlaying The playing state of the media that the icon is
 *     representing. The icon will be a pause icon when [isPlaying]
 *     is true, or a play icon when [isPlaying] is false.
 * @param contentDescription The contentDescription of the icon
 * @param tint The tint to use for the icon
 */
@Composable fun PlayPauseIcon(
    isPlaying: Boolean,
    contentDescription: String,
    tint: Color = LocalContentColor.current
) {
    val playToPause = AnimatedImageVector.animatedVectorResource(R.drawable.play_to_pause)
    val playToPausePainter = rememberAnimatedVectorPainter(playToPause, atEnd = isPlaying)
    val pauseToPlay = AnimatedImageVector.animatedVectorResource(R.drawable.pause_to_play)
    val pauseToPlayPainter = rememberAnimatedVectorPainter(pauseToPlay, atEnd = !isPlaying)
    val painter = if (isPlaying) playToPausePainter
                  else           pauseToPlayPainter
    Icon(painter, contentDescription, tint = tint)
}

/** A simple back arrow [IconButton] for when only the onClick needs changed. */
@Composable fun BackButton(onClick: () -> Unit) = IconButton(onClick) {
    Icon(Icons.Default.ArrowBack, stringResource(R.string.back))
}

/** A simple settings [IconButton] for when only the onClick needs changed. */
@Composable fun SettingsButton(onClick: () -> Unit) = IconButton(onClick) {
    Icon(Icons.Default.Settings, stringResource(R.string.settings))
}

@Composable fun <T>overshootTween(
    duration: Int = DefaultDurationMillis,
    delay: Int = 0,
) = tween<T>(duration, delay) {
    val t = it - 1
    t * t * (3 * t + 2) + 1
}

@Composable fun <T>anticipateTween(
    duration: Int = DefaultDurationMillis,
    delay: Int = 0,
) = tween<T>(duration, delay) {
    it * it * (3 * it - 2)
}

/**
 * An [AnimatedContent] with predefined slide left/right transitions.
 * @param targetState The key that will cause a change in the SlideAnimatedContent's
 *     content when its value changes.
 * @param modifier The [Modifier] that will be applied to the content.
 * @param leftToRight Whether the existing content should slide off screen
 *     to the left with the new content sliding in from the right, or the
 *     other way around.
 * @param content The composable that itself composes the contents depending
 *     on the value of [targetState], e.g. if (targetState) A() else B().
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

/** Add a vertical divider to the [Row]. The divider will take
 * up a fraction of the [Row]'s height equal to [heightFraction]. */
@Composable fun RowScope.VerticalDivider(
    modifier: Modifier = Modifier,
    heightFraction: Float = 1f,
) = Box(modifier
    .width((1.5).dp).fillMaxHeight(heightFraction)
    .align(Alignment.CenterVertically)
    .background(LocalContentColor.current.copy(alpha = 0.2f)))

/** Add a horizontal divider to the [Column]. The divider will take
 * up a fraction of the [Column]'s width equal to [widthFraction]. */
@Composable fun ColumnScope.HorizontalDivider(
    modifier: Modifier = Modifier,
    widthFraction: Float = 1f,
) = Box(modifier
    .fillMaxWidth(widthFraction).height((1.5).dp)
    .align(Alignment.CenterHorizontally)
    .background(LocalContentColor.current.copy(alpha = 0.2f)))

@Composable fun Divider(
    orientation: Orientation,
    modifier: Modifier = Modifier,
    sizeFraction: Float = 1f,
) = Box(modifier
    .background(LocalContentColor.current.copy(alpha = 0.2f))
    .then(if (orientation.isHorizontal)
              Modifier.width((1.5).dp).fillMaxHeight(sizeFraction)
          else Modifier.fillMaxWidth(sizeFraction).height((1.5).dp)))

/**
 * Compose a bulleted list of [String]s.
 *
 * @param items The list of [String]s to display in bulleted form.
 * @param modifier The [Modifier] to use for the entire list.
 */
@Composable fun BulletedList(
    items: List<String>,
    modifier: Modifier = Modifier,
) = Column(modifier, Arrangement.spacedBy(8.dp)) {
    for (item in items) {
        Row {
            Text("\u2022")
            Spacer(Modifier.width(12.dp))
            Text(item)
        }
    }
}

/**
 * Compose a [Text] containing a clickable link.
 *
 * @param modifier The [Modifier] to use for the entire text.
 * @param linkText The text that will be clickable.
 * @param completeText The entire text that will be displayed. This must
 *     contain the linkText, or else the link will not work properly.
 * @param onLinkClick The callback that will be invoked when the link is clicked.
 */
@Composable fun TextWithClickableLink(
    modifier: Modifier = Modifier,
    linkText: String,
    completeText: String,
    onLinkClick: () -> Unit
) {
    val linkTextStartIndex = completeText.indexOf(linkText)
    val linkTextLastIndex = linkTextStartIndex + linkText.length
    val linkifiedText = buildAnnotatedString {
        // ClickableText seems to not follow the local text style by default
        pushStyle(SpanStyle(color = LocalContentColor.current,
                            fontSize = LocalTextStyle.current.fontSize))
        append(completeText)
        val urlStyle = SpanStyle(color = MaterialTheme.colors.primary,
                                 textDecoration = TextDecoration.Underline)
        addStyle(urlStyle, linkTextStartIndex, linkTextLastIndex)
    }
    ClickableText(
        text = linkifiedText,
        modifier = modifier.alpha(LocalContentAlpha.current),
        style = MaterialTheme.typography.body1
    ) {
        if (it in linkTextStartIndex..linkTextLastIndex)
            onLinkClick()
    }
}

/**
 * Display a single line [Text] that, when width restrictions prevent the
 * whole line from being visible, automatically scrolls to its end, springs
 * back to its beginning, and repeats this cycle indefinitely. The parameters
 * mirror those of [Text], except that the maxLines and the softWrap parameters
 * are unable to be changed, and the additional [maxWidth] parameter. If the
 * available horizontal space is known at the composition site, this can be
 * passed in as the value of [maxWidth] to prevent [MarqueeText] from
 * needing to calculate this itself.
 */
@Composable fun MarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxWidth: Dp? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current,
) {
    val content = @Composable { maxWidth: Dp ->
        val scrollState = rememberScrollState()
        var shouldAnimate by remember { mutableStateOf(true) }
        var animationDuration by remember { mutableStateOf(0) }
        if (animationDuration > 0)
            LaunchedEffect(shouldAnimate) {
                scrollState.animateScrollTo(scrollState.maxValue,
                    tween(animationDuration, 2000, LinearEasing))
                delay(2000)
                scrollState.animateScrollTo(0)
                shouldAnimate = !shouldAnimate
            }
        val density = LocalDensity.current
        Text(text, Modifier.horizontalScroll(scrollState, false),
            color, fontSize, fontStyle, fontWeight, fontFamily, letterSpacing,
            textDecoration, textAlign, lineHeight, overflow, maxLines = 1,
            onTextLayout = {
                onTextLayout(it)
                val overflowAmount = it.size.width -
                    with(density) { maxWidth.roundToPx() }
                animationDuration = overflowAmount.coerceAtLeast(0) * 10
            }, style = style)
    }
    if (maxWidth != null)
        Box(modifier, Alignment.Center) {
            content(maxWidth)
        }
    else BoxWithConstraints(modifier, Alignment.Center) {
        content(this.maxWidth)
    }
}

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
    content: @Composable (divider: @Composable () -> Unit) -> Unit
) {
    val divider = @Composable {
        Divider(orientation, sizeFraction = 0.8f)
    }
    if (orientation.isHorizontal)
        Row(modifier, verticalAlignment = Alignment.CenterVertically) { content(divider) }
    else Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) { content(divider) }
}