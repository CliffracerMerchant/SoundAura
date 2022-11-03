/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * A row of buttons for an alert dialog.
 *
 * @param onCancel The callback that will be invoked when the optional
 *     cancel button is clicked. The cancel button will not be displayed
 *     if onCancel is null, or if the [content] parameter is overridden.
 * @param confirmButtonEnabled Whether or not the confirm button will be
 *     enabled. This parameter will only take effect if the [content] parameter
 *     is not overridden.
 * @param confirmText The text used for the confirm button. This parameter will
 *     only take effect if the [content] parameter is not overridden.
 * @param onConfirm The callback that will be invoked when the confirm button
 *     is clicked. This parameter will only take effect if the [content]
 *     parameter is not overridden.
 * @param content The content of the button row. content defaults to a row with
 *     a cancel button if [onCancel] is not null, and a confirm button that is
 *     enabled according to the value of [confirmButtonEnabled], with a text
 *     matching [confirmText], and a callback equal to [onConfirm].
 */
@Composable fun DialogButtonRow(
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null,
    confirmButtonEnabled: Boolean = true,
    confirmText: String = stringResource(R.string.ok),
    onConfirm: () -> Unit = {},
    content: @Composable () -> Unit = {
        // The height(IntrinsicSize.Max) is needed to prevent the buttons from taking
        // up the dialog window's max height in the PhoneStatePermission dialog.
        Row(modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
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
}) {
    content()
}

// SoundAuraDialog was created to have more control over the layout of the dialog
// than the stock Compose AlertDialog allows, and due to the fact that the standard
// AlertDialog was not adding space in between the title and the content TextField
// of the rename dialog, despite trying to add spacers and/or padding to both the
// title and the TextField.
/**
 * Compose an alert dialog.
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
    Surface(modifier, MaterialTheme.shapes.medium) {
        Column {
            titleLayout()
            Column(Modifier.weight(1f, false).verticalScroll(rememberScrollState()),
                   content = content)
            buttons()
        }
    }
}

/**
 * Compose an alert dialog with multiple pages. The dialog will display
 * one page of content from the pages parameter at a time. The buttons
 * at the bottom of the dialog allow the user to go forward or backward
 * through the pages of content. A progress indicator (e.g. 2 of 4) will
 * be displayed at the top-end corner of the dialog.
 *
 * @param modifier The [Modifier] to use for the dialog window.
 * @param useDefaultWidth The value to use for the [DialogProperties]
 *     usePlatformDefaultWidth value. If false, the size of the dialog
 *     can be set through [modifier] argument instead.
 * @param title The [String] representing the dialog's title.
 * @param onDismissRequest The callback that will be invoked when the user
 *     taps the cancel button (which will replace the previous button if on
 *     the first page of content), or when they tap outside the dialog, or
 *     when the back button is pressed.
 * @param onFinish The callback that will be invoked when the user taps the
 *     finish button. The finish button will replace the next button when
 *     the last page of content is being displayed.
 * @param numPages The total number of pages.
 * @param currentPageIndex The index of the page that should be displayed.
 * @param onCurrentPageIndexChange The callback that will be invoked when the
 *     [currentPageIndex] should be changed. If [currentPageIndex] is not changed
 *     to the provided Int value when this is called, the current page
 *     indicator will be incorrect.
 * @param pages The composable lambda that composes the correct content given
 *     the value of [currentPageIndex]. The provided [Modifier] parameter should
 *     be used for the content to ensure a consistent look. Like [AnimatedVisibility]'s
 *     content parameter, the value of the provided Int should always be
 *     taken into account when determining each page's contents.
 */
@Composable fun MultiStepDialog(
    modifier: Modifier = Modifier,
    useDefaultWidth: Boolean = true,
    title: String,
    onDismissRequest: () -> Unit,
    onFinish: () -> Unit = onDismissRequest,
    numPages: Int,
    currentPageIndex: Int,
    onCurrentPageIndexChange: (Int) -> Unit,
    pages: @Composable AnimatedVisibilityScope.(Modifier, Int) -> Unit
) {
    require(currentPageIndex in 0 until numPages)
    var previousPageIndex by rememberSaveable { mutableStateOf(currentPageIndex) }

    SoundAuraDialog(
        modifier = modifier,
        useDefaultWidth = useDefaultWidth,
        titleLayout = {
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)) {
                Spacer(Modifier.weight(1f))
                Text(title, style = MaterialTheme.typography.h6)
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.multi_step_dialog_indicator,
                                    currentPageIndex + 1, numPages),
                     style = MaterialTheme.typography.subtitle1)
            }
        }, onDismissRequest = onDismissRequest,
        buttons = {
            Divider(Modifier.padding(top = 12.dp))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
                TextButton(
                    onClick = {
                        if (currentPageIndex == 0)
                            onDismissRequest()
                        else {
                            previousPageIndex = currentPageIndex
                            onCurrentPageIndexChange(currentPageIndex - 1)
                        }
                    }, modifier = Modifier.minTouchTargetSize().weight(1f),
                    shape = MaterialTheme.shapes.medium.bottomStartShape()
                ) {
                    Text(stringResource(
                        if (currentPageIndex == 0) R.string.cancel
                        else                       R.string.previous))
                }
                VerticalDivider()
                TextButton(
                    onClick = {
                        if (currentPageIndex == numPages - 1)
                            onFinish()
                        else {
                            previousPageIndex = currentPageIndex
                            onCurrentPageIndexChange(currentPageIndex + 1)
                        }
                    }, modifier = Modifier.minTouchTargetSize().weight(1f),
                    shape = MaterialTheme.shapes.medium.bottomEndShape()
                ) {
                    Text(stringResource(
                        if (currentPageIndex == numPages - 1) R.string.finish
                        else                                  R.string.next))
                }
            }
        }
    ) {
        // The horizontal padding is applied to each item here instead of to
        // the parent column so that the pages can have their background set
        // before the padding is applied. This improves the look of the page
        // slide animations.
        val pageModifier = Modifier
            .background(MaterialTheme.colors.surface)
            .padding(horizontal = 16.dp)

        ProvideTextStyle(MaterialTheme.typography.subtitle1) {
            SlideAnimatedContent(
                targetState = currentPageIndex,
                leftToRight = currentPageIndex >= previousPageIndex,
                content = { pages(pageModifier, it) })
        }
    }
}