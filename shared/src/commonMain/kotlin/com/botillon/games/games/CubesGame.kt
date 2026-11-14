package com.botillon.games.games

import androidx.compose.foundation.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.botillon.games.ui.*
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Compte les cubes : une pile isométrique, un compte total, y compris les cachés. */
object CubesGame {
    val entry = GameEntry(
        id = "cubes", title = "Compte les cubes", tagline = "Y compris ceux qui sont cachés",
        family = Family.LOGIC, accent = Ink.Blue, variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ -> Cubes(variant, finish) }
}

private fun heights(variant: Int): List<Int> {
    val maxHeight = if (variant == 0) 2 else 3
    repeat(600) {
        val grid = List(9) { Random.nextInt(0, maxHeight + 1) }
        val total = grid.sum()
        if (total in 7..19 && grid.count { it > 0 } >= 5 && (variant < 2 || grid.maxOrNull() == 3)) return grid
    }
    return listOf(1, 2, 1, 2, 3, 1, 1, 2, 1)
}

private fun DrawScope.cube(cx: Float, cy: Float, s: Float, v: Float) {
    fun poly(a: Offset, b: Offset, c: Offset, d: Offset) = Path().apply {
        moveTo(a.x, a.y)
        lineTo(b.x, b.y)
        lineTo(c.x, c.y)
        lineTo(d.x, d.y)
        close()
    }
    val top = poly(Offset(cx, cy - s * 0.5f), Offset(cx + s, cy), Offset(cx, cy + s * 0.5f), Offset(cx - s, cy))
    val left = poly(Offset(cx - s, cy), Offset(cx, cy + s * 0.5f), Offset(cx, cy + s * 0.5f + v), Offset(cx - s, cy + v))
    val right = poly(Offset(cx + s, cy), Offset(cx, cy + s * 0.5f), Offset(cx, cy + s * 0.5f + v), Offset(cx + s, cy + v))
    drawPath(top, Ink.Card)
    drawPath(left, Ink.Blue)
    drawPath(right, Ink.Blue.copy(alpha = 0.62f))
    val outline = Ink.Text.copy(alpha = 0.28f)
    for (path in listOf(top, left, right)) drawPath(path, outline, style = Stroke(width = 1.4f))
}

@Composable
private fun Cubes(variant: Int, finish: (Long, Boolean) -> Unit) {
    var round by remember { mutableIntStateOf(0) }
    var grid by remember { mutableStateOf(heights(variant)) }
    var input by remember { mutableStateOf("") }
    var score by remember { mutableStateOf(0L) }
    var correct by remember { mutableIntStateOf(0) }
    var feedback by remember { mutableStateOf<Boolean?>(null) }
    var reported by remember { mutableStateOf(false) }
    val total = grid.sum()

    fun report(won: Boolean) {
        if (reported) return
        reported = true
        finish(score, won)
    }

    fun validate() {
        if (reported || feedback != null || input.isEmpty()) return
        val value = input.toIntOrNull() ?: return
        if (value == total) { score += (20 + variant * 10).toLong(); correct++ }
        feedback = value == total
    }

    LaunchedEffect(feedback) {
        if (feedback == null) return@LaunchedEffect
        delay(1100)
        if (round == 5) report(correct >= 4)
        else {
            round += 1
            grid = heights(variant)
            input = ""
            feedback = null
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            AppText("CUBES ${round + 1} / 6", style = Typo.label, color = Ink.Muted)
            AppText("$score PTS", style = Typo.heading, color = Ink.Text)
        }
        Canvas(Modifier.fillMaxWidth().height(218.dp)) {
            val s = size.width / 8.6f
            val v = s * 0.72f
            val ox = size.width / 2f
            val oy = size.height * 0.45f
            for (sum in 0..4) {
                for (row in 0..2) for (col in 0..2) {
                    if (row + col != sum) continue
                    val count = grid[row * 3 + col]
                    val bx = ox + (col - row) * s * 0.85f
                    val by = oy + (row + col) * s * 0.34f
                    for (level in 0 until count) cube(bx, by - level * v, s, v)
                }
            }
        }
        Panel(Modifier.fillMaxWidth().height(46.dp), color = Ink.CardAlt) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AppText(
                    input.ifEmpty { "Nombre de cubes ?" },
                    style = Typo.display.copy(fontSize = 24.sp),
                    color = if (input.isEmpty()) Ink.Muted else Ink.Text,
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(34.dp), contentAlignment = Alignment.Center) {
            AppText(
                text = when (feedback) {
                    null -> "Additionnez les hauteurs de chaque pile."
                    true -> "Exact : $total cubes."
                    false -> "Raté : il y avait $total cubes."
                },
                style = Typo.small,
                color = when (feedback) { null -> Ink.Muted; true -> Ink.Teal; else -> Ink.Coral },
                align = TextAlign.Center,
            )
        }
        for (row in 0 until 3) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                for (col in 0 until 3) {
                    val digit = row * 3 + col + 1
                    Box(
                        Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(11.dp)).background(Ink.Card)
                            .border(1.dp, Ink.Line, RoundedCornerShape(11.dp))
                            .clickable(enabled = feedback == null && !reported) { input = (input + digit).take(2) },
                        contentAlignment = Alignment.Center,
                    ) { AppText("$digit", style = Typo.heading.copy(fontSize = 17.sp), color = Ink.Text) }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(11.dp)).background(Ink.CardAlt)
                    .border(1.dp, Ink.Line, RoundedCornerShape(11.dp))
                    .clickable { input = input.dropLast(1) },
                contentAlignment = Alignment.Center,
            ) { AppText("EFFACER", style = Typo.small, color = Ink.Soft) }
            Box(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(11.dp)).background(Ink.Card)
                    .border(1.dp, Ink.Line, RoundedCornerShape(11.dp))
                    .clickable(enabled = feedback == null && !reported) { input = (input + "0").take(2) },
                contentAlignment = Alignment.Center,
            ) { AppText("0", style = Typo.heading.copy(fontSize = 17.sp), color = Ink.Text) }
        }
        Spacer(Modifier.height(8.dp))
        PrimaryButton(
            "VALIDER",
            { validate() },
            Modifier.fillMaxWidth(),
            color = Ink.Blue,
            enabled = input.isNotEmpty() && feedback == null && !reported,
        )
        Spacer(Modifier.weight(1f))
        HintBar("Comptez les cubes de chaque pile, sans oublier ceux du dessous.")
    }
}
