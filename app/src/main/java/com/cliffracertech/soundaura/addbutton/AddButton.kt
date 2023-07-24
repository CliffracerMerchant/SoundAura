/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.addbutton

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.FloatingActionButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cliffracertech.soundaura.R
import com.cliffracertech.soundaura.collectAsState
import com.cliffracertech.soundaura.dialog.RenameDialog
import com.cliffracertech.soundaura.dialog.ValidatedNamingState
import com.cliffracertech.soundaura.model.ActivePresetState
import com.cliffracertech.soundaura.model.MessageHandler
import com.cliffracertech.soundaura.model.StringResource
import com.cliffracertech.soundaura.model.UriPermissionHandler
import com.cliffracertech.soundaura.model.database.PlaylistDao
import com.cliffracertech.soundaura.model.database.PlaylistNameValidator
import com.cliffracertech.soundaura.model.database.PresetDao
import com.cliffracertech.soundaura.model.database.PresetNameValidator
import com.cliffracertech.soundaura.model.database.TrackNamesValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A [ViewModel] that contains state and callbacks for a button to add playlists.
 *
 * The add playlist button's onClick should be set to the view model's
 * provided [onClick] method. The property [dialogStep] can then be observed
 * to access the current [AddLocalFilesDialogStep] that should be shown to the
 * user. State and callbacks for each dialog step are contained inside the
 * current [AddLocalFilesDialogStep] value of the [dialogStep] property. User
 * attempts to back out of the dialog via system back button presses or back
 * gestures (but not dialog back button presses, which should be connected to
 * the [AddLocalFilesDialogStep.onBackClick] for the current step instead)
 * should be connected to the [onDialogDismissRequest] method.
 *
 * Note that the [Context] argument passed in the constructor will be saved
 * for the lifetime of the view model, and therefore should not be a [Context]
 * whose owner should not outlive the view model (e.g. an activity context).
 */
