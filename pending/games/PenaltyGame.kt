package com.botillon.games.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import kotlin.math.min
import kotlin.random.Random

/** Penalty : choisir un côté, tromper le gardien, cinq tirs. */
object PenaltyGame {
    val entry = GameEntry(
        id = "penalty",
        title = "Penalty",
        tagline = "Le gardien plonge… du bon côté ?",
        family = Family.ARCADE,
        accent = Ink.Blue,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        PenaltyScreen(variant, finish)
    }
}

@Composable
private fun PenaltyScreen(variant: Int, finish: (Long, Boolean) -> Unit) {
    val keeperSkill = listOf(0.28f, 0.45f, 0.62f)[variant]
    val sound = LocalSound.current
    val haptics = LocalHaptics.current
    var phase by remember { mutableIntStateOf(0) }
    var goals by remember { mutableIntStateOf(0) }
    var shots by remember { mutableIntStateOf(0) }
    var targetCol by remember { mutableIntStateOf(1) }
    var targetY by remember { mutableFloatStateOf(0.35f) }
    var keeperCol by remember { mutableIntStateOf(0) }
    var ballT by remember { mutableFloatStateOf(0f) }
    var keeperT by remember { mutableFloatStateOf(0f) }
    var wait by remember { mutableFloatStateOf(0f) }
    var message by remember { mutableStateOf("Choisissez un côté du but.") }
    var reported by remember { mutableStateOf(false) }

    fun report() {
        if (reported) return
        reported = true
        finish((goals * 20).toLong(), goals >= 3)
    }

    fun shoot(x: Float, y: Float, w: Float, h: Float) {
        if (phase != 0 || reported) return
        if (y !in h * 0.20f..h * 0.50f || x < w * 0.16f || x > w * 0.84f) return
        targetCol = when {
            x < w * 0.39f -> 0
            x < w * 0.61f -> 1
            else -> 2
        }
        targetY = (y / h).coerceIn(0.28f, 0.42f)
        keeperCol = if (Random.nextFloat() < keeperSkill) targetCol
        else (targetCol + if (Random.nextBoolean()) 1 else 2) % 3
        ballT = 0f
        keeperT = 0f
        shots += 1
        phase = 1
        sound.play(Sound.TAP)
        haptics.tap()
    }

    LaunchedEffect(Unit) {
        var last = 0L
        while (!reported) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else min(0.034f, (now - last) / 1_000_000_000f)
                last = now
                if (dt <= 0f) return@withFrameNanos
                if (phase == 1) {
                    ballT = (ballT + dt / 0.50f).coerceAtMost(1f)
                    keeperT = (keeperT + dt / 0.30f).coerceAtMost(1f)
                    if (ballT >= 1f) {
                        if (keeperCol == targetCol) {
                            message = "Arrêt du gardien !"
                            sound.play(Sound.LOSE)
                            haptics.error()
                        } else {
                            goals += 1
                            message = "But !"
                            sound.play(Sound.WIN)
                            haptics.success()
                        }
                        phase = 2
                        wait = 0f
                    }
                } else if (phase == 2) {
                    wait += dt
                    if (wait >= 1.10f) {
                        if (shots >= 5) report()
                        else {
                            phase = 0
                            message = "Au suivant. Choisissez un côté."
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
            AppText("Buts $goals", style = Typo.heading, color = Ink.Text)
            AppText("Tir ${(shots + if (phase == 0) 1 else 0).coerceAtMost(5)} / 5", style = Typo.heading, color = Ink.Soft)
        }
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(Unit) {
                    detectTapGestures { o ->
                        shoot(o.x, o.y, size.width.toFloat(), size.height.toFloat())
                    }
                },
        ) {
            val w = size.width
            val h = size.height
            drawRoundRect(Ink.Card, cornerRadius = CornerRadius(24f))

            // Terrain.
            drawRect(
                Ink.Green.copy(alpha = 0.12f),
                topLeft = Offset(0f, h * 0.52f),
                size = Size(w, h * 0.48f),
            )
            drawLine(Ink.Green.copy(alpha = 0.35f), Offset(0f, h * 0.52f), Offset(w, h * 0.52f), strokeWidth = 3f)

            // But et filet.
            val gl = w * 0.16f
            val gr = w * 0.84f
            val gt = h * 0.22f
            val gb = h * 0.46f
            drawRect(Ink.CardAlt, topLeft = Offset(gl, gt), size = Size(gr - gl, gb - gt))
            for (i in 1..6) {
                val x = gl + (gr - gl) * i / 7f
                drawLine(Ink.Line, Offset(x, gt), Offset(x, gb), strokeWidth = 1.5f)
            }
            for (i in 1..4) {
                val y = gt + (gb - gt) * i / 5f
                drawLine(Ink.Line, Offset(gl, y), Offset(gr, y), strokeWidth = 1.5f)
            }
            drawRect(Ink.Card, topLeft = Offset(gl, gt), size = Size(gr - gl, gb - gt), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f))
            drawRect(Ink.Muted, topLeft = Offset(gl - 4f, gt), size = Size(8f, gb - gt))
            drawRect(Ink.Muted, topLeft = Offset(gr - 4f, gt), size = Size(8f, gb - gt))

            // Zones discrètes.
            if (phase == 0) {
                val zy = h * 0.335f
                listOf(0.28f, 0.50f, 0.72f).forEach { zx ->
                    drawCircle(Ink.Blue.copy(alpha = 0.10f), w * 0.045f, Offset(w * zx, zy))
                }
            }

            // Gardien.
            val kx = w * (0.50f + (listOf(0.28f, 0.50f, 0.72f)[keeperCol] - 0.50f) * keeperT)
            val ky = h * 0.42f
            drawCircle(Ink.Mustard, w * 0.028f, Offset(kx, ky - h * 0.075f))
            drawRoundRect(
                Ink.Mustard,
                topLeft = Offset(kx - w * 0.045f, ky - h * 0.055f),
                size = Size(w * 0.09f, h * 0.13f),
                cornerRadius = CornerRadius(w * 0.03f),
            )
            drawLine(Ink.Mustard, Offset(kx - w * 0.02f, ky + h * 0.07f), Offset(kx - w * 0.07f, ky + h * 0.09f), strokeWidth = w * 0.018f)
            drawLine(Ink.Mustard, Offset(kx + w * 0.02f, ky + h * 0.07f), Offset(kx + w * 0.07f, ky + h * 0.09f), strokeWidth = w * 0.018f)

            // Ballon et point de penalty.
            drawCircle(Ink.Line, w * 0.05f, Offset(w * 0.5f, h * 0.80f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
            val tx = w * listOf(0.28f, 0.50f, 0.72f)[targetCol]
            val px = w * 0.5f + (tx - w * 0.5f) * ballT
            val py = h * 0.80f + (h * targetY - h * 0.80f) * ballT
            drawCircle(Ink.Coral, w * 0.026f, Offset(px, py))
            drawCircle(Ink.Card.copy(alpha = 0.55f), w * 0.008f, Offset(px - w * 0.008f, py - w * 0.008f))
        }
        Spacer(Modifier.height(10.dp))
        AppText(message, style = Typo.body, color = Ink.Soft)
        Spacer(Modifier.height(6.dp))
        HintBar("Touchez un côté du but. Trois buts sur cinq suffisent pour gagner.")
    }
}
