package com.botillon.games.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.botillon.games.ui.*
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/** Basket : dix tirs, visée par glissement, physique simple. */
object BasketGame {
    val entry = GameEntry(
        id = "basket",
        title = "Basket",
        tagline = "Dix tirs, un seul geste",
        family = Family.ARCADE,
        accent = Ink.Mustard,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        BasketScreen(variant, finish)
    }
}

@Composable
private fun BasketScreen(variant: Int, finish: (Long, Boolean) -> Unit) {
    val rim = listOf(0.105f, 0.088f, 0.072f)[variant]
    val wind = listOf(0f, 0.10f, -0.14f)[variant]
    val sound = LocalSound.current
    val haptics = LocalHaptics.current
    var bx by remember { mutableFloatStateOf(0.30f) }
    var by by remember { mutableFloatStateOf(0.84f) }
    var vx by remember { mutableFloatStateOf(0f) }
    var vy by remember { mutableFloatStateOf(0f) }
    var phase by remember { mutableIntStateOf(0) }
    var shots by remember { mutableIntStateOf(0) }
    var made by remember { mutableIntStateOf(0) }
    var wait by remember { mutableFloatStateOf(0f) }
    var aimStart by remember { mutableStateOf(Offset.Zero) }
    var aimNow by remember { mutableStateOf(Offset.Zero) }
    var scored by remember { mutableStateOf(false) }
    var reported by remember { mutableStateOf(false) }
    val hoopX = 0.70f
    val hoopY = 0.30f

    fun report() {
        if (reported) return
        reported = true
        finish((made * 10).toLong(), made >= 6)
    }

    fun endShot() {
        phase = 2
        wait = 0f
    }

    LaunchedEffect(Unit) {
        var last = 0L
        while (!reported) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else min(0.034f, (now - last) / 1_000_000_000f)
                last = now
                if (dt <= 0f) return@withFrameNanos
                if (phase == 1) {
                    val prevY = by
                    vx += wind * dt
                    vy += 1.55f * dt
                    bx += vx * dt
                    by += vy * dt
                    if (!scored && vy > 0f && prevY < hoopY && by >= hoopY &&
                        abs(bx - hoopX) < rim
                    ) {
                        scored = true
                        made += 1
                        sound.play(Sound.WIN)
                        haptics.success()
                        endShot()
                    } else if (by > 1.05f || bx < -0.1f || bx > 1.1f) {
                        sound.play(Sound.LOSE)
                        endShot()
                    }
                } else if (phase == 2) {
                    wait += dt
                    if (wait >= 0.75f) {
                        shots += 1
                        if (shots >= 10) report()
                        else {
                            bx = 0.30f
                            by = 0.84f
                            vx = 0f
                            vy = 0f
                            scored = false
                            phase = 0
                        }
                    }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            AppText("Paniers $made", style = Typo.heading, color = Ink.Text)
            AppText("Tir ${(shots + 1).coerceAtMost(10)} / 10", style = Typo.heading, color = Ink.Soft)
        }
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { if (phase == 0) { aimStart = it; aimNow = it } },
                        onDragEnd = {
                            if (phase == 0) {
                                val w = size.width.toFloat()
                                val dx = (aimStart.x - aimNow.x) / w
                                val dy = (aimStart.y - aimNow.y) / size.height.toFloat()
                                val len = sqrt(dx * dx + dy * dy)
                                if (len >= 0.03f) {
                                    val speed = 0.9f + min(0.6f, len) * 1.8f
                                    vx = dx / len * speed
                                    vy = dy / len * speed
                                    phase = 1
                                    scored = false
                                    sound.play(Sound.TAP)
                                }
                            }
                        },
                    ) { change, _ ->
                        change.consume()
                        if (phase == 0) aimNow = change.position
                    }
                },
        ) {
            val w = size.width
            val h = size.height
            drawRoundRect(Ink.Card, cornerRadius = CornerRadius(24f))
            drawLine(
                Ink.Line,
                Offset(0f, h * 0.52f),
                Offset(w, h * 0.52f),
                strokeWidth = 2f,
            )

            // Panier.
            val hx = hoopX * w
            val hy = hoopY * h
            val rw = rim * w
            drawRoundRect(
                Ink.CardAlt,
                topLeft = Offset(hx + rw + w * 0.012f, hy - h * 0.10f),
                size = Size(w * 0.022f, h * 0.18f),
                cornerRadius = CornerRadius(4f),
            )
            drawLine(Ink.Mustard, Offset(hx - rw, hy), Offset(hx + rw, hy), strokeWidth = w * 0.012f)
            repeat(5) { i ->
                val t = i / 4f
                val nxp = hx - rw + 2f * rw * t
                drawLine(
                    Ink.Muted.copy(alpha = 0.55f),
                    Offset(nxp, hy),
                    Offset(hx - rw * 0.35f + rw * 0.7f * t, hy + h * 0.075f),
                    strokeWidth = 1.5f,
                )
            }

            // Visée.
            if (phase == 0 && aimNow != Offset.Zero) {
                drawLine(Ink.Teal.copy(alpha = 0.55f), Offset(bx * w, by * h), aimNow, strokeWidth = 3f)
                drawCircle(Ink.Teal.copy(alpha = 0.25f), w * 0.035f, aimNow)
            }

            // Ballon.
            val c = Offset(bx * w, by * h)
            val br = w * 0.038f
            drawCircle(Ink.Coral, br, c)
            drawLine(Ink.Text.copy(alpha = 0.45f), Offset(c.x - br, c.y), Offset(c.x + br, c.y), strokeWidth = 2f)
            drawLine(Ink.Text.copy(alpha = 0.45f), Offset(c.x, c.y - br), Offset(c.x, c.y + br), strokeWidth = 2f)
            drawCircle(Ink.Card.copy(alpha = 0.6f), br * 0.16f, Offset(c.x - br * 0.3f, c.y - br * 0.35f))
        }
        Spacer(Modifier.height(10.dp))
        HintBar("Glissez vers le bas pour armer, relâchez pour tirer. Le vent dévie la balle.")
    }
}
