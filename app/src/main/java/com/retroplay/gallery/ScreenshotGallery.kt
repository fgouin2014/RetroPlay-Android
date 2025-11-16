package com.retroplay.gallery

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScreenshotGallery(
    state: ScreenshotGalleryState,
    modifier: Modifier = Modifier,
    emptyMessage: String = "No screenshots yet",
    onItemSelected: (ScreenshotRepository.ScreenshotItem) -> Unit = {}
) {
    val items by state.items.collectAsState()
    val isLoading by state.isLoading.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        when {
            isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            items.isEmpty() -> {
                Text(
                    text = emptyMessage,
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(120.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(items, key = { _, item -> item.path }) { index, item ->
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            tonalElevation = 2.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clickable {
                                    state.select(index)
                                    onItemSelected(item)
                                }
                        ) {
                            val imagePath = item.thumbnailPath ?: item.path
                            val bitmap = remember(imagePath) {
                                BitmapFactory.decodeFile(imagePath)
                            }
                            DisposableEffect(bitmap) {
                                onDispose {
                                    bitmap?.recycle()
                                }
                            }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Screenshot",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.DarkGray),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Preview unavailable", color = Color.LightGray)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConsoleGroupedGallery(
    state: ScreenshotGalleryState,
    systemName: String,
    modifier: Modifier = Modifier,
    onItemSelected: (ScreenshotRepository.ScreenshotItem) -> Unit = {}
) {
    val items by state.items.collectAsState()
    val isLoading by state.isLoading.collectAsState()

    // Group by game directory (parent folder name of the screenshot file)
    val grouped = remember(items) {
        items.groupBy { item ->
            try {
                File(item.path).parentFile?.name ?: "unknown"
            } catch (e: Exception) {
                "unknown"
            }
        }.toSortedMap(String.CASE_INSENSITIVE_ORDER)
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            items.isEmpty() -> {
                Text(
                    text = "No screenshots for $systemName",
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            else -> {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    grouped.forEach { (gameKey, gameItems) ->
                        item("header-$gameKey") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = beautifyGameKey(gameKey),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "(${gameItems.size})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }
                        item("grid-$gameKey") {
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                gameItems.forEachIndexed { _, item ->
                                    Surface(
                                        shape = MaterialTheme.shapes.medium,
                                        tonalElevation = 2.dp,
                                        modifier = Modifier
                                            .width(160.dp)
                                            .aspectRatio(16f / 9f)
                                            .clickable {
                                                // Select the index in the flat list for the viewer
                                                val indexInFlat = items.indexOfFirst { it.path == item.path }
                                                state.select(indexInFlat.takeIf { it >= 0 })
                                                onItemSelected(item)
                                            }
                                    ) {
                                        val imagePath = item.thumbnailPath ?: item.path
                                        val bitmap = remember(imagePath) { BitmapFactory.decodeFile(imagePath) }
                                        DisposableEffect(bitmap) {
                                            onDispose { bitmap?.recycle() }
                                        }
                                        if (bitmap != null) {
                                            Image(
                                                bitmap = bitmap.asImageBitmap(),
                                                contentDescription = "Screenshot",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(Color.DarkGray),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("Preview unavailable", color = Color.LightGray)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        item("spacer-$gameKey") { Spacer(modifier = Modifier.height(8.dp)) }
                    }
                }
            }
        }
    }
}

private fun beautifyGameKey(key: String): String {
    return key.replace('_', ' ').replace('-', ' ').trim()
}

@Composable
fun ScreenshotGalleryToolbar(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        if (!subtitle.isNullOrEmpty()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
            )
        }
    }
}

