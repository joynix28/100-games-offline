package com.botillon.games.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

private data class JumpPlatform(val x: Float, val y: Float, val w: Float)

/** Sauteur : les plateformes défilent, on se déplace au doigt. */
object SauteurGame {
    val entry = GameEntry(
        id = "sauteur",
        title = "Sauteur",
        tagline = "Monter sans jamais tomber",
        family = Family.ARCADE,
        accent = Ink.Violet,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        Sauteur(variant, finish)
    }
}

@Composable
private fun Sauteur(variant: Int, finish: (Long, Boolean) -> Unit) {
    val sound = LocalSound.current
    val haptics = LocalHaptics.current
    val platformW = listOf(0.30f, 0.24f, 0.19f)[variant]
    val spacing = listOf(0.12f, 0.145f, 0.165f)[variant]

    var playerX by remember { mutableFloatStateOf(0.5f) }
    var playerY by remember { mutableFloatStateOf(0.76f) }
    var vy by remember { mutableFloatStateOf(0f) }
    var climb by remember { mutableFloatStateOf(0f) }
    var platforms by remember {
        mutableStateOf(
            listOf(
                JumpPlatform(0.5f, 0.79f, platformW),
                JumpPlatform(0.26f, 0.64f, platformW),
                JumpPlatform(0.72f, 0.49f, platformW),
                JumpPlatform(0.45f, 0.34f, platformW),
            ),
        )
    }
    var over by remember { mutableStateOf(false) }

    fun report() {
        if (over) return
        over = true
        finish((climb * 100f).toInt().toLong(), climb >= 0.30f)
    }

    LaunchedEffect(Unit) {
        var last = 0L
        while (!over) {
            withFrameNanos { now ->
                if (last == 0L) {
                    last = now
                    return@withFrameNanos
                }
                val dt = min(0.033f, (now - last) / 1_000_000_000f)
                last = now
                vy += 1.4f * dt
                val previousY = playerY
                playerY += vy * dt
                if (playerY < 0.36f) {
                    val shift = 0.36f - playerY
                    playerY = 0.36f
                    climb += shift
                    platforms = platforms.map { it.copy(y = it.y + shift) }
                }
                if (vy > 0f) {
                    val landing = platforms.firstOrNull {
                        abs(playerX - it.x) < it.w / 2f + 0.025f &&
                            previousY <= it.y && playerY >= it.y
                    }
                    if (landing != null) {
                        playerY = landing.y
                        vy = -0.92f
                        sound.play(Sound.TAP)
                        haptics.tap()
                    }
                }
                if (playerY > 1.08f) {
                    sound.play(Sound.LOSE)
                    haptics.error()
                    report()
                    return@withFrameNanos
                }
                platforms = platforms.filter { it.y < 1.2f }
                var topY = platforms.minOfOrNull { it.y } ?: 0f
                while (topY > -0.10f) {
                    topY -= spacing + Random.nextFloat() * 0.03f
                    platforms = platforms + JumpPlatform(
                        x = 0.12f + Random.nextFloat() * 0.76f,
                        y = topY,
                        w = platformW,
                    )
                }
            }
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText("Altitude ${(climb * 100f).toInt()} m", style = Typo.heading, color = Ink.Text)
            Pill("Objectif 30 m", color = Ink.Violet)
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(over) {
                    detectTapGestures { offset ->
                        if (!over) playerX = (offset.x / size.width).coerceIn(0.06f, 0.94f)
                    }
                }
                .pointerInput(over) {
                    detectDragGestures(
                        onDragStart = { playerX = (it.x / size.width).coerceIn(0.06f, 0.94f) },
                    ) { change, drag ->
                        change.consume()
                        playerX = (playerX + drag.x / size.width).coerceIn(0.06f, 0.94f)
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = Ink.Card,
                    size = Size(size.width, size.height),
                    cornerRadius = CornerRadius(24f),
                )
                for ((index, p) in platforms.withIndex()) {
                    val color = Ink.Accents[index % Ink.Accents.size]
                    drawRoundRect(
                        color = color.copy(alpha = 0.85f),
                        topLeft = Offset(p.x * size.width - p.w * size.width / 2f, p.y * size.height),
                        size = Size(p.w * size.width, size.height * 0.016f),
                        cornerRadius = CornerRadius(8f),
                    )
                }
                val cx = playerX * size.width
                val cy = playerY * size.height
                val r = min(size.width, size.height) * 0.034f
                drawCircle(Ink.Coral, r, Offset(cx, cy))
                drawCircle(Ink.Card, r * 0.32f, Offset(cx - r * 0.35f, cy - r * 0.22f))
                drawCircle(Ink.Text, r * 0.14f, Offset(cx - r * 0.35f, cy - r * 0.22f))
                drawCircle(Ink.Card, r * 0.32f, Offset(cx + r * 0.35f, cy - r * 0.22f))
                drawCircle(Ink.Text, r * 0.14f, Offset(cx + r * 0.35f, cy - r * 0.22f))
                drawRoundRect(
                    color = Ink.Text.copy(alpha = 0.5f),
                    topLeft = Offset(cx - r * 0.7f, cy + r * 0.7f),
                    size = Size(r * 1.4f, r * 0.24f),
                    cornerRadius = CornerRadius(4f),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        HintBar("Touchez ou glissez à gauche et à droite pour rester sur les plateformes.")
    }
}
