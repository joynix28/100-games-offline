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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Lancer de couteaux : la bûche tourne, chaque toucher plante une lame dans le
 * bas de la cible. Deux lames trop proches se percutent et la partie s'arrête.
 * Tous les cinq couteaux, la cible s'inverse ou accélère un peu.
 */
object KnifeHitGame {
    val entry = GameEntry(
        id = "couteaux",
        title = "Lancer de couteaux",
        tagline = "Planter sans toucher les autres",
        family = Family.ARCADE,
        accent = Ink.Coral,
        variants = listOf("Lent", "Normal", "Rapide"),
    ) { variant, _, finish, _ ->
        KnifeHit(variant, finish)
    }
}

private val FullTurn = (2.0 * PI).toFloat()
private val HalfTurn = PI.toFloat()

private val Steel = Color(0xFF9BA3AC)
private val Wood = Color(0xFFC28F5E)
private val WoodLight = Color(0xFFDBB183)
private val WoodDark = Color(0xFF8A5A32)

private fun angleDistance(a: Float, b: Float): Float {
    val diff = abs(a - b) % FullTurn
    return if (diff > HalfTurn) FullTurn - diff else diff
}

@Composable
private fun KnifeHit(variant: Int, finish: (Long, Boolean) -> Unit) {
    val index = variant.coerceIn(0, 2)
    val spinSpeed = listOf(1.4f, 2.1f, 2.9f)[index]
    val slots = listOf(8, 10, 12)[index]
    val minGap = FullTurn / slots * 0.62f

    val sound = LocalSound.current
    val haptics = LocalHaptics.current

    // Angles locaux des couteaux plantés : ils tournent avec la bûche.
    var planted by remember { mutableStateOf(emptyList<Float>()) }
    var score by remember { mutableIntStateOf(0) }
    var over by remember { mutableStateOf(false) }
    var reported by remember { mutableStateOf(false) }

    // États lus uniquement dans le dessin : aucun recomposition à chaque image.
    val angle = remember { mutableFloatStateOf(0f) }
    val direction = remember { mutableFloatStateOf(1f) }
    val pace = remember { mutableFloatStateOf(1f) }

    LaunchedEffect(over) {
        if (over) return@LaunchedEffect
        var previous = 0L
        while (!over) {
            withFrameNanos { now ->
                if (previous != 0L) {
                    val dt = min(0.032f, (now - previous) / 1_000_000_000f)
                    angle.floatValue =
                        (angle.floatValue + direction.floatValue * spinSpeed * pace.floatValue * dt) % FullTurn
                }
                previous = now
            }
        }
    }

    fun throwKnife() {
        if (over || reported) return
        // Le couteau frappe toujours le bas de la bûche.
        val local = (HalfTurn - angle.floatValue + FullTurn) % FullTurn
        val collision = planted.any { angleDistance(it, local) < minGap }
        if (collision) {
            over = true
            reported = true
            sound.play(Sound.LOSE)
            haptics.error()
            finish(score.toLong(), false)
            return
        }
        planted = planted + local
        score += 10
        sound.play(Sound.TAP)
        haptics.tap()
        if (planted.size % 5 == 0) {
            // Cinq couteaux de plus : demi-tour, puis légère accélération.
            if ((planted.size / 5) % 2 == 1) {
                direction.floatValue = -direction.floatValue
            } else {
                pace.floatValue = (pace.floatValue * 1.10f).coerceAtMost(1.7f)
            }
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText("Couteaux ${planted.size}", style = Typo.heading, color = Ink.Text)
            AppText("$slots emplacements", style = Typo.small, color = Ink.Muted)
            Pill(
                text = "$score pts",
                color = Ink.Coral,
                background = Ink.Coral.copy(alpha = 0.12f),
            )
        }

        Spacer(Modifier.height(8.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(over) {
                    if (over) return@pointerInput
                    detectTapGestures { throwKnife() }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val center = Offset(width / 2f, height * 0.40f)
                // La bûche et les manches doivent tenir dans la zone, même en fenêtre basse.
                val radius = min(width * 0.29f, height * 0.26f)
                val rotation = angle.floatValue
                val degrees = rotation * 180f / HalfTurn

                // Fond de l'aire de jeu.
                drawRoundRect(
                    color = Ink.Card,
                    size = Size(width, height),
                    cornerRadius = CornerRadius(24.dp.toPx()),
                )
                drawRoundRect(
                    color = Ink.Line,
                    size = Size(width, height),
                    cornerRadius = CornerRadius(24.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx()),
                )

                // Bûche : cercles de bois concentriques.
                drawCircle(WoodDark, radius = radius, center = center)
                drawCircle(Wood, radius = radius * 0.95f, center = center)
                drawCircle(WoodLight, radius = radius * 0.78f, center = center)
                drawCircle(Wood, radius = radius * 0.56f, center = center)
                drawCircle(WoodLight, radius = radius * 0.34f, center = center)

                // Rayons : ils rendent la rotation lisible.
                rotate(degrees = degrees, pivot = center) {
                    for (i in 0 until 12) {
                        val a = i * FullTurn / 12f
                        drawLine(
                            color = WoodDark.copy(alpha = 0.22f),
                            start = Offset(
                                center.x + cos(a) * radius * 0.14f,
                                center.y + sin(a) * radius * 0.14f,
                            ),
                            end = Offset(
                                center.x + cos(a) * radius * 0.92f,
                                center.y + sin(a) * radius * 0.92f,
                            ),
                            strokeWidth = radius * 0.018f,
                        )
                    }
                }

                // Anneaux.
                for (ring in 1..3) {
                    drawCircle(
                        color = WoodDark.copy(alpha = 0.32f),
                        radius = radius * (0.16f + ring * 0.21f),
                        center = center,
                        style = Stroke(width = radius * 0.012f),
                    )
                }

                // Axe central.
                drawCircle(WoodDark, radius = radius * 0.10f, center = center)
                drawCircle(Steel, radius = radius * 0.045f, center = center)

                // Lames déjà plantées.
                for (local in planted) {
                    drawKnifeAt(center, radius, local + rotation)
                }

                // Couteau prêt, aligné sous la cible.
                if (!over) {
                    val readyTip = min(height - radius * 0.85f, center.y + radius * 1.55f)
                    var y = center.y + radius * 1.10f
                    while (y < readyTip - radius * 0.12f) {
                        drawLine(
                            color = Ink.Line,
                            start = Offset(center.x, y),
                            end = Offset(center.x, min(y + radius * 0.07f, readyTip - radius * 0.12f)),
                            strokeWidth = 2.dp.toPx(),
                        )
                        y += radius * 0.16f
                    }
                    drawReadyKnife(center.x, readyTip, radius)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        HintBar(
            text = "Touchez pour lancer. Évitez vos propres couteaux.",
            accent = Ink.Coral,
        )
    }
}

/** Couteau planté : pointe vers le centre, manche vers l'extérieur. */
private fun DrawScope.drawKnifeAt(center: Offset, radius: Float, radians: Float) {
    rotate(degrees = radians * 180f / HalfTurn, pivot = center) {
        val half = radius * 0.05f
        val tipX = center.x + radius * 0.70f
        val bladeStart = center.x + radius * 0.86f
        val bladeEnd = center.x + radius * 1.18f
        val handleEnd = center.x + radius * 1.42f

        val tip = Path().apply {
            moveTo(tipX, center.y)
            lineTo(bladeStart, center.y - half)
            lineTo(bladeStart, center.y + half)
            close()
        }
        drawPath(tip, Steel)
        drawRect(
            color = Steel,
            topLeft = Offset(bladeStart, center.y - half),
            size = Size(bladeEnd - bladeStart, half * 2f),
        )
        drawRoundRect(
            color = Ink.Text,
            topLeft = Offset(bladeEnd, center.y - half * 1.35f),
            size = Size(handleEnd - bladeEnd, half * 2.7f),
            cornerRadius = CornerRadius(half),
        )
        drawCircle(
            color = Ink.Mustard,
            radius = half * 0.42f,
            center = Offset(bladeEnd + (handleEnd - bladeEnd) * 0.5f, center.y),
        )
    }
}

/** Couteau en attente, pointe vers le haut. */
private fun DrawScope.drawReadyKnife(x: Float, tipY: Float, radius: Float) {
    val half = radius * 0.05f
    val bladeStart = tipY + radius * 0.10f
    val bladeEnd = tipY + radius * 0.38f
    val handleEnd = tipY + radius * 0.60f

    val tip = Path().apply {
        moveTo(x, tipY)
        lineTo(x + half, bladeStart)
        lineTo(x - half, bladeStart)
        close()
    }
    drawPath(tip, Steel)
    drawRect(
        color = Steel,
        topLeft = Offset(x - half, bladeStart),
        size = Size(half * 2f, bladeEnd - bladeStart),
    )
    drawRoundRect(
        color = Ink.Text,
        topLeft = Offset(x - half * 1.35f, bladeEnd),
        size = Size(half * 2.7f, handleEnd - bladeEnd),
        cornerRadius = CornerRadius(half),
    )
    drawCircle(
        color = Ink.Mustard,
        radius = half * 0.42f,
        center = Offset(x, bladeEnd + (handleEnd - bladeEnd) * 0.5f),
    )
}
