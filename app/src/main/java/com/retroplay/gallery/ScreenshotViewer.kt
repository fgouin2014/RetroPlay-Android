package com.retroplay.gallery

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ScreenshotViewer(
    state: ScreenshotGalleryState,
    onClose: () -> Unit,
    onDelete: (ScreenshotRepository.ScreenshotItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val items by state.items.collectAsState()
    val selectedIndex by state.selectedIndex.collectAsState()
    val pagerState = rememberPagerState(
        initialPage = selectedIndex ?: 0,
        pageCount = { items.size }
    )

    if (items.isEmpty()) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Page indicator (center top) already provided by ViewerTopBar
        // Add a small bottom context with index/total for quick glance
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
                .background(Color(0x66000000), RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${pagerState.currentPage + 1} / ${items.size}",
                color = Color(0xFFFF3333), // kitt_red
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { items[it].path }
        ) { page ->
            val item = items[page]
            val bitmap = remember(item.path) { BitmapFactory.decodeFile(item.path) }
            DisposableEffect(bitmap) {
                onDispose { bitmap?.recycle() }
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Screenshot",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp)
        ) {
            ViewerTopBar(
                index = pagerState.currentPage + 1,
                total = items.size,
                item = items[pagerState.currentPage],
                onClose = onClose,
                onDelete = { onDelete(items[pagerState.currentPage]) }
            )
        }
    }
}

@Composable
private fun ViewerTopBar(
    index: Int,
    total: Int,
    item: ScreenshotRepository.ScreenshotItem,
    onClose: () -> Unit,
    onDelete: () -> Unit
) {
    val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(item.timestamp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(Color(0xAA000000), RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFFFF3333)) // kitt_red
        }
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "$index / $total",
                color = Color(0xFFFF3333), // kitt_red
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = date,
                color = Color(0xFF666666), // kitt_light_gray
                style = MaterialTheme.typography.bodySmall
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF3333)) // kitt_red
        }
    }
}

