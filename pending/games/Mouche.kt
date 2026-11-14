package com.botillon.games.games

import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
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
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Mouche : une cible vivante et rapide, vingt-cinq secondes. */
object MoucheGame {
    val entry = GameEntry(
        id = "mouche",
        title = "Mouche",
        tagline = "Plus rapide que l'insecte",
        family = Family.ARCADE,
        accent = Ink.Text,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        Mouche(variant, finish)
    }
}

@Composable
private fun Mouche(variant: Int, finish: (Long, Boolean) -> Unit) {
    val sound = LocalSound.current
    val haptics = LocalHaptics.current
    val speed = listOf(0.42f, 0.60f, 0.80f)[variant]

    var x by remember { mutableFloatStateOf(0.5f) }
    var y by remember { mutableFloatStateOf(0.45f) }
    var vx by remember { mutableFloatStateOf(0.3f) }
    var vy by remember { mutableFloatStateOf(0.22f) }
    var turnIn by remember { mutableFloatStateOf(0.4f) }
    var wing by remember { mutableFloatStateOf(0f) }
    var score by remember { mutableIntStateOf(0) }
    var left by remember { mutableFloatStateOf(25f) }
    var over by remember { mutableStateOf(false) }

    fun report() {
        if (over) return
        over = true
        finish(score * 10L, score >= 3)
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
                wing += dt * 40f
                x += vx * dt
                y += vy * dt
                if (x < 0.05f) {
                    x = 0.05f
                    vx = abs(vx)
                }
                if (x > 0.95f) {
                    x = 0.95f
                    vx = -abs(vx)
                }
                if (y < 0.06f) {
                    y = 0.06f
                    vy = abs(vy)
                }
                if (y > 0.94f) {
                    y = 0.94f
                    vy = -abs(vy)
                }
                turnIn -= dt
                if (turnIn <= 0f) {
                    val angle = Random.nextFloat() * 6.2832f
                    val velocity = speed * (0.75f + Random.nextFloat() * 0.55f)
                    vx = cos(angle) * velocity
                    vy = sin(angle) * velocity * 0.7f
                    turnIn = 0.30f + Random.nextFloat() * 0.45f
                }
                left -= dt
                if (left <= 0f) report()
            }
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText("Score ${score * 10}", style = Typo.heading, color = Ink.Text)
            Pill("${left.toInt().coerceAtLeast(0)} s", color = if (left < 6f) Ink.Coral else Ink.Soft)
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(over) {
                    detectTapGestures { offset ->
                        if (over) return@detectTapGestures
                        val side = min(size.width, size.height).toFloat()
                        val dx = offset.x - x * size.width
                        val dy = offset.y - y * size.height
                        val radius = side * 0.085f
                        if (dx * dx + dy * dy <= radius * radius) {
                            score++
                            sound.play(Sound.MATCH)
                            haptics.success()
                            x = Random.nextFloat() * 0.7f + 0.15f
                            y = Random.nextFloat() * 0.6f + 0.18f
                            vx = if (Random.nextBoolean()) 0.34f else -0.34f
                            vy = if (Random.nextBoolean()) 0.24f else -0.24f
                            turnIn = 0.2f
                        } else {
                            haptics.error()
                        }
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = Ink.Card,
                    size = Size(size.width, size.height),
                    cornerRadius = CornerRadius(24f),
                )
                // Quelques points de fuite pour donner du relief.
                for (i in 0 until 6) {
                    drawCircle(
                        color = Ink.Line.copy(alpha = 0.55f),
                        radius = 2f,
                        center = Offset(size.width * (0.15f + i * 0.14f), size.height * 0.82f),
                    )
                }
                val cx = x * size.width
                val cy = y * size.height
                val r = min(size.width, size.height) * 0.045f
                drawCircle(Ink.Text.copy(alpha = 0.10f), r * 2.2f, Offset(cx + r * 0.5f, cy + r * 0.8f))
                rotate(degrees = -16f + sin(wing) * 22f, pivot = Offset(cx - r * 0.3f, cy - r * 0.3f)) {
                    drawOval(
                        color = Color.White.copy(alpha = 0.85f),
                        topLeft = Offset(cx - r * 1.55f, cy - r * 1.85f),
                        size = Size(r * 1.25f, r * 0.95f),
                    )
                }
                rotate(degrees = 16f - sin(wing) * 22f, pivot = Offset(cx + r * 0.3f, cy - r * 0.3f)) {
                    drawOval(
                        color = Color.White.copy(alpha = 0.85f),
                        topLeft = Offset(cx + r * 0.30f, cy - r * 1.85f),
                        size = Size(r * 1.25f, r * 0.95f),
                    )
                }
                drawOval(
                    color = Ink.Text,
                    topLeft = Offset(cx - r * 0.75f, cy - r * 0.55f),
                    size = Size(r * 1.5f, r * 1.1f),
                )
                drawCircle(Ink.Coral, r * 0.20f, Offset(cx - r * 0.32f, cy - r * 0.22f))
                drawCircle(Ink.Coral, r * 0.20f, Offset(cx + r * 0.32f, cy - r * 0.22f))
                drawLine(
                    color = Ink.Text,
                    start = Offset(cx - r * 0.1f, cy + r * 0.45f),
                    end = Offset(cx - r * 0.35f, cy + r * 0.95f),
                    strokeWidth = 2.5f,
                )
                drawLine(
                    color = Ink.Text,
                    start = Offset(cx + r * 0.1f, cy + r * 0.45f),
                    end = Offset(cx + r * 0.35f, cy + r * 0.95f),
                    strokeWidth = 2.5f,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        HintBar("Tapez la mouche. Elle change de direction sans prévenir.")
    }
}
