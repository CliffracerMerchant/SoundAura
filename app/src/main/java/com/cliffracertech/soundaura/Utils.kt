/* This file is part of SoundAura, which is released under the terms of the Apache
 * License 2.0. See license.md in the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlin.reflect.KProperty

operator fun <T> State<T>.getValue(receiver: Any, property: KProperty<*>) = value
operator fun <T> MutableState<T>.setValue(receiver: Any, property: KProperty<*>, value: T) {
    this.value = value
}

operator fun <T> StateFlow<T>.getValue(receiver: Any, property: KProperty<*>) = value
operator fun <T> MutableStateFlow<T>.setValue(receiver: Any, property: KProperty<*>, value: T) {
    this.value = value
}


/** Return a State<T> that contains the most recent value for the DataStore
 * preference pointed to by @param key, with an initial value of @param
 * initialValue. */
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

/** Return a State<T> that contains the most recent value for the DataStore
 * preference pointed to by @param key, with a default value of @param
 * defaultValue. awaitPreferenceState will suspend until the first value
 * of the preference is returned. The provided default value will only be
 * used if the receiver DataStore does not have a value associated with
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

/** Return a State<T> that contains the most recent enum value for the
 * DataStore preference pointed to by the parameter key, with an initial value
 * of parameter initialValue. The parameter key is a Preferences.Key<Int>
 * instance whose value indicates the ordinal of the current enum value. */
inline fun <reified T: Enum<*>> DataStore<Preferences>.enumPreferenceState(
    key: Preferences.Key<Int>,
    scope: CoroutineScope,
    initialValue: T = enumValues<T>()[0],
): State<T> {
    val indexState = preferenceState(key, initialValue.ordinal, scope)
    val values = enumValues<T>()
    return derivedStateOf {
        values.getOrNull(indexState.value) ?: initialValue
    }
}

/** Return a State<T> that contains the most recent enum value for the
 * DataStore preference pointed to by the parameter key, with a default value
 * of parameter defaultValue. The parameter key is a Preferences.Key<Int>
 * instance whose value indicates the ordinal of the current enum value.
 * awaitEnumPreferenceState will suspend until the first value of the enum
 * is read from the receiver DataStore object. The provided default value will
 * only be used if the receiver DataStore does not have a value associated with
 * the provided key. */
suspend inline fun <reified T: Enum<*>> DataStore<Preferences>.awaitEnumPreferenceState(
    key: Preferences.Key<Int>,
    scope: CoroutineScope,
    defaultValue: T = enumValues<T>()[0],
): State<T> {
    val indexState = awaitPreferenceState(key, defaultValue.ordinal, scope)
    val values = enumValues<T>()
    return derivedStateOf {
        values.getOrNull(indexState.value) ?: defaultValue
    }
}

/** Return a Flow<T> that contains the most recent value for the DataStore
 * preference pointed to by @param key, with a default value of @param
 * defaultValue. */
fun <T> DataStore<Preferences>.preferenceFlow(
    key: Preferences.Key<T>,
    defaultValue: T,
) = data.map { it[key] ?: defaultValue }

/** Return a Flow<T> that contains the most recent enum value for the DataStore
 * preference pointed to by @param key, with a default value of @param
 * defaultValue. @param key should be an Preferences.Key<Int> instance whose
 * value indicates the ordinal of the current enum value. */
inline fun <reified T: Enum<*>> DataStore<Preferences>.enumPreferenceFlow(
    key: Preferences.Key<Int>,
    defaultValue: T = enumValues<T>()[0],
) = data.map { prefs ->
    val index = prefs[key] ?: defaultValue.ordinal
    enumValues<T>().getOrNull(index) ?: defaultValue
}

