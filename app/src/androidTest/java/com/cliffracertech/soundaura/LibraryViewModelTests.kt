/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cliffracertech.soundaura.library.LibraryViewModel
import com.cliffracertech.soundaura.library.Playlist
import com.cliffracertech.soundaura.library.PlaylistDialog
import com.cliffracertech.soundaura.library.RemovablePlaylistTrack
import com.cliffracertech.soundaura.library.uri
import com.cliffracertech.soundaura.model.MessageHandler
import com.cliffracertech.soundaura.model.ModifyLibraryUseCase
import com.cliffracertech.soundaura.model.PlaybackState
import com.cliffracertech.soundaura.model.ReadLibraryUseCase
import com.cliffracertech.soundaura.model.SearchQueryState
import com.cliffracertech.soundaura.model.TestPermissionHandler
import com.cliffracertech.soundaura.model.TestPlaybackState
import com.cliffracertech.soundaura.model.UriPermissionHandler
import com.cliffracertech.soundaura.model.database.PlaylistDao
import com.cliffracertech.soundaura.model.database.SoundAuraDatabase
import com.cliffracertech.soundaura.settings.PrefKeys
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

typealias PlaylistSort = com.cliffracertech.soundaura.model.database.Playlist.Sort

@RunWith(AndroidJUnit4::class)
class LibraryViewModelTests {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = TestCoroutineScope()
    private val playlistSortKey = intPreferencesKey(PrefKeys.playlistSort)
    private val showActivePlaylistsFirstKey = booleanPreferencesKey(PrefKeys.showActivePlaylistsFirst)
    private val testUris = List(4) { "uri $it".toUri() }
    private val testPlaylistNames = List(5) { "playlist $it" }
    private val testPlaylists = List(5) {
        Playlist(
            name = testPlaylistNames[it],
            isActive = false,
            volume = 1.0f,
            hasError = false,
            isSingleTrack = it != 4)
    }

    private val dataStore = PreferenceDataStoreFactory
        .create(scope = scope) { context.preferencesDataStoreFile("testDatastore") }
    private lateinit var db: SoundAuraDatabase
    private lateinit var dao: PlaylistDao
    private lateinit var permissionHandler: UriPermissionHandler
    private lateinit var messageHandler: MessageHandler
    private lateinit var playbackState: PlaybackState
    private lateinit var searchQueryState: SearchQueryState
    private lateinit var instance: LibraryViewModel

    private val renameDialog get() = instance.shownDialog as PlaylistDialog.Rename
    private val fileChooser get() = instance.shownDialog as PlaylistDialog.FileChooser
    private val playlistOptionsDialog get() = instance.shownDialog as PlaylistDialog.PlaylistOptions
    private val removeDialog get() = instance.shownDialog as PlaylistDialog.Remove

    @Before fun init() {
        db = Room.inMemoryDatabaseBuilder(context, SoundAuraDatabase::class.java).build()
        dao = db.playlistDao()
        permissionHandler = TestPermissionHandler()
        messageHandler = MessageHandler()
        playbackState = TestPlaybackState()
        searchQueryState = SearchQueryState()
        instance = LibraryViewModel(
            ReadLibraryUseCase(dataStore, searchQueryState, dao),
            ModifyLibraryUseCase(permissionHandler, messageHandler, dao),
            messageHandler, playbackState)
        runTest {
            // Of the five names in testPlaylistNames, the first four will
            // be used for four single track playlists. The last playlist
            // name will be used for a multi-track playlist containing all
            // four uris in testUris.
            val map = LinkedHashMap<Uri, String>()
            map.putAll(testUris.zip(testPlaylistNames))
            dao.insertSingleTrackPlaylists(map)
            dao.insertPlaylist(
                playlistName = testPlaylistNames.last(),
                shuffle = true,
                trackUris = testUris)
            waitUntil { instance.playlists?.size == 5 }
        }
    }
    @After fun cleanUp() {
        db.close()
        runBlocking { dataStore.edit { it.clear() } }
        scope.cancel()
    }

    @Test fun dialog_not_shown_initially() {
        assertThat(instance.shownDialog).isNull()
    }

