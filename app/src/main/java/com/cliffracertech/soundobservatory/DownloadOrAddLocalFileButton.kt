/* This file is part of SoundObservatory, which is released under the
 * Apache License 2.0. See license.md in the project's root directory
 * or use an internet search engine to see the full license. */
package com.cliffracertech.soundobservatory

import androidx.compose.animation.*
import androidx.compose.animation.core.AnimationConstants.DefaultDurationMillis
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * A layout that acts as an implementation of the speed dial floating action button concept.
 *
 * SpeedDialLayout displays only its @param content when @param expanded ==
 * false, but will animate the appearance of each piece of child content
 * (usually a button) above the main content in order when expanded == true.
 * The child contents will appear above the main content, so a bottom
 * alignment for the SpeedDialLayout in its parent is recommended.
 *
 * @param expanded Whether or not the child contents will be displayed.
 * @param modifier The modifier for the parent layout
 * @param childAlignment The alignment that will be used for the child content.
 *     The default value if Alignment.End
 * @param childAppearanceDuration The duration for a given child's appearance animation.
 * @param totalDuration The total duration over which all children will appear. If
 *     longer than childAppearanceDuration, the children will have an appearance or
 *     disappearance delay according to their position in the list of children.
 * @param childContent A list of each piece of child content that will appear
 *     when the layout is expanded.
 * @param content The content that will be displayed when the layout is collapsed.
 */
@Composable fun SpeedDialLayout(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    childAlignment: Alignment.Horizontal = Alignment.End,
    childAppearanceDuration: Int = DefaultDurationMillis,
    totalDuration: Int = DefaultDurationMillis,
    childContent: List<@Composable () -> Unit>,
    content: @Composable () -> Unit,
) = Column(modifier, Arrangement.spacedBy(8.dp), childAlignment) {

    val delayFactor = (totalDuration - childAppearanceDuration) / childContent.size
    childContent.forEachIndexed { index, button ->
        val exitDelay = index * delayFactor
        val enterDelay = childContent.lastIndex * delayFactor - exitDelay
        AnimatedVisibility(expanded,
            enter = fadeIn(tween(childAppearanceDuration, enterDelay)) +
                    scaleIn(overshootTweenSpec(childAppearanceDuration, enterDelay), initialScale = 0.8f),
            exit = fadeOut(tween(childAppearanceDuration, exitDelay)) +
                   scaleOut(tween(childAppearanceDuration, exitDelay), targetScale = 0.8f)
        ) { button() }
    }
    content()
}


private val overshootEasing = Easing {
    val t = it - 1
    t * t * (3 * t + 2) + 1
}

@Composable fun <T>overshootTweenSpec(duration: Int = DefaultDurationMillis, delay: Int = 0) =
    tween<T>(duration, delay, overshootEasing)

/**
 * An implementation of SpeedDialLayout whose main content is a floating action
 * button with an add icon. When clicked, the button will animate to display a
 * close icon instead and will display buttons for adding a file from the
 * internet via download or through a local file .
 *
 * @param expanded Whether the add download or add local file buttons will be displayed.
 * @param onClick The callback that will be invoked when the main content FAB is clicked.
 * @param onAddDownloadClick The callback that will be invoked when the download button is clicked.
 * @param onAddLocalFileClick The callback that will be invoked when the add local file button is clicked.
 * @param modifier The modifier that will be used for the surrounding layout.
 */
@Composable fun DownloadOrAddLocalFileButton(
    expanded: Boolean,
    onClick: () -> Unit,
    onAddDownloadClick: () -> Unit,
    onAddLocalFileClick: () -> Unit,
    modifier: Modifier = Modifier,
) = SpeedDialLayout(
    expanded = expanded,
    modifier = modifier,
    childAppearanceDuration = 275,
    totalDuration = 400,
    childContent = listOf(
        { ExtendedFloatingActionButton(
            text = { Text("download") },
            onClick = onAddDownloadClick,
            icon = { Icon(Icons.Default.Add, null) },
            backgroundColor = MaterialTheme.colors.primaryVariant,
            contentColor = MaterialTheme.colors.onPrimary,
            elevation = FloatingActionButtonDefaults.elevation(6.dp, 3.dp))
        }, { ExtendedFloatingActionButton(
            text = { Text("local file") },
            onClick = onAddLocalFileClick,
            icon = { Icon(Icons.Default.Add, null) },
            backgroundColor = MaterialTheme.colors.primaryVariant,
            contentColor = MaterialTheme.colors.onPrimary,
            elevation = FloatingActionButtonDefaults.elevation(6.dp, 3.dp))
        })
) { FloatingActionButton(
    onClick = onClick,
    backgroundColor = MaterialTheme.colors.primaryVariant,
    contentColor = MaterialTheme.colors.onPrimary,
    elevation = FloatingActionButtonDefaults.elevation(6.dp, 3.dp)
) {
    // The two angles are chosen between so that the icon always appears
    // to rotate clockwise, instead of clockwise and then counterclockwise.
    val angle1 by animateFloatAsState(if (expanded) 45f else 0f, tween())
    val angle2 by animateFloatAsState(if (expanded) 45f else 90f, tween())
    val angle = if (expanded) angle1 else angle2
    val description = if (expanded) stringResource(R.string.add_button_expanded_description)
                      else          stringResource(R.string.add_button_description)
    Icon(Icons.Default.Add, description, Modifier.rotate(angle))
}}

@Preview @Composable fun AddTrackButtonPreview() = DownloadOrAddLocalFileButton(true, {}, {}, {})