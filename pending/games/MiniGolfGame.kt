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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.botillon.games.ui.*
import kotlin.math.min
import kotlin.math.sqrt

/** Mini golf : neuf trous, puissance et direction au putting. */
object MiniGolfGame {
    val entry = GameEntry(
        id = "mini-golf",
        title = "Mini golf",
        tagline = "Neuf trous, un putter",
        family = Family.ARCADE,
        accent = Ink.Green,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        MiniGolfScreen(variant, finish)
    }
}

private val GOLF_HOLES = listOf(
    0.50f to 0.22f,
    0.26f to 0.28f,
    0.74f to 0.26f,
    0.30f to 0.55f,
    0.70f to 0.58f,
    0.50f to 0.42f,
    0.22f to 0.70f,
    0.78f to 0.72f,
    0.50f to 0.18f,
)

@Composable
private fun MiniGolfScreen(variant: Int, finish: (Long, Boolean) -> Unit) {
    val friction = listOf(0.978f, 0.975f, 0.972f)[variant]
    val sound = LocalSound.current
    var hole by remember { mutableIntStateOf(0) }
    var bx by remember { mutableFloatStateOf(0.50f) }
    var by by remember { mutableFloatStateOf(0.85f) }
    var vx by remember { mutableFloatStateOf(0f) }
    var vy by remember { mutableFloatStateOf(0f) }
    var strokes by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var phase by remember { mutableIntStateOf(0) }
    var wait by remember { mutableFloatStateOf(0f) }
    var aimStart by remember { mutableStateOf(Offset.Zero) }
    var aimNow by remember { mutableStateOf(Offset.Zero) }
    var message by remember { mutableStateOf("Glissez pour putter.") }
    var reported by remember { mutableStateOf(false) }

    fun report() {
        if (reported) return
        reported = true
        finish((900 - total * 20).coerceAtLeast(0).toLong(), total <= 27)
    }

    fun nextHole() {
        total += strokes
        strokes = 0
        if (hole >= 8) {
            report()
        } else {
            hole += 1
            bx = 0.50f
            by = 0.85f
            vx = 0f
            vy = 0f
            phase = 0
            message = "Trou ${hole + 1} : à vous."
        }
    }

    LaunchedEffect(Unit) {
        var last = 0L
        while (!reported) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else min(0.034f, (now - last) / 1_000_000_000f)
                last = now
                if (dt <= 0f) return@withFrameNanos
                if (phase == 1) {
                    bx += vx * dt
                    by += vy * dt
                    vx *= friction
                    vy *= friction
                    if (bx < 0.06f) { bx = 0.06f; vx = kotlin.math.abs(vx) * 0.72f }
                    if (bx > 0.94f) { bx = 0.94f; vx = -kotlin.math.abs(vx) * 0.72f }
                    if (by < 0.06f) { by = 0.06f; vy = kotlin.math.abs(vy) * 0.72f }
                    if (by > 0.94f) { by = 0.94f; vy = -kotlin.math.abs(vy) * 0.72f }
                    val hp = GOLF_HOLES[hole]
                    val dx = bx - hp.first
                    val dy = (by - hp.second) * 1.5f
                    val speed = sqrt(vx * vx + vy * vy)
                    if (sqrt(dx * dx + dy * dy) < 0.050f && speed < 1.1f) {
                        phase = 2
                        wait = 0f
                        message = "Dans le trou !"
                        sound.play(Sound.WIN)
                    } else if (speed < 0.055f) {
                        vx = 0f
                        vy = 0f
                        phase = 0
                        message = "Encore un coup."
                        sound.play(Sound.TAP)
                    }
                } else if (phase == 2) {
                    wait += dt
                    if (wait >= 0.85f) nextHole()
                }
            }
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AppText("Trou ${hole + 1} / 9", style = Typo.heading, color = Ink.Text)
            AppText("Coups $strokes", style = Typo.heading, color = Ink.Soft)
        }
        Spacer(Modifier.height(4.dp))
        AppText(message, style = Typo.small, color = Ink.Muted)
        Spacer(Modifier.height(6.dp))
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
                                val h = size.height.toFloat()
                                val dx = bx * w - aimNow.x
                                val dy = by * h - aimNow.y
                                val len = sqrt(dx * dx + dy * dy)
                                val power = (len / (w * 0.46f)).coerceIn(0f, 1f)
                                if (power > 0.06f) {
                                    val speed = 0.45f + power * 2.25f
                                    vx = dx / len * speed
                                    vy = dy / len * speed * (w / h)
                                    strokes += 1
                                    phase = 1
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
            drawRoundRect(
                Ink.Green.copy(alpha = 0.16f),
                topLeft = Offset(w * 0.04f, h * 0.04f),
                size = Size(w * 0.92f, h * 0.92f),
                cornerRadius = CornerRadius(20f),
            )
            drawRoundRect(
                Ink.Green.copy(alpha = 0.30f),
                topLeft = Offset(w * 0.04f, h * 0.04f),
                size = Size(w * 0.92f, h * 0.92f),
                cornerRadius = CornerRadius(20f),
                style = Stroke(width = 3f),
            )

            val hp = GOLF_HOLES[hole]
            val hx = hp.first * w
            val hy = hp.second * h
            drawCircle(Ink.Text, w * 0.045f, Offset(hx, hy))
            drawCircle(Ink.Text.copy(alpha = 0.55f), w * 0.060f, Offset(hx, hy), style = Stroke(width = 3f))
            drawLine(Ink.Card, Offset(hx, hy), Offset(hx, hy - h * 0.075f), strokeWidth = 3f)
            drawRect(Ink.Coral, topLeft = Offset(hx, hy - h * 0.075f), size = Size(w * 0.045f, h * 0.025f))

            if (phase == 0 && aimNow != Offset.Zero) {
                drawLine(Ink.Text.copy(alpha = 0.35f), Offset(bx * w, by * h), aimNow, strokeWidth = 2f)
                drawCircle(Ink.Teal.copy(alpha = 0.25f), w * 0.03f, aimNow)
            }

            val ball = Offset(bx * w, by * h)
            drawCircle(Ink.Card, w * 0.021f, ball)
            drawCircle(Ink.Muted, w * 0.021f, ball, style = Stroke(width = 2f))
        }
        Spacer(Modifier.height(10.dp))
        HintBar("Tirez vers l'arrière puis relâchez. Le par est de 3 coups par trou.")
    }
}
