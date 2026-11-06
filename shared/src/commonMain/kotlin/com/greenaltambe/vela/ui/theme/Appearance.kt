package com.greenaltambe.vela.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.sp
import com.greenaltambe.vela.core.settings.ProgressStyle
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.core.settings.LookSettings
import com.greenaltambe.vela.core.settings.ThemeMode

/**
 * Everything the app looks like, as one value. [AppTheme] puts it into Material; components read only Material's own roles
 * ([androidx.compose.material3.MaterialTheme]), [ExtraColors], [Spacing] and the progress style, never this class directly.
 * That is the seam for customization later: a different [AppearanceSource] (reading new typed settings) yields a different [Appearance]
 * and no component changes.
 */
data class Appearance(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val lightColors: ColorScheme = RedColorSchemes.Light,
    val darkColors: ColorScheme = RedColorSchemes.Dark,
    val typography: Typography = DefaultTypography,
    val shapes: Shapes = Shapes(),
    val extras: ExtraColorTokens = ExtraColorTokens.Default,
    val progress: ProgressStyle = ProgressStyle.EXPRESSIVE,
    val motion: MotionTokens = MotionTokens(),
    val icons: AppIconSet = MaterialIconSet.Default,
) {
    fun colors(dark: Boolean): ColorScheme = if (dark) darkColors else lightColors

    companion object {
        val Default = Appearance()
    }
}

/** Material's type scale as it is; only the smallest label is lifted to the app's 12 sp text floor. */
val DefaultTypography: Typography = Typography().let { it.copy(labelSmall = it.labelSmall.copy(fontSize = 12.sp)) }

/** Where the [Appearance] comes from. Today: the fixed red look plus the Theme and Progress bar settings. */
fun interface AppearanceSource {
    fun resolve(settings: SettingsResolver): Appearance
}

object DefaultAppearanceSource : AppearanceSource {
    override fun resolve(settings: SettingsResolver) = Appearance(
        themeMode = settings.resolve(LookSettings.theme),
        progress = settings.resolve(LookSettings.progressStyle),
    )
}

/** How the seek bar is drawn (the one visual choice kept): provided by [AppTheme]. */
val LocalProgressStyle = staticCompositionLocalOf { ProgressStyle.EXPRESSIVE }

/** Every animation takes its numbers from here: four durations, three spring feels. */
data class MotionTokens(
    val durationsMs: List<Int> = listOf(90, 160, 240, 360),
    /** (damping ratio, stiffness) for snappy, standard and bouncy. */
    val snappy: List<Float> = listOf(0.9f, 900f),
    val standard: List<Float> = listOf(0.8f, 400f),
    val bouncy: List<Float> = listOf(0.55f, 380f),
    val reduced: Boolean = false,
    val reducedFactor: Float = 0.4f,
    val staggerMs: Int = 28,
)

val LocalMotion = staticCompositionLocalOf { MotionTokens() }
