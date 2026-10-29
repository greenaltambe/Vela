package com.greenaltambe.vela.ui.look

import com.greenaltambe.vela.core.settings.Palette

/** The gradient stops (0xAARRGGBB, light to deep) of each [Palette]. The middle stop is the app's accent colour. */
object Palettes {
    fun stops(p: Palette): List<Long> = when (p) {
        Palette.VELA -> listOf(0xFF2EC4B6, 0xFF5B5BD6, 0xFFD946A8)
        Palette.AURORA -> listOf(0xFF34D399, 0xFF22D3EE, 0xFF6366F1)
        Palette.OCEAN -> listOf(0xFF06B6D4, 0xFF3B82F6, 0xFF1D4ED8)
        Palette.SUNSET -> listOf(0xFFFBBF24, 0xFFFB7185, 0xFFC026D3)
        Palette.EMBER -> listOf(0xFFFACC15, 0xFFF97316, 0xFFDC2626)
        Palette.ORCHID -> listOf(0xFFA78BFA, 0xFFD946EF, 0xFFF43F5E)
        Palette.MEADOW -> listOf(0xFFA3E635, 0xFF22C55E, 0xFF0D9488)
        Palette.SLATE -> listOf(0xFFCBD5E1, 0xFF94A3B8, 0xFF64748B)
    }
}
