package com.retroplay

import android.graphics.Bitmap
import android.opengl.GLES20
import android.util.Log
import com.swordfish.libretrodroid.GLRetroView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.IntBuffer

/**
 * Screenshot Manager for RetroPlay
 * Captures game screenshots and generates thumbnails
 */
object ScreenshotManager {
    private const val TAG = "ScreenshotManager"
    private const val SCREENSHOTS_BASE_PATH = "/storage/emulated/0/RetroPlay-Data/screenshots"
    private const val THUMBNAIL_WIDTH = 256
    private const val THUMBNAIL_HEIGHT = 192

    data class ScreenshotSaveResult(
        val screenshotPath: String,
        val thumbnailPath: String?
    )

    /**
     * Capture a screenshot from GLRetroView
     * Must be called from the GL thread (use queueEvent)
     */
    fun captureScreenshotGL(width: Int, height: Int): Bitmap? {
        try {
            val buffer = IntBuffer.allocate(width * height)
            GLES20.glReadPixels(
                0, 0, width, height,
                GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE,
                buffer
            )
            
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(buffer)
            
            // Flip vertically (OpenGL is upside down)
            val matrix = android.graphics.Matrix()
            matrix.preScale(1.0f, -1.0f)
            return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, false)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to capture screenshot", e)
            return null
        }
    }
    
    /**
     * Save screenshot to disk
     * @param bitmap Screenshot bitmap
     * @param console Console name (e.g. "nes", "psx")
     * @param gameName Game name (e.g. "Super Mario Bros")
     * @return File path if successful, null otherwise
     */
    suspend fun saveScreenshot(
        bitmap: Bitmap,
        console: String,
        gameId: String
    ): ScreenshotSaveResult? = withContext(Dispatchers.IO) {
        try {
            val timestamp = System.currentTimeMillis()
            val sanitizedGameId = com.retroplay.gallery.ScreenshotRepository.sanitizeGameKey(gameId)
            val dir = File("$SCREENSHOTS_BASE_PATH/$console/$sanitizedGameId")
            
            if (!dir.exists()) {
                dir.mkdirs()
            }
            
            val fileName = "screenshot_$timestamp.png"
            val file = File(dir, fileName)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            
            val thumbnailPath = generateThumbnail(bitmap, dir, fileName)
            Log.i(TAG, "Screenshot saved: ${file.absolutePath}")
            if (thumbnailPath != null) {
                Log.i(TAG, "Thumbnail saved: $thumbnailPath")
            }
            return@withContext ScreenshotSaveResult(
                screenshotPath = file.absolutePath,
                thumbnailPath = thumbnailPath
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save screenshot", e)
            return@withContext null
        }
    }

    private fun generateThumbnail(bitmap: Bitmap, gameDir: File, screenshotFileName: String): String? {
        return try {
            val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
            val (thumbWidth, thumbHeight) = if (aspectRatio > 1.0f) {
                Pair(THUMBNAIL_WIDTH, (THUMBNAIL_WIDTH / aspectRatio).toInt().coerceAtLeast(1))
            } else {
                Pair((THUMBNAIL_HEIGHT * aspectRatio).toInt().coerceAtLeast(1), THUMBNAIL_HEIGHT)
            }
 
            val thumbnail = Bitmap.createScaledBitmap(bitmap, thumbWidth, thumbHeight, true)
            val thumbDir = File(gameDir, "thumbnails")
            if (!thumbDir.exists()) {
                thumbDir.mkdirs()
            }
            val thumbFile = File(thumbDir, screenshotFileName.substringBeforeLast('.') + ".jpg")
            FileOutputStream(thumbFile).use { out ->
                thumbnail.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            if (thumbnail !== bitmap && !thumbnail.isRecycled) {
                thumbnail.recycle()
            }
            thumbFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate thumbnail", e)
            null
        }
    }
}

