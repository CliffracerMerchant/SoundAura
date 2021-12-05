/* This file is part of SoundAura, which is released under the terms of the Apache
 * License 2.0. See license.md in the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.compose.foundation.background
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.res.stringResource

/** A modifier that sets the background to be a MaterialTheme.shapes.large
 * shape filled in with a MaterialTheme.colors.surface color. */
fun Modifier.largeSurfaceBackground() = composed {
    background(MaterialTheme.colors.surface, MaterialTheme.shapes.large)
}

/** Return a radio button icon with its checked state set according to the value of @param checked. */
@Composable fun RadioButton(checked: Boolean, modifier: Modifier) {
    val vector = if (checked) Icons.Default.RadioButtonChecked
                 else         Icons.Default.RadioButtonUnchecked
    val desc = if (checked) stringResource(R.string.checked_description)
               else         stringResource(R.string.unchecked_description)
    Icon(vector, desc, modifier)
}

/** A simple back arrow IconButton for when only the onClick needs changed. */
@Composable fun BackButton(onClick: () -> Unit) = IconButton(onClick) {
    Icon(Icons.Default.ArrowBack, stringResource(R.string.back_description))
}

/** A simple settings IconButton for when only the onClick needs changed. */
@Composable fun SettingsButton(onClick: () -> Unit) = IconButton(onClick) {
    Icon(Icons.Default.Settings, stringResource(R.string.settings_description))
}