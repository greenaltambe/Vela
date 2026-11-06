package com.greenaltambe.vela.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.greenaltambe.vela.home.HomeFilter
import com.greenaltambe.vela.ui.components.ChipRow

/** The quick filters above Home: the fixed ones, then the viewer's own topics. They only filter what is already loaded. */
@Composable
internal fun HomeChips(filter: HomeFilter, onFilter: (HomeFilter) -> Unit, topics: List<String>, topic: String?, onTopic: (String?) -> Unit, modifier: Modifier = Modifier) {
    ChipRow(modifier.fillMaxWidth().testTag("home.chips")) {
        items(HomeFilter.entries.toList(), key = { "f-${it.name}" }) { f ->
            FilterChip(selected = topic == null && filter == f, onClick = { onTopic(null); onFilter(f) }, label = { Text(f.label, maxLines = 1) }, modifier = Modifier.testTag("home.chip.${f.name}"))
        }
        items(topics, key = { "t-$it" }) { t ->
            FilterChip(selected = topic == t, onClick = { onTopic(if (topic == t) null else t) }, label = { Text(t.replaceFirstChar(Char::uppercase), maxLines = 1) }, modifier = Modifier.testTag("home.chip.topic"))
        }
    }
}
