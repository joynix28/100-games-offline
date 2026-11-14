package com.botillon.games.games

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.botillon.games.ui.AppText
import com.botillon.games.ui.Family
import com.botillon.games.ui.GameEntry
import com.botillon.games.ui.HintBar
import com.botillon.games.ui.Ink
import com.botillon.games.ui.LocalHaptics
import com.botillon.games.ui.LocalSound
import com.botillon.games.ui.Pill
import com.botillon.games.ui.Sound
import com.botillon.games.ui.Typo
import kotlin.math.min
import kotlin.random.Random

/** Réflexe : attendre le vert, taper au plus vite, cinq essais. */
object ReflexeGame {
    val entry = GameEntry(
        id = "reflexe",
        title = "Test de réflexe",
        tagline = "Cinq essais, une moyenne",
        family = Family.ARCADE,
        accent = Ink.Coral,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        Reflexe(variant, finish)
    }
}

@Composable
private fun Reflexe(variant: Int, finish: (Long, Boolean) -> Unit) {
    val sound = LocalSound.current
    val haptics = LocalHaptics.current
    val minWait = listOf(1.4f, 1.0f, 0.65f)[variant]
    val extraWait = listOf(1.6f, 1.4f, 1.2f)[variant]

    var state by remember { mutableIntStateOf(0) } // 0 rouge, 1 vert, 2 pause
    var elapsed by remember { mutableFloatStateOf(0f) }
    var target by remember { mutableFloatStateOf(1.5f) }
    var attempt by remember { mutableIntStateOf(0) }
    var totalMs by remember { mutableFloatStateOf(0f) }
    var lastMs by remember { mutableIntStateOf(0) }
    var over by remember { mutableStateOf(false) }

    fun report() {
        if (over) return
        over = true
        val avg = (totalMs / 5f).toInt()
        finish(avg.toLong(), avg < 350)
    }

    LaunchedEffect(Unit) {
        var last = 0L
        while (!over) {
            withFrameNanos { now ->
                if (last == 0L) {
                    last = now
                    return@withFrameNanos
                }
                val dt = min(0.05f, (now - last) / 1_000_000_000f)
                last = now
                elapsed += dt
                when (state) {
                    0 -> if (elapsed >= target) {
                        state = 1
                        elapsed = 0f
                        sound.play(Sound.POINT)
                    }

                    2 -> if (elapsed >= 0.7f) {
                        state = 0
                        elapsed = 0f
                        target = minWait + Random.nextFloat() * extraWait
                    }
                }
            }
        }
    }

    val bg = when (state) {
        0 -> Ink.Coral
        1 -> Ink.Teal
        else -> Ink.CardAlt
    }
    val fg = if (state == 2) Ink.Soft else Color.White

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText("Essai ${(attempt + 1).coerceAtMost(5)} / 5", style = Typo.heading, color = Ink.Text)
            Pill(
                text = when {
                    lastMs > 0 -> "$lastMs ms"
                    lastMs < 0 -> "Trop tôt"
                    else -> "— ms"
                },
                color = if (lastMs > 0) Ink.Teal else Ink.Soft,
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(bg)
                .pointerInput(over) {
                    detectTapGestures {
                        if (over || state == 2) return@detectTapGestures
                        if (state == 0) {
                            state = 2
                            elapsed = 0f
                            lastMs = -1
                            sound.play(Sound.LOSE)
                            haptics.error()
                        } else {
                            val ms = (elapsed * 1000f).toInt().coerceAtLeast(1)
                            lastMs = ms
                            attempt++
                            totalMs += ms
                            haptics.tap()
                            sound.play(Sound.TAP)
                            if (attempt >= 5) {
                                report()
                            } else {
                                state = 2
                                elapsed = 0f
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AppText(
                    text = when (state) {
                        0 -> "Attendez…"
                        1 -> "TAPEZ !"
                        else -> if (lastMs < 0) "Trop tôt !" else if (lastMs > 0) "$lastMs ms" else "Prêt ?"
                    },
                    style = Typo.display,
                    color = fg,
                    align = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                AppText(
                    text = when (state) {
                        0 -> "Rouge : ne touchez pas encore."
                        1 -> "Vert : tapez maintenant."
                        else -> if (attempt >= 5) "Terminé." else "On remet ça…"
                    },
                    style = Typo.small,
                    color = fg.copy(alpha = 0.82f),
                    align = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        HintBar("Moyenne des 5 essais en millisecondes. Plus bas, c'est mieux.")
    }
}
