/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cliffracertech.soundaura.dialog.DialogButtonRow
import com.cliffracertech.soundaura.dialog.RenameDialog
import com.cliffracertech.soundaura.dialog.SoundAuraDialog
import com.cliffracertech.soundaura.model.ActivePresetState
import com.cliffracertech.soundaura.model.MessageHandler
import com.cliffracertech.soundaura.model.StringResource
import com.cliffracertech.soundaura.model.Validator
import com.cliffracertech.soundaura.model.database.Playlist
import com.cliffracertech.soundaura.model.database.PlaylistDao
import com.cliffracertech.soundaura.model.database.PlaylistNameValidator
import com.cliffracertech.soundaura.model.database.PresetDao
import com.cliffracertech.soundaura.model.database.PresetNameValidator
import com.cliffracertech.soundaura.model.database.Track
import com.cliffracertech.soundaura.model.database.TrackNamesValidator
import com.cliffracertech.soundaura.ui.HorizontalDivider
import com.cliffracertech.soundaura.ui.SlideAnimatedContent
import com.cliffracertech.soundaura.ui.bottomShape
import com.cliffracertech.soundaura.ui.minTouchTargetSize
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

// The stored context object here is the application
// context, and therefore does not present a problem.
@HiltViewModel @SuppressLint("StaticFieldLeak")
class AddPlaylistButtonViewModel(
    private val context: Context,
    private val playlistDao: PlaylistDao,
    private val messageHandler: MessageHandler,
    coroutineScope: CoroutineScope? = null
) : ViewModel() {

    @Inject constructor(
        @ApplicationContext
        context: Context,
        playlistDao: PlaylistDao,
        messageHandler: MessageHandler
    ) : this(context, playlistDao, messageHandler, null)

    private val scope = coroutineScope ?: viewModelScope

    var showingDialog by mutableStateOf(false)
        private set

    fun onClick() { showingDialog = true }

    fun onDialogDismiss() { showingDialog = false }

    private var onDialogConfirmJob: Job? = null

    private var trackNamesValidator: TrackNamesValidator? = null
    private var playlistNameValidator: PlaylistNameValidator? = null
    val message by derivedStateOf {
        trackNamesValidator?.message
    }

    var targetUris = emptyList<Uri>()
    val targetUriNamesAndErrors get() = trackNamesValidator?.values ?: emptyList()

    fun onAddAsIndividualTracksClick(uris: List<Uri>) {
        assert(uris.isNotEmpty())
        targetUris = uris
        trackNamesValidator = TrackNamesValidator(
            playlistDao, uris.map{ it.getDisplayName(context) ?: ""}, scope)
    }

    fun onNewTrackNameChange(index: Int, newName: String) =
        trackNamesValidator?.setValue(index, newName)

    fun onAddTracksDialogConfirm() {
        if (onDialogConfirmJob != null) return

        onDialogConfirmJob = scope.launch {
            val newTrackNames = trackNamesValidator?.validate()
                ?: return@launch

            val persistedPermissionAllowance =
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) 128 else 512
            val persistedPermissionsCount = context.contentResolver.persistedUriPermissions.size
            val remainingSpace = persistedPermissionAllowance - persistedPermissionsCount

            val failureCount = newTrackNames.size - remainingSpace
            messageHandler.postMessage(
                StringResource(
                    string = null,
                    stringResId = R.string.cant_add_all_tracks_warning,
                    args = arrayListOf(failureCount, persistedPermissionAllowance)))

            if (remainingSpace > 0) {
                val newPlaylistsAndContents = newTrackNames
                    .subList(0, remainingSpace - 1)
                    .map(::Playlist).withIndex()
                    .associate { it.value to listOf(Track(targetUris[it.index])) }
                playlistDao.insert(newPlaylistsAndContents)
            }
            onDialogDismiss()
        }
    }

    fun onAddAsPlaylistClick(uris: List<Uri>) {
        assert(uris.isNotEmpty())
        targetUris = uris
        playlistNameValidator = PlaylistNameValidator(
            playlistDao, "${uris.first().getDisplayName(context)} playlist", scope)
    }

    fun onNewPlaylistNameChange(newName: String) {
        playlistNameValidator?.value = newName
    }

    fun onAddPlaylistDialogConfirm() {
        if (onDialogConfirmJob != null) return

        onDialogConfirmJob = scope.launch {
            val newPlaylistName = playlistNameValidator?.validate()
                ?: return@launch

            val persistedPermissionAllowance =
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) 128 else 512
            val persistedPermissionsCount = context.contentResolver.persistedUriPermissions.size
            val remainingSpace = persistedPermissionAllowance - persistedPermissionsCount

            if (remainingSpace < targetUris.size)
                messageHandler.postMessage(StringResource(
                    R.string.cant_add_playlist_warning, persistedPermissionAllowance))
            else playlistDao.insert(mapOf(Playlist(newPlaylistName) to targetUris.map(::Track)))
            onDialogDismiss()
        }
    }
}

