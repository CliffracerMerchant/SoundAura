/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.addbutton

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cliffracertech.soundaura.R
import com.cliffracertech.soundaura.dialog.NamingState
import com.cliffracertech.soundaura.dialog.ValidatedNamingState
import com.cliffracertech.soundaura.library.MutablePlaylist
import com.cliffracertech.soundaura.model.database.PlaylistNameValidator
import com.cliffracertech.soundaura.model.database.TrackNamesValidator
import com.cliffracertech.soundaura.model.Validator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A type whose subtypes represent the possible steps in an add local files dialog.
 *
 * The abstract property [onDismissRequest] must be overridden in subclasses,
 * while the properties [wasNavigatedForwardTo], [titleResId], and [buttons]
 * should be overridden when appropriate.
 */
sealed class AddLocalFilesDialogStep {
    /** Whether the step is ahead of a previous step. Final steps (i.e. ones
     * that end with a finish instead of a next button) should override this
     * value to return true, while intermediate steps should override it to
     * return true or false depending on whether it was reached by proceeding
     * from a previous step or going back from a successive step. This value
     * is not crucial to functionality, but will allow enter/exit animations
     * between steps to be more precise.  */
    open val wasNavigatedForwardTo = false

    /** The callback that should be invoked when the dialog has been requested
     * to be dismissed via a tap outside the dialog's bounds, a system back
     * button press, or a system back gesture has been performed. */
    abstract val onDismissRequest: () -> Unit

    /**
     * A container of state for a visible dialog button.
     *
     * @param textResId A string resource id that points to the string
     *     to use for the button's text.
     * @param isEnabledProvider A callback that returns whether or not
     *     the button should be enabled when invoked.
     * @param onClick The callback to use for when the button is clicked
     */
    class ButtonInfo(
        @StringRes
        val textResId: Int,
        val isEnabledProvider: () -> Boolean = { true },
        val onClick: () -> Unit)

    /** The string resource that points to the string to use for the dialog's title. */
    open val titleResId: Int = 0

    /** A [List] of [ButtonInfo]s that describes the dialog step's buttons. */
    open val buttons: List<ButtonInfo> = emptyList()

    /**
     * Files are being chosen via the system file picker.
     *
     * @param onFilesSelected The callback that will be invoked when files have been chosen
     */
    class SelectingFiles(
        override val onDismissRequest: () -> Unit,
        val onFilesSelected: (List<Uri>) -> Unit,
    ): AddLocalFilesDialogStep()

    /**
     * A question about whether to add multiple files as separate tracks
     * or as files within a single playlist is being presented.
     *
     * @param onCancelClick The callback invoked when the dialog's cancel button is clicked
     * @param onAddIndividuallyClick The callback that is invoked when the
     *     dialog's option to add the files as individual tracks is chosen
     * @param onAddAsPlaylistClick The callback that is invoked when dialog's
     *     option to add the files as the contents of a single playlist is chosen
     */
    class AddIndividuallyOrAsPlaylistQuery(
        override val onDismissRequest: () -> Unit,
        private val onCancelClick: () -> Unit,
        private val onAddIndividuallyClick: () -> Unit,
        private val onAddAsPlaylistClick: () -> Unit,
    ): AddLocalFilesDialogStep() {
        override val titleResId = R.string.add_local_files_as_playlist_or_tracks_title
        val textResId = R.string.add_local_files_as_playlist_or_tracks_question
        override val buttons = listOf(
            ButtonInfo(R.string.cancel, onClick = onCancelClick),
            ButtonInfo(R.string.add_local_files_individually_option, onClick = onAddIndividuallyClick),
            ButtonInfo(R.string.add_local_files_as_playlist_option, onClick = onAddAsPlaylistClick))
    }

