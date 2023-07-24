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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cliffracertech.soundaura.R
import com.cliffracertech.soundaura.addbutton.FileChooser
import com.cliffracertech.soundaura.dialog.NamingState
import com.cliffracertech.soundaura.dialog.RenameDialog
import com.cliffracertech.soundaura.dialog.SoundAuraDialog
import com.cliffracertech.soundaura.dialog.ValidatedNamingState
import com.cliffracertech.soundaura.library.PlaylistDialog.PlaylistOptions
import com.cliffracertech.soundaura.library.PlaylistDialog.Remove
import com.cliffracertech.soundaura.library.PlaylistDialog.Rename
import com.cliffracertech.soundaura.model.database.PlaylistNameValidator
import com.cliffracertech.soundaura.restrictWidthAccordingToSizeClass
import com.cliffracertech.soundaura.ui.MarqueeText
import kotlinx.collections.immutable.ImmutableList
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
     * can therefore be used as the state parameter for a [RenameDialog].
     *
     * @param target The [Playlist] that is the target of the dialog
     * @param validator The [PlaylistNameValidator] to use for validation of the new name
     * @param coroutineScope A [CoroutineScope] to use for background work.
     * @param onDismissRequest The callback that should be invoked when the dialog's
     *     cancel button is clicked or a back button click or gesture is performed
     * @param onNameValidated The callback that will be invoked upon a successful
     *     validation of the [Playlist]'s new name after [NamingState.finalize] is
     *     called with a valid name.
     */
    class Rename(
        target: Playlist,
        validator: PlaylistNameValidator,
        coroutineScope: CoroutineScope,
        onDismissRequest: () -> Unit,
        private val onNameValidated: suspend (String) -> Unit,
    ): PlaylistDialog(target, onDismissRequest),
       NamingState by ValidatedNamingState(
           validator, coroutineScope, onNameValidated, onDismissRequest)

    /**
     * The 'playlist options' dialog for a playlist.
     *
     * @param target The [Playlist] that is the target of the dialog
     * @param playlistShuffleEnabled Whether shuffle is enabled for the [target]
     * @param playlistTracks The [List] of [Uri]s that represents the tracks in the [target]
     * @param onDismissRequest The callback that should be invoked when the dialog's
     *     cancel button is clicked or a back button click or gesture is performed
     * @param onConfirmClick The callback that should be invoked when the dialog's
     *     confirmatory button is clicked. The new values for the playlist's shuffle
     *     and tracks, as set within the dialog, should be provided.
     */
    class PlaylistOptions(
        target: Playlist,
        val playlistShuffleEnabled: Boolean,
        val playlistTracks: ImmutableList<Uri>,
        onDismissRequest: () -> Unit,
        val onConfirmClick: (
                shuffleEnabled: Boolean,
                newTrackList: List<Uri>,
            ) -> Unit,
    ): PlaylistDialog(target, onDismissRequest)

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
    is Rename -> RenameDialog(
        modifier = modifier,
        title = stringResource(R.string.default_rename_dialog_title),
        state = dialogState)
    is PlaylistOptions -> PlaylistOptionsDialog(
        modifier = modifier,
        playlist = dialogState.target,
        shuffleEnabled = dialogState.playlistShuffleEnabled,
        tracks = dialogState.playlistTracks,
        onDismissRequest = dialogState.onDismissRequest,
        onConfirmClick = dialogState.onConfirmClick)
    is Remove -> ConfirmRemoveDialog(
        modifier = modifier,
        playlistName = dialogState.target.name,
        onDismissRequest = dialogState.onDismissRequest,
        onConfirmClick = dialogState.onConfirmClick)
}

/**
 * Show a dialog that contains an inner [PlaylistOptions] section to alter the
 * [playlist]'s shuffle and track order. If [playlist]'s [Playlist.isSingleTrack]
 * property is true, then a system file picker will show first to allow the
 * user to choose extra files to add to the single track in order to create a
 * multi-track playlist.
 *
 * @param playlist The [Playlist] whose shuffle and track order
 *     are being adjusted
 * @param shuffleEnabled Whether or not the playlist has shuffle enabled
 * @param tracks An [ImmutableList] containing the [Uri]s of the playlist's tracks
 * @param onDismissRequest The callback that will be invoked
 *     when the back button or gesture is activated or the
 *     dialog's cancel button is clicked
 * @param modifier The [Modifier] to use for the dialog window
 * @param onConfirmClick The callback that will be invoked when the dialog's
 *     confirm button is clicked. The Boolean and List<Uri> parameters
 *     are the playlist's requested shuffle value and track order.
 */
@Composable fun PlaylistOptionsDialog(
    playlist: Playlist,
    shuffleEnabled: Boolean,
    tracks: ImmutableList<Uri>,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    onConfirmClick: (shuffleEnabled: Boolean, newTrackOrder: List<Uri>) -> Unit,
) {
    var chosenUris by remember { mutableStateOf<List<Uri>?>(null) }

    if (playlist.isSingleTrack && chosenUris == null) {
        FileChooser { uris ->
            if (uris.isEmpty())
                onDismissRequest()
            chosenUris = uris
        }
    } else {
        var tempShuffleEnabled by rememberSaveable { mutableStateOf(shuffleEnabled) }
        val tempTrackOrder: MutableList<Uri> = rememberSaveable(
            /* inputs =*/ tracks, chosenUris,
            saver = listSaver({ it }, List<Uri>::toMutableStateList)
        ) {
            val newTracks = chosenUris ?: emptyList()
            tracks.toMutableStateList().apply { addAll(newTracks) }
        }
        SoundAuraDialog(
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
                    MarqueeText(playlist.name, style = style)
                    Text(stringResource(R.string.playlist_options_dialog_title), style = style)
                }
            }, onDismissRequest = onDismissRequest,
            onConfirm = { onConfirmClick(tempShuffleEnabled, tempTrackOrder) }
        ) {
            PlaylistOptions(
                shuffleEnabled = tempShuffleEnabled,
                tracks = tempTrackOrder,
                onShuffleSwitchClick = {
                    tempShuffleEnabled = !tempShuffleEnabled
                })
        }
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