    @Test fun playlists_property_matches_underlying_state() = runTest {
        assertThat(instance.playlists).containsExactlyElementsIn(testPlaylists).inOrder()

        dao.deletePlaylist(testPlaylistNames[1])
        waitUntil { dao.getPlaylistNames().first().size == 4 }
        val expected = testPlaylists - testPlaylists.find { it.name == testPlaylistNames[1] }
        assertThat(instance.playlists).containsExactlyElementsIn(expected).inOrder()
    }

    @Test fun playlists_property_reflects_sort_option() = runTest {
        dataStore.edit(playlistSortKey, PlaylistSort.NameDesc.ordinal)
        waitUntil { instance.playlists?.first() == testPlaylists.last() }
        assertThat(instance.playlists).containsExactlyElementsIn(testPlaylists.reversed()).inOrder()

        dataStore.edit(playlistSortKey, PlaylistSort.OrderAdded.ordinal)
        waitUntil { instance.playlists?.first() == testPlaylists.first() }
        assertThat(instance.playlists).containsExactlyElementsIn(testPlaylists).inOrder()
    }

    @Test fun playlists_property_reflects_show_active_first_option() = runTest {
        dataStore.edit(showActivePlaylistsFirstKey, true)
        dao.toggleIsActive(testPlaylistNames[1])
        dao.toggleIsActive(testPlaylistNames[3])
        waitUntil { instance.playlists?.first()?.name == testPlaylistNames[1] }
        assertThat(instance.playlists?.map(Playlist::name)).containsExactly(
            testPlaylistNames[1], testPlaylistNames[3], testPlaylistNames[0],
            testPlaylistNames[2], testPlaylistNames[4]).inOrder()

        dataStore.edit(showActivePlaylistsFirstKey, false)
        waitUntil { instance.playlists?.first()?.name == testPlaylistNames[0] }
        assertThat(instance.playlists?.map(Playlist::name))
            .containsExactlyElementsIn(testPlaylistNames).inOrder()
    }

    @Test fun playlist_property_reflects_search_query() = runTest {
        searchQueryState.set("playlist ")
        waitUntil { (instance.playlists?.size ?: 0) < 5 } // should time out
        assertThat(instance.playlists).containsExactlyElementsIn(testPlaylists).inOrder()

        searchQueryState.set("2")
        waitUntil { (instance.playlists?.size ?: 0) == 1 }
        assertThat(instance.playlists).containsExactly(testPlaylists[2])

        searchQueryState.set("4")
        waitUntil { instance.playlists?.contains(testPlaylists[4]) == true }
        assertThat(instance.playlists).containsExactly(testPlaylists[4])

        searchQueryState.set(null)
        waitUntil { (instance.playlists?.size ?: 0) == 5 }
        assertThat(instance.playlists).containsExactlyElementsIn(testPlaylists).inOrder()
    }

    @Test fun track_add_remove_click() = runTest {
        assertThat(instance.playlists?.map(Playlist::isActive)).doesNotContain(true)
        instance.itemCallback.onAddRemoveButtonClick(testPlaylists[3])
        waitUntil { instance.playlists?.map(Playlist::isActive)?.contains(true) == true }
        assertThat(instance.playlists?.map(Playlist::isActive))
            .containsExactly(false, false, false, true, false).inOrder()

        instance.itemCallback.onAddRemoveButtonClick(testPlaylists[1])
        instance.itemCallback.onAddRemoveButtonClick(testPlaylists[3])
        waitUntil { instance.playlists?.filter(Playlist::isActive)?.size == 2 }
        assertThat(instance.playlists?.map(Playlist::isActive))
            .containsExactly(false, true, false, false, false).inOrder()
    }

