package com.greenaltambe.vela

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.greenaltambe.vela.ui.nav.Navigator
import com.greenaltambe.vela.ui.AppShell
import com.greenaltambe.vela.ui.theme.AppTheme

/** The whole app: theme from the settings, then the navigation shell. The platform entry points build [services]. */
@Composable
fun App(services: AppServices, navigator: Navigator = remember { Navigator() }, startupAnimation: Boolean = false) {
    AppTheme(services.settings) {
        AppShell(services, navigator, startupAnimation)
    }
}
