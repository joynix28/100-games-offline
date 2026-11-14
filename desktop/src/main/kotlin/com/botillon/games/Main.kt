package com.botillon.games

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.botillon.games.ui.App
import com.botillon.games.ui.NoopHaptics

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "100 Games Offline",
        state = rememberWindowState(width = 420.dp, height = 880.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            App(
                store = DesktopProgressStore(),
                sound = DesktopSoundPlayer(),
                haptics = NoopHaptics,
            )
        }
    }
}
