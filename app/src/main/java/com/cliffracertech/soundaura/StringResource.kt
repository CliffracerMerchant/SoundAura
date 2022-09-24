/* This file is part of SoundAura, which is released under the terms of the Apache
 * License 2.0. See license.md in the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.content.Context
import androidx.annotation.StringRes

/** A holder of a string resource, which can be resolved to a
 * [String] by calling the method [resolve] with a [Context]. */
class StringResource(
    private val string: String?,
    @StringRes val stringResId: Int = 0,
    private val args: ArrayList<Any>?
) {
    data class Id(@StringRes val id: Int)

    constructor(string: String): this(string, 0, null)
    constructor(@StringRes stringResId: Int): this(null, stringResId, null)
    constructor(@StringRes stringResId: Int, stringVar: String):
            this(null, stringResId, arrayListOf(stringVar))
    constructor(@StringRes stringResId: Int, intVar: Int):
            this(null, stringResId, arrayListOf(intVar))
    constructor(@StringRes stringResId: Int, stringVarId: Id):
            this(null, stringResId, arrayListOf(stringVarId))

    fun resolve(context: Context?) = string ?: when {
        context == null -> ""
        args == null -> context.getString(stringResId)
        else -> {
            for (i in args.indices) {
                val it = args[i]
                if (it is Id)
                    args[i] = context.getString(it.id)
            }
            context.getString(stringResId, *args.toArray())
        }
    }
}