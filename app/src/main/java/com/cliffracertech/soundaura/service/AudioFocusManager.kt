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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A manager for audio focus, with a setting to temporarily ignore audio focus.
 *
 * AudioFocusManager tracks the acquisition and loss of audio focus for use in
 * environments (e.g. Android) where audio focus is expected to be acquired to
 * play audio. Audio focus can be requested via [requestAudioFocus], abandoned
 * via [abandonAudioFocus],
 */
abstract class AudioFocusManager {
    var hasAudioFocus = false
        protected set
    var ignoringAudioFocus = false
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

class TestAudioFocusManager(): AudioFocusManager() {
    override fun requestAudioFocus(): Boolean {
        hasAudioFocus = true
        return true
    }
    override fun abandonAudioFocus() {
        hasAudioFocus = false
    }
}

class AndroidAudioFocusManager @Inject constructor(
    @ApplicationContext context: Context
): AudioFocusManager() {
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

    /** Request audio focus, and return whether the request was granted. */
    override fun requestAudioFocus() =
        if (ignoringAudioFocus)
            true
        else AudioManagerCompat.requestAudioFocus(
                androidAudioManager, audioFocusRequest
            ) == AUDIOFOCUS_REQUEST_GRANTED

    override fun abandonAudioFocus() {
        if (ignoringAudioFocus)
            return
        AudioManagerCompat.abandonAudioFocusRequest(androidAudioManager, audioFocusRequest)
        hasAudioFocus = false
    }
}

@Module @InstallIn(SingletonComponent::class)
class AudioFocusManagerModule {
    @Singleton @Provides
    fun provideAudioFocusManager(@ApplicationContext app: Context): AudioManager =
        app.getSystemService(AUDIO_SERVICE) as AudioManager
}
