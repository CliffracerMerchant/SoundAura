/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.mediacontroller

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cliffracertech.soundaura.MarqueeText
import com.cliffracertech.soundaura.R
import com.cliffracertech.soundaura.dialog.RenameDialog
import com.cliffracertech.soundaura.dialog.SoundAuraDialog
import com.cliffracertech.soundaura.minTouchTargetSize
import com.cliffracertech.soundaura.model.Validator
import com.cliffracertech.soundaura.model.database.Preset
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
        .clickable(
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
        if (isModified) Text(" *")
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

/** A collection of callbacks used in user [PresetList] interactions. */
interface PresetListCallback {
    /** A function that will provide the list
     * of [Preset]s to display when invoked */
    val listProvider: () -> ImmutableList<Preset>?
    /** A function that will provide the current target
     * of the rename dialog, if any, when invoked */
    val renameTargetProvider: () -> Preset?
    /** A function that will provide the proposed name
     * for the [Preset] being renamed when invoked */
    val proposedNameProvider: () -> String?
    /** The callback that will be invoked when the proposed
     * name for the [Preset] being renamed is changed to [newName] */
    fun onProposedNameChange(newName: String)
    /** A function that will provide the error message to display for the proposed
     * name of the [Preset] being renamed, or null if the proposed name is valid */
    val renameErrorMessageProvider: () -> Validator.Message?
    /** The callback that will be invoked when the
     * rename option has been chosen for [preset] */
    fun onRenameStart(preset: Preset) {}
    /** The callback that will be invoked when the rename dialog is dismissed */
    fun onRenameCancel()
    /** The callback that will be invoked when the rename dialog ok button is clicked */
    fun onRenameConfirm()
    /** The callback that will be invoked when the user confirms that
     * they want to overwrite [preset] with the current sound mix */
    fun onOverwriteConfirm(preset: Preset)
    /** The callback that will be invoked when the
     * user confirms that they want to delete [preset] */
    fun onDeleteConfirm(preset: Preset)
    /** The callback that will be invoked when the user clicks on [preset] */
    fun onPresetClick(preset: Preset)
}

/**
 * Display a list of [Preset]s, with an options menu for each [Preset]
 * to allow for renaming, overwriting, and deletion. Optionally, a single
 * [Preset] identified as the active one will have the [selectionBrush]
 * applied to it to indicate this status.
 *
 * @param modifier The [Modifier] to use for the [Preset] list
 * @param activePresetCallback An [ActivePresetCallback] to provide the name,
 *     the is modified status, and an on click callback for the active preset.
 * @param selectionBrush The [Brush] that will be applied with alpha = 0.5f
 *     to the active [Preset] to indicate its status as the active [Preset]
 * @param callback The [PresetListCallback] that will be used for user
 *     interactions with the displayed [Preset]s
 */
@Composable fun PresetList(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    activePresetCallback: ActivePresetCallback,
    selectionBrush: Brush,
    callback: PresetListCallback,
) = CompositionLocalProvider(LocalContentColor provides MaterialTheme.colors.onSurface) {
    val presetList = callback.listProvider()

    Crossfade(presetList?.isEmpty(), modifier) { when(it) {
        null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator(Modifier.size(50.dp))
        } true -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text(stringResource(R.string.empty_preset_list_message),
                 modifier = Modifier.width(300.dp),
                 textAlign = TextAlign.Justify)
        } else -> {
            var overwriteDialogTarget by rememberSaveable { mutableStateOf<Preset?>(null) }
            var deleteDialogTarget by rememberSaveable { mutableStateOf<Preset?>(null) }
            // Although it is inconsistent, the renameDialogTarget state must be stored in
            // the view model and accessed through the provided callback due to its more
            // complicated logic (i.e. unlike the overwrite and delete dialogs, which only
            // need a simple yes/no confirmation, the rename dialog may be dismissed or not
            // on ok clicks depending on the validity of the proposed name).

            val activePresetName = activePresetCallback.nameProvider()
            val activePresetIsModified = activePresetCallback.isModifiedProvider()

            LazyColumn(Modifier, contentPadding = contentPadding) {
                items(
                    items = presetList ?: emptyList(),
                    key = Preset::name::get
                ) { preset ->
                    val isActivePreset = preset.name == activePresetName
                    PresetView(
                        modifier = if (!isActivePreset) Modifier
                                   else Modifier.background(selectionBrush, alpha = 0.5f),
                        presetName = preset.name,
                        isModified = isActivePreset && activePresetIsModified,
                        onRenameClick = { callback.onRenameStart(preset) },
                        onOverwriteClick = { overwriteDialogTarget = preset },
                        onDeleteClick = { deleteDialogTarget = preset },
                        onClick = { callback.onPresetClick(preset) })
                    Divider()
                }
            }

            val renameDialogTarget = callback.renameTargetProvider()
            renameDialogTarget?.let { preset ->
                RenameDialog(
                    title = stringResource(R.string.create_new_preset_dialog_title),
                    initialName = preset.name,
                    proposedNameProvider = callback.proposedNameProvider,
                    onProposedNameChange = callback::onProposedNameChange,
                    errorMessageProvider = callback.renameErrorMessageProvider,
                    onDismissRequest = callback::onRenameCancel,
                    onConfirm = callback::onRenameConfirm)
            }
            overwriteDialogTarget?.let { preset ->
                ConfirmPresetOverwriteDialog(
                    presetName = preset.name,
                    onDismissRequest = { overwriteDialogTarget = null },
                    onConfirm = {
                        overwriteDialogTarget = null
                        callback.onOverwriteConfirm(preset)
                    })
            }
            deleteDialogTarget?.let { preset ->
                ConfirmDeletePresetDialog(preset.name,
                    onDismissRequest = { deleteDialogTarget = null },
                    onConfirm = {
                        deleteDialogTarget = null
                        callback.onDeleteConfirm(preset)
                    })
            }
        }
    }}
}

@Preview @Composable
fun PresetListPreview() = SoundAuraTheme {
    val list = remember { List(4) {
        Preset("Super Duper Extra Really Long Preset Name$it")
    }.toImmutableList() }
    val activePresetName = remember { mutableStateOf<String?>(list.first().name) }
    val callback = remember { object: PresetListCallback {
        override val listProvider = { list }
        override val renameTargetProvider = { null }
        override val proposedNameProvider = { null }
        override val renameErrorMessageProvider = { null }
        override fun onProposedNameChange(newName: String) {}
        override fun onRenameStart(preset: Preset) {}
        override fun onRenameCancel() {}
        override fun onRenameConfirm() {}
        override fun onOverwriteConfirm(preset: Preset) {}
        override fun onDeleteConfirm(preset: Preset) {}
        override fun onPresetClick(preset: Preset) {
            activePresetName.value = preset.name
        }
    }}

    PresetList(
        activePresetCallback = ActivePresetCallback(
            nameProvider = activePresetName::value::get,
            isModifiedProvider = { true },
            onClick = {}),
        selectionBrush = Brush.horizontalGradient(
            listOf(MaterialTheme.colors.primaryVariant,
                   MaterialTheme.colors.secondaryVariant)),
        callback = callback)
}