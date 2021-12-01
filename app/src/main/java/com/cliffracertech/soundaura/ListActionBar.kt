/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.graphics.res.animatedVectorResource
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

@Composable fun primaryColorHorizontalGradient() =
    Brush.horizontalGradient(listOf(MaterialTheme.colors.primary,
                                    MaterialTheme.colors.primaryVariant))

/**
 * A bar that is suitable to be used as a top action bar when displaying a
 * list of items. The bar integrates a navigation title / search query, a
 * search button, a button to open a list of sorting options, and a settings
 * button. The bar will display a back button if there is an active search
 * query (i.e. searchQuery is not null). The title will be replaced by the
 * search query if it is not null.
 *
 * @param title The title that will be displayed when there is no search query.
 * @param searchQuery The current search query that will be displayed if not null.
 * @param onSearchQueryChanged The callback that will be invoked when
 *     the user input should modify the value of the search query.
 * @param onSearchButtonClicked The callback that will be invoked when
 *     the user clicks the search button. Typically this should set the
 *     search query to an empty string if it is already null so that
 *     the search query entry will appear, or set it to null if it is
 *     not null so that the search query entry will be closed.
 * @param sortOptions An array of all possible sorting enum values
 *                    (usually accessed with enumValues<>()
 * @param sortOption A value of the type parameter that indicates
 *                   the currently selected sort option.
 * @param sortOptionNameFunc A function that should return a string
 *     representation of the provided value of the enum type parameter T.
 * @param onSettingsButtonClicked A callback that will be invoked when
 *     the user clicks the settings button.
 */
@Composable
fun <T> ListActionBar(
    title: String,
    searchQuery: String?,
    onSearchQueryChanged: (String?) -> Unit,
    onSearchButtonClicked: () -> Unit,
    sortOptions: Array<T>,
    sortOption: T,
    onSortOptionChanged: (T) -> Unit,
    sortOptionNameFunc: @Composable (T) -> String,
    onSettingsButtonClicked: () -> Unit,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.fillMaxWidth(1f).height(56.dp)
                       .background(primaryColorHorizontalGradient())
){
    val contentTint = MaterialTheme.colors.onPrimary

    AnimatedContent(
        targetState = searchQuery != null,
        transitionSpec = { fadeIn(tween()) with fadeOut(tween()) }
    ) { backButtonVisible ->
        if (!backButtonVisible)
            Spacer(Modifier.width(24.dp))
        else IconButton(onClick = { onSearchQueryChanged(null) }) {
            Icon(imageVector = Icons.Default.ArrowBack, tint = contentTint,
                 contentDescription = stringResource(R.string.back_description))
        }
    }

    Crossfade(searchQuery != null, Modifier.weight(1f)) {
        if (it) AutoFocusSearchQuery(query = searchQuery ?: "",
                                     onQueryChanged = onSearchQueryChanged)
        else Text(text = title, color = contentTint,
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
        Icon(imageVector = Icons.Default.Sort, tint = contentTint,
             contentDescription = stringResource(R.string.sort_options_button_description))
        EnumDropDownMenu(
            expanded = sortMenuShown,
            values = sortOptions,
            value = sortOption,
            onValueChanged = onSortOptionChanged,
            nameFunc = sortOptionNameFunc,
            onDismissRequest = { sortMenuShown = false })
    }

    IconButton(onClick = onSettingsButtonClicked) {
        Icon(imageVector = Icons.Default.Settings, tint = contentTint,
             contentDescription = stringResource(R.string.settings_description))
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
 * @param value The currently selected enum value
 * @param onValueChanged The callback that will be invoked when the user taps an item.
 * @param nameFunc A function that will return a string representation of the
 *                 provided value of the parameter enum type T.
 * @param onDismissRequest The callback that will be invoked when the menu should
 *                         be dismissed.
 */
@Composable fun <T> EnumDropDownMenu(
    expanded: Boolean,
    values: Array<T>,
    value: T,
    onValueChanged: (T) -> Unit,
    nameFunc: @Composable (T) -> String,
    onDismissRequest: () -> Unit
) = DropdownMenu(expanded, onDismissRequest) {
    values.forEach {
        DropdownMenuItem({ onValueChanged(it); onDismissRequest() }) {
            Text(text = nameFunc(it), style = MaterialTheme.typography.button)
            Spacer(Modifier.weight(1f))
            val vector = if (value == it) Icons.Default.RadioButtonChecked
                         else             Icons.Default.RadioButtonUnchecked
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
                Divider(color = MaterialTheme.colors.onPrimary, thickness = (1.5).dp,
                        modifier = Modifier.padding(0.dp, 26.dp, 0.dp, 0.dp))
            }
        }
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Preview @Composable fun PreviewRecyclerViewActionBar() =
    SoundAuraTheme {
        val title = stringResource(R.string.app_name)
        var searchQuery by remember { mutableStateOf<String?>(null) }
        var sortOption by remember { mutableStateOf(Track.Sort.NameAsc) }

        ListActionBar(
            title, searchQuery,
            onSearchQueryChanged = { searchQuery = it },
            sortOption = sortOption,
            sortOptions = enumValues(),
            onSortOptionChanged = { sortOption = it},
            sortOptionNameFunc = { composeString(it) },
            onSearchButtonClicked = {
                searchQuery = if (searchQuery == null) "" else null
            }, onSettingsButtonClicked = { })
    }

