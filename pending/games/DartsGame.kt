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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.botillon.games.ui.*
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/** Fléchettes : 501, trois fléchettes par tour, l'ordinateur répond. */
object DartsGame {
    val entry = GameEntry(
        id = "flechettes",
        title = "Fléchettes",
        tagline = "501 à zéro, trois fléchettes",
        family = Family.ARCADE,
        accent = Ink.Green,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        DartsScreen(variant, finish)
    }
}

private val DART_VALUES = intArrayOf(20, 1, 18, 4, 13, 6, 10, 15, 2, 17, 3, 19, 7, 16, 8, 11, 14, 9, 12, 5)

@Composable
private fun DartsScreen(variant: Int, finish: (Long, Boolean) -> Unit) {
    val sound = LocalSound.current
    var remainingP by remember { mutableIntStateOf(501) }
    var remainingA by remember { mutableIntStateOf(501) }
    var turn by remember { mutableIntStateOf(0) }
    var darts by remember { mutableIntStateOf(0) }
    var marks by remember { mutableStateOf(emptyList<Pair<Float, Float>>()) }
    var message by remember { mutableStateOf("À vous : touchez la cible.") }
    var reported by remember { mutableStateOf(false) }

    fun report(won: Boolean) {
        if (reported) return
        reported = true
        finish((501 - remainingP).toLong(), won)
    }

    fun aiHit(): Int = when (Random.nextInt(10)) {
        0 -> 50
        1 -> 0
        else -> Random.nextInt(20, listOf(60, 80, 100)[variant])
    }

    fun throwDart(o: Offset, w: Float, h: Float) {
        if (turn != 0 || reported) return
        val cx = w / 2f
        val cy = h * 0.49f
        val radius = min(w, h) * 0.40f
        val dx = o.x - cx
        val dy = o.y - cy
        val dist = sqrt(dx * dx + dy * dy)
        if (dist > radius) return
        val deg = ((atan2(dy, dx) * 180f / PI.toFloat()) + 360f) % 360f
        val index = (((deg + 9f) / 18f).toInt()) % 20
        val base = DART_VALUES[index]
        val r = dist / radius
        val hit = when {
            r < 0.06f -> 50
            r < 0.13f -> 25
            r < 0.72f -> base
            r < 0.92f -> base * 2
            else -> 0
        }
        remainingP = (remainingP - hit).coerceAtLeast(0)
        marks = marks + (o.x / w to o.y / h)
        message = if (hit > 0) "Vous marquez $hit." else "À côté…"
        sound.play(if (hit >= 25) Sound.WIN else Sound.TAP)
        if (remainingP <= 0) {
            report(true)
        } else {
            darts += 1
            if (darts >= 3) {
                darts = 0
                turn = 1
                message = "L'ordinateur lance…"
            }
        }
    }

    LaunchedEffect(turn) {
        if (turn != 1 || reported) return@LaunchedEffect
        delay(450)
        repeat(3) {
            if (reported) return@LaunchedEffect
            val hit = aiHit()
            remainingA = (remainingA - hit).coerceAtLeast(0)
            message = "L'ordinateur marque $hit."
            sound.play(Sound.TAP)
            delay(480)
        }
        if (remainingA <= 0) {
            report(false)
            sound.play(Sound.LOSE)
        } else {
            turn = 0
            darts = 0
            marks = emptyList()
            message = "À vous : touchez la cible."
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AppText("Vous $remainingP", style = Typo.heading, color = Ink.Coral)
            AppText("IA $remainingA", style = Typo.heading, color = Ink.Blue)
        }
        Spacer(Modifier.height(4.dp))
        AppText(message, style = Typo.small, color = Ink.Muted)
        Spacer(Modifier.height(6.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(Unit) {
                    detectTapGestures { o ->
                        throwDart(o, size.width.toFloat(), size.height.toFloat())
                    }
                },
        ) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h * 0.49f
            val radius = min(w, h) * 0.40f
            drawRoundRect(Ink.Card, cornerRadius = CornerRadius(24f))

            for (i in 0 until 20) {
                drawArc(
                    color = if (i % 2 == 0) Ink.CardAlt else Ink.Card,
                    startAngle = i * 18f - 9f,
                    sweepAngle = 18f,
                    useCenter = true,
                    topLeft = Offset(cx - radius, cy - radius),
                    size = Size(radius * 2f, radius * 2f),
                )
            }
            for (i in 0 until 20) {
                drawArc(
                    color = if (i % 2 == 0) Ink.Coral else Ink.Green,
                    startAngle = i * 18f - 9f,
                    sweepAngle = 18f,
                    useCenter = false,
                    topLeft = Offset(cx - radius * 0.82f, cy - radius * 0.82f),
                    size = Size(radius * 1.64f, radius * 1.64f),
                    style = Stroke(width = radius * 0.20f),
                )
            }
            for (i in 0 until 20) {
                val a = (i * 18f - 9f) * PI.toFloat() / 180f
                val ca = kotlin.math.cos(a)
                val sa = kotlin.math.sin(a)
                drawLine(
                    Ink.Text.copy(alpha = 0.35f),
                    Offset(cx + ca * radius * 0.13f, cy + sa * radius * 0.13f),
                    Offset(cx + ca * radius * 0.92f, cy + sa * radius * 0.92f),
                    strokeWidth = 1.5f,
                )
            }
            drawCircle(Ink.Text.copy(alpha = 0.35f), radius, Offset(cx, cy), style = Stroke(width = 2.5f))
            drawCircle(Ink.Green, radius * 0.13f, Offset(cx, cy))
            drawCircle(Ink.Coral, radius * 0.06f, Offset(cx, cy))

            marks.forEach { m ->
                val p = Offset(m.first * w, m.second * h)
                drawCircle(Ink.Text, radius * 0.018f, p)
                drawCircle(Ink.Card, radius * 0.010f, Offset(p.x - radius * 0.004f, p.y - radius * 0.004f))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pill(if (turn == 0) "À vous" else "IA", color = if (turn == 0) Ink.Coral else Ink.Blue)
            AppText("Fléchettes : ${3 - darts}/3", style = Typo.small, color = Ink.Soft)
        }
        Spacer(Modifier.height(6.dp))
        HintBar("Centre 50, petit anneau 25, double extérieur = valeur doublée. Trois fléchettes puis l'IA.")
    }
}
