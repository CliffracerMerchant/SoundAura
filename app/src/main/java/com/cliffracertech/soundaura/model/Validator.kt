/*
 * This file is part of SoundAura, which is released under the terms of the Apache
 * License 2.0. See license.md in the project's root directory to see the full license.
 */
package com.cliffracertech.soundaura.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.toMutableStateList
import com.cliffracertech.soundaura.collectAsState
import com.cliffracertech.soundaura.model.Validator.Message
import com.cliffracertech.soundaura.model.Validator.Message.Error
import com.cliffracertech.soundaura.model.Validator.Message.Information
import com.cliffracertech.soundaura.model.Validator.Message.Warning
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest

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
 * a [Message] that explains why the current value is not valid, or null if the
 * name is valid. The property [message] can be used to access the message for
 * the most recent [value].
 *
 * Because [message] may not have had time to update after a recent change to
 * [value], and because [value] might change in another thread after being
 * validated, the suspend function [validate] should always be called to ensure
 * that a given value is valid. The current [value] will be validated, and then
 * either the validated value or null if the value was invalid will be returned.
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

    val message by flow.mapLatest(::messageFor).collectAsState(null, coroutineScope)

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
 * A validator for a list of values, all of which must be valid.
 *
 * ListValidator can be used to validate a list of generic non-null values and
 * return a message explaining why one or more of the values is not valid if
 * necessary. The property [values] will initially be equal to the constructor
 * provided [values]. The [errors] property is a same-sized list of [Boolean]
 * values that indicates whether each corresponding [values] element is valid.
 * The property [allowDuplicates] can be used to mark all duplicate values as
 * being errors.
 *
 * [setValue] should be called to change the proposed value for a particular
 * item, and will also mark the value at that index as having an error if the
 * [hasError] override returns true for the new value.
 *
 * The property [message] can be observed for to obtain a [Validator.Message]
 * instance that describes why one or more values in the list is invalid, or
 * null if all values are valid. Once all desired changes to the list of values
 * has been performed, the suspend function [validate] should be called.
 *
 * The method [hasError] should be overridden to return whether or not a given
 * value is invalid at the provided index. Note that ListValidator will already
 * check for and mark duplicate values as errors if [allowDuplicates] is false,
 * so the [hasError] override does not need to take duplicates into account.
 * The property [errorMessage] should be overridden to be a [Validator.Message.Error]
 * that describes why the list of current values is invalid.
 */
abstract class ListValidator <T>(
    values: List<T>,
    scope: CoroutineScope,
    private val allowDuplicates: Boolean = true,
) {
    private val _values = values.toMutableStateList()
    private val _errors = List(values.size) { false}.toMutableStateList()

    val values get() = _values as List<T>
    val errors get() = _errors as List<Boolean>

    fun setValue(index: Int, value: T) {
        if (index !in _values.indices) return
        val oldValue = _values[index]
        val hadError = _errors[index]

        _values[index] = value
        _errors[index] = hasError(value)
        if (allowDuplicates)
            return

        for (i in values.indices) when {
            values[i] == value && i != index -> {
                // If there is a duplicate and allowDuplicates is false, we set
                // the value being set and the duplicate value as having an error
                _errors[i] = true
                _errors[index] = true
            } hadError && values[i] == oldValue ->
                // We have to recheck all values that are equal to oldValue
                // in case they were marked as having errors only because they were
                // duplicate values (which will not be the case now that values[index]
                // has been changed).
                _errors[i] = hasError(_values[i])
        }
    }

    protected abstract fun hasError(value: T): Boolean
    protected abstract val errorMessage: Validator.Message.Error

    val message by snapshotFlow {
            _errors.find { it } != null
        }.map { hasError ->
            if (hasError) errorMessage
            else          null
        }.collectAsState(null, scope)

    /** Return the validated list of values if they are all valid, or null if
     * one or more values are invalid. The default implementation only checks
     * for duplicate values if [allowDuplicates] is false, and can be used
     * as an else branch in an override if no other errors are detected. */
    open suspend fun validate(): List<T>? {
        val isValid = allowDuplicates ||
            _values.toSet().size < values.size
        return if (isValid) values
               else         null
    }
}