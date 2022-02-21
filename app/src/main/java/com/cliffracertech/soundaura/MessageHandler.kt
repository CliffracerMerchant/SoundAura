/* This file is part of SoundAura, which is released under the terms of the Apache
 * License 2.0. See license.md in the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.compose.runtime.mutableStateOf
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject

/** A state holder for a search query entry. */
@ActivityRetainedScoped
class SearchQueryState @Inject constructor() {
    val query = mutableStateOf<String?>(null)
}

/**
 * A manager of messages to be displayed to the user, e.g. through a SnackBar.
 *
 * New messages can be posted using the postMessage function. MessageHandler
 * users can listen to the SharedFlow member messages for new messages. The
 * function postItemsDeletedMessage is provided for convenience for the common
 * use case of showing an X item(s) deleted message after items are deleted
 * from a list, along with an undo action.
 */
@ActivityRetainedScoped
class MessageHandler @Inject constructor() {
    /**
     * A message to be displayed to the user.
     * @param stringResource A StringResource that, when resolved, will be the text of the message.
     * @param actionStringResource A nullable StringResource that, when resolved, will be the text
     * of the message action, if any.
     * @param onActionClick The callback that will be invoked if the message action is clicked.
     * @param onDismiss The callback that will be invoked when the message is dismissed. The
     * int parameter will be equal to a value of BaseTransientBottomBar.BaseCallback.DismissEvent.
     */
    data class Message(
        val stringResource: StringResource,
        val actionStringResource: StringResource? = null,
        val onActionClick: (() -> Unit)? = null,
        val onDismiss: ((Int) -> Unit)? = null)

    private val _messages = MutableSharedFlow<Message>(
        extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val messages = _messages.asSharedFlow()

    /** Post the message described by the parameters to the message queue. */
    fun postMessage(
        stringResource: StringResource,
        actionStringResource: StringResource? = null,
        onActionClick: (() -> Unit)? = null,
        onDismiss: ((Int) -> Unit)? = null
    ) = _messages.tryEmit(Message(stringResource, actionStringResource, onActionClick, onDismiss))
}