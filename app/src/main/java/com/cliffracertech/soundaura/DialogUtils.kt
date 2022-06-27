/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
    horizontalPadding: Dp = 16.dp,
    title: String? = null,
    titleLayout: @Composable (String) -> Unit = @Composable {
        Box(Modifier.padding(
            top = 16.dp,
            start = horizontalPadding,
            end = horizontalPadding)
        ) {
            val textStyle = MaterialTheme.typography.body1
            ProvideTextStyle(textStyle) { Text(it) }
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
) = Dialog(onDismissRequest) {
    Surface(shape = MaterialTheme.shapes.medium) {
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
 * @param pages The list of composable lambdas that will be used as the
 *     content for each page of the dialog. The provided modifier should be
 *     used for the root layout of each page of content so that the pages'
 *     margins will match those of the dialog's title.
 */
@Composable fun MultiStepDialog(
    title: String,
    titleContentSpacing: Dp = 12.dp,
    contentButtonSpacing: Dp = 12.dp,
    onDismissRequest: () -> Unit,
    onFinish: () -> Unit = onDismissRequest,
    pages: List<@Composable (Modifier) -> Unit>
) = Dialog(onDismissRequest, DialogProperties(usePlatformDefaultWidth = false)) {
    require(pages.isNotEmpty())

    Surface(Modifier.padding(24.dp), MaterialTheme.shapes.medium) {
        Column(Modifier.padding(top = 16.dp)) {
            var previousPage by rememberSaveable { mutableStateOf(0) }
            var currentPage by rememberSaveable { mutableStateOf(0) }

            Column {
                // The horizontal padding is applied to each item here instead of to
                // the parent column so that the pages can have their background set
                // before the padding is applied. This improves the look of the page
                // slide animations.
                val horizontalPaddingModifier = Modifier.padding(horizontal = 16.dp)

                Row(horizontalPaddingModifier) {
                    Text(title, style = MaterialTheme.typography.body1)
                    Spacer(Modifier.weight(1f))
                    Text(stringResource(R.string.multi_step_dialog_indicator, currentPage + 1, pages.size),
                         style = MaterialTheme.typography.subtitle1)
                }
                Spacer(Modifier.height(titleContentSpacing))

                val pageModifier = Modifier.background(MaterialTheme.colors.surface)
                                           .then(horizontalPaddingModifier)
                CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.subtitle1) {
                    SlideAnimatedContent(
                        targetState = currentPage,
                        modifier = Modifier.animateContentSize(),
                        leftToRight = currentPage >= previousPage
                    ) { pages[it](pageModifier) }
                }
            }

            Spacer(Modifier.height(contentButtonSpacing))

            Divider()
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
                TextButton(
                    onClick = {
                        if (currentPage == 0)
                            onDismissRequest()
                        else previousPage = currentPage--
                    }, modifier = Modifier.minTouchTargetSize().weight(1f),
                    shape = MaterialTheme.shapes.medium.bottomStartShape()
                ) {
                    Text(stringResource(if (currentPage == 0)
                                            R.string.cancel
                                        else R.string.previous))
                }
                VerticalDivider()
                TextButton(
                    onClick = {
                        if (currentPage == pages.lastIndex)
                            onFinish()
                        else previousPage = currentPage++
                    }, modifier = Modifier.minTouchTargetSize().weight(1f),
                    shape = MaterialTheme.shapes.medium.bottomEndShape()
                ) {
                    Text(stringResource(if (currentPage == pages.lastIndex)
                                            R.string.finish
                                        else R.string.next))
                }
            }
        }
    }
}