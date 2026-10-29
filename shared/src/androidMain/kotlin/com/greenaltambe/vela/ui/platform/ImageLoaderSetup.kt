package com.greenaltambe.vela.ui.platform

import android.content.Context
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.request.crossfade

/**
 * Thumbnails are decoded at the size they are drawn (AsyncImage asks for its own constraints), and the decoded bitmaps live in a
 * memory cache capped at a tenth of the app's memory class, so a long scroll cannot grow the heap without bound.
 */
fun configureImageLoader(context: Context) {
    SingletonImageLoader.setSafe { ctx ->
        coil3.ImageLoader.Builder(ctx)
            .memoryCache { MemoryCache.Builder().maxSizePercent(ctx, 0.10).build() }
            .crossfade(false)
            .build()
    }
}
