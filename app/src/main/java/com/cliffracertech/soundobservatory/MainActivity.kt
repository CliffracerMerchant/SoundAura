/* This file is part of SoundObservatory, which is released under the Apache License 2.0. See
 * license.md in the project's root directory or use an internet search engine to see the full license. */
package com.cliffracertech.soundobservatory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.ButtonDefaults.buttonColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.cliffracertech.soundobservatory.ui.theme.SoundObservatoryTheme

@ExperimentalAnimationApi
@ExperimentalComposeUiApi
@ExperimentalAnimationGraphicsApi
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MainActivityContent() }
    }
}

@ExperimentalComposeUiApi
@ExperimentalAnimationGraphicsApi
@ExperimentalAnimationApi
@Preview(showBackground = true)
@Composable fun MainActivityPreview() = MainActivityContent()

@ExperimentalComposeUiApi
@ExperimentalAnimationGraphicsApi
@ExperimentalAnimationApi
@Composable fun MainActivityContent() {
    val title = stringResource(R.string.app_name)

    SoundObservatoryTheme(true) {
        Surface(
            color = MaterialTheme.colors.background,
            modifier = Modifier.fillMaxSize(1f)
        ) {
            var actionModeTitle = remember { mutableStateOf<String?>(null) }
            var searchQuery = remember { mutableStateOf<String?>(null) }
            var sortOption = remember { mutableStateOf(Track.Sort.NameAsc) }

            Column {
                RecyclerViewActionBar(backButtonVisible = false, backButtonOnClick = { },
                                      title, actionModeTitle, searchQuery, sortOption)
                SoundList()
            }
        }
    }
}

@ExperimentalComposeUiApi
@ExperimentalAnimationGraphicsApi
@Composable fun SoundList() =
    Column(
        modifier = Modifier.padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TrackView(Track(path = "", title = "Audio clip 1", volume = 0.5f))
        TrackView(Track(path = "", title = "Audio clip 2", volume = 0.5f))
    }

@ExperimentalComposeUiApi
@ExperimentalAnimationGraphicsApi
@Composable fun TrackView(track: Track) = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
        .fillMaxWidth(1f)
        .background(MaterialTheme.colors.surface, MaterialTheme.shapes.large)
){
    var playing by remember { mutableStateOf(false) }
    PlayPauseButton(playing, MaterialTheme.colors.primary) { playing = !playing }

    var volume by remember { mutableStateOf(track.volume) }
    SliderBox(value = volume, onValueChange = { volume = it },
              modifier = Modifier.height(66.dp).weight(1f),
              sliderPadding = PaddingValues(top = 24.dp)) {
        Text(text = track.title, style = MaterialTheme.typography.h6,
             modifier = Modifier.padding(8.dp, 6.dp, 0.dp, 0.dp))
    }

    var showingOptionsMenu by remember { mutableStateOf(false) }
    var showingRenameDialog by remember { mutableStateOf(false) }
    var showingDeleteDialog by remember { mutableStateOf(false) }

    IconButton(onClick = { showingOptionsMenu = !showingOptionsMenu }) {
        Icon(imageVector = Icons.Default.MoreVert,
             tint = MaterialTheme.colors.primaryVariant,
             contentDescription = "${track.title} options")

        DropdownMenu(
            expanded = showingOptionsMenu,
            onDismissRequest = { showingOptionsMenu = false }
        ) {
            DropdownMenuItem(onClick = {
                showingRenameDialog = true
                showingOptionsMenu = false
            }) {
                Text(text = "Rename", style = MaterialTheme.typography.button)
            }
            DropdownMenuItem(onClick = {
                showingDeleteDialog = true
                showingOptionsMenu = false
            }) {
                Text(text = "Delete", style = MaterialTheme.typography.button)
            }
        }
    }

    if (showingRenameDialog)
        RenameDialog(
            track.title,
            onDismissRequest = { showingRenameDialog = false },
            onConfirmRequest = {  })

    if (showingDeleteDialog)
        ConfirmDeleteDialog(
            track.title,
            onDismissRequest = { showingDeleteDialog = false },
            onConfirmRequest = { })
}

@Composable fun PlayPauseButton(playing: Boolean, tint: Color, onClick: () -> Unit) = Box(
    modifier = Modifier.size(48.dp),
    contentAlignment = Alignment.Center
) {
    AndroidView(
        update = { it.isChecked = playing },
        factory = {
            android.widget.CheckBox(it).apply {
                val drawable = ContextCompat.getDrawable(it, R.drawable.play_pause)
                drawable?.setTint(tint.toArgb())
                buttonDrawable = drawable
                setOnClickListener { onClick() }
            }
        }
    )
}


