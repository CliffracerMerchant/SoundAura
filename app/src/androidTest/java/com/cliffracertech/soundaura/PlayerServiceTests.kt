/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.net.toUri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule
import com.cliffracertech.soundaura.model.database.SoundAuraDatabase
import com.cliffracertech.soundaura.service.PlayerService
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerServiceTests {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @get:Rule val serviceRule = ServiceTestRule()
    private lateinit var db: SoundAuraDatabase
    private val dao get() = db.playlistDao()
    private val testPlaylistUri = "test playlist 1"

    @Before fun init() {
        // This test track is added so that PlayerService doesn't prevent
        // changes to STATE_PLAYING due to there not being any active playlists.
        db = Room.inMemoryDatabaseBuilder(context, SoundAuraDatabase::class.java).build()
        val map = LinkedHashMap<Uri, String>()
        map[testPlaylistUri.toUri()] = testPlaylistUri
        runBlocking {
            dao.insertSingleTrackPlaylists(map)
            dao.toggleIsActive(testPlaylistUri)
        }
    }

    @After fun finish() { db.close() }

    @Test fun playback_begins_in_paused_state_by_default() = runTest {
        serviceRule.startService(Intent(context, PlayerService::class.java))
        waitUntil { PlayerService.playbackState != PlaybackStateCompat.STATE_STOPPED }
        assertThat(PlayerService.playbackState).isEqualTo(PlaybackStateCompat.STATE_PAUSED)
    }

    @Test fun play_intent() = runTest {
        waitUntil { dao.getAtLeastOnePlaylistIsActive().first() }
        serviceRule.startService(PlayerService.playIntent(context))
        waitUntil { PlayerService.playbackState == PlaybackStateCompat.STATE_PLAYING }
        assertThat(PlayerService.playbackState).isEqualTo(PlaybackStateCompat.STATE_PLAYING)
    }

    @Test fun pause_intent() = runTest {
        serviceRule.startService(PlayerService.pauseIntent(context))
        waitUntil { PlayerService.playbackState != PlaybackStateCompat.STATE_STOPPED }
        assertThat(PlayerService.playbackState).isEqualTo(PlaybackStateCompat.STATE_PAUSED)
    }

    @Test fun stop_intent_while_stopped_no_ops() = runTest {
        serviceRule.startService(PlayerService.stopIntent(context))
        waitUntil { PlayerService.playbackState != PlaybackStateCompat.STATE_STOPPED } // should time out
        assertThat(PlayerService.playbackState).isEqualTo(PlaybackStateCompat.STATE_STOPPED)
    }

    @Test fun stop_intent_while_playing() = runTest {
        waitUntil { dao.getAtLeastOnePlaylistIsActive().first() }
        serviceRule.startService(PlayerService.playIntent(context))
        waitUntil { PlayerService.playbackState != PlaybackStateCompat.STATE_STOPPED }
        serviceRule.startService(PlayerService.stopIntent(context))
        waitUntil { PlayerService.playbackState != PlaybackStateCompat.STATE_PLAYING }
        assertThat(PlayerService.playbackState).isEqualTo(PlaybackStateCompat.STATE_STOPPED)
    }

    @Test fun binding_succeeds() = runTest {
        val intent = Intent(context, PlayerService::class.java)
        val binder = serviceRule.bindService(intent)
        assertThat(binder as? PlayerService.Binder).isNotNull()
    }

    @Test fun binder_is_playing_state() = runTest {
        val intent = Intent(context, PlayerService::class.java)
        val binder = serviceRule.bindService(intent)
        val service = binder as PlayerService.Binder
        assertThat(service.isPlaying).isFalse()

        val playIntent = PlayerService.playIntent(context)
        serviceRule.startService(playIntent)
        waitUntil { PlayerService.playbackState != PlaybackStateCompat.STATE_PAUSED }
        assertThat(service.isPlaying).isTrue()

        val pauseIntent = PlayerService.pauseIntent(context)
        serviceRule.startService(pauseIntent)
        waitUntil { PlayerService.playbackState != PlaybackStateCompat.STATE_PLAYING }
        assertThat(service.isPlaying).isFalse()

        val stopIntent = PlayerService.stopIntent(context)
        serviceRule.startService(stopIntent)
        waitUntil { PlayerService.playbackState != PlaybackStateCompat.STATE_PAUSED }
        assertThat(service.isPlaying).isFalse()
    }

    @Test fun binder_toggle_is_playing() = runTest {
        val intent = Intent(context, PlayerService::class.java)
        val binder = serviceRule.bindService(intent)
        val service = binder as PlayerService.Binder
        assertThat(service.isPlaying).isFalse()

        service.toggleIsPlaying()
        waitUntil { PlayerService.playbackState != PlaybackStateCompat.STATE_PAUSED }
        assertThat(service.isPlaying).isTrue()

        service.toggleIsPlaying()
        waitUntil { PlayerService.playbackState != PlaybackStateCompat.STATE_PLAYING }
        assertThat(service.isPlaying).isFalse()
    }

    @Test fun service_prevents_playing_with_no_active_playlists() = runTest {
        val intent = Intent(context, PlayerService::class.java)
        val binder = serviceRule.bindService(intent)
        waitUntil { PlayerService.playbackState != PlaybackStateCompat.STATE_STOPPED }
        assertThat(PlayerService.playbackState).isEqualTo(PlaybackStateCompat.STATE_PAUSED)
        dao.toggleIsActive(testPlaylistUri)
        waitUntil { !dao.getAtLeastOnePlaylistIsActive().first() }

        val playIntent = PlayerService.playIntent(context)
        serviceRule.startService(playIntent)
        waitUntil { PlayerService.playbackState == PlaybackStateCompat.STATE_PLAYING } // should time out
        assertThat(PlayerService.playbackState).isEqualTo(PlaybackStateCompat.STATE_PAUSED)

        (binder as PlayerService.Binder).toggleIsPlaying()
        waitUntil { PlayerService.playbackState == PlaybackStateCompat.STATE_PLAYING } // should time out
        assertThat(PlayerService.playbackState).isEqualTo(PlaybackStateCompat.STATE_PAUSED)
    }
}