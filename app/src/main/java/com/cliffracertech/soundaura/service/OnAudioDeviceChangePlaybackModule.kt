/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.service

import android.content.Context.AUDIO_SERVICE
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioManager.STREAM_MUSIC
import android.support.v4.media.session.PlaybackStateCompat.STATE_STOPPED
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.lifecycle.LifecycleService
import com.cliffracertech.soundaura.enumPreferenceFlow
import com.cliffracertech.soundaura.repeatWhenStarted
import com.cliffracertech.soundaura.settings.OnZeroVolumeAudioDeviceBehavior
import com.cliffracertech.soundaura.settings.PrefKeys
import com.cliffracertech.soundaura.settings.dataStore
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * OnAudioDeviceChangePlaybackModule will detect changes in audio devices that
 * result in a media volume of zero, and automatically affect playback in
 * response to this event according to the enum preference pointed to by
 * [PrefKeys.onZeroVolumeAudioDeviceBehavior].
 */
class OnAudioDeviceChangePlaybackModule(
    private val unpauseLocks: MutableSet<String>,
    private val autoPauseIf: (condition: Boolean, key: String) -> Unit,
    private val setPlaybackState: (playbackState: Int) -> Unit
) : PlayerService.PlaybackModule {
    private lateinit var audioManager: AudioManager
    private var firstAudioDeviceChangeDetected = false
    private var mediaVolumeIsZero = false

    private var onZeroVolumeAudioDeviceBehavior =
        OnZeroVolumeAudioDeviceBehavior.AutoStop
        set(value) {
            if (field == value) return
            if (field == OnZeroVolumeAudioDeviceBehavior.AutoPause)
            // Because the unpause lock is removed directly here instead
            // of through a call to autoPauseIf, playback will not resume
            // here if it was only paused due to a prior change to a zero
            // volume audio device. This in intended.
                unpauseLocks.remove(autoPauseAudioDeviceChangeKey)
            field = value
        }

    override fun onCreate(service: LifecycleService) {
        audioManager = service.getSystemService(AUDIO_SERVICE) as AudioManager
        audioManager.registerAudioDeviceCallback(audioDeviceChangeCallback, null)

        val key = intPreferencesKey(PrefKeys.onZeroVolumeAudioDeviceBehavior)

        service.repeatWhenStarted {
            service.dataStore.enumPreferenceFlow<OnZeroVolumeAudioDeviceBehavior>(key)
                .onEach { onZeroVolumeAudioDeviceBehavior = it }
                .launchIn(this)
        }
    }

    override fun onDestroy(service: LifecycleService) {
        audioManager.unregisterAudioDeviceCallback(audioDeviceChangeCallback)
    }

    private val audioDeviceChangeCallback = object: AudioDeviceCallback() {
        private fun onAudioDeviceChange() {
            // If the service is moved directly from stopped to playing (e.g. from
            // a tile press), then onAudioDeviceChange will be called for the first
            // time after playback has already started and result in the playback
            // being paused immediately if the volume is zero, even though it was
            // the result of a direct user action. We ignore the first audio device
            // change here to prevent this.
            if (!firstAudioDeviceChangeDetected) {
                firstAudioDeviceChangeDetected = true
                mediaVolumeIsZero = audioManager.getStreamVolume(STREAM_MUSIC) == 0
                return
            }

            val newMediaVolumeIsZero = audioManager.getStreamVolume(STREAM_MUSIC) == 0
            // Connecting true wireless bluetooth headsets can sometimes result in
            // multiple onAudioDevicesAdded calls. In this case the first call can
            // occur before the media volume is moved to its new state. Returning
            // early if the new media volume is zero when the last recorded media
            // volume was already zero prevents playback from being affected when
            // it shouldn't be.
            if (mediaVolumeIsZero == newMediaVolumeIsZero)
                return
            else mediaVolumeIsZero = newMediaVolumeIsZero

            when (onZeroVolumeAudioDeviceBehavior) {
                OnZeroVolumeAudioDeviceBehavior.AutoStop -> {
                    if (mediaVolumeIsZero)
                        setPlaybackState(STATE_STOPPED)
                } OnZeroVolumeAudioDeviceBehavior.AutoPause -> {
                    autoPauseIf(mediaVolumeIsZero, autoPauseAudioDeviceChangeKey)
                } OnZeroVolumeAudioDeviceBehavior.DoNothing -> {}
            }
        }

        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) =
            onAudioDeviceChange()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) =
            onAudioDeviceChange()
    }

    companion object {
        private const val autoPauseAudioDeviceChangeKey = "auto_pause_audio_device_change"
    }
}