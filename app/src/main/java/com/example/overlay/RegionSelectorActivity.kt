package com.example.overlay

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SlatePrimary

/**
 * Displays the captured full-screen screenshot as a background, allowing the user
 * to drag a selection rectangle to choose the region they want to fact-check.
 *
 * On confirm, the selected region bitmap is sent to [OverlayModeService.runImageFactCheck].
 * This activity has no system chrome (full-screen, translucent) to feel like a seamless
 * part of the overlay experience.
 */
class RegionSelectorActivity : ComponentActivity() {

    companion object {
        const val EXTRA_SCREENSHOT_URI = "extra_screenshot_uri"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full-screen, no status bar
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        val screenshotUri = intent.getStringExtra(EXTRA_SCREENSHOT_URI)

        setContent {
            MyApplicationTheme {
                RegionSelectorScreen(
                    screenshotUri = screenshotUri,
                    onConfirm = { cropRect, screenWidth, screenHeight ->
                        if (screenshotUri != null) {
                            cropAndSend(screenshotUri, cropRect, screenWidth, screenHeight)
                        }
                        finish()
                    },
                    onCancel = { finish() }
                )
            }
        }
    }

    private fun cropAndSend(
        uriString: String,
        cropRect: Rect,
        screenWidth: Float,
        screenHeight: Float
    ) {
        try {
            val uri = Uri.parse(uriString)
            val stream = contentResolver.openInputStream(uri) ?: return
            val fullBitmap = BitmapFactory.decodeStream(stream)
            stream.close()

            // Map the on-screen rect to bitmap coordinates
            val scaleX = fullBitmap.width / screenWidth
            val scaleY = fullBitmap.height / screenHeight

            val cropX = (cropRect.left * scaleX).toInt().coerceIn(0, fullBitmap.width - 1)
            val cropY = (cropRect.top * scaleY).toInt().coerceIn(0, fullBitmap.height - 1)
            val cropW = ((cropRect.width * scaleX).toInt()).coerceIn(1, fullBitmap.width - cropX)
            val cropH = ((cropRect.height * scaleY).toInt()).coerceIn(1, fullBitmap.height - cropY)

            val cropped = Bitmap.createBitmap(fullBitmap, cropX, cropY, cropW, cropH)
            fullBitmap.recycle()

            if (OverlayModeService.isRunning) {
                // Send to the running overlay service
                // We can't send a Bitmap directly via Intent (too large), so we pass via the cache file
                val cacheFile = java.io.File(cacheDir, "overlay_region_crop.png")
                cacheFile.outputStream().use { cropped.compress(Bitmap.CompressFormat.PNG, 90, it) }
                val cropUri = androidx.core.content.FileProvider.getUriForFile(
                    this, "${packageName}.fileprovider", cacheFile
                )
                // Route to MainActivity which will pick up the image via the existing pipeline
                val intent = android.content.Intent(this, com.example.MainActivity::class.java).apply {
                    action = ProcessTextActivity.ACTION_OVERLAY_FACT_CHECK
                    putExtra("extra_image_uri", cropUri.toString())
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(intent)
            }
        } catch (e: Exception) {
            android.util.Log.e("RegionSelector", "Crop failed: ${e.message}", e)
        }
    }
}

@Composable
private fun RegionSelectorScreen(
    screenshotUri: String?,
    onConfirm: (Rect, Float, Float) -> Unit,
    onCancel: () -> Unit
) {
    var startPoint by remember { mutableStateOf<Offset?>(null) }
    var endPoint by remember { mutableStateOf<Offset?>(null) }
    var containerSize by remember { mutableStateOf(Size.Zero) }

    val selectionRect = remember(startPoint, endPoint) {
        val s = startPoint ?: return@remember null
        val e = endPoint ?: return@remember null
        Rect(
            left = minOf(s.x, e.x),
            top = minOf(s.y, e.y),
            right = maxOf(s.x, e.x),
            bottom = maxOf(s.y, e.y)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Screenshot as background
        AsyncImage(
            model = screenshotUri,
            contentDescription = "Screenshot",
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    val size = coords.size
                    containerSize = Size(size.width.toFloat(), size.height.toFloat())
                }
        )

        // Darkened overlay + drag-to-select
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            startPoint = offset
                            endPoint = offset
                        },
                        onDrag = { change, _ ->
                            endPoint = change.position
                        }
                    )
                }
        ) {
            // Dim the whole screen
            drawRect(Color.Black.copy(alpha = 0.5f))

            // Clear the selection rect
            val rect = selectionRect
            if (rect != null && rect.width > 4 && rect.height > 4) {
                // Punch-out the selection area
                drawRect(
                    color = Color.Transparent,
                    topLeft = Offset(rect.left, rect.top),
                    size = Size(rect.width, rect.height)
                )
                // Draw selection border
                drawRect(
                    color = Color.White,
                    topLeft = Offset(rect.left, rect.top),
                    size = Size(rect.width, rect.height),
                    style = Stroke(width = 3f)
                )
                drawRect(
                    color = SlatePrimary,
                    topLeft = Offset(rect.left, rect.top),
                    size = Size(rect.width, rect.height),
                    style = Stroke(width = 1.5f)
                )
            }
        }

        // Instruction label
        if (startPoint == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = "Drag to select the region to fact-check",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }

        // Confirm / Cancel buttons (shown only after selection starts)
        if (selectionRect != null && selectionRect.width > 20 && selectionRect.height > 20) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 60.dp)
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Text("Cancel", color = Color.White)
                }
                Button(
                    onClick = {
                        selectionRect?.let {
                            onConfirm(it, containerSize.width, containerSize.height)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SlatePrimary),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Verify")
                }
            }
        }
    }
}
