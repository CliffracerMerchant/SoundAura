/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.*
import androidx.compose.animation.core.AnimationConstants.DefaultDurationMillis
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
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
 * @param childAlignment The horizontal alignment that will be used for the child
 *     content. The default value is Alignment.End.
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

// The button elevations will be set to 0 for the time being
// to work around an AnimatedVisibility bug where the button's
// shadows are clipped.
@Composable private fun AddTrackButtonChild(
    @StringRes textResId: Int,
    onClick: () -> Unit,
) = ExtendedFloatingActionButton(
    onClick = onClick,
    text = { Text(stringResource(textResId)) },
    icon = { Icon(Icons.Default.Add, null) },
    backgroundColor = MaterialTheme.colors.secondary,
    contentColor = MaterialTheme.colors.onPrimary,
//  elevation = FloatingActionButtonDefaults.elevation(8.dp, 4.dp))
    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp))

/** Compose an icon that rotates between a plus state and a close
 * state with an animation, according to the value of the parameter
 * showAsClose. The icon will always appear to rotate clockwise.*/
@Composable private fun PlusCloseIcon(showAsClose: Boolean) {
    // The two angles are chosen between so that the icon always appears
    // to rotate clockwise, instead of clockwise and then counterclockwise.
    val angle1 by animateFloatAsState(
        targetValue = if (showAsClose) 45f else 0f,
        animationSpec = overshootTweenSpec())
    val angle2 by animateFloatAsState(
        targetValue = if (showAsClose) 45f else 90f,
        animationSpec = overshootTweenSpec())
    val angle = if (showAsClose) angle1
                else             angle2
    val description = stringResource(
        if (showAsClose) R.string.add_button_expanded_description
        else             R.string.add_button_description)
    Icon(Icons.Default.Add, description, Modifier.rotate(angle))
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
 * @param onAddLocalFilesClick The callback that will be invoked when the add local files button is clicked.
 */
@Composable fun AddTrackButton(
    expanded: Boolean,
    onBoundsChange: (Rect) -> Unit = {},
    onClick: () -> Unit = {},
    onAddDownloadClick: () -> Unit = {},
    onAddLocalFilesClick: () -> Unit = {},
) = SpeedDialLayout(
    expanded = expanded,
    onBoundsChange = onBoundsChange,
    childAppearanceDuration = 275,
    totalDuration = 400,
    children = listOf(
        { AddTrackButtonChild(R.string.download_description, onAddDownloadClick) },
        { AddTrackButtonChild(R.string.local_file_description, onAddLocalFilesClick) },
    ), content = { FloatingActionButton(
        onClick = onClick,
        backgroundColor = MaterialTheme.colors.secondary,
        contentColor = MaterialTheme.colors.onPrimary,
        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp),
        content = { PlusCloseIcon(showAsClose = expanded) }
//    elevation = FloatingActionButtonDefaults.elevation(8.dp, 4.dp)
    )})

@Preview @Composable
fun AddTrackButtonPreview() = AddTrackButton(expanded = true)

// The stored context object here is the application
// context, and therefore does not present a problem.
@HiltViewModel @SuppressLint("StaticFieldLeak")
class AddTrackButtonViewModel(
    @ApplicationContext
    private val context: Context,
    private val trackDao: TrackDao,
    private val messageHandler: MessageHandler,
    coroutineScope: CoroutineScope? = null
) : ViewModel() {

    @Inject constructor(
        @ApplicationContext
        context: Context,
        trackDao: TrackDao,
        messageHandler: MessageHandler
    ) : this(context, trackDao, messageHandler, null)

    private val scope = coroutineScope ?: viewModelScope

    var expanded by mutableStateOf(false)
        private set

    private var lastBounds = Rect(0f, 0f, 0f, 0f)

    fun onClick() { expanded = !expanded }

    fun onBoundsChange(bounds: Rect) { lastBounds = bounds }

    fun onGlobalClick(pos: Offset) {
        if (!lastBounds.contains(pos))
            expanded = false
    }


    var showingDownloadFileDialog by mutableStateOf(false)
        private set

    fun onDownloadFileButtonClick() {
        expanded = false
//        showingDownloadFileDialog = true
        messageHandler.postMessage(StringResource(R.string.download_button_message))
    }

    fun onDownloadFileDialogDismiss() {
        showingDownloadFileDialog = false
    }

    fun onDownloadFileDialogConfirm(track: Track) {
        onDownloadFileDialogDismiss()
        scope.launch {
            try { trackDao.insert(track) }
            catch(e: SQLiteConstraintException) {
                val stringResId = R.string.track_already_exists_error_message
                messageHandler.postMessage(StringResource(stringResId))
            }
        }
    }

    var showingAddLocalFilesDialog by mutableStateOf(false)
        private set

    fun onAddLocalFilesButtonClick() {
        expanded = false
        showingAddLocalFilesDialog = true
    }

    fun onAddLocalFilesDialogDismiss() {
        showingAddLocalFilesDialog = false
    }

    fun onAddLocalFilesDialogConfirm(trackUris: List<Uri>, trackNames: List<String>) {
        onAddLocalFilesDialogDismiss()
        scope.launch {
            val newTracks = List(trackUris.size) {
                val name = trackNames.getOrNull(it) ?: ""
                Track(trackUris[it].toString(), name)
            }
            val results = trackDao.insert(newTracks)
            val insertedTracks = mutableListOf<Track>()
            val failedTracks = mutableListOf<Track>()
            results.forEachIndexed { index, insertedId ->
                val track = newTracks[index]
                if (insertedId >= 0)
                    insertedTracks.add(track)
                else failedTracks.add(track)
            }
            if (failedTracks.isNotEmpty())
                messageHandler.postMessage(
                    if (newTracks.size == 1)
                        StringResource(R.string.track_already_exists_error_message)
                    else StringResource(R.string.some_tracks_already_exist_error_message,
                                        failedTracks.size))

            val persistedPermissionAllowance =
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) 128 else 512
            var numPersistedPermissions = context.contentResolver.persistedUriPermissions.size

            failedTracks.clear()
            insertedTracks.forEach { track ->
                if (numPersistedPermissions < persistedPermissionAllowance) {
                    val trackUri = Uri.parse(track.uriString)
                    context.contentResolver.takePersistableUriPermission(
                        trackUri, FLAG_GRANT_READ_URI_PERMISSION)
                    numPersistedPermissions++
                } else failedTracks.add(track)
            }
            if (failedTracks.isNotEmpty()) {
                messageHandler.postMessage(
                    StringResource(
                        string = null,
                        stringResId = R.string.over_file_permission_limit_warning,
                        args = arrayListOf(failedTracks.size, persistedPermissionAllowance)))
                trackDao.delete(failedTracks.map { it.uriString })
            }
        }
    }
}