    @Test fun track_volume_change_request() = runTest {
        assertThat(instance.playlists?.map(Playlist::volume))
            .containsExactly(1f, 1f, 1f, 1f, 1f).inOrder()

        instance.itemCallback.onVolumeChangeFinished(testPlaylists[2], 0.5f)
        waitUntil { instance.playlists?.map(Playlist::volume)?.contains(0.5f) == true }
        assertThat(instance.playlists?.map(Playlist::volume))
            .containsExactly(1f, 1f, 0.5f, 1f, 1f).inOrder()

        instance.itemCallback.onVolumeChangeFinished(testPlaylists[2], 1f)
        instance.itemCallback.onVolumeChangeFinished(testPlaylists[1], 0.25f)
        instance.itemCallback.onVolumeChangeFinished(testPlaylists[4], 0.75f)
        waitUntil { instance.playlists?.map(Playlist::volume)?.sum() == 4f }
        assertThat(instance.playlists?.map(Playlist::volume))
            .containsExactly(1f, 0.25f, 1f, 1f, 0.75f).inOrder()
    }

    @Test fun rename_dialog_appearance() = runTest {
        instance.itemCallback.onRenameClick(testPlaylists[1])
        waitUntil { instance.shownDialog != null }
        assertThat(instance.shownDialog).isInstanceOf(PlaylistDialog.Rename::class)
        assertThat(renameDialog.target).isEqualTo(testPlaylists[1])
    }

    @Test fun rename_dialog_dismissal() = runTest {
        instance.itemCallback.onRenameClick(testPlaylists[1])
        waitUntil { instance.shownDialog != null }

        // Make changes that (hopefully) won't be saved
        renameDialog.onNameChange("new name")
        waitUntil { renameDialog.name == "new name" }

        renameDialog.onDismissRequest()
        assertThat(instance.shownDialog).isNull()
        assertThat(dao.getPlaylistNames().first()).doesNotContain("new name")
    }

    @Test fun rename_dialog_confirmation() = runTest {
        instance.itemCallback.onRenameClick(testPlaylists[1])
        waitUntil { instance.shownDialog != null }
        renameDialog.onNameChange("new name")
        waitUntil { renameDialog.name == "new name" }
        renameDialog.finalize()
        waitUntil { dao.getPlaylistNames().first().contains("new name") }

        assertThat(instance.shownDialog).isNull()
        assertThat(dao.getPlaylistNames().first()).containsExactlyElementsIn(
            testPlaylistNames.toMutableList().also { it[1] = "new name" })
    }

    @Test fun single_track_playlist_extra_options_click_opens_file_chooser() = runTest {
        instance.itemCallback.onExtraOptionsClick(testPlaylists[1])
        waitUntil { instance.shownDialog != null }
        assertThat(instance.shownDialog).isInstanceOf(PlaylistDialog.FileChooser::class)
    }

    @Test fun single_track_playlist_file_chooser_dismissal() = runTest {
        instance.itemCallback.onExtraOptionsClick(testPlaylists[1])
        waitUntil { instance.shownDialog != null }
        fileChooser.onDismissRequest()
        waitUntil { instance.shownDialog == null }

        assertThat(instance.shownDialog).isNull()
        val playlistTracks = dao.getPlaylistTracks(testPlaylistNames[1])
        assertThat(playlistTracks).containsExactly(testUris[1])
    }
    
    @Test fun single_track_playlist_file_chooser_confirm_opens_playlist_options() = runTest {
        instance.itemCallback.onExtraOptionsClick(testPlaylists[1])
        waitUntil { instance.shownDialog != null }
        val newUris = List(2) { "new uri $it".toUri() }
        fileChooser.onFilesChosen(newUris)
        waitUntil { instance.shownDialog !is PlaylistDialog.FileChooser }

        assertThat(instance.shownDialog).isInstanceOf(PlaylistDialog.PlaylistOptions::class)
        assertThat(playlistOptionsDialog.target).isEqualTo(testPlaylists[1])
        val playlistUris = playlistOptionsDialog.mutablePlaylist.tracks.map { it.uri }
        val expectedUris = listOf(testUris[1]) + newUris
        assertThat(playlistUris).containsExactlyElementsIn(expectedUris).inOrder()
        assertThat(playlistOptionsDialog.shuffleEnabled).isFalse()
    }
    
    @Test fun single_track_playlist_options_dismissal() = runTest {
        instance.itemCallback.onExtraOptionsClick(testPlaylists[1])
        waitUntil { instance.shownDialog != null }
        val newUris = List(2) { "new uri $it".toUri() }
        fileChooser.onFilesChosen(newUris)
        waitUntil { instance.shownDialog !is PlaylistDialog.FileChooser }
        playlistOptionsDialog.onDismissRequest()
        waitUntil { instance.shownDialog !is PlaylistDialog.PlaylistOptions }
        assertThat(instance.shownDialog).isNull()
    }

