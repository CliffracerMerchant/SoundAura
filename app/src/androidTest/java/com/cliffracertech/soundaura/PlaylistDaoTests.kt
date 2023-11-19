/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cliffracertech.soundaura.model.database.LibraryPlaylist
import com.cliffracertech.soundaura.model.database.Playlist
import com.cliffracertech.soundaura.model.database.PlaylistDao
import com.cliffracertech.soundaura.model.database.SoundAuraDatabase
import com.cliffracertech.soundaura.model.database.Track
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistDaoTests {
    private lateinit var db: SoundAuraDatabase
    private lateinit var dao: PlaylistDao

    private val testUris = List(5) { "uri $it".toUri() }
    private val testTracks = testUris.map(::Track)
    private val testPlaylistNames = listOf(
        "playlist b", "playlist d", "playlist a", "playlist c", "track e")

    @Before fun init() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, SoundAuraDatabase::class.java).build()
        dao = db.playlistDao()
    }

    @After fun closeDb() { db.close() }

    @Test fun initial_list_is_empty() = runTest {
        val playlists = dao.getPlaylistNames().first()
        assertThat(playlists).isEmpty()
    }

    @Test fun inserting_multi_track_playlists() = runTest {
        val name1 = testPlaylistNames[0]
        dao.insertPlaylist(name1, false, testTracks)
        assertThat(dao.getPlaylistNames().first()).containsExactly(name1)
        assertThat(dao.getPlaylistShuffle(name1)).isFalse()
        assertThat(dao.getPlaylistTracks(name1)).containsExactlyElementsIn(testTracks)

        val name2 = testPlaylistNames[2]
        dao.insertPlaylist(name2, true, testTracks.subList(0, 3))
        assertThat(dao.getPlaylistNames().first()).containsExactly(name1, name2)
        assertThat(dao.getPlaylistShuffle(name2)).isTrue()
        assertThat(dao.getPlaylistTracks(name2)).containsExactlyElementsIn(testTracks.subList(0, 3))
    }

    @Test fun inserting_single_track_playlists() = runTest {
        val map = LinkedHashMap<Uri, String>()
        map[testUris[0]] = testPlaylistNames[0]
        map[testUris[1]] = testPlaylistNames[1]
        dao.insertSingleTrackPlaylists(map)
        assertThat(dao.getPlaylistNames().first())
            .containsExactly(testPlaylistNames[0], testPlaylistNames[1])

        map.clear()
        map[testUris[2]] = testPlaylistNames[2]
        map[testUris[3]] = testPlaylistNames[3]
        map[testUris[4]] = testPlaylistNames[4]
        dao.insertSingleTrackPlaylists(map)
        assertThat(dao.getPlaylistNames().first())
            .containsExactlyElementsIn(testPlaylistNames)
        testPlaylistNames.forEachIndexed { index, name ->
            assertThat(dao.getPlaylistTracks(name)).containsExactly(testTracks[index])
        }
    }

    @Test fun deleting_playlists() = runTest {
        val map = LinkedHashMap<Uri, String>()
        map.putAll(testUris.zip(testPlaylistNames))
        dao.insertSingleTrackPlaylists(map)

        dao.deletePlaylist(testPlaylistNames[2])
        assertThat(dao.getPlaylistNames().first())
            .containsExactlyElementsIn(testPlaylistNames - testPlaylistNames[2])

        dao.deletePlaylist(testPlaylistNames[1])
        dao.deletePlaylist(testPlaylistNames[4])
        assertThat(dao.getPlaylistNames().first())
            .containsExactly(testPlaylistNames[0], testPlaylistNames[3])
    }

    @Test fun deleting_playlists_returns_unused_uris() = runTest {
        dao.insertPlaylist(testPlaylistNames[0], false, testTracks.subList(0, 2))
        dao.insertPlaylist(testPlaylistNames[1], false, listOf(testTracks[1]))
        dao.insertPlaylist(testPlaylistNames[2], false, listOf(testTracks[2]))
        dao.insertPlaylist(testPlaylistNames[3], false, testTracks.subList(3, 5))
        dao.insertPlaylist(testPlaylistNames[4], false, testTracks.subList(3, 5))

        var unusedUris = dao.deletePlaylist(testPlaylistNames[0])
        assertThat(unusedUris).containsExactly(testUris[0])
        
        unusedUris = dao.deletePlaylist(testPlaylistNames[1])
        assertThat(unusedUris).containsExactly(testUris[1])

        unusedUris = dao.deletePlaylist(testPlaylistNames[2])
        assertThat(unusedUris).containsExactly(testUris[2])

        unusedUris = dao.deletePlaylist(testPlaylistNames[3])
        assertThat(unusedUris).isEmpty()
        
        unusedUris = dao.deletePlaylist(testPlaylistNames[4])
        assertThat(unusedUris).containsExactly(testUris[3], testUris[4])
    }

    @Test fun set_playlist_shuffle() = runTest {
        dao.insertPlaylist(testPlaylistNames[0], false, listOf(testTracks[0]))
        dao.setPlaylistShuffle(testPlaylistNames[0], true)
        dao.insertPlaylist(testPlaylistNames[1], true, listOf(testTracks[1]))
        dao.setPlaylistShuffle(testPlaylistNames[1], false)
        dao.insertPlaylist(testPlaylistNames[2], false, listOf(testTracks[2]))
        dao.setPlaylistShuffle(testPlaylistNames[2], false)
        dao.insertPlaylist(testPlaylistNames[3], true, listOf(testTracks[3]))
        dao.setPlaylistShuffle(testPlaylistNames[3], true)
        
        assertThat(dao.getPlaylistShuffle(testPlaylistNames[0])).isTrue()
        assertThat(dao.getPlaylistShuffle(testPlaylistNames[1])).isFalse()
        assertThat(dao.getPlaylistShuffle(testPlaylistNames[2])).isFalse()
        assertThat(dao.getPlaylistShuffle(testPlaylistNames[3])).isTrue()
    }
    
    @Test fun set_playlist_shuffle_and_contents() = runTest {
        val name = testPlaylistNames[0]
        dao.insertPlaylist(name, false, listOf(testTracks[0]))
        
        dao.setPlaylistShuffleAndContents(name, true, testTracks.subList(0, 2))
        assertThat(dao.getPlaylistShuffle(name)).isTrue()
        assertThat(dao.getPlaylistTracks(name))
            .containsExactlyElementsIn(testTracks.subList(0, 2))
        
        dao.setPlaylistShuffleAndContents(name, true, testTracks.subList(2, 5))
        assertThat(dao.getPlaylistShuffle(name)).isTrue()
        assertThat(dao.getPlaylistTracks(name))
            .containsExactlyElementsIn(testTracks.subList(2, 5))
    }

    @Test fun set_playlist_shuffle_and_contents_returns_unused_uris() = runTest {
        val name = testPlaylistNames[0]
        dao.insertPlaylist(name, false, listOf(testTracks[0]))

        var unusedUris = dao.setPlaylistShuffleAndContents(name, true, testTracks.subList(0, 2))
        assertThat(unusedUris).isEmpty()

        unusedUris = dao.setPlaylistShuffleAndContents(name, true, testTracks.subList(2, 3))
        assertThat(unusedUris).containsExactlyElementsIn(testUris.subList(0, 2))

        unusedUris = dao.setPlaylistShuffleAndContents(name, true, testTracks)
        assertThat(unusedUris).isEmpty()

        unusedUris = dao.setPlaylistShuffleAndContents(name, true, testTracks.subList(3, 5))
        assertThat(unusedUris).containsExactlyElementsIn(testUris.subList(0, 3))
    }
    
    @Test fun filter_new_tracks() = runTest {
        var newTracks = dao.filterNewTracks(testUris)
        assertThat(newTracks).containsExactlyElementsIn(testUris)
        
        dao.insertPlaylist(testPlaylistNames[0], false, testTracks.subList(0, 2))
        newTracks = dao.filterNewTracks(testUris)
        assertThat(newTracks).containsExactlyElementsIn(testUris.subList(2, 5))
    }

    @Test fun playlist_name_already_used() = runTest {
        val name = testPlaylistNames[0]
        assertThat(dao.exists(name)).isFalse()

        dao.insertPlaylist(name, false, testTracks)
        assertThat(dao.exists(name)).isTrue()
        
        dao.deletePlaylist(name)
        assertThat(dao.exists(name)).isFalse()
    }
    
    // testPlaylistNames = listOf("playlist b", "playlist d", "playlist a", "playlist c", "track e")
    @Test fun get_all_playlists_sorted_by_name_asc() = runTest {
        val map = LinkedHashMap<Uri, String>()
        map.putAll(testUris.zip(testPlaylistNames))
        dao.insertSingleTrackPlaylists(map)
        dao.toggleIsActive(testPlaylistNames[1])
        dao.toggleIsActive(testPlaylistNames[3])
        
        var playlistNames = dao.getAllPlaylistsSortedByNameAsc("%%").first().map { it.name }
        assertThat(playlistNames).containsExactlyElementsIn(testPlaylistNames.sorted()).inOrder()

        playlistNames = dao.getAllPlaylistsSortedByNameAsc("%playlist %").first().map { it.name }
        assertThat(playlistNames).containsExactlyElementsIn(
            testPlaylistNames.subList(0, 4).sorted()).inOrder()
    }

    // testPlaylistNames = listOf("playlist b", "playlist d", "playlist a", "playlist c", "track e")
    @Test fun get_all_playlists_sorted_by_name_desc() = runTest {
        val map = LinkedHashMap<Uri, String>()
        map.putAll(testUris.zip(testPlaylistNames))
        dao.insertSingleTrackPlaylists(map)
        dao.toggleIsActive(testPlaylistNames[1])
        dao.toggleIsActive(testPlaylistNames[3])

        var playlistNames = dao.getAllPlaylistsSortedByNameDesc("%%").first().map { it.name }
        assertThat(playlistNames).containsExactlyElementsIn(
            testPlaylistNames.sortedDescending()).inOrder()

        playlistNames = dao.getAllPlaylistsSortedByNameDesc("%playlist %").first().map { it.name }
        assertThat(playlistNames).containsExactlyElementsIn(
            testPlaylistNames.subList(0, 4).sortedDescending()).inOrder()
    }

    // testPlaylistNames = listOf("playlist b", "playlist d", "playlist a", "playlist c", "track e")
    @Test fun get_all_playlists_sorted_by_name_asc_with_active_first() = runTest {
        val map = LinkedHashMap<Uri, String>()
        map.putAll(testUris.zip(testPlaylistNames))
        dao.insertSingleTrackPlaylists(map)
        dao.toggleIsActive(testPlaylistNames[1])
        dao.toggleIsActive(testPlaylistNames[3])

        var playlistNames = dao.getAllPlaylistsSortedByActiveThenNameAsc("%%").first().map { it.name }
        assertThat(playlistNames).containsExactly(
            testPlaylistNames[3], testPlaylistNames[1],
            testPlaylistNames[2], testPlaylistNames[0],
            testPlaylistNames[4]
        ).inOrder()

        playlistNames = dao.getAllPlaylistsSortedByActiveThenNameAsc("%playlist %").first().map { it.name }
        assertThat(playlistNames).containsExactly(
            testPlaylistNames[3], testPlaylistNames[1],
            testPlaylistNames[2], testPlaylistNames[0]
        ).inOrder()
    }

    // testPlaylistNames = listOf("playlist b", "playlist d", "playlist a", "playlist c", "track e")
    @Test fun get_all_playlists_sorted_by_name_desc_with_active_first() = runTest {
        val map = LinkedHashMap<Uri, String>()
        map.putAll(testUris.zip(testPlaylistNames))
        dao.insertSingleTrackPlaylists(map)
        dao.toggleIsActive(testPlaylistNames[1])
        dao.toggleIsActive(testPlaylistNames[3])

        var playlistNames = dao.getAllPlaylistsSortedByActiveThenNameDesc("%%").first().map { it.name }
        assertThat(playlistNames).containsExactly(
            testPlaylistNames[1], testPlaylistNames[3],
            testPlaylistNames[4], testPlaylistNames[0],
            testPlaylistNames[2]
        ).inOrder()

        playlistNames = dao.getAllPlaylistsSortedByActiveThenNameDesc("%playlist %").first().map { it.name }
        assertThat(playlistNames).containsExactly(
            testPlaylistNames[1], testPlaylistNames[3],
            testPlaylistNames[0], testPlaylistNames[2]
        ).inOrder()
    }

    @Test fun get_at_least_one_playlist_is_active() = runTest {
        val map = LinkedHashMap<Uri, String>()
        map.putAll(testUris.zip(testPlaylistNames))
        dao.insertSingleTrackPlaylists(map)

        var atLeastOnePlaylistIsActive = dao.getAtLeastOnePlaylistIsActive().first()
        assertThat(atLeastOnePlaylistIsActive).isFalse()

        dao.toggleIsActive(testPlaylistNames[1])
        atLeastOnePlaylistIsActive = dao.getAtLeastOnePlaylistIsActive().first()
        assertThat(atLeastOnePlaylistIsActive).isTrue()

        dao.toggleIsActive(testPlaylistNames[3])
        atLeastOnePlaylistIsActive = dao.getAtLeastOnePlaylistIsActive().first()
        assertThat(atLeastOnePlaylistIsActive).isTrue()

        dao.toggleIsActive(testPlaylistNames[1])
        atLeastOnePlaylistIsActive = dao.getAtLeastOnePlaylistIsActive().first()
        assertThat(atLeastOnePlaylistIsActive).isTrue()

        dao.toggleIsActive(testPlaylistNames[3])
        atLeastOnePlaylistIsActive = dao.getAtLeastOnePlaylistIsActive().first()
        assertThat(atLeastOnePlaylistIsActive).isFalse()
    }

    @Test fun get_active_playlists_and_tracks() = runTest {
        dao.insertPlaylist(testPlaylistNames[0], false, listOf(testTracks[0]))
        dao.insertPlaylist(testPlaylistNames[1], false, testTracks)
        dao.insertPlaylist(testPlaylistNames[2], false, testTracks.subList(2, 5))
        var activePlaylists = dao.getActivePlaylistsAndTracks().first()
        assertThat(activePlaylists).isEmpty()

        dao.toggleIsActive(testPlaylistNames[1])
        activePlaylists = dao.getActivePlaylistsAndTracks().first()
        assertThat(activePlaylists.size).isEqualTo(1)
        assertThat(activePlaylists.keys.first().name)
            .isEqualTo(testPlaylistNames[1])
        assertThat(activePlaylists.values.first())
            .containsExactlyElementsIn(testUris)
            .inOrder()

        dao.toggleIsActive(testPlaylistNames[0])
        activePlaylists = dao.getActivePlaylistsAndTracks().first()
        assertThat(activePlaylists.size).isEqualTo(2)
        assertThat(activePlaylists.keys.map(Playlist::name))
            .containsExactlyElementsIn(testPlaylistNames.subList(0, 2))
            .inOrder()
       assertThat(activePlaylists.values)
           .containsExactly(listOf(testUris[0]), testUris)

        dao.toggleIsActive(testPlaylistNames[0])
        dao.toggleIsActive(testPlaylistNames[1])
        dao.toggleIsActive(testPlaylistNames[2])
        activePlaylists = dao.getActivePlaylistsAndTracks().first()
        assertThat(activePlaylists.size).isEqualTo(1)
        assertThat(activePlaylists.keys.first().name)
            .isEqualTo(testPlaylistNames[2])
        assertThat(activePlaylists.values.first())
            .containsExactlyElementsIn(testUris.subList(2, 5))
            .inOrder()
    }

    @Test fun get_playlist_names() = runTest {
        var names = dao.getPlaylistNames().first()
        assertThat(names).isEmpty()

        dao.insertPlaylist(testPlaylistNames[0], false, testTracks)
        names = dao.getPlaylistNames().first()
        assertThat(names).containsExactly(testPlaylistNames[0])

        dao.insertPlaylist(testPlaylistNames[1], false, testTracks)
        dao.insertPlaylist(testPlaylistNames[2], false, testTracks)
        dao.deletePlaylist(testPlaylistNames[0])
        names = dao.getPlaylistNames().first()
        assertThat(names).containsExactlyElementsIn(testPlaylistNames.subList(1, 3))
    }

    @Test fun get_playlist_tracks() = runTest {
        var uris = dao.getPlaylistTracks("")
        assertThat(uris).isEmpty()

        dao.insertPlaylist(testPlaylistNames[0], false, testTracks)
        uris = dao.getPlaylistTracks(testPlaylistNames[0])
        assertThat(uris).containsExactlyElementsIn(testTracks).inOrder()

        dao.insertPlaylist(testPlaylistNames[1], false, testTracks.subList(3, 5))
        uris = dao.getPlaylistTracks(testPlaylistNames[1])
        assertThat(uris).containsExactlyElementsIn(testTracks.subList(3, 5)).inOrder()
    }

    @Test fun rename_playlist() = runTest {
        dao.insertPlaylist(testPlaylistNames[0], false, testTracks)
        val newName = "new playlist name"
        dao.rename(testPlaylistNames[0], newName)
        assertThat(dao.getPlaylistNames().first()).containsExactly(newName)
    }

    @Test fun toggle_playlist_is_active() = runTest {
        dao.insertPlaylist(testPlaylistNames[0], false, testTracks)
        dao.toggleIsActive(testPlaylistNames[0])
        var activePlaylistNames = dao.getActivePlaylistsAndTracks().first().keys.map(Playlist::name)
        assertThat(activePlaylistNames.size).isEqualTo(1)
        assertThat(activePlaylistNames.first()).isEqualTo(testPlaylistNames[0])

        dao.insertPlaylist(testPlaylistNames[1], false, testTracks)
        dao.insertPlaylist(testPlaylistNames[2], false, testTracks)
        dao.toggleIsActive(testPlaylistNames[0])
        dao.toggleIsActive(testPlaylistNames[1])
        dao.toggleIsActive(testPlaylistNames[2])
        activePlaylistNames = dao.getActivePlaylistsAndTracks().first().keys.map(Playlist::name)
        assertThat(activePlaylistNames.size).isEqualTo(2)
        assertThat(activePlaylistNames).containsExactlyElementsIn(testPlaylistNames.subList(1, 3))
    }

    // testPlaylistNames = listOf("playlist b", "playlist d", "playlist a", "playlist c", "track e")
    @Test fun set_volume() = runTest {
        dao.insertPlaylist(testPlaylistNames[0], false, testTracks)
        dao.insertPlaylist(testPlaylistNames[1], false, testTracks)
        dao.insertPlaylist(testPlaylistNames[2], false, testTracks)
        var volumes = dao.getAllPlaylistsSortedByNameAsc("%%")
            .first().map(LibraryPlaylist::volume)
        assertThat(volumes).containsExactly(1f, 1f, 1f)

        dao.setVolume(testPlaylistNames[0], 0.5f)
        dao.setVolume(testPlaylistNames[2], 0.25f)
        volumes = dao.getAllPlaylistsSortedByNameAsc("%%")
            .first().map(LibraryPlaylist::volume)
        assertThat(volumes).containsExactly(0.25f, 0.5f, 1.0f).inOrder()

        dao.setVolume(testPlaylistNames[2], 1f)
        volumes = dao.getAllPlaylistsSortedByNameAsc("%%")
            .first().map(LibraryPlaylist::volume)
        assertThat(volumes).containsExactly(1.0f, 0.5f, 1f).inOrder()
    }

    // testPlaylistNames = listOf("playlist b", "playlist d", "playlist a", "playlist c", "track e")
    @Test fun set_track_hasError() = runTest {
        dao.insertPlaylist(testPlaylistNames[0], false, listOf(testTracks[0]))
        dao.insertPlaylist(testPlaylistNames[1], false, testTracks.subList(2, 5))
        dao.insertPlaylist(testPlaylistNames[2], false, testTracks)

        dao.setTracksHaveError(listOf(testUris[0]))
        var tracks = dao.getPlaylistTracks(testPlaylistNames[0])
        assertThat(tracks.map(Track::hasError)).containsExactly(true)

        dao.setTracksHaveError(listOf(testUris[2]))
        tracks = dao.getPlaylistTracks(testPlaylistNames[1])
        assertThat(tracks.map(Track::hasError))
            .containsExactly(true, false, false).inOrder()

        dao.setTracksHaveError(listOf(testUris[3], testUris[4]))
        tracks = dao.getPlaylistTracks(testPlaylistNames[1])
        assertThat(tracks.map(Track::hasError)).doesNotContain(false)
    }

    @Test fun playlist_hasError_updates() = runTest {
        dao.insertPlaylist(testPlaylistNames[0], false, listOf(testTracks[0]))
        dao.insertPlaylist(testPlaylistNames[1], false, testTracks.subList(2, 5))
        dao.insertPlaylist(testPlaylistNames[2], false, testTracks)
        var playlists = dao.getAllPlaylistsSortedByNameAsc("%%").first()
        assertThat(playlists.map(LibraryPlaylist::hasError)).doesNotContain(true)

        dao.setTracksHaveError(listOf(testUris[0]))
        playlists = dao.getAllPlaylistsSortedByNameAsc("%%").first()
        assertThat(playlists.map(LibraryPlaylist::hasError))
            .containsExactly(false, true, false).inOrder()

        dao.setTracksHaveError(listOf(testUris[2]))
        playlists = dao.getAllPlaylistsSortedByNameAsc("%%").first()
        assertThat(playlists.map(LibraryPlaylist::hasError))
            .containsExactly(false, true, false).inOrder()

        dao.setTracksHaveError(listOf(testUris[3], testUris[4]))
        playlists = dao.getAllPlaylistsSortedByNameAsc("%%").first()
        assertThat(playlists.map(LibraryPlaylist::hasError))
            .containsExactly(false, true, true).inOrder()

        dao.setTracksHaveError(listOf(testUris[1]))
        playlists = dao.getAllPlaylistsSortedByNameAsc("%%").first()
        assertThat(playlists.map(LibraryPlaylist::hasError)).doesNotContain(false)
    }
}