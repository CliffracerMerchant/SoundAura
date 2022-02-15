/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cliffracertech.soundaura.ui.theme.SoundAuraTheme

/** A Modifier extension function that will give the composable a 56.dp height
 * background that fills its parent's max width and has a horizontal gradient
 * brush that goes from the local theme's primary color to its primaryVariant. */
fun Modifier.gradientActionBarModifier() = composed {
    fillMaxWidth(1f).height(56.dp).background(Brush.horizontalGradient(
        listOf(MaterialTheme.colors.primary, MaterialTheme.colors.primaryVariant)))
}

/**
 * A horizontal bar that is suitable to be used as a top action bar when
 * displaying a list of items. The bar integrates an optional back button, a
 * navigation title / search query, an optional search button and a button to
 * open a list of sorting options, and any other content passed in through the
 * parameter otherContent. The bar will always display a back button if there
 * is an active search query (i.e. searchQuery is not null), but will otherwise
 * only display it if backButtonShouldBeVisible is true. The title will be
 * replaced by the search query if it is not null.
 *
 * @param backButtonShouldBeVisible Whether or not the back button should be
 * visible due to other state held outside the action bar. If searchQuery is
 * not null, the back button will be shown regardless.
 * @param onBackButtonClick The callback that will be invoked when the back
 * button is clicked while backButtonShouldBeVisible is true. If the back
 * button is shown due to there being a non-null search query, the back button
 * will close the search query and onBackButtonClicked will not be called.
 * @param title The title that will be displayed when there is no search query.
 * @param searchQuery The current search query that will be displayed if not null.
 * @param onSearchQueryChanged The callback that will be invoked when the user
 * input should modify the value of the search query.
 * @param showSearchAndChangeSortButtons Whether or not the search and change
 * sort buttons should be visible.
 * @param onSearchButtonClick The callback that will be invoked when the user
 * clicks the search button. Typically this should set the search query to an
 * empty string if it is already null so that the search query entry will appear,
 * or set it to null if it is not null so that the search query entry will be closed.
 * @param sortOptions An array of all possible sorting enum values (usually
 * accessed with enumValues<>()
 * @param sortOptionNames An array containing the string values that should
 * represent each sorting option.
 * @param currentSortOption A value of the type parameter that indicates the
 * currently selected sort option.
 * @param otherContent A composable containing other contents that should be
 * placed at the end of the action bar.
 */
@Composable
fun <T> ListActionBar(
    backButtonShouldBeVisible: Boolean = false,
    onBackButtonClick: () -> Unit,
    title: String,
    searchQuery: String? = null,
    onSearchQueryChanged: (String?) -> Unit,
    showSearchAndChangeSortButtons: Boolean = true,
    onSearchButtonClick: () -> Unit,
    sortOptions: Array<T>,
    sortOptionNames: Array<String>,
    currentSortOption: T,
    onSortOptionClick: (T) -> Unit,
    otherContent: @Composable () -> Unit = { },
) = Row(modifier = Modifier.gradientActionBarModifier(),
        verticalAlignment = Alignment.CenterVertically
) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colors.onPrimary) {
        // Back button
        val backButtonIsVisible = backButtonShouldBeVisible || searchQuery != null
        AnimatedContent(
            targetState = backButtonIsVisible,
            contentAlignment = Alignment.Center,
            transitionSpec = { ContentTransform(
                slideInHorizontally(tween()) { -it },
                slideOutHorizontally(tween()) { -it },
                0f, SizeTransform(false) { _, _ -> tween() },
            )}
        ) {
            if (!it) Spacer(Modifier.width(24.dp))
            else BackButton { if (searchQuery == null) onBackButtonClick()
                              else onSearchQueryChanged(null) }
        }

        // Title / search query
        Crossfade(searchQuery != null, Modifier.weight(1f)) {
            if (it) AutoFocusSearchQuery(searchQuery ?: "", onSearchQueryChanged)
            else Crossfade(title) { Text(it, style = MaterialTheme.typography.h6) }
        }

        AnimatedVisibility(
            visible = showSearchAndChangeSortButtons,
            enter = slideInHorizontally(tween()) { it },
            exit = slideOutHorizontally(tween()) { it },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Search button
                val vector = AnimatedImageVector.animatedVectorResource(R.drawable.search_to_close)
                val painter = rememberAnimatedVectorPainter(vector, searchQuery != null)
                IconButton(onClick = onSearchButtonClick) {
                    Icon(painter, stringResource(R.string.search_description))
                }
                // Change sort button
                var sortMenuShown by remember { mutableStateOf(false) }
                IconButton(onClick = { sortMenuShown = !sortMenuShown }) {
                    Icon(imageVector = Icons.Default.Sort,
                         stringResource(R.string.sort_options_description))
                    EnumDropDownMenu(
                        expanded = sortMenuShown,
                        values = sortOptions,
                        valueNames = sortOptionNames,
                        currentValue = currentSortOption,
                        onValueChanged = onSortOptionClick,
                        onDismissRequest = { sortMenuShown = false })
                }
                otherContent()
            }
        }
    }
}

/**
 * A DropdownMenu that displays an option for each value of the enum type
 * parameter, and a checked or unchecked radio button besides each to show
 * the currently selected value.
 *
 * @param expanded Whether the dropdown menu is displayed
 * @param values An array of all possible values for the enum type
 *               (usually accessed with enumValues<T>()
 * @param valueNames A string array containing string values to use to
 *                   represent each value of the parameter enum type T.
 * @param currentValue The currently selected enum value
 * @param onValueChanged The callback that will be invoked when the user taps an item.
 * @param onDismissRequest The callback that will be invoked when the menu should
 *                         be dismissed.
 */
@Composable fun <T> EnumDropDownMenu(
    expanded: Boolean,
    values: Array<T>,
    valueNames: Array<String>,
    currentValue: T,
    onValueChanged: (T) -> Unit,
    onDismissRequest: () -> Unit
) = DropdownMenu(expanded, onDismissRequest) {
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
}

/**
 * A search query that auto-focuses when composed or recomposed, and displays
 * an underline instead of a background.
 *
 * @param query The current value of the search query
 * @param onQueryChanged The callback to be invoked when user input changes the query
 */
@Composable fun AutoFocusSearchQuery(
    query: String,
    onQueryChanged: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = FocusRequester()
    val keyboardController = LocalSoftwareKeyboardController.current

    BasicTextField(
        value = query, onValueChange = { onQueryChanged(it) },
        textStyle = MaterialTheme.typography.h6.copy(color = MaterialTheme.colors.onPrimary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, ),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        modifier = Modifier
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) keyboardController?.show() },
        singleLine = true,
        decorationBox = { innerTextField ->
            Box {
                innerTextField()
                Divider(Modifier.padding(0.dp, 26.dp, 0.dp, 0.dp),
                        LocalContentColor.current.copy(LocalContentAlpha.current),
                        thickness = (1.5).dp,)
            }
        }
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Preview @Composable fun PreviewListActionBarWithTitle() =
    SoundAuraTheme { ListActionBar(
        backButtonShouldBeVisible = false,
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
        backButtonShouldBeVisible = false,
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
        backButtonShouldBeVisible = true,
        onBackButtonClick = { },
        title = "Nested screen",
        searchQuery = null,
        onSearchQueryChanged = { },
        showSearchAndChangeSortButtons = false,
        sortOptions = Track.Sort.values(),
        sortOptionNames = Track.Sort.stringValues(),
        currentSortOption = Track.Sort.NameAsc,
        onSortOptionClick = { },
        onSearchButtonClick = { }
    )}

