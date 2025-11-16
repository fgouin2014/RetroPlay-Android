package com.retroplay.gallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.collectAsState

class ScreenshotGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val console = intent.getStringExtra(EXTRA_CONSOLE) ?: "unknown"
        val gameId = intent.getStringExtra(EXTRA_GAME_ID) ?: "unknown"
        val gameName = intent.getStringExtra(EXTRA_GAME_NAME) ?: gameId

        setContent {
            MaterialTheme {
                val state = remember { ScreenshotGalleryState(console, gameId) }
                LaunchedEffect(Unit) {
                    state.reload()
                }
                ScreenshotGalleryScreen(
                    state = state,
                    title = if (gameId == com.retroplay.gallery.ScreenshotRepository.ALL_GAMES_KEY) {
                        "${console.upperCaseOrSelf()} Gallery"
                    } else {
                        gameName
                    },
                    onBack = { finish() }
                )
            }
        }
    }

    companion object {
        const val EXTRA_CONSOLE = "console"
        const val EXTRA_GAME_ID = "gameId"
        const val EXTRA_GAME_NAME = "gameName"
    }
}

private fun String.upperCaseOrSelf(): String {
    return try {
        this.uppercase()
    } catch (e: Exception) {
        this
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenshotGalleryScreen(
    state: ScreenshotGalleryState,
    title: String,
    onBack: () -> Unit
) {
    val selectedIndex = state.selectedIndex.collectAsState()

    BackHandler(enabled = selectedIndex.value != null) {
        state.select(null)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedIndex.value != null) {
                            state.select(null)
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ScreenshotGallery(
                state = state,
                modifier = Modifier.fillMaxSize(),
                onItemSelected = { /* viewer triggered below */ }
            )

            AnimatedVisibility(
                visible = selectedIndex.value != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.95f))
                ) {
                    ScreenshotViewer(
                        state = state,
                        onClose = { state.select(null) },
                        onDelete = { item -> state.deleteItem(item) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

