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
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

private data class VisTarget(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val color: Color,
)

private fun newTarget(): VisTarget = VisTarget(
    x = Random.nextFloat() * 0.62f + 0.19f,
    y = Random.nextFloat() * 0.58f + 0.20f,
    vx = (if (Random.nextBoolean()) 0.20f else -0.20f) * (0.8f + Random.nextFloat() * 0.7f),
    vy = (if (Random.nextBoolean()) 0.13f else -0.13f) * (0.8f + Random.nextFloat() * 0.7f),
    color = Ink.Accents[Random.nextInt(Ink.Accents.size)],
)

/** Visée : des cibles mobiles, vingt tirs, la précision compte. */
object ViseeGame {
    val entry = GameEntry(
        id = "visee",
        title = "Visée",
        tagline = "Vingt tirs, zéro tremblement",
        family = Family.ARCADE,
        accent = Ink.Blue,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        Visee(variant, finish)
    }
}

@Composable
private fun Visee(variant: Int, finish: (Long, Boolean) -> Unit) {
    val sound = LocalSound.current
    val haptics = LocalHaptics.current
    val speedScale = listOf(0.65f, 1f, 1.35f)[variant]
    val targetRadius = 0.062f

    var targets by remember { mutableStateOf(List(3) { newTarget() }) }
    var shots by remember { mutableIntStateOf(0) }
    var hits by remember { mutableIntStateOf(0) }
    var over by remember { mutableStateOf(false) }

    fun report() {
        if (over) return
        over = true
        val accuracy = if (shots == 0) 0 else hits * 100 / shots
        finish((hits * 10 + accuracy / 5).toLong(), hits >= 12)
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
                targets = targets.map { t ->
                    var nx = t.x + t.vx * speedScale * dt
                    var ny = t.y + t.vy * speedScale * dt
                    var nvx = t.vx
                    var nvy = t.vy
                    if (nx < 0.10f) {
                        nx = 0.10f
                        nvx = abs(nvx)
                    }
                    if (nx > 0.90f) {
                        nx = 0.90f
                        nvx = -abs(nvx)
                    }
                    if (ny < 0.11f) {
                        ny = 0.11f
                        nvy = abs(nvy)
                    }
                    if (ny > 0.89f) {
                        ny = 0.89f
                        nvy = -abs(nvy)
                    }
                    t.copy(x = nx, y = ny, vx = nvx, vy = nvy)
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
            AppText("Touches $hits", style = Typo.heading, color = Ink.Text)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill("Tirs $shots / 20", color = Ink.Soft)
                Pill(
                    text = if (shots == 0) "— %" else "${hits * 100 / shots} %",
                    color = Ink.Blue,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(over) {
                    detectTapGestures { offset ->
                        if (over || shots >= 20) return@detectTapGestures
                        val side = min(size.width, size.height).toFloat()
                        val radius = targetRadius * side
                        var hit: VisTarget? = null
                        for (t in targets) {
                            val dx = offset.x - t.x * size.width
                            val dy = offset.y - t.y * size.height
                            if (dx * dx + dy * dy <= radius * radius) {
                                hit = t
                                break
                            }
                        }
                        shots++
                        val touched = hit
                        if (touched != null) {
                            hits++
                            targets = targets - touched + newTarget()
                            sound.play(Sound.MATCH)
                            haptics.success()
                        } else {
                            sound.play(Sound.TAP)
                            haptics.error()
                        }
                        if (shots >= 20) report()
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = Ink.Card,
                    size = Size(size.width, size.height),
                    cornerRadius = CornerRadius(24f),
                )
                // Graduations de stand de tir.
                for (i in 1..4) {
                    drawCircle(
                        color = Ink.Line.copy(alpha = 0.7f),
                        radius = size.minDimension * 0.11f * i,
                        center = Offset(size.width / 2f, size.height / 2f),
                        style = Stroke(width = 1.5f),
                    )
                }
                val side = min(size.width, size.height)
                for (t in targets) {
                    val center = Offset(t.x * size.width, t.y * size.height)
                    val radius = targetRadius * side
                    drawCircle(Ink.Text.copy(alpha = 0.08f), radius * 1.35f, center)
                    drawCircle(Color.White, radius, center)
                    drawCircle(t.color.copy(alpha = 0.20f), radius, center)
                    drawCircle(t.color, radius, center, style = Stroke(width = radius * 0.16f))
                    drawCircle(t.color, radius * 0.55f, center, style = Stroke(width = radius * 0.12f))
                    drawCircle(t.color, radius * 0.14f, center)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        HintBar("Tapez les cibles mobiles. Chaque tir manqué coûte de la précision.")
    }
}