/**
 * Compose an AddTrackButton with state provided by an instance of AddTrackButtonViewModel.
 *
 * @param onClick The callback that will be invoked when the button is clicked.
 *     StatefulAddTrackButton will already handle the expanding and collapsing
 *     of the button itself. Use onClick for additional onClick actions.
 */
@Composable fun StatefulAddTrackButton(
    onClick: (() -> Unit)? = null
) {
    val viewModel: AddTrackButtonViewModel = viewModel()

    AddTrackButton(
        expanded = viewModel.expanded,
        onBoundsChange = viewModel::onBoundsChange,
        onClick = { viewModel.onClick()
                    onClick?.invoke() },
        onAddDownloadClick = viewModel::onDownloadFileButtonClick,
        onAddLocalFilesClick = viewModel::onAddLocalFilesButtonClick,)

    //if (viewModel.showingDownloadFileDialog)

    if (viewModel.showingAddLocalFilesDialog)
        AddTracksFromLocalFilesDialog(
            onDismissRequest = viewModel::onAddLocalFilesDialogDismiss,
            onConfirmRequest = viewModel::onAddLocalFilesDialogConfirm)
}

/**
 * Open a dialog for the user to select one or more audio files to add to
 * their library.
 *
 * @param onDismissRequest The callback that will be invoked when the user
 *     clicks outside the dialog or taps the cancel button.
 * @param onConfirmRequest The callback that will be invoked when the user
 *     taps the dialog's confirm button after having selected one or more files.
 *     The first parameter is the list of Uris representing the files to add,
 *     while the second parameter is the list of names for each of these uris.
 */
@Composable fun AddTracksFromLocalFilesDialog(
    onDismissRequest: () -> Unit,
    onConfirmRequest: (List<Uri>, List<String>) -> Unit,
) {
    val context = LocalContext.current
    var chosenUris by rememberSaveable { mutableStateOf<List<Uri>?>(null) }
    val trackNames = rememberSaveable<SnapshotStateList<String>>(
        saver = listSaver(
            save = { it },
            restore = { it.toMutableStateList() }),
        init = { mutableStateListOf() })

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty())
            onDismissRequest()
        chosenUris = uris
        trackNames.clear()
        for (uri in uris)
            trackNames.add(uri.getDisplayName(context) ?: "")
    }

    if (chosenUris == null)
        LaunchedEffect(Unit) { launcher.launch(arrayOf("audio/*")) }
    else SoundAuraDialog(
        title = stringResource(R.string.add_local_files_dialog_title),
        onDismissRequest = onDismissRequest,
        confirmButtonEnabled = chosenUris != null &&
                !trackNames.containsBlanks,
        onConfirm = {
            val uris = chosenUris ?: return@SoundAuraDialog
            onConfirmRequest(uris, trackNames)
        }, content = {
            trackNames.Editor(Modifier.heightIn(max = 400.dp))
        })
}

/** Return a suitable display name for a file uri (i.e. the file name minus
 * the file type extension, and with underscores replaced with spaces. */
fun Uri.getDisplayName(context: Context) =
    DocumentFile.fromSingleUri(context, this)?.name
        ?.substringBeforeLast('.')
        ?.replace('_', ' ')

/** Return whether the List<String> contains any strings that are blank
 * (i.e. are either empty or consist of only whitespace characters). */
val List<String>.containsBlanks get() =
    find { it.isBlank() } != null

/** Compose a LazyColumn of TextFields to edit the
 * strings of the the receiver MutableList<NewTrack>. */
@Composable private fun MutableList<String>.Editor(
    modifier: Modifier = Modifier
) = LazyColumn(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(8.dp),
    content = { items(size) { index ->
        TextField(
            value = get(index),
            onValueChange = { this@Editor[index] = it },
            textStyle = MaterialTheme.typography.body1,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(1f))
    }})