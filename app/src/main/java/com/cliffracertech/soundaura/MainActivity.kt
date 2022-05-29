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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cliffracertech.soundaura.ui.theme.SoundAuraTheme
import com.google.accompanist.insets.ProvideWindowInsets
import com.google.accompanist.insets.navigationBarsHeight
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.flow.collect
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

    override fun onBackPressed() {
        if (!viewModel.onBackButtonClick())
            super.onBackPressed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentWithTheme {
            val scaffoldState = rememberScaffoldState()

            MessageDisplayer(scaffoldState)

            Scaffold(
                scaffoldState = scaffoldState,
                topBar = {
                    SoundAuraActionBar(::onBackPressed)
                }, bottomBar = {
                    Spacer(Modifier.navigationBarsHeight().fillMaxWidth())
                }, floatingActionButton = {
                    // The floating action buttons are added in the content
                    // section instead to have more control over their placement.
                    // A spacer is added here so that snack bars still appear
                    // above the floating action buttons. 48dp was arrived at
                    // by starting from the FAB size of 56dp and adjusting
                    // downward by 8dp due to the inherent snack bar padding.
                    Spacer(Modifier.height(48.dp).fillMaxWidth())
                }, content = {
                    MainContent(padding = it)
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
        val useDarkTheme by derivedStateOf {
            if (themePreference == AppTheme.Dark)
                true
            else themePreference == AppTheme.UseSystem
                    && systemIsUsingDarkTheme
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
            ProvideWindowInsets { content() }
        }
    }

    /** Compose a message handler that will read messages emitted from a
     * MainActivityViewModel's messages member and display them using snack bars.*/
    @Composable private fun MessageDisplayer(scaffoldState: ScaffoldState) {
        val dismissLabel = stringResource(R.string.dismiss)

        LaunchedEffect(Unit) {
            viewModel.messages.collect { message ->
                val context = this@MainActivity
                val messageText = message.stringResource.resolve(context)
                val actionLabel = message.actionStringResource?.resolve(context)
                                                    ?: dismissLabel.uppercase()
                scaffoldState.snackbarHostState.showSnackbar(
                    message = messageText,
                    actionLabel = actionLabel,
                    duration = SnackbarDuration.Short)
            }
        }
    }

    @Composable private fun MainContent(
        padding: PaddingValues,
    ) = Box(Modifier.fillMaxSize()) {
        val showingAppSettings = viewModel.showingAppSettings
        val ld = LocalLayoutDirection.current

        // The padding parameter only accounts for the system bars.
        // The buttons are given an extra 8dp so that they don't
        // appear right along the bottom edge.
        val buttonBottomPadding = remember(padding) {
            8.dp + padding.calculateBottomPadding()
        }

        // The track list state is remembered here so that the
        // scrolling position will not be lost if the user
        // navigates to the app settings screen and back.
        val trackListState = rememberLazyListState()

        SlideAnimatedContent(
            targetState = showingAppSettings,
            leftToRight = !showingAppSettings
        ) { showingAppSettingsScreen ->
            if (showingAppSettingsScreen) {
                val appSettingsPadding = remember(padding) {
                    PaddingValues(
                        start = 8.dp + padding.calculateStartPadding(ld),
                        top = 8.dp + padding.calculateTopPadding(),
                        end = 8.dp + padding.calculateEndPadding(ld),
                        bottom = buttonBottomPadding)
                }
                AppSettings(Modifier.fillMaxSize(), appSettingsPadding)
            } else {
                // The track list is given an additional 64dp padding
                // to account for the size of the FABs themselves.
                val trackListPadding = remember(padding) {
                    PaddingValues(
                        start = 8.dp + padding.calculateStartPadding(ld),
                        top = 8.dp + padding.calculateTopPadding(),
                        end = 8.dp + padding.calculateEndPadding(ld),
                        bottom = 64.dp + buttonBottomPadding)
                }
                StatefulTrackList(
                    modifier = Modifier.fillMaxSize(),
                    padding = trackListPadding,
                    state = trackListState,
                    onVolumeChange = { uri, volume ->
                        boundPlayerService?.setTrackVolume(uri, volume)
                    })
            }
        }
        FloatingActionButtons(
            visible = !showingAppSettings,
            bottomPadding = buttonBottomPadding)
    }

    @Composable private fun BoxScope.FloatingActionButtons(
        visible: Boolean,
        bottomPadding: Dp
    ) {
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomPadding),
            enter = fadeIn(tween(delayMillis = 75)) + scaleIn(overshootTweenSpec(delay = 75)),
            exit = fadeOut(tween(delayMillis = 125)) + scaleOut(anticipateTweenSpec(delay = 75))
        ) {
            val isPlaying by boundPlayerService?.isPlaying.mapToNonNullState(false)
            FloatingActionButton(
                onClick = { boundPlayerService?.toggleIsPlaying() },
                backgroundColor = lerp(MaterialTheme.colors.primary,
                                       MaterialTheme.colors.secondary, 0.5f),
//                    elevation = FloatingActionButtonDefaults.elevation(8.dp, 4.dp)
                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
            ) {
                val description = stringResource(
                    if (isPlaying) R.string.pause_button_description
                    else           R.string.play_button_description)
                PlayPauseIcon(
                    playing = isPlaying,
                    contentDescription = description,
                    tint = MaterialTheme.colors.onPrimary)
            }
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = bottomPadding),
            enter = fadeIn(tween()) + scaleIn(overshootTweenSpec()),
            exit = fadeOut(tween(delayMillis = 50)) + scaleOut(anticipateTweenSpec()),
            content = { AddLocalFilesButton() })
    }
}