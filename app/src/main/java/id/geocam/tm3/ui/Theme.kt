package id.geocam.tm3.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Orange = Color(0xFFFF8A00)
val Glass = Color(0x99000000)
val Muted = Color(0xFFB4BCC8)
val Good = Color(0xFF34C759)
val Fair = Color(0xFFFFC233)
val Poor = Color(0xFFFF4D4F)

private val colors = darkColorScheme(
    primary = Orange,
    onPrimary = Color.Black,
    secondary = Orange,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF161B22),
    onSurface = Color.White,
    surfaceContainerHigh = Color(0xFF1F252E),
    error = Poor,
)

@Composable
fun GeoCamTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}

/** Green ≤ 5 m, amber ≤ 10 m, red above. */
fun accuracyColor(metres: Float?): Color = when {
    metres == null -> Poor
    metres <= 5f -> Good
    metres <= 10f -> Fair
    else -> Poor
}
