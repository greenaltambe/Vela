package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.items
import com.greenaltambe.vela.ui.components.ExpressiveChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.greenaltambe.vela.home.HomeFilter
import com.greenaltambe.vela.ui.components.ChipRow

/**
 * The row above Home. First a Filter button (what the loaded list is narrowed by: All videos, Unwatched, Under 10 min), then, with an account feed,
 * YouTube's own chips in YouTube's order (All, your custom feeds, topics, Live, ...). The "create a custom feed" chip is not drawn. A chip that
 * answered with nothing is left out for the rest of the session. Signed out there are no chips, only the Filter button.
 */
@Composable
internal fun HomeChips(
    chips: List<com.greenaltambe.vela.auth.HomeChip>, selected: String?, onSelect: (String?) -> Unit, withChips: Boolean,
    filter: HomeFilter, onFilter: (HomeFilter) -> Unit, hidden: Set<String>, modifier: Modifier = Modifier,
) {
    val shown = chips.filter { it.kind != com.greenaltambe.vela.auth.HomeChipKind.CREATE_CUSTOM_FEED && it.label !in hidden }
    // Before the chips have loaded (or if YouTube sent none) the feed itself is still there as "All".
    val row = if (shown.any { it.kind == com.greenaltambe.vela.auth.HomeChipKind.ALL }) shown
        else listOf(com.greenaltambe.vela.auth.HomeChip("All", null, true, com.greenaltambe.vela.auth.HomeChipKind.ALL)) + shown
    ChipRow(modifier.fillMaxWidth().testTag("home.chips"), bleed = false) {
        item(key = "filter") { FilterButton(filter, onFilter) }
        if (withChips) items(row, key = { "c-${it.kind}-${it.label}" }) { c ->
            val all = c.kind == com.greenaltambe.vela.auth.HomeChipKind.ALL
            ExpressiveChip(
                selected = if (all) selected == null else selected == c.label, label = c.label,
                onClick = { onSelect(if (all) null else c.label) }, modifier = Modifier.testTag("home.chip.${if (all) "ALL" else c.label}"),
            )
        }
    }
}

/** Narrows the loaded list; it applies on top of whichever chip is open. */
@Composable
private fun FilterButton(filter: HomeFilter, onFilter: (HomeFilter) -> Unit) {
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        ExpressiveChip(selected = filter != HomeFilter.ALL, label = if (filter == HomeFilter.ALL) "Filter" else filter.label, onClick = { open = true }, modifier = Modifier.testTag("home.filter"), icon = com.greenaltambe.vela.ui.theme.AppIcons.tune)
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (f in FILTER_CHOICES) androidx.compose.material3.DropdownMenuItem(
                text = { androidx.compose.material3.Text(if (f == HomeFilter.ALL) "All videos" else f.label) },
                trailingIcon = { if (f == filter) androidx.compose.material3.Icon(com.greenaltambe.vela.ui.theme.AppIcons.check, null) },
                onClick = { onFilter(f); open = false }, modifier = Modifier.testTag("home.filter.${f.name}"),
            )
        }
    }
}

private val FILTER_CHOICES = listOf(HomeFilter.ALL, HomeFilter.UNWATCHED, HomeFilter.SHORT)

/** What takes the place of the feed when nobody is signed in: Home's own shelves stay, and this says where the feed would come from. */
@Composable
internal fun SignInCard(onSignIn: () -> Unit, onExplore: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Column(
        modifier.fillMaxWidth().padding(horizontal = com.greenaltambe.vela.ui.theme.Spacing.gutter, vertical = com.greenaltambe.vela.ui.theme.Spacing.md)
            .clip(androidx.compose.material3.MaterialTheme.shapes.large)
            .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(com.greenaltambe.vela.ui.theme.Spacing.md).testTag("home.signin"),
        verticalArrangement = Arrangement.spacedBy(com.greenaltambe.vela.ui.theme.Spacing.sm),
    ) {
        androidx.compose.material3.Text("Your feed comes from your YouTube account", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        androidx.compose.material3.Text(
            "Sign in to see what YouTube recommends for you. Until then Home shows what Vela can build from your history and subscriptions.",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
        )
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(com.greenaltambe.vela.ui.theme.Spacing.sm)) {
            androidx.compose.material3.Button(onClick = onSignIn, modifier = Modifier.testTag("home.signin.button")) { androidx.compose.material3.Text("Sign in") }
            androidx.compose.material3.TextButton(onClick = onExplore, modifier = Modifier.testTag("home.signin.explore")) { androidx.compose.material3.Text("Browse Explore") }
        }
    }
}
