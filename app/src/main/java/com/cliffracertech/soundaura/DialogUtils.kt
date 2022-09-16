/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// SoundAuraDialog was created to have more control over the layout of the dialog
// than the stock Compose AlertDialog allows, and due to the fact that the standard
// AlertDialog was not adding space in between the title and the content TextField
// of the rename dialog, despite trying to add spacers and/or padding to both the
// title and the TextField.
/**
 * Compose an alert dialog.
 *
 * @param windowPadding The PaddingValues instance to use for the dialog's window.
 *     If null, the platform default dialog window padding will be used instead.
 * @param horizontalPadding The horizontal padding for the content. The value
 *     is also used as the horizontal padding for the default title layout.
 * @param title The string representing the dialog's title. Can be null, in
 *     which case the title will not be displayed.
 * @param titleLayout The layout that will be used for the dialog's title.
 *     Will default to a composable Text using the value of the title parameter.
 *     Will not be displayed if title == null
 * @param text The string representing the dialog's message. Will only be used
 *     if the content parameter is not overridden, in which case it will default
 *     to a composable Text containing the value of this parameter.
 * @param titleContentSpacing The spacing, in Dp, in between the title and the content.
 * @param contentButtonSpacing The spacing, in Dp, in between the content and the buttons.
 * @param onDismissRequest The callback that will be invoked when the user taps
 *     the cancel button, if shown, or when they tap outside the dialog, or when
 *     the back button is pressed.
 * @param showCancelButton Whether or not the cancel button will be shown.
 * @param confirmButtonEnabled Whether the confirm button is enabled.
 * @param confirmText The string used for the confirm button.
 * @param onConfirm The callback that will be invoked when the confirm button is tapped.
 * @param content The composable lambda used for the dialog's content area.
 *     content will default to a composable Text object that contains the text
 *     described by the text parameter.
 */
@Composable fun SoundAuraDialog(
    windowPadding: PaddingValues? = null,
    horizontalPadding: Dp = 16.dp,
    title: String? = null,
    titleLayout: @Composable (String) -> Unit = @Composable {
        Row(Modifier.fillMaxWidth(), Arrangement.Center) {
            Text(text = it,
                modifier = Modifier.padding(
                    start = horizontalPadding, top = 16.dp,
                    end = horizontalPadding, bottom = 0.dp),
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                style = MaterialTheme.typography.h6)
        }
    }, text: String? = null,
    titleContentSpacing: Dp = 12.dp,
    contentButtonSpacing: Dp = 12.dp,
    onDismissRequest: () -> Unit,
    showCancelButton: Boolean = true,
    confirmButtonEnabled: Boolean = true,
    confirmText: String = stringResource(R.string.ok),
    onConfirm: () -> Unit = onDismissRequest,
    content: @Composable () -> Unit = @Composable {
        CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.high) {
            val textStyle = MaterialTheme.typography.subtitle1
            ProvideTextStyle(textStyle) { Text(text ?: "") }
        }
    }
) = Dialog(
    onDismissRequest = onDismissRequest,
    properties = DialogProperties(usePlatformDefaultWidth = windowPadding == null)
) {
    val surfaceModifier = if (windowPadding == null) Modifier
                          else Modifier.padding(windowPadding)
    Surface(surfaceModifier, MaterialTheme.shapes.medium) {
        Column {
            if (title != null) {
                titleLayout(title)
                Spacer(Modifier.height(titleContentSpacing))
            }
            Box(Modifier.padding(horizontal = horizontalPadding)) {
                content()
            }

            Spacer(Modifier.height(contentButtonSpacing))

            Divider()
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
                if (showCancelButton)
                    TextButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.minTouchTargetSize().weight(1f),
                        shape = MaterialTheme.shapes.medium.bottomStartShape(),
                        content = { Text(stringResource(R.string.cancel)) })
                VerticalDivider()
                TextButton(
                    onClick = onConfirm,
                    modifier = Modifier.minTouchTargetSize().weight(1f),
                    enabled = confirmButtonEnabled,
                    shape = if (showCancelButton)
                                MaterialTheme.shapes.medium.bottomEndShape()
                            else MaterialTheme.shapes.medium.bottomShape(),
                    content = { Text(confirmText) })
            }
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
 * @param title The string representing the dialog's title.
 * @param titleContentSpacing The spacing, in Dp, in between the title and the content.
 * @param contentButtonSpacing The spacing, in Dp, in between the content and the buttons.
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
 *     currentPageIndex should be changed. If currentPageIndex is not changed
 *     to the provided Int value when this is called, the current page
 *     indicator will be incorrect.
 * @param pages The composable lambda that composes the correct content given
 *     the value of currentPageIndex. The provided Modifier parameter should
 *     be used for the content to ensure a consistent look. Like AnimatedVisiblilty's
 *     content parameter, the value of the provided Int should always be
 *     taken into account when determining each page's contents.
 */
@Composable fun MultiStepDialog(
    title: String,
    titleContentSpacing: Dp = 12.dp,
    contentButtonSpacing: Dp = 12.dp,
    onDismissRequest: () -> Unit,
    onFinish: () -> Unit = onDismissRequest,
    numPages: Int,
    currentPageIndex: Int,
    onCurrentPageIndexChange: (Int) -> Unit,
    pages: @Composable AnimatedVisibilityScope.(Modifier, Int) -> Unit
) = Dialog(onDismissRequest, DialogProperties(usePlatformDefaultWidth = false)) {
    require(currentPageIndex in 0 until numPages)

    Surface(Modifier.padding(24.dp), MaterialTheme.shapes.medium) {
        Column(Modifier.padding(top = 16.dp)) {
            var previousPageIndex by rememberSaveable { mutableStateOf(currentPageIndex) }

            Column {
                // The horizontal padding is applied to each item here instead of to
                // the parent column so that the pages can have their background set
                // before the padding is applied. This improves the look of the page
                // slide animations.
                val horizontalPaddingModifier = Modifier.padding(horizontal = 16.dp)

                Row(horizontalPaddingModifier) {
                    Spacer(Modifier.weight(1f))
                    Text(title, style = MaterialTheme.typography.h6)
                    Spacer(Modifier.weight(1f))
                    Text(stringResource(R.string.multi_step_dialog_indicator, currentPageIndex + 1, numPages),
                         style = MaterialTheme.typography.subtitle1)
                }
                Spacer(Modifier.height(titleContentSpacing))

                val pageModifier = Modifier.background(MaterialTheme.colors.surface)
                                           .then(horizontalPaddingModifier)
                ProvideTextStyle(MaterialTheme.typography.subtitle1) {
                    SlideAnimatedContent(
                        targetState = currentPageIndex,
                        leftToRight = currentPageIndex >= previousPageIndex,
                        content = { pages(pageModifier, it) })
                }
            }

            Spacer(Modifier.height(contentButtonSpacing))

            Divider()
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
    }
}