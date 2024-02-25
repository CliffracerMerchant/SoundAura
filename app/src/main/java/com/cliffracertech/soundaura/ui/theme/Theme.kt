/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.google.accompanist.systemuicontroller.rememberSystemUiController

private val lightColorPalette = lightColors(
    primary = LightThemePrimary,
    primaryVariant = LightThemePrimaryVariant,
    secondary = LightThemeSecondary,
    secondaryVariant = LightThemeSecondaryVariant,
    background = LightBackground,
    surface = LightSurface,
    error = LightError,
    onBackground = LightOnSurface,
    onSurface = LightOnSurface,
    onPrimary = LightOnPrimary)

private val darkColorPalette = darkColors(
    primary = DarkThemePrimary,
    primaryVariant = DarkThemePrimaryVariant,
    secondary = DarkThemeSecondary,
    secondaryVariant = DarkThemeSecondaryVariant,
    background = DarkBackground,
    surface = DarkSurface,
    error = DarkError,
    onBackground = DarkOnSurface,
    onSurface = DarkOnSurface,
    onPrimary = DarkOnPrimary)

@Composable fun SoundAuraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val systemUiController = rememberSystemUiController()
    systemUiController.setSystemBarsColor(Color.Transparent)

    MaterialTheme(
        colors = if (darkTheme) darkColorPalette
                 else           lightColorPalette,
        typography = Typography,
        shapes = Shapes,
        content = content)
}