@HiltViewModel class AddPresetButtonViewModel(
    private val presetDao: PresetDao,
    private val messageHandler: MessageHandler,
    private val activePresetState: ActivePresetState,
    playlistDao: PlaylistDao,
    coroutineScope: CoroutineScope?,
) : ViewModel() {

    @Inject constructor(
        presetDao: PresetDao,
        messageHandler: MessageHandler,
        activePresetState: ActivePresetState,
        playlistDao: PlaylistDao,
    ) : this(presetDao, messageHandler, activePresetState, playlistDao, null)

    private val scope = coroutineScope ?: viewModelScope

    var showingAddPresetDialog by mutableStateOf(false)
        private set

    private val activeTracksIsEmpty by playlistDao
        .getAtLeastOnePlaylistIsActive()
        .collectAsState(true, scope)

    fun onClick() { when {
        activeTracksIsEmpty -> messageHandler.postMessage(
            StringResource(R.string.preset_cannot_be_empty_warning_message))
        else -> showingAddPresetDialog = true
    }}

    private val nameValidator = PresetNameValidator(presetDao, scope)
    val proposedNewPresetName by nameValidator::value
    val newPresetNameValidatorMessage by nameValidator::message

    fun onAddPresetDialogDismiss() {
        showingAddPresetDialog = false
        nameValidator.reset("")
    }

    fun onNewPresetNameChange(newName: String) {
        nameValidator.value = newName
    }

    fun onAddPresetDialogConfirm() {
         scope.launch {
             val name = nameValidator.validate() ?: return@launch
             showingAddPresetDialog = false
             presetDao.savePreset(name)
             activePresetState.setName(name)
        }
    }
}

/** An enum class whose values describe the entities that can be added by the [AddButton]. */
enum class AddButtonTarget { Playlist, Preset }

/**
 * A button to add local files or presets, with state provided by instances
 * of [AddPlaylistButtonViewModel] and [AddPresetButtonViewModel].
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
    val addPlaylistViewModel: AddPlaylistButtonViewModel = viewModel()
    val addPresetViewModel: AddPresetButtonViewModel = viewModel()

    FloatingActionButton(
        onClick = { when(target) {
            AddButtonTarget.Playlist -> addPlaylistViewModel.onClick()
            AddButtonTarget.Preset ->   addPresetViewModel.onClick()
        }},
        modifier = modifier,
        backgroundColor = backgroundColor,
        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
    ) {
        Icon(imageVector = Icons.Default.Add,
            contentDescription = stringResource(when(target) {
                AddButtonTarget.Playlist -> R.string.add_local_files_button_description
                AddButtonTarget.Preset ->   R.string.add_preset_button_description
            }),
            tint = MaterialTheme.colors.onPrimary)
    }

    if (addPlaylistViewModel.showingDialog)
        AddLocalFilesDialog(addPlaylistViewModel::onDialogDismiss)

    if (addPresetViewModel.showingAddPresetDialog)
        RenameDialog(
            title = stringResource(R.string.create_new_preset_dialog_title),
            newNameProvider = addPresetViewModel::proposedNewPresetName,
            onNewNameChange = addPresetViewModel::onNewPresetNameChange,
            errorMessageProvider = addPresetViewModel::newPresetNameValidatorMessage,
            onDismissRequest = addPresetViewModel::onAddPresetDialogDismiss,
            onConfirmClick = addPresetViewModel::onAddPresetDialogConfirm)
}

/**
 * Open a dialog for the user to select one or more audio files to add to
 * their library.
 *
 * @param onDismissRequest The callback that will be invoked when the user
 *     clicks outside the dialog or taps the cancel button.
 */
