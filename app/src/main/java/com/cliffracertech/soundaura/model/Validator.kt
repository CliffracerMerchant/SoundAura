/*
 * This file is part of SoundAura, which is released under the terms of the Apache
 * License 2.0. See license.md in the project's root directory to see the full license.
 */
package com.cliffracertech.soundaura.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshotFlow
import com.cliffracertech.soundaura.collectAsState
import com.cliffracertech.soundaura.model.Validator.Message
import com.cliffracertech.soundaura.model.Validator.Message.Error
import com.cliffracertech.soundaura.model.Validator.Message.Information
import com.cliffracertech.soundaura.model.Validator.Message.Warning
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * An abstract value validator.
 *
 * Validator can be used to validate generic non-null values and return
 * messages explaining why the value is not valid if necessary. The initial
 * value of the mutable property [value] will be equal to the provided
 * [initialValue]. The property [valueHasBeenChanged] is visible to sub-classes,
 * and can be used in case different error messages are desired before or after
 * [value] has been changed at least once. For example, a [String] property
 * would mostly likely be initialized to a blank [String]. If blank [String]s
 * are invalid, [valueHasBeenChanged] can be utilized to only show a 'value
 * must not be blank' error message after it has been changed at least once.
 * This would prevent the message from being immediately displayed before the
 * user has had a chance to change the value.
 *
 * The suspend function [messageFor] must be overridden in subclasses to return
 * a [StringResource] that becomes the message explaining why the current value
 * is not valid when resolved, or null if the name is valid. The property
 * [message] can be used to access the message for the most recent [value].
 *
 * Because [message] may not have had time to update after a recent change to
 * [value], and because [value] might change in another thread after being
 * validated, the suspend function [validate] should always be called to ensure
 * that a given value is valid. The current [value] will be validated, and then
 * either the validated value or null if the value was invalid will be returned.
 * If the validator needs to be reused after a successful validation, calling
 * [reset] with a new initial value will reset [valueHasBeenChanged].
 */
abstract class Validator <T>(initialValue: T, coroutineScope: CoroutineScope) {
    private val flow = MutableStateFlow(initialValue)
    private val _value by flow.collectAsState(initialValue, coroutineScope)
    var value get() = _value
              set(value) {
                  flow.value = value
                  valueHasBeenChanged = true
              }
    protected var valueHasBeenChanged = false
        private set

    fun reset(newInitialValue: T) {
        valueHasBeenChanged = false
        flow.value = newInitialValue
    }

    /** Message's subclasses [Information], [Warning], and [Error] provide
     * information about a proposed value for a value being validated. */
    sealed class Message(val stringResource: StringResource) {
        /** An informational message that does not indicate
         * that there is a problem with the proposed value. */
        class Information(stringResource: StringResource): Message(stringResource)
        /** A message that warns about a potential problem with the
         * proposed value. It is left up to the user to heed or ignore. */
        class Warning(stringResource: StringResource): Message(stringResource)
        /** A message that describes a critical error with the proposed
         * value that requires the value to be changed before proceeding. */
        class Error(stringResource: StringResource): Message(stringResource)

        val isInformational get() = this is Information
        val isWarning get() = this is Warning
        val isError get() = this is Error
    }

    protected abstract suspend fun messageFor(value: T): Message?

    val message by flow.map(::messageFor).collectAsState(null, coroutineScope)

    suspend fun validate(): T? {
        val value = this.value
        // Although value might not have been changed yet, we set
        // valueHasBeenChanged to true so that subclasses that don't
        // provide an error message for an invalid initial value do
        // provide an error message here
        valueHasBeenChanged = true
        val isValid = messageFor(value)?.isError != true
        return if (isValid) value else null
    }
}

/**
 * A validator for a list of values, all of which must individually be valid.
 *
 * ListValidator can be used to validate a list of generic non-null values and
 * return a message explaining why one or more of the values is not valid if
 * necessary. The initial value of the property [values] will be equal to a
 * list containing each value in the provided [items] paired with false. This
 * boolean paired value indicates whether that individual value is invalid.
 * [setValue] should be called to change the proposed value for a particular
 * item. This will also update its paired boolean value indicating whether that
 * value is valid.
 *
 * The property [message] can be observed for to obtain a [Validator.Message]
 * instance that describes why one or more values in the list is invalid, or
 * null if all values are valid. Once all desired changes to the list of values
 * has been performed, the suspend function [validate] should be called.
 *
 * The method [isValid] should be overridden to return whether or not a given
 * value is invalid. Likewise, the suspend function [messageFor] should be
 * overridden to return a [Validator.Message] that describes why the list of
 * current values is invalid, or null if the values are valid.
 */
abstract class ListValidator <T>(
    items: List<T>,
    scope: CoroutineScope,
) {
    private val _values = mutableStateListOf(
        *Array(items.size) { items[it] to false })
    val values = _values as List<Pair<T, Boolean>>

    protected abstract fun isValid(value: T): Boolean

    fun setValue(index: Int, value: T) {
        if (index in values.indices)
            _values[index] = value to isValid(value)
    }

    protected abstract suspend fun messageFor(values: List<Pair<T, Boolean>>): Message?

    val message by snapshotFlow { _values }
        .map(::messageFor).collectAsState(null, scope)

    /** Return the validated list of values if they are all
     * valid, or null if one or more values are invalid. */
    abstract suspend fun validate(): List<T>?
}