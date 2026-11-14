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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.botillon.games.ui.*
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/** Bouteille : figer la jauge au bon moment pour réussir un flip. */
object BottleFlipGame {
    val entry = GameEntry(
        id = "bouteille",
        title = "Bouteille",
        tagline = "Un flip, et elle se pose debout",
        family = Family.ARCADE,
        accent = Ink.Teal,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        BottleScreen(variant, finish)
    }
}

@Composable
private fun BottleScreen(variant: Int, finish: (Long, Boolean) -> Unit) {
    val zone = listOf(0.15f, 0.10f, 0.065f)[variant]
    val speed = listOf(0.75f, 1.15f, 1.60f)[variant]
    val sound = LocalSound.current
    var needle by remember { mutableFloatStateOf(0f) }
    var dir by remember { mutableFloatStateOf(1f) }
    var phase by remember { mutableIntStateOf(0) }
    var spin by remember { mutableFloatStateOf(0f) }
    var fly by remember { mutableFloatStateOf(0f) }
    var wait by remember { mutableFloatStateOf(0f) }
    var tries by remember { mutableIntStateOf(0) }
    var hits by remember { mutableIntStateOf(0) }
    var goodFlip by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Touchez pour figer la jauge.") }
    var reported by remember { mutableStateOf(false) }

    fun report() {
        if (reported) return
        reported = true
        finish((hits * 20).toLong(), hits >= 3)
    }

    fun launch() {
        if (phase != 0 || reported) return
        goodFlip = abs(needle - 0.5f) <= zone
        phase = 1
        fly = 0f
        spin = 0f
        sound.play(Sound.TAP)
    }

    LaunchedEffect(Unit) {
        var last = 0L
        while (!reported) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else min(0.034f, (now - last) / 1_000_000_000f)
                last = now
                if (dt <= 0f) return@withFrameNanos
                when (phase) {
                    0 -> {
                        needle += dir * speed * dt
                        if (needle > 1f) { needle = 1f; dir = -1f }
                        if (needle < 0f) { needle = 0f; dir = 1f }
                    }
                    1 -> {
                        fly += dt
                        spin = (if (goodFlip) 720f else 600f) * (fly / 0.75f).coerceIn(0f, 1f)
                        if (fly >= 0.75f) {
                            tries += 1
                            if (goodFlip) {
                                hits += 1
                                sound.play(Sound.WIN)
                                message = "Debout ! ${hits} réussite(s)."
                            } else {
                                sound.play(Sound.LOSE)
                                message = "Raté… elle bascule."
                            }
                            phase = 2
                            wait = 0f
                        }
                    }
                    else -> {
                        wait += dt
                        if (wait >= 0.85f) {
                            if (tries >= 5) report()
                            else { phase = 0; needle = 0f; dir = 1f; spin = 0f }
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
            AppText("Essai ${(tries + 1).coerceAtMost(5)} / 5", style = Typo.heading, color = Ink.Text)
            AppText("Réussites $hits", style = Typo.heading, color = Ink.Teal)
        }
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(Unit) { detectTapGestures { launch() } },
        ) {
            val w = size.width
            val h = size.height
            drawRoundRect(Ink.Card, cornerRadius = CornerRadius(24f))
            drawRect(Ink.CardAlt, topLeft = Offset(0f, h * 0.78f), size = Size(w, h * 0.22f))
            drawLine(Ink.Line, Offset(0f, h * 0.78f), Offset(w, h * 0.78f), strokeWidth = 3f)

            val gy = h * 0.14f
            drawRoundRect(
                Ink.CardAlt,
                topLeft = Offset(w * 0.12f, gy - h * 0.018f),
                size = Size(w * 0.76f, h * 0.036f),
                cornerRadius = CornerRadius(20f),
            )
            drawRoundRect(
                Ink.Teal.copy(alpha = 0.28f),
                topLeft = Offset(w * (0.12f + (0.5f - zone) * 0.76f), gy - h * 0.018f),
                size = Size(w * zone * 2f * 0.76f, h * 0.036f),
                cornerRadius = CornerRadius(20f),
            )
            drawRect(
                Ink.Text,
                topLeft = Offset(w * (0.12f + needle * 0.76f) - 2.5f, gy - h * 0.034f),
                size = Size(5f, h * 0.068f),
            )

            val lift = if (phase == 1) sin(PI * (fly / 0.75f).coerceIn(0f, 1f)).toFloat() * h * 0.20f else 0f
            translate(0f, -lift) {
                rotate(if (phase >= 1) spin else 0f, Offset(w * 0.5f, h * 0.60f)) {
                    val bodyW = w * 0.105f
                    drawRoundRect(
                        Ink.Green,
                        topLeft = Offset(w * 0.5f - bodyW / 2f, h * 0.47f),
                        size = Size(bodyW, h * 0.31f),
                        cornerRadius = CornerRadius(bodyW * 0.34f),
                    )
                    drawRect(
                        Ink.Green.copy(alpha = 0.72f),
                        topLeft = Offset(w * 0.5f - w * 0.019f, h * 0.405f),
                        size = Size(w * 0.038f, h * 0.075f),
                    )
                    drawRoundRect(
                        Ink.Mustard,
                        topLeft = Offset(w * 0.5f - w * 0.024f, h * 0.365f),
                        size = Size(w * 0.048f, h * 0.05f),
                        cornerRadius = CornerRadius(w * 0.012f),
                    )
                    drawRect(
                        Ink.Card.copy(alpha = 0.65f),
                        topLeft = Offset(w * 0.5f - bodyW * 0.28f, h * 0.52f),
                        size = Size(bodyW * 0.13f, h * 0.18f),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        AppText(message, style = Typo.body, color = Ink.Soft, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        HintBar("La jauge va et vient : touchez dans la zone claire pour un flip parfait.")
    }
}
