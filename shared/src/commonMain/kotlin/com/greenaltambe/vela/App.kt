package com.greenaltambe.vela

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.greenaltambe.vela.ui.nav.Navigator
import com.greenaltambe.vela.ui.AppShell
import com.greenaltambe.vela.ui.VelaTheme

/** The whole app: theme from the settings, then the navigation shell. The platform entry points build [services]. */
@Composable
fun App(services: AppServices, navigator: Navigator = remember { Navigator() }) {
    VelaTheme(services.settings) {
        AppShell(services, navigator)
    }
}
