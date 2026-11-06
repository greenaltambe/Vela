@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.greenaltambe.vela.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The app's one bottom sheet: Material's modal sheet with the extra-large top corners, a tonal container, Material's drag handle and no half-open
 * stop (a half-height first stop hid the bottom of long sheets until dragged). Every sheet in the app goes through here so they look and move alike.
 */
@Composable
fun VelaSheet(onDismiss: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        content = content,
    )
}

/** Material's sheet drag handle, for the panels that sit inside a page instead of in a modal sheet. */
@Composable
fun SheetHandle() { BottomSheetDefaults.DragHandle() }
