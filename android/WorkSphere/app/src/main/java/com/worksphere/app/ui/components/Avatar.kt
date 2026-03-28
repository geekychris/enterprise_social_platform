package com.worksphere.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * Reusable avatar composable.
 *
 * Displays an [AsyncImage] loaded via Coil when [url] is non-null, otherwise
 * renders a coloured circle with the first letter of [name].
 *
 * @param url     Optional image URL.
 * @param name    Display name used for the fallback initial and colour seed.
 * @param size    Diameter of the avatar circle.
 * @param modifier Additional modifiers.
 */
@Composable
fun Avatar(
    url: String?,
    name: String,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    val initial = name.firstOrNull()?.uppercase() ?: "?"

    // Deterministic pastel colour based on the name
    val bgColor = remember(name) { colorForName(name) }

    if (!url.isNullOrBlank()) {
        AsyncImage(
            model = url,
            contentDescription = "$name avatar",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
        )
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(bgColor)
        ) {
            Text(
                text = initial,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.45f).sp
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private val avatarPalette = listOf(
    Color(0xFF5C6BC0), // Indigo 400
    Color(0xFF26A69A), // Teal 400
    Color(0xFFEF5350), // Red 400
    Color(0xFFAB47BC), // Purple 400
    Color(0xFF42A5F5), // Blue 400
    Color(0xFF66BB6A), // Green 400
    Color(0xFFFFA726), // Orange 400
    Color(0xFF78909C), // Blue Grey 400
    Color(0xFFEC407A), // Pink 400
    Color(0xFF8D6E63), // Brown 400
)

private fun colorForName(name: String): Color {
    val hash = name.fold(0) { acc, c -> acc * 31 + c.code }
    return avatarPalette[(hash and Int.MAX_VALUE) % avatarPalette.size]
}
