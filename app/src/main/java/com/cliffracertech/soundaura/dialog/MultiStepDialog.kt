/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.dialog

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cliffracertech.soundaura.R
import com.cliffracertech.soundaura.ui.SlideAnimatedContent
import com.cliffracertech.soundaura.ui.TextButton
import com.cliffracertech.soundaura.ui.VerticalDivider
import com.cliffracertech.soundaura.ui.bottomEndShape
import com.cliffracertech.soundaura.ui.bottomStartShape
import com.cliffracertech.soundaura.ui.minTouchTargetSize

/**
 * Compose an alert dialog with multiple pages. The dialog will display
 * one page of content from the pages parameter at a time. The buttons
 * at the bottom of the dialog allow the user to go forward or backward
 * through the pages of content. A progress indicator (e.g. 2 of 4) will
 * be displayed at the top-end corner of the dialog.
 *
 * @param modifier The [Modifier] to use for the dialog window.
 * @param width A [DialogWidth] value that describes how the dialog's
 *     horizontal size will be determined
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
    width: DialogWidth = DialogWidth.MatchToScreenSize(WindowInsets.ime),
    title: String,
    onDismissRequest: () -> Unit,
    onFinish: () -> Unit = onDismissRequest,
    numPages: Int,
    currentPageIndex: Int,
    onCurrentPageIndexChange: (Int) -> Unit,
    pages: @Composable AnimatedVisibilityScope.(Modifier, Int) -> Unit
) {
    require(currentPageIndex in 0 until numPages)
    var previousPageIndex by rememberSaveable { mutableIntStateOf(currentPageIndex) }

    SoundAuraDialog(
        modifier = modifier,
        width = width,
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
                    modifier = Modifier.minTouchTargetSize().weight(1f),
                    shape = MaterialTheme.shapes.medium.bottomStartShape(),
                    textResId = if (currentPageIndex == 0) R.string.cancel
                                else                       R.string.previous,
                    onClick = {
                        if (currentPageIndex == 0)
                            onDismissRequest()
                        else {
                            previousPageIndex = currentPageIndex
                            onCurrentPageIndexChange(currentPageIndex - 1)
                        }
                    })
                VerticalDivider()
                TextButton(
                    modifier = Modifier.minTouchTargetSize().weight(1f),
                    shape = MaterialTheme.shapes.medium.bottomEndShape(),
                    textResId = if (currentPageIndex == numPages - 1) R.string.finish
                                else                                  R.string.next,
                    onClick = {
                        if (currentPageIndex == numPages - 1)
                            onFinish()
                        else {
                            previousPageIndex = currentPageIndex
                            onCurrentPageIndexChange(currentPageIndex + 1)
                        }
                    })
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