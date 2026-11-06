@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.greenaltambe.vela.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.ui.theme.AppIcons

/** The app's one choice chip: an expressive toggle button, with a check in front of the chosen one. Used by Home's filters and Search's result types. */
@Composable
fun ExpressiveChip(selected: Boolean, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ToggleButton(
        checked = selected, onCheckedChange = { onClick() },
        modifier = modifier.heightIn(min = 40.dp),
        shapes = ToggleButtonDefaults.shapes(),
    ) {
        if (selected) { Icon(AppIcons.check, null, Modifier.size(18.dp)); Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing)) }
        Text(label, maxLines = 1, softWrap = false)
    }
}
