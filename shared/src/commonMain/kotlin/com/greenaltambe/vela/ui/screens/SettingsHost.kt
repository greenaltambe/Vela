package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.ui.SettingsScreen
import com.greenaltambe.vela.ui.look.Vela
import com.greenaltambe.vela.ui.nav.Navigator
import com.greenaltambe.vela.ui.nav.Route

/** The generated settings screen, plus the entry to Diagnostics. */
@Composable
fun SettingsHost(services: AppServices, navigator: Navigator, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(Vela.md)) {
        Text(
            "Diagnostics",
            Modifier.fillMaxWidth().clickable { navigator.push(Route.Diagnostics) }.padding(vertical = Vela.md).testTag("settings.diagnostics"),
            style = MaterialTheme.typography.titleMedium,
        )
        HorizontalDivider()
        SettingsScreen(services.settings, Modifier.weight(1f))
    }
}
