/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.library

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cliffracertech.soundaura.R
import com.cliffracertech.soundaura.addbutton.SystemFileChooser
import com.cliffracertech.soundaura.dialog.DialogWidth
import com.cliffracertech.soundaura.dialog.NamingDialog
import com.cliffracertech.soundaura.dialog.NamingState
import com.cliffracertech.soundaura.dialog.SoundAuraDialog
import com.cliffracertech.soundaura.dialog.ValidatedNamingState
import com.cliffracertech.soundaura.library.PlaylistDialog.FileChooser
import com.cliffracertech.soundaura.library.PlaylistDialog.PlaylistOptions
import com.cliffracertech.soundaura.library.PlaylistDialog.Remove
import com.cliffracertech.soundaura.library.PlaylistDialog.Rename
import com.cliffracertech.soundaura.model.MessageHandler
import com.cliffracertech.soundaura.model.StringResource
import com.cliffracertech.soundaura.model.Validator
import com.cliffracertech.soundaura.ui.MarqueeText
import kotlinx.coroutines.CoroutineScope

/** The subclasses of PlaylistDialog (i.e. [Rename], [PlaylistOptions],
 * and [Remove]) contain all of the state and callbacks needed to display
 * a [Playlist] related dialog. The [onDismissRequest] property should be
 * used for the displayed dialog's onDismissRequest. */
sealed class PlaylistDialog(
    val target: Playlist,
    val onDismissRequest: () -> Unit,
) {
    /**
     * The rename dialog for a playlist. Rename implements [NamingState], and
     * can therefore be used as the state parameter for a [NamingDialog].
     *
     * @param target The [Playlist] that is the target of the dialog
     * @param validator The [Validator] to use for validation of the new name
     * @param coroutineScope A [CoroutineScope] to use for background work.
     * @param onDismissRequest The callback that should be invoked when the dialog's
     *     cancel button is clicked or a back button click or gesture is performed
     * @param onNameValidated The callback that will be invoked upon a successful
     *     validation of the [Playlist]'s new name after [NamingState.finalize] is
     *     called with a valid name.
     */
    class Rename(
        target: Playlist,
        validator: Validator<String>,
        coroutineScope: CoroutineScope,
        onDismissRequest: () -> Unit,
        private val onNameValidated: suspend (String) -> Unit,
    ): PlaylistDialog(target, onDismissRequest),
       NamingState by ValidatedNamingState(
           validator, coroutineScope, onNameValidated, onDismissRequest)

    /**
     * The 'file chooser' step. This step can appear when the add button of
     * the playlist options step is clicked, or when the 'create playlist'
     * option is selected for a single track playlist. A [List] of the [Uri]s
     * of the chosen files should be passed to a [onFilesChosen] call.
     *
     * @param target The [Playlist] that is the target of the dialog
     * @param messageHandler A [MessageHandler] instance that will be used to show messages to the user
     * @param currentTracks A [List] of the [Uri]s of the tracks that are already in the playlist
     * @param onDismissRequest The callback that should be invoked when
     *     a back button click or gesture is performed
     */
    class FileChooser(
        target: Playlist,
        private val messageHandler: MessageHandler,
        private val currentTracks: List<Uri>,
        onDismissRequest: () -> Unit,
        private val onChosenFilesValidated: (List<Uri>) -> Unit,
    ): PlaylistDialog(target, onDismissRequest) {
        val onFilesChosen: (List<Uri>) -> Unit = { chosenFiles ->
            if (chosenFiles.isEmpty())
                onDismissRequest()
                // If no files were chosen at all, then the user
                // must have backed out of the files chooser
                // intentionally. No error message is required.
            else {
                val validatedFiles = chosenFiles - currentTracks.toSet()
                if (validatedFiles.isEmpty()) {
                    onDismissRequest()
                    messageHandler.postMessage(R.string.file_chooser_no_valid_tracks_error_message)
                } else {
                    val rejectedCount = chosenFiles.size - validatedFiles.size
                    if (rejectedCount > 0)
                        messageHandler.postMessage(StringResource(
                            R.string.file_chooser_invalid_tracks_warning_message, rejectedCount))
                    onChosenFilesValidated(validatedFiles)
                }
            }
        }
    }

    /**
     * The 'playlist options' dialog for a playlist. The properties [shuffleEnabled]
     * and [onShuffleSwitchClick] can be used for the current state of and onClick
     * callback, respectively, for a 'shuffle enabled' toggle. The value of
     * [mutablePlaylist] should be used as the same-named parameter for a
     * [PlaylistOptionsView].
     *
     * @param target The [Playlist] that is the target of the dialog
     * @param shuffleEnabled Whether shuffle is initially enabled for the [target]
     * @param playlistTracks The [List] of [Uri]s that represents the tracks in the [target]
     * @param onDismissRequest The callback that should be invoked when the dialog's
     *     cancel button is clicked or a back button click or gesture is performed
     * @param onAddFilesClick The callback that will be invoked when the dialog's
     *     add files button is clicked.
     * @param onConfirm The callback that will be invoked when [onFinishClick]
     *     is invoked. The new values for the playlist's
     *     shuffle and tracks, as set within the dialog, are provided.
     */
    class PlaylistOptions(
        target: Playlist,
        private val playlistTracks: List<Uri>,
        shuffleEnabled: Boolean,
        onDismissRequest: () -> Unit,
        val onAddFilesClick: () -> Unit,
        private val onConfirm: (
                shuffleEnabled: Boolean,
                newTrackList: List<Uri>,
            ) -> Unit,
    ): PlaylistDialog(target, onDismissRequest) {
        var shuffleEnabled by mutableStateOf(shuffleEnabled)
            private set
        val onShuffleSwitchClick = { this.shuffleEnabled = !this.shuffleEnabled }

        val mutablePlaylist = MutablePlaylist(playlistTracks)

        val onFinishClick = {
            val newList = mutablePlaylist.applyChanges()
            onConfirm(this.shuffleEnabled, newList)
        }
    }

    /**
     * The remove dialog for a playlist
     *
     * @param target The [Playlist] that is the target of the dialog
     * @param onDismissRequest The callback that should be invoked when the dialog's
     *     cancel button is clicked or a back button click or gesture is performed
     * @param onConfirmClick The callback that should be invoked when the dialog's
     *     confirmatory button is clicked
     */
    class Remove(
        target: Playlist,
        onDismissRequest: () -> Unit,
        val onConfirmClick: () -> Unit,
    ): PlaylistDialog(target, onDismissRequest)
}

