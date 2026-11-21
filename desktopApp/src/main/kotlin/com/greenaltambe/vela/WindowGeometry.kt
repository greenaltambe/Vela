package com.greenaltambe.vela

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.greenaltambe.vela.core.settings.SettingLayer
import com.greenaltambe.vela.core.settings.SettingsStore

/** Where the window was last: size, position and whether it was maximized (or fullscreen is not remembered), so the app opens where it was left. */
data class SavedGeometry(val width: Int, val height: Int, val x: Int?, val y: Int?, val maximized: Boolean) {
    fun encode() = "$width,$height,${x ?: ""},${y ?: ""},${if (maximized) 1 else 0}"

    companion object {
        const val KEY = "desktop.window"
        val Default = SavedGeometry(1100, 800, null, null, false)

        fun decode(raw: String?): SavedGeometry? {
            val p = raw?.split(',') ?: return null
            if (p.size != 5) return null
            val w = p[0].toIntOrNull() ?: return null
            val h = p[1].toIntOrNull() ?: return null
            if (w < 360 || h < 300) return null
            return SavedGeometry(w, h, p[2].toIntOrNull(), p[3].toIntOrNull(), p[4] == "1")
        }

        fun load(store: SettingsStore): SavedGeometry? = runCatching { decode(store.get(KEY, SettingLayer.Global)) }.getOrNull()?.let(::withinScreens)

        /** A remembered position on a screen that is gone (a second monitor unplugged) would open the window out of sight: forget the position then. */
        fun withinScreens(g: SavedGeometry): SavedGeometry {
            val x = g.x; val y = g.y
            if (x == null || y == null) return g
            val bounds = runCatching { java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { it.defaultConfiguration.bounds } }.getOrNull().orEmpty()
            if (bounds.isEmpty()) return g
            // The middle of the title bar must be on some screen.
            val cx = x + g.width / 2; val cy = y + 20
            return if (bounds.any { it.contains(cx, cy) }) g else g.copy(x = null, y = null)
        }

        fun of(state: WindowState): SavedGeometry? {
            if (state.placement == WindowPlacement.Fullscreen) return null
            val size = state.size
            if (size.width.value.isNaN() || size.height.value.isNaN()) return null
            val pos = state.position
            return SavedGeometry(size.width.value.toInt(), size.height.value.toInt(), if (pos.isSpecified) pos.x.value.toInt() else null, if (pos.isSpecified) pos.y.value.toInt() else null, state.placement == WindowPlacement.Maximized)
        }
    }

    fun size() = DpSize(width.dp, height.dp)
    fun position(): WindowPosition = if (x != null && y != null) WindowPosition(x.dp, y.dp) else WindowPosition.PlatformDefault
    fun placement() = if (maximized) WindowPlacement.Maximized else WindowPlacement.Floating
}

fun SettingsStore.saveGeometry(g: SavedGeometry) = runCatching { put(SavedGeometry.KEY, SettingLayer.Global, g.encode()) }
