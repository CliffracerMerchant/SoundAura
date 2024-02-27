/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.appbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.cliffracertech.soundaura.rememberMutableStateOf
import com.cliffracertech.soundaura.ui.minTouchTargetSize
import kotlinx.collections.immutable.ImmutableList

/** Compose a [Row] with a gradient background and vertically centered
 * content, while providing the current theme's onPrimary color as the
 * [LocalContentColor]. */
@Composable
fun GradientToolBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val gradStart = MaterialTheme.colorScheme.secondaryContainer
    val gradEnd = MaterialTheme.colorScheme.tertiaryContainer
    val gradient = remember(gradStart, gradEnd) {
        Brush.horizontalGradient(listOf(gradStart, gradEnd))
    }
    Row(modifier.fillMaxWidth().background(gradient)
                .statusBarsPadding().height(56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val color = MaterialTheme.colorScheme.onSecondaryContainer
        CompositionLocalProvider(LocalContentColor provides color) { content() }
    }
}

/**
 * A [DropdownMenu] that displays a [DropdownMenuItem] for each option in
 * [options] when [expanded] is true. Each option's item will show its name
 * and a radio button that will be filled if its index matches [currentIndex].
 * Clicks on options will use [onOptionClick] as a callback. [onDismissRequest]
 * will be used as the same-named DropdownMenu property.
 *
 * Content other than the list of options can also be added to the menu via the
 * [otherContent] parameter. The additional content will be shown either before
 * or after the main options according to the value of [showOtherContentFirst].
 */
@Composable fun RadioDropdownMenu(
    expanded: Boolean,
    options: ImmutableList<String>,
    currentIndex: Int,
    onOptionClick: (Int) -> Unit,
    onDismissRequest: () -> Unit,
    showOtherContentFirst: Boolean = false,
    otherContent: @Composable ColumnScope.() -> Unit = {},
) = DropdownMenu(expanded, onDismissRequest) {
    if (showOtherContentFirst)
        otherContent()
    options.forEachIndexed { index, name ->
        DropdownMenuItem(
            text = { Text(name) },
            onClick = { onOptionClick(index) },
            trailingIcon = {
                val vector = if (index == currentIndex)
                                 Icons.Default.RadioButtonChecked
                             else Icons.Default.RadioButtonUnchecked
                Icon(vector, name, Modifier.size(36.dp).padding(8.dp))
            })
    }
    if (!showOtherContentFirst)
        otherContent()
}

/**
 * A search query that displays an underline as a background.
 *
 * @param state A [SearchQueryViewState] instance
 * @param modifier The [Modifier] used for the entire search query
 */
@Composable fun SearchQueryView(
    state: SearchQueryViewState,
    modifier: Modifier = Modifier
) {
    // Remembering the last non-null query allows it to fade out when the query
    // changes from a non-null value to null, instead of abruptly disappearing
    var query by rememberMutableStateOf(state.query ?: "")
    state.query?.let { query = it }
    BasicTextField(
        value = query,
        onValueChange = state.onQueryChange,
        textStyle = MaterialTheme.typography.titleMedium,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = modifier.minTouchTargetSize(),
        singleLine = true,
    ) { innerTextField ->
        Column(verticalArrangement = Arrangement.Center) {
            innerTextField()
            HorizontalDivider(
                thickness = 1.5.dp,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}