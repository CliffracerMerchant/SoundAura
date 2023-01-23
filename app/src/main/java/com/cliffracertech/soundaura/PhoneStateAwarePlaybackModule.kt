/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.lifecycle.LifecycleService
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** Run the provided block after a clearCallingIdentity
 * call and before a restoreCallingIdentity call. */
fun withClearCallingIdentity(block: () -> Unit) {
    val id = android.os.Binder.clearCallingIdentity()
    block()
    android.os.Binder.restoreCallingIdentity(id)
}

/**
 * PhoneStateAwarePlaybackModule will, in the event of both boolean preferences
 * pointed to by the keys [PrefKeys.playInBackground] and [PrefKeys.autoPauseDuringCalls]
 * being true, automatically pause playback during a phone call and automatically
 * unpause when the call ends. This behavior only occurs when the playInBackground
 * preference is true because it assumes that the app will obey audio focus rules
 * if the playInBackground preference is false (and therefore lose audio focus
 * during phone calls
 */
class PhoneStateAwarePlaybackModule(
    private val autoPauseIf: (condition: Boolean, key: String) -> Unit,
) : PlayerService.PlaybackModule {

    private var phoneStateListener: PhoneStateListener? = null
    private var telephonyCallback: TelephonyCallback? = null
    private lateinit var telephonyManager: TelephonyManager

    override fun onCreate(service: LifecycleService) {
        telephonyManager = service.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val playInBackgroundKey = booleanPreferencesKey(PrefKeys.playInBackground)
        val playInBackgroundFlow = service.dataStore.preferenceFlow(playInBackgroundKey, false)
        val autoPauseDuringCallsKey = booleanPreferencesKey(PrefKeys.autoPauseDuringCalls)
        val autoPauseDuringCallsFlow = service.dataStore.preferenceFlow(autoPauseDuringCallsKey, false)

        service.repeatWhenStarted {
            // We want setAutoPauseDuringCallEnabled(true) to be called only
            // if the preference is true AND playInBackground is true. If
            // playInBackground is false, the phone will be paused anyways
            // due to the app losing audio focus during calls.
            autoPauseDuringCallsFlow
                .combine(playInBackgroundFlow) { pauseDuringCalls, playInBackground ->
                    pauseDuringCalls && playInBackground
                }.distinctUntilChanged()
                .onEach { setAutoPauseDuringCallEnabled(service, it) }
                .launchIn(this)
        }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    private fun setAutoPauseDuringCallEnabled(context: Context, enabled: Boolean) {
        val hasReadPhoneState = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        if (!enabled || !hasReadPhoneState) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyCallback?.let(telephonyManager::unregisterTelephonyCallback)
                telephonyCallback = null
            } else {
                phoneStateListener?.let {
                    telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE)
                }
                phoneStateListener = null
            }
            autoPauseIf(false, autoPauseOngoingCallKey)
        }
        else withClearCallingIdentity {
            val onCallStateChange = { state: Int ->
                autoPauseIf(state == TelephonyManager.CALL_STATE_RINGING ||
                                state == TelephonyManager.CALL_STATE_OFFHOOK,
                            autoPauseOngoingCallKey)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val listener = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) = onCallStateChange(state)
                }
                telephonyManager.registerTelephonyCallback(context.mainExecutor, listener)
            } else {
                val listener = object : PhoneStateListener() {
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) =
                        onCallStateChange(state)
                }
                val state = PhoneStateListener.LISTEN_CALL_STATE
                telephonyManager.listen(listener, state)
            }
        }
    }

    companion object {
        private const val autoPauseOngoingCallKey = "auto_pause_ongoing_call"
    }
}