package com.retroplay.gallery

import android.util.Log
import java.io.File

/**
 * Low-level access to screenshot files. Keeps file-system logic isolated from the UI layer.
 */
object ScreenshotRepository {
    private const val TAG = "ScreenshotRepository"
    private const val SCREENSHOTS_ROOT = "/storage/emulated/0/RetroPlay-Data/screenshots"
    const val ALL_GAMES_KEY: String = "__ALL__"

    @JvmStatic
    fun sanitizeGameKey(raw: String): String {
        val source = if (raw.isEmpty()) "unknown" else raw
        val sanitized = source.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return if (sanitized.isBlank()) "game" else sanitized
    }

    private fun gameDirectory(console: String, rawGameId: String): File {
        val key = sanitizeGameKey(rawGameId)
        return File("$SCREENSHOTS_ROOT/$console/$key")
    }

    private fun thumbnailsDirectory(console: String, rawGameId: String): File {
        return File(gameDirectory(console, rawGameId), "thumbnails")
    }

    data class ScreenshotItem(
        val path: String,
        val thumbnailPath: String?,
        val timestamp: Long
    )

    fun listScreenshots(console: String, gameId: String): List<ScreenshotItem> {
        // Special mode: aggregate all screenshots for a console
        if (gameId == ALL_GAMES_KEY) {
            val consoleDir = File("$SCREENSHOTS_ROOT/$console")
            if (!consoleDir.exists() || !consoleDir.isDirectory) return emptyList()
            val results = mutableListOf<ScreenshotItem>()
            consoleDir.listFiles { f -> f.isDirectory }?.forEach { gameDir ->
                gameDir.listFiles()
                    ?.filter { it.isFile && it.extension.equals("png", ignoreCase = true) }
                    ?.forEach { png ->
                        results.add(
                            ScreenshotItem(
                                path = png.absolutePath,
                                thumbnailPath = resolveThumbnailForDir(console, gameDir.name, png),
                                timestamp = png.lastModified()
                            )
                        )
                    }
            }
            return results.sortedByDescending { it.timestamp }
        }

        val dir = gameDirectory(console, gameId)
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { it.extension.equals("png", ignoreCase = true) }
            ?.map {
                ScreenshotItem(
                    path = it.absolutePath,
                    thumbnailPath = resolveThumbnail(it, console, gameId),
                    timestamp = it.lastModified()
                )
            }
            ?.sortedByDescending { it.timestamp }
            ?: emptyList()
    }

    fun deleteScreenshot(item: ScreenshotItem) {
        try {
            File(item.path).takeIf { it.exists() }?.delete()
            item.thumbnailPath?.let { File(it).takeIf { f -> f.exists() }?.delete() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete screenshot ${item.path}", e)
        }
    }

    private fun resolveThumbnail(file: File, console: String, rawGameId: String): String? {
        val thumbDir = thumbnailsDirectory(console, rawGameId)
        val candidate = File(thumbDir, file.nameWithoutExtension + ".jpg")
        return if (candidate.exists()) candidate.absolutePath else null
    }

    private fun resolveThumbnailForDir(console: String, gameDirName: String, pngFile: File): String? {
        val thumbDir = File("$SCREENSHOTS_ROOT/$console/$gameDirName/thumbnails")
        val candidate = File(thumbDir, pngFile.nameWithoutExtension + ".jpg")
        return if (candidate.exists()) candidate.absolutePath else null
    }
}

