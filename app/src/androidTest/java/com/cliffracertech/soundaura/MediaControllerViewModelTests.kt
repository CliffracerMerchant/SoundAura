/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaControllerViewModelTests {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val coroutineScope = TestCoroutineScope()
    private val dataStore = PreferenceDataStoreFactory.create(scope = coroutineScope) {
        context.preferencesDataStoreFile("testDatastore")
    }

    private val testTracks = List(5) { Track("uri $it", "track $it") }
    private val testPresets = List(3) { Preset("preset $it") }
    private suspend fun addTestPresets() {
        trackDao.insert(testTracks)
        trackDao.toggleIsActive(testTracks[0].uriString)
        presetDao.savePreset(testPresets[0].name)

        trackDao.toggleIsActive(testTracks[0].uriString)
        trackDao.toggleIsActive(testTracks[1].uriString)
        trackDao.toggleIsActive(testTracks[2].uriString)
        presetDao.savePreset(testPresets[1].name)

        trackDao.toggleIsActive(testTracks[1].uriString)
        trackDao.toggleIsActive(testTracks[2].uriString)
        trackDao.toggleIsActive(testTracks[3].uriString)
        trackDao.toggleIsActive(testTracks[4].uriString)
        presetDao.savePreset(testPresets[2].name)
    }

    private lateinit var instance: MediaControllerViewModel
    private lateinit var db: SoundAuraDatabase
    private lateinit var presetDao: PresetDao
    private lateinit var trackDao: TrackDao
    private lateinit var navigationState: MainActivityNavigationState
    private lateinit var activePresetState: ActivePresetState

    @Before fun init() {
        db = Room.inMemoryDatabaseBuilder(context, SoundAuraDatabase::class.java).build()
        presetDao = db.presetDao()
        trackDao = db.trackDao()
        navigationState = MainActivityNavigationState()
        activePresetState = ActivePresetState(dataStore, trackDao, presetDao)
        val messageHandler = MessageHandler()
        instance = MediaControllerViewModel(presetDao, navigationState,
                                            activePresetState,
                                            messageHandler, trackDao)
    }

    @After fun cleanUp() {
        db.close()
        coroutineScope.cancel()
    }

    @Test fun initialState() = runTest {
        assertThat(instance.presetList).isNull()
        assertThat(instance.activePresetIsModified).isFalse()
        assertThat(instance.showingPresetSelector).isFalse()
        assertThat(instance.proposedPresetName).isNull()
        assertThat(instance.proposedPresetNameErrorMessage).isNull()
        assertThat(instance.showingUnsavedChangesWarning).isFalse()

        waitUntil { instance.presetList != null }
        assertThat(instance.presetList).isEmpty()
    }

    @Test fun presetListUpdates() = runTest {
        presetDao.savePreset(testPresets[0].name)
        waitUntil { instance.presetList?.isEmpty() == false }
        assertThat(instance.presetList)
            .containsExactly(testPresets[0])
        presetDao.savePreset(testPresets[1].name)
        presetDao.savePreset(testPresets[2].name)
        waitUntil { instance.presetList?.size == 3 }
        assertThat(instance.presetList)
            .containsExactlyElementsIn(testPresets).inOrder()
    }

    @Test fun activePresetNameUpdates() = runTest {
        addTestPresets()
        assertThat(instance.activePresetName).isNull()
        activePresetState.setName(testPresets[1].name)
        waitUntil { instance.activePresetName != null }
        assertThat(instance.activePresetName).isEqualTo(testPresets[1].name)
    }

    @Test fun activePresetIsModifiedUpdates() = runTest {
        addTestPresets()
        activePresetState.setName(testPresets[2].name)
        waitUntil { instance.activePresetName != null }
        assertThat(instance.activePresetName).isEqualTo(testPresets[2].name)
        assertThat(instance.activePresetIsModified).isFalse()

        trackDao.toggleIsActive(testTracks[2].uriString)
        waitUntil { instance.activePresetIsModified }
        assertThat(instance.activePresetIsModified).isTrue()

        trackDao.toggleIsActive(testTracks[1].uriString)
        trackDao.toggleIsActive(testTracks[3].uriString)
        trackDao.toggleIsActive(testTracks[4].uriString)
        activePresetState.setName(testPresets[1].name)
        waitUntil { instance.activePresetName == testPresets[1].name }
        assertThat(instance.activePresetIsModified).isFalse()
    }

    @Test fun showingAndClosingPresetSelector() {
        instance.onActivePresetClick()
        assertThat(instance.showingPresetSelector).isTrue()
        instance.onCloseButtonClick()
        assertThat(instance.showingPresetSelector).isFalse()

        instance.onActivePresetClick()
        assertThat(instance.showingPresetSelector).isTrue()
        navigationState.onBackButtonClick()
        assertThat(instance.showingPresetSelector).isFalse()
    }

    @Test fun renamingPresets() = runTest {
        addTestPresets()
        instance.onPresetRenameClick(testPresets[1])
        assertThat(instance.renameDialogTarget).isEqualTo(testPresets[1])
        assertThat(instance.proposedPresetNameErrorMessage).isNull()

        instance.onPresetRenameConfirm()
        waitUntil { instance.renameDialogTarget == null }
        assertThat(instance.renameDialogTarget).isNull()
        assertThat(instance.proposedPresetNameErrorMessage).isNull()

        instance.onPresetRenameClick(testPresets[1])
        val newName = testPresets[1].name + " new name"
        instance.onProposedPresetNameChange(newName)
        waitUntil { instance.proposedPresetNameErrorMessage != null } // should time out
        assertThat(instance.proposedPresetNameErrorMessage).isNull()

        activePresetState.setName(testPresets[1].name)
        waitUntil { instance.activePresetName == testPresets[1].name }
        instance.onPresetRenameConfirm()
        waitUntil { instance.activePresetName == newName }
        assertThat(instance.renameDialogTarget).isNull()
        assertThat(instance.presetList?.get(1)?.name).isEqualTo(newName)
        assertThat(instance.activePresetName).isEqualTo(newName)
    }

    @Test fun blankNamesAreRejected() = runTest {
        addTestPresets()
        instance.onPresetRenameClick(testPresets[1])
        assertThat(instance.renameDialogTarget).isEqualTo(testPresets[1])
        assertThat(instance.proposedPresetNameErrorMessage).isNull()

        instance.onProposedPresetNameChange("")
        waitUntil { instance.proposedPresetNameErrorMessage != null }
        assertThat(instance.proposedPresetNameErrorMessage?.resolve(context))
            .isEqualTo(context.getString(R.string.preset_name_cannot_be_blank_error_message))

        instance.onPresetRenameConfirm()
        waitUntil { instance.proposedPresetNameErrorMessage == null } // should time out
        assertThat(instance.proposedPresetNameErrorMessage?.resolve(context))
            .isEqualTo(context.getString(R.string.preset_name_cannot_be_blank_error_message))
        assertThat(instance.renameDialogTarget).isEqualTo(testPresets[1])
        assertThat(instance.presetList?.get(1)?.name).isEqualTo(testPresets[1].name)

        instance.onProposedPresetNameChange("a")
        waitUntil { instance.proposedPresetNameErrorMessage == null }
        assertThat(instance.proposedPresetNameErrorMessage).isNull()
    }

    @Test fun duplicateNamesAreRejected() = runTest {
        addTestPresets()
        instance.onPresetRenameClick(testPresets[1])
        assertThat(instance.renameDialogTarget).isEqualTo(testPresets[1])
        assertThat(instance.proposedPresetNameErrorMessage).isNull()

        instance.onProposedPresetNameChange(testPresets[0].name)
        waitUntil { instance.proposedPresetNameErrorMessage != null }
        assertThat(instance.proposedPresetNameErrorMessage?.resolve(context))
            .isEqualTo(context.getString(R.string.preset_name_already_in_use_error_message))

        instance.onPresetRenameConfirm()
        waitUntil { instance.proposedPresetNameErrorMessage == null } // should time out
        assertThat(instance.proposedPresetNameErrorMessage?.resolve(context))
            .isEqualTo(context.getString(R.string.preset_name_already_in_use_error_message))
        assertThat(instance.renameDialogTarget).isEqualTo(testPresets[1])
        assertThat(instance.presetList?.get(1)?.name).isEqualTo(testPresets[1].name)

        instance.onProposedPresetNameChange("a")
        waitUntil { instance.proposedPresetNameErrorMessage == null }
        assertThat(instance.proposedPresetNameErrorMessage).isNull()
    }

    @Test fun overwritingNotAllowedWithNoActiveTracks() = runTest {
        addTestPresets()
        activePresetState.setName(testPresets[2].name)
        trackDao.toggleIsActive(testTracks[3].uriString)
        trackDao.toggleIsActive(testTracks[4].uriString)
        waitUntil { trackDao.getActiveTracks().first().isEmpty() }
        assertThat(instance.activePresetIsModified).isTrue()

        instance.onPresetOverwriteRequest(testPresets[2])
        waitUntil { !instance.activePresetIsModified } // should time out
        assertThat(presetDao.getPresetTracks(testPresets[2].name).first())
            .containsExactly(testTracks[3].toActiveTrack(), testTracks[4].toActiveTrack())
        assertThat(instance.activePresetIsModified).isTrue()

        instance.onPresetOverwriteRequest(testPresets[0])
        waitUntil { instance.activePresetName == testPresets[0].name } // should time out
        assertThat(instance.activePresetName).isNotEqualTo(testPresets[0].name)
        assertThat(presetDao.getPresetTracks(testPresets[0].name).first())
            .containsExactly(testTracks[0].toActiveTrack())
    }

    @Test fun overwritingPresets() = runTest {
        addTestPresets()
        instance.onPresetOverwriteRequest(testPresets[2])
        waitUntil { instance.activePresetName == testPresets[2].name }
        assertThat(instance.activePresetName).isEqualTo(testPresets[2].name)

        instance.onPresetOverwriteRequest(testPresets[2])
        waitUntil { instance.activePresetName != testPresets[2].name } // should time out
        assertThat(instance.activePresetName).isEqualTo(testPresets[2].name)

        instance.onPresetOverwriteRequest(testPresets[0])
        waitUntil { instance.activePresetName != testPresets[2].name }
        assertThat(instance.activePresetName).isEqualTo(testPresets[0].name)
        assertThat(trackDao.getActiveTracks().first()).containsExactly(
            testTracks[3].toActiveTrack(), testTracks[4].toActiveTrack())
    }

    @Test fun deletingPresets() = runTest {
        addTestPresets()
        var presets = presetDao.getPresetList().first()
        assertThat(presets).containsExactlyElementsIn(testPresets)
                           .inOrder()

        instance.onPresetDeleteRequest(testPresets[1])
        waitUntil { presetDao.getPresetList().first().size < 3 }
        presets = presetDao.getPresetList().first()
        assertThat(presets).containsExactlyElementsIn(testPresets
                           .minus(testPresets[1])).inOrder()

        activePresetState.setName(testPresets[0].name)
        waitUntil { instance.activePresetName == testPresets[0].name }
        instance.onPresetDeleteRequest(testPresets[0])
        waitUntil { presetDao.getPresetList().first().size < 2 }
        presets = presetDao.getPresetList().first()
        assertThat(presets).containsExactly(testPresets[2])
        assertThat(instance.activePresetName).isNull()
    }

    @Test fun clickingPresetsWithoutUnsavedChanges() = runTest {
        addTestPresets()
        instance.onActivePresetClick()
        instance.onPresetSelectorPresetClick(testPresets[1])
        waitUntil { instance.activePresetName != null }
        assertThat(instance.activePresetName).isEqualTo(testPresets[1].name)
        assertThat(trackDao.getActiveTracks().first()).containsExactly(
            testTracks[1].toActiveTrack(), testTracks[2].toActiveTrack())
        assertThat(instance.showingPresetSelector).isFalse()

        instance.onActivePresetClick()
        instance.onPresetSelectorPresetClick(testPresets[0])
        waitUntil { instance.activePresetName != testPresets[1].name }
        assertThat(instance.activePresetName).isEqualTo(testPresets[0].name)
        assertThat(trackDao.getActiveTracks().first())
            .containsExactly(testTracks[0].toActiveTrack())
        assertThat(instance.showingPresetSelector).isFalse()
    }
    // 310 showingPresetSelector still false
    @Test fun unsavedChangesDialogShows() = runTest {
        addTestPresets()
        instance.onPresetSelectorPresetClick(testPresets[1])
        waitUntil { instance.activePresetName != null }
        trackDao.toggleIsActive(testTracks[3].uriString)
        waitUntil { instance.activePresetIsModified }
        assertThat(instance.activePresetIsModified).isTrue()

        instance.onActivePresetClick()
        instance.onPresetSelectorPresetClick(testPresets[2])
        waitUntil { !instance.activePresetIsModified } // should time out
        assertThat(instance.showingUnsavedChangesWarning).isTrue()
        assertThat(instance.showingPresetSelector).isTrue()
        assertThat(instance.activePresetIsModified).isTrue()
        assertThat(trackDao.getActiveTracks().first()).containsExactly(
            testTracks[1].toActiveTrack(), testTracks[2].toActiveTrack(), testTracks[3].toActiveTrack())
    }

    @Test fun cancellingAfterClickingPresetWithUnsavedChanges() = runTest {
        addTestPresets()
        instance.onPresetSelectorPresetClick(testPresets[1])
        waitUntil { instance.activePresetName != null }
        trackDao.toggleIsActive(testTracks[3].uriString)
        waitUntil { trackDao.getActiveTracks().first().size > 2 }

        instance.onActivePresetClick()
        instance.onPresetSelectorPresetClick(testPresets[2])
        instance.onUnsavedChangesWarningCancel()
        waitUntil { !instance.activePresetIsModified } // should time out
        assertThat(instance.showingUnsavedChangesWarning).isFalse()
        assertThat(instance.showingPresetSelector).isTrue()
        assertThat(instance.activePresetIsModified).isTrue()
        assertThat(trackDao.getActiveTracks().first()).containsExactly(
            testTracks[1].toActiveTrack(), testTracks[2].toActiveTrack(), testTracks[3].toActiveTrack())
    }

    @Test fun droppingChangesAfterClickingPresetWithUnsavedChanges() = runTest {
        addTestPresets()
        instance.onPresetSelectorPresetClick(testPresets[1])
        waitUntil { instance.activePresetName != null }
        trackDao.toggleIsActive(testTracks[3].uriString)
        waitUntil { trackDao.getActiveTracks().first().size > 2 }

        instance.onActivePresetClick()
        instance.onPresetSelectorPresetClick(testPresets[2])
        instance.onUnsavedChangesWarningConfirm(saveFirst = false)
        waitUntil { !instance.activePresetIsModified }
        assertThat(instance.activePresetIsModified).isFalse()
        assertThat(instance.showingUnsavedChangesWarning).isFalse()
        assertThat(instance.showingPresetSelector).isFalse()

        // Check that new preset was loaded
        assertThat(instance.activePresetName).isEqualTo(testPresets[2].name)
        assertThat(trackDao.getActiveTracks().first()).containsExactly(
            testTracks[3].toActiveTrack(), testTracks[4].toActiveTrack())

        // Check that previous preset's changes were dropped
        assertThat(presetDao.getPresetTracks(testPresets[1].name).first())
            .containsExactly(testTracks[1].toActiveTrack(), testTracks[2].toActiveTrack())
    }

    @Test fun savingFirstAfterClickingPresetWithUnsavedChanges() = runTest {
        addTestPresets()
        instance.onActivePresetClick()
        instance.onPresetSelectorPresetClick(testPresets[1])
        waitUntil { instance.activePresetName != null }
        trackDao.toggleIsActive(testTracks[3].uriString)
        waitUntil { trackDao.getActiveTracks().first().size > 2 }

        instance.onPresetSelectorPresetClick(testPresets[2])
        instance.onUnsavedChangesWarningConfirm(saveFirst = true)
        waitUntil { !instance.activePresetIsModified }
        assertThat(instance.activePresetIsModified).isFalse()
        assertThat(instance.showingUnsavedChangesWarning).isFalse()
        assertThat(instance.showingPresetSelector).isFalse()

        // Check that new preset was loaded
        assertThat(instance.activePresetName).isEqualTo(testPresets[2].name)
        assertThat(trackDao.getActiveTracks().first()).containsExactly(
            testTracks[3].toActiveTrack(), testTracks[4].toActiveTrack())

        // Check that previous preset's changes were saved first
        val oldPresetContents = presetDao.getPresetTracks(testPresets[1].name).first()
        assertThat(oldPresetContents).containsExactly(
            testTracks[1].toActiveTrack(), testTracks[2].toActiveTrack(),
            testTracks[3].toActiveTrack())
    }
}