package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.ui.SettingsScreen
import com.greenaltambe.vela.ui.components.MenuAction
import com.greenaltambe.vela.ui.components.VelaTopBar
import com.greenaltambe.vela.ui.look.Vela
import com.greenaltambe.vela.ui.nav.Navigator
import com.greenaltambe.vela.ui.nav.Route

/** The generated settings screen with its search field under the top bar, and the way to Diagnostics. */
@Composable
fun SettingsHost(services: AppServices, navigator: Navigator, modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    Column(modifier.fillMaxSize()) {
        // The shell leaves Settings' bar to this screen: its search is the settings search, not the video search.
        VelaTopBar("Settings", menu = listOf(MenuAction("Accounts") { navigator.push(Route.Accounts) }, MenuAction("Health") { navigator.push(Route.Health) }, MenuAction("Backup and restore") { navigator.push(Route.Backup) }, MenuAction("Account activity") { navigator.push(Route.AccountActivity) }, MenuAction("Diagnostics") { navigator.push(Route.Diagnostics) }))
        // Same width and left edge as the list below it.
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            OutlinedTextField(
                query, { query = it }, singleLine = true, placeholder = { Text("Search settings", maxLines = 1) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }, modifier = Modifier.testTag("settings.clear")) { Icon(Vela.icons.close, "Clear") } },
                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = Vela.gutter, vertical = Vela.xs).testTag("settings.search"),
            )
        }
        SettingsScreen(services.settings, query, Modifier.weight(1f))
    }
}
