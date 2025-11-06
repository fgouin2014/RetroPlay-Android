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
import javax.microedition.khronos.opengles.GL10

/**
 * Screenshot Manager for RetroPlay
 * Captures game screenshots and generates thumbnails
 */
object ScreenshotManager {
    private const val TAG = "ScreenshotManager"
    private const val SCREENSHOTS_BASE_PATH = "/storage/emulated/0/RetroPlay-Data/screenshots"
    private const val THUMBNAILS_BASE_PATH = "/storage/emulated/0/RetroPlay-Data/thumbnails"
    private const val THUMBNAIL_WIDTH = 256
    private const val THUMBNAIL_HEIGHT = 192
    
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
        gameName: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val timestamp = System.currentTimeMillis()
            val sanitizedGameName = gameName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val dir = File("$SCREENSHOTS_BASE_PATH/$console/$sanitizedGameName")
            
            if (!dir.exists()) {
                dir.mkdirs()
            }
            
            val file = File(dir, "screenshot_$timestamp.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            
            Log.i(TAG, "Screenshot saved: ${file.absolutePath}")
            return@withContext file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save screenshot", e)
            return@withContext null
        }
    }
    
    /**
     * Generate and save thumbnail from screenshot
     * @param bitmap Source bitmap
     * @param crc Game CRC (used as filename)
     * @param console Console name
     * @return File path if successful, null otherwise
     */
    suspend fun saveThumbnail(
        bitmap: Bitmap,
        crc: String,
        console: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            // Resize to thumbnail size while maintaining aspect ratio
            val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
            val (thumbWidth, thumbHeight) = if (aspectRatio > 1.0f) {
                // Landscape
                Pair(THUMBNAIL_WIDTH, (THUMBNAIL_WIDTH / aspectRatio).toInt())
            } else {
                // Portrait or square
                Pair((THUMBNAIL_HEIGHT * aspectRatio).toInt(), THUMBNAIL_HEIGHT)
            }
            
            val thumbnail = Bitmap.createScaledBitmap(bitmap, thumbWidth, thumbHeight, true)
            
            val dir = File("$THUMBNAILS_BASE_PATH/$console")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            
            val file = File(dir, "$crc.jpg")
            FileOutputStream(file).use { out ->
                thumbnail.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            
            Log.i(TAG, "Thumbnail saved: ${file.absolutePath}")
            return@withContext file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save thumbnail", e)
            return@withContext null
        }
    }
    
    /**
     * Get thumbnail path for a game
     * @param crc Game CRC
     * @param console Console name
     * @return File path if thumbnail exists, null otherwise
     */
    fun getThumbnailPath(crc: String?, console: String): String? {
        if (crc == null) return null
        
        val file = File("$THUMBNAILS_BASE_PATH/$console/$crc.jpg")
        return if (file.exists()) file.absolutePath else null
    }
    
    /**
     * Get all screenshots for a game
     * @param console Console name
     * @param gameName Game name
     * @return List of screenshot file paths
     */
    fun getScreenshotsForGame(console: String, gameName: String): List<String> {
        val sanitizedGameName = gameName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val dir = File("$SCREENSHOTS_BASE_PATH/$console/$sanitizedGameName")
        
        if (!dir.exists() || !dir.isDirectory) {
            return emptyList()
        }
        
        return dir.listFiles()
            ?.filter { it.extension == "png" }
            ?.map { it.absolutePath }
            ?.sortedDescending() // Most recent first
            ?: emptyList()
    }
    
    /**
     * Check if thumbnail exists for a game
     */
    fun hasThumbnail(crc: String?, console: String): Boolean {
        return getThumbnailPath(crc, console) != null
    }
}