    @Test fun single_track_playlist_options_confirmation() = runTest {
        instance.itemCallback.onExtraOptionsClick(testPlaylists[1])
        waitUntil { instance.shownDialog != null }
        val newUris = List(2) { "new uri $it".toUri() }
        fileChooser.onFilesChosen(newUris)
        waitUntil { instance.shownDialog !is PlaylistDialog.FileChooser }

        playlistOptionsDialog.mutablePlaylist.moveTrack(2, 0)
        playlistOptionsDialog.onShuffleSwitchClick()
        playlistOptionsDialog.onFinishClick()
        waitUntil { dao.getPlaylistShuffle(testPlaylistNames[1]) }

        assertThat(instance.shownDialog).isNull()
        assertThat(dao.getPlaylistShuffle(testPlaylistNames[1])).isTrue()
        val expectedUris = listOf(newUris[1], testUris[1], newUris[0])
        val actualUris = dao.getPlaylistTracks(testPlaylistNames[1])
        assertThat(actualUris).containsExactlyElementsIn(expectedUris).inOrder()
    }

    @Test fun multi_track_playlist_extra_options_click_opens_playlist_options() = runTest {
        instance.itemCallback.onExtraOptionsClick(testPlaylists[4])
        waitUntil { instance.shownDialog != null }

        assertThat(instance.shownDialog).isInstanceOf(PlaylistDialog.PlaylistOptions::class)
        assertThat(playlistOptionsDialog.target).isEqualTo(testPlaylists[4])
        assertThat(playlistOptionsDialog.shuffleEnabled).isTrue()
        val dialogUris = playlistOptionsDialog.mutablePlaylist.tracks.map(RemovablePlaylistTrack::uri)
        assertThat(dialogUris).containsExactlyElementsIn(testUris).inOrder()
    }

    @Test fun multi_track_playlist_options_dismissal() = runTest {
        instance.itemCallback.onExtraOptionsClick(testPlaylists[4])
        waitUntil { instance.shownDialog != null }

        // Make changes that (hopefully) won't be saved
        playlistOptionsDialog.onShuffleSwitchClick()
        playlistOptionsDialog.mutablePlaylist.moveTrack(3, 1)
        playlistOptionsDialog.mutablePlaylist.toggleTrackRemoval(3)

        playlistOptionsDialog.onDismissRequest()
        waitUntil { instance.shownDialog == null }

        assertThat(instance.shownDialog).isNull()
        assertThat(dao.getPlaylistShuffle(testPlaylistNames[4])).isTrue()
        val actualUris = dao.getPlaylistTracks(testPlaylistNames[4])
        assertThat(actualUris).containsExactlyElementsIn(testUris).inOrder()
    }

    @Test fun multi_track_playlist_options_confirmation() = runTest {
        instance.itemCallback.onExtraOptionsClick(testPlaylists[4])
        waitUntil { instance.shownDialog != null }

        playlistOptionsDialog.onShuffleSwitchClick()
        playlistOptionsDialog.mutablePlaylist.moveTrack(3, 1)
        playlistOptionsDialog.mutablePlaylist.toggleTrackRemoval(3)
        playlistOptionsDialog.onFinishClick()
        waitUntil { instance.shownDialog == null }
        waitUntil { !dao.getPlaylistShuffle(testPlaylistNames[4]) }
        waitUntil { dao.getPlaylistTracks(testPlaylistNames[4]).size < 4 }

        assertThat(instance.shownDialog).isNull()
        assertThat(dao.getPlaylistShuffle(testPlaylistNames[4])).isFalse()
        val expectedUris = listOf(testUris[0], testUris[3], testUris[1])
        val actualUris = dao.getPlaylistTracks(testPlaylistNames[4])
        assertThat(actualUris).containsExactlyElementsIn(expectedUris).inOrder()
    }

