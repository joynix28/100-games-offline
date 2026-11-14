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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.botillon.games.ui.*
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/** Billard 8 : la blanche, cinq couleurs, puis la noire en dernier. */
object BilliardsGame {
    val entry = GameEntry(
        id = "billard-8",
        title = "Billard 8",
        tagline = "Empocher la 8 en dernier",
        family = Family.ARCADE,
        accent = Ink.Green,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        BilliardsScreen(variant, finish)
    }
}

private data class BBall(
    val x: Float,
    val y: Float,
    val vx: Float = 0f,
    val vy: Float = 0f,
    val color: Color,
    val num: Int,
    val potted: Boolean = false,
)

private val POCKETS = listOf(
    0.055f to 0.075f, 0.945f to 0.075f,
    0.055f to 0.925f, 0.945f to 0.925f,
    0.50f to 0.045f, 0.50f to 0.955f,
)

private fun rack() = listOf(
    BBall(0.24f, 0.55f, color = Ink.Card, num = 0),
    BBall(0.62f, 0.44f, color = Ink.Coral, num = 1),
    BBall(0.62f, 0.56f, color = Ink.Blue, num = 2),
    BBall(0.68f, 0.38f, color = Ink.Mustard, num = 3),
    BBall(0.68f, 0.50f, color = Ink.Violet, num = 4),
    BBall(0.68f, 0.62f, color = Ink.Green, num = 5),
    BBall(0.75f, 0.50f, color = Ink.Text, num = 8),
)

