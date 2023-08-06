/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.content.Context
import androidx.core.net.toUri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cliffracertech.soundaura.addbutton.AddLocalFilesDialogStep
import com.cliffracertech.soundaura.addbutton.AddPlaylistButtonViewModel
import com.cliffracertech.soundaura.addbutton.getDisplayName
import com.cliffracertech.soundaura.model.MessageHandler
import com.cliffracertech.soundaura.model.TestPermissionHandler
import com.cliffracertech.soundaura.model.Validator
import com.cliffracertech.soundaura.model.database.PlaylistDao
import com.cliffracertech.soundaura.model.database.SoundAuraDatabase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddPlaylistButtonViewModelTests {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val permissionHandler = TestPermissionHandler()
    private val coroutineScope = TestCoroutineScope()
    private val messageHandler = MessageHandler()

    private lateinit var instance: AddPlaylistButtonViewModel
    private lateinit var db: SoundAuraDatabase
    private lateinit var playlistDao: PlaylistDao

    @Before fun init() {
        db = Room.inMemoryDatabaseBuilder(context, SoundAuraDatabase::class.java).build()
        playlistDao = db.playlistDao()
        instance = AddPlaylistButtonViewModel(
            context, permissionHandler, playlistDao, messageHandler, coroutineScope)
    }

    @After fun clean_up() {
        db.close()
        coroutineScope.cancel()
    }

    private val testUris = List(3) { "uri $it".toUri() }
    private val selectingFilesStep get() = instance.dialogStep as AddLocalFilesDialogStep.SelectingFiles
    private val addIndividuallyOrAsPlaylistStep get() =
        instance.dialogStep as AddLocalFilesDialogStep.AddIndividuallyOrAsPlaylistQuery
    private val nameTracksStep get() = instance.dialogStep as AddLocalFilesDialogStep.NameTracks
    private val namePlaylistStep get() = instance.dialogStep as AddLocalFilesDialogStep.NamePlaylist
    private val playlistOptionsStep get() = instance.dialogStep as AddLocalFilesDialogStep.PlaylistOptions

    private val AddLocalFilesDialogStep.buttonTexts get() =
        buttons.map(AddLocalFilesDialogStep.ButtonInfo::textResId)
    private val AddLocalFilesDialogStep.cancelButton get() =
        buttons.find { it.textResId == R.string.cancel }!!
    private val AddLocalFilesDialogStep.backButton get() =
        buttons.find { it.textResId == R.string.back }!!
    private val AddLocalFilesDialogStep.nextButton get() =
        buttons.find { it.textResId == R.string.next }!!
    private val AddLocalFilesDialogStep.finishButton get() =
        buttons.find { it.textResId == R.string.finish }!!
    private val AddLocalFilesDialogStep.AddIndividuallyOrAsPlaylistQuery.addIndividuallyButton get() =
        buttons.find { it.textResId == R.string.add_local_files_individually_option }!!
    private val AddLocalFilesDialogStep.AddIndividuallyOrAsPlaylistQuery.addAsPlaylistButton get() =
        buttons.find { it.textResId == R.string.add_local_files_as_playlist_option }!!

    private fun goto_add_individually_or_as_playlist_step() {
        instance.onClick()
        selectingFilesStep.onFilesSelected(testUris)
    }
    private fun goto_name_tracks_step_with_one_file() {
        instance.onClick()
        selectingFilesStep.onFilesSelected(testUris.subList(0, 1))
    }
    private fun goto_name_tracks_step_with_multiple_files() {
        goto_add_individually_or_as_playlist_step()
        addIndividuallyOrAsPlaylistStep.addIndividuallyButton.onClick()
    }
    private fun goto_name_playlist_step() {
        goto_add_individually_or_as_playlist_step()
        addIndividuallyOrAsPlaylistStep.addAsPlaylistButton.onClick()
    }
    private suspend fun goto_playlist_options_step() {
        goto_name_playlist_step()
        namePlaylistStep.nextButton.onClick()
        waitUntil { instance.dialogStep is AddLocalFilesDialogStep.PlaylistOptions }
    }

    @Test fun onClick_opens_file_selector() {
        assertThat(instance.dialogStep).isNull()
        instance.onClick()
        assertThat(instance.dialogStep).isInstanceOf(AddLocalFilesDialogStep.SelectingFiles::class)
        assertThat(selectingFilesStep.buttonTexts).isEmpty()
    }

    @Test fun file_selector_back_navigation() {
        instance.onClick()
        selectingFilesStep.onDismissRequest()
        assertThat(instance.dialogStep).isNull()
    }

    @Test fun selecting_single_file_skips_query() {
        goto_name_tracks_step_with_one_file()
        assertThat(instance.dialogStep).isInstanceOf(AddLocalFilesDialogStep.NameTracks::class)
        assertThat(instance.dialogStep!!.wasNavigatedForwardTo).isTrue()
        assertThat(nameTracksStep.buttonTexts)
            .containsExactly(R.string.cancel, R.string.finish).inOrder()
    }

    @Test fun naming_single_track_back_navigation() {
        goto_name_tracks_step_with_one_file()
        nameTracksStep.cancelButton.onClick()
        assertThat(instance.dialogStep).isNull()

        goto_name_tracks_step_with_one_file()
        nameTracksStep.onDismissRequest()
        assertThat(instance.dialogStep).isNull()
    }

    @Test fun selecting_multiple_files_goes_to_query() {
        goto_add_individually_or_as_playlist_step()
        assertThat(instance.dialogStep).isInstanceOf(
            AddLocalFilesDialogStep.AddIndividuallyOrAsPlaylistQuery::class)
        assertThat(instance.dialogStep!!.wasNavigatedForwardTo).isFalse()
        assertThat(addIndividuallyOrAsPlaylistStep.buttonTexts)
            .containsExactly(
                R.string.cancel,
                R.string.add_local_files_individually_option,
                R.string.add_local_files_as_playlist_option
            ).inOrder()
    }

    @Test fun query_back_navigation() {
        goto_add_individually_or_as_playlist_step()
        addIndividuallyOrAsPlaylistStep.onDismissRequest()
        assertThat(instance.dialogStep).isNull()

        goto_add_individually_or_as_playlist_step()
        addIndividuallyOrAsPlaylistStep.cancelButton.onClick()
        assertThat(instance.dialogStep).isNull()
    }

    @Test fun clicking_add_individually_goes_to_name_tracks_step() {
        goto_name_tracks_step_with_multiple_files()
        assertThat(instance.dialogStep).isInstanceOf(AddLocalFilesDialogStep.NameTracks::class)
        assertThat(instance.dialogStep!!.wasNavigatedForwardTo).isTrue()
        assertThat(nameTracksStep.buttonTexts)
            .containsExactly(R.string.back, R.string.finish).inOrder()
    }

    @Test fun name_tracks_back_navigation() {
        goto_name_tracks_step_with_multiple_files()
        nameTracksStep.onDismissRequest()
        assertThat(instance.dialogStep).isNull()

        goto_name_tracks_step_with_multiple_files()
        nameTracksStep.backButton.onClick()
        assertThat(instance.dialogStep).isInstanceOf(
            AddLocalFilesDialogStep.AddIndividuallyOrAsPlaylistQuery::class)
        assertThat(instance.dialogStep!!.wasNavigatedForwardTo).isFalse()
    }

    @Test fun clicking_add_as_playlist_goes_to_name_playlist_step() {
        goto_name_playlist_step()
        assertThat(instance.dialogStep).isInstanceOf(AddLocalFilesDialogStep.NamePlaylist::class)
        assertThat(instance.dialogStep!!.wasNavigatedForwardTo).isTrue()
        assertThat(namePlaylistStep.buttonTexts)
            .containsExactly(R.string.back, R.string.next).inOrder()
    }

    @Test fun name_playlist_back_navigation() {
        goto_name_playlist_step()
        namePlaylistStep.onDismissRequest()
        assertThat(instance.dialogStep).isNull()

        goto_name_playlist_step()
        namePlaylistStep.backButton.onClick()
        assertThat(instance.dialogStep).isInstanceOf(
            AddLocalFilesDialogStep.AddIndividuallyOrAsPlaylistQuery::class)
        assertThat(instance.dialogStep!!.wasNavigatedForwardTo).isFalse()
    }

    @Test fun confirming_playlist_name_goes_to_playlist_options_step() = runTest {
        goto_playlist_options_step()
        assertThat(instance.dialogStep).isInstanceOf(AddLocalFilesDialogStep.PlaylistOptions::class)
        assertThat(instance.dialogStep!!.wasNavigatedForwardTo).isTrue()
        assertThat(playlistOptionsStep.buttonTexts)
            .containsExactly(R.string.back, R.string.finish).inOrder()
    }

    @Test fun playlist_options_back_navigation() = runTest {
        goto_playlist_options_step()
        playlistOptionsStep.onDismissRequest()
        assertThat(instance.dialogStep).isNull()

        goto_playlist_options_step()
        playlistOptionsStep.backButton.onClick()
        assertThat(instance.dialogStep).isInstanceOf(
            AddLocalFilesDialogStep.NamePlaylist::class)
        assertThat(instance.dialogStep!!.wasNavigatedForwardTo).isFalse()
    }

    @Test fun validating_track_names() = runTest {
        val existingTrackName = "existing track name"
        playlistDao.insertPlaylist(existingTrackName, false, testUris)
        advanceUntilIdle()

        goto_name_tracks_step_with_multiple_files()
        assertThat(nameTracksStep.errors).containsExactly(false, false, false).inOrder()
        assertThat(nameTracksStep.message).isNull()

        // Check for name already used error
        nameTracksStep.onNameChange(0, existingTrackName)
        advanceUntilIdle()
        assertThat(nameTracksStep.errors).containsExactly(true, false, false).inOrder()
        assertThat(nameTracksStep.message).isInstanceOf(Validator.Message.Error::class)

        // Check that error changes back to false when name is changed
        nameTracksStep.onNameChange(0, "new name")
        advanceUntilIdle()
        assertThat(nameTracksStep.message).isNull()
        assertThat(nameTracksStep.errors).containsExactly(false, false, false).inOrder()

        // Check that both track names are invalid when they match
        nameTracksStep.onNameChange(1, "new name")
        advanceUntilIdle()
        assertThat(nameTracksStep.errors).containsExactly(true, true, false).inOrder()
        assertThat(nameTracksStep.message).isInstanceOf(Validator.Message.Error::class)

        // Check that error changes back to false when names no longer match
        nameTracksStep.onNameChange(1, "new name 2")
        advanceUntilIdle()
        assertThat(nameTracksStep.errors).containsExactly(false, false, false).inOrder()
        assertThat(nameTracksStep.message).isNull()

        // Check for blank name error
        nameTracksStep.onNameChange(2, "")
        advanceUntilIdle()
        assertThat(nameTracksStep.errors).containsExactly(false, false, true).inOrder()
        assertThat(nameTracksStep.message).isInstanceOf(Validator.Message.Error::class)

        // Check that error changes back to false when name is no longer blank
        nameTracksStep.onNameChange(2, "non-blank name")
        advanceUntilIdle()
        assertThat(nameTracksStep.errors).containsExactly(false, false, false).inOrder()
        assertThat(nameTracksStep.message).isNull()
    }

    @Test fun validating_playlist_names() = runTest {
        val playlistName = testUris.first().getDisplayName(context) + " playlist"
        playlistDao.insertPlaylist(playlistName, false, testUris)
        waitUntil { playlistDao.getPlaylistNames().first().isNotEmpty() }

        goto_name_playlist_step()
        waitUntil { namePlaylistStep.message != null }
        assertThat(namePlaylistStep.message).isInstanceOf(Validator.Message.Error::class)

        namePlaylistStep.onNameChange("new playlist 2")
        waitUntil { namePlaylistStep.message == null }
        assertThat(namePlaylistStep.message).isNull()

        namePlaylistStep.onNameChange("")
        waitUntil { namePlaylistStep.message != null }
        assertThat(namePlaylistStep.message).isInstanceOf(Validator.Message.Error::class)
    }

    @Test fun name_tracks_step_finish_closes_dialog_and_adds_tracks() = runTest{
        goto_name_tracks_step_with_multiple_files()
        assertThat(playlistDao.getPlaylistNames().first()).isEmpty()
        val newTrack2Name = "new name"
        nameTracksStep.onNameChange(1, newTrack2Name)
        nameTracksStep.finishButton.onClick()
        waitUntil { instance.dialogStep == null } // advanceUntilIdle doesn't work here for some reason
        assertThat(instance.dialogStep).isNull()

        advanceUntilIdle()
        val names = playlistDao.getPlaylistNames().first()
        val expectedNames = listOf(
            testUris[0].getDisplayName(context),
            newTrack2Name,
            testUris[2].getDisplayName(context))
        assertThat(names).containsExactlyElementsIn(expectedNames)

        expectedNames.forEachIndexed { index, name ->
            val tracks = playlistDao.getPlaylistTracks(name)
            assertThat(tracks).containsExactly(testUris[index])
        }
    }

    @Test fun playlist_options_finish_closes_dialog_and_adds_playlist() = runTest {
        var names: List<String> = emptyList()
        playlistDao.getPlaylistNames()
            .onEach { names = it }
            .launchIn(coroutineScope)

        // default playlist name, shuffle, and track order
        goto_playlist_options_step()
        playlistOptionsStep.finishButton.onClick()
        advanceUntilIdle()
        assertThat(instance.dialogStep).isNull()

        val name1 = testUris.first().getDisplayName(context) + " playlist"
        waitUntil { names.isNotEmpty() } // advanceUntilIdle doesn't work here for some reason
        assertThat(names).containsExactly(name1)
        assertThat(playlistDao.getPlaylistShuffle(name1)).isFalse()
        assertThat(playlistDao.getPlaylistTracks(name1))
            .containsExactlyElementsIn(testUris).inOrder()

        // non-default playlist name, shuffle, and track order
        val name2 = "new playlist name"
        goto_name_playlist_step()
        namePlaylistStep.onNameChange(name2)
        namePlaylistStep.nextButton.onClick()
        waitUntil { instance.dialogStep is AddLocalFilesDialogStep.PlaylistOptions } // advanceUntilIdle doesn't work here for some reason
        playlistOptionsStep.onShuffleSwitchClick()
        playlistOptionsStep.mutablePlaylist.moveTrack(1, 2)
        playlistOptionsStep.mutablePlaylist.moveTrack(0, 1)
        playlistOptionsStep.finishButton.onClick()
        assertThat(instance.dialogStep).isNull()

        waitUntil { playlistDao.getPlaylistNames().first().size == 2 } // advanceUntilIdle doesn't work here for some reason
        names = playlistDao.getPlaylistNames().first()
        assertThat(names).containsExactly(name1, name2)
        assertThat(playlistDao.getPlaylistShuffle(name2)).isTrue()
        assertThat(playlistDao.getPlaylistTracks(name2))
            .containsExactly(testUris[2], testUris[0], testUris[1]).inOrder()
    }
}