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
import kotlin.math.min
import kotlin.random.Random

private data class BalloonItem(
    val x: Float,
    val y: Float,
    val vy: Float,
    val black: Boolean,
    val color: Color,
)

/** Ballons : crever les colorés, laisser filer les noirs. */
object BallonsGame {
    val entry = GameEntry(
        id = "ballons",
        title = "Ballons",
        tagline = "Pop les couleurs, pas les noirs",
        family = Family.ARCADE,
        accent = Ink.Pink,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        Ballons(variant, finish)
    }
}

@Composable
private fun Ballons(variant: Int, finish: (Long, Boolean) -> Unit) {
    val sound = LocalSound.current
    val haptics = LocalHaptics.current
    val rise = listOf(0.26f, 0.34f, 0.44f)[variant]
    val rate = listOf(0.66f, 0.54f, 0.44f)[variant]

    var balloons by remember { mutableStateOf<List<BalloonItem>>(emptyList()) }
    var spawnIn by remember { mutableFloatStateOf(0.4f) }
    var score by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(3) }
    var left by remember { mutableFloatStateOf(30f) }
    var over by remember { mutableStateOf(false) }

    fun report() {
        if (over) return
        over = true
        finish(score * 10L, score >= 12)
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
                    val black = Random.nextFloat() < 0.18f
                    balloons = balloons + BalloonItem(
                        x = Random.nextFloat() * 0.76f + 0.12f,
                        y = 1.06f,
                        vy = -rise * (0.85f + Random.nextFloat() * 0.4f),
                        black = black,
                        color = Ink.Accents[Random.nextInt(Ink.Accents.size)],
                    )
                    spawnIn = rate * (0.8f + Random.nextFloat() * 0.4f)
                }
                balloons = balloons
                    .map { it.copy(y = it.y + it.vy * dt) }
                    .filter { it.y > -0.14f }
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
                    detectTapGestures { offset ->
                        if (over) return@detectTapGestures
                        val side = min(size.width, size.height).toFloat()
                        var hit: BalloonItem? = null
                        for (b in balloons) {
                            val dx = (offset.x - b.x * size.width) / (side * 0.085f)
                            val dy = (offset.y - b.y * size.height) / (side * 0.105f)
                            if (dx * dx + dy * dy <= 1f) {
                                hit = b
                                break
                            }
                        }
                        val target = hit ?: return@detectTapGestures
                        balloons = balloons - target
                        if (target.black) {
                            lives--
                            sound.play(Sound.LOSE)
                            haptics.error()
                            if (lives <= 0) report()
                        } else {
                            score++
                            sound.play(Sound.POINT)
                            haptics.tap()
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
                val side = min(size.width, size.height)
                for (b in balloons) {
                    val cx = b.x * size.width
                    val cy = b.y * size.height
                    val rx = side * 0.058f
                    val ry = side * 0.072f
                    drawLine(
                        color = Ink.Muted,
                        start = Offset(cx, cy + ry * 0.9f),
                        end = Offset(cx, cy + ry * 2.1f),
                        strokeWidth = 2f,
                    )
                    drawOval(
                        color = if (b.black) Ink.Text else b.color,
                        topLeft = Offset(cx - rx, cy - ry),
                        size = Size(rx * 2f, ry * 2f),
                    )
                    drawOval(
                        color = Color.White.copy(alpha = if (b.black) 0.20f else 0.45f),
                        topLeft = Offset(cx - rx * 0.6f, cy - ry * 0.62f),
                        size = Size(rx * 0.55f, ry * 0.42f),
                    )
                    if (b.black) {
                        drawLine(
                            color = Color.White,
                            start = Offset(cx - rx * 0.3f, cy - ry * 0.25f),
                            end = Offset(cx + rx * 0.3f, cy + ry * 0.25f),
                            strokeWidth = 3f,
                        )
                        drawLine(
                            color = Color.White,
                            start = Offset(cx + rx * 0.3f, cy - ry * 0.25f),
                            end = Offset(cx - rx * 0.3f, cy + ry * 0.25f),
                            strokeWidth = 3f,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        HintBar("Tapez les ballons colorés. Les noirs coûtent une vie.")
    }
}
