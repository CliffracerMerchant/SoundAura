/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.actionbar

import androidx.compose.animation.*
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cliffracertech.soundaura.R
import com.cliffracertech.soundaura.minTouchTargetSize
import com.cliffracertech.soundaura.model.database.Track
import com.cliffracertech.soundaura.ui.theme.SoundAuraTheme
import com.google.accompanist.insets.statusBarsPadding

/** Compose a [Row] with a gradient background and vertically centered
 * content, while providing the current theme's onPrimary color as the
 * [LocalContentColor]. */
@Composable fun GradientToolBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val gradStart = MaterialTheme.colors.primaryVariant
    val gradEnd = MaterialTheme.colors.secondaryVariant
    val gradient = remember(gradStart, gradEnd) {
        Brush.horizontalGradient(listOf(gradStart, gradEnd))
    }
    Row(modifier.fillMaxWidth()
            .background(gradient)
            .statusBarsPadding()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val color = MaterialTheme.colors.onPrimary
        CompositionLocalProvider(LocalContentColor provides color) {
            content()
        }
    }
}

/**
 * A horizontal bar that is suitable to be used as a top action bar when
 * displaying a list of items. The bar integrates an optional back button, a
 * navigation title / search query, an optional search button and a button to
 * open a list of sorting options, and any other content passed in through the
 * parameter [otherContent]. The bar will always display a back button if there
 * is an active search query (i.e. [searchQuery] is not null), but will otherwise
 * only display it if [showBackButtonForNavigation] is true. The title will be
 * replaced by the search query if it is not null.
 *
 * @param modifier The [Modifier] to use for the bar
 * @param showBackButtonForNavigation Whether or not the back button should be
 *     visible due to other state held outside the action bar. If [searchQuery]
 *     is not null, the back button will be shown regardless.
 * @param onBackButtonClick The callback that will be invoked when the back
 *     button is clicked while [showBackButtonForNavigation] is true. If the
 *     back button is shown due to a non-null search query, the back button
 *     will close the search query and [onBackButtonClick] will not be called.
 * @param title The title that will be displayed when there is no search query
 * @param searchQuery The current search query that will be displayed if not null
 * @param onSearchQueryChanged The callback that will be invoked when the
 *     user input should modify the value of the search query
 * @param showRightAlignedContent Whether or not the contents to the right
 *     of the back button and title / search query should be shown. If false
 *     the search button, change sort button, and any content described in
 *     the parameter content will be invisible.
 * @param onSearchButtonClick The callback that will be invoked when the
 *     user clicks the search button. Typically this should set the search
 *     query to an empty string if it is already null so that the search
 *     query entry will appear, or set it to null if it is not null so that
 *     the search query entry will be closed.
 * @param sortOptions An array of all possible sorting enum values,
 *     usually accessed with [enumValues]<T>()
 * @param sortOptionNames An array containing the [String] values that
 *     should represent each sorting option
 * @param currentSortOption A value of the type parameter that indicates
 *     the currently selected sort option
 * @param onSortOptionClick The callback that will be invoked when the
 *     user clicks on a sort option
 * @param otherSortMenuContent A composable lambda that contains other
 *     content that should be displayed in the popup sort menu. The
 *     dismiss request lambda for the popup sort menu is passed as a
 *     parameter so that additional sort menu contents can close the
 *     menu in their on click action.
 * @param otherContent A composable containing other contents that should
 *     be placed at the end of the action bar
 */
