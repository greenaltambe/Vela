package com.greenaltambe.vela.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.core.settings.ThemeMode

/** The app's theme: Material 3 Expressive with the [Appearance] the [source] gives for the current settings. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppTheme(settings: SettingsResolver, source: AppearanceSource = DefaultAppearanceSource, content: @Composable () -> Unit) {
    val revision by settings.revision.collectAsState()
    val appearance = remember(revision, source) { source.resolve(settings) }
    AppTheme(appearance, isSystemInDarkTheme(), content)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppTheme(appearance: Appearance, systemDark: Boolean, content: @Composable () -> Unit) {
    val dark = when (appearance.themeMode) { ThemeMode.DARK -> true; ThemeMode.LIGHT -> false; ThemeMode.SYSTEM -> systemDark }
    val colors = appearance.colors(dark)
    // Compose Desktop does not enforce Material's 48 dp minimum for touch (Android does): it is the rule on both.
    CompositionLocalProvider(
        LocalExtraColors provides appearance.extras, LocalProgressStyle provides appearance.progress, LocalMotion provides appearance.motion, LocalIconSet provides appearance.icons,
        LocalMinimumInteractiveComponentSize provides Spacing.minTouch,
    ) {
        MaterialExpressiveTheme(colorScheme = colors, motionScheme = MotionScheme.expressive(), shapes = appearance.shapes, typography = appearance.typography) {
            // Text with no colour of its own takes the content colour, which is black unless a Surface says otherwise.
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground, content = content)
        }
    }
}
