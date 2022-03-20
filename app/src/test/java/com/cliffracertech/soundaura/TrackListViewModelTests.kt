/* This file is part of SoundAura, which is released under the terms of the Apache
 * License 2.0. See license.md in the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScope
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(sdk=[30])
@RunWith(RobolectricTestRunner::class)
class TrackListViewModelTests {
    private lateinit var instance: TrackListViewModel
    private lateinit var db: SoundAuraDatabase
    private lateinit var dao: TrackDao
    private lateinit var searchQueryState: SearchQueryState
    private val testTracks = List(5) {
        Track(uriString = "uri$it", name = "track $it")
    }

    @Before fun init() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SoundAuraDatabase::class.java).build()
        dao = db.trackDao()
        searchQueryState = SearchQueryState()
        instance = TrackListViewModel(context, context.dataStore, dao,
                                      searchQueryState, TestCoroutineScope())
    }

    @After fun closeDb() = db.close()

    @Test fun tracksPropertyReflectsAddedTracks() {
        // Unfortunately I can't get this test to work without a Thread.sleep call.
        // Normally using a TestCoroutineScope and/or a TestCoroutineDispatcher
        // would cause all jobs to be executed immediately in blocking fashion,
        // but only the combination of using a TestCoroutineDispatcher and a
        // Thread.sleep call seems to allow this test to pass (even though it
        // does pass when tested manually). This likely has something to do with
        // how the tracks property is backed by a MutableState instance whose
        // value updates when the StateFlow of tracks returned from the TrackDao
        // updates its own value.
        assertThat(instance.tracks).isEmpty()
        runBlocking { dao.insert(testTracks) }
        Thread.sleep(50L)
        assertThat(instance.tracks)
            .containsExactlyElementsIn(testTracks).inOrder()
    }

    @Test fun deleteTrackDialogConfirm() {
        tracksPropertyReflectsAddedTracks()
        val track3 = testTracks[2]
        instance.onDeleteTrackDialogConfirm(track3.uriString)
        Thread.sleep(50L)
        assertThat(instance.tracks)
            .containsExactlyElementsIn(testTracks.minus(track3)).inOrder()
    }

    @Test fun trackPlayPauseClick() {
        tracksPropertyReflectsAddedTracks()
        assertThat(instance.tracks.map { it.playing }).doesNotContain(true)
        instance.onTrackPlayPauseClick(testTracks[3].uriString)
        Thread.sleep(50L)
        assertThat(instance.tracks.map { it.playing })
            .containsExactlyElementsIn(listOf(false, false, false, true, false))
            .inOrder()
        instance.onTrackPlayPauseClick(testTracks[1].uriString)
        instance.onTrackPlayPauseClick(testTracks[3].uriString)
        Thread.sleep(50L)
        assertThat(instance.tracks.map { it.playing })
            .containsExactlyElementsIn(listOf(false, true, false, false, false))
            .inOrder()
    }

    @Test fun trackVolumeChangeRequest() {
        tracksPropertyReflectsAddedTracks()
        val expectedVolumes = mutableListOf(1f, 1f, 1f, 1f, 1f)
        assertThat(instance.tracks.map { it.volume })
            .containsExactlyElementsIn(expectedVolumes).inOrder()
        instance.onTrackVolumeChangeRequest(testTracks[2].uriString, 0.5f)
        expectedVolumes[2] = 0.5f
        Thread.sleep(50L)
        assertThat(instance.tracks.map { it.volume })
            .containsExactlyElementsIn(expectedVolumes).inOrder()
        instance.onTrackVolumeChangeRequest(testTracks[2].uriString, 1f)
        instance.onTrackVolumeChangeRequest(testTracks[1].uriString, 0.25f)
        instance.onTrackVolumeChangeRequest(testTracks[4].uriString, 0.75f)
        expectedVolumes[2] = 1f
        expectedVolumes[1] = 0.25f
        expectedVolumes[4] = 0.75f
        Thread.sleep(50L)
        assertThat(instance.tracks.map { it.volume })
            .containsExactlyElementsIn(expectedVolumes).inOrder()

    }

    @Test fun trackRenameDialogConfirm() {
        tracksPropertyReflectsAddedTracks()
        assertThat(instance.tracks[3].name).isEqualTo(testTracks[3].name)
        val newTrack3Name = "new ${testTracks[3].name}"
        instance.onTrackRenameDialogConfirm(testTracks[3].uriString, newTrack3Name)
        Thread.sleep(50L)
        val expectedTracks = testTracks
            .minus(testTracks[3])
            .plus(testTracks[3].copy(name = newTrack3Name))
        assertThat(instance.tracks)
            .containsExactlyElementsIn(expectedTracks)
    }
}