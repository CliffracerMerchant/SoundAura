/* This file is part of SoundObservatory, which is released under the Apache License 2.0. See
 * license.md in the project's root directory or use an internet search engine to see the full license. */
package com.cliffracertech.soundobservatory

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Sort
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
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
import com.cliffracertech.soundobservatory.ui.theme.SoundObservatoryTheme

@Composable fun primaryColorHorizontalGradient() =
    Brush.horizontalGradient(listOf(MaterialTheme.colors.primary,
                                    MaterialTheme.colors.primaryVariant))

/**
 * A bar that is suitable to be used as a top action bar when displaying a
 * list of items. The bar integrates a back button, a navigation title /
 * action mode title / search query, a search button, and a button to open
 * a list of sorting options.
 *
 * The bar will display a back button if the parameter backButtonVisible is
 * is true, or there is an active action mode (i.e. actionModeTitle is not
 * null), or when there is an active search query (i.e. searchQuery is not
 * null). The bar will display an action mode title when there is an ongoing
 * action mode, a search query if there is an active search query and no
 * action mode, or a title otherwise.
 *
 * @param backButtonVisible Whether or not the back button should be
 *     visible to the user due to factors outside the ListActionBar's
 *     scope (e.g. a screen deeper in the navigation hierarchy is being
 *     displayed that the user should be able to back out of.
 * @param onBackButtonClick The callback that will be invoked when the
 *     user taps the back button.
 * @param title The title that will be displayed when there is no action
 *     mode or search query.
 * @param actionModeTitle The title that will be displayed to indicate the
 *     state of an ongoing action mode. If null, the search query or title
 *     will be displayed instead.
 * @param searchQuery The current search query that will be displayed if
 *     not null and actionModeTitle is null.
 * @param onSearchQueryChanged The callback that will be invoked when
 *     the user input should modify the value of the search query.
 * @param onSearchButtonClicked The callback that will be invoked when
 *     the user clicks the search button. Typically this should set the
 *     search query to an empty string if it is already null so that
 *     the search query entry will appear, or set it to null if it is
 *     not null so that the search query entry will be closed.
 * @param sortOption A value of the type parameter that indicates the
 *     currently selected sort option.
 * @param sortOptionNameFunc A callback that should return a string
 *     representation of the provided value of the enum type parameter T.
 */
@ExperimentalComposeUiApi
@ExperimentalAnimationApi
@ExperimentalAnimationGraphicsApi
@Composable
inline fun <reified T : Enum<T>>ListActionBar(
    backButtonVisible: Boolean = false,
    noinline onBackButtonClick: () -> Unit,
    title: String,
    actionModeTitle: String?,
    searchQuery: String?,
    noinline onSearchQueryChanged: (String) -> Unit,
    noinline onSearchButtonClicked: () -> Unit,
    sortOption: T,
    crossinline onSortOptionChanged: (T) -> Unit,
    crossinline sortOptionNameFunc: @Composable (T) -> String,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.fillMaxWidth(1f).height(56.dp)
                       .background(primaryColorHorizontalGradient())
){
    val contentTint = MaterialTheme.colors.onPrimary

    val backButtonVisible = backButtonVisible || actionModeTitle != null || searchQuery != null
    AnimatedContent(backButtonVisible, transitionSpec = { fadeIn(tween()) with fadeOut(tween()) }) {
        if (it) IconButton(onClick = onBackButtonClick) {
                    Icon(imageVector = Icons.Default.ArrowBack,
                         tint = contentTint,
                         contentDescription = stringResource(R.string.back_description))
                }
        else Spacer(Modifier.width(24.dp))
    }

    val searchQueryIsVisible = searchQuery != null && actionModeTitle == null
    Crossfade(searchQueryIsVisible, Modifier.weight(1f)) {
        if (it) AutoFocusUnderlinedSearchQuery(query = searchQuery ?: "",
                                               onQueryChanged = onSearchQueryChanged)
        else Text(text = actionModeTitle ?: title,
                  color = contentTint,
                  style = MaterialTheme.typography.h6)
    }

    val animatedSearchIcon = animatedVectorResource(R.drawable.search_to_close)
    IconButton(onClick = onSearchButtonClicked) {
        Icon(painter = animatedSearchIcon.painterFor(searchQuery != null),
             tint = contentTint,
             contentDescription = stringResource(R.string.search_description))
    }

    var sortMenuShown by remember {mutableStateOf(false) }
    IconButton(onClick = { sortMenuShown = !sortMenuShown }) {
        Icon(imageVector = Icons.Default.Sort,
             tint = contentTint,
             contentDescription = stringResource(R.string.sort_options_button_description))
        EnumDropDownMenu(value = sortOption,
                         onValueChanged = onSortOptionChanged,
                         nameFunc = sortOptionNameFunc,
                         expanded = sortMenuShown,
                         onDismissRequest = { sortMenuShown = false })
    }
}

/**
 * A DropdownMenu that displays an option for each value of the parameter enum
 * type, and a checked or unchecked radio button besides each to show the
 * currently selected value.
 *
 * @param expanded Whether the dropdown menu is displayed
 * @param value The currently selected enum value
 * @param onValueChanged The callback that will be invoked when the user taps an item.
 * @param nameFunc A function that will return a string representation of the
 *                 provided value of the parameter enum type T.
 * @param onDismissRequest The callback that will be invoked when the menu should
 *                         be dismissed.
 */
@Composable inline fun <reified T : Enum<T>>EnumDropDownMenu(
    expanded: Boolean,
    value: T,
    crossinline onValueChanged: (T) -> Unit,
    crossinline nameFunc: @Composable (T) -> String,
    noinline onDismissRequest: () -> Unit
) = DropdownMenu(expanded, onDismissRequest) {
    enumValues<T>().forEach {
        DropdownMenuItem({ onValueChanged(it); onDismissRequest() }) {
            Text(text = nameFunc(it), style = MaterialTheme.typography.button)
            val vector = if (value == it) Icons.Default.RadioButtonChecked
                         else             Icons.Default.RadioButtonUnchecked
            Spacer(Modifier.weight(1f))
            Icon(vector, nameFunc(it), Modifier.size(36.dp).padding(8.dp))
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
@ExperimentalComposeUiApi
@Composable fun AutoFocusUnderlinedSearchQuery(
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
                Divider(color = MaterialTheme.colors.onPrimary, thickness = (1.5).dp,
                        modifier = Modifier.padding(0.dp, 26.dp, 0.dp, 0.dp))
            }
        }
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@ExperimentalComposeUiApi
@ExperimentalAnimationGraphicsApi
@ExperimentalAnimationApi
@Preview @Composable fun PreviewRecyclerViewActionBar() =
    SoundObservatoryTheme {
        var title = stringResource(R.string.app_name)
        var actionModeTitle by remember { mutableStateOf<String?>(null) }
        var searchQuery by remember { mutableStateOf<String?>(null) }
        var sortOption by remember { mutableStateOf(Track.Sort.NameAsc) }

        ListActionBar(
            backButtonVisible = false, onBackButtonClick = { },
            title, actionModeTitle, searchQuery,
            onSearchQueryChanged = { searchQuery = it },
            sortOption = sortOption,
            onSortOptionChanged = { sortOption = it},
            sortOptionNameFunc = { string(it) },
            onSearchButtonClicked = {
                searchQuery = if (searchQuery == null) "" else null
            })
    }

