@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.greenaltambe.vela.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** The app's one choice chip: an expressive toggle button. The chosen one is told by its colour and shape alone (no check mark: it changed the chip's width and shifted its neighbours). Used by Home's filters, the channel's sort options, comments' sort, Search's result types and more. */
@Composable
fun ExpressiveChip(selected: Boolean, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ToggleButton(
        checked = selected, onCheckedChange = { onClick() },
        modifier = modifier.heightIn(min = 40.dp),
        // An unselected chip keeps Material's outline (outlineVariant), so its edge shows on a light page where the tonal fill alone is 1.1:1.
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant),
        shapes = ToggleButtonDefaults.shapes(),
    ) {
        Text(label, maxLines = 1, softWrap = false)
    }
}
