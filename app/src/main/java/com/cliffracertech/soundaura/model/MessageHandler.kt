/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.model

import android.content.Context
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import com.cliffracertech.soundaura.R
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A manager of messages to be displayed to the user, e.g. through a [Snackbar].
 *
 * New messages can be posted using the [postMessage] function. MessageHandler
 * users can collect the [SharedFlow] member [messages] for new messages.
 */
@Singleton
class MessageHandler @Inject constructor() {
    /**
     * A message to be displayed to the user. The convenience method
     * [showAsSnackbar] can be used to show the message as a snackbar.
     *
     * @param stringResource A [StringResource] that represents the text of the message
     * @param actionStringResource A nullable [StringResource] that represents
     *                             the text of the message action, if any
     * @param duration The suggested [SnackbarDuration] for the message
     * @param onActionClick The callback that will be invoked if the message action is clicked
     * @param onDismiss The callback that will be invoked when the message
     *     is dismissed. The int parameter will be equal to a value of
     *     [BaseTransientBottomBar.BaseCallback.DismissEvent].
     */
    data class Message(
        val stringResource: StringResource,
        val actionStringResource: StringResource? = null,
        val duration: SnackbarDuration = SnackbarDuration.Short,
        val onActionClick: (() -> Unit)? = null,
        val onDismiss: ((Int) -> Unit)? = null
    ) {
        /** Show the message to the user in the form of a snackbar, using
         * the provided [Context] and [SnackbarHostState] instances. The
         * [BaseTransientBottomBar.BaseCallback.DismissEvent] value provided
         * to the [onDismiss] callback will always be DISMISS_EVENT_SWIPE
         * when using this method. */
        suspend fun showAsSnackbar(
            context: Context,
            snackbarHostState: SnackbarHostState
        ) {
            val result = snackbarHostState.showSnackbar(
                message = stringResource.resolve(context),
                actionLabel = actionStringResource?.resolve(context)
                    ?: context.getString(R.string.dismiss).uppercase(),
                duration = duration)
            when (result) {
                SnackbarResult.ActionPerformed -> onActionClick?.invoke()
                // SnackBarHostState does not allow us to know the type of dismiss,
                // so we'll use DISMISS_EVENT_SWIPE (the first value) for everything.
                SnackbarResult.Dismissed -> onDismiss?.invoke(
                    BaseTransientBottomBar.BaseCallback.DISMISS_EVENT_SWIPE)
                else -> {}
            }
        }
    }

    private val _messages = MutableSharedFlow<Message>(
        extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val messages = _messages.asSharedFlow()

    /** Post the message described by the parameters to the message queue. */
    fun postMessage(
        stringResource: StringResource,
        duration: SnackbarDuration = SnackbarDuration.Short,
        actionStringResource: StringResource? = null,
        onActionClick: (() -> Unit)? = null,
        onDismiss: ((Int) -> Unit)? = null
    ) {
        val message = Message(stringResource, actionStringResource,
                              duration, onActionClick, onDismiss)
        _messages.tryEmit(message)
    }

    /** Post the message described by the parameters to the message queue. */
    fun postMessage(
        stringResId: Int,
        duration: SnackbarDuration = SnackbarDuration.Short,
        actionStringResource: StringResource? = null,
        onActionClick: (() -> Unit)? = null,
        onDismiss: ((Int) -> Unit)? = null
    ) = postMessage(
        StringResource(stringResId), duration,
        actionStringResource, onActionClick, onDismiss)
}