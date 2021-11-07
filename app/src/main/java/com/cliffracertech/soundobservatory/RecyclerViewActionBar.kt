/* This file is part of SoundObservatory, which is released under the Apache License 2.0. See
 * license.md in the project's root directory or use an internet search engine to see the full license. */
package com.cliffracertech.soundobservatory

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
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

@ExperimentalAnimationApi
@ExperimentalAnimationGraphicsApi
@OptIn(ExperimentalComposeUiApi::class)
@Composable
inline fun <reified T : Enum<T>>RecyclerViewActionBar(
    backButtonVisible: Boolean = false,
    noinline backButtonOnClick: () -> Unit,
    title: String,
    actionModeTitle: MutableState<String?>,
    searchQuery: MutableState<String?>,
    sortOption: MutableState<T>
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
        .fillMaxWidth(1f)
        .height(56.dp)
        .background(primaryColorHorizontalGradient())
){
    val contentTint = MaterialTheme.colors.onPrimary

    val backButtonVisible = backButtonVisible ||
                            actionModeTitle.value != null ||
                            searchQuery.value != null
    AnimatedContent(backButtonVisible) {
        if (it) IconButton(onClick = backButtonOnClick) {
                    Icon(imageVector = Icons.Default.ArrowBack,
                    tint = contentTint,
                    contentDescription = "Back")
                }
        else Spacer(Modifier.width(24.dp))
    }

    val searchQueryIsVisible = searchQuery.value != null && actionModeTitle.value == null
    Crossfade(searchQueryIsVisible, Modifier.weight(1f)) {
        if (it) AutoFocusUnderlinedSearchQuery(query = searchQuery.value ?: "",
                                               onQueryChanged = { searchQuery.value = it })
        else Text(text = actionModeTitle.value ?: title,
                  color = contentTint, style = MaterialTheme.typography.h6)
    }

    val animatedSearchIcon = animatedVectorResource(R.drawable.search_to_close)
    IconButton(onClick = {
        searchQuery.value = if (searchQuery.value != null) null else ""
    }) {
        Icon(painter = animatedSearchIcon.painterFor(searchQuery.value != null),
             tint = contentTint, contentDescription = "Search")
    }

        var sortMenuShown by remember {mutableStateOf(false) }
        IconButton(onClick = { sortMenuShown = !sortMenuShown }) {
            Icon(imageVector = Icons.Default.Sort,
                 tint = contentTint,
                 contentDescription = "Change sort method")
            EnumDropDownMenu(value = sortOption,
                             onValueChangedListener = { },
                             expanded = sortMenuShown,
                             onDismissRequest = { sortMenuShown = false })
        }
}

@Composable
inline fun <reified T : Enum<T>>EnumDropDownMenu(
    value: MutableState<T>,
    crossinline onValueChangedListener: (T) -> Unit,
    expanded: Boolean,
    noinline onDismissRequest: () -> Unit) =
    DropdownMenu(expanded, onDismissRequest) {
        enumValues<T>().forEach {
            DropdownMenuItem(onClick = {
                value.value = it
                onValueChangedListener(it)
                onDismissRequest()
            }) {
                Text(text = it.toString(), style = MaterialTheme.typography.button)
                val vector = if (value.value == it) Icons.Default.RadioButtonChecked
                             else                   Icons.Default.RadioButtonUnchecked
                Spacer(Modifier.weight(1f))
                Icon(vector, it.toString(), Modifier.size(36.dp).padding(8.dp))
            }
        }
    }

@OptIn(ExperimentalComposeUiApi::class)
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
        modifier = Modifier.focusRequester(focusRequester)
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
    DisposableEffect(Unit) {
        focusRequester.requestFocus()
        onDispose { }
    }
}


@ExperimentalAnimationApi
@OptIn(ExperimentalAnimationGraphicsApi::class)
@Preview @Composable fun PreviewRecyclerViewActionBar() =
    SoundObservatoryTheme {
        var title = stringResource(R.string.app_name)
        var actionModeTitle = remember { mutableStateOf<String?>(null) }
        var searchQuery = remember { mutableStateOf<String?>(null) }
        var sortOption = remember { mutableStateOf(Track.Sort.NameAsc) }

        RecyclerViewActionBar(backButtonVisible = false, backButtonOnClick = { },
                              title, actionModeTitle, searchQuery, sortOption)
    }

