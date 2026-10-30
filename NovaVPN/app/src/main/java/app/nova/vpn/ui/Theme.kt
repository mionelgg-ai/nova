package app.nova.vpn.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

object NovaColors {
    val Bg = Color(0xFF0A0B09)
    val Surface = Color(0xFF131512)
    val Surface2 = Color(0xFF1B1E19)
    val BarBg = Color(0xFF1F211E)
    val Border = Color(0xFF2A2E26)
    val Lime = Color(0xFFB6FF1A)
    val Text = Color(0xFFF2F4EE)
    val Dim = Color(0xFF8A9084)
    val Gold = Color(0xFFFFC53D)
    val Danger = Color(0xFFFF5C5C)
}

/** Persian uses IRANSans when the .ttf files exist in assets/fonts, otherwise the system font. */
@Composable
fun rememberAppFont(): FontFamily {
    val context = LocalContext.current
    val fa = LocalConfiguration.current.locales[0].language == "fa"
    return remember(fa) {
        if (!fa) return@remember FontFamily.Default
        runCatching {
            val files = context.assets.list("fonts")?.toSet().orEmpty()
            fun f(name: String, w: FontWeight) =
                if (name in files) Font(path = "fonts/$name", assetManager = context.assets, weight = w) else null
            val list = listOfNotNull(
                f("IRANSans-Regular.ttf", FontWeight.Normal),
                f("IRANSans-Medium.ttf", FontWeight.Medium),
                f("IRANSans-Bold.ttf", FontWeight.Bold),
            )
            if (list.isEmpty()) FontFamily.Default else FontFamily(list)
        }.getOrDefault(FontFamily.Default)
    }
}

private fun Typography.withFont(ff: FontFamily) = copy(
    displayLarge = displayLarge.copy(fontFamily = ff), displayMedium = displayMedium.copy(fontFamily = ff),
    displaySmall = displaySmall.copy(fontFamily = ff), headlineLarge = headlineLarge.copy(fontFamily = ff),
    headlineMedium = headlineMedium.copy(fontFamily = ff), headlineSmall = headlineSmall.copy(fontFamily = ff),
    titleLarge = titleLarge.copy(fontFamily = ff), titleMedium = titleMedium.copy(fontFamily = ff),
    titleSmall = titleSmall.copy(fontFamily = ff), bodyLarge = bodyLarge.copy(fontFamily = ff),
    bodyMedium = bodyMedium.copy(fontFamily = ff), bodySmall = bodySmall.copy(fontFamily = ff),
    labelLarge = labelLarge.copy(fontFamily = ff), labelMedium = labelMedium.copy(fontFamily = ff),
    labelSmall = labelSmall.copy(fontFamily = ff),
)

@Composable
fun NovaTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = NovaColors.Lime,
        onPrimary = NovaColors.Bg,
        background = NovaColors.Bg,
        onBackground = NovaColors.Text,
        surface = NovaColors.Surface,
        onSurface = NovaColors.Text,
        surfaceVariant = NovaColors.Surface2,
        outline = NovaColors.Border,
    )
    MaterialTheme(colorScheme = scheme, typography = Typography().withFont(rememberAppFont())) {
        Surface(color = NovaColors.Bg, contentColor = NovaColors.Text, modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}