@Composable fun AddLocalFilesDialog(onDismissRequest: () -> Unit) {
    val vm = viewModel<AddPlaylistButtonViewModel>()
    var chosenUris by rememberSaveable { mutableStateOf<List<Uri>?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty())
            onDismissRequest()
        chosenUris = uris
    }

    val uris = chosenUris
    if (uris == null)
        LaunchedEffect(Unit) { launcher.launch(arrayOf("audio/*", "application/ogg")) }
    else {
        /** A false value means the uris are being added as individual tracks.
         * A true value means the uris are being added collectively as a playlist.
         * A null value means the user has not chosen yet. */
        var addingUrisAsPlaylist by rememberSaveable {
            assert(uris.isNotEmpty())
            mutableStateOf(if (uris.size > 1) null else {
                // If there is only one Uri, we default to false (add as an
                // individual track) to skip the user needing to choose. But, we
                // need to call the vm's onAddAsIndividualTracksClick manually.
                vm.onAddAsIndividualTracksClick(uris)
                false
            })
        }
        SoundAuraDialog(
            modifier = Modifier.restrictWidthAccordingToSizeClass(),
            useDefaultWidth = false,
            title = when (addingUrisAsPlaylist) {
                null -> stringResource(R.string.add_local_files_dialog_title)
                false -> stringResource(R.string.add_local_files_as_tracks_dialog_title)
                true -> stringResource(R.string.configure_playlist_dialog_title)
            }, onDismissRequest = onDismissRequest,
            buttons = {
                if (addingUrisAsPlaylist == null)
                    Column {
                        HorizontalDivider(Modifier.padding(top = 12.dp))
                        TextButton(
                            onClick = {
                                addingUrisAsPlaylist = false
                                vm.onAddAsIndividualTracksClick(uris)
                            }, modifier = Modifier.minTouchTargetSize(),
                            shape = RectangleShape,
                        ) { Text(stringResource(R.string.add_local_files_as_tracks_option)) }

                        HorizontalDivider()
                        TextButton(
                            onClick = {
                                addingUrisAsPlaylist = true
                                vm.onAddAsPlaylistClick(uris)
                            }, modifier = Modifier.minTouchTargetSize(),
                            shape = RectangleShape,
                        ) { Text(stringResource(R.string.add_local_files_as_playlist_option)) }

                        HorizontalDivider()
                        TextButton(
                            onClick = onDismissRequest,
                            modifier = Modifier.minTouchTargetSize(),
                            shape = MaterialTheme.shapes.medium.bottomShape(),
                        ) { Text(stringResource(R.string.cancel)) }
                    }
                else DialogButtonRow(
                    onCancel = { addingUrisAsPlaylist = null },
                    confirmButtonEnabled = vm.message !is Validator.Message.Error,
                    onConfirm = { when (addingUrisAsPlaylist) {
                        true -> vm.onAddPlaylistDialogConfirm()
                        false -> vm.onAddTracksDialogConfirm()
                        else -> {}
                    }})
            }
        ) {
            SlideAnimatedContent(
                targetState = addingUrisAsPlaylist,
                leftToRight = addingUrisAsPlaylist != null,
            ) { addingUrisAsPlaylist ->
                when (addingUrisAsPlaylist) {
                    null -> {
                        Text(stringResource(R.string.add_local_files_as_playlist_or_tracks_question))
                    } false -> {
                        // To prevent a crash due to nested LazyColumns we have to restrict its height
                        LazyColumn(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .heightIn(max = LocalConfiguration.current.screenHeightDp.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            content = {
                                items(vm.targetUriNamesAndErrors.size) { index ->
                                    val value = vm.targetUriNamesAndErrors.getOrNull(index)
                                    TextField(
                                        value = value?.first ?: "",
                                        onValueChange = { vm.onNewTrackNameChange(index, it) },
                                        textStyle = MaterialTheme.typography.body1,
                                        singleLine = true,
                                        isError = value?.second ?: false,
                                        modifier = Modifier.fillMaxWidth())
                            }})
                    } true -> {
                        val value = vm.targetUriNamesAndErrors.first()
                        TextField(
                            value = value.first,
                            onValueChange = vm::onNewPlaylistNameChange,
                            textStyle = MaterialTheme.typography.body1,
                            singleLine = true,
                            isError = vm.message?.isError == true,
                            modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

/** Return a suitable display name for a file [Uri] (i.e. the file name minus
 * the file type extension, and with underscores replaced with spaces). */
fun Uri.getDisplayName(context: Context) =
    DocumentFile.fromSingleUri(context, this)?.name
        ?.substringBeforeLast('.')
        ?.replace('_', ' ')

/** Return whether the list contains any strings that are blank
 * (i.e. are either empty or consist of only whitespace characters). */
fun List<String>.containsBlanks() = find { it.isBlank() } != null