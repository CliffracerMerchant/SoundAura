/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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

// The stored context object here is the application
// context, and therefore does not present a problem.
@HiltViewModel @SuppressLint("StaticFieldLeak")
class AddLocalFilesButtonViewModel(
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

    var showingDialog by mutableStateOf(false)
        private set

    fun onClick() { showingDialog = true }

    fun onDialogDismiss() { showingDialog = false }

    fun onDialogConfirm(trackUris: List<Uri>, trackNames: List<String>) {
        onDialogDismiss()
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
 * Compose a button to add local files with state provided by an
 * instance of AddTrackButtonViewModelAddTrackButtonViewModel.
 *
 * @param backgroundColor The color to use for the button's background.
 */
@Composable fun AddLocalFilesButton(backgroundColor: Color) {
    val viewModel: AddLocalFilesButtonViewModel = viewModel()

    FloatingActionButton(
        onClick = viewModel::onClick,
        backgroundColor = backgroundColor,
        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
    ) {
        Icon(imageVector = Icons.Default.Add,
            contentDescription = stringResource(R.string.add_button_description),
            tint = MaterialTheme.colors.onPrimary)
    }

    if (viewModel.showingDialog)
        AddTracksFromLocalFilesDialog(
            onDismissRequest = viewModel::onDialogDismiss,
            onConfirmRequest = viewModel::onDialogConfirm)
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
        LaunchedEffect(Unit) { launcher.launch(arrayOf("audio/*", "application/ogg")) }
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
 * the file type extension, and with underscores replaced with spaces). */
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
            modifier = Modifier.fillMaxWidth())
    }})