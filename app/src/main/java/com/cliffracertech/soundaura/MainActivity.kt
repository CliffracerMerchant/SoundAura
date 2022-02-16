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
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
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

            val isPlaying by produceState(false, boundPlayerService?.isPlaying) {
                value = boundPlayerService?.isPlaying ?: false
            }
            val scaffoldState = rememberScaffoldState()
            MessageHandler(scaffoldState)

            Scaffold(
                scaffoldState = scaffoldState,
                floatingActionButtonPosition = FabPosition.Center,
                topBar = { ActionBar(showingAppSettings = showingAppSettings,
                                     onBackButtonClick = { showingAppSettings = false },
                                     onSettingsButtonClick = { showingAppSettings = true }) },
                floatingActionButton = { PlayPauseButton(isPlaying) }
            ) {
                SlideAnimatedContent(
                    targetState = showingAppSettings,
                    leftToRight = !showingAppSettings
                ) { showingAppSettings ->
                    if (showingAppSettings) AppSettings()
                    else SoundMixEditor(boundPlayerService)
                }
            }
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

            LaunchedEffect(usingDarkTheme) {
                (windowBackground as GradientDrawable).colors =
                    intArrayOf(colorPrimary.toArgb(), colorPrimaryVariant.toArgb())
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
            viewModel.messages.collect {
                scaffoldState.snackbarHostState.showSnackbar(
                    message = it.stringResource.resolve(this@MainActivity),
                    actionLabel = it.actionStringResource?.resolve(this@MainActivity)
                        ?: dismissLabel.uppercase(),
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    /** Compose a ListActionBar that switches between a normal state with all
     * buttons enabled, and an alternative state with most buttons disabled
     * according to the value of @param showingAppSettings. Back and settings
     * button clicks will invoke @param onBackButtonClick and @param
     * onSettingsButtonClick, respectively. */
    @Composable private fun ActionBar(
        showingAppSettings: Boolean,
        onBackButtonClick: () -> Unit,
        onSettingsButtonClick: () -> Unit,
    ) {
        val viewModel: ActionBarViewModel = viewModel()
        val title = if (!showingAppSettings) stringResource(R.string.app_name)
                    else stringResource(R.string.app_settings_description)
        ListActionBar(
            backButtonShouldBeVisible = showingAppSettings,
            onBackButtonClick = onBackButtonClick,
            title = title,
            searchQuery = viewModel.searchQuery,
            onSearchQueryChanged = { viewModel.searchQuery = it },
            showSearchAndChangeSortButtons = !showingAppSettings,
            onSearchButtonClick = viewModel::onSearchButtonClick,
            sortOptions = Track.Sort.values(),
            sortOptionNames = Track.Sort.stringValues(),
            currentSortOption = viewModel.trackSort,
            onSortOptionClick = viewModel::ontrackSortOptionClick,
        ) {
            SettingsButton(onClick = onSettingsButtonClick)
        }
    }

    /** Compose a play / pause floating action button. */
    @Composable private fun PlayPauseButton(isPlaying: Boolean) {
        FloatingActionButton(
            onClick = { boundPlayerService?.toggleIsPlaying() },
            backgroundColor = lerp(MaterialTheme.colors.primary,
                                   MaterialTheme.colors.primaryVariant, 0.5f),
            elevation = FloatingActionButtonDefaults.elevation(6.dp, 3.dp)
        ) { PlayPauseIcon(isPlaying, tint = MaterialTheme.colors.onPrimary) }
    }


    /** Compose a SoundMixEditor, using an instance of SoundMixEditorViewModel
     * to obtain the list of tracks and to respond to item related callbacks.
     * @param boundPlayerService The MainActivity's PlayerService.Binder instance. */
    @Composable private fun SoundMixEditor(
        boundPlayerService: PlayerService.Binder? = null
    ) {
        val viewModel: SoundMixEditorViewModel = viewModel()
        val itemCallback = remember {
            TrackViewCallback(
                onPlayPauseButtonClick = viewModel::onTrackPlayPauseClick,
                onVolumeChange = { uri, volume ->
                    boundPlayerService?.setTrackVolume(uri, volume)
                }, onVolumeChangeFinished = viewModel::onTrackVolumeChangeRequest,
                onRenameRequest = viewModel::onTrackRenameRequest,
                onDeleteRequest = viewModel::onDeleteTrackDialogConfirmation)
        }
        SoundMixEditor(tracks = viewModel.tracks, itemCallback = itemCallback,
                       onConfirmAddTrackDialog = viewModel::onConfirmAddTrackDialog)
    }
}

@Composable fun MainActivityPreview(usingDarkTheme: Boolean) =
    SoundAuraTheme(usingDarkTheme) {
        Scaffold(
            topBar = { ListActionBar(
                backButtonShouldBeVisible = false,
                onBackButtonClick = { },
                title = stringResource(R.string.app_name),
                onSearchQueryChanged = { },
                onSearchButtonClick = { },
                sortOptions = Track.Sort.values(),
                sortOptionNames = Track.Sort.stringValues(),
                currentSortOption = Track.Sort.OrderAdded,
                onSortOptionClick = { },
                otherContent = { SettingsButton{ } }
            )},
            floatingActionButtonPosition = FabPosition.Center,
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { },
                    backgroundColor = lerp(MaterialTheme.colors.primary,
                                           MaterialTheme.colors.primaryVariant, 0.5f),
                    elevation = FloatingActionButtonDefaults.elevation(6.dp, 3.dp),
                ) { PlayPauseIcon(playing = true, tint = MaterialTheme.colors.onPrimary) }
            }
        ) {
            SoundMixEditor(tracks = listOf(
                Track(uriString = "0", name = "Audio track 1", volume = 0.3f),
                Track(uriString = "1", name = "Audio track 2", volume = 0.8f),
                Track(uriString = "2", name = "Audio track 3", volume = 0.5f)))
        }
}

@Preview @Composable fun MainActivityLightThemePreview() =
    MainActivityPreview(usingDarkTheme = false)
@Preview @Composable fun MainActivityDarkThemePreview() =
    MainActivityPreview(usingDarkTheme = true)