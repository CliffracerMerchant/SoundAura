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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Error
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

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
                trackDao.delete(failedTracks.map(Track::uriString::get))
            }
        }
    }
}

@HiltViewModel
class AddPresetButtonViewModel(
    private val presetDao: PresetDao,
    private val messageHandler: MessageHandler,
    private val activePresetState: ActivePresetState,
    trackDao: TrackDao,
    coroutineScope: CoroutineScope?,
) : ViewModel() {

    @Inject constructor(
        presetDao: PresetDao,
        messageHandler: MessageHandler,
        activePresetState: ActivePresetState,
        trackDao: TrackDao,
    ) : this(presetDao, messageHandler, activePresetState, trackDao, null)

    private val scope = coroutineScope ?: viewModelScope
    private val presetNameValidator = PresetNameValidator(presetDao)
    val newPresetNameValidatorMessage by
        presetNameValidator.errorMessage.collectAsState(null, scope)

    var showingAddPresetDialog by mutableStateOf(false)
        private set

    private val activeTracksIsEmpty by trackDao.getActiveTracks()
        .map { it.isEmpty() }
        .collectAsState(false, scope)

    fun onClick() { when {
        activeTracksIsEmpty -> messageHandler.postMessage(
            StringResource(R.string.preset_cannot_be_empty_warning_message))
        else -> showingAddPresetDialog = true
    }}

    fun onAddPresetDialogDismiss() {
        showingAddPresetDialog = false
        presetNameValidator.clearProposedName()
    }

    fun onNewPresetNameChange(newName: String) =
        presetNameValidator.setProposedName(newName)

    fun onAddPresetDialogConfirm() {
         scope.launch {
             val name = presetNameValidator.proposedName.value ?: ""
             if (presetNameValidator.onNameConfirm(name)) {
                 showingAddPresetDialog = false
                 presetDao.savePreset(name)
                 activePresetState.setName(name)
             }
        }
    }
}

/** An enum class whose values describe the entities that can be added by the [AddButton]. */
enum class AddButtonTarget { Track, Preset }

/**
 * A button to add local files or presets, with state provided by instances
 * of [AddTrackButtonViewModel] and [AddPresetButtonViewModel].
 *
 * @param target The [AddButtonTarget] that should be added when the button is clicked
 * @param backgroundColor The color to use for the button's background
 * @param modifier The [Modifier] to use for the button
 */
@Composable fun AddButton(
    target: AddButtonTarget,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
) {
    val addTrackViewModel: AddTrackButtonViewModel = viewModel()
    val addPresetViewModel: AddPresetButtonViewModel = viewModel()

    FloatingActionButton(
        onClick = { when(target) {
            AddButtonTarget.Track -> addTrackViewModel.onClick()
            AddButtonTarget.Preset -> addPresetViewModel.onClick()
        }},
        modifier = modifier,
        backgroundColor = backgroundColor,
        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
    ) {
        Icon(imageVector = Icons.Default.Add,
            contentDescription = stringResource(when(target) {
                AddButtonTarget.Track -> R.string.add_track_button_description
                AddButtonTarget.Preset -> R.string.add_preset_button_description
            }),
            tint = MaterialTheme.colors.onPrimary)
    }

    if (addTrackViewModel.showingDialog)
        AddTracksFromLocalFilesDialog(
            onDismissRequest = addTrackViewModel::onDialogDismiss,
            onConfirmRequest = addTrackViewModel::onDialogConfirm)

    val context = LocalContext.current
    val nameValidatorMessage by remember { derivedStateOf {
        addPresetViewModel.newPresetNameValidatorMessage?.resolve(context)
    }}
    if (addPresetViewModel.showingAddPresetDialog)
        CreateNewPresetDialog(
            onNameChange = addPresetViewModel::onNewPresetNameChange,
            nameValidatorMessage = nameValidatorMessage,
            onDismissRequest = addPresetViewModel::onAddPresetDialogDismiss,
            onConfirm = addPresetViewModel::onAddPresetDialogConfirm)
}

/**
 * Open a dialog for the user to select one or more audio files to add to
 * their library.
 *
 * @param onDismissRequest The callback that will be invoked when the user
 *     clicks outside the dialog or taps the cancel button.
 * @param onConfirmRequest The callback that will be invoked when the user
 *     taps the dialog's confirm button after having selected one or more files.
 *     The first parameter is the list of [Uri]s representing the files to add,
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
        modifier = Modifier.restrictWidthAccordingToSizeClass(),
        useDefaultWidth = false,
        title = stringResource(R.string.add_local_files_dialog_title),
        onDismissRequest = onDismissRequest,
        confirmButtonEnabled = chosenUris != null &&
                               !trackNames.containsBlanks,
        onConfirm = {
            val uris = chosenUris ?: return@SoundAuraDialog
            onConfirmRequest(uris, trackNames)
        }, content = {
            // Editor uses a LazyColumn internally. To prevent a crash
            // due to nested LazyColumn we have to restrict its height.
            trackNames.Editor(Modifier
                .padding(horizontal = 16.dp)
                .heightIn(max = LocalConfiguration.current.screenHeightDp.dp))
        })
}

/** Return a suitable display name for a file [Uri] (i.e. the file name minus
 * the file type extension, and with underscores replaced with spaces). */
fun Uri.getDisplayName(context: Context) =
    DocumentFile.fromSingleUri(context, this)?.name
        ?.substringBeforeLast('.')
        ?.replace('_', ' ')

/** Return whether the list contains any strings that are blank
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

/**
 * Show a dialog to create a new preset.
 *
 * @Param onNameChange The callback that will be invoked when the user input
 *     for the new preset's name changes.
 * @param nameValidatorMessage A nullable String that represents a warning
 *     message that should be displayed to the user regarding the new preset's
 *     name. Generally onNameChange should change this value to null if the
 *     entered new name is acceptable, or an error message explaining why the
 *     new name is not acceptable otherwise.
 * @param onDismissRequest The callback that will be invoked when the dialog is dismissed
 * @param onConfirm The callback that will be invoked when the confirm button has been clicked
 */
@Composable fun CreateNewPresetDialog(
    onNameChange: (String) -> Unit,
    nameValidatorMessage: String?,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    var currentName by rememberSaveable { mutableStateOf("") }
    SoundAuraDialog(
        modifier = Modifier.restrictWidthAccordingToSizeClass(),
        useDefaultWidth = false,
        title = stringResource(R.string.create_new_preset_dialog_title),
        onDismissRequest = onDismissRequest,
        confirmButtonEnabled = nameValidatorMessage == null,
        onConfirm = onConfirm
    ) {
        TextField(
            onValueChange = {
                currentName = it
                onNameChange(it)
            }, value = currentName,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            isError = nameValidatorMessage != null,
            singleLine = true,
            textStyle = MaterialTheme.typography.body1)

        var previousNameValidatorMessage by remember { mutableStateOf("") }
        AnimatedVisibility(nameValidatorMessage != null) {
            Row(Modifier.align(Alignment.CenterHorizontally)
                .padding(start = 16.dp, end = 16.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colors.error)
                AnimatedContent(nameValidatorMessage ?: previousNameValidatorMessage) {
                    Text(it, Modifier.weight(1f), MaterialTheme.colors.error)
                }
                if (nameValidatorMessage != null)
                    previousNameValidatorMessage = nameValidatorMessage
            }
        }
    }
}