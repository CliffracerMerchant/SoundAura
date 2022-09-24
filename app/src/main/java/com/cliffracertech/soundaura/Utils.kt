/* This file is part of SoundAura, which is released under the terms of the Apache
 * License 2.0. See license.md in the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.support.v4.media.session.PlaybackStateCompat
import androidx.compose.runtime.*
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.reflect.KProperty

operator fun <T> StateFlow<T>.getValue(receiver: Any, property: KProperty<*>) = value
operator fun <T> MutableStateFlow<T>.setValue(receiver: Any, property: KProperty<*>, value: T) {
    this.value = value
}

/** Produce a [State]`<T>` instance from the receiver T? instance. When the
 * receiver is null, the [State]`<T>` value will be equal to [defaultValue]. */
@Composable fun <T> T?.mapToNonNullState(defaultValue: T) =
    produceState(initialValue = defaultValue, key1 = this) {
        value = this@mapToNonNullState ?: defaultValue
    }

/** Repeat [onStarted] each time the [LifecycleOwner]'s state moves to [Lifecycle.State.STARTED]. */
fun LifecycleOwner.repeatWhenStarted(onStarted: suspend CoroutineScope.() -> Unit) {
    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED, onStarted)
    }
}

/** Return a [State]`<T>` that contains the most recent value for the [DataStore]
 * preference pointed to by [key], with an initial value of [initialValue]. */
fun <T> DataStore<Preferences>.preferenceState(
    key: Preferences.Key<T>,
    initialValue: T,
    scope: CoroutineScope,
) : State<T> {
    val state = mutableStateOf(initialValue)
    data.map { it[key] ?: initialValue }
        .onEach { state.value = it }
        .launchIn(scope)
    return state
}

/** Return a [State]`<T>` that contains the most recent value for the
 * [DataStore] preference pointed to by [key], with a default value of
 * [defaultValue]. awaitPreferenceState will suspend until the first value
 * of the preference is returned. The provided default value will only be
 * used if the receiver [DataStore] does not have a value associated with
 * the provided key. */
suspend fun <T> DataStore<Preferences>.awaitPreferenceState(
    key: Preferences.Key<T>,
    defaultValue: T,
    scope: CoroutineScope,
) : State<T> {
    val flow = data.map { it[key] ?: defaultValue }
    val state = mutableStateOf(flow.first())
    flow.onEach { state.value = it }.launchIn(scope)
    return state
}

/** Return a [State]`<T>` that contains the most recent enum value for the
 * [DataStore] preference pointed to by [key], with an initial value
 * of [initialValue]. [key] is a [Preferences.Key]`<Int>` instance whose
 * value indicates the ordinal of the current enum value. */
inline fun <reified T: Enum<T>> DataStore<Preferences>.enumPreferenceState(
    key: Preferences.Key<Int>,
    scope: CoroutineScope,
    initialValue: T = enumValues<T>()[0],
): State<T> {
    val indexState = preferenceState(key, initialValue.ordinal, scope)
    val values = enumValues<T>()
    return derivedStateOf {
        values.getOrElse(indexState.value) { initialValue }
    }
}

/** Return a [State]`<T>` that contains the most recent enum value for the
 * [DataStore] preference pointed to by the parameter [key], with a default
 * value of [defaultValue]. [key] is a [Preferences.Key]`<Int>` instance
 * whose value indicates the ordinal of the current enum value.
 * awaitEnumPreferenceState will suspend until the first value of the enum
 * is read from the receiver [DataStore] object. The provided default value
 * will only be used if the receiver [DataStore] does not have a value
 * associated with the provided key. */
suspend inline fun <reified T: Enum<T>> DataStore<Preferences>.awaitEnumPreferenceState(
    key: Preferences.Key<Int>,
    scope: CoroutineScope,
    defaultValue: T = enumValues<T>()[0],
): State<T> {
    val indexState = awaitPreferenceState(key, defaultValue.ordinal, scope)
    val values = enumValues<T>()
    return derivedStateOf {
        values.getOrElse(indexState.value) { defaultValue }
    }
}

/** Return a [Flow]`<T>` that contains the most recent value for the [DataStore]
 * preference pointed to by [key], with a default value of [defaultValue]. */
fun <T> DataStore<Preferences>.preferenceFlow(
    key: Preferences.Key<T>,
    defaultValue: T,
) = data.map { it[key] ?: defaultValue }

/** Return a [Flow]`<T>` that contains the most recent enum value for the [DataStore]
 * preference pointed to by [key], with a default value of [defaultValue]. [key]
 * should be an [Preferences.Key]`<Int>` instance whose value indicates the ordinal
 * of the current enum value. */
inline fun <reified T: Enum<T>> DataStore<Preferences>.enumPreferenceFlow(
    key: Preferences.Key<Int>,
    defaultValue: T = enumValues<T>()[0],
) = data.map { prefs ->
    val index = prefs[key] ?: defaultValue.ordinal
    enumValues<T>().getOrElse(index) { defaultValue }
}

fun Int.toPlaybackStateString() = when (this) {
    PlaybackStateCompat.STATE_STOPPED -> "stopped"
    PlaybackStateCompat.STATE_PLAYING -> "playing"
    PlaybackStateCompat.STATE_PAUSED -> "paused"
    else -> "unsupported state (int value = $this)"
}