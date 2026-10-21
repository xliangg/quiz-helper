package com.quizhelper.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.DisplayMetrics
import android.view.WindowManager
import com.quizhelper.core.Prefs
import java.io.ByteArrayOutputStream

/**
 * 通过 MediaProjection + ImageReader 取最近一帧，缩放压缩为 base64 data URI。
 * 不依赖无障碍服务。
 */
object ScreenCaptureManager {
    private var mediaProjection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var width = 0
    private var height = 0
    private var density = 0

    fun isReady(): Boolean = mediaProjection != null && imageReader != null

    fun prepareProjection(context: Context, resultCode: Int, data: android.content.Intent) {
        if (isReady()) return
        val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mp = mpm.getMediaProjection(resultCode, data)
        mediaProjection = mp

        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                release()
            }
        }
        projectionCallback = callback
        mp.registerCallback(callback, null)

        val metrics = DisplayMetrics()
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        width = metrics.widthPixels
        height = metrics.heightPixels
        density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        virtualDisplay = mp.createVirtualDisplay(
            "QuizCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, null
        )
        Prefs.captureGranted = true
    }

    /** 一次截图的结果，带体积信息用于状态提示。 */
    data class Shot(
        val dataUri: String,
        val jpegBytes: Int,
        val width: Int,
        val height: Int
    )

    /** 返回截图结果；若截到黑屏（FLAG_SECURE 等情况）或失败返回 null。 */
    fun captureToDataUri(): Shot? {
        val reader = imageReader ?: return null
        // VirtualDisplay 异步渲染，稍等一帧
        Thread.sleep(150)
        val image = reader.acquireLatestImage() ?: return null
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            buffer.rewind()
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * width
            val rawWidth = width + rowPadding / pixelStride
            val raw = Bitmap.createBitmap(rawWidth, height, Bitmap.Config.ARGB_8888)
            raw.copyPixelsFromBuffer(buffer)

            val clean: Bitmap
            if (rawWidth == width) {
                clean = raw
            } else {
                clean = Bitmap.createBitmap(raw, 0, 0, width, height)
                raw.recycle()
            }

            if (isLikelyBlank(clean)) {
                clean.recycle()
                return null
            }
            val scaled = scaleToMaxEdge(clean, Prefs.maxEdge)
            if (scaled != clean) clean.recycle()
            val out = ByteArrayOutputStream()
            val outW = scaled.width
            val outH = scaled.height
            scaled.compress(
                Bitmap.CompressFormat.JPEG,
                Prefs.jpegQuality.coerceIn(50, 95),
                out
            )
            val jpegBytes = out.size()
            if (scaled != clean) scaled.recycle()
            val b64 = android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
            android.util.Log.d(
                "QuizCapture",
                "screenshot ${outW}x$outH, jpeg=${jpegBytes / 1024}KB, base64=${b64.length / 1024}KB"
            )
            return Shot("data:image/jpeg;base64,$b64", jpegBytes, outW, outH)
        } finally {
            image.close()
        }
    }

    private fun scaleToMaxEdge(src: Bitmap, maxEdge: Int): Bitmap {
        val longest = maxOf(src.width, src.height)
        if (longest <= maxEdge) return src
        val ratio = maxEdge.toFloat() / longest
        return Bitmap.createScaledBitmap(
            src, (src.width * ratio).toInt(), (src.height * ratio).toInt(), true
        )
    }

    private fun isLikelyBlank(bmp: Bitmap): Boolean {
        val stepX = maxOf(1, bmp.width / 10)
        val stepY = maxOf(1, bmp.height / 10)
        val colors = mutableSetOf<Int>()
        var y = 0
        while (y < bmp.height) {
            var x = 0
            while (x < bmp.width) {
                colors.add(bmp.getPixel(x, y))
                if (colors.size > 3) return false
                x += stepX
            }
            y += stepY
        }
        return colors.size <= 1
    }

    fun release() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        if (projectionCallback != null) {
            try {
                mediaProjection?.unregisterCallback(projectionCallback!!)
            } catch (_: Exception) {
            }
        }
        projectionCallback = null
        try {
            mediaProjection?.stop()
        } catch (_: Exception) {
        }
        mediaProjection = null
        Prefs.captureGranted = false
    }
}