@Composable
private fun BilliardsScreen(variant: Int, finish: (Long, Boolean) -> Unit) {
    val friction = listOf(0.984f, 0.980f, 0.976f)[variant]
    val sound = LocalSound.current
    var balls by remember { mutableStateOf(rack()) }
    var strokes by remember { mutableIntStateOf(0) }
    var aimNow by remember { mutableStateOf(Offset.Zero) }
    var message by remember { mutableStateOf("Visez avec la blanche.") }
    var reported by remember { mutableStateOf(false) }
    val colors = balls.count { it.potted && it.num != 8 }
    val moving = balls.any { !it.potted && abs(it.vx) + abs(it.vy) > 0.012f }

    fun canShoot(): Boolean = balls.all { it.potted || abs(it.vx) + abs(it.vy) <= 0.012f }

    fun report(won: Boolean) {
        if (reported) return
        reported = true
        val scored = balls.count { it.potted && it.num != 8 }
        val score = if (won) (scored * 40 + (15 - strokes).coerceAtLeast(0) * 5) else scored * 20
        finish(score.toLong(), won)
    }

    LaunchedEffect(Unit) {
        var last = 0L
        while (!reported) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else min(0.034f, (now - last) / 1_000_000_000f)
                last = now
                if (dt <= 0f) return@withFrameNanos
                val arr = balls.toMutableList()
                var active = false
                for (i in arr.indices) {
                    val b = arr[i]
                    if (b.potted) continue
                    if (abs(b.vx) + abs(b.vy) > 0.012f) active = true
                    arr[i] = b.copy(
                        x = b.x + b.vx * dt,
                        y = b.y + b.vy * dt,
                        vx = b.vx * friction,
                        vy = b.vy * friction,
                    )
                }
                for (i in arr.indices) {
                    val b = arr[i]
                    if (b.potted) continue
                    var nb = b
                    if (nb.x < 0.035f) nb = nb.copy(x = 0.035f, vx = abs(nb.vx) * 0.85f)
                    if (nb.x > 0.965f) nb = nb.copy(x = 0.965f, vx = -abs(nb.vx) * 0.85f)
                    if (nb.y < 0.035f) nb = nb.copy(y = 0.035f, vy = abs(nb.vy) * 0.85f)
                    if (nb.y > 0.965f) nb = nb.copy(y = 0.965f, vy = -abs(nb.vy) * 0.85f)
                    val pocketed = POCKETS.any { p ->
                        val dx = nb.x - p.first
                        val dy = nb.y - p.second
                        sqrt(dx * dx + dy * dy) < 0.048f
                    }
                    if (pocketed) {
                        nb = if (nb.num == 0) {
                            nb.copy(x = 0.24f, y = 0.55f, vx = 0f, vy = 0f)
                        } else {
                            nb.copy(vx = 0f, vy = 0f, potted = true)
                        }
                        sound.play(Sound.POINT)
                    }
                    arr[i] = nb
                }
                for (i in arr.indices) {
                    if (arr[i].potted) continue
                    for (j in i + 1 until arr.size) {
                        if (arr[j].potted) continue
                        val a = arr[i]
                        val b = arr[j]
                        val dx = b.x - a.x
                        val dy = b.y - a.y
                        val d = sqrt(dx * dx + dy * dy)
                        if (d in 0.0001f..0.051f) {
                            val ux = dx / d
                            val uy = dy / d
                            val p = (a.vx - b.vx) * ux + (a.vy - b.vy) * uy
                            if (p > 0f) {
                                arr[i] = a.copy(vx = a.vx - p * ux, vy = a.vy - p * uy)
                                arr[j] = b.copy(vx = b.vx + p * ux, vy = b.vy + p * uy)
                            }
                        }
                    }
                }
                balls = arr
                val pottedColors = arr.count { it.potted && it.num != 8 }
                val black = arr.any { it.potted && it.num == 8 }
                if (black && pottedColors >= 5) {
                    report(true)
                    sound.play(Sound.WIN)
                } else if (black) {
                    message = "La 8 est tombée trop tôt."
                    report(false)
                    sound.play(Sound.LOSE)
                } else if (!active && !black && strokes >= 14) {
                    message = "Plus de coups : partie perdue."
                    report(false)
                } else if (!active && strokes > 0) {
                    message = "Couleurs : $pottedColors / 5."
                }
            }
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AppText("Coups $strokes / 14", style = Typo.heading, color = Ink.Text)
            AppText("Couleurs $colors / 5", style = Typo.heading, color = Ink.Soft)
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
                        onDragStart = { if (canShoot() && !reported) aimNow = it },
                        onDragEnd = {
                            if (canShoot() && !reported && aimNow != Offset.Zero) {
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()
                                val cue = balls[0]
                                val dx = aimNow.x / w - cue.x
                                val dy = aimNow.y / h - cue.y
                                val len = sqrt(dx * dx + dy * dy)
                                if (len > 0.045f) {
                                    val speed = 1.0f + min(0.6f, len) * 2.2f
                                    val next = balls.toMutableList()
                                    next[0] = cue.copy(vx = dx / len * speed, vy = dy / len * speed * (w / h))
                                    balls = next
                                    strokes += 1
                                    sound.play(Sound.TAP)
                                }
                                aimNow = Offset.Zero
                            }
                        },
                    ) { change, _ ->
                        change.consume()
                        if (canShoot() && !reported) aimNow = change.position
                    }
                },
        ) {
            val w = size.width
            val h = size.height
            drawRoundRect(Ink.Card, cornerRadius = CornerRadius(26f))
            drawRoundRect(
                Ink.Green.copy(alpha = 0.20f),
                topLeft = Offset(w * 0.03f, h * 0.03f),
                size = Size(w * 0.94f, h * 0.94f),
                cornerRadius = CornerRadius(18f),
            )
            drawRoundRect(
                Ink.Green.copy(alpha = 0.35f),
                topLeft = Offset(w * 0.03f, h * 0.03f),
                size = Size(w * 0.94f, h * 0.94f),
                cornerRadius = CornerRadius(18f),
                style = Stroke(width = 2f),
            )
            POCKETS.forEach { p ->
                drawCircle(Ink.Text, w * 0.043f, Offset(p.first * w, p.second * h))
            }
            drawLine(Ink.Card.copy(alpha = 0.35f), Offset(w * 0.5f, h * 0.30f), Offset(w * 0.5f, h * 0.70f), strokeWidth = 2f)

            if (!moving && aimNow != Offset.Zero && !reported) {
                val cue = balls[0]
                drawLine(
                    Ink.Text.copy(alpha = 0.35f),
                    Offset(cue.x * w, cue.y * h),
                    aimNow,
                    strokeWidth = 2f,
                )
            }

            balls.forEach { b ->
                if (b.potted) return@forEach
                val c = Offset(b.x * w, b.y * h)
                val r = w * 0.026f
                drawCircle(b.color, r, c)
                drawCircle(if (b.num == 8) Ink.Mustard else Ink.Card.copy(alpha = 0.5f), r, c, style = Stroke(width = 1.5f))
                if (b.num != 0) {
                    drawCircle(Ink.Card, r * 0.32f, Offset(c.x - r * 0.30f, c.y - r * 0.32f))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        HintBar("Glissez de la blanche vers la cible. Les couleurs d'abord, la 8 en dernier.")
    }
}