/** Display the appropriate [Playlist]-related dialog, as identified by [dialogState]. */
@Composable fun PlaylistDialogShower(
    dialogState: PlaylistDialog?,
    modifier: Modifier = Modifier,
) = when (dialogState) {
    null -> {}
    is Rename -> NamingDialog(
        modifier = modifier,
        title = stringResource(R.string.default_rename_dialog_title),
        state = dialogState)
    is FileChooser -> SystemFileChooser(onFilesSelected = dialogState.onFilesChosen)
    is PlaylistOptions -> PlaylistOptionsDialog(
        modifier = modifier,
        state = dialogState)
    is Remove -> ConfirmRemoveDialog(
        modifier = modifier,
        playlistName = dialogState.target.name,
        onDismissRequest = dialogState.onDismissRequest,
        onConfirmClick = dialogState.onConfirmClick)
}

/**
 * Show a dialog that contains an inner [PlaylistOptionsView] section to
 * alter a [Playlist]'s shuffle and track order. If the playlist's
 * [Playlist.isSingleTrack] property is true, then a system file picker
 * will show first to allow the user to choose extra files to add to the
 * single track in order to create a multi-track playlist.
 *
 * @param state A [PlaylistOptions] instance containing
 *     the state and callbacks to use for the dialog
 * @param modifier The [Modifier] to use for the dialog window
 */
@Composable fun PlaylistOptionsDialog(
    state: PlaylistOptions,
    modifier: Modifier = Modifier,
) = SoundAuraDialog(
    modifier = modifier,
    width = DialogWidth.MatchToScreenSize(),
    titleLayout = @Composable {
        Row(modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 12.dp,
                     start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            MarqueeText(text = state.target.name,
                        style = MaterialTheme.typography.h6)
        }
    }, onDismissRequest = state.onDismissRequest,
    onConfirm = state.onFinishClick,
) {
    PlaylistOptionsView(
        shuffleEnabled = state.shuffleEnabled,
        onShuffleClick = state.onShuffleSwitchClick,
        mutablePlaylist = state.mutablePlaylist,
        onAddButtonClick = state.onAddFilesClick)
}

/** Show a dialog to confirm the removal of the [Playlist] identified by [playlistName]. */
@Composable fun ConfirmRemoveDialog(
    playlistName: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    onConfirmClick: () -> Unit
) = SoundAuraDialog(
    modifier = modifier,
    onDismissRequest = onDismissRequest,
    title = stringResource(R.string.confirm_remove_title, playlistName),
    text = stringResource(R.string.confirm_remove_message),
    confirmText = stringResource(R.string.remove),
    onConfirm = {
        onConfirmClick()
        onDismissRequest()
    })