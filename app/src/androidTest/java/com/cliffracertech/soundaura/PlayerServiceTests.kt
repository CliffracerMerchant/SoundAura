/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.content.Context
import android.content.Intent
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeoutException

@RunWith(AndroidJUnit4::class)
class PlayerServiceTests {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: SoundAuraDatabase
    private val testTrackUriString = "test track 1"
    @get:Rule val serviceRule = ServiceTestRule()

    @Before fun createTestTrack() {
        // This test track is added so that PlayerService doesn't prevent
        // changes to STATE_PLAYING due to there not being any active tracks.
        db = Room.inMemoryDatabaseBuilder(context, SoundAuraDatabase::class.java).build()
        val testTrack = Track(
            uriString = testTrackUriString,
            name = testTrackUriString,
            isActive = true)
        runBlocking { db.trackDao().insert(testTrack) }
    }

    @Test @Throws(TimeoutException::class)
    fun playbackBeginsInPausedStateByDefault() {
        val intent = Intent(context, PlayerService::class.java)
        serviceRule.startService(intent)
        assertThat(PlayerService.playbackState).isEqualTo(PlaybackStateCompat.STATE_PAUSED)
    }

    @Test @Throws(TimeoutException::class)
    fun playIntent() {
        val intent = PlayerService.playIntent(context)
        serviceRule.startService(intent)
        assertThat(PlayerService.playbackState).isEqualTo(PlaybackStateCompat.STATE_PLAYING)
    }

    @Test @Throws(TimeoutException::class)
    fun pauseIntent() {
        val intent = PlayerService.pauseIntent(context)
        serviceRule.startService(intent)
        assertThat(PlayerService.playbackState).isEqualTo(PlaybackStateCompat.STATE_PAUSED)
    }

    @Test @Throws(TimeoutException::class)
    fun stopIntent() {
        val intent = PlayerService.stopIntent(context)
        serviceRule.startService(intent)
        assertThat(PlayerService.playbackState).isEqualTo(PlaybackStateCompat.STATE_STOPPED)
    }

    @Test @Throws(TimeoutException::class)
    fun bindingSucceeds() {
        val intent = Intent(context, PlayerService::class.java)
        val binder = serviceRule.bindService(intent)
        val service = binder as? PlayerService.Binder
        assertThat(service).isNotNull()
    }

    @Test @Throws(TimeoutException::class)
    fun binderExposedIsPlayingStateWorks() {
        val intent = Intent(context, PlayerService::class.java)
        val binder = serviceRule.bindService(intent)
        val service = binder as PlayerService.Binder
        assertThat(service.isPlaying).isFalse()

        val playIntent = PlayerService.playIntent(context)
        serviceRule.startService(playIntent)
        assertThat(service.isPlaying).isTrue()

        val pauseIntent = PlayerService.pauseIntent(context)
        serviceRule.startService(pauseIntent)
        assertThat(service.isPlaying).isFalse()

        val stopIntent = PlayerService.stopIntent(context)
        serviceRule.startService(stopIntent)
        assertThat(service.isPlaying).isFalse()
    }

    @Test @Throws(TimeoutException::class)
    fun binderIsPlayingControlsWorks() {
        val intent = Intent(context, PlayerService::class.java)
        val binder = serviceRule.bindService(intent)
        val service = binder as PlayerService.Binder
        assertThat(service.isPlaying).isFalse()
        service.toggleIsPlaying()
        assertThat(service.isPlaying).isTrue()
        service.toggleIsPlaying()
        assertThat(service.isPlaying).isFalse()
    }

    @Test @Throws(TimeoutException::class)
    fun servicePausesInsteadOfStoppingWhenBoundToActivity() {
        val intent = Intent(context, PlayerService::class.java)
        serviceRule.bindService(intent)
        val stopIntent = PlayerService.stopIntent(context)
        serviceRule.startService(stopIntent)
        assertThat(PlayerService.playbackState).isEqualTo(PlaybackStateCompat.STATE_PAUSED)
    }

    @Test @Throws(TimeoutException::class)
    fun servicePreventsPlayingWithNoActiveTracks() {
        val intent = Intent(context, PlayerService::class.java)
        val binder = serviceRule.bindService(intent)
        assertThat(PlayerService.playbackState).isEqualTo(PlaybackStateCompat.STATE_PAUSED)
        runBlocking { db.trackDao().toggleIsActive(testTrackUriString) }

        val playIntent = PlayerService.playIntent(context)
        serviceRule.startService(playIntent)
        assertThat(PlayerService.playbackState).isEqualTo(PlaybackStateCompat.STATE_PAUSED)

        (binder as PlayerService.Binder).toggleIsPlaying()
        assertThat(PlayerService.playbackState).isEqualTo(PlaybackStateCompat.STATE_PAUSED)
    }
}