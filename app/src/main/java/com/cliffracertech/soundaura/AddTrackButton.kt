/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.database.sqlite.SQLiteConstraintException
import androidx.compose.animation.*
import androidx.compose.animation.core.AnimationConstants.DefaultDurationMillis
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A layout that acts as an implementation of the speed dial floating action button concept.
 *
 * SpeedDialLayout displays only its @param content when @param expanded ==
 * false, but will animate the appearance of each of its children (usually
 * a button) above the main content in order when expanded == true. The
 * children will appear above the main content, so a bottom alignment for
 * the SpeedDialLayout in its parent is recommended.
 *
 * @param expanded Whether or not the child contents will be displayed.
 * @param onBoundsChange The callback that will be invoked when the bounds of the
 *     layout change. These bounds can optionally be used to detect whether taps
 *     fall outside the bounds of the layout, useful when, e.g., an automatic
 *     collapsing of the layout when a click occurs outside its bounds is desired.
 * @param childAlignment The alignment that will be used for the child content.
 *     The default value is Alignment.End.
 * @param childAppearanceDuration The duration for a given child's appearance animation.
 * @param totalDuration The total duration over which all children will appear. If
 *     longer than childAppearanceDuration, the appearance or disappearance of
 *     children will be staggered according to their position in the list of children.
 * @param children A list of each piece of child content that will appear
 *     when the layout is expanded.
 * @param content The content that will be displayed even when the layout is collapsed.
 */
@Composable fun SpeedDialLayout(
    expanded: Boolean,
    onBoundsChange: (Rect) -> Unit = {},
    childAlignment: Alignment.Horizontal = Alignment.End,
    childAppearanceDuration: Int = DefaultDurationMillis,
    totalDuration: Int = DefaultDurationMillis,
    children: List<@Composable () -> Unit>,
    content: @Composable () -> Unit,
) = Column(
     modifier = Modifier.onGloballyPositioned {
        onBoundsChange(it.boundsInRoot())
    }, verticalArrangement = Arrangement.spacedBy(8.dp),
    horizontalAlignment = childAlignment
) {
    require(totalDuration >= childAppearanceDuration)
    val delayFactor = (totalDuration - childAppearanceDuration) / children.size
    children.forEachIndexed { index, child ->
        val exitDelay = index * delayFactor
        val enterDelay = children.lastIndex * delayFactor - exitDelay
        AnimatedVisibility(expanded,
            enter = fadeIn(tween(childAppearanceDuration, enterDelay)) +
                    scaleIn(overshootTweenSpec(childAppearanceDuration, enterDelay), initialScale = 0.8f),
            exit = fadeOut(tween(childAppearanceDuration, exitDelay)) +
                   scaleOut(tween(childAppearanceDuration, exitDelay), targetScale = 0.8f)
        ) { child() }
    }
    content()
}

/**
 * An implementation of SpeedDialLayout whose main content is a floating action
 * button with an add icon. When clicked, the button will animate to display a
 * close icon instead and will display buttons for adding a file from the
 * internet via download or through a local file .
 *
 * @param expanded Whether the add download or add local file buttons will be displayed.
 * @param onBoundsChange The callback that will be invoked when the bounds of the
 *     layout change. These bounds can optionally be used to detect whether taps
 *     fall outside the bounds of the layout, useful when, e.g., an automatic
 *     collapsing of the layout when a click occurs outside its bounds is desired.
 * @param onClick The callback that will be invoked when the main content FAB is clicked.
 * @param onAddDownloadClick The callback that will be invoked when the download button is clicked.
 * @param onAddLocalFileClick The callback that will be invoked when the add local file button is clicked.
 */
