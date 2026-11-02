package com.codeframe78.twentyfourseven.player.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage

/** The pairing and catalog screens share the app's night-violet palette. */
internal object PairingPalette {
    val Night = Color(0xFF0E0B14)
    val DeepNavy = Color(0xFF131522)
    val Charcoal = Color(0xFF1C1C27)
    val Raised = Color(0xFF282734)
    val WarmWhite = Color(0xFFF8F2F4)
    val Lavender = Color(0xFFC9A8FF)
    val Violet = Color(0xFF8B5CF6)
    val Muted = Color(0xFFCEC3D4)
    val Amber = Color(0xFFFFC56C)
    val Green = Color(0xFF47C96B)
    val Red = Color(0xFFFF6B76)
}

/** Dark gradient with a soft violet glow, so no screen is a flat grey slab. */
@Composable
internal fun PairingBackdrop(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(PairingPalette.Night, PairingPalette.DeepNavy)))
            .background(Brush.radialGradient(listOf(PairingPalette.Violet.copy(alpha = 0.22f), Color.Transparent), radius = 1100f)),
        content = content,
    )
}

/** Album artwork from the station, with a violet placeholder tile while it loads or when there is none. */
@Composable
internal fun CoverArt(url: String?, size: Dp, label: String = "", modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(if (size > 120.dp) 20.dp else 12.dp)
    Box(
        modifier.size(size).clip(shape).background(Brush.linearGradient(listOf(PairingPalette.Raised, PairingPalette.Violet.copy(alpha = 0.45f)))),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Text(label.take(1).uppercase(), fontSize = (size.value / 2.4f).sp, color = PairingPalette.Lavender)
    }
}

/** A small rounded status chip, e.g. "Requestable" or "Cooldown". */
@Composable
internal fun Chip(text: String, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.18f))) {
        Text(text, color = color, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
    }
}
