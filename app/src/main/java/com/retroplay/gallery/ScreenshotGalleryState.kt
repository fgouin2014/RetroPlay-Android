package com.retroplay.gallery

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ScreenshotGalleryState(
    private val console: String,
    private val gameId: String
) {
    private val _items = MutableStateFlow(emptyList<ScreenshotRepository.ScreenshotItem>())
    val items: StateFlow<List<ScreenshotRepository.ScreenshotItem>> = _items.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedIndex = MutableStateFlow<Int?>(null)
    val selectedIndex: StateFlow<Int?> = _selectedIndex.asStateFlow()

    fun reload() {
        _isLoading.value = true
        val previousIndex = _selectedIndex.value
        val newItems = ScreenshotRepository.listScreenshots(console, gameId)
        _items.value = newItems
        _isLoading.value = false
        _selectedIndex.value = when {
            newItems.isEmpty() -> null
            previousIndex == null -> null
            else -> previousIndex.coerceIn(0, newItems.lastIndex)
        }
    }

    fun deleteItem(item: ScreenshotRepository.ScreenshotItem) {
        val previousIndex = _selectedIndex.value
        ScreenshotRepository.deleteScreenshot(item)
        reload()
        if (_items.value.isNotEmpty() && previousIndex != null) {
            _selectedIndex.value = previousIndex.coerceIn(0, _items.value.lastIndex)
        }
    }

    fun select(index: Int?) {
        if (index == null || index < 0 || index >= _items.value.size) {
            _selectedIndex.value = null
        } else {
            _selectedIndex.value = index
        }
    }
}

