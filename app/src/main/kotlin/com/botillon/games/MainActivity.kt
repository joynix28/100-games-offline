package com.botillon.games

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import com.botillon.games.ui.App

class MainActivity : ComponentActivity() {

    private lateinit var sound: AndroidSoundPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        sound = AndroidSoundPlayer(this)
        val store = AndroidProgressStore(this)
        val haptics = AndroidHaptics(this)

        setContent {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                App(store = store, sound = sound, haptics = haptics)
            }
        }
    }

    override fun onDestroy() {
        sound.release()
        super.onDestroy()
    }
}
