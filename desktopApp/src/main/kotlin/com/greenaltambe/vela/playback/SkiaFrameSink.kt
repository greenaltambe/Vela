package com.greenaltambe.vela.playback

import androidx.compose.runtime.mutableLongStateOf
import com.greenaltambe.vela.player.mpv.FrameSink
import com.greenaltambe.vela.player.mpv.FrameTarget
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Rect

/**
 * Hands mpv the pixel memory of three Skia bitmaps, one at a time, so a frame goes decoder -> mpv's converter ->
 * bitmap with no JVM-side copy; the only copy is Skia's own when the UI draws it. Three because one is being drawn,
 * one is the newest finished frame, and one is free for mpv: the render thread never waits for the UI and never
 * touches a bitmap the UI is reading.
 *
 * Bitmaps are reallocated only when the size changes. [version] changes with every finished frame, so a composable
 * that reads it in its draw phase redraws without recomposing.
 */
class SkiaFrameSink : FrameSink {
    val version = mutableLongStateOf(0)

    /** Frames actually blitted to the window by [draw], for measuring what reaches the screen. */
    val framesDrawn = java.util.concurrent.atomic.AtomicLong()

    private val lock = Any()
    private var width = 0
    private var height = 0
    private var bitmaps = arrayOfNulls<Bitmap>(3)
    private var ready = -1   // newest finished frame
    private var reading = -1 // being copied by the UI
    private var writing = -1 // being filled by mpv

    override fun beginFrame(width: Int, height: Int): FrameTarget? = synchronized(lock) {
        if (width != this.width || height != this.height) {
            if (reading >= 0) return null // the UI is mid-copy of an old-size bitmap; mpv retries on its next frame
            bitmaps.forEach { it?.close() }
            // BGRA_8888 with an opaque alpha type is byte-for-byte mpv's `bgr0`.
            val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
            bitmaps = Array(3) { Bitmap().also { b -> check(b.allocPixels(info)) { "cannot allocate ${width}x$height" } } }
            this.width = width; this.height = height
            ready = -1
        }
        val free = (0..2).firstOrNull { it != ready && it != reading } ?: return null
        writing = free
        val pixmap = bitmaps[free]!!.peekPixels() ?: return null
        FrameTarget(pixmap.addr, pixmap.rowBytes, free)
    }

    override fun endFrame(target: FrameTarget) {
        synchronized(lock) {
            ready = target.token as Int
            writing = -1
        }
        version.longValue += 1
    }

    /** UI thread: draws the newest finished frame scaled into [dstWidth] x [dstHeight]. False if there is none yet. */
    fun draw(canvas: Canvas, dstWidth: Float, dstHeight: Float): Boolean {
        val bitmap = synchronized(lock) {
            reading = ready
            if (reading < 0) return false
            bitmaps[reading]
        } ?: return false
        try {
            // makeFromBitmap copies the pixels, so mpv may reuse nothing we are drawing from once this returns.
            Image.makeFromBitmap(bitmap).use { image ->
                // Linear filtering: the frame may be smaller than the widget (see cappedRenderSize) and nearest would look blocky.
                canvas.drawImageRect(image, Rect.makeWH(image.width.toFloat(), image.height.toFloat()), Rect.makeWH(dstWidth, dstHeight), FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE), null, true)
            }
        } finally {
            synchronized(lock) { reading = -1 }
        }
        framesDrawn.incrementAndGet()
        return true
    }

    fun close() = synchronized(lock) {
        bitmaps.forEach { it?.close() }
        bitmaps = arrayOfNulls(3); ready = -1; reading = -1; width = 0; height = 0
    }
}
