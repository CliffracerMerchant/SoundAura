/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.addbutton

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import com.cliffracertech.soundaura.model.database.PlaylistNameValidator
import com.cliffracertech.soundaura.model.database.TrackNamesValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

sealed class AddLocalFilesDialogStep(
    val goingForward: Boolean
) {
    open fun onCancelClick() {}
    open fun onConfirmClick() {}

    class SelectingFiles(
        val onFilesSelected: (List<Uri>) -> Unit,
    ): AddLocalFilesDialogStep(goingForward = false)

    class AddIndividuallyOrAsPlaylistQuery(
        private val onCancel: () -> Unit,
        val chosenUris: List<Uri>,
        val onAddIndividuallyClick: () -> Unit,
        val onAddAsPlaylistClick: () -> Unit,
    ): AddLocalFilesDialogStep(goingForward = false) {
        override fun onCancelClick() = onCancel()
    }

    class NameTracks(
        private val onBack: () -> Unit,
        private val validator: TrackNamesValidator,
        private val coroutineScope: CoroutineScope,
        private val onFinish: (List<String>) -> Unit,
    ): AddLocalFilesDialogStep(goingForward = true) {
        private var confirmJob: Job? = null

        val namesAndErrors by validator::values
        val message by validator::message

        fun onNameChange(index: Int, newName: String) {
            validator.setValue(index, newName)
        }

        override fun onCancelClick() = onBack()

        override fun onConfirmClick() {
            if (confirmJob != null) return
            confirmJob = coroutineScope.launch {
                val newTrackNames = validator.validate()
                if (newTrackNames != null)
                    onFinish(newTrackNames)
                confirmJob = null
            }
        }
    }

    class NamePlaylist(
        goingForward: Boolean,
        private val onBack: () -> Unit,
        private val validator: PlaylistNameValidator,
        private val coroutineScope: CoroutineScope,
        private val onFinish: (String) -> Unit,
    ): AddLocalFilesDialogStep(goingForward) {
        private var confirmJob: Job? = null

        val name by validator::value
        val message by validator::message

        fun onNameChange(newName: String) {
            validator.value = newName
        }

        override fun onCancelClick() = onBack()
        override fun onConfirmClick() {
            if (confirmJob != null) return
            confirmJob = coroutineScope.launch {
                val newPlaylistName = validator.validate()
                if (newPlaylistName != null)
                    onFinish(newPlaylistName)
                confirmJob = null
            }
        }
    }

    class PlaylistOptions(
        private val onBack: () -> Unit,
        tracks: List<Uri>,
        val onFinish: (shuffleEnabled: Boolean, newTrackOrder: List<Uri>) -> Unit,
    ): AddLocalFilesDialogStep(goingForward = true) {
        var shuffleEnabled by mutableStateOf(false)
            private set

        fun onShuffleSwitchClick() { shuffleEnabled = !shuffleEnabled }

        val trackOrder = tracks.toMutableStateList()

        override fun onCancelClick() = onBack()
        override fun onConfirmClick() = onFinish(shuffleEnabled, trackOrder)
    }

    val isSelectingFiles get() = this is SelectingFiles
    val isAddIndividuallyOrAsPlaylistQuery get() = this is AddIndividuallyOrAsPlaylistQuery
    val isNameTracks get() = this is NameTracks
    val isNamePlaylist get() = this is NamePlaylist
    val isPlaylistOptions get() = this is PlaylistOptions
}