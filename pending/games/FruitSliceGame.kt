package com.botillon.games.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.botillon.games.ui.*
import kotlin.math.min
import kotlin.random.Random

/** Tranche de fruits : glisser pour trancher, bombes à éviter, 60 secondes. */
object FruitSliceGame {
    val entry = GameEntry(
        id = "tranche-fruits",
        title = "Tranche de fruits",
        tagline = "Tranchez, mais évitez les bombes",
        family = Family.ARCADE,
        accent = Ink.Coral,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        FruitSliceScreen(variant, finish)
    }
}

private data class FSlice(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val bomb: Boolean,
    val color: Color,
)

@Composable
private fun FruitSliceScreen(variant: Int, finish: (Long, Boolean) -> Unit) {
    val bombChance = listOf(0.13f, 0.22f, 0.32f)[variant]
    val spawnGap = listOf(0.85f, 0.62f, 0.45f)[variant]
    val sound = LocalSound.current
    val haptics = LocalHaptics.current
    var items by remember { mutableStateOf(emptyList<FSlice>()) }
    var score by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(3) }
    var time by remember { mutableFloatStateOf(60f) }
    var dragFrom by remember { mutableStateOf(Offset.Zero) }
    var dragTo by remember { mutableStateOf(Offset.Zero) }
    var reported by remember { mutableStateOf(false) }

    fun report() {
        if (reported) return
        reported = true
        finish(score.toLong(), score >= 120)
    }

    fun cut(a: Offset, b: Offset, w: Float, h: Float) {
        if (reported || a == b) return
        val touched = items.filter { f ->
            distanceToSegment(Offset(f.x * w, f.y * h), a, b) < w * 0.085f
        }
        if (touched.isEmpty()) return
        items = items.filter { it !in touched }
        touched.forEach { f ->
            if (f.bomb) {
                lives -= 1
                sound.play(Sound.LOSE)
                haptics.error()
                if (lives <= 0) report()
            } else {
                score += 10
                sound.play(Sound.MATCH)
                haptics.tap()
            }
        }
    }

    LaunchedEffect(Unit) {
        var last = 0L
        var spawn = 0f
        while (!reported) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else min(0.034f, (now - last) / 1_000_000_000f)
                last = now
                if (dt <= 0f || reported) return@withFrameNanos
                time = (time - dt).coerceAtLeast(0f)
                spawn += dt
                val fresh = items.mapNotNull { f ->
                    val ny = f.y + f.vy * dt
                    if (ny < -0.25f) null
                    else FSlice(f.x + f.vx * dt, ny, f.vx, f.vy + 1.9f * dt, f.bomb, f.color)
                }.toMutableList()
                if (spawn >= spawnGap) {
                    spawn = 0f
                    fresh += FSlice(
                        x = 0.5f + (Random.nextFloat() - 0.5f) * 0.66f,
                        y = 1.08f,
                        vx = (Random.nextFloat() - 0.5f) * 0.30f,
                        vy = -1.10f - Random.nextFloat() * 0.25f,
                        bomb = Random.nextFloat() < bombChance,
                        color = Ink.Accents.random(),
                    )
                }
                items = fresh
                if (time <= 0f) report()
            }
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText("Score $score", style = Typo.heading, color = Ink.Text)
            AppText("${time.toInt()} s", style = Typo.heading, color = if (time < 10f) Ink.Coral else Ink.Soft)
            Row {
                repeat(3) { i ->
                    AppText(if (i < lives) "♥" else "♡", style = Typo.body, color = Ink.Coral)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { dragFrom = it; dragTo = it },
                        onDragEnd = { dragFrom = Offset.Zero; dragTo = Offset.Zero },
                    ) { change, _ ->
                        change.consume()
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        dragFrom = dragTo
                        dragTo = change.position
                        cut(dragFrom, dragTo, w, h)
                    }
                },
        ) {
            drawRoundRect(Ink.Card, cornerRadius = CornerRadius(24f))
            if (dragFrom != Offset.Zero && dragTo != Offset.Zero) {
                drawLine(Ink.Mustard.copy(alpha = 0.65f), dragFrom, dragTo, strokeWidth = 6f)
            }
            val r = size.width * 0.055f
            items.forEach { f ->
                val c = Offset(f.x * size.width, f.y * size.height)
                if (f.bomb) {
                    drawCircle(Ink.Text, r, c)
                    drawLine(
                        Ink.Mustard,
                        Offset(c.x + r * 0.45f, c.y - r * 0.55f),
                        Offset(c.x + r * 0.8f, c.y - r * 1.25f),
                        strokeWidth = r * 0.20f,
                    )
                    drawCircle(Ink.Mustard, r * 0.20f, Offset(c.x + r * 0.8f, c.y - r * 1.25f))
                } else {
                    drawCircle(f.color, r, c)
                    drawCircle(Color.White.copy(alpha = 0.45f), r * 0.22f, Offset(c.x - r * 0.3f, c.y - r * 0.3f))
                    drawLine(
                        Ink.Green,
                        Offset(c.x, c.y - r),
                        Offset(c.x + r * 0.42f, c.y - r * 1.35f),
                        strokeWidth = r * 0.16f,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        HintBar("Glissez pour trancher. Une bombe coûte une vie. 60 secondes.")
    }
}

private fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
    val ab = b - a
    val len = ab.x * ab.x + ab.y * ab.y
    if (len == 0f) return (p - a).getDistance()
    val ap = p - a
    val t = ((ap.x * ab.x + ap.y * ab.y) / len).coerceIn(0f, 1f)
    return (p - Offset(a.x + ab.x * t, a.y + ab.y * t)).getDistance()
}
