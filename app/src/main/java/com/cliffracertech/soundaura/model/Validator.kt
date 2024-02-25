/*
 * This file is part of SoundAura, which is released under the terms of the Apache
 * License 2.0. See license.md in the project's root directory to see the full license.
 */
package com.cliffracertech.soundaura.model

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import com.cliffracertech.soundaura.model.Validator.Message
import com.cliffracertech.soundaura.model.Validator.Message.Error
import com.cliffracertech.soundaura.model.Validator.Message.Information
import com.cliffracertech.soundaura.model.Validator.Message.Warning
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A generic validator.
 *
 * Validator can be used to validate generic non-null values. When the [value]
 * property is changed, the [message] property will update after a delay to a
 * new [Message] concerning the value if necessary, or null if no [Message] is
 * required for the new value. Because [message] may not have had time to
 * update after a recent change to [value], and because [value] might change in
 * another thread after being validated, the suspend function [validate] should
 * always be called to ensure that a given value is valid. The current [value]
 * will be validated, and then either the validated value or null if the value
 * was invalid will be returned.
 *
 * @param initialValue The initial value for the [value] property
 * @param coroutineScope A [CoroutineScope] to run background work on
 * @param messageFor A method that will return a [Message] describing why the
 *     provided T value is invalid, or null if the value is valid. The second
 *     Boolean parameter indicates whether the value has been changed at least
 *     once. This can be useful when, e.g., an initial blank name is invalid,
 *     but the 'no blank names' error message should only be shown after the
 *     value has been changed at least once.
 */
class Validator <T>(
    initialValue: T,
    private val coroutineScope: CoroutineScope,
    private val messageFor: suspend (value: T, hasBeenChanged: Boolean) -> Message?,
) {
    // Meta-note: This class was designed as a final class with messageFor
    // being passed as a functional parameter in the constructor instead of
    // as an abstract class with messageFor being an abstract method so that
    // would-be subclasses can access whatever captured values they want in
    // their passed messageFor value. If messageFor were an abstract method,
    // would-be subclasses accessing their members in their messageFor
    // override could cause NullPointerExceptions due to their members not
    // yet being initialized.
    var message by mutableStateOf<Message?>(null)
        private set
    private var updateMessageJob: Job? = coroutineScope.launch {
        message = messageFor(initialValue, false)
        updateMessageJob = null
    }

    private var _value by mutableStateOf(initialValue)
    var value get() = _value
        set(value) {
            _value = value
            updateMessageJob?.cancel()
            updateMessageJob = coroutineScope.launch {
                message = messageFor(value, true)
                updateMessageJob = null
            }
        }

    suspend fun validate(): T? {
        val value = this.value
        val isValid = messageFor(value, true)?.isError != true
        // If the value is invalid when there is no error message and
        // no error update job, validate must have been called with an
        // unchanged invalid initial value. In this case we will run
        // the custom setter so that an update message job will be ran
        // and an appropriate error message will be shown.
        if (!isValid && updateMessageJob == null && message == null)
            this.value = value
        return if (isValid) value else null
    }

    /** Message's subclasses [Information], [Warning], and [Error] provide
     * information about a proposed value for a value being validated. */
    sealed class Message(val stringResource: StringResource) {
        /** An informational message that does not indicate
         * that there is a problem with the proposed value. */
        class Information(stringResource: StringResource): Message(stringResource) {
            constructor(@StringRes stringResId: Int) : this(StringResource(stringResId))
        }
        /** A message that warns about a potential problem with the
         * proposed value. It is left up to the user to heed or ignore. */
        class Warning(stringResource: StringResource): Message(stringResource) {
            constructor(@StringRes stringResId: Int) : this(StringResource(stringResId))
        }
        /** A message that describes a critical error with the proposed
         * value that requires the value to be changed before proceeding. */
        class Error(stringResource: StringResource): Message(stringResource) {
            constructor(@StringRes stringResId: Int) : this(StringResource(stringResId))
        }

        val isInformational get() = this is Information
        val isWarning get() = this is Warning
        val isError get() = this is Error
    }
}

