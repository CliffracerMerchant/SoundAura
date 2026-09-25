/*
 * This file is part of SoundAura, which is released under the terms of the Apache
 * License 2.0. See license.md in the project's root directory to see the full license.
 */

package com.cliffracertech.soundaura.service

import android.content.Context
import android.content.Context.AUDIO_SERVICE
import android.media.AudioManager
import android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED
import androidx.media.AudioAttributesCompat
import androidx.media.AudioAttributesCompat.CONTENT_TYPE_UNKNOWN
import androidx.media.AudioAttributesCompat.USAGE_MEDIA
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import androidx.media.AudioManagerCompat.AUDIOFOCUS_GAIN
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * A manager for audio focus, with a setting to temporarily ignore audio focus.
 *
 * AudioFocusManager tracks the acquisition and loss of audio focus for use in
 * environments (e.g. Android) where audio focus is expected to be acquired to
 * play audio. Audio focus can be requested via [requestAudioFocus] or
 * abandoned via [abandonAudioFocus]. Audio focus can be temporarily ignored by
 * setting the property [ignoreAudioFocus] to true or false.
 *
 * The value of [hasAudioFocus] is intended to reflect whether the app has been
 * granted audio focus by the app environment (e.g. Android) when
 * [ignoreAudioFocus] is false, but to always be true when [ignoreAudioFocus]
 * is true. This allows audio players to always know whether they are allowed
 * to play audio (i.e. when [hasAudioFocus] is true), regardless of whether it
 * is because the system has granted audio focus, or because audio focus rules
 * are being ignored.
 */
abstract class AudioFocusManager {
    var hasAudioFocus = false
        protected set
    var ignoreAudioFocus = false
        set(value) {
            field = value
            if (value) {
                abandonAudioFocus()
                hasAudioFocus = true
            } else hasAudioFocus = requestAudioFocus()
        }

    /** Request audio focus, and return whether the request was granted. */
    abstract fun requestAudioFocus(): Boolean

    abstract fun abandonAudioFocus()
}

/**
 * A sample AudioFocusManager for use in testing. Audio focus requests will be:
 *   - granted if [ignoreAudioFocus] is true
 *   - granted if [ignoreAudioFocus] is false and [simulateSystemDenyingRequests] is false
 *   - not granted if [ignoreAudioFocus] is false and [simulateSystemDenyingRequests] is true
 */
class TestAudioFocusManager(): AudioFocusManager() {
    var simulateSystemDenyingRequests = false

    override fun requestAudioFocus(): Boolean {
        val newValue = ignoreAudioFocus || !simulateSystemDenyingRequests
        hasAudioFocus = newValue
        return newValue
    }

    override fun abandonAudioFocus() {
        hasAudioFocus = false
    }
}

/** An implementation of [AudioFocusManager] for use in Android environments. */
class AndroidAudioFocusManager(context: Context): AudioFocusManager() {
    private val androidAudioManager = context.getSystemService(AUDIO_SERVICE) as AudioManager

    private val audioFocusRequest =
        AudioFocusRequestCompat.Builder(AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributesCompat.Builder()
                .setContentType(CONTENT_TYPE_UNKNOWN)
                .setUsage(USAGE_MEDIA).build())
            .setOnAudioFocusChangeListener { focusChange ->
                hasAudioFocus = focusChange == AUDIOFOCUS_GAIN
            }.build()

    override fun requestAudioFocus() =
        if (ignoreAudioFocus) true
        else AudioManagerCompat.requestAudioFocus(
                androidAudioManager, audioFocusRequest
            ) == AUDIOFOCUS_REQUEST_GRANTED

    override fun abandonAudioFocus() {
        if (ignoreAudioFocus)
            return
        AudioManagerCompat.abandonAudioFocusRequest(androidAudioManager, audioFocusRequest)
        hasAudioFocus = false
    }
}

@Module @InstallIn(SingletonComponent::class)
class AudioFocusManagerModule {
    @Singleton @Provides
    fun provideAudioFocusManager(@ApplicationContext context: Context): AudioFocusManager =
        AndroidAudioFocusManager(context)
}
