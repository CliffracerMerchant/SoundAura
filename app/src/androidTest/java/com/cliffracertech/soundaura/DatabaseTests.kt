/* This file is part of SoundAura, which is released under the terms of the Apache
 * License 2.0. See license.md in the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class DatabaseTests {
    private lateinit var db: SoundAuraDatabase
    private lateinit var dao: TrackDao

    @Before fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SoundAuraDatabase::class.java).build()
        dao = db.trackDao()
    }

    @After @Throws(IOException::class)
    fun closeDb() = db.close()

    private suspend fun getAllTracks() =
        dao.getAllTracks(Track.Sort.OrderAdded, null).first()

    @Test fun initialState() = runBlocking {
        val items = getAllTracks()
        assertThat(items).isEmpty()
    }

    @Test fun addingTracks() = runBlocking {
        initialState()
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

    @Test fun removingTracks() = runBlocking {
        addingTracks()
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

    @Test fun updatePlaying() = runBlocking {
        addingTracks()
        var tracks = getAllTracks()
        assertThat(tracks[0].playing || tracks[1].playing || tracks[2].playing).isFalse()

        dao.updatePlaying(tracks[1].uriString, true)
        dao.updatePlaying(tracks[2].uriString, true)
        tracks = getAllTracks()
        assertThat(tracks[0].playing).isFalse()
        assertThat(tracks[1].playing && tracks[2].playing).isTrue()

        dao.updatePlaying(tracks[2].uriString, false)
        tracks = getAllTracks()
        assertThat(tracks[0].playing || tracks[2].playing).isFalse()
        assertThat(tracks[1].playing).isTrue()
    }

    @Test fun updateVolume() = runBlocking {
        addingTracks()
        var tracks = getAllTracks()
        assertThat(tracks[1].volume).isEqualTo(1f)
        assertThat(tracks[2].volume).isEqualTo(1f)

        dao.updateVolume(tracks[1].uriString, 0.5f)
        dao.updateVolume(tracks[2].uriString, 0.25f)
        tracks = getAllTracks()
        assertThat(tracks[1].volume).isEqualTo(0.5f)
        assertThat(tracks[2].volume).isEqualTo(0.25f)

        dao.updateVolume(tracks[2].uriString, 1f)
        tracks = getAllTracks()
        assertThat(tracks[2].volume).isEqualTo(1f)
    }

    @Test fun updateName() = runBlocking {
        addingTracks()
        var tracks = getAllTracks()
        val oldTrack1Name = "track 1"
        assertThat(tracks[1].name).isEqualTo(oldTrack1Name)

        val newTrack1Name = "new track 1 name"
        dao.updateName(tracks[1].uriString, newTrack1Name)
        tracks = getAllTracks()
        assertThat(tracks[1].name).isEqualTo(newTrack1Name)

        dao.updateName(tracks[1].uriString, oldTrack1Name)
        tracks = getAllTracks()
        assertThat(tracks[1].name).isEqualTo(oldTrack1Name)
    }

    @Test fun getAllTracksSortedByNameAsc() = runBlocking {
        val testTracks = listOf(
            Track("track0uri", "b track"),
            Track("track1uri", "c track"),
            Track("track2uri", "a track"))
        dao.insert(testTracks)
        var tracks = dao.getAllTracks(Track.Sort.NameAsc, null).first()
        assertThat(tracks).containsExactly(
            testTracks[2], testTracks[0], testTracks[1]
        ).inOrder()

        tracks = dao.getAllTracks(Track.Sort.NameAsc, "track").first()
        assertThat(tracks).containsExactly(
            testTracks[2], testTracks[0], testTracks[1]
        ).inOrder()

        tracks = dao.getAllTracks(Track.Sort.NameAsc, "b").first()
        assertThat(tracks).containsExactly(testTracks[0])

        dao.updateName(testTracks[0].uriString, "f track")
        tracks = dao.getAllTracks(Track.Sort.NameAsc, "track").first()
        assertThat(tracks).containsExactly(
            testTracks[2], testTracks[1], testTracks[0].copy(name = "f track")
        ).inOrder()
    }

    @Test fun getAllTracksSortedByNameDesc() = runBlocking {
        val testTracks = listOf(
            Track("track0uri", "b track"),
            Track("track1uri", "c track"),
            Track("track2uri", "a track"))
        dao.insert(testTracks)
        var tracks = dao.getAllTracks(Track.Sort.NameDesc, null).first()
        assertThat(tracks).containsExactly(
            testTracks[1], testTracks[0], testTracks[2]
        ).inOrder()

        tracks = dao.getAllTracks(Track.Sort.NameDesc, "track").first()
        assertThat(tracks).containsExactly(
            testTracks[1], testTracks[0], testTracks[2]
        ).inOrder()

        tracks = dao.getAllTracks(Track.Sort.NameDesc, "b").first()
        assertThat(tracks).containsExactly(testTracks[0])

        dao.updateName(testTracks[0].uriString, "f track")
        tracks = dao.getAllTracks(Track.Sort.NameDesc, "track").first()
        assertThat(tracks).containsExactly(
            testTracks[0].copy(name = "f track"), testTracks[1], testTracks[2]
        ).inOrder()
    }

    @Test fun getAllTracksSortedByOrderAdded() = runBlocking {
        val testTracks = listOf(
            Track("track0uri", "b track"),
            Track("track1uri", "c track"),
            Track("track2uri", "a track"))
        dao.insert(testTracks)
        var tracks = dao.getAllTracks(Track.Sort.OrderAdded, null).first()
        assertThat(tracks).containsExactly(
            testTracks[0], testTracks[1], testTracks[2]
        ).inOrder()

        tracks = dao.getAllTracks(Track.Sort.OrderAdded, "track").first()
        assertThat(tracks).containsExactly(
            testTracks[0], testTracks[1], testTracks[2]
        ).inOrder()

        dao.delete(testTracks[1].uriString)
        tracks = dao.getAllTracks(Track.Sort.OrderAdded, null).first()
        assertThat(tracks).containsExactly(
            testTracks[0], testTracks[2]
        ).inOrder()
        val newTrack = Track("track3uri", "z track")

        dao.insert(newTrack)
        tracks = dao.getAllTracks(Track.Sort.OrderAdded, null).first()
        assertThat(tracks).containsExactly(
            testTracks[0], testTracks[2], newTrack
        ).inOrder()
    }

    @Test fun getAllPlayingTracks() = runBlocking {
        addingTracks()
        var allTracks = getAllTracks()
        var playingTracks = dao.getAllPlayingTracks().first()
        assertThat(playingTracks).isEmpty()

        dao.updatePlaying(allTracks[0].uriString, true)
        dao.updatePlaying(allTracks[2].uriString, true)
        allTracks = getAllTracks()
        playingTracks = dao.getAllPlayingTracks().first()
        assertThat(playingTracks).containsExactly(allTracks[0], allTracks[2])

        dao.updatePlaying(allTracks[0].uriString, false)
        allTracks = getAllTracks()
        playingTracks = dao.getAllPlayingTracks().first()
        assertThat(playingTracks).containsExactly(allTracks[2])
        Unit
    }
}