@Composable fun <T> ListActionBar(
    modifier: Modifier = Modifier,
    showBackButtonForNavigation: Boolean = false,
    onBackButtonClick: () -> Unit,
    title: String,
    searchQuery: String? = null,
    onSearchQueryChanged: (String?) -> Unit,
    showRightAlignedContent: Boolean = true,
    onSearchButtonClick: () -> Unit,
    sortOptions: Array<T>,
    sortOptionNames: Array<String>,
    currentSortOption: T,
    onSortOptionClick: (T) -> Unit,
    otherSortMenuContent: @Composable ColumnScope.(onDismissRequest: () -> Unit) -> Unit = {},
    otherContent: @Composable () -> Unit = { },
) = GradientToolBar(modifier) {
    // Back button
    AnimatedContent(
        targetState = showBackButtonForNavigation || searchQuery != null,
        contentAlignment = Alignment.Center,
        transitionSpec = { slideInHorizontally { -it } + fadeIn() with
                           slideOutHorizontally { -it } + fadeOut() using
                           SizeTransform(clip = false) }
    ) { backButtonIsVisible ->
        if (!backButtonIsVisible)
            Spacer(Modifier.width(24.dp))
        else IconButton(onClick = {
            if (searchQuery == null)
                onBackButtonClick()
            else onSearchQueryChanged(null)
        }) {
            Icon(Icons.Default.ArrowBack, stringResource(R.string.back))
        }
    }

    // Title / search query
    Crossfade(// This outer crossfade is for when the search query appears/disappears.
        targetState = searchQuery != null,
        modifier = Modifier.weight(1f)
    ) { searchQueryIsNotNull ->
        // This inner row should be unnecessary, but prevents a minor animation
        // bug where the search underline appears thicker during the animation,
        // but then changes to its final thickness after the animation ends.
        Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
            // lastSearchQuery is used so that when the search query changes from a
            // non-null non-blank value to null, the search query will be recomposed
            // with the value of lastSearchQuery instead of null during the search
            // query's fade out animation. This allows the last non-null search
            // query text to fade out with the rest of the search query (i.e. the
            // underline) instead of abruptly disappearing.
            var lastSearchQuery by rememberSaveable { mutableStateOf("") }
            if (searchQueryIsNotNull) {
                val text = searchQuery ?: lastSearchQuery
                SearchQuery(text, onSearchQueryChanged)
                @Suppress("UNUSED_VALUE")
                if (searchQuery != null)
                    lastSearchQuery = searchQuery
            } else Crossfade(title) { // This inner crossfade is for when the title changes.
                Text(it, style = MaterialTheme.typography.h5, maxLines = 1)
            }
        }
    }

    // Right aligned content
    AnimatedVisibility(
        visible = showRightAlignedContent,
        enter = slideInHorizontally { it } + fadeIn(),
        exit = slideOutHorizontally { it } + fadeOut(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Search button
            val vector = AnimatedImageVector.animatedVectorResource(R.drawable.search_to_close)
            val painter = rememberAnimatedVectorPainter(vector, searchQuery != null)
            IconButton(onClick = onSearchButtonClick) {
                Icon(painter, stringResource(R.string.search))
            }
            // Change sort button
            var sortMenuShown by rememberSaveable { mutableStateOf(false) }
            IconButton(onClick = { sortMenuShown = !sortMenuShown }) {
                Icon(imageVector = Icons.Default.Sort,
                     stringResource(R.string.sort_options_description))
                EnumDropDownMenu(
                    expanded = sortMenuShown,
                    values = sortOptions,
                    valueNames = sortOptionNames,
                    currentValue = currentSortOption,
                    onValueChanged = onSortOptionClick,
                    onDismissRequest = { sortMenuShown = false },
                    otherContent = { otherSortMenuContent { sortMenuShown = false } })
            }
            otherContent()
        }
    }
}

/**
 * A [DropdownMenu] that displays an option for each value of the enum type
 * parameter, and a checked or unchecked radio button besides each to show
 * the currently selected value.
 *
 * @param expanded Whether the dropdown menu is displayed
 * @param values An array of all possible values for the enum type,
 *               usually accessed with [enumValues]<T>()
 * @param valueNames An Array<String> containing [String] values to use
 *                   to represent each value of the parameter enum type T
 * @param currentValue The currently selected enum value
 * @param onValueChanged The callback that will be invoked when the user taps an item
 * @param onDismissRequest The callback that will be invoked when the menu should
 *                         be dismissed
 * @param showOtherContentFirst Whether or not to show additional content passed
 *     to [otherContent] above the enum value buttons. If false, the additional
 *     content will be displayed below the enum value buttons instead.
 * @param otherContent Additional content to display either before or after the
 *     enum value buttons, according to the value of [showOtherContentsFirst]
 */
