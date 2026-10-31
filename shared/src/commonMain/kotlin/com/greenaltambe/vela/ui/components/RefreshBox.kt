package com.greenaltambe.vela.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.greenaltambe.vela.ui.look.Vela
import com.greenaltambe.vela.ui.look.toColor
import com.greenaltambe.vela.ui.platform.LocalVolumeSlider

/** What Ctrl+R does on the page that is showing: the page registers its refresh here (desktop). */
class RefreshHandle { var action: (() -> Unit)? = null }

val LocalRefreshHandle = staticCompositionLocalOf<RefreshHandle?> { null }

/**
 * Pull to refresh on touch screens. On desktop there is no pulling, so the page gets a refresh button in its corner and answers Ctrl+R instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshBox(isRefreshing: Boolean, onRefresh: () -> Unit, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    if (!LocalVolumeSlider.current) { PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = modifier, content = content); return }
    val handle = LocalRefreshHandle.current
    val latest by rememberUpdatedState(onRefresh)
    DisposableEffect(handle) {
        val mine: () -> Unit = { latest() }
        handle?.action = mine
        onDispose { if (handle?.action === mine) handle.action = null }
    }
    Box(modifier) {
        content()
        IconButton(
            onClick = onRefresh, enabled = !isRefreshing,
            modifier = Modifier.align(Alignment.BottomEnd).padding(Vela.md).clip(Vela.pillShape).background(Vela.colors.surfaceVariant.toColor()).testTag("page.refresh"),
        ) {
            if (isRefreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(Vela.icons.refresh, "Refresh (Ctrl+R)")
        }
    }
}
