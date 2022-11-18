/* This file is part of SoundAura, which is released under the terms of the Apache
 * License 2.0. See license.md in the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cliffracertech.soundaura.ui.theme.SoundAuraTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * A view to display the name and modified status of a [Preset] instance. An
 * options button is also provided that provides options to rename, overwrite,
 * or delete the preset.
 *
 * @param modifier The [Modifier] to use for the view
 * @param presetName The name of the [Preset] instance
 * @param isModified Whether or not the [Preset] has unsaved changes.
 *     This will be indicated by an asterisk next to the [Preset]'s name.
 * @param onRenameClick The callback that will be invoked when the user
 *     clicks on the options menu's rename option
 * @param onOverwriteClick The callback that will be invoked when the
 *     user clicks on the options menu's overwrite option
 * @param onDeleteClick The callback that will be invoked when the user
 *     clicks on the option menu's delete option
 * @param onClick The callback that will be invoked when the user clicks the view
 */
@Composable fun PresetView(
    modifier: Modifier = Modifier,
    presetName: String,
    isModified: Boolean,
    onRenameClick: () -> Unit,
    onOverwriteClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onClick: () -> Unit
) = Row(
    modifier = modifier
        .minTouchTargetSize()
        .clickable (
            onClickLabel = stringResource(R.string.preset_click_label, presetName),
            role = Role.Button,
            onClick = onClick
        ).padding(vertical = 2.dp),
    verticalAlignment = Alignment.CenterVertically
) {

    Row(Modifier.weight(1f).padding(start = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MarqueeText(presetName, Modifier.weight(1f, false))
        if (isModified)
            Text(" *", style = LocalTextStyle.current.copy(fontSize = 18.sp))
    }
    var showingOptionsMenu by rememberSaveable { mutableStateOf(false) }

    IconButton({ showingOptionsMenu = true }) {
        Icon(imageVector = Icons.Default.MoreVert,
             contentDescription = stringResource(
                 R.string.item_options_button_description, presetName))

        DropdownMenu(
            expanded = showingOptionsMenu,
            onDismissRequest = { showingOptionsMenu = false }
        ) {
            DropdownMenuItem(onClick = {
                onRenameClick()
                showingOptionsMenu = false
            }) { Text(stringResource(R.string.rename)) }

            DropdownMenuItem(onClick = {
                onOverwriteClick()
                showingOptionsMenu = false
            }) { Text(stringResource(R.string.overwrite)) }

            DropdownMenuItem(onClick = {
                onDeleteClick()
                showingOptionsMenu = false
            }) { Text(stringResource(R.string.delete)) }
        }
    }
}

/**
 * Show a dialog asking the user to confirm that they would like to overwrite
 * the [Preset] whose name is equal to [presetName] with any current
 * changes. The cancel and ok buttons will invoke [onDismissRequest] and
 * [onConfirm], respectively.
 */
@Composable fun ConfirmPresetOverwriteDialog(
    presetName: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) = SoundAuraDialog(
    title = stringResource(R.string.confirm_overwrite_preset_dialog_title),
    text = stringResource(R.string.confirm_overwrite_preset_dialog_message, presetName),
    onDismissRequest = onDismissRequest,
    onConfirm = {
        onConfirm()
        onDismissRequest()
    })

/**
 * Show a dialog asking the user to confirm that they want
 * to delete the [Preset] whose name equals [presetName].
 */
@Composable fun ConfirmDeletePresetDialog(
    presetName: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit
) = SoundAuraDialog(
    onDismissRequest = onDismissRequest,
    title = stringResource(R.string.confirm_delete_preset_title, presetName),
    text = stringResource(R.string.confirm_delete_preset_message),
    confirmText = stringResource(R.string.delete),
    onConfirm = {
        onConfirm()
        onDismissRequest()
    })

/**
 * Display a list of [Preset]s, with an options menu for each [Preset]
 * to allow for renaming, overwriting, and deletion. Optionally, a single
 * [Preset] identified as the active one will have the [selectionBrush]
 * applied to it to indicate this status.
 *
 * @param modifier The [Modifier] to use for the [Preset] list
 * @param activePresetNameProvider A function that returns the name of the
 *     actively playing [Preset], or null if there isn't one, when invoked
 * @param activePresetIsModified Whether or not the [Preset] marked as the
 *     active one has been modified. An asterisk will be placed next to its
 *     name in this case to indicate this to the user.
 * @param selectionBrush The [Brush] that will be applied to the active
 *     [Preset] to indicate its status as the active [Preset]
 * @param presetListProvider A function that returns the list of presets when invoked
 * @param onPresetClick The callback that will be invoked when the user clicks on a [Preset]
 * @param onPresetRenameRequest The callback that will be invoked when the user
 *     has requested that the [Preset] parameter be renamed to the [String] parameter
 * @param onPresetOverwriteRequest The callback that will be invoked when the
 *     user has requested the current track / volume combination to overwrite
 *     the one currently associated with the [Preset] parameter
 * @param onPresetDeleteRequest The callback that will be invoked when the user
 *     has requested that the [Preset] parameter be deleted
 */
@Composable fun PresetList(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    activePresetNameProvider: () -> String?,
    activePresetIsModified: Boolean,
    selectionBrush: Brush,
    presetListProvider: () -> ImmutableList<Preset>,
    onPresetRenameRequest: (Preset, String) -> Unit,
    onPresetOverwriteRequest: (Preset) -> Unit,
    onPresetDeleteRequest: (Preset) -> Unit,
    onPresetClick: (Preset) -> Unit,
) = CompositionLocalProvider(LocalContentColor provides MaterialTheme.colors.onSurface) {
    val presetList = presetListProvider()

    AnimatedContent(presetList.isEmpty(), modifier) { listIsEmpty ->
        if (listIsEmpty)
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(stringResource(R.string.empty_preset_list_message),
                     modifier = Modifier.width(300.dp),
                     textAlign = TextAlign.Justify)
            }
        else {
            var renameDialogTarget by rememberSaveable { mutableStateOf<Preset?>(null) }
            var overwriteDialogTarget by rememberSaveable { mutableStateOf<Preset?>(null) }
            var deleteDialogTarget by rememberSaveable { mutableStateOf<Preset?>(null) }

            LazyColumn(Modifier, contentPadding = contentPadding) {
                val activePresetName = activePresetNameProvider()
                items(presetList, key = { preset -> preset.name }) { preset ->
                    val isActivePreset = preset.name == activePresetName
                    PresetView(
                        modifier = if (!isActivePreset) Modifier
                                   else Modifier.background(selectionBrush, alpha = 0.5f),
                        presetName = preset.name,
                        isModified = isActivePreset && activePresetIsModified,
                        onRenameClick = remember {{ renameDialogTarget = preset }},
                        onOverwriteClick = remember {{ overwriteDialogTarget = preset }},
                        onDeleteClick = remember {{ deleteDialogTarget = preset }},
                        onClick = remember {{ onPresetClick(preset) }})
                    Divider()
                }
            }
            renameDialogTarget?.let { preset ->
                RenameDialog(
                    itemName = preset.name,
                    onDismissRequest = { renameDialogTarget = null },
                    onConfirm = {
                        renameDialogTarget = null
                        onPresetRenameRequest(preset, it)
                    })
            }
            overwriteDialogTarget?.let { preset ->
                ConfirmPresetOverwriteDialog(
                    presetName = preset.name,
                    onDismissRequest = { overwriteDialogTarget = null },
                    onConfirm = {
                        overwriteDialogTarget = null
                        onPresetOverwriteRequest(preset)
                    })
            }
            deleteDialogTarget?.let { preset ->
                ConfirmDeletePresetDialog(preset.name,
                    onDismissRequest = { deleteDialogTarget = null },
                    onConfirm = {
                        deleteDialogTarget = null
                        onPresetDeleteRequest(preset)
                    })
            }
        }
    }
}

@Preview @Composable
fun PresetListPreview() = SoundAuraTheme {
    val list = remember { List(4) {
        Preset("Super Duper Extra Really Long Preset Name$it")
    }.toImmutableList() }
    var activePresetName by remember { mutableStateOf(list.first().name) }

    PresetList(
        activePresetNameProvider = { activePresetName },
        activePresetIsModified = true,
        selectionBrush = Brush.horizontalGradient(
            listOf(MaterialTheme.colors.primaryVariant,
                   MaterialTheme.colors.secondaryVariant)),
        presetListProvider = { list },
        onPresetClick = { activePresetName = it.name },
        onPresetRenameRequest = { _, _ -> },
        onPresetDeleteRequest = {},
        onPresetOverwriteRequest = {}
    )
}