/* This file is part of SoundObservatory, which is released under the Apache License 2.0. See
 * license.md in the project's root directory or use an internet search engine to see the full license. */
package com.cliffracertech.soundobservatory.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.google.accompanist.systemuicontroller.rememberSystemUiController

private val DarkColorPalette = darkColors(
    primary = DarkThemeGradientStart,
    primaryVariant = DarkThemeGradientEnd,
    secondary = DarkThemeSecondary,
    secondaryVariant = DarkThemeSecondary,
    background = DarkBackground,
    surface = DarkSurface,
    onBackground = DarkOnSurface,
    onSurface = DarkOnSurface,
    onPrimary = DarkOnPrimary,
)

private val LightColorPalette = lightColors(
    primary = LightThemeGradientStart,
    primaryVariant = LightThemeGradientEnd,
    secondary = LightThemeSecondary,
    secondaryVariant = LightThemeSecondary,
    background = LightBackground,
    surface = LightSurface,
    onBackground = LightOnSurface,
    onSurface = LightOnSurface,
    onPrimary = LightOnPrimary,
)

@Composable
fun SoundObservatoryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable() () -> Unit
) {
    val systemUiController = rememberSystemUiController()
    systemUiController.setSystemBarsColor(Color.Transparent)

    val colors = if (darkTheme) DarkColorPalette
                 else           LightColorPalette
    MaterialTheme(
        colors = colors,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}