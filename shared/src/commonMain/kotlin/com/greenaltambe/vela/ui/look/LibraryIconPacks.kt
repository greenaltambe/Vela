package com.greenaltambe.vela.ui.look

import androidx.compose.ui.graphics.vector.ImageVector
import com.greenaltambe.vela.core.settings.IconStyle

/**
 * The ready-made icon packs: Material Icons Extended (Apache-2.0) in Rounded, Outlined, Sharp and Two-tone, and Lucide (ISC), a fine rounded
 * line set. Each maps every name in [IconNames] to the pack's closest icon, so choosing a pack restyles the whole app. The Look's icon weight and
 * "rounded ends" options only apply to Vela's own packs: these come drawn as designed.
 */
internal object LibraryIconPacks {
    fun build(style: IconStyle): Map<String, ImageVector> = when (style) {
        IconStyle.ROUNDED -> MaterialRounded.icons
        IconStyle.OUTLINED -> MaterialOutlined.icons
        IconStyle.SHARP -> MaterialSharp.icons
        IconStyle.TWO_TONE -> MaterialTwoTone.icons
        IconStyle.LUCIDE -> LucideIcons.icons
        else -> error("$style is drawn by VelaIcons")
    }
}