    /**
     * Text fields for each track are being presented to the user to allow them
     * to name each added track. The property [names] should be used as the
     * list of current names to display in each text field. The property
     * [errors] is a [List] of [Boolean] values, each value of which represents
     * whether the same-indexed name in [names] is invalid. [message] updates
     * with the most recent [Validator.Message] concerning the input names.
     * Changed within any of the text fields should be connected to [onNameChange].
     *
     * @param validator The [TrackNamesValidator] instance that will be used
     *     to validate the track names
     * @param coroutineScope The [CoroutineScope] that will be used for background work
     * @param onBackClick The callback that is invoked when the dialog's back button is clicked
     * @param onFinish The callback that will be invoked when the dialog's
     *     finish button is clicked and none of the track names are invalid
     */
    class NameTracks(
        private val validator: TrackNamesValidator,
        private val coroutineScope: CoroutineScope,
        override val onDismissRequest: () -> Unit,
        onBackClick: () -> Unit,
        private val onFinish: (List<String>) -> Unit,
    ): AddLocalFilesDialogStep() {
        override val wasNavigatedForwardTo = true
        private var confirmJob: Job? = null

        override val titleResId = R.string.add_local_files_as_tracks_dialog_title
        override val buttons = listOf(
            ButtonInfo(R.string.back, onClick = onBackClick),
            ButtonInfo(
                textResId = R.string.finish,
                isEnabledProvider = { message?.isError != true},
                onClick = {
                    confirmJob = confirmJob ?: coroutineScope.launch {
                        val newTrackNames = validator.validate()
                        if (newTrackNames != null)
                            onFinish(newTrackNames)
                        confirmJob = null
                    }
                }))

        val names by validator::values
        val errors by validator::errors
        val message by validator::message
        val onNameChange = validator::setValue
    }

    /**
     * A text field to name a new playlist is being presented.
     *
     * @param wasNavigatedForwardTo Whether the step was reached by proceeding
     *     forward from a previous step (as opposed to going backwards from a
     *     following step)
     * @param validator The [PlaylistNameValidator] instance that will be
     *     used to validate the playlist name
     * @param coroutineScope The [CoroutineScope] that will be used for background work
     * @param onBackClick The callback that will be invoked when the dialog's back button is clicked
     * @param onNameValidated The callback that will be invoked when the
     *     dialog's finish button is clicked and the name is valid
     */
    class NamePlaylist(
        override val wasNavigatedForwardTo: Boolean,
        validator: PlaylistNameValidator,
        coroutineScope: CoroutineScope,
        override val onDismissRequest: () -> Unit,
        onBackClick: () -> Unit,
        onNameValidated: (String) -> Unit,
    ): AddLocalFilesDialogStep(), NamingState by ValidatedNamingState(
        validator, coroutineScope, onNameValidated, onBackClick)
   {
       override val titleResId = R.string.add_local_files_as_playlist_dialog_title
       override val buttons = listOf(
           ButtonInfo(R.string.back, onClick = onBackClick),
           ButtonInfo(
               textResId = R.string.next,
               isEnabledProvider = { message?.isError != true },
               onClick = ::finalize))
   }

    /**
     * A shuffle toggle switch and a reorder track widget are being presented
     * to allow these playlist settings to be changed. The on/off state of the
     * playlist's shuffle is provided via [shuffleEnabled]. Clicks on the shuffle
     * switch should be connected to [onShuffleSwitchClick]. The provided [mutablePlaylist]
     * can be used in a [com.cliffracertech.soundaura.library.PlaylistOptionsView].
     *
     * @param tracks The [List] of [Uri]s that represent the new playlist's tracks
     * @param onBackClick The callback that will be invoked when the dialog's back button is clicked
     * @param onFinish The callback that will be invoked when the dialog's
     *     finish button is clicked. The current shuffle and track ordering
     *     as passed as arguments.
     */
    class PlaylistOptions(
        tracks: List<Uri>,
        override val onDismissRequest: () -> Unit,
        onBackClick: () -> Unit,
        private val onFinish: (shuffleEnabled: Boolean, newTrackList: List<Uri>) -> Unit,
    ): AddLocalFilesDialogStep() {
        var shuffleEnabled by mutableStateOf(false)
            private set
        val onShuffleSwitchClick = { shuffleEnabled = !shuffleEnabled }
        val mutablePlaylist = MutablePlaylist(tracks)

        override val wasNavigatedForwardTo = true
        override val titleResId = R.string.configure_playlist_dialog_title
        override val buttons = listOf(
            ButtonInfo(R.string.back, onClick = onBackClick),
            ButtonInfo(R.string.finish, onClick = {
                onFinish(shuffleEnabled, mutablePlaylist.applyChanges())
            }))
    }
}