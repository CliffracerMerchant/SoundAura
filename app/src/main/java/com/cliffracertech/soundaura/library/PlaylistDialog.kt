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
import androidx.compose.material.Text
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
import com.cliffracertech.soundaura.library.PlaylistDialog.PlaylistOptions
import com.cliffracertech.soundaura.library.PlaylistDialog.Remove
import com.cliffracertech.soundaura.library.PlaylistDialog.Rename
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
     * The 'playlist options' dialog for a playlist. The properties [shuffleEnabled]
     * and [onShuffleSwitchClick] can be used for the current state of and onClick
     * callback, respectively, for a 'shuffle enabled' toggle. The value of
     * [mutablePlaylist] should be used as the same-named parameter for a
     * [PlaylistOptionsView] if it is not null. If the value of [mutablePlaylist]
     * is null, then a file chooser should be shown instead, with the chosen
     * [Uri]s being passed to a [onExtraFilesChosen] call.
     *
     * @param target The [Playlist] that is the target of the dialog
     * @param shuffleEnabled Whether shuffle is initially enabled for the [target]
     * @param playlistTracks The [List] of [Uri]s that represents the tracks in the [target]
     * @param onDismissRequest The callback that should be invoked when the dialog's
     *     cancel button is clicked or a back button click or gesture is performed
     * @param onConfirm The callback that will be invoked when [onFinishClick]
     *     is invoked. The new values for the playlist's
     *     shuffle and tracks, as set within the dialog, are provided.
     */
    class PlaylistOptions(
        target: Playlist,
        shuffleEnabled: Boolean,
        private val playlistTracks: List<Uri>,
        onDismissRequest: () -> Unit,
        private val onConfirm: (
                shuffleEnabled: Boolean,
                newTrackList: List<Uri>,
            ) -> Unit,
    ): PlaylistDialog(target, onDismissRequest) {
        var shuffleEnabled by mutableStateOf(shuffleEnabled)
            private set

        val onShuffleSwitchClick = { this.shuffleEnabled = !this.shuffleEnabled }

        fun onExtraFilesChosen(files: List<Uri>) {
            if (files.isEmpty())
                onDismissRequest()
            mutablePlaylist = MutablePlaylist(playlistTracks + files)
        }

        var mutablePlaylist by mutableStateOf(
                if (target.isSingleTrack) null
                else MutablePlaylist(playlistTracks))
            private set

        val onFinishClick = {
            val newList = mutablePlaylist?.applyChanges()
            if (newList != null)
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
) {
    val mutablePlaylist = state.mutablePlaylist
    if (mutablePlaylist == null)
        FileChooser(onFilesSelected = state::onExtraFilesChosen)
    else SoundAuraDialog(
        modifier = modifier.restrictWidthAccordingToSizeClass(),
        useDefaultWidth = false,
        titleLayout = @Composable {
            Row(modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 12.dp,
                         start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                val style = MaterialTheme.typography.h6
                MarqueeText(state.target.name, style = style)
                Text(stringResource(R.string.playlist_options_dialog_title), style = style)
            }
        }, onDismissRequest = state.onDismissRequest,
        onConfirm = state.onFinishClick,
    ) {
        PlaylistOptionsView(
            shuffleEnabled = state.shuffleEnabled,
            mutablePlaylist = mutablePlaylist,
            onShuffleSwitchClick = state.onShuffleSwitchClick)
    }
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