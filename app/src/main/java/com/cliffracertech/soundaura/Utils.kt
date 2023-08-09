/* This file is part of SoundAura, which is released under the terms of the Apache
 * License 2.0. See license.md in the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.view.ViewTreeObserver
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.reflect.KProperty

operator fun <T> StateFlow<T>.getValue(receiver: Any, property: KProperty<*>) = value
operator fun <T> MutableStateFlow<T>.setValue(receiver: Any, property: KProperty<*>, value: T) {
    this.value = value
}

/** Repeat [onStarted] each time the [LifecycleOwner]'s state moves to [Lifecycle.State.STARTED]. */
fun LifecycleOwner.repeatWhenStarted(onStarted: suspend CoroutineScope.() -> Unit) {
    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED, onStarted)
    }
}

/** Return a State<T> object that is updated with the latest values of the receiver flow,
 * with an initial value of [initialValue]. [scope] is used for the collection of the flow. */
fun <T> Flow<T>.collectAsState(initialValue: T, scope: CoroutineScope): State<T> {
    val state = mutableStateOf(initialValue)
    onEach { state.value = it }.launchIn(scope)
    return state
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

/** Edit the DataStore preference pointed to by [key] to the new [value]. */
suspend fun <T> DataStore<Preferences>.edit(
    key: Preferences.Key<T>,
    value: T,
) {
    edit { it[key] = value }
}

/** Edit the DataStore preference pointed to by [key] to the new [value] in [scope]. */
fun <T> DataStore<Preferences>.edit(
    key: Preferences.Key<T>,
    value: T,
    scope: CoroutineScope
) {
    scope.launch { edit(key, value) }
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
    val values = enumValues<T>()
    val state = mutableStateOf(initialValue)
    data.map { it[key] ?: initialValue.ordinal }
        .onEach { state.value = values.getOrElse(it) { initialValue } }
        .launchIn(scope)
    return state
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
    val values = enumValues<T>()
    val initialValue = values[data.first()[key] ?: defaultValue.ordinal]
    val state = mutableStateOf(initialValue)
    data.map { it[key] ?: defaultValue.ordinal }
        .onEach { state.value = values.getOrElse(it) { defaultValue } }
        .launchIn(scope)
    return state
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

/**
 * Restrict the horizontal width as a percentage of the screen width according
 * to the [LocalWindowSizeClass] value. When the [WindowWidthSizeClass] is
 * equal to [WindowWidthSizeClass.Compact], there will be no width restriction;
 * with [WindowWidthSizeClass.Medium] the width restriction is equal to 80% of
 * the screen width; with [WindowWidthSizeClass.Expanded] the width restriction
 * is equal to 60% of the screen width. This modifier can be used to prevent
 * top level UI elements that don't need to be very wide from becoming too
 * stretched out in configurations with a large [WindowWidthSizeClass]. The
 * parameter [compactPadding] allows the specification of a minimum amount of
 * horizontal padding that will only be added when the [WindowWidthSizeClass]
 * is equal to [WindowWidthSizeClass.Compact].
 */
fun Modifier.restrictWidthAccordingToSizeClass(
    compactPadding: Dp = 16.dp
) = composed {
    val config = LocalConfiguration.current
    val widthSizeClass = LocalWindowSizeClass.current.widthSizeClass
    val modifier = remember(config, widthSizeClass) {
        if (widthSizeClass == WindowWidthSizeClass.Compact)
            Modifier.padding(horizontal = compactPadding)
        else {
            val widthDp = config.screenWidthDp.dp
            val maxWidth = when (widthSizeClass) {
                WindowWidthSizeClass.Medium ->   widthDp * 0.8f
                WindowWidthSizeClass.Expanded -> widthDp * 0.6f
                else ->                          widthDp
            }
            Modifier.widthIn(max = maxWidth)
        }
    }
    this.then(modifier)
}

@Composable fun <T> rememberMutableStateOf(value: T) = remember { mutableStateOf(value) }
@Composable fun rememberMutableIntStateOf(value: Int) = remember { mutableIntStateOf(value) }
@Composable fun rememberMutableFloatStateOf(value: Float) = remember { mutableFloatStateOf(value) }
@Composable fun <T> rememberDerivedStateOf(calculation: () -> T) = remember { derivedStateOf(calculation) }

/** Returns a [State]`<Boolean>` that indicates whether or not the soft keyboard is open. */
@Composable fun imeIsOpen(): State<Boolean> {
    val imeIsOpen = rememberMutableStateOf(false)
    val view = LocalView.current
    DisposableEffect(view) {
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            imeIsOpen.value = ViewCompat.getRootWindowInsets(view)
                ?.isVisible(WindowInsetsCompat.Type.ime()) ?: true
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose { view.viewTreeObserver.removeOnGlobalLayoutListener(listener) }
    }
    return imeIsOpen
}

/** Return a [PaddingValues] created from adding [additionalStart], [additionalTop],
 * [additionalEnd], and [additionalBottom] to the [original] [PaddingValues] instance. */
fun PaddingValues(
    original: PaddingValues,
    layoutDirection: LayoutDirection,
    additionalStart: Dp = 0.dp,
    additionalTop: Dp = 0.dp,
    additionalEnd: Dp = 0.dp,
    additionalBottom: Dp = 0.dp,
): PaddingValues {
    return PaddingValues(
        start = original.calculateStartPadding(layoutDirection) + additionalStart,
        top = original.calculateTopPadding() + additionalTop,
        end = original.calculateEndPadding(layoutDirection) + additionalEnd,
        bottom = original.calculateBottomPadding() + additionalBottom)
}