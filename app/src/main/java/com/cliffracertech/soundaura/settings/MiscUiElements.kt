/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.cliffracertech.soundaura.R

/** Compose a radio button icon. */
@Composable fun RadioButton(
    checked: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    val vector = if (checked) Icons.Default.RadioButtonChecked
                 else         Icons.Default.RadioButtonUnchecked
    val desc = stringResource(if (checked) R.string.checked
                              else         R.string.unchecked)
    Icon(vector, desc, modifier, tint)
}

/**
 * Compose a bulleted list of [String]s.
 *
 * @param items The list of [String]s to display in bulleted form.
 * @param modifier The [Modifier] to use for the entire list.
 */
@Composable fun BulletedList(
    items: List<String>,
    modifier: Modifier = Modifier,
) = Column(modifier, Arrangement.spacedBy(8.dp)) {
    for (item in items) Row {
        Text("\u2022", Modifier.padding(end = 12.dp))
        Text(item)
    }
}

/**
 * Compose a [Text] containing a clickable link.
 *
 * @param modifier The [Modifier] to use for the entire text.
 * @param linkText The text that will be clickable.
 * @param completeText The entire text that will be displayed. This must
 *     contain the linkText, or else the link will not work properly.
 * @param onLinkClick The callback that will be invoked when the link is clicked.
 */
@Composable fun TextWithClickableLink(
    modifier: Modifier = Modifier,
    linkText: String,
    completeText: String,
    onLinkClick: () -> Unit
) {
    val linkTextStartIndex = completeText.indexOf(linkText)
    val linkTextLastIndex = linkTextStartIndex + linkText.length
    val linkifiedText = buildAnnotatedString {
        // ClickableText seems to not follow the local text style by default
        pushStyle(SpanStyle(color = LocalContentColor.current,
                            fontSize = LocalTextStyle.current.fontSize))
        append(completeText)
        val urlStyle = SpanStyle(color = MaterialTheme.colorScheme.primary,
                                 textDecoration = TextDecoration.Underline)
        addStyle(urlStyle, linkTextStartIndex, linkTextLastIndex)
    }
    ClickableText(
        text = linkifiedText,
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge
    ) {
        if (it in linkTextStartIndex..linkTextLastIndex)
            onLinkClick()
    }
}