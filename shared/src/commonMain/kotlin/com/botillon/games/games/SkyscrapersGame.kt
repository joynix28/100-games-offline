package com.botillon.games.games

import androidx.compose.foundation.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.botillon.games.ui.*

/** Gratte-ciel : un carré latin 4 × 4 avec des indices de visibilité sur les quatre côtés. */
object SkyscrapersGame {
    val entry = GameEntry(
        id = "gratte-ciel", title = "Gratte-ciel", tagline = "Voyez les tours depuis chaque côté",
        family = Family.LOGIC, accent = Ink.Violet, variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ -> Skyscrapers(variant, finish) }
}

private data class Sky(val given: List<Int?>, val top: List<Int>, val bottom: List<Int>, val left: List<Int>, val right: List<Int>)

private fun visible(line: List<Int>): Int {
    var best = 0
    var count = 0
    for (value in line) if (value > best) { best = value; count += 1 }
    return count
}

private fun newSky(variant: Int): Sky {
    val symbols = (1..4).shuffled()
    val base = List(16) { index -> symbols[(index / 4 + index % 4) % 4] }
    val rows = (0..3).shuffled()
    val cols = (0..3).shuffled()
    val solution = List(16) { index -> base[rows[index / 4] * 4 + cols[index % 4]] }
    val top = (0 until 4).map { c -> visible((0 until 4).map { r -> solution[r * 4 + c] }) }
    val bottom = (0 until 4).map { c -> visible((3 downTo 0).map { r -> solution[r * 4 + c] }) }
    val left = (0 until 4).map { r -> visible((0 until 4).map { c -> solution[r * 4 + c] }) }
    val right = (0 until 4).map { r -> visible((3 downTo 0).map { c -> solution[r * 4 + c] }) }
    val keep = (0..15).shuffled().take(listOf(9, 7, 5)[variant]).toSet()
    return Sky(solution.mapIndexed { index, value -> if (index in keep) value else null }, top, bottom, left, right)
}

@Composable
private fun SkyBox(
    modifier: Modifier,
    text: String,
    style: TextStyle = Typo.body,
    fill: Color = Ink.Card,
    color: Color = Ink.Text,
    line: Color = Ink.Line,
    width: Dp = 1.dp,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier.clip(RoundedCornerShape(8.dp)).background(fill).border(width, line, RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) { if (text.isNotEmpty()) AppText(text, style = style, color = color) }
}

@Composable
private fun Skyscrapers(variant: Int, finish: (Long, Boolean) -> Unit) {
    var puzzle by remember { mutableStateOf(newSky(variant)) }
    var cells by remember { mutableStateOf(puzzle.given) }
    var selected by remember { mutableIntStateOf(-1) }
    var round by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(3) }
    var score by remember { mutableStateOf(0L) }
    var message by remember { mutableStateOf("Un chiffre de 1 à 4 par ligne et par colonne.") }
    var reported by remember { mutableStateOf(false) }
    val clue = Typo.small.copy(fontSize = 12.sp)

    fun report(won: Boolean) {
        if (reported) return
        reported = true
        finish(score, won)
    }

    fun validate() {
        if (reported) return
        if (cells.any { it == null }) { message = "Remplissez toutes les cases."; return }
        val values = cells.map { it ?: 0 }
        val rowsOk = (0 until 4).all { r ->
            val line = (0 until 4).map { c -> values[r * 4 + c] }
            line.toSet().size == 4 && visible(line) == puzzle.left[r] && visible(line.reversed()) == puzzle.right[r]
        }
        val colsOk = (0 until 4).all { c ->
            val line = (0 until 4).map { r -> values[r * 4 + c] }
            line.toSet().size == 4 && visible(line) == puzzle.top[c] && visible(line.reversed()) == puzzle.bottom[c]
        }
        if (rowsOk && colsOk) {
            score += (60 + variant * 20).toLong()
            round += 1
            if (round == 3) report(true)
            else {
                puzzle = newSky(variant)
                cells = puzzle.given
                selected = -1
                message = "Bien vu ! Nouvelle ville."
            }
        } else {
            lives -= 1
            message = if (lives <= 0) "Plus de vies." else "Un indice de visibilité ne correspond pas."
            if (lives <= 0) report(false)
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            AppText("VILLE ${round + 1} / 3", style = Typo.label, color = Ink.Muted)
            Pill("VIES $lives", color = Ink.Soft, background = Ink.Violet.copy(alpha = 0.14f))
            AppText("$score PTS", style = Typo.heading, color = Ink.Text)
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth(0.88f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(Modifier.weight(1f).aspectRatio(1f))
                for (c in 0 until 4) SkyBox(Modifier.weight(1f).aspectRatio(1f), "${puzzle.top[c]}", clue, Ink.CardAlt, Ink.Soft)
                Box(Modifier.weight(1f).aspectRatio(1f))
            }
            for (r in 0 until 4) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    SkyBox(Modifier.weight(1f).aspectRatio(1f), "${puzzle.left[r]}", clue, Ink.CardAlt, Ink.Soft)
                    for (c in 0 until 4) {
                        val index = r * 4 + c
                        val locked = puzzle.given[index] != null
                        val on = selected == index
                        SkyBox(
                            Modifier.weight(1f).aspectRatio(1f),
                            cells[index]?.toString() ?: "",
                            Typo.heading.copy(fontSize = 18.sp),
                            if (locked) Ink.CardAlt else Ink.Card,
                            if (locked) Ink.Soft else Ink.Violet,
                            if (on) Ink.Violet else Ink.Line,
                            if (on) 2.dp else 1.dp,
                            onClick = { if (!locked && !reported) selected = if (on) -1 else index },
                        )
                    }
                    SkyBox(Modifier.weight(1f).aspectRatio(1f), "${puzzle.right[r]}", clue, Ink.CardAlt, Ink.Soft)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(Modifier.weight(1f).aspectRatio(1f))
                for (c in 0 until 4) SkyBox(Modifier.weight(1f).aspectRatio(1f), "${puzzle.bottom[c]}", clue, Ink.CardAlt, Ink.Soft)
                Box(Modifier.weight(1f).aspectRatio(1f))
            }
        }
        Box(Modifier.fillMaxWidth().height(38.dp), contentAlignment = Alignment.Center) {
            AppText(
                message,
                style = Typo.small,
                color = if (message.startsWith("Bien")) Ink.Teal else Ink.Soft,
                align = TextAlign.Center,
                maxLines = 2,
                softWrap = true,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (digit in 1..4) {
                Box(
                    Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(12.dp)).background(Ink.Card)
                        .border(1.dp, Ink.Line, RoundedCornerShape(12.dp))
                        .clickable(enabled = selected >= 0 && !reported) {
                            cells = cells.toMutableList().also { it[selected] = digit }
                        },
                    contentAlignment = Alignment.Center,
                ) { AppText("$digit", style = Typo.heading.copy(fontSize = 18.sp), color = Ink.Text) }
            }
        }
        Spacer(Modifier.height(7.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(
                "EFFACER",
                {
                    val index = selected
                    if (index >= 0 && puzzle.given[index] == null) cells = cells.toMutableList().also { it[index] = null }
                },
                Modifier.weight(1f),
            )
            PrimaryButton("VALIDER", { validate() }, Modifier.weight(1.4f), color = Ink.Violet)
        }
        Spacer(Modifier.weight(1f))
        HintBar("Depuis un côté, on voit une tour dès qu'elle dépasse toutes les précédentes.")
    }
}
