package com.greenaltambe.vela.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.greenaltambe.vela.ui.theme.AppIcons
import com.greenaltambe.vela.ui.theme.ExtraColors

/**
 * Pictures full screen: swipe sideways between them, pinch or double-tap to zoom, drag a zoomed picture around, close with the button
 * (or Back). Used for the images of a community post, which the card only shows cropped.
 */
@Composable
fun ImageViewer(urls: List<String>, startIndex: Int, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, (urls.size - 1).coerceAtLeast(0)), pageCount = { urls.size })
        var zoomed by remember { mutableStateOf(false) }
        Box(Modifier.fillMaxSize().background(ExtraColors.mediaBackground).testTag("imageViewer")) {
            HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = !zoomed) { page ->
                var scale by remember { mutableFloatStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }
                val state = rememberTransformableState { zoom, pan, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offset = if (scale > 1f) offset + pan else Offset.Zero
                    zoomed = scale > 1f
                }
                AsyncImage(
                    urls[page], null,
                    Modifier.fillMaxSize()
                        .pointerInput(Unit) { detectTapGestures(onDoubleTap = { if (scale > 1f) { scale = 1f; offset = Offset.Zero; zoomed = false } else { scale = 2.5f; zoomed = true } }) }
                        .transformable(state, canPan = { scale > 1f })
                        .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
                    contentScale = ContentScale.Fit,
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp).testTag("imageViewer.close"), colors = IconButtonDefaults.iconButtonColors(containerColor = ExtraColors.mediaBackground.copy(alpha = 0.5f), contentColor = ExtraColors.onMedia)) { Icon(AppIcons.close, "Close") }
            if (urls.size > 1) Text("${pager.currentPage + 1} / ${urls.size}", color = ExtraColors.onMedia, style = MaterialTheme.typography.labelLarge, modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(20.dp).background(ExtraColors.mediaBackground.copy(alpha = 0.5f), MaterialTheme.shapes.large).padding(horizontal = 12.dp, vertical = 4.dp))
        }
    }
}
