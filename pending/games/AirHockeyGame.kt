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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Air hockey : raquette au doigt, IA en face, premier à cinq buts. */
object AirHockeyGame {
    val entry = GameEntry(
        id = "air-hockey",
        title = "Air hockey",
        tagline = "Premier à cinq, raquette au doigt",
        family = Family.ARCADE,
        accent = Ink.Blue,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        AirHockeyScreen(variant, finish)
    }
}

@Composable
private fun AirHockeyScreen(variant: Int, finish: (Long, Boolean) -> Unit) {
    val aiSpeed = listOf(2.4f, 3.6f, 5.0f)[variant]
    val sound = LocalSound.current
    var px by remember { mutableFloatStateOf(0.50f) }
    var py by remember { mutableFloatStateOf(0.50f) }
    var pvx by remember { mutableFloatStateOf(0.32f) }
    var pvy by remember { mutableFloatStateOf(0.55f) }
    var playerX by remember { mutableFloatStateOf(0.50f) }
    var playerY by remember { mutableFloatStateOf(0.82f) }
    var aiX by remember { mutableFloatStateOf(0.50f) }
    var aiY by remember { mutableFloatStateOf(0.18f) }
    var scoreP by remember { mutableIntStateOf(0) }
    var scoreA by remember { mutableIntStateOf(0) }
    var reported by remember { mutableStateOf(false) }

    fun report() {
        if (reported) return
        reported = true
        finish((scoreP * 100).toLong(), scoreP >= 5)
    }

    fun resetPuck(towardPlayer: Boolean) {
        px = 0.50f
        py = 0.50f
        pvx = if (towardPlayer) 0.12f else -0.12f
        pvy = if (towardPlayer) 0.55f else -0.55f
    }

    LaunchedEffect(Unit) {
        var last = 0L
        while (!reported) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else min(0.034f, (now - last) / 1_000_000_000f)
                last = now
                if (dt <= 0f) return@withFrameNanos
                px += pvx * dt
                py += pvy * dt
                if (px < 0.045f) { px = 0.045f; pvx = abs(pvx) * 0.98f }
                if (px > 0.955f) { px = 0.955f; pvx = -abs(pvx) * 0.98f }
                if (py < 0.035f) {
                    if (px in 0.37f..0.63f) {
                        scoreA += 1
                        sound.play(Sound.LOSE)
                        if (scoreA >= 5) report() else resetPuck(true)
                    } else {
                        py = 0.035f
                        pvy = abs(pvy) * 0.98f
                    }
                }
                if (py > 0.965f && !reported) {
                    if (px in 0.37f..0.63f) {
                        scoreP += 1
                        sound.play(Sound.WIN)
                        if (scoreP >= 5) report() else resetPuck(false)
                    } else {
                        py = 0.965f
                        pvy = -abs(pvy) * 0.98f
                    }
                }

                // Raquette du joueur.
                var dx = px - playerX
                var dy = (py - playerY) * 1.35f
                var d = sqrt(dx * dx + dy * dy)
                if (d < 0.105f) {
                    if (d < 0.0001f) { dx = 0f; dy = -1f; d = 1f }
                    val ux = dx / d
                    val uy = dy / d
                    val speed = max(0.55f, sqrt(pvx * pvx + pvy * pvy) * 0.95f)
                    pvx = ux * speed
                    pvy = uy * speed / 1.35f
                }

                // Raquette de l'IA.
                val targetX = if (py < 0.50f) px else 0.50f
                val targetY = if (py < 0.50f) py.coerceIn(0.10f, 0.40f) else 0.17f
                val k = min(1f, aiSpeed * dt)
                aiX += (targetX - aiX) * k
                aiY += (targetY - aiY) * k
                aiX = aiX.coerceIn(0.10f, 0.90f)
                aiY = aiY.coerceIn(0.10f, 0.42f)

                dx = px - aiX
                dy = (py - aiY) * 1.35f
                d = sqrt(dx * dx + dy * dy)
                if (d < 0.105f) {
                    if (d < 0.0001f) { dx = 0f; dy = 1f; d = 1f }
                    val ux = dx / d
                    val uy = dy / d
                    val speed = max(0.55f, sqrt(pvx * pvx + pvy * pvy) * 0.95f)
                    pvx = ux * speed
                    pvy = uy * speed / 1.35f
                }

                pvx = pvx.coerceIn(-1.5f, 1.5f)
                pvy = pvy.coerceIn(-1.5f, 1.5f)
            }
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AppText("Vous $scoreP", style = Typo.heading, color = Ink.Coral)
            AppText("IA $scoreA", style = Typo.heading, color = Ink.Blue)
        }
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        playerX = (change.position.x / size.width).coerceIn(0.08f, 0.92f)
                        playerY = (change.position.y / size.height).coerceIn(0.55f, 0.94f)
                    }
                },
        ) {
            val w = size.width
            val h = size.height
            drawRoundRect(Ink.Card, cornerRadius = CornerRadius(24f))
            drawRoundRect(
                Ink.Blue.copy(alpha = 0.10f),
                topLeft = Offset(0f, 0f),
                size = Size(w, h * 0.47f),
                cornerRadius = CornerRadius(24f),
            )
            drawRect(Ink.Coral.copy(alpha = 0.10f), topLeft = Offset(0f, h * 0.53f), size = Size(w, h * 0.47f))

            // Buts.
            drawRect(Ink.Coral, topLeft = Offset(w * 0.37f, 0f), size = Size(w * 0.26f, h * 0.018f))
            drawRect(Ink.Blue, topLeft = Offset(w * 0.37f, h * 0.982f), size = Size(w * 0.26f, h * 0.018f))
            drawLine(Ink.Line, Offset(w * 0.5f, h * 0.06f), Offset(w * 0.5f, h * 0.94f), strokeWidth = 3f)
            drawCircle(Ink.Line, w * 0.14f, Offset(w * 0.5f, h * 0.5f), style = Stroke(width = 3f))
            drawCircle(Ink.Line.copy(alpha = 0.18f), w * 0.14f, Offset(w * 0.5f, h * 0.5f), style = Stroke(width = 30f))

            // Raquettes.
            drawCircle(Ink.Blue, w * 0.062f, Offset(aiX * w, aiY * h))
            drawCircle(Ink.Card, w * 0.025f, Offset(aiX * w, aiY * h))
            drawCircle(Ink.Coral, w * 0.062f, Offset(playerX * w, playerY * h))
            drawCircle(Ink.Card, w * 0.025f, Offset(playerX * w, playerY * h))

            // Palet.
            drawCircle(Ink.Text, w * 0.030f, Offset(px * w, py * h))
            drawCircle(Ink.Mustard, w * 0.011f, Offset(px * w, py * h))
        }
        Spacer(Modifier.height(10.dp))
        HintBar("Faites glisser votre raquette dans le bas. Le premier à 5 buts gagne.")
    }
}
