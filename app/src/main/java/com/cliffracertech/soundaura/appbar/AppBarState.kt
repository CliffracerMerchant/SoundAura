/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.appbar

import android.content.Context
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cliffracertech.soundaura.appbar.SearchQueryViewState.Icon
import kotlinx.collections.immutable.ImmutableList

/**
 * A holder of state and callbacks related to a search query and a search button.
 *
 * Consumers of the SearchQueryViewState should show the search query when the
 * value of the property [query] is not null, and connect desired changes to
 * the search query (e.g. through [TextField.onValueChange]) to the property
 * [onQueryChange]. The displayed search button should show either a search
 * icon or a close icon according to the [Icon] value of the property [icon],
 * and the property [onButtonClick] should be used as its onClick action.
 *
 * @param getQuery A getter for the active search query.
 *     This should return null if there is no active search.
 * @param onQueryChange A setter for the active search query
 * @param onButtonClick The callback that will be used as the
 *     search button's onClick action
 * @param getIcon A getter that returns the
 *     [SearchQueryViewState.Icon] that the search button should display
 */
class SearchQueryViewState(
    private val getQuery: () -> String?,
    val onQueryChange: (String) -> Unit,
    val onButtonClick: () -> Unit,
    private val getIcon: () -> Icon,
) {
    enum class Icon { Search, Close }
    val query get() = getQuery()
    val icon get() = getIcon()
}

/**
 * A holder of state and callbacks related to a sort
 * button and an associated sorting options popup menu.
 *
 * Consumers of the SortMenuState should display a change sort button that uses
 * the property [onButtonClick] as its onClick action. It should also have an
 * associated popup menu whose expanded state should match the property
 * [showingPopup]. If [showingPopup] is true, the popup menu should show a list
 * of sorting options whose names match those in the [ImmutableList] returned
 * when the [optionNames] property is invoked. The index of the sorting option
 * that should be shown as the current one (e.g. with a filled radio button)
 * is obtained through the property [currentOptionIndex]. The popup menu's
 * onDismissRequest should be set to the provided [onPopupDismissRequest].
 * Clicks on sorting options within the popup menu should be connected to the
 * [onOptionClick] callback.
 */
class SortMenuState(
    val optionNames: @Composable (Context) -> ImmutableList<String>,
    private val getCurrentOptionIndex: () -> Int,
    onOptionClick: (Int) -> Unit,
) {
    val currentOptionIndex get() = getCurrentOptionIndex()

    var showingPopup by mutableStateOf(false)
        private set
    val onPopupDismissRequest = { showingPopup = false }
    val onButtonClick = { showingPopup = !showingPopup }
    val onOptionClick = { index: Int ->
        onPopupDismissRequest()
        onOptionClick(index)
    }
}

/** A state holder for a switch UI component. The switch's current checked
 * state can be accessed via the property [checked]. The property [onClick]
 * should be used as the onClick action for the switch. */
class SwitchState(
    private val getChecked: () -> Boolean,
    val onClick: () -> Unit,
) {
    val checked get() = getChecked()
}