@Composable fun AddTrackButton(
    expanded: Boolean,
    onBoundsChange: (Rect) -> Unit = {},
    onClick: () -> Unit = {},
    onAddDownloadClick: () -> Unit = {},
    onAddLocalFileClick: () -> Unit = {},
) = SpeedDialLayout(
    expanded = expanded,
    onBoundsChange = onBoundsChange,
    childAppearanceDuration = 275,
    totalDuration = 400,
    children = listOf(
        // The button elevations will be set to 0 for the time being
        // to work around an AnimatedVisibility bug where the button's
        // shadows are clipped.
        { ExtendedFloatingActionButton(
            text = { Text("download") },
            onClick = onAddDownloadClick,
            icon = { Icon(Icons.Default.Add, null) },
            backgroundColor = MaterialTheme.colors.primaryVariant,
            contentColor = MaterialTheme.colors.onPrimary,
//            elevation = FloatingActionButtonDefaults.elevation(6.dp, 3.dp))
            elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp))
        }, { ExtendedFloatingActionButton(
            text = { Text("local file") },
            onClick = onAddLocalFileClick,
            icon = { Icon(Icons.Default.Add, null) },
            backgroundColor = MaterialTheme.colors.primaryVariant,
            contentColor = MaterialTheme.colors.onPrimary,
//            elevation = FloatingActionButtonDefaults.elevation(6.dp, 3.dp))
            elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp))
        })
) { FloatingActionButton(
    onClick = onClick,
    backgroundColor = MaterialTheme.colors.primaryVariant,
    contentColor = MaterialTheme.colors.onPrimary,
    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
//    elevation = FloatingActionButtonDefaults.elevation(6.dp, 3.dp)
) {
    // The two angles are chosen between so that the icon always appears
    // to rotate clockwise, instead of clockwise and then counterclockwise.
    val angle1 by animateFloatAsState(if (expanded) 45f else 0f, overshootTweenSpec())
    val angle2 by animateFloatAsState(if (expanded) 45f else 90f, overshootTweenSpec())
    val angle = if (expanded) angle1 else angle2
    val description = if (expanded) stringResource(R.string.add_button_expanded_description)
                      else          stringResource(R.string.add_button_description)
    Icon(Icons.Default.Add, description, Modifier.rotate(angle))
}}

@Preview @Composable
fun AddTrackButtonPreview() = AddTrackButton(expanded = true)

@HiltViewModel
class AddTrackButtonViewModel @Inject constructor(
    private val trackDao: TrackDao,
    private val messageHandler: MessageHandler
) : ViewModel() {

    private var _expanded by mutableStateOf(false)
    val expanded get() = _expanded

    private var _showingAddLocalFileDialog by mutableStateOf(false)
    val showingAddLocalFileDialog get() = _showingAddLocalFileDialog

    private var _showingDownloadFileDialog by mutableStateOf(false)
    val showingDownloadFileDialog get() = _showingDownloadFileDialog

    private var lastBounds = Rect(0f, 0f, 0f, 0f)

    fun onClick() { _expanded = !expanded }

    fun onBoundsChange(bounds: Rect) { lastBounds = bounds }

    fun onGlobalClick(pos: Offset) {
        if (!lastBounds.contains(pos))
            _expanded = false
    }

    private fun addTrack(track: Track) {
        viewModelScope.launch {
            try { trackDao.insert(track) }
            catch(e: SQLiteConstraintException) {
                val stringResId = R.string.track_already_exists_error_message
                messageHandler.postMessage(StringResource(stringResId))
            }
        }
    }

    fun onDownloadFileButtonClick() {
        _expanded = false
        _showingDownloadFileDialog = true
    }

    fun onDownloadFileDialogDismiss() {
        _showingDownloadFileDialog = false
    }

    fun onDownloadFileDialogConfirm(track: Track) {
        onDownloadFileDialogDismiss()
        addTrack(track)
    }

    fun onAddLocalFileButtonClick() {
        _expanded = false
        _showingAddLocalFileDialog = true
    }

    fun onAddLocalFileDialogDismiss() {
        _showingAddLocalFileDialog = false
    }

    fun onAddLocalFileDialogConfirm(track: Track) {
        onAddLocalFileDialogDismiss()
        addTrack(track)
    }
}

/** Compose an AddTrackButton with state provided by an instance of AddTrackButtonViewModel. */
@Composable fun StatefulAddTrackButton() {
    val viewModel: AddTrackButtonViewModel = viewModel()

    AddTrackButton(
        expanded = viewModel.expanded,
        onBoundsChange = viewModel::onBoundsChange,
        onClick = viewModel::onClick,
        onAddDownloadClick = viewModel::onDownloadFileButtonClick,
        onAddLocalFileClick = viewModel::onAddLocalFileButtonClick)

    //if (viewModel.showingDownloadFileDialog)

    if (viewModel.showingAddLocalFileDialog)
        AddTrackFromLocalFileDialog(
            onDismissRequest = viewModel::onAddLocalFileDialogDismiss,
            onConfirmRequest = viewModel::onAddLocalFileDialogConfirm)
}