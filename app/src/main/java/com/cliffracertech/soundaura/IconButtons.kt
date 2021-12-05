/* This file is part of SoundAura, which is released under the terms of the Apache
 * License 2.0. See license.md in the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** A collection of simple icon buttons, to make larger composables that need to
 * contain icon buttons more readable. */

/** A simple back arrow IconButton for when only the onClick needs changed. */
@Composable fun BackButton(onClick: () -> Unit) = IconButton(onClick) {
    Icon(Icons.Default.ArrowBack, stringResource(R.string.back_description))
}

/** A simple settings IconButton for when only the onClick needs changed. */
@Composable fun SettingsButton(onClick: () -> Unit) = IconButton(onClick) {
    Icon(Icons.Default.Settings, stringResource(R.string.settings_description))
}