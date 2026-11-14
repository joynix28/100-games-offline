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
import androidx.compose.ui.graphics.drawscope.Stroke
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

private data class CibleTarget(
    val x: Float,
    val y: Float,
    val age: Float,
    val life: Float,
    val color: Color,
)

/** Cible : des cercles rétrécissent, il faut les toucher avant disparition. */
object CibleGame {
    val entry = GameEntry(
        id = "cible",
        title = "Tape la cible",
        tagline = "Douze cercles, pas un de raté",
        family = Family.ARCADE,
        accent = Ink.Coral,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        Cible(variant, finish)
    }
}

@Composable
private fun Cible(variant: Int, finish: (Long, Boolean) -> Unit) {
    val sound = LocalSound.current
    val haptics = LocalHaptics.current
    val life = listOf(1.55f, 1.25f, 1.0f)[variant]
    val gap = listOf(0.85f, 0.68f, 0.54f)[variant]
    val total = 12

    var targets by remember { mutableStateOf<List<CibleTarget>>(emptyList()) }
    var spawned by remember { mutableIntStateOf(0) }
    var spawnIn by remember { mutableFloatStateOf(0.45f) }
    var hits by remember { mutableIntStateOf(0) }
    var misses by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var over by remember { mutableStateOf(false) }

    fun report() {
        if (over) return
        over = true
        finish(score.toLong(), hits >= 10)
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
                spawnIn -= dt
                if (spawnIn <= 0f && spawned < total) {
                    targets = targets + CibleTarget(
                        x = Random.nextFloat() * 0.62f + 0.19f,
                        y = Random.nextFloat() * 0.56f + 0.20f,
                        age = 0f,
                        life = life,
                        color = Ink.Accents[Random.nextInt(Ink.Accents.size)],
                    )
                    spawned++
                    spawnIn = gap
                }
                var expired = 0
                val alive = mutableListOf<CibleTarget>()
                for (t in targets) {
                    val next = t.copy(age = t.age + dt)
                    if (next.age < next.life) alive += next else expired++
                }
                if (expired > 0) {
                    misses += expired
                    sound.play(Sound.LOSE)
                }
                targets = alive
                if (spawned >= total && alive.isEmpty()) report()
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
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill("$hits touchés", color = Ink.Teal)
                Pill("${(hits + misses).coerceAtMost(total)} / $total", color = Ink.Soft)
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
                        var best: CibleTarget? = null
                        var bestRadius = Float.MAX_VALUE
                        for (t in targets) {
                            val radius = (0.10f - 0.062f * (t.age / t.life)) * side
                            val dx = offset.x - t.x * size.width
                            val dy = offset.y - t.y * size.height
                            if (dx * dx + dy * dy <= radius * radius && radius < bestRadius) {
                                best = t
                                bestRadius = radius
                            }
                        }
                        val hit = best
                        if (hit == null) {
                            haptics.error()
                        } else {
                            score += (10f + (1f - hit.age / hit.life) * 15f).toInt()
                            hits++
                            targets = targets - hit
                            sound.play(Sound.MATCH)
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
                for (t in targets) {
                    val radius = (0.10f - 0.062f * (t.age / t.life)) * size.minDimension
                    val center = Offset(t.x * size.width, t.y * size.height)
                    drawCircle(t.color.copy(alpha = 0.16f), radius, center)
                    drawCircle(t.color, radius, center, style = Stroke(width = radius * 0.13f))
                    drawCircle(t.color, radius * 0.52f, center, style = Stroke(width = radius * 0.10f))
                    drawCircle(t.color, radius * 0.12f, center)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        HintBar("Touchez le centre : plus le cercle est petit, plus il rapporte.")
    }
}
