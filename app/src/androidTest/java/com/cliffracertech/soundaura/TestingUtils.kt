/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.util.Log
import com.google.common.truth.Subject
import kotlin.reflect.KClass

suspend fun waitUntil(
    timeOut: Long = 1000L,
    condition: suspend () -> Boolean,
) {
    val start = System.currentTimeMillis()
    while (!condition()) {
        if (System.currentTimeMillis() - start >= timeOut) {
            Log.d("SoundAuraTag", "waitUntil timed out after $timeOut milliseconds")
            return
        }
        Thread.sleep(50L)
    }
}

fun <T: Any> Subject.isInstanceOf(clazz: KClass<T>) = isInstanceOf(clazz.java)