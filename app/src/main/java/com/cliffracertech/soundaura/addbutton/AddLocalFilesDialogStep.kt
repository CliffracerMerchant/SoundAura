/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.addbutton

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import com.cliffracertech.soundaura.dialog.NamingState
import com.cliffracertech.soundaura.dialog.ValidatedNamingState
import com.cliffracertech.soundaura.model.database.PlaylistNameValidator
import com.cliffracertech.soundaura.model.database.TrackNamesValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** A type whose subtypes represent the possible steps in a add local files dialog. */
sealed class AddLocalFilesDialogStep {
    /** Whether the step is ahead of a previous step. Final steps (i.e. ones
     * that end with a finish instead of a next button) should override this
     * value to return true, while intermediate steps should override it to
     * return true or false depending on whether it was reached by proceeding
     * from a previous step or going back from a successive step. This value
     * is not crucial to functionality, but will allow enter/exit animations
     * between steps to be more precise.  */
    open val isAheadOfPreviousStep = false

    /** The callback that should be invoked when the
     * dialog step's back or cancel button is clicked */
    open fun onBackClick() {}

    /** The callback that should be invoked when the
     * dialog step's next or finish button is clicked */
    open fun onNextClick() {}

    /**
     * Files are being chosen via the system file picker.
     *
     * @param onFilesSelected The callback that will be invoked when files have been chosen
     */
    class SelectingFiles(
        val onFilesSelected: (List<Uri>) -> Unit,
    ): AddLocalFilesDialogStep()

    /**
     * A question about whether to add multiple files as separate tracks
     * or as files within a single playlist is being presented.
     *
     * @param onBack The callback invoked when the dialog's cancel button is clicked
     * @param onAddIndividuallyClick The callback that is invoked when the
     *     dialog's option to add the files as individual tracks is chosen
     * @param onAddAsPlaylistClick The callback that is invoked when dialog's
     *     option to add the files as the contents of a single playlist is chosen
     */
    class AddIndividuallyOrAsPlaylistQuery(
        private val onBack: () -> Unit,
        val onAddIndividuallyClick: () -> Unit,
        val onAddAsPlaylistClick: () -> Unit,
    ): AddLocalFilesDialogStep() {
        override fun onBackClick() = onBack()
    }

    /**
     * Text fields for each track are being presented to
     * the user to allow them to name each added track.
     *
     * @param onBack The callback that is invoked when the dialog's back button is clicked
     * @param validator The [TrackNamesValidator] instance that will be used
     *     to validate the track names
     * @param coroutineScope The [CoroutineScope] that will be used for background work
     * @param onFinish The callback that will be invoked when the dialog's
     *     finish button is clicked and none of the track names are invalid
     */
    class NameTracks(
        private val onBack: () -> Unit,
        private val validator: TrackNamesValidator,
        private val coroutineScope: CoroutineScope,
        private val onFinish: (List<String>) -> Unit,
    ): AddLocalFilesDialogStep() {
        private var confirmJob: Job? = null

        val names by validator::values
        val errors by validator::errors
        val message by validator::message

        fun onNameChange(index: Int, newName: String) {
            validator.setValue(index, newName)
        }

        override fun onBackClick() = onBack()

        override fun onNextClick() {
            if (confirmJob != null) return
            confirmJob = coroutineScope.launch {
                val newTrackNames = validator.validate()
                if (newTrackNames != null)
                    onFinish(newTrackNames)
                confirmJob = null
            }
        }

        override val isAheadOfPreviousStep = true
    }

    /**
     * A text field to name a new playlist is being presented.
     *
     * @param isAheadOfPreviousStep Whether the step was reached by
     *     proceeding from from a previous step (as opposed to going
     *     backwards from a successive step
     * @param onBack The callback that will be invoked when the dialog's back button is clicked
     * @param validator The [PlaylistNameValidator] instance that will be
     *     used to validate the playlist name
     * @param coroutineScope The [CoroutineScope] that will be used for background work
     * @param onFinish The callback that will be invoked when the dialog's
     *     finish button is clicked and the name is valid
     */
    class NamePlaylist(
        override val isAheadOfPreviousStep: Boolean,
        private val onBack: () -> Unit,
        private val validator: PlaylistNameValidator,
        private val coroutineScope: CoroutineScope,
        private val onFinish: (String) -> Unit,
    ): AddLocalFilesDialogStep(),
       NamingState by ValidatedNamingState(validator, coroutineScope, onFinish, onBack)
    {
        override fun onBackClick() = cancel()
        override fun onNextClick() = finalize()
    }

    /**
     * A shuffle toggle switch and a reorder track widget are being
     * presented to allow these playlist settings to be changed.
     *
     * @param onBack The callback that will be invoked when the dialog's back button is clicked
     * @param tracks The [List] of [Uri]s that represent the new playlist's tracks
     * @param onFinish The callback that will be invoked when the dialog's
     *     finish button is clicked. The current shuffle and track ordering
     *     as passed as arguments.
     */
    class PlaylistOptions(
        private val onBack: () -> Unit,
        tracks: List<Uri>,
        val onFinish: (shuffleEnabled: Boolean, newTrackOrder: List<Uri>) -> Unit,
    ): AddLocalFilesDialogStep() {
        var shuffleEnabled by mutableStateOf(false)
            private set

        fun onShuffleSwitchClick() { shuffleEnabled = !shuffleEnabled }

        val trackOrder = tracks.toMutableStateList()

        override val isAheadOfPreviousStep = true
        override fun onBackClick() = onBack()
        override fun onNextClick() = onFinish(shuffleEnabled, trackOrder)
    }

    val isSelectingFiles get() = this is SelectingFiles
    val isAddIndividuallyOrAsPlaylistQuery get() = this is AddIndividuallyOrAsPlaylistQuery
    val isNameTracks get() = this is NameTracks
    val isNamePlaylist get() = this is NamePlaylist
    val isPlaylistOptions get() = this is PlaylistOptions
}