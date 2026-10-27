package com.greenaltambe.vela.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Design-token entry point. Colours, fonts, shapes and spacing will come from the active Look here;
 * until then it passes through Material defaults and composables must not hardcode visuals.
 */
@Composable
fun VelaTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
