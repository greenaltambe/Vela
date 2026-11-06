package com.greenaltambe.vela.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.ui.theme.AppIcons

/**
 * The search field: a Material filled text field in a pill, the search icon at its start (it submits), a clear button at its end. The keyboard's
 * search key (Enter) submits.
 */
@Composable
fun VelaSearchField(
    value: String, onValueChange: (String) -> Unit, onSubmit: () -> Unit, modifier: Modifier = Modifier,
    placeholder: String = "Search videos", focusRequester: FocusRequester? = null,
) {
    TextField(
        value, onValueChange,
        modifier = modifier.heightIn(min = 56.dp).let { if (focusRequester != null) it.focusRequester(focusRequester) else it }.testTag("search.field"),
        singleLine = true, shape = CircleShape,
        placeholder = { Text(placeholder, maxLines = 1) },
        leadingIcon = { IconButton(onClick = onSubmit, enabled = value.isNotBlank(), modifier = Modifier.testTag("search.submit")) { Icon(AppIcons.search, "Search") } },
        trailingIcon = if (value.isNotEmpty()) ({ IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(40.dp).testTag("search.clear")) { Icon(AppIcons.close, "Clear") } }) else null,
        colors = TextFieldDefaults.colors(focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
    )
}
