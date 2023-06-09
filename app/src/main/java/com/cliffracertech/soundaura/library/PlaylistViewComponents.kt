/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationConstants.DefaultDurationMillis
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cliffracertech.soundaura.R
import com.cliffracertech.soundaura.dialog.RenameDialog
import com.cliffracertech.soundaura.dialog.SoundAuraDialog
import com.cliffracertech.soundaura.model.StringResource
import com.cliffracertech.soundaura.model.Validator

@Composable fun <T>overshootTween(
    duration: Int = DefaultDurationMillis,
    delay: Int = 0,
) = tween<T>(duration, delay) {
    val t = it - 1
    t * t * (3 * t + 2) + 1
}

@Composable fun <T>anticipateTween(
    duration: Int = DefaultDurationMillis,
    delay: Int = 0,
) = tween<T>(duration, delay) {
    it * it * (3 * it - 2)
}

/**
 * An [IconButton] that alternates between an empty circle with a plus icon,
 * and a filled circle with a minus icon depending on the parameter [added].
 *
 * @param added The added/removed state of the item the button is
 *     representing. If added is true, the button will display a minus
 *     icon. If added is false, a plus icon will be displayed instead.
 * @param contentDescription The content description of the button.
 * @param backgroundColor The [Color] of the background that the button
 *     is being displayed on. This is used for the inner plus icon
 *     when [added] is true and the background of the button is filled.
 * @param tint The [Color] that will be used for the button.
 * @param onClick The callback that will be invoked when the button is clicked.
 */
@Composable fun AddRemoveButton(
    added: Boolean,
    contentDescription: String? = null,
    backgroundColor: Color = MaterialTheme.colors.background,
    tint: Color = LocalContentColor.current,
    onClick: () -> Unit
) = IconButton(onClick) {
    // Circle background
    // The combination of the larger tinted circle and the smaller
    // background-tinted circle creates the effect of a circle that
    // animates between filled and outlined.
    Box(Modifier.size(24.dp).background(tint, CircleShape))
    AnimatedVisibility(
        visible = !added,
        enter = scaleIn(overshootTween()),
        exit = scaleOut(anticipateTween()),
    ) {
        Box(Modifier.size(20.dp).background(backgroundColor, CircleShape))
    }

    // Plus / minus icon
    // The combination of the two angles allows the icon to always
    // rotate clockwise, instead of alternating between clockwise
    // and counterclockwise.
    val angleMod by animateFloatAsState(if (added) 90f else 0f)
    val angle = if (added) 90f + angleMod
    else       90f - angleMod

    val iconTint by animateColorAsState(
        if (added) backgroundColor else tint)
    val minusIcon = painterResource(R.drawable.minus)

    // One minus icon always appears horizontally, while the other
    // can rotate between 0 and 90 degrees so that both minus icons
    // together appear as a plus icon.
    Icon(minusIcon, null, Modifier.rotate(2 * angle), tint)
    Icon(minusIcon, contentDescription, Modifier.rotate(angle), iconTint)
}

@Composable fun TrackRenameDialog(
    itemName: String,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var currentName by rememberSaveable { mutableStateOf(itemName) }
    RenameDialog(
        initialName = itemName,
        proposedNameProvider = { currentName },
        onProposedNameChange = { currentName = it },
        errorMessageProvider = {
            if (currentName.isNotBlank()) null
            else Validator.Message.Error(StringResource(R.string.track_name_cannot_be_blank_error_message))
        }, onDismissRequest = onDismissRequest,
        onConfirm = {
            onConfirm(currentName)
            onDismissRequest()
        })
}

@Composable fun ConfirmRemoveDialog(
    itemName: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit
) = SoundAuraDialog(
    onDismissRequest = onDismissRequest,
    title = stringResource(R.string.confirm_remove_title, itemName),
    text = stringResource(R.string.confirm_remove_message),
    confirmText = stringResource(R.string.remove),
    onConfirm = {
        onConfirm()
        onDismissRequest()
    })