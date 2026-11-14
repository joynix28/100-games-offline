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

private data class Pipe(val x: Float, val gapY: Float, val scored: Boolean)

/** Flappy : taper pour battre des ailes et passer entre les tuyaux. */
object FlappyGame {
    val entry = GameEntry(
        id = "flappy",
        title = "Flappy",
        tagline = "Un tap, un battement d'ailes",
        family = Family.ARCADE,
        accent = Ink.Mustard,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        Flappy(variant, finish)
    }
}

@Composable
private fun Flappy(variant: Int, finish: (Long, Boolean) -> Unit) {
    val sound = LocalSound.current
    val haptics = LocalHaptics.current
    val speed = listOf(0.34f, 0.44f, 0.56f)[variant]
    val gapHalf = listOf(0.19f, 0.165f, 0.145f)[variant]
    val spacing = listOf(1.55f, 1.4f, 1.25f)[variant]
    val birdX = 0.27f

    var birdY by remember { mutableFloatStateOf(0.5f) }
    var vy by remember { mutableFloatStateOf(0f) }
    var pipes by remember { mutableStateOf<List<Pipe>>(emptyList()) }
    var spawnIn by remember { mutableFloatStateOf(0.7f) }
    var score by remember { mutableIntStateOf(0) }
    var over by remember { mutableStateOf(false) }

    fun report() {
        if (over) return
        over = true
        finish(score * 10L, score >= 10)
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
                vy += 1.55f * dt
                birdY += vy * dt
                if (birdY < 0.03f) {
                    birdY = 0.03f
                    vy = 0f
                }
                spawnIn -= dt
                if (spawnIn <= 0f) {
                    pipes = pipes + Pipe(1.15f, 0.22f + Random.nextFloat() * 0.56f, false)
                    spawnIn = spacing
                }
                var gained = 0
                val next = mutableListOf<Pipe>()
                for (p in pipes) {
                    val x = p.x - speed * dt
                    var scored = p.scored
                    if (!scored && x < birdX - 0.06f) {
                        scored = true
                        gained++
                    }
                    if (x > -0.2f) next += Pipe(x, p.gapY, scored)
                }
                if (gained > 0) {
                    score += gained
                    sound.play(Sound.POINT)
                    haptics.tap()
                }
                pipes = next
                val hit = birdY > 0.97f || pipes.any {
                    abs(it.x - birdX) < 0.095f &&
                        (birdY < it.gapY - gapHalf || birdY > it.gapY + gapHalf)
                }
                if (hit) {
                    sound.play(Sound.LOSE)
                    haptics.error()
                    report()
                    return@withFrameNanos
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
            AppText("$score tuyaux", style = Typo.heading, color = Ink.Text)
            Pill("${score * 10} pts", color = Ink.Mustard)
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(over) {
                    detectTapGestures {
                        if (over) return@detectTapGestures
                        vy = -0.62f
                        sound.play(Sound.TAP)
                        haptics.tap()
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = Ink.Card,
                    size = Size(size.width, size.height),
                    cornerRadius = CornerRadius(24f),
                )
                // Nuages fixes, pour l'ambiance.
                drawCircle(Ink.BgDeep, size.width * 0.09f, Offset(size.width * 0.18f, size.height * 0.12f))
                drawCircle(Ink.BgDeep, size.width * 0.065f, Offset(size.width * 0.27f, size.height * 0.10f))
                drawCircle(Ink.BgDeep, size.width * 0.07f, Offset(size.width * 0.72f, size.height * 0.20f))
                val pipeW = size.width * 0.13f
                for (p in pipes) {
                    val cx = p.x * size.width
                    val top = (p.gapY - gapHalf) * size.height
                    val bottom = (p.gapY + gapHalf) * size.height
                    drawRoundRect(
                        color = Ink.Green,
                        topLeft = Offset(cx - pipeW / 2f, -20f),
                        size = Size(pipeW, top + 20f),
                        cornerRadius = CornerRadius(8f),
                    )
                    drawRoundRect(
                        color = Ink.Green,
                        topLeft = Offset(cx - pipeW * 0.62f, top - size.height * 0.028f),
                        size = Size(pipeW * 1.24f, size.height * 0.028f),
                        cornerRadius = CornerRadius(6f),
                    )
                    drawRoundRect(
                        color = Ink.Green,
                        topLeft = Offset(cx - pipeW / 2f, bottom),
                        size = Size(pipeW, size.height - bottom + 20f),
                        cornerRadius = CornerRadius(8f),
                    )
                    drawRoundRect(
                        color = Ink.Green,
                        topLeft = Offset(cx - pipeW * 0.62f, bottom),
                        size = Size(pipeW * 1.24f, size.height * 0.028f),
                        cornerRadius = CornerRadius(6f),
                    )
                }
                val bx = birdX * size.width
                val by = birdY * size.height
                val r = min(size.width, size.height) * 0.045f
                drawCircle(Ink.Mustard, r, Offset(bx, by))
                drawCircle(Ink.Card, r * 0.38f, Offset(bx + r * 0.33f, by - r * 0.3f))
                drawCircle(Ink.Text, r * 0.17f, Offset(bx + r * 0.40f, by - r * 0.3f))
                drawCircle(Ink.Coral, r * 0.30f, Offset(bx + r * 0.95f, by + r * 0.08f))
                drawOval(
                    color = Ink.Card.copy(alpha = 0.7f),
                    topLeft = Offset(bx - r * 1.05f, by - r * 0.1f),
                    size = Size(r * 0.9f, r * 0.5f),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        HintBar("Tapez l'écran pour monter. Chaque tuyau passé rapporte 10 points.")
    }
}
