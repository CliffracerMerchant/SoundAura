/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cliffracertech.soundaura.ui.theme.SoundAuraTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collect
import javax.inject.Inject

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    messageHandler: MessageHandler
) : ViewModel() {
    val messages = messageHandler.messages
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var boundPlayerService by mutableStateOf<PlayerService.Binder?>(null)

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
        setContentWithTheme {
            var showingAppSettings by rememberSaveable { mutableStateOf(false) }
            val scaffoldState = rememberScaffoldState()
            val isPlaying by boundPlayerService?.isPlaying.mapToNonNullState(false)
            MessageHandler(scaffoldState)

            Scaffold(
                scaffoldState = scaffoldState,
                floatingActionButtonPosition = FabPosition.Center,
                topBar = {
                    SoundAuraActionBar(
                        showingAppSettings = showingAppSettings,
                        onBackButtonClick = { showingAppSettings = false },
                        onSettingsButtonClick = { showingAppSettings = true })
                }, floatingActionButton = {
                    PlayPauseButton(showing = !showingAppSettings, isPlaying)
                }, content = {
                    MainContent(showingAppSettings)
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
        SoundAuraTheme(usingDarkTheme) {
            val windowBackground = remember {
                ContextCompat.getDrawable(this, R.drawable.background_gradient)
            }
            val colorPrimary = MaterialTheme.colors.primary
            val colorPrimaryVariant = MaterialTheme.colors.primaryVariant
            val colorSurface = MaterialTheme.colors.background

            LaunchedEffect(usingDarkTheme) {
                (windowBackground as GradientDrawable).colors =
                    intArrayOf(colorPrimary.toArgb(), colorPrimaryVariant.toArgb())
                window.navigationBarColor = colorSurface.toArgb()
                window.setBackgroundDrawable(windowBackground)
            }
            content()
        }
    }

    /** Compose a message handler that will read messages emitted from a
     * MainActivityViewModel's messages member and display them using snack bars.*/
    @Composable private fun MessageHandler(scaffoldState: ScaffoldState) {
        val viewModel: MainActivityViewModel = viewModel()
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
    @Composable private fun PlayPauseButton(
        showing: Boolean,
        isPlaying: Boolean
    ) = AnimatedVisibility(showing,
        enter = fadeIn(tween()) + scaleIn(overshootTweenSpec()),
        exit = fadeOut(tween(delayMillis = 50)) + scaleOut(anticipateTweenSpec())
    ) {
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

    @Composable private fun MainContent(
        showingAppSettings: Boolean
    ) = Box(Modifier.fillMaxSize(1f)) {
        SlideAnimatedContent(
            targetState = showingAppSettings,
            leftToRight = !showingAppSettings
        ) { showingAppSettings ->
            if (showingAppSettings)
                AppSettings()
            else StatefulTrackList(onVolumeChange = { uri, volume ->
                boundPlayerService?.setTrackVolume(uri, volume)
            })
        }
        AnimatedVisibility(
            visible = !showingAppSettings,
            modifier = Modifier.align(Alignment.BottomEnd),
            enter = fadeIn(tween()) + scaleIn(overshootTweenSpec()),
            exit = fadeOut(tween(delayMillis = 50)) + scaleOut(anticipateTweenSpec())
        ) {
            StatefulAddTrackButton()
        }
    }
}