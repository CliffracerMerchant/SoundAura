/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.appbar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cliffracertech.soundaura.R
import com.cliffracertech.soundaura.rememberMutableStateOf
import com.cliffracertech.soundaura.ui.SimpleIconButton
import com.cliffracertech.soundaura.ui.defaultSpring
import com.cliffracertech.soundaura.ui.theme.SoundAuraTheme
import com.cliffracertech.soundaura.ui.tweenDuration
import kotlinx.collections.immutable.toImmutableList

/**
 * A horizontal bar to be use as a top app bar for an app
 * that displays a list of searchable and sortable items. 
 * 
 * The bar integrates an optional back button, a navigation title (which
 * is temporarily replaced by any active search query), a search button,
 * a change sort button that open a list of sorting options when clicked,
 * and any other content passed in through the [otherIconButtons] parameter.
 *
 * @param modifier The [Modifier] to use for the bar
 * @param insets The [WindowInsets] for which padding should be applied
 * @param onBackButtonClick The callback to use for the back button.
 *    If null, the back button will not be shown.
 * @param title The title that will be displayed when there is no search query
 * @param showIconButtons Whether or not the icon buttons to the right of the
 *     back button and title/search query should be shown. This includes the
 *     search button, the change sort button, and any other icon buttons added
 *     in [otherContent].
 * @param searchQueryState A [SearchQueryViewState] that contains state and
 *     callbacks related to the active search query and the search button
 * @param sortMenuState A [SortMenuState]`<T>` that contains state and
 *     callbacks related to the sort button and its popup menu
 * @param otherSortMenuContent A composable lambda that contains other
 *     content that should be displayed in the popup sort menu
 * @param otherIconButtons A composable containing other icon buttons
 *     that should be placed at the end of the action bar
 */
@Composable fun ListAppBar(
    modifier: Modifier = Modifier,
    insets: WindowInsets = WindowInsets.statusBars,
    onBackButtonClick: (() -> Unit)?,
    title: String,
    showIconButtons: Boolean,
    searchQueryState: SearchQueryViewState,
    sortMenuState: SortMenuState,
    otherSortMenuContent: @Composable ColumnScope.() -> Unit,
    otherIconButtons: @Composable RowScope.() -> Unit,
) = GradientToolBar(modifier, insets) {
    // Back button
    AnimatedContent(
        targetState = onBackButtonClick != null,
        transitionSpec = { slideInHorizontally(defaultSpring()) { -it }  togetherWith
                           slideOutHorizontally(defaultSpring()) { -it } },
        contentAlignment = Alignment.Center,
        label = "Action bar back button appearance/disappearance",
    ) { backButtonVisible ->
        if (!backButtonVisible)
            Spacer(Modifier.width(24.dp))
        else SimpleIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.back),
            tint = MaterialTheme.colorScheme.onPrimary,
            onClick = onBackButtonClick ?: {})
    }

    // Title / search query
    Crossfade(
        targetState = searchQueryState.query != null,
        modifier = Modifier.weight(1f),
        animationSpec = tween(tweenDuration),
        label = "Action bar search query appearance/disappearance",
    ) { searchIsActive ->
        if (searchIsActive) {
            SearchQueryView(searchQueryState)
        } else Crossfade(
            targetState = title,
            animationSpec = tween(tweenDuration),
            label = "Action bar title crossfade"
        ) {
            Text(text = it,
                modifier = Modifier.height(48.dp).wrapContentHeight(),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.headlineSmall, maxLines = 1)
        }
    }

    // Right aligned content
    AnimatedVisibility(
        visible = showIconButtons,
        enter = slideInHorizontally(defaultSpring()) { it },
        exit = slideOutHorizontally(defaultSpring()) { it },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Search button
            val vector = AnimatedImageVector.animatedVectorResource(R.drawable.search_to_close)
            val painter = rememberAnimatedVectorPainter(vector,
                searchQueryState.icon == SearchQueryViewState.Icon.Close)
            IconButton(onClick = searchQueryState.onButtonClick) {
                Icon(painter, stringResource(R.string.search),
                     tint = MaterialTheme.colorScheme.onPrimary)
            }
            // Sort button
            IconButton(onClick = sortMenuState.onButtonClick) {
                Icon(imageVector = Icons.AutoMirrored.Filled.Sort,
                    contentDescription = stringResource(R.string.sort_options_description),
                    tint = MaterialTheme.colorScheme.onPrimary)
                RadioDropdownMenu(
                    expanded = sortMenuState.showingPopup,
                    options = sortMenuState.optionNames(LocalContext.current),
                    currentIndex = sortMenuState.currentOptionIndex,
                    onOptionClick = sortMenuState.onOptionClick,
                    onDismissRequest = sortMenuState.onPopupDismissRequest,
                    showOtherContentFirst = true,
                    otherContent = otherSortMenuContent)
            }
            otherIconButtons()
        }
    }
}

@Preview @Composable fun PreviewListActionBar() = SoundAuraTheme {
    var searchIsActive by rememberMutableStateOf(false)
    var searchQuery by rememberMutableStateOf<String?>(null)
    var showingSettings by rememberMutableStateOf(false)
    ListAppBar(
        onBackButtonClick = when {
            showingSettings -> {{ showingSettings = false }}
            searchIsActive ->  {{ searchIsActive = false }}
            else ->                null
        }, title = stringResource(
            if (!showingSettings) R.string.app_name
            else R.string.app_settings_description),
        showIconButtons = !showingSettings,
        searchQueryState = remember {
            SearchQueryViewState(
                getQuery = { searchQuery },
                onQueryChange = { searchQuery = it },
                onButtonClick = {
                    if (!searchIsActive)
                        searchQuery = ""
                    searchIsActive = !searchIsActive
                }, getIcon = {
                    if (searchIsActive) SearchQueryViewState.Icon.Close
                    else                SearchQueryViewState.Icon.Search
                })
        }, sortMenuState = remember {
            SortMenuState(
                optionNames = { remember { emptyList<String>().toImmutableList() }},
                getCurrentOptionIndex = { 0 },
                onOptionClick = { })
        }, otherSortMenuContent = {},
    ) {
        SimpleIconButton(Icons.Default.Settings, "") {
            showingSettings = !showingSettings
        }
    }
}