package com.greenaltambe.vela.ui.look

import androidx.compose.ui.graphics.vector.ImageVector
import com.greenaltambe.vela.core.settings.IconStyle

/**
 * The ready-made icon packs: Material Icons Extended (Apache-2.0) in Rounded, Outlined, Sharp and Two-tone, and Lucide (ISC), a fine rounded
 * line set. Each maps every name in [IconNames] to the pack's closest icon, so choosing a pack restyles the whole app. The Look's icon weight and
 * "rounded ends" options only apply to Vela's own packs: these come drawn as designed.
 */
internal object LibraryIconPacks {
    fun build(style: IconStyle): Pair<Map<String, ImageVector>, Map<String, ImageVector>> = when (style) {
        IconStyle.ROUNDED -> MaterialRounded.icons to MaterialRounded.filledIcons
        IconStyle.OUTLINED -> MaterialOutlined.icons to MaterialFilled.icons
        IconStyle.SHARP -> MaterialSharp.icons to MaterialFilled.icons
        IconStyle.TWO_TONE -> MaterialTwoTone.icons to MaterialFilled.icons
        IconStyle.LUCIDE -> LucideIcons.icons to MaterialFilled.icons
        else -> error("$style is drawn by VelaIcons")
    }
}
