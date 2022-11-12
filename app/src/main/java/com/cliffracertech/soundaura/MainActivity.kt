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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
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
                AppSettings(padding, Modifier.fillMaxSize())
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
        var showingPresetSelector by rememberSaveable { mutableStateOf(false) }
        MediaController(
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
                          else with(density) { (-12).dp.toPx() },
            animationSpec = spring(stiffness = 700f))
        val addPresetButtonYOffset by animateFloatAsState(
            targetValue = if (!showingPresetSelector) 0f
                          else with(density) { (-12).dp.toPx() },
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
        val alignment = if (alignToEnd) Alignment.TopEnd
                        else            Alignment.BottomStart
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(alignment),
            enter = fadeIn(tween(delayMillis = 75)) +
                    scaleIn(overshootTween(delay = 75)),
            exit = fadeOut(tween(delayMillis = 125)) +
                   scaleOut(anticipateTween(delay = 75))
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

            val density = LocalDensity.current
            val ld = LocalLayoutDirection.current
            val collapsedSize = remember(constraints, alignToEnd, density) {
                with (density) { DpSize(
                    // The goal is to have the media controller bar have such a
                    // width/height that the play/pause icon is centered in the
                    // content area's width in portrait mode. The main content
                    // area's width is divided by two, then 28dp is added to to
                    // account for the media controller bar's rounded corner radius.
                    // The start padding also must be subtracted.
                    height = if (!alignToEnd) 56.dp else
                        (constraints.maxHeight / 2f).toDp() + 28.dp,
                    width = if (alignToEnd) 56.dp else
                        (constraints.maxWidth / 2f).toDp() + 28.dp - padding.calculateStartPadding(ld)
                )}
            }
            val presetSelectorSize = remember(constraints, alignToEnd, density) {
                val widthFraction = if (alignToEnd) 0.6f else 1.0f
                with (density) {
                    DpSize(width = (constraints.maxWidth * widthFraction).toDp(),
                           height = if (!alignToEnd) 350.dp
                                    else constraints.maxHeight.toDp())
                }
            }
            val startColor = MaterialTheme.colors.primaryVariant
            val endColor = MaterialTheme.colors.secondaryVariant
            val backgroundBrush = remember(alignToEnd, showingPresetSelector, startColor, endColor) {
                val viewStart = with(density) {
                    if (alignToEnd)
                        constraints.maxWidth - padding.calculateEndPadding(ld).toPx() -
                            if (showingPresetSelector) presetSelectorSize.width.toPx()
                            else                       collapsedSize.width.toPx()
                    else padding.calculateStartPadding(ld).toPx()
                }
                Brush.horizontalGradient(
                    colors = listOf(startColor, endColor),
                    startX = -viewStart,
                    endX = constraints.maxWidth - viewStart)
            }

            MediaController(
                modifier = Modifier.padding(padding),
                orientation = if (alignToEnd) Orientation.Vertical
                              else            Orientation.Horizontal,
                backgroundBrush = backgroundBrush,
                contentColor = MaterialTheme.colors.onPrimary,
                collapsedSize = collapsedSize,
                expandedSize = presetSelectorSize,
                showingPresetSelector = showingPresetSelector,
                isPlaying = isPlaying,
                onPlayPauseClick = ::onPlayPauseClick,
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
        }
    }

    /** Compose an add track button at the bottom end edge of the screen,
     * which is conditionally visible depending on the value of [visible],
     * with padding equal to the parameter [padding]. */
    @Composable private fun BoxScope.AddTrackButton(
        visible: Boolean,
        modifier: Modifier,
        target: AddButtonTarget,
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