/**
 * A validator for a list of values, all of which must be individually valid.
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
 * [isInvalid] override returns true for the new value.
 *
 * The property [message] can be observed for to obtain a [Validator.Message]
 * instance that describes why one or more values in the list is invalid, or
 * null if all values are valid. Once all desired changes to the list of values
 * has been performed, the suspend function [validate] should be called.
 *
 * The method [isInvalid] should be overridden to return whether or not a given
 * value is invalid at the provided index. Note that ListValidator will already
 * check for and mark duplicate values as errors if [allowDuplicates] is false,
 * so the [isInvalid] override does not need to take duplicates into account.
 * The property [errorMessage] should be overridden to be a [Validator.Message.Error]
 * that describes why the list of current values is invalid.
 */
abstract class ListValidator <T>(
    values: List<T>,
    private val coroutineScope: CoroutineScope,
    private val allowDuplicates: Boolean = true,
) {
    private val _values = values.toMutableStateList()
    val values get() = _values as List<T>

    private var errorCount by mutableIntStateOf(0)
    private val _errors = List(values.size) { false }.toMutableStateList()
    val errors get() = _errors as List<Boolean>

    private val mutex = Mutex(false)

    init {
        coroutineScope.launch {
            mutex.withLock {
                for (i in _errors.indices) {
                    // If there is already an error at an index, then
                    // it must be a duplicate of an earlier value.
                    if (_errors[i]) continue

                    val hasOtherError = isInvalid(values[i])
                    val hasDuplicateError =
                        if (allowDuplicates || i == _errors.lastIndex)
                            false
                        else run {
                            var duplicateCount = 0
                            for (j in i + 1..values.lastIndex)
                                if (values[j] == values[i]) {
                                    duplicateCount++
                                    _errors[j] = true
                                }
                            duplicateCount > 0
                        }
                    if (hasDuplicateError || hasOtherError)
                        _errors[i] = true
                }
                errorCount = _errors.count { it }
            }
        }
    }

    protected abstract fun isInvalid(value: T): Boolean
    protected abstract val errorMessage: Validator.Message.Error

    fun setValue(index: Int, newValue: T) {
        if (index !in values.indices) return
        val oldValue = values[index]
        if (oldValue == newValue) return

        coroutineScope.launch {
            mutex.withLock {
                _values[index] = newValue

                val hadError = _errors[index]
                val hasError = isInvalid(newValue)
                if (hasError && !hadError)      errorCount++
                else if (hadError && !hasError) errorCount--
                _errors[index] = hasError

                if (allowDuplicates) return@withLock
                values.forEachIndexed { i, value -> when {
                    value == newValue && i != index -> {
                        // If there is a duplicate we set the value being
                        // set and the duplicate value as having an error
                        if (!_errors[i])     errorCount++
                        if (!_errors[index]) errorCount++
                        _errors[i] = true
                        _errors[index] = true
                    } value == oldValue -> {
                        // We have to recheck all values that are equal to oldValue
                        // in case they were marked as having errors only because
                        // they were duplicate values (which will not be the case
                        // now that values[index] has been changed).
                        val hasDuplicateError = isInvalid(values[i])
                        val hadDuplicateError = _errors[i]
                        if (hasDuplicateError && !hadDuplicateError)      errorCount++
                        else if (hadDuplicateError && !hasDuplicateError) errorCount--
                        _errors[i] = hasDuplicateError
                    }
                }}
            }
        }
    }

    /** Force a recheck of all values. This can be useful if the
     * conditions that are checked via [isInvalid] change. */
    protected fun recheck() {
        coroutineScope.launch {
            mutex.withLock {
                for (i in _values.indices) {
                    val hadError = _errors[i]
                    val hasError = isInvalid(_values[i])
                    if (hasError == hadError) continue

                    if (hasError) errorCount++
                    else          errorCount--
                    _errors[i] = hasError
                }
                // Since the values are being rechecked against possibly changed
                // conditions via isInvalid, but aren't actually being changed,
                // we do not need to check for duplicates again.
            }
        }
    }

    val message get() = if (errorCount > 0) errorMessage
                        else                null

    /** Return the validated list of values if they are all valid, or null if
     * one or more values are invalid. The default implementation only checks
     * for duplicate values if [allowDuplicates] is false, and can be used
     * as an else branch in an override if no other errors are detected. */
    open suspend fun validate(): List<T>? {
        val isValid = allowDuplicates ||
            values.toSet().size == values.size
        return if (isValid) values
               else         null
    }
}