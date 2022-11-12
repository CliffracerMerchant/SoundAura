/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cliffracertech.soundaura.ui.theme.SoundAuraTheme

/** Return a [CornerBasedShape] that matches just the topStart
 * corner of the receiver shape, with otherwise sharp corners.*/
fun CornerBasedShape.topStartShape() =
    RoundedCornerShape(
        topStart = topStart,
        topEnd = ZeroCornerSize,
        bottomEnd = ZeroCornerSize,
        bottomStart = ZeroCornerSize)

/** Return a [CornerBasedShape] that matches the top edge
 * of the receiver shape, with sharp bottom corners.*/
fun CornerBasedShape.topShape() =
    RoundedCornerShape(
        topStart = topStart,
        topEnd = topEnd,
        bottomStart = ZeroCornerSize,
        bottomEnd = ZeroCornerSize)

/** Return a [CornerBasedShape] that matches just the topEnd corner
 * of the receiver shape, with otherwise sharp corners.*/
fun CornerBasedShape.topEndShape() =
    RoundedCornerShape(
        topStart = ZeroCornerSize,
        topEnd = topEnd,
        bottomStart = ZeroCornerSize,
        bottomEnd = ZeroCornerSize)

/** Return a [CornerBasedShape] that matches just the starting edge
 * of the receiver shape, with otherwise sharp corners.*/
fun CornerBasedShape.startShape() =
    RoundedCornerShape(
        topStart = topStart,
        topEnd = ZeroCornerSize,
        bottomStart = bottomStart,
        bottomEnd = ZeroCornerSize)

/** Return a [CornerBasedShape] that matches just the ending
 * edge of the receiver shape, with otherwise sharp corners.*/
fun CornerBasedShape.endShape() =
    RoundedCornerShape(
        topStart = ZeroCornerSize,
        topEnd = topEnd,
        bottomStart = ZeroCornerSize,
        bottomEnd = bottomEnd)

/** Return a [CornerBasedShape] that matches just the bottomStart
 * corner of the receiver shape, with otherwise sharp corners.*/
fun CornerBasedShape.bottomStartShape() =
    RoundedCornerShape(
        topStart = ZeroCornerSize,
        topEnd = ZeroCornerSize,
        bottomEnd = ZeroCornerSize,
        bottomStart = bottomStart)

/** Return a [CornerBasedShape] that matches just the bottomEnd
 * corner of the receiver shape, with otherwise sharp corners.*/
fun CornerBasedShape.bottomEndShape() =
    RoundedCornerShape(
        topStart = ZeroCornerSize,
        topEnd = ZeroCornerSize,
        bottomStart = ZeroCornerSize,
        bottomEnd = bottomEnd)

/** Return a [CornerBasedShape] that matches the bottom
 * edge of the receiver shape, with sharp top corners.*/
fun CornerBasedShape.bottomShape() =
    RoundedCornerShape(
        topStart = ZeroCornerSize,
        topEnd = ZeroCornerSize,
        bottomStart = bottomStart,
        bottomEnd = bottomEnd)

@Preview @Composable
fun CutoutShapesPreview() = SoundAuraTheme {
    val shape = MaterialTheme.shapes.large
    val tint = MaterialTheme.colors.primary
    val textColor = MaterialTheme.colors.onPrimary

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(Modifier.width(258.dp), Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier
                .width(150.dp).height(50.dp)
                .align(Alignment.CenterHorizontally)
                .background(tint, shape.topShape()),
                horizontalArrangement = Arrangement.Center
            ) {
                Spacer(Modifier.weight(1f))
                Text(text = "Top shape", color = textColor,
                    modifier = Modifier.align(Alignment.CenterVertically))
                Spacer(Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(Modifier
                    .width(50.dp).height(150.dp)
                    .background(tint, shape.startShape()),
                    Alignment.Center
                ) {
                    Text(text = "Start Shape",
                        modifier = Modifier.rotate(-90f),
                        color = textColor)
                }
                Box(Modifier.background(tint, shape)
                    .sizeIn(minHeight = 150.dp, minWidth = 150.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Original shape", color = textColor)
                }
                Box(Modifier
                    .width(50.dp).height(150.dp)
                    .background(tint, shape.endShape()),
                    Alignment.Center
                ) {
                    Text(text = "End Shape",
                        modifier = Modifier.rotate(90f),
                        color = textColor)
                }
            }
            Row(Modifier
                .width(150.dp).height(56.dp)
                .align(Alignment.CenterHorizontally)
                .background(tint, shape.bottomShape())
            ) {
                Spacer(Modifier.weight(1f))
                Text(text = "Bottom shape", color = textColor,
                    modifier = Modifier.align(Alignment.CenterVertically))
                Spacer(Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.size(width = 254.dp, height = 125.dp),
                Arrangement.spacedBy(4.dp)
            ) {
                Box(Modifier.weight(1f).fillMaxHeight()
                    .background(tint, shape.topStartShape()),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Top start", color = textColor)
                }
                Box(Modifier.weight(1f).fillMaxHeight()
                    .background(tint, shape.topEndShape()),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Top end", color = textColor)
                }
            }
            Row(Modifier.size(width = 254.dp, height = 125.dp),
                Arrangement.spacedBy(4.dp)
            ) {
                Box(Modifier.weight(1f).fillMaxHeight()
                    .background(tint, shape.bottomStartShape()),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Bottom start", color = textColor)
                }
                Box(Modifier.weight(1f).fillMaxHeight()
                    .background(tint, shape.bottomEndShape()),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Bottom end", color = textColor)
                }
            }
        }
    }
}