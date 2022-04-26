/* This file is part of SoundAura, which is released under the terms of the Apache
 * License 2.0. See license.md in the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(sdk=[30])
@RunWith(RobolectricTestRunner::class)
class TrackListViewModelTests {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val coroutineDispatcher = TestCoroutineDispatcher()
    private val coroutineScope = TestCoroutineScope(coroutineDispatcher + Job())
    private lateinit var searchQueryState: SearchQueryState
    private lateinit var db: SoundAuraDatabase
    private lateinit var dao: TrackDao
    private lateinit var instance: TrackListViewModel
    private val testTracks = List(5) {
        Track(uriString = "uri$it", name = "track $it")
    }

    @Before fun init() {
        db = Room.inMemoryDatabaseBuilder(context, SoundAuraDatabase::class.java).build()
        dao = db.trackDao()
        searchQueryState = SearchQueryState()
        instance = TrackListViewModel(context, context.dataStore, dao,
                                      searchQueryState, coroutineScope)
        Dispatchers.setMain(coroutineDispatcher)
    }
    @After fun cleanUp() {
        Dispatchers.resetMain()
        coroutineDispatcher.cleanupTestCoroutines()
        coroutineScope.cancel()
        db.close()
    }

    // Unfortunately I can't get these tests to work without a Thread.sleep
    // call. This shouldn't be necessary when using a test dispatcher and
    // coroutine scope, but is for some reason. This might have something to
    // do with how the tracks property is backed by a MutableState instance
    // whose value updates when the Flow of tracks returned from the TrackDao
    // emits a new value.

    @Test fun tracksPropertyReflectsAddedTracks() {
        assertThat(instance.tracks).isEmpty()
        runBlocking { dao.insert(testTracks) }
        Thread.sleep(50L)
        assertThat(instance.tracks)
            .containsExactlyElementsIn(testTracks).inOrder()
    }

    @Test fun deleteTrackDialogConfirm() {
        tracksPropertyReflectsAddedTracks()
        val track3 = testTracks[2]
        instance.onDeleteTrackDialogConfirm(context, track3.uriString)
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