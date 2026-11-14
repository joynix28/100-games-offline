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
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/** Tir à l'arc : visée et puissance, cinq flèches sur une cible concentrique. */
object ArcheryGame {
    val entry = GameEntry(
        id = "tir-arc",
        title = "Tir à l'arc",
        tagline = "Viser juste, doser la force",
        family = Family.ARCADE,
        accent = Ink.Green,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        ArcheryScreen(variant, finish)
    }
}

@Composable
private fun ArcheryScreen(variant: Int, finish: (Long, Boolean) -> Unit) {
    val targetR = listOf(0.125f, 0.105f, 0.085f)[variant]
    val sound = LocalSound.current
    var ax by remember { mutableFloatStateOf(0.12f) }
    var ay by remember { mutableFloatStateOf(0.55f) }
    var avx by remember { mutableFloatStateOf(0f) }
    var avy by remember { mutableFloatStateOf(0f) }
    var targetY by remember { mutableFloatStateOf(0.38f) }
    var arrows by remember { mutableIntStateOf(0) }
    var points by remember { mutableIntStateOf(0) }
    var phase by remember { mutableIntStateOf(0) }
    var wait by remember { mutableFloatStateOf(0f) }
    var aimNow by remember { mutableStateOf(Offset.Zero) }
    var message by remember { mutableStateOf("Tirez vers la cible.") }
    var reported by remember { mutableStateOf(false) }

    fun report() {
        if (reported) return
        reported = true
        finish(points.toLong(), points >= 28)
    }

    fun shoot(w: Float, h: Float) {
        if (phase != 0 || reported) return
        val dx = aimNow.x - w * 0.12f
        val dy = aimNow.y - h * 0.55f
        val len = sqrt(dx * dx + dy * dy)
        if (len < w * 0.07f) return
        val power = (len / (w * 0.55f)).coerceIn(0.2f, 1f)
        val speed = w * (1.55f + power * 1.55f)
        ax = 0.12f
        ay = 0.55f
        avx = dx / len * speed / w
        avy = dy / len * speed / h
        phase = 1
        sound.play(Sound.TAP)
    }

    LaunchedEffect(Unit) {
        var last = 0L
        while (!reported) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else min(0.034f, (now - last) / 1_000_000_000f)
                last = now
                if (dt <= 0f) return@withFrameNanos
                if (phase == 1) {
                    avy += 2.1f * dt
                    ax += avx * dt
                    ay += avy * dt
                    var landed = false
                    if (avx > 0f && ax >= 0.86f) {
                        ax = 0.86f
                        val dyR = abs(ay - targetY) * 1.6f
                        val hit = when {
                            dyR < targetR * 0.20f -> 10
                            dyR < targetR * 0.50f -> 7
                            dyR < targetR * 0.80f -> 4
                            dyR < targetR * 1.05f -> 1
                            else -> 0
                        }
                        points += hit
                        arrows += 1
                        message = if (hit > 0) "+$hit points" else "Trop court…"
                        sound.play(if (hit >= 7) Sound.WIN else Sound.TAP)
                        landed = true
                    }
                    if (!landed && (ax > 1.05f || ay > 1.05f || ax < -0.05f || ay < -0.05f)) {
                        arrows += 1
                        message = "Flèche perdue."
                        sound.play(Sound.LOSE)
                        landed = true
                    }
                    if (landed) {
                        phase = 2
                        wait = 0f
                    }
                } else if (phase == 2) {
                    wait += dt
                    if (wait >= 0.85f) {
                        if (arrows >= 5) report()
                        else {
                            targetY = 0.20f + Random.nextFloat() * 0.42f
                            ax = 0.12f
                            ay = 0.55f
                            avx = 0f
                            avy = 0f
                            phase = 0
                            aimNow = Offset.Zero
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
            AppText("Points $points", style = Typo.heading, color = Ink.Text)
            AppText("Flèche ${(arrows + if (phase == 0) 1 else 0).coerceAtMost(5)} / 5", style = Typo.heading, color = Ink.Soft)
        }
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { if (phase == 0) aimNow = it },
                        onDragEnd = {
                            if (phase == 0) shoot(size.width.toFloat(), size.height.toFloat())
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
            drawLine(Ink.Green.copy(alpha = 0.25f), Offset(0f, h * 0.90f), Offset(w, h * 0.90f), strokeWidth = 4f)

            // Cible.
            val cx = w * 0.86f
            val cy = h * targetY
            val r = w * targetR
            drawCircle(Ink.Mustard, r, Offset(cx, cy))
            drawCircle(Ink.Card, r * 0.78f, Offset(cx, cy))
            drawCircle(Ink.Coral, r * 0.52f, Offset(cx, cy))
            drawCircle(Ink.Card, r * 0.28f, Offset(cx, cy))
            drawCircle(Ink.Coral, r * 0.12f, Offset(cx, cy))
            drawCircle(Ink.Text.copy(alpha = 0.35f), r, Offset(cx, cy), style = Stroke(width = 2f))

            // Arc.
            val bowX = w * 0.12f
            val bowY = h * 0.55f
            val br = w * 0.055f
            drawArc(
                Ink.Text,
                startAngle = 110f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(bowX - br, bowY - br),
                size = Size(br * 2f, br * 2f),
                style = Stroke(width = w * 0.013f),
            )

            if (phase == 0 && aimNow != Offset.Zero) {
                drawLine(Ink.Teal.copy(alpha = 0.55f), Offset(bowX, bowY), aimNow, strokeWidth = 3f)
                drawCircle(Ink.Teal.copy(alpha = 0.22f), w * 0.035f, aimNow)
            }

            if (phase == 1) {
                val tailX = ax * w - avx * w * 0.035f
                val tailY = ay * h - avy * h * 0.035f
                drawLine(Ink.Text, Offset(tailX, tailY), Offset(ax * w, ay * h), strokeWidth = 4f)
                drawLine(Ink.Coral, Offset(ax * w, ay * h), Offset(ax * w + avx * w * 0.012f, ay * h + avy * h * 0.012f), strokeWidth = 4f)
            }
        }
        Spacer(Modifier.height(10.dp))
        AppText(message, style = Typo.body, color = Ink.Soft)
        Spacer(Modifier.height(6.dp))
        HintBar("Tirez depuis l'arc : la direction et la longueur du geste donnent l'angle et la force.")
    }
}
