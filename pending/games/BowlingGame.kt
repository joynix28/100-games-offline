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
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/** Bowling : cinq cadres, deux lancers, dix quilles à renverser. */
object BowlingGame {
    val entry = GameEntry(
        id = "bowling",
        title = "Bowling",
        tagline = "Cinq cadres, dix quilles",
        family = Family.ARCADE,
        accent = Ink.Violet,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        BowlingScreen(variant, finish)
    }
}

private val BOWL_PINS = listOf(
    0.50f to 0.265f,
    0.44f to 0.210f, 0.56f to 0.210f,
    0.38f to 0.155f, 0.50f to 0.155f, 0.62f to 0.155f,
    0.32f to 0.100f, 0.44f to 0.100f, 0.56f to 0.100f, 0.68f to 0.100f,
)

@Composable
private fun BowlingScreen(variant: Int, finish: (Long, Boolean) -> Unit) {
    val curve = listOf(0f, 0.18f, -0.26f)[variant]
    val sound = LocalSound.current
    val haptics = LocalHaptics.current
    var pins by remember { mutableStateOf(List(10) { true }) }
    var bx by remember { mutableFloatStateOf(0.50f) }
    var by by remember { mutableFloatStateOf(0.86f) }
    var bvx by remember { mutableFloatStateOf(0f) }
    var bvy by remember { mutableFloatStateOf(0f) }
    var phase by remember { mutableIntStateOf(0) }
    var frame by remember { mutableIntStateOf(0) }
    var ballNo by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var down by remember { mutableIntStateOf(0) }
    var wait by remember { mutableFloatStateOf(0f) }
    var aimStart by remember { mutableStateOf(Offset.Zero) }
    var aimNow by remember { mutableStateOf(Offset.Zero) }
    var message by remember { mutableStateOf("Glissez vers le haut pour lancer.") }
    var reported by remember { mutableStateOf(false) }

    fun report() {
        if (reported) return
        reported = true
        finish(total.toLong(), total >= 30)
    }

    fun resetBall() {
        bx = 0.50f
        by = 0.86f
        bvx = 0f
        bvy = 0f
        down = 0
        phase = 0
        aimNow = Offset.Zero
    }

    LaunchedEffect(Unit) {
        var last = 0L
        while (!reported) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else min(0.034f, (now - last) / 1_000_000_000f)
                last = now
                if (dt <= 0f) return@withFrameNanos
                if (phase == 1) {
                    bvx += curve * dt * 0.55f
                    bx += bvx * dt
                    by += bvy * dt
                    if (bx < 0.13f) { bx = 0.13f; bvx = kotlin.math.abs(bvx) * 0.7f }
                    if (bx > 0.87f) { bx = 0.87f; bvx = -kotlin.math.abs(bvx) * 0.7f }
                    if (by < 0.31f) {
                        var knocked = emptySet<Int>()
                        pins.forEachIndexed { i, standing ->
                            if (!standing) return@forEachIndexed
                            val p = BOWL_PINS[i]
                            val dx = bx - p.first
                            val dy = (by - p.second) * 1.7f
                            if (sqrt(dx * dx + dy * dy) < 0.085f) knocked = knocked + i
                        }
                        var grew = knocked.isNotEmpty()
                        while (grew) {
                            grew = false
                            pins.forEachIndexed { i, standing ->
                                if (!standing || i in knocked) return@forEachIndexed
                                val p = BOWL_PINS[i]
                                if (knocked.any { j ->
                                        val q = BOWL_PINS[j]
                                        val dx = p.first - q.first
                                        val dy = (p.second - q.second) * 1.7f
                                        sqrt(dx * dx + dy * dy) < 0.095f
                                    }
                                ) {
                                    knocked = knocked + i
                                    grew = true
                                }
                            }
                        }
                        if (knocked.isNotEmpty()) {
                            pins = pins.toMutableList().also { list -> knocked.forEach { list[it] = false } }
                            down += knocked.size
                            bvx += (Random.nextFloat() - 0.5f) * 0.10f
                            bvy *= 0.965f
                            sound.play(Sound.MATCH)
                            haptics.tap()
                        }
                    }
                    if (by < 0.01f) {
                        phase = 2
                        wait = 0f
                        message = if (down == 10) "Strike !" else "$down quille(s) tombée(s)."
                        sound.play(if (down >= 8) Sound.WIN else Sound.TAP)
                    }
                } else if (phase == 2) {
                    wait += dt
                    if (wait >= 1.15f) {
                        total += down
                        val strike = pins.none { it }
                        when {
                            frame >= 4 -> report()
                            ballNo == 0 && strike -> { frame += 1; ballNo = 0; pins = List(10) { true }; resetBall() }
                            ballNo == 0 -> { ballNo = 1; resetBall() }
                            else -> { frame += 1; ballNo = 0; pins = List(10) { true }; resetBall() }
                        }
                    }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AppText("Total $total", style = Typo.heading, color = Ink.Text)
            AppText("Cadre ${(frame + 1).coerceAtMost(5)}/5 · lancer ${ballNo + 1}", style = Typo.heading, color = Ink.Soft)
        }
        Spacer(Modifier.height(6.dp))
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
                                val dx = (aimNow.x - aimStart.x) / w
                                val up = (aimStart.y - aimNow.y) / h
                                if (up > 0.05f) {
                                    bvx = dx * 1.8f
                                    bvy = -(0.55f + up * 1.15f)
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
            drawRect(Ink.CardAlt, topLeft = Offset(w * 0.10f, 0f), size = Size(w * 0.80f, h))
            drawRect(Ink.Muted.copy(alpha = 0.18f), topLeft = Offset(0f, 0f), size = Size(w * 0.06f, h))
            drawRect(Ink.Muted.copy(alpha = 0.18f), topLeft = Offset(w * 0.94f, 0f), size = Size(w * 0.06f, h))

            if (phase == 0) {
                val dx = (aimNow.x - aimStart.x) / w
                drawLine(
                    Ink.Teal.copy(alpha = 0.5f),
                    Offset(bx * w, by * h),
                    Offset((bx + dx) * w, h * 0.20f),
                    strokeWidth = 3f,
                )
            }

            BOWL_PINS.forEachIndexed { i, p ->
                if (!pins[i]) return@forEachIndexed
                val c = Offset(p.first * w, p.second * h)
                drawCircle(Ink.Card, w * 0.030f, c)
                drawCircle(Ink.Line, w * 0.030f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
                drawRect(
                    Ink.Coral,
                    topLeft = Offset(c.x - w * 0.018f, c.y - w * 0.004f),
                    size = Size(w * 0.036f, w * 0.008f),
                )
            }

            val ball = Offset(bx * w, by * h)
            drawCircle(Ink.Text, w * 0.035f, ball)
            drawCircle(Ink.Violet, w * 0.014f, Offset(ball.x - w * 0.012f, ball.y - w * 0.012f))
            drawCircle(Ink.Violet, w * 0.009f, Offset(ball.x + w * 0.010f, ball.y - w * 0.014f))
            drawCircle(Ink.Violet, w * 0.008f, Offset(ball.x, ball.y + w * 0.012f))
        }
        Spacer(Modifier.height(10.dp))
        HintBar("Glissez vers le haut : l'angle donne la direction, la longueur la vitesse.")
    }
}
