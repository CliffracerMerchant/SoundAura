/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.MaterialTheme
import androidx.compose.material.SnackbarDuration
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.rememberScaffoldState
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cliffracertech.soundaura.ui.theme.SoundAuraTheme
import com.google.accompanist.insets.LocalWindowInsets
import com.google.accompanist.insets.ProvideWindowInsets
import com.google.accompanist.insets.navigationBarsHeight
import com.google.accompanist.insets.rememberInsetsPaddingValues
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.scopes.ActivityRetainedScoped
import javax.inject.Inject

@ActivityRetainedScoped
class MainActivityNavigationState @Inject constructor() {
    var showingAppSettings by mutableStateOf(false)
}

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    messageHandler: MessageHandler,
    private val navigationState: MainActivityNavigationState,
) : ViewModel() {
    val messages = messageHandler.messages
    val showingAppSettings get() = navigationState.showingAppSettings

    fun onBackButtonClick() =
        if (showingAppSettings) {
            navigationState.showingAppSettings = false
            true
        } else false
}

val LocalWindowSizeClass = compositionLocalOf {
    WindowSizeClass.calculateFromSize(DpSize(0.dp, 0.dp))
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var boundPlayerService by mutableStateOf<PlayerService.Binder?>(null)
    private val viewModel: MainActivityViewModel by viewModels()

    private val serviceConnection = object: ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            boundPlayerService = service as? PlayerService.Binder
        }
        override fun onServiceDisconnected(name: ComponentName?) {}
    }

    override fun onResume() {
        super.onResume()
        val intent = Intent(this, PlayerService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onPause() {
        super.onPause()
        unbindService(serviceConnection)
        boundPlayerService = null
    }

    @Deprecated("Replace with OnBackPressedDispatcher")
    override fun onBackPressed() {
        @Suppress("DEPRECATION")
        if (!viewModel.onBackButtonClick())
            super.onBackPressed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentWithTheme {
            val scaffoldState = rememberScaffoldState()

            MessageDisplayer(scaffoldState.snackbarHostState)

            val windowWidthSizeClass = LocalWindowSizeClass.current.widthSizeClass
            val widthIsConstrained = windowWidthSizeClass == WindowWidthSizeClass.Compact
            val insets = LocalWindowInsets.current

            com.google.accompanist.insets.ui.Scaffold(
                scaffoldState = scaffoldState,
                topBar = {
                    val padding = rememberInsetsPaddingValues(
                        // The top bar's top padding is set internally using
                        // statusBarsPadding, so we have to use applyTop = false
                        // here to prevent the top padding from being doubled.
                        insets = insets.systemBars, applyTop = false, applyBottom = false)
                    SoundAuraActionBar(
                        onUnhandledBackButtonClick = ::onBackPressed,
                        modifier = Modifier.padding(padding))
                }, bottomBar = {
                    Spacer(Modifier.navigationBarsHeight().fillMaxWidth())
                }, floatingActionButton = {
                    // The floating action buttons are added in the content
                    // section instead to have more control over their placement.
                    // A spacer is added here so that snack bars still appear
                    // above the floating action buttons. 48dp was arrived at
                    // by starting from the FAB size of 56dp and adjusting
                    // downward by 8dp due to the inherent snack bar padding.
                    if (widthIsConstrained)
                        Spacer(Modifier.height(48.dp).fillMaxWidth())
                }, content = {
                    val padding = rememberInsetsPaddingValues(
                        insets = insets.systemBars,
                        additionalStart = 8.dp,
                        // The 56dp is added here manually for the action bar's height.
                        additionalTop = 8.dp + 56.dp,
                        additionalEnd = 8.dp,
                        additionalBottom = 8.dp)
                    MainContent(widthIsConstrained, padding)
                })
        }
    }

    /** Read the app's theme from a SettingsViewModel instance
     * and compose the provided content using the theme. */
    private fun setContentWithTheme(
        parent: CompositionContext? = null,
        content: @Composable () -> Unit
    ) = setContent(parent) {
        val settingsViewModel: SettingsViewModel = viewModel()
        val themePreference = settingsViewModel.appTheme
        val systemIsUsingDarkTheme = isSystemInDarkTheme()
        val useDarkTheme by remember(themePreference, systemIsUsingDarkTheme) {
            derivedStateOf {
                themePreference == AppTheme.Dark ||
                (themePreference == AppTheme.UseSystem && systemIsUsingDarkTheme)
            }
        }

        val uiController = rememberSystemUiController()
        LaunchedEffect(useDarkTheme) {
            // For some reason the status bar icons get reset
            // to a light color when the theme is changed, so
            // this effect needs to run after every theme change
            // instead of just when the app starts.
            uiController.setStatusBarColor(Color.Transparent, true)
            uiController.setNavigationBarColor(Color.Transparent, true)
        }
        SoundAuraTheme(useDarkTheme) {
            val windowSizeClass = calculateWindowSizeClass(this)
            CompositionLocalProvider(LocalWindowSizeClass provides windowSizeClass) {
                ProvideWindowInsets { content() }
            }
        }
    }

    /** Compose a message handler that will read messages emitted from a
     * MainActivityViewModel's messages member and display them using snack bars.*/
    @Composable private fun MessageDisplayer(
        snackbarHostState: SnackbarHostState
    ) {
        val dismissLabel = stringResource(R.string.dismiss)

        LaunchedEffect(Unit) {
            viewModel.messages.collect { message ->
                val context = this@MainActivity
                val messageText = message.stringResource.resolve(context)
                val actionLabel = message.actionStringResource?.resolve(context)
                                                    ?: dismissLabel.uppercase()
                snackbarHostState.showSnackbar(
                    message = messageText,
                    actionLabel = actionLabel,
                    duration = SnackbarDuration.Short)
            }
        }
    }

    @Composable private fun MainContent(
        widthIsConstrained: Boolean,
        padding: PaddingValues,
    ) = BoxWithConstraints(Modifier.fillMaxSize()) {
        val showingAppSettings = viewModel.showingAppSettings
        val ld = LocalLayoutDirection.current
        // The track list state is remembered here so that the
        // scrolling position will not be lost if the user
        // navigates to the app settings screen and back.
        val trackListState = rememberLazyListState()

        SlideAnimatedContent(
            targetState = showingAppSettings,
            leftToRight = !showingAppSettings
        ) { showingAppSettingsScreen ->
            if (showingAppSettingsScreen)
                AppSettings(Modifier.fillMaxSize(), padding)
            else {
                // The track list's padding must be adjusted depending on the placement of the FABs.
                val trackListPadding = remember(padding, widthIsConstrained) {
                    PaddingValues(
                        start = padding.calculateStartPadding(ld),
                        top = padding.calculateTopPadding(),
                        end = padding.calculateEndPadding(ld) +
                              if (widthIsConstrained) 0.dp else 64.dp,
                        bottom = padding.calculateBottomPadding() +
                                 if (widthIsConstrained) 64.dp else 0.dp)
                }
                StatefulTrackList(
                    padding = trackListPadding,
                    state = trackListState,
                    onVolumeChange = { uri, volume ->
                        boundPlayerService?.setTrackVolume(uri, volume)
                    })
            }
        }
        var showingPresetSelector by remember { mutableStateOf(false) }
        FloatingMediaController(
            visible = !showingAppSettings,
            showingPresetSelector = showingPresetSelector,
            onActivePresetClick = { showingPresetSelector = true },
            onCloseButtonClick = { showingPresetSelector = false },
            padding = padding,
            alignToEnd = !widthIsConstrained)


        val density = LocalDensity.current
        // Different stiffnesses are used so that the add button
        // moves in a swooping movement instead of a linear one
        val addPresetButtonXOffset by animateFloatAsState(
            targetValue = if (!showingPresetSelector) 0f
                          else with(density) { (-12).dp.toPx() })
        val addPresetButtonYOffset by animateFloatAsState(
            targetValue = if (!showingPresetSelector) 0f
                          else with(density) { (-12).dp.toPx() },
            animationSpec = spring(stiffness = Spring.StiffnessLow))
        AddTrackButton(
            visible = !showingAppSettings,
            modifier = Modifier
                .padding(padding)
                .graphicsLayer {
                    translationX = addPresetButtonXOffset
                    translationY = addPresetButtonYOffset
                })
    }

    @Composable private fun FloatingMediaController(
        visible: Boolean,
        showingPresetSelector: Boolean,
        onActivePresetClick: () -> Unit,
        onCloseButtonClick: () -> Unit,
        padding: PaddingValues,
        alignToEnd: Boolean
    ) {
        val alignment = if (alignToEnd) Alignment.TopEnd
                        else            Alignment.BottomStart
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(delayMillis = 75)) + scaleIn(overshootTweenSpec(delay = 75)),
            exit = fadeOut(tween(delayMillis = 125)) + scaleOut(anticipateTweenSpec(delay = 75))
        ) {
            val list = remember { mutableStateListOf(
                Preset("Super duper extra really long preset name 0"),
                Preset("Super duper extra really long preset name 1"),
                Preset("Super duper extra really long preset name 2"),
                Preset("Super duper extra really long preset name 3")
            )}
            var currentPreset by remember { mutableStateOf(list.first()) }
            val currentPresetIsModified = true
            val isPlaying = boundPlayerService?.isPlaying ?: false
            var newPresetName by rememberSaveable { mutableStateOf("")}
            val startColor = MaterialTheme.colors.primaryVariant
            val endColor = MaterialTheme.colors.secondaryVariant
            val backgroundBrush = remember(startColor, endColor) {
                Brush.horizontalGradient(listOf(startColor, endColor))
            }
            FloatingMediaController(
                modifier = Modifier.padding(padding),
                showingPresetSelector = showingPresetSelector,
                backgroundBrush = backgroundBrush,
                alignment = alignment as BiasAlignment,
                isPlaying = isPlaying,
                onPlayPauseClick = { boundPlayerService?.toggleIsPlaying() },
                activePreset = currentPreset,
                activePresetIsModified = currentPresetIsModified,
                onActivePresetClick = onActivePresetClick,
                presetListProvider = { list },
                onPresetRenameRequest = { preset, newName ->
                    list.replaceAll { if (it != preset) it
                    else Preset(newName) }
                },
                onPresetOverwriteRequest = {},
                onPresetDeleteRequest = list::remove,
                onCloseButtonClick = onCloseButtonClick,
                onPresetClick = { currentPreset = it })

            val nameValidator = remember { { newName: String -> when {
                newName.isBlank() ->
                    "The preset's name must not be blank"
                newName.length % 2 == 1 ->
                    "New preset names must have an even number of characters"
                else -> null
            }}}
            val nameValidatorMessage by remember { derivedStateOf {
                nameValidator(newPresetName)
            }}
        }
    }

    /** Compose an add track button at the bottom end edge of the screen,
     * which is conditionally visible depending on the value of [visible],
     * with padding equal to the parameter [padding]. */
    @Composable private fun BoxScope.AddTrackButton(
        visible: Boolean,
        modifier: Modifier,
    ) {
        AnimatedVisibility( // add track button
            visible = visible,
            modifier = modifier.align(Alignment.BottomEnd),
            enter = fadeIn(tween()) + scaleIn(overshootTween()),
            exit = fadeOut(tween(delayMillis = 50)) + scaleOut(anticipateTween()),
        ) {
            AddLocalFilesButton(MaterialTheme.colors.secondaryVariant)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?) =
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                boundPlayerService?.toggleIsPlaying()
                true
            } KeyEvent.KEYCODE_MEDIA_PLAY -> {
                if (boundPlayerService?.isPlaying == false) {
                    boundPlayerService?.toggleIsPlaying()
                    true
                } else false
            } KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                if (boundPlayerService?.isPlaying == true) {
                    boundPlayerService?.toggleIsPlaying()
                    true
                } else false
            } KeyEvent.KEYCODE_MEDIA_STOP -> {
                if (boundPlayerService?.isPlaying == true) {
                    boundPlayerService?.toggleIsPlaying()
                    true
                } else false
            }
            else -> super.onKeyDown(keyCode, event)
        }
}