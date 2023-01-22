/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule

@RunWith(AndroidJUnit4::class)
class DatabaseTests {
    private lateinit var db: SoundAuraDatabase
    private lateinit var dao: TrackDao

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SoundAuraDatabase::class.java)

    @Before fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, SoundAuraDatabase::class.java).build()
        dao = db.trackDao()
    }

    @After @Throws(IOException::class)
    fun closeDb() = db.close()

    private suspend fun getAllTracks() =
        dao.getAllTracks(Track.Sort.OrderAdded, false, null).first()

    @Test fun initial_state() = runBlocking {
        val items = getAllTracks()
        assertThat(items).isEmpty()
    }

    @Test fun adding_tracks() = runBlocking {
        initial_state()
        val testTracks = List(3) {
            Track(uriString = "uri$it", name = "track $it")
        }

        dao.insert(testTracks[0])
        var items = getAllTracks()
        assertThat(items).containsExactly(testTracks[0])

        dao.insert(testTracks.subList(1, 3))
        items = getAllTracks()
        assertThat(items).containsExactlyElementsIn(testTracks)
        Unit
    }

    @Test fun removing_tracks() = runBlocking {
        adding_tracks()
        val testTracks = List(3) {
            Track(uriString = "uri$it", name = "track $it")
        }
        dao.delete(testTracks[1].uriString)
        var items = getAllTracks()
        assertThat(items).containsExactly(testTracks[0], testTracks[2])

        dao.delete(listOf(testTracks[0].uriString, testTracks[2].uriString))
        items = getAllTracks()
        assertThat(items).isEmpty()
    }

    @Test fun set_name() = runBlocking {
        adding_tracks()
        var tracks = getAllTracks()
        val oldTrack1Name = "track 1"
        assertThat(tracks[1].name).isEqualTo(oldTrack1Name)

        val newTrack1Name = "new track 1 name"
        dao.setName(tracks[1].uriString, newTrack1Name)
        tracks = getAllTracks()
        assertThat(tracks[1].name).isEqualTo(newTrack1Name)

        dao.setName(tracks[1].uriString, oldTrack1Name)
        tracks = getAllTracks()
        assertThat(tracks[1].name).isEqualTo(oldTrack1Name)
    }

    @Test fun toggle_isActive() = runBlocking {
        adding_tracks()
        var tracks = getAllTracks()
        assertThat(tracks[0].isActive || tracks[1].isActive || tracks[2].isActive).isFalse()

        dao.toggleIsActive(tracks[1].uriString)
        dao.toggleIsActive(tracks[2].uriString)
        tracks = getAllTracks()
        assertThat(tracks[0].isActive).isFalse()
        assertThat(tracks[1].isActive && tracks[2].isActive).isTrue()

        dao.toggleIsActive(tracks[2].uriString)
        tracks = getAllTracks()
        assertThat(tracks[0].isActive || tracks[2].isActive).isFalse()
        assertThat(tracks[1].isActive).isTrue()
    }

    @Test fun set_volume() = runBlocking {
        adding_tracks()
        var tracks = getAllTracks()
        assertThat(tracks[1].volume).isEqualTo(1f)
        assertThat(tracks[2].volume).isEqualTo(1f)

        dao.setVolume(tracks[1].uriString, 0.5f)
        dao.setVolume(tracks[2].uriString, 0.25f)
        tracks = getAllTracks()
        assertThat(tracks[1].volume).isEqualTo(0.5f)
        assertThat(tracks[2].volume).isEqualTo(0.25f)

        dao.setVolume(tracks[2].uriString, 1f)
        tracks = getAllTracks()
        assertThat(tracks[2].volume).isEqualTo(1f)
    }

    @Test fun set_hasError() = runBlocking {
        adding_tracks()
        var tracks = getAllTracks()
        assertThat(tracks[0].hasError).isFalse()
        assertThat(tracks[1].hasError).isFalse()
        assertThat(tracks[2].hasError).isFalse()

        dao.notifyOfError(tracks[1].uriString)
        tracks = getAllTracks()
        assertThat(tracks[0].hasError).isFalse()
        assertThat(tracks[1].hasError).isTrue()
        assertThat(tracks[2].hasError).isFalse()

        dao.notifyOfError(tracks[0].uriString)
        tracks = getAllTracks()
        assertThat(tracks[0].hasError).isTrue()
        assertThat(tracks[1].hasError).isTrue()
        assertThat(tracks[2].hasError).isFalse()
    }

    @Test fun get_all_tracks_sorted_by_name_asc_with_active_first() = runBlocking {
        val testTracks = listOf(
            Track("track0uri", "b track", isActive = false),
            Track("track1uri", "c track", isActive = true),
            Track("track2uri", "a track", isActive = false),
            Track("track4uri", "d track", isActive = true))
        dao.insert(testTracks)
        var tracks = dao.getAllTracks(Track.Sort.NameAsc, true, null).first()
        assertThat(tracks).containsExactly(
            testTracks[1], testTracks[3], testTracks[2], testTracks[0]
        ).inOrder()

        tracks = dao.getAllTracks(Track.Sort.NameAsc, true, "track").first()
        assertThat(tracks).containsExactly(
            testTracks[1], testTracks[3], testTracks[2], testTracks[0]
        ).inOrder()

        dao.setName(testTracks[0].uriString, "f track")
        tracks = dao.getAllTracks(Track.Sort.NameAsc, true, "track").first()
        assertThat(tracks).containsExactly(
            testTracks[1], testTracks[3], testTracks[2], testTracks[0].copy(name = "f track")
        ).inOrder()
    }

    @Test fun get_all_tracks_sorted_by_name_desc_with_active_first() = runBlocking {
        val testTracks = listOf(
            Track("track0uri", "b track", isActive = false),
            Track("track1uri", "c track", isActive = true),
            Track("track2uri", "a track", isActive = false),
            Track("track4uri", "d track", isActive = true))
        dao.insert(testTracks)
        var tracks = dao.getAllTracks(Track.Sort.NameDesc, true, null).first()
        assertThat(tracks).containsExactly(
            testTracks[3], testTracks[1], testTracks[0], testTracks[2]
        ).inOrder()

        tracks = dao.getAllTracks(Track.Sort.NameDesc, true, "track").first()
        assertThat(tracks).containsExactly(
            testTracks[3], testTracks[1], testTracks[0], testTracks[2]
        ).inOrder()

        dao.setName(testTracks[2].uriString, "f track")
        tracks = dao.getAllTracks(Track.Sort.NameDesc, true, "track").first()
        assertThat(tracks).containsExactly(
            testTracks[3], testTracks[1], testTracks[2].copy(name = "f track"), testTracks[0]
        ).inOrder()
    }

    @Test fun get_all_tracks_sorted_by_order_added_with_active_first() = runBlocking {
        val testTracks = listOf(
            Track("track0uri", "b track", isActive = false),
            Track("track1uri", "c track", isActive = true),
            Track("track2uri", "a track", isActive = false),
            Track("track4uri", "d track", isActive = true))
        dao.insert(testTracks)
        var tracks = dao.getAllTracks(Track.Sort.OrderAdded, true, null).first()
        assertThat(tracks).containsExactly(
            testTracks[1], testTracks[3], testTracks[0], testTracks[2]
        ).inOrder()

        tracks = dao.getAllTracks(Track.Sort.OrderAdded, true, "track").first()
        assertThat(tracks).containsExactly(
            testTracks[1], testTracks[3], testTracks[0], testTracks[2]
        ).inOrder()

        dao.setName(testTracks[2].uriString, "f track")
        tracks = dao.getAllTracks(Track.Sort.OrderAdded, true, "track").first()
        assertThat(tracks).containsExactly(
            testTracks[1], testTracks[3], testTracks[0], testTracks[2].copy(name = "f track"),
        ).inOrder()
    }

    @Test fun get_all_tracks_sorted_by_name_asc() = runBlocking {
        val testTracks = listOf(
            Track("track0uri", "b track"),
            Track("track1uri", "c track"),
            Track("track2uri", "a track"))
        dao.insert(testTracks)
        var tracks = dao.getAllTracks(Track.Sort.NameAsc, false, null).first()
        assertThat(tracks).containsExactly(
            testTracks[2], testTracks[0], testTracks[1]
        ).inOrder()

        tracks = dao.getAllTracks(Track.Sort.NameAsc, false, "track").first()
        assertThat(tracks).containsExactly(
            testTracks[2], testTracks[0], testTracks[1]
        ).inOrder()

        tracks = dao.getAllTracks(Track.Sort.NameAsc, false, "b").first()
        assertThat(tracks).containsExactly(testTracks[0])

        dao.setName(testTracks[0].uriString, "f track")
        tracks = dao.getAllTracks(Track.Sort.NameAsc, false, "track").first()
        assertThat(tracks).containsExactly(
            testTracks[2], testTracks[1], testTracks[0].copy(name = "f track")
        ).inOrder()
    }

    @Test fun get_all_tracks_sorted_by_name_desc() = runBlocking {
        val testTracks = listOf(
            Track("track0uri", "b track"),
            Track("track1uri", "c track"),
            Track("track2uri", "a track"))
        dao.insert(testTracks)
        var tracks = dao.getAllTracks(Track.Sort.NameDesc, false, null).first()
        assertThat(tracks).containsExactly(
            testTracks[1], testTracks[0], testTracks[2]
        ).inOrder()

        tracks = dao.getAllTracks(Track.Sort.NameDesc, false, "track").first()
        assertThat(tracks).containsExactly(
            testTracks[1], testTracks[0], testTracks[2]
        ).inOrder()

        tracks = dao.getAllTracks(Track.Sort.NameDesc, false, "b").first()
        assertThat(tracks).containsExactly(testTracks[0])

        dao.setName(testTracks[0].uriString, "f track")
        tracks = dao.getAllTracks(Track.Sort.NameDesc, false, "track").first()
        assertThat(tracks).containsExactly(
            testTracks[0].copy(name = "f track"), testTracks[1], testTracks[2]
        ).inOrder()
    }

    @Test fun get_all_tracks_sorted_by_order_added() = runBlocking {
        val testTracks = listOf(
            Track("track0uri", "b track"),
            Track("track1uri", "c track"),
            Track("track2uri", "a track"))
        dao.insert(testTracks)
        var tracks = dao.getAllTracks(Track.Sort.OrderAdded, false, null).first()
        assertThat(tracks).containsExactly(
            testTracks[0], testTracks[1], testTracks[2]
        ).inOrder()

        tracks = dao.getAllTracks(Track.Sort.OrderAdded, false, "track").first()
        assertThat(tracks).containsExactly(
            testTracks[0], testTracks[1], testTracks[2]
        ).inOrder()

        dao.delete(testTracks[1].uriString)
        tracks = dao.getAllTracks(Track.Sort.OrderAdded, false, null).first()
        assertThat(tracks).containsExactly(
            testTracks[0], testTracks[2]
        ).inOrder()
        val newTrack = Track("track3uri", "z track")

        dao.insert(newTrack)
        tracks = dao.getAllTracks(Track.Sort.OrderAdded, false, null).first()
        assertThat(tracks).containsExactly(
            testTracks[0], testTracks[2], newTrack
        ).inOrder()
    }

    @Test fun getAllActiveTracks() = runBlocking {
        adding_tracks()
        var allTracks = getAllTracks()
        var activeTracks = dao.getActiveTracks().first()
        assertThat(activeTracks).isEmpty()

        dao.toggleIsActive(allTracks[0].uriString)
        dao.toggleIsActive(allTracks[2].uriString)
        allTracks = getAllTracks()
        activeTracks = dao.getActiveTracks().first()
        assertThat(activeTracks).containsExactly(
            allTracks[0].toActiveTrack(),
            allTracks[2].toActiveTrack())

        dao.toggleIsActive(allTracks[0].uriString)
        allTracks = getAllTracks()
        activeTracks = dao.getActiveTracks().first()
        assertThat(activeTracks).containsExactly(allTracks[2].toActiveTrack())
        Unit
    }

    @Test @Throws(IOException::class)
    fun all_migrations() {
        val dbName = "migration test database"
        val oldestDb = helper.createDatabase(dbName, 1)
        oldestDb.close()

        val newestDb = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            SoundAuraDatabase::class.java,
            dbName
        ).also(SoundAuraDatabase::addAllMigrations).build()
        newestDb.openHelper.writableDatabase.close()
    }
}