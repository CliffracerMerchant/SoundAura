/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
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

@ActivityRetainedScoped
class ReadOnlyMainActivityNavigationState @Inject constructor(
    private val mutableState: MainActivityNavigationState
) {
    val showingAppSettings get() = mutableState.showingAppSettings
}

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    messageHandler: MessageHandler,
    private val navigationState: ReadOnlyMainActivityNavigationState,
) : ViewModel() {
    val messages = messageHandler.messages
    val showingAppSettings get() = navigationState.showingAppSettings
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentWithTheme {
            val scaffoldState = rememberScaffoldState()
            val addTrackButtonViewModel: AddTrackButtonViewModel = viewModel()

            MessageHandler(scaffoldState)

            Scaffold(
                modifier = Modifier.pointerInput(Unit) {
                    detectTapWithoutConsuming(addTrackButtonViewModel::onGlobalClick)
                }, scaffoldState = scaffoldState,
                floatingActionButtonPosition = FabPosition.Center,
                topBar = {
                    SoundAuraActionBar(::onBackPressed)
                }, bottomBar = {
                    Spacer(Modifier.navigationBarsHeight().fillMaxWidth())
                }, floatingActionButton = {
                    PlayPauseButton()
                }, content = {
                    MainContent(it.calculateBottomPadding())
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
        val usingDarkTheme by derivedStateOf {
            val theme = settingsViewModel.appTheme
            if (theme == AppTheme.Dark)
                true
            else {
                val systemDarkThemeActive = Configuration.UI_MODE_NIGHT_YES ==
                    (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
                theme == AppTheme.UseSystem && systemDarkThemeActive
            }
        }

        val uiController = rememberSystemUiController()
        LaunchedEffect(Unit) {
            uiController.setStatusBarColor(Color.Transparent, true)
            uiController.setNavigationBarColor(Color.Transparent, true)
        }

        SoundAuraTheme(usingDarkTheme) {
            ProvideWindowInsets {
                content()
            }
        }
    }

    /** Compose a message handler that will read messages emitted from a
     * MainActivityViewModel's messages member and display them using snack bars.*/
    @Composable private fun MessageHandler(scaffoldState: ScaffoldState) {
        val dismissLabel = stringResource(R.string.dismiss_description)

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

    /** Compose a play / pause floating action button inside an AnimatedVisibility. */
    @Composable private fun PlayPauseButton() = AnimatedVisibility(
        visible = !viewModel.showingAppSettings,
        enter = fadeIn(tween()) + scaleIn(overshootTweenSpec()),
        exit = fadeOut(tween(delayMillis = 125)) + scaleOut(anticipateTweenSpec(delay = 75))
    ) {
        val isPlaying by boundPlayerService?.isPlaying.mapToNonNullState(false)
        FloatingActionButton(
            onClick = { boundPlayerService?.toggleIsPlaying() },
            backgroundColor = lerp(MaterialTheme.colors.primary,
                                   MaterialTheme.colors.primaryVariant, 0.5f),
//            elevation = FloatingActionButtonDefaults.elevation(6.dp, 3.dp)
            elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
        ) {
            PlayPauseIcon(isPlaying, tint = MaterialTheme.colors.onPrimary)
        }
    }

    @Composable private fun MainContent(bottomPadding: Dp) =
        Box(Modifier.fillMaxSize(1f)) {
            val showingAppSettings = viewModel.showingAppSettings
            // The bottomPadding parameter only accounts for the navigation bar.
            // The extra 16dp for the add track button is the standard floating
            // action button edge margin. The extra 72dp for the track list is
            // the 16dp margin for the FAB, plus 56dp for the FAB itself.
            val trackListBottomPadding = bottomPadding + 72.dp
            val addTrackButtonBottomPadding = bottomPadding + 16.dp

            SlideAnimatedContent(
                targetState = showingAppSettings,
                leftToRight = !showingAppSettings
            ) { showingAppSettingsScreen ->
                if (showingAppSettingsScreen)
                    AppSettings()
                else StatefulTrackList(
                    bottomPadding = trackListBottomPadding,
                    onVolumeChange = { uri, volume ->
                        boundPlayerService?.setTrackVolume(uri, volume)
                    })
            }
            AnimatedVisibility(
                visible = !showingAppSettings,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = addTrackButtonBottomPadding),
                enter = fadeIn(tween(delayMillis = 75)) + scaleIn(overshootTweenSpec(delay = 75)),
                exit = fadeOut(tween(delayMillis = 50)) + scaleOut(anticipateTweenSpec())
            ) { StatefulAddTrackButton() }
        }
}