@Composable fun <T> EnumDropDownMenu(
    expanded: Boolean,
    values: Array<T>,
    valueNames: Array<String>,
    currentValue: T,
    onValueChanged: (T) -> Unit,
    onDismissRequest: () -> Unit,
    showOtherContentFirst: Boolean = true,
    otherContent: @Composable ColumnScope.() -> Unit = {},
) = DropdownMenu(expanded, onDismissRequest) {
    if (showOtherContentFirst)
        otherContent()
    values.forEachIndexed { index, value ->
        DropdownMenuItem({ onValueChanged(value); onDismissRequest() }) {
            val name = valueNames.getOrNull(index) ?: "Error"
            Text(text = name, style = MaterialTheme.typography.button)
            Spacer(Modifier.weight(1f))
            val vector = if (value == currentValue)
                             Icons.Default.RadioButtonChecked
                         else Icons.Default.RadioButtonUnchecked
            Icon(vector, name, Modifier.size(36.dp).padding(8.dp))
        }
    }
    if (!showOtherContentFirst)
        otherContent()
}

/**
 * A search query that displays an underline as a background.
 *
 * @param query The current value of the search query
 * @param onQueryChanged The callback to be invoked when user input changes the query
 * @param modifier The [Modifier] used for the entire search query
 */
@Composable fun SearchQuery(
    query: String,
    onQueryChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) = BasicTextField(
    value = query,
    onValueChange = onQueryChanged,
    textStyle = MaterialTheme.typography.h6,
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    modifier = modifier.minTouchTargetSize(),
    singleLine = true,
) { innerTextField ->
    Column(verticalArrangement = Arrangement.Center) {
        innerTextField()
        Divider(color = LocalContentColor.current,
                thickness = (1.5).dp)
    }
}

@Preview @Composable fun PreviewListActionBarWithTitle() =
    SoundAuraTheme { ListActionBar(
        showBackButtonForNavigation = false,
        onBackButtonClick = { },
        title = stringResource(R.string.app_name),
        searchQuery = null,
        onSearchQueryChanged = { },
        sortOptions = Track.Sort.values(),
        sortOptionNames = Track.Sort.stringValues(),
        currentSortOption = Track.Sort.NameAsc,
        onSortOptionClick = { },
        onSearchButtonClick = { }
    ) {
        IconButton({ }) { Icon(Icons.Default.MoreVert, null) }
    }}

@Preview @Composable fun PreviewListActionBarWithSearchQuery() =
    SoundAuraTheme { ListActionBar(
        showBackButtonForNavigation = false,
        onBackButtonClick = { },
        title = "",
        searchQuery = "search query",
        onSearchQueryChanged = { },
        sortOptions = Track.Sort.values(),
        sortOptionNames = Track.Sort.stringValues(),
        currentSortOption = Track.Sort.NameAsc,
        onSortOptionClick = { },
        onSearchButtonClick = { }
    ) {
        IconButton({ }) { Icon(Icons.Default.MoreVert, null) }
    }}

@Preview @Composable fun PreviewListActionBarInNestedScreen() =
    SoundAuraTheme { ListActionBar(
        showBackButtonForNavigation = true,
        onBackButtonClick = { },
        title = "Nested screen",
        searchQuery = null,
        onSearchQueryChanged = { },
        showRightAlignedContent = false,
        sortOptions = Track.Sort.values(),
        sortOptionNames = Track.Sort.stringValues(),
        currentSortOption = Track.Sort.NameAsc,
        onSortOptionClick = { },
        onSearchButtonClick = { }
    )}