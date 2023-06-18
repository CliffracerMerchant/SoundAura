/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.dialog

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cliffracertech.soundaura.R
import com.cliffracertech.soundaura.imeIsOpen
import com.cliffracertech.soundaura.ui.HorizontalDivider
import com.cliffracertech.soundaura.ui.VerticalDivider
import com.cliffracertech.soundaura.ui.bottomEndShape
import com.cliffracertech.soundaura.ui.bottomShape
import com.cliffracertech.soundaura.ui.bottomStartShape
import com.cliffracertech.soundaura.ui.minTouchTargetSize
import com.google.accompanist.insets.LocalWindowInsets

/**
 * A row of buttons for an alert dialog containing an optional
 * cancel button and a disableable confirm button.
 *
 * @param onCancel The callback that will be invoked when the optional
 *     cancel button is clicked. The cancel button will not be displayed
 *     if onCancel is null.
 * @param confirmButtonEnabled Whether or not the confirm button will be enabled
 * @param confirmText The text used for the confirm button
 * @param onConfirm The callback that will be invoked when the confirm button is clicked
 */
@Composable fun DialogButtonRow(
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null,
    confirmButtonEnabled: Boolean = true,
    confirmText: String = stringResource(R.string.ok),
    onConfirm: () -> Unit = {},
) = Row(modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
    // The height(IntrinsicSize.Max) is needed to prevent the buttons from taking
    // up the dialog window's max height in the PhoneStatePermission dialog.
    if (onCancel != null) {
        TextButton(
            onClick = onCancel,
            modifier = Modifier.minTouchTargetSize().weight(1f),
            shape = MaterialTheme.shapes.medium.bottomStartShape(),
            content = { Text(stringResource(R.string.cancel)) })
        VerticalDivider()
    }
    TextButton(
        onClick = onConfirm,
        modifier = Modifier.minTouchTargetSize().weight(1f),
        enabled = confirmButtonEnabled,
        shape = if (onCancel != null)
            MaterialTheme.shapes.medium.bottomEndShape()
        else MaterialTheme.shapes.medium.bottomShape(),
        content = { Text(confirmText) })
}

@Composable private fun SoundAuraDialogContent(
    titleLayout: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
    buttons: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier
) = Surface(modifier, MaterialTheme.shapes.medium) {
    Column {
        titleLayout()
        Column(Modifier.weight(1f, false).verticalScroll(rememberScrollState()),
            content = content)
        buttons()
    }
}

// SoundAuraDialog was created to have more control over the layout of the dialog
// than the stock Compose AlertDialog allows, and due to the fact that the standard
// AlertDialog was not adding space in between the title and the content TextField
// of the rename dialog, despite trying to add spacers and/or padding to both the
// title and the TextField.
/**
 * Show an alert dialog.
 *
 * @param modifier The [Modifier] to use for the dialog window.
 * @param useDefaultWidth The value to use for the [DialogProperties]
 *     usePlatformDefaultWidth value. If false, the size of the dialog
 *     can be set through [modifier] argument instead.
 * @param title The string representing the dialog's title. Can be null, in
 *     which case the title will not be displayed.
 * @param titleLayout The layout that will be used for the dialog's title.
 *     Will default to a composable [Text] using the value of the title parameter.
 *     Will not be displayed if title == null.
 * @param text The string representing the dialog's message. Will only be used
 *     if the [content] parameter is not overridden, in which case it will default
 *     to a composable [Text] containing the value of this parameter.
 * @param onDismissRequest The callback that will be invoked when the user taps
 *     the cancel button, if shown, or when they tap outside the dialog, or when
 *     the back button is pressed.
 * @param showCancelButton Whether or not the cancel button will be shown.
 * @param confirmButtonEnabled Whether the confirm button is enabled.
 * @param confirmText The string used for the confirm button.
 * @param onConfirm The callback that will be invoked when the confirm button is tapped.
 * @param buttons The composable lambda whose contents will be used as the
 *     bottom row of buttons. The default value composes an ok button and an
 *     optional cancel button according to the values of [showCancelButton],
 *     [confirmButtonEnabled], [confirmText], and [onConfirm].
 * @param content The composable lambda used for the dialog's content area.
 *     content will default to a composable Text object that contains the text
 *     described by the text parameter.
 */
@Composable fun SoundAuraDialog(
    modifier: Modifier = Modifier,
    useDefaultWidth: Boolean = true,
    title: String? = null,
    titleLayout: @Composable ColumnScope.() -> Unit = {
        if (title != null)
            Row(modifier = Modifier
                    .padding(start = 16.dp, top = 16.dp,
                             end = 16.dp, bottom = 12.dp)
                    .align(Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(text = title, maxLines = 1,
                     overflow = TextOverflow.Ellipsis,
                     style = MaterialTheme.typography.h6)
            }
    }, text: String? = null,
    onDismissRequest: () -> Unit,
    showCancelButton: Boolean = true,
    confirmButtonEnabled: Boolean = true,
    confirmText: String = stringResource(R.string.ok),
    onConfirm: () -> Unit = onDismissRequest,
    buttons: @Composable ColumnScope.() -> Unit = {
        HorizontalDivider(Modifier.padding(top = 12.dp))
        DialogButtonRow(
            onCancel = if (showCancelButton) onDismissRequest
                       else                  null,
            confirmButtonEnabled = confirmButtonEnabled,
            confirmText = confirmText,
            onConfirm = onConfirm)
    },
    content: @Composable ColumnScope.() -> Unit = @Composable {
        Text(text = text ?: "",
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.body1)
    }
) = Dialog(onDismissRequest, DialogProperties(usePlatformDefaultWidth = useDefaultWidth)) {
    if (!useDefaultWidth) {
        val imeIsOpen by imeIsOpen()
        val yOffset by animateFloatAsState(
            if (!imeIsOpen) 0f
            else LocalWindowInsets.current
                .run { ime.bottom - navigationBars.bottom } / -2f)
        Box(modifier = Modifier
                .graphicsLayer { translationY = yOffset }
                .fillMaxSize()
                .clickable(onClick = onDismissRequest),
            contentAlignment = Alignment.Center,
        ) {
            // The combo of the outer box clickable and the disabled
            // dialog content clickable causes the dialog to close when a
            // click outside its bounds is performed like a standard dialog
            SoundAuraDialogContent(
                titleLayout, content, buttons,
                modifier.clickable(false) {})
        }
    } else SoundAuraDialogContent(titleLayout, content, buttons, modifier)
}