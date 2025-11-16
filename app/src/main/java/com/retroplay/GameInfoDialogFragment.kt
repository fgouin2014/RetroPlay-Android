package com.retroplay

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.core.os.bundleOf
import com.retroplay.database.DatabaseManager
import com.retroplay.database.GameInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class GameInfoDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext(), android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)
        val composeView = ComposeView(requireContext())
        dialog.setContentView(composeView)

        val gameCRC = requireArguments().getString(ARG_GAME_CRC) ?: ""
        val console = requireArguments().getString(ARG_CONSOLE) ?: ""
        val initialName = requireArguments().getString(ARG_GAME_NAME) ?: ""
        val initialInfo = requireArguments().getSerializable(ARG_GAME_INFO) as? GameInfo

        composeView.setContent {
            MaterialTheme {
                GameInfoContent(
                    gameCRC = gameCRC,
                    console = console,
                    initialName = initialName,
                    initialInfo = initialInfo,
                    onDismiss = { dismiss() }
                )
            }
        }

        dialog.setCanceledOnTouchOutside(true)
        dialog.setCancelable(true)
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                dismissAllowingStateLoss()
                true
            } else {
                false
            }
        }
        return dialog
    }

    override fun onStart() {
        super.onStart()
        parentFragmentManager.setFragmentResult(
            RESULT_KEY,
            bundleOf(EVENT_KEY to EVENT_SHOW)
        )
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        parentFragmentManager.setFragmentResult(
            RESULT_KEY,
            bundleOf(EVENT_KEY to EVENT_DISMISS)
        )
    }

    companion object {
        private const val ARG_GAME_CRC = "arg_game_crc"
        private const val ARG_CONSOLE = "arg_console"
        private const val ARG_GAME_NAME = "arg_game_name"
        private const val ARG_GAME_INFO = "arg_game_info"
        internal const val RESULT_KEY = "game_info_dialog_result"
        internal const val EVENT_KEY = "event"
        internal const val EVENT_SHOW = "show"
        internal const val EVENT_DISMISS = "dismiss"

        @JvmStatic
        fun show(
            fragmentManager: FragmentManager,
            gameCRC: String,
            console: String,
            gameName: String,
            gameInfo: GameInfo?
        ) {
            GameInfoDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_GAME_CRC, gameCRC)
                    putString(ARG_CONSOLE, console)
                    putString(ARG_GAME_NAME, gameName)
                    if (gameInfo != null) {
                        putSerializable(ARG_GAME_INFO, gameInfo)
                    }
                }
            }.show(fragmentManager, "game_info_dialog")
        }
    }
}

@Composable
private fun GameInfoContent(
    gameCRC: String,
    console: String,
    initialName: String,
    initialInfo: GameInfo?,
    onDismiss: () -> Unit
) {
    var isLoading by remember { mutableStateOf(initialInfo == null) }
    var gameInfo by remember { mutableStateOf(initialInfo) }
    var cheatFile by remember { mutableStateOf<File?>(null) }
    var showPerGameConfig by remember { mutableStateOf(false) }

    LaunchedEffect(gameCRC, console) {
        if (gameInfo == null) {
            val (info, cheats) = loadGameInfo(gameCRC, console)
            gameInfo = info
            cheatFile = cheats
            isLoading = false
        } else {
            cheatFile = loadCheatFile(gameInfo!!, console)
        }
    }

    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xAA000000)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else {
        GameInfoDialog(
            gameInfo = gameInfo,
            gameCRC = gameCRC,
            console = console,
            cheatFile = cheatFile,
            onDismiss = onDismiss,
            onOpenPerGameConfig = { _, _ -> showPerGameConfig = true }
        )
    }

    if (showPerGameConfig) {
        PerGameConfigDialog(
            gameCRC = gameCRC,
            gameName = gameInfo?.name ?: initialName,
            onDismiss = { showPerGameConfig = false },
            onSave = {
                showPerGameConfig = false
            }
        )
    }
}

private suspend fun loadGameInfo(gameCRC: String, console: String): Pair<GameInfo?, File?> {
    return withContext(Dispatchers.IO) {
        val info = DatabaseManager.lookupGame(gameCRC, console)
        val cheat = info?.let { DatabaseManager.getCheatsPath(it, console) }
        info to cheat
    }
}

private suspend fun loadCheatFile(info: GameInfo, console: String): File? {
    return withContext(Dispatchers.IO) {
        DatabaseManager.getCheatsPath(info, console)
    }
}
