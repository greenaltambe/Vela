package com.greenaltambe.vela.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.ui.look.Vela
import com.greenaltambe.vela.ui.look.toColor

/**
 * The search field: a surface-coloured rounded bar (the corner radius setting), the search icon at its start (it submits), the
 * query, and a clear button; while it has focus it wears the palette gradient as its outline. Enter / the keyboard's search key submits.
 */
@Composable
fun VelaSearchField(
    value: String, onValueChange: (String) -> Unit, onSubmit: () -> Unit, modifier: Modifier = Modifier,
    placeholder: String = "Search videos", focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = Vela.largeShape
    val muted = Vela.colors.onSurfaceVariant.toColor()
    Row(
        modifier.heightIn(min = 48.dp).clip(shape).background(Vela.colors.surfaceVariant.toColor())
            .then(if (focused) Modifier.border(2.dp, Vela.wake, shape) else Modifier)
            .padding(start = Vela.sm, end = Vela.xs),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Vela.sm),
    ) {
        Icon(Vela.icons.search, "Search", Modifier.clip(Vela.pillShape).clickable(enabled = value.isNotBlank(), onClick = onSubmit).testTag("search.submit"), tint = if (focused) Vela.colors.onSurface.toColor() else muted)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value, onValueChange, Modifier.fillMaxWidth().let { if (focusRequester != null) it.focusRequester(focusRequester) else it }.onFocusChanged { focused = it.isFocused }.testTag("search.field"),
                singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.copy(color = Vela.colors.onSurface.toColor()),
                cursorBrush = SolidColor(Vela.colors.primary.toColor()),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            )
        }
        if (value.isNotEmpty()) IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(36.dp).testTag("search.clear")) { Icon(Vela.icons.close, "Clear", tint = muted) }
    }
}
