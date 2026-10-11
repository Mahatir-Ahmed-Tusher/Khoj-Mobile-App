package com.example.overlay

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "ScreenshotCaptureManager"

/**
 * Manages MediaProjection-based screen capture for the overlay screenshot feature.
 *
 * Lifecycle:
 *  1. The OverlayModeService calls [initialize] after the system consent dialog result arrives.
 *  2. When the user taps "Screenshot" on the FAB, the caller calls [captureFullScreen] to get a
 *     full-frame Bitmap, then crops it to the user-selected rect.
 *  3. [release] must be called when OverlayModeService stops.
 *
 * Threading: All MediaProjection/ImageReader work is done on a dedicated background handler thread.
 * Callers receive the Bitmap on the calling coroutine dispatcher.
 */
class ScreenshotCaptureManager(private val context: Context) {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())

    val isReady: Boolean get() = mediaProjection != null

    /** Called from OverlayModeService after MediaProjection consent is granted. */
    fun initialize(resultCode: Int, data: Intent) {
        release() // Ensure no stale resources
        val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mgr.getMediaProjection(resultCode, data)
        Log.d(TAG, "MediaProjection initialized")
    }

    /**
     * Captures the full screen and returns the result as a [Bitmap].
     * The caller is responsible for cropping to the user-selected rect.
     *
     * @throws IllegalStateException if [initialize] has not been called.
     * @throws Exception if capture fails (FLAG_SECURE screen, hardware error, etc.).
     */
    suspend fun captureFullScreen(): Bitmap = suspendCancellableCoroutine { cont ->
        val projection = mediaProjection
            ?: return@suspendCancellableCoroutine cont.resumeWithException(
                IllegalStateException("MediaProjection not initialized — user must grant screen capture consent first.")
            )

        // Get display dimensions
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        // Tear down any previous capture resources
        virtualDisplay?.release()
        imageReader?.close()

        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader = reader

        var captured = false

        reader.setOnImageAvailableListener({ imageReader ->
            if (captured) return@setOnImageAvailableListener
            captured = true

            var image: Image? = null
            try {
                image = imageReader.acquireLatestImage() ?: run {
                    cont.resumeWithException(Exception("Capture returned null image — screen may be protected (FLAG_SECURE)."))
                    return@setOnImageAvailableListener
                }

                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * width

                val bitmap = Bitmap.createBitmap(
                    width + rowPadding / pixelStride,
                    height,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)

                // Crop to exact screen dimensions (remove row-padding artifact)
                val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                bitmap.recycle()

                cont.resume(cropped)
            } catch (e: Exception) {
                Log.e(TAG, "Image processing error: ${e.message}", e)
                cont.resumeWithException(e)
            } finally {
                image?.close()
                // Release display after capture — we only need one frame
                virtualDisplay?.release()
                virtualDisplay = null
            }
        }, handler)

        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                Log.d(TAG, "MediaProjection stopped by system")
            }
        }
        projection.registerCallback(callback, handler)

        val display = projection.createVirtualDisplay(
            "KhojScreenCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            handler
        )
        virtualDisplay = display

        cont.invokeOnCancellation {
            display?.release()
            reader.close()
        }
    }

    /**
     * Crops a full-screen bitmap to the user-selected rectangle.
     * Safe to call on any thread; performs no I/O.
     */
    fun cropBitmap(full: Bitmap, x: Int, y: Int, width: Int, height: Int): Bitmap {
        val safeX = x.coerceIn(0, full.width - 1)
        val safeY = y.coerceIn(0, full.height - 1)
        val safeW = width.coerceIn(1, full.width - safeX)
        val safeH = height.coerceIn(1, full.height - safeY)
        return Bitmap.createBitmap(full, safeX, safeY, safeW, safeH)
    }

    /** Releases all MediaProjection and ImageReader resources. */
    fun release() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.stop()
        mediaProjection = null
        OverlayStateRepository.setMediaProjectionReady(false)
        Log.d(TAG, "MediaProjection released")
    }
}
