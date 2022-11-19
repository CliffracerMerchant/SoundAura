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
import androidx.compose.foundation.gestures.Orientation
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
    var showingPresetSelector by mutableStateOf(false)
}

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    messageHandler: MessageHandler,
    private val navigationState: MainActivityNavigationState,
) : ViewModel() {
    val messages = messageHandler.messages
    val showingAppSettings get() = navigationState.showingAppSettings
    val showingPresetSelector get() = navigationState.showingPresetSelector

    fun onBackButtonClick() = when {
        showingAppSettings -> {
            navigationState.showingAppSettings = false
            true
        } showingPresetSelector -> {
            navigationState.showingPresetSelector = false
            true
        } else -> false
    }

    fun onMediaControllerPresetClick() {
        navigationState.showingPresetSelector = true
    }

    fun onPresetSelectorCloseButtonClick() {
        navigationState.showingPresetSelector = false
    }
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
            val windowWidthSizeClass = LocalWindowSizeClass.current.widthSizeClass
            val widthIsConstrained = windowWidthSizeClass == WindowWidthSizeClass.Compact
            val insets = LocalWindowInsets.current

            val scaffoldState = rememberScaffoldState()
            MessageDisplayer(scaffoldState.snackbarHostState)

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
            leftToRight = !showingAppSettings,
            modifier = Modifier.fillMaxSize()
        ) { showingAppSettingsScreen ->
            if (showingAppSettingsScreen)
                AppSettings(padding)
            else {
                // The track list's padding must be adjusted depending on the placement of the FABs.
                val trackListPadding = remember(padding, widthIsConstrained) {
                    paddingValues(padding, ld,
                        additionalEnd = if (widthIsConstrained) 0.dp else 64.dp,
                        additionalBottom = if (widthIsConstrained) 64.dp else 0.dp)
                }
                StatefulTrackList(
                    padding = trackListPadding,
                    state = trackListState,
                    onVolumeChange = { uri, volume ->
                        boundPlayerService?.setTrackVolume(uri, volume)
                    })
            }
        }

        val showingPresetSelector = viewModel.showingPresetSelector
        MediaController(
            visible = !showingAppSettings,
            showingPresetSelector = viewModel.showingPresetSelector,
            onActivePresetClick = viewModel::onMediaControllerPresetClick,
            onCloseButtonClick = viewModel::onPresetSelectorCloseButtonClick,
            padding = padding,
            alignToEnd = !widthIsConstrained)

        val density = LocalDensity.current
        // Different stiffnesses are used so that the add button
        // moves in a swooping movement instead of a linear one
        val addPresetButtonXOffset by animateFloatAsState(
            targetValue = if (!showingPresetSelector) 0f
                          else with(density) { (-16).dp.toPx() },
            animationSpec = spring(stiffness = 600f))
        val addPresetButtonYOffset by animateFloatAsState(
            targetValue = if (!showingPresetSelector) 0f
                          else with(density) { (-16).dp.toPx() },
            animationSpec = spring(stiffness = Spring.StiffnessLow))
        AddTrackButton(
            visible = !showingAppSettings,
            modifier = Modifier.padding(padding).graphicsLayer {
                translationX = addPresetButtonXOffset
                translationY = addPresetButtonYOffset
            }, target = if (showingPresetSelector)
                            AddButtonTarget.Preset
                        else AddButtonTarget.Track)
    }

    @Composable private fun BoxWithConstraintsScope.MediaController(
        visible: Boolean,
        showingPresetSelector: Boolean,
        onActivePresetClick: () -> Unit,
        onCloseButtonClick: () -> Unit,
        padding: PaddingValues,
        alignToEnd: Boolean
    ) {
        val density = LocalDensity.current
        val ld = LocalLayoutDirection.current

        val contentAreaSize = remember(padding, alignToEnd) {
            val screenWidthDp = with(density) { constraints.maxWidth.toDp() }
            val startPadding = padding.calculateStartPadding(ld)
            val endPadding = padding.calculateEndPadding(ld)
            val width = screenWidthDp - startPadding - endPadding

            val screenHeightDp = with(density) { constraints.maxHeight.toDp() }
            val topPadding = padding.calculateTopPadding()
            val bottomPadding = padding.calculateBottomPadding()
            val height = screenHeightDp - topPadding - bottomPadding

            DpSize(width, height)
        }
        val collapsedSize = remember(padding, alignToEnd) {
            // The goal is to have the media controller bar have such a
            // width/height that the play/pause icon is centered in the
            // content area's width in portrait mode, or centered in the
            // content area's height in landscape mode. The main content
            // area's width/height is divided by two, then 28dp is added
            // to account for the media controller bar's rounded corner
            // radius. The min value between this calculated width/height
            // and the full content area width/height minus 64dp (i.e.
            // the add button's 56dp size plus an 8dp margin) is then
            // used to ensure that for small screen sizes the media
            // controller can't overlap the add button.
            DpSize(width = if (alignToEnd) 56.dp else minOf(
                               contentAreaSize.width / 2f + 28.dp,
                               contentAreaSize.width - 64.dp),
                   height = if (!alignToEnd) 56.dp else minOf(
                               contentAreaSize.height / 2f + 28.dp,
                               contentAreaSize.height - 64.dp))
        }
        val expandedSize = remember(padding, alignToEnd) {
            val widthFraction = if (alignToEnd) 0.6f else 1.0f
            DpSize(width = contentAreaSize.width * widthFraction,
                   height = if (!alignToEnd) 350.dp
                            else contentAreaSize.height)
        }
        val alignment = if (alignToEnd) Alignment.TopEnd as BiasAlignment
                        else            Alignment.BottomStart as BiasAlignment
        val transformOrigin = rememberClippedBrushBoxTransformOrigin(collapsedSize, alignment, padding)

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(delayMillis = 75)) +
                    scaleIn(overshootTween(delay = 75),
                            transformOrigin = transformOrigin),
            exit = fadeOut(tween(delayMillis = 125)) +
                   scaleOut(anticipateTween(delay = 75),
                            transformOrigin = transformOrigin)
        ) {
            val startColor = MaterialTheme.colors.primaryVariant
            val endColor = MaterialTheme.colors.secondaryVariant
            val backgroundBrush = remember(startColor, endColor) {
                Brush.horizontalGradient(colors = listOf(startColor, endColor))
            }
            StatefulMediaController(
                orientation = if (alignToEnd) Orientation.Vertical
                              else            Orientation.Horizontal,
                backgroundBrush = backgroundBrush,
                contentColor = MaterialTheme.colors.onPrimary,
                collapsedSize = collapsedSize,
                expandedSize = expandedSize,
                alignment = alignment,
                padding = padding,
                showingPresetSelector = showingPresetSelector,
                isPlaying = boundPlayerService?.isPlaying ?: false,
                onPlayPauseClick = ::onPlayPauseClick,
                onActivePresetClick = onActivePresetClick,
                onCloseButtonClick = onCloseButtonClick)
        }
    }

    /** Compose an add button at the bottom end edge of the screen that is
     * conditionally visible depending on the value of [visible]. [target]
     * defines the value of [AddButtonTarget] that describes the type of
     * entity that will be added.*/
    @Composable private fun BoxScope.AddTrackButton(
        visible: Boolean,
        target: AddButtonTarget,
        modifier: Modifier = Modifier,
    ) {
        AnimatedVisibility( // add track button
            visible = visible,
            modifier = modifier.align(Alignment.BottomEnd),
            enter = fadeIn(tween()) + scaleIn(overshootTween()),
            exit = fadeOut(tween(delayMillis = 50)) + scaleOut(anticipateTween()),
        ) {
            AddButton(target, MaterialTheme.colors.secondaryVariant)
        }
    }

    private fun onPlayPauseClick() {
        boundPlayerService?.toggleIsPlaying()
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