package com.greenaltambe.vela.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.greenaltambe.vela.core.settings.LookChoice
import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.ui.look.LocalLook
import com.greenaltambe.vela.ui.look.LookBuilder
import com.greenaltambe.vela.ui.look.LookSnapshot
import com.greenaltambe.vela.ui.look.LocalPerformanceMode
import com.greenaltambe.vela.ui.look.LookTokens
import com.greenaltambe.vela.ui.look.Looks
import com.greenaltambe.vela.ui.look.toColor

/** Design-token entry point: provides [look] and maps it onto Material. */
@Composable
fun VelaTheme(look: LookTokens, performanceMode: Boolean = false, content: @Composable () -> Unit) {
    val c = look.colors
    val dark = c.background.toColor().luminance() < 0.5f
    val scheme = remember(look) {
        val base = if (dark) darkColorScheme() else lightColorScheme()
        base.copy(
            background = c.background.toColor(), onBackground = c.onBackground.toColor(),
            surface = c.surface.toColor(), onSurface = c.onSurface.toColor(),
            surfaceVariant = c.surfaceVariant.toColor(), onSurfaceVariant = c.onSurfaceVariant.toColor(),
            primary = c.primary.toColor(), onPrimary = c.onPrimary.toColor(),
            outline = c.outline.toColor(), error = c.error.toColor(),
            // Menus and dialogs sit on the "raised" surface of the Look, not Material's tinted defaults.
            surfaceContainerLowest = c.surface.toColor(), surfaceContainerLow = c.surface.toColor(), surfaceContainer = c.surfaceVariant.toColor(),
            surfaceContainerHigh = c.surfaceVariant.toColor(), surfaceContainerHighest = c.surfaceVariant.toColor(), surfaceTint = androidx.compose.ui.graphics.Color.Transparent,
        )
    }
    val typography = remember(look) { typographyFor(look.typography) }
    // Compose Desktop does not enforce Material's 48 dp minimum for touch (Android does): make it the Look's rule on both.
    CompositionLocalProvider(
        LocalLook provides look, LocalPerformanceMode provides performanceMode,
        LocalMinimumInteractiveComponentSize provides look.spacing.minTouchDp.dp,
    ) {
        // Dropdown menus, dialogs, chips and fields use the one corner-radius setting like everything else.
        val medium = androidx.compose.foundation.shape.RoundedCornerShape(look.radii.mediumDp.dp)
        val large = androidx.compose.foundation.shape.RoundedCornerShape(look.radii.largeDp.dp)
        val shapes = androidx.compose.material3.Shapes(extraSmall = medium, small = medium, medium = medium, large = large, extraLarge = large)
        MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes) {
            // Text with no colour of its own takes the content colour, which is black unless a Surface says otherwise.
            Surface(Modifier.fillMaxSize(), color = scheme.background, contentColor = scheme.onBackground, content = content)
        }
    }
}

/** The Look the settings ask for; the default one follows the system light/dark preference. */
@Composable
fun VelaTheme(settings: SettingsResolver, content: @Composable () -> Unit) {
    val revision by settings.revision.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val snapshot = remember(revision) { LookSnapshot.of(settings) }
    val look = remember(snapshot, systemDark) { LookBuilder.build(snapshot, systemDark) }
    VelaTheme(look, snapshot.performance, content)
}

private fun androidx.compose.ui.graphics.Color.luminance() = 0.2126f * red + 0.7152f * green + 0.0722f * blue

/** Material typography from the Look's type tokens: font family slot, text scale and the (light by default) weight of titles. */
fun typographyFor(t: com.greenaltambe.vela.ui.look.TypographyTokens): Typography {
    val family = fontFamilyFor(t.fontFamily)
    val titleWeight = when (t.titleWeight) {
        com.greenaltambe.vela.core.settings.TitleWeight.LIGHT -> androidx.compose.ui.text.font.FontWeight.Light
        com.greenaltambe.vela.core.settings.TitleWeight.NORMAL -> androidx.compose.ui.text.font.FontWeight.Normal
        com.greenaltambe.vela.core.settings.TitleWeight.MEDIUM -> androidx.compose.ui.text.font.FontWeight.Medium
    }
    fun size(sp: Float) = (sp * t.scale).sp
    return Typography(
        titleLarge = TextStyle(fontFamily = family, fontSize = size(t.titleSizeSp), fontWeight = titleWeight),
        titleMedium = TextStyle(fontFamily = family, fontSize = size(t.headingSizeSp), fontWeight = titleWeight),
        bodyLarge = TextStyle(fontFamily = family, fontSize = size(t.bodySizeSp)),
        bodyMedium = TextStyle(fontFamily = family, fontSize = size(t.bodySizeSp)),
        bodySmall = TextStyle(fontFamily = family, fontSize = size(t.captionSizeSp)),
        labelLarge = TextStyle(fontFamily = family, fontSize = size(t.bodySizeSp)),
        headlineMedium = TextStyle(fontFamily = family, fontSize = size(t.titleSizeSp + 4), fontWeight = titleWeight),
    )
}

/** The family a font choice names (its lower-case id). Vela and System use the platform UI font; the others are the platform's generic families. */
fun fontFamilyFor(id: String): FontFamily = when (id) {
    "grotesque" -> FontFamily.SansSerif
    "serif" -> FontFamily.Serif
    "mono" -> FontFamily.Monospace
    else -> FontFamily.Default
}