    @Test fun multi_track_playlist_options_add_button_opens_file_chooser() = runTest {
        instance.itemCallback.onExtraOptionsClick(testPlaylists[4])
        waitUntil { instance.shownDialog != null }

        playlistOptionsDialog.onAddFilesClick()
        waitUntil { instance.shownDialog !is PlaylistDialog.PlaylistOptions }
        assertThat(instance.shownDialog).isInstanceOf(PlaylistDialog.FileChooser::class)
    }

    @Test fun multi_track_playlist_file_chooser_dismissal_returns_to_playlist_options() = runTest {
        instance.itemCallback.onExtraOptionsClick(testPlaylists[4])
        waitUntil { instance.shownDialog != null }
        playlistOptionsDialog.onAddFilesClick()
        waitUntil { instance.shownDialog is PlaylistDialog.FileChooser }

        fileChooser.onDismissRequest()
        waitUntil { instance.shownDialog !is PlaylistDialog.FileChooser }

        assertThat(instance.shownDialog).isInstanceOf(PlaylistDialog.PlaylistOptions::class)
        assertThat(playlistOptionsDialog.target).isEqualTo(testPlaylists[4])
        val actualUris = playlistOptionsDialog.mutablePlaylist.tracks.map(RemovablePlaylistTrack::uri)
        assertThat(actualUris).containsExactlyElementsIn(testUris).inOrder()
    }

    @Test fun multi_track_playlist_file_chooser_confirmation() = runTest {
        instance.itemCallback.onExtraOptionsClick(testPlaylists[4])
        waitUntil { instance.shownDialog != null }
        playlistOptionsDialog.onAddFilesClick()
        waitUntil { instance.shownDialog is PlaylistDialog.FileChooser }

        val newUris = List(2) { "new uri $it".toUri() }
        fileChooser.onFilesChosen(newUris)
        waitUntil { instance.shownDialog !is PlaylistDialog.FileChooser }

        assertThat(instance.shownDialog).isInstanceOf(PlaylistDialog.PlaylistOptions::class)
        assertThat(playlistOptionsDialog.target).isEqualTo(testPlaylists[4])
        val expectedUris = testUris + newUris
        var actualUris = playlistOptionsDialog.mutablePlaylist.tracks.map(RemovablePlaylistTrack::uri)
        assertThat(actualUris).containsExactlyElementsIn(expectedUris).inOrder()

        playlistOptionsDialog.onFinishClick()
        waitUntil { instance.shownDialog == null }
        waitUntil { dao.getPlaylistTracks(testPlaylistNames[4]).size > 4 }
        logd(dao.getPlaylistTracks(testPlaylistNames[4]).joinToString())
        assertThat(instance.shownDialog).isNull()
        actualUris = dao.getPlaylistTracks(testPlaylistNames[4])
        assertThat(actualUris).containsExactlyElementsIn(expectedUris).inOrder()
    }

    @Test fun remove_dialog_appearance() = runTest {
        instance.itemCallback.onRemoveClick(testPlaylists[1])
        waitUntil { instance.shownDialog != null }
        assertThat(instance.shownDialog).isInstanceOf(PlaylistDialog.Remove::class)
        assertThat(removeDialog.target).isEqualTo(testPlaylists[1])
    }
    
    @Test fun remove_dialog_dismissal() = runTest {
        instance.itemCallback.onRemoveClick(testPlaylists[1])
        waitUntil { instance.shownDialog != null }
        removeDialog.onDismissRequest()
        waitUntil { dao.getPlaylistNames().first().size < testPlaylists.size } // should time out
        assertThat(instance.shownDialog).isNull()
        assertThat(dao.getPlaylistNames().first()).contains(testPlaylistNames[1])
    }
    
    @Test fun remove_dialog_confirmation() = runTest {
        instance.itemCallback.onRemoveClick(testPlaylists[1])
        waitUntil { instance.shownDialog != null }
        removeDialog.onConfirmClick()
        waitUntil { dao.getPlaylistNames().first().size < testPlaylists.size }
        assertThat(instance.shownDialog).isNull()
        assertThat(dao.getPlaylistNames().first())
            .containsExactlyElementsIn(testPlaylistNames - testPlaylistNames[1])
    }
}