@HiltViewModel @SuppressLint("StaticFieldLeak")
class AddPlaylistButtonViewModel(
    private val context: Context,
    private val permissionHandler: UriPermissionHandler,
    private val playlistDao: PlaylistDao,
    private val messageHandler: MessageHandler,
    coroutineScope: CoroutineScope? = null
) : ViewModel() {

    @Inject constructor(
        @ApplicationContext
        context: Context,
        permissionHandler: UriPermissionHandler,
        playlistDao: PlaylistDao,
        messageHandler: MessageHandler
    ) : this(context, permissionHandler, playlistDao, messageHandler, null)

    private val scope = coroutineScope ?: viewModelScope

    var dialogStep by mutableStateOf<AddLocalFilesDialogStep?>(null)
        private set

    fun onDialogDismissRequest() { dialogStep = null }

    fun onClick() {
        dialogStep = AddLocalFilesDialogStep.SelectingFiles(
            onFilesSelected = { chosenUris ->
                // If uris.size == 1, we can skip straight to the name
                // track dialog step to skip the user needing to choose
                if (chosenUris.size > 1)
                    showAddIndividuallyOrAsPlaylistQueryStep(chosenUris)
                else showNameTracksStep(chosenUris)
            })
    }

    private fun showAddIndividuallyOrAsPlaylistQueryStep(chosenUris: List<Uri>) {
        dialogStep = AddLocalFilesDialogStep.AddIndividuallyOrAsPlaylistQuery(
            onBack = ::onDialogDismissRequest,
            onAddIndividuallyClick = { showNameTracksStep(chosenUris) },
            onAddAsPlaylistClick = { showNamePlaylistStep(chosenUris, goingForward = true) })
    }

    private fun showNameTracksStep(trackUris: List<Uri>) {
        dialogStep = AddLocalFilesDialogStep.NameTracks(
            onBack = {
                // if uris.size == 1, then the question of whether to add as
                // a track or as a playlist should have been skipped. In this
                // case, the dialog will be dismissed instead of going back.
                if (trackUris.size > 1)
                    showAddIndividuallyOrAsPlaylistQueryStep(trackUris)
                else onDialogDismissRequest()
            }, validator = TrackNamesValidator(
                playlistDao, scope, trackUris.map { it.getDisplayName(context) }),
            coroutineScope = scope,
            onFinish = { validatedTrackNames ->
                addTracks(validatedTrackNames, trackUris)
            })
    }

    private fun showNamePlaylistStep(tracks: List<Uri>, goingForward: Boolean) {
        dialogStep = AddLocalFilesDialogStep.NamePlaylist(
            isAheadOfPreviousStep = goingForward,
            onBack = { showAddIndividuallyOrAsPlaylistQueryStep(tracks) },
            validator = PlaylistNameValidator(
                playlistDao, scope, "${tracks.first().getDisplayName(context)} playlist"),
            coroutineScope = scope,
            onFinish = { validatedPlaylistName ->
                showPlaylistOptionsStep(validatedPlaylistName, tracks)
            })
    }

    private fun showPlaylistOptionsStep(
        validatedPlaylistName: String,
        tracks: List<Uri>
    ) {
        dialogStep = AddLocalFilesDialogStep.PlaylistOptions(
            onBack = { showNamePlaylistStep(tracks, goingForward = false) },
            tracks = tracks,
            onFinish = { shuffle, newTrackOrder ->
                addPlaylist(validatedPlaylistName, shuffle, newTrackOrder)
            })
    }

    private fun addTracks(trackNames: List<String>, trackUris: List<Uri>) {
        scope.launch {
            assert(trackUris.size == trackNames.size)
            val acceptedTracks = permissionHandler.takeUriPermissions(trackUris)

            val failureCount = trackUris.size - acceptedTracks.size
            if (failureCount > 0)
                messageHandler.postMessage(StringResource(
                    R.string.cant_add_all_tracks_warning,
                    failureCount, permissionHandler.permissionAllowance))
            if (acceptedTracks.isNotEmpty()) {
                val names = trackNames.subList(0, acceptedTracks.size - 1)
                playlistDao.insertSingleTrackPlaylists(names, acceptedTracks)
            }
            onDialogDismissRequest()
        }
    }

    private fun addPlaylist(name: String, shuffle: Boolean, tracks: List<Uri>) {
        scope.launch {
            val acceptedTracks = permissionHandler
                .takeUriPermissions(tracks, insertPartial = false)
            if (acceptedTracks.isEmpty())
                messageHandler.postMessage(StringResource(
                    R.string.cant_add_playlist_warning,
                    permissionHandler.permissionAllowance))
            else playlistDao.insertPlaylist(name, shuffle, acceptedTracks)
            onDialogDismissRequest()
        }
    }

    /** Return a suitable display name for a file [Uri] (i.e. the file name minus
     * the file type extension, and with underscores replaced with spaces). */
    private fun Uri.getDisplayName(context: Context) =
        DocumentFile.fromSingleUri(context, this)
            ?.name?.substringBeforeLast('.')?.replace('_', ' ')
            ?: pathSegments.last().substringBeforeLast('.').replace('_', ' ')
}

/**
 * A [ViewModel] that contains state and callbacks for a button to add presets.
 *
 * The method [onClick] should be used as the onClick callback for the
 * button being used to add presets. If the property [newPresetDialogState]
 * is not null, then its [ValidatedNamingState] value should be used as
 * the state parameter for a shown [RenameDialog].
 */
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

    var newPresetDialogState by mutableStateOf<ValidatedNamingState?>(null)
        private set

    private val activeTracksIsEmpty by playlistDao
        .getAtLeastOnePlaylistIsActive()
        .map(Boolean::not)
        .collectAsState(true, scope)

    fun onClick() { when {
        activeTracksIsEmpty -> messageHandler.postMessage(
            StringResource(R.string.preset_cannot_be_empty_warning_message))
        else -> newPresetDialogState = ValidatedNamingState(
            validator = PresetNameValidator(presetDao, scope),
            coroutineScope = scope,
            onNameValidated = { validatedName ->
                newPresetDialogState = null
                presetDao.savePreset(validatedName)
                activePresetState.setName(validatedName)
            }, onCancel = {
                newPresetDialogState = null
            })
    }}
}

/** An enum whose values describe the entities that can be added by the [AddButton]. */
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

    addPlaylistViewModel.dialogStep?.let {
        AddLocalFilesDialog(it, addPlaylistViewModel::onDialogDismissRequest)
    }

    addPresetViewModel.newPresetDialogState?.let {
        RenameDialog(it, title = stringResource(R.string.create_new_preset_dialog_title))
    }
}