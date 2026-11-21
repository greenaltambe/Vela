package com.greenaltambe.vela

import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant

/** `org.freedesktop.portal.Settings`: where GNOME, KDE and others publish the light/dark choice (`org.freedesktop.appearance` / `color-scheme`). */
@DBusInterfaceName("org.freedesktop.portal.Settings")
interface PortalSettings : DBusInterface {
    fun Read(namespace: String, key: String): Variant<*>
}

/**
 * Whether the desktop is in its dark mode, for the "System" theme. Compose Desktop's own check reports light on Linux whatever the desktop says, so the app
 * asks the desktop itself: the settings portal on the session bus first (works on GNOME, KDE, and sandboxes), then `gsettings`, then the GTK theme's name.
 * Null when nothing answers (the theme then stays light, as before). Windows and macOS are left to Compose.
 */
object SystemTheme {
    fun supported(): Boolean = System.getProperty("os.name").orEmpty().startsWith("Linux")

    /** The current answer: true dark, false light, null unknown. A blocking call (a bus round trip or a process): call it off the UI thread. */
    fun isDark(): Boolean? = if (!supported()) null else (portal() ?: gsettings() ?: gtkThemeName())

    private var connection: DBusConnection? = null
    private var portalObject: PortalSettings? = null

    private fun portal(): Boolean? = try {
        val obj = portalObject ?: run {
            val c = connection ?: DBusConnectionBuilder.forSessionBus().build().also { connection = it }
            c.getRemoteObject("org.freedesktop.portal.Desktop", "/org/freedesktop/portal/desktop", PortalSettings::class.java).also { portalObject = it }
        }
        var value: Any? = obj.Read("org.freedesktop.appearance", "color-scheme")
        while (value is Variant<*>) value = value.value
        when ((value as? UInt32)?.toInt() ?: (value as? Number)?.toInt()) { 1 -> true; 2 -> false; 0 -> false; else -> null }
    } catch (_: Throwable) { portalObject = null; null }

    private fun gsettings(): Boolean? = run1("gsettings", "get", "org.gnome.desktop.interface", "color-scheme")?.let {
        when { "prefer-dark" in it -> true; "prefer-light" in it || "default" in it -> false; else -> null }
    }

    private fun gtkThemeName(): Boolean? = (System.getenv("GTK_THEME") ?: run1("gsettings", "get", "org.gnome.desktop.interface", "gtk-theme"))?.let { it.contains("dark", ignoreCase = true) }

    private fun run1(vararg cmd: String): String? = try {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText().trim()
        if (!p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) { p.destroyForcibly(); null } else if (p.exitValue() == 0) out else null
    } catch (_: Throwable) { null }
}
