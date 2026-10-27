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
        )
    }
    val typography = remember(look) {
        val t = look.typography
        val family = if (t.fontFamily == "system") FontFamily.Default else FontFamily.Default // custom families arrive with the font picker
        Typography(
            titleLarge = TextStyle(fontFamily = family, fontSize = t.titleSizeSp.sp),
            titleMedium = TextStyle(fontFamily = family, fontSize = t.headingSizeSp.sp),
            bodyLarge = TextStyle(fontFamily = family, fontSize = t.bodySizeSp.sp),
            bodyMedium = TextStyle(fontFamily = family, fontSize = t.bodySizeSp.sp),
            bodySmall = TextStyle(fontFamily = family, fontSize = t.captionSizeSp.sp),
            labelLarge = TextStyle(fontFamily = family, fontSize = t.bodySizeSp.sp),
            headlineMedium = TextStyle(fontFamily = family, fontSize = (t.titleSizeSp + 4).sp),
        )
    }
    // Compose Desktop does not enforce Material's 48 dp minimum for touch (Android does): make it the Look's rule on both.
    CompositionLocalProvider(
        LocalLook provides look, LocalPerformanceMode provides performanceMode,
        LocalMinimumInteractiveComponentSize provides look.spacing.minTouchDp.dp,
    ) {
        MaterialTheme(colorScheme = scheme, typography = typography) {
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
    val look = remember(revision, systemDark) {
        Looks.forChoice(pureBlack = settings.resolve(Settings.look) == LookChoice.PURE_BLACK, systemDark = systemDark)
    }
    val performance = remember(revision) { settings.resolve(Settings.performanceMode) }
    VelaTheme(look, performance, content)
}

private fun androidx.compose.ui.graphics.Color.luminance() = 0.2126f * red + 0.7152f * green + 0.0722f * blue
