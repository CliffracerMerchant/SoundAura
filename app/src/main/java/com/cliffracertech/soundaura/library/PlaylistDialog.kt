/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.library

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
import com.cliffracertech.soundaura.model.database.Track
import com.cliffracertech.soundaura.ui.MarqueeText

/** The subclasses of PlaylistDialog (i.e. [Rename], [PlaylistOptions],
 * and [Remove]) contain all of the state and callbacks needed to display
 * a [Playlist] related dialog. The [onDismissRequest] property should be
 * used for the displayed dialog's onDismissRequest. */
sealed class PlaylistDialog(
    val target: Playlist,
    val onDismissRequest: () -> Unit,
) {
    /**
     * The rename dialog for a playlist.
     *
     * @param target The [Playlist] that is the target of the dialog
     * @param namingState A [ValidatedNamingState] instance. [Rename]
     *     will implement [NamingState] using this, and can therefore
     *     be used for the state parameter in a [RenameDialog].
     * @param onDismissRequest The callback that should be invoked
     *     when the dialog's cancel button is clicked or a back
     *     button click or gesture is performed
     */
    class Rename(
        target: Playlist,
        namingState: ValidatedNamingState,
        onDismissRequest: () -> Unit,
    ): PlaylistDialog(target, onDismissRequest),
       NamingState by namingState

    /**
     * The 'file chooser' step. This step can appear when the add button of
     * the playlist options step is clicked, or when the 'create playlist'
     * option is selected for a single track playlist. A [List] of the [Uri]s
     * of the chosen files should be passed to a [onFilesChosen] call.
     *
     * @param target The [Playlist] that is the target of the dialog
     * @param messageHandler A [MessageHandler] instance that will be used to show messages to the user
     * @param currentTracks A [List] of the [Track]s that are already in the playlist
     * @param onDismissRequest The callback that should be invoked when
     *     a back button click or gesture is performed
     * @param onChosenFilesValidated The callback that will be invoked
     *     inside [onFilesChosen] if the files are validated
     */
    class FileChooser(
        target: Playlist,
        private val messageHandler: MessageHandler,
        currentTracks: List<Track>,
        onDismissRequest: () -> Unit,
        private val onChosenFilesValidated: (List<Uri>) -> Unit,
    ): PlaylistDialog(target, onDismissRequest) {
        val onFilesChosen: (List<Uri>) -> Unit = { chosenFiles ->
            if (chosenFiles.isEmpty()) {
                onDismissRequest()
                // If no files were chosen at all, then the user
                // must have backed out of the file chooser intentionally
            } else {
                val validFiles = if (currentTracks.size == 1) {
                        chosenFiles - currentTracks.first().uri
                    } else {
                        val currentTrackSet = HashSet<Uri>(currentTracks.size)
                        for (track in currentTracks)
                            currentTrackSet.add(track.uri)
                        chosenFiles - currentTrackSet
                    }
                if (validFiles.isEmpty()) {
                    onDismissRequest()
                    messageHandler.postMessage(R.string.file_chooser_no_valid_tracks_error_message)
                } else {
                    val rejectedCount = chosenFiles.size - validFiles.size
                    if (rejectedCount > 0) messageHandler.postMessage(StringResource(
                        R.string.file_chooser_invalid_tracks_warning_message, rejectedCount))
                    onChosenFilesValidated(validFiles)
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
     * @param playlistTracks The [List] of [Track]s in the target [Playlist]
     * @param shuffleEnabled Whether shuffle is initially enabled for the [target]
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
        private val playlistTracks: List<Track>,
        shuffleEnabled: Boolean,
        onDismissRequest: () -> Unit,
        val onAddFilesClick: () -> Unit,
        private val onConfirm: (
            shuffleEnabled: Boolean,
            newTrackList: List<Track>,
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
     * The 'boost volume' dialog for a playlist. The property [volumeBoost]
     * should be used as the current value for a slider with a range of 0 to
     * 30dB (i.e. the supported range for the volume boost feature). Changes
     * in the slider's value should be connected to the [onSliderDrag]
     * property. Clicks on the dialog's confirmatory button should be
     * connected to the [onConfirmClick] property.
     *
     * @param target The [Playlist] that is the target of the dialog
     * @param onDismissRequest The callback that should be invoked when the dialog's
     *     cancel button is clicked or a back button click or gesture is performed
     * @param onConfirm The callback that should be invoked when the dialog's
     *     confirmatory button is clicked. The Int parameter will be the current
     *     value of the volume boost slider in the range of [0, 30].
     */
    class BoostVolume(
        target: Playlist,
        onDismissRequest: () -> Unit,
        private val onConfirm: (Int) -> Unit,
    ): PlaylistDialog(target, onDismissRequest) {
        var volumeBoost by mutableStateOf(target.volumeBoostDb.toFloat())
            private set

        val onSliderDrag = { newBoost: Float -> volumeBoost = newBoost }

        val onConfirmClick = { onConfirm(volumeBoost.toInt().coerceIn(0, 30)) }
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
        onDismissRequest = dialogState.onDismissRequest,
        modifier = modifier,
        title = stringResource(R.string.default_rename_dialog_title),
        state = dialogState)
    is FileChooser -> SystemFileChooser(onFilesSelected = dialogState.onFilesChosen)
    is PlaylistOptions -> PlaylistOptionsDialog(
        modifier = modifier,
        state = dialogState)
    is PlaylistDialog.BoostVolume -> BoostVolumeDialog(
        modifier = modifier,
        state = dialogState)
    is Remove -> ConfirmRemoveDialog(
        modifier = modifier,
        playlistName = dialogState.target.name,
        isSingleTrack = dialogState.target.isSingleTrack,
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

/** Show a dialog to change a playlist's volume boost. */
@Composable fun BoostVolumeDialog(
    state: PlaylistDialog.BoostVolume,
    modifier: Modifier = Modifier,
) = SoundAuraDialog(
    modifier = modifier,
    width = DialogWidth.MatchToScreenSize(),
    title = stringResource(R.string.volume_boost_description),
    onDismissRequest = state.onDismissRequest,
) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(stringResource(R.string.volume_boost_explanation_part_1),
             style = MaterialTheme.typography.body1,
             textAlign = TextAlign.Justify)
        Text(stringResource(R.string.volume_boost_explanation_part_2),
             modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
                                .align(Alignment.CenterHorizontally),
             style = MaterialTheme.typography.body1,)
        Slider(
            value = state.volumeBoost,
            onValueChange = state.onSliderDrag,
            modifier = Modifier.weight(1f),
            valueRange = 0f..30f,
            steps = 30)
        Text("+\u2009${state.volumeBoost.toInt().coerceIn(0, 30)}\u2009dB",
             modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                                .align(Alignment.CenterHorizontally),
             style = MaterialTheme.typography.h6)
    }
}


/** Show a dialog to confirm the removal of the [Playlist] identified by [playlistName]. */
@Composable fun ConfirmRemoveDialog(
    playlistName: String,
    isSingleTrack: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    onConfirmClick: () -> Unit
) = SoundAuraDialog(
    modifier = modifier,
    onDismissRequest = onDismissRequest,
    title = stringResource(R.string.confirm_remove_title, playlistName),
    text = stringResource(
        if (isSingleTrack) R.string.confirm_remove_track_message
        else               R.string.confirm_remove_playlist_message),
    confirmText = stringResource(R.string.remove),
    onConfirm = {
        onConfirmClick()
        onDismissRequest()
    })