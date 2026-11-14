package com.botillon.games.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
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

private data class FruitItem(
    val x: Float,
    val y: Float,
    val vy: Float,
    val bomb: Boolean,
    val tint: Int,
)

/** Fruits : déplacer le panier, attraper les fruits, laisser les bombes. */
object FruitsGame {
    val entry = GameEntry(
        id = "fruits",
        title = "Attrape les fruits",
        tagline = "Le panier plein, les bombes loin",
        family = Family.ARCADE,
        accent = Ink.Green,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        Fruits(variant, finish)
    }
}

@Composable
private fun Fruits(variant: Int, finish: (Long, Boolean) -> Unit) {
    val sound = LocalSound.current
    val haptics = LocalHaptics.current
    val fall = listOf(0.30f, 0.40f, 0.52f)[variant]
    val rate = listOf(1.0f, 0.82f, 0.64f)[variant]
    val basketW = 0.28f

    var items by remember { mutableStateOf<List<FruitItem>>(emptyList()) }
    var basket by remember { mutableFloatStateOf(0.5f) }
    var spawnIn by remember { mutableFloatStateOf(0.4f) }
    var score by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(3) }
    var left by remember { mutableFloatStateOf(40f) }
    var over by remember { mutableStateOf(false) }

    fun report() {
        if (over) return
        over = true
        finish(score * 10L, score >= 15)
    }

    LaunchedEffect(Unit) {
        var last = 0L
        while (!over) {
            withFrameNanos { now ->
                if (last == 0L) {
                    last = now
                    return@withFrameNanos
                }
                val dt = min(0.04f, (now - last) / 1_000_000_000f)
                last = now
                left -= dt
                if (left <= 0f) {
                    report()
                    return@withFrameNanos
                }
                spawnIn -= dt
                if (spawnIn <= 0f) {
                    items = items + FruitItem(
                        x = Random.nextFloat() * 0.78f + 0.11f,
                        y = -0.04f,
                        vy = fall * (0.85f + Random.nextFloat() * 0.4f),
                        bomb = Random.nextFloat() < 0.22f,
                        tint = Random.nextInt(Ink.Accents.size),
                    )
                    spawnIn = rate * (0.75f + Random.nextFloat() * 0.5f)
                }
                val kept = mutableListOf<FruitItem>()
                for (f in items) {
                    val y = f.y + f.vy * dt
                    if (y <= 0.88f) {
                        kept += f.copy(y = y)
                        continue
                    }
                    if (abs(f.x - basket) < basketW / 2f + 0.02f) {
                        if (f.bomb) {
                            lives--
                            sound.play(Sound.LOSE)
                            haptics.error()
                            if (lives <= 0) {
                                report()
                                return@withFrameNanos
                            }
                        } else {
                            score++
                            sound.play(Sound.POINT)
                            haptics.tap()
                        }
                    }
                }
                items = kept
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
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill("Vies $lives", color = if (lives == 1) Ink.Coral else Ink.Soft)
                Pill("${left.toInt().coerceAtLeast(0)} s", color = Ink.Soft)
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(over) {
                    detectDragGestures(
                        onDragStart = { basket = (it.x / size.width).coerceIn(0.12f, 0.88f) },
                    ) { change, drag ->
                        change.consume()
                        basket = (basket + drag.x / size.width).coerceIn(0.12f, 0.88f)
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = Ink.Card,
                    size = Size(size.width, size.height),
                    cornerRadius = CornerRadius(24f),
                )
                val side = min(size.width, size.height)
                for (f in items) {
                    val cx = f.x * size.width
                    val cy = f.y * size.height
                    val radius = side * 0.032f
                    if (f.bomb) {
                        drawCircle(Ink.Text, radius * 1.25f, Offset(cx, cy))
                        drawCircle(Ink.Text, radius * 0.55f, Offset(cx - radius * 0.35f, cy - radius * 0.4f))
                        drawLine(
                            color = Ink.Mustard,
                            start = Offset(cx, cy - radius),
                            end = Offset(cx + radius * 0.6f, cy - radius * 1.8f),
                            strokeWidth = radius * 0.28f,
                        )
                    } else {
                        val color = Ink.Accents[f.tint]
                        drawCircle(color, radius, Offset(cx, cy))
                        drawCircle(color.copy(alpha = 0.5f), radius * 0.45f, Offset(cx - radius * 0.3f, cy - radius * 0.3f))
                        drawOval(
                            color = Ink.Green,
                            topLeft = Offset(cx - radius * 0.15f, cy - radius * 1.55f),
                            size = Size(radius * 0.8f, radius * 0.6f),
                        )
                    }
                }
                val bx = basket * size.width
                val by = size.height * 0.90f
                val bw = size.width * basketW
                drawRoundRect(
                    color = Ink.Soft,
                    topLeft = Offset(bx - bw / 2f, by),
                    size = Size(bw, size.height * 0.055f),
                    cornerRadius = CornerRadius(10f),
                )
                drawRoundRect(
                    color = Ink.Mustard,
                    topLeft = Offset(bx - bw / 2f, by),
                    size = Size(bw, size.height * 0.014f),
                    cornerRadius = CornerRadius(8f),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        HintBar("Glissez le panier sous les fruits. Une bombe = une vie.")
    }
}
