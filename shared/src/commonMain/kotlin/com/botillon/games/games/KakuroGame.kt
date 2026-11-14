package com.botillon.games.games

import androidx.compose.foundation.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.botillon.games.ui.*

/** Kakuro 4 × 4 : remplir la grille pour respecter les sommes, sans doublon par ligne. */
object KakuroGame {
    val entry = GameEntry(
        id = "kakuro", title = "Kakuro", tagline = "Des sommes, pas de doublons",
        family = Family.LOGIC, accent = Ink.Mustard, variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ -> Kakuro(variant, finish) }
}

private data class Kakuro(val given: List<Int?>, val rows: List<Int>, val cols: List<Int>)

private fun latin4(): List<Int> {
    repeat(3000) {
        val grid = IntArray(16)
        var ok = true
        for (r in 0 until 4) {
            val row = (1..9).shuffled().take(4)
            for (c in 0 until 4) {
                if ((0 until r).any { grid[it * 4 + c] == row[c] }) { ok = false; break }
            }
            if (!ok) break
            for (c in 0 until 4) grid[r * 4 + c] = row[c]
        }
        if (ok) return grid.toList()
    }
    return listOf(1, 2, 3, 4, 2, 3, 4, 1, 3, 4, 1, 2, 4, 1, 2, 3)
}

private fun newKakuro(variant: Int): Kakuro {
    val solution = latin4()
    val keep = (0..15).shuffled().take(listOf(8, 6, 4)[variant]).toSet()
    return Kakuro(
        solution.mapIndexed { index, value -> if (index in keep) value else null },
        (0 until 4).map { r -> (0 until 4).sumOf { c -> solution[r * 4 + c] } },
        (0 until 4).map { c -> (0 until 4).sumOf { r -> solution[r * 4 + c] } },
    )
}

@Composable
private fun Kakuro(variant: Int, finish: (Long, Boolean) -> Unit) {
    var puzzle by remember { mutableStateOf(newKakuro(variant)) }
    var cells by remember { mutableStateOf(puzzle.given) }
    var selected by remember { mutableIntStateOf(-1) }
    var round by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(3) }
    var score by remember { mutableStateOf(0L) }
    var message by remember { mutableStateOf("Cliquez une case, puis un chiffre de 1 à 9.") }
    var reported by remember { mutableStateOf(false) }
    val cellSize = 58.dp

    fun report(won: Boolean) {
        if (reported) return
        reported = true
        finish(score, won)
    }

    fun put(digit: Int) {
        val index = selected
        if (index >= 0 && puzzle.given[index] == null) cells = cells.toMutableList().also { it[index] = digit }
    }

    fun validate() {
        if (reported) return
        if (cells.any { it == null }) { message = "Toutes les cases doivent être remplies."; return }
        val values = cells.map { it ?: 0 }
        val rowsOk = (0 until 4).all { r ->
            val line = values.subList(r * 4, r * 4 + 4)
            line.sum() == puzzle.rows[r] && line.toSet().size == 4
        }
        val colsOk = (0 until 4).all { c ->
            val line = (0 until 4).map { r -> values[r * 4 + c] }
            line.sum() == puzzle.cols[c] && line.toSet().size == 4
        }
        if (rowsOk && colsOk) {
            score += (50 + variant * 20).toLong()
            round += 1
            if (round == 3) report(true)
            else {
                puzzle = newKakuro(variant)
                cells = puzzle.given
                selected = -1
                message = "Grille résolue ! Suivante."
            }
        } else {
            lives -= 1
            message = if (lives <= 0) "Plus de vies." else "Une somme ou un doublon ne colle pas."
            if (lives <= 0) report(false)
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            AppText("GRILLE ${round + 1} / 3", style = Typo.label, color = Ink.Muted)
            Pill("VIES $lives", color = Ink.Soft, background = Ink.Mustard.copy(alpha = 0.20f))
            AppText("$score PTS", style = Typo.heading, color = Ink.Text)
        }
        Spacer(Modifier.height(8.dp))
        Row {
            Box(Modifier.size(cellSize))
            puzzle.cols.forEach { sum ->
                Box(
                    Modifier.size(cellSize).clip(RoundedCornerShape(5.dp)).background(Ink.CardAlt)
                        .border(1.dp, Ink.Line, RoundedCornerShape(5.dp)),
                    contentAlignment = Alignment.Center,
                ) { AppText("↓ $sum", style = Typo.small.copy(fontSize = 11.sp), color = Ink.Soft) }
            }
        }
        for (r in 0 until 4) {
            Row {
                Box(
                    Modifier.size(cellSize).clip(RoundedCornerShape(5.dp)).background(Ink.CardAlt)
                        .border(1.dp, Ink.Line, RoundedCornerShape(5.dp)),
                    contentAlignment = Alignment.Center,
                ) { AppText("→ ${puzzle.rows[r]}", style = Typo.small.copy(fontSize = 11.sp), color = Ink.Soft) }
                for (c in 0 until 4) {
                    val index = r * 4 + c
                    val given = puzzle.given[index] != null
                    val on = selected == index
                    Box(
                        Modifier.size(cellSize).clip(RoundedCornerShape(5.dp))
                            .background(if (given) Ink.CardAlt else Ink.Card)
                            .border(
                                if (on) 2.dp else 1.dp,
                                if (on) Ink.Mustard else Ink.Line,
                                RoundedCornerShape(5.dp),
                            )
                            .clickable(enabled = !given && !reported) { selected = if (on) -1 else index },
                        contentAlignment = Alignment.Center,
                    ) {
                        AppText(
                            cells[index]?.toString() ?: "",
                            style = Typo.heading.copy(fontSize = 19.sp),
                            color = if (given) Ink.Soft else Ink.Mustard,
                        )
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(36.dp), contentAlignment = Alignment.Center) {
            AppText(
                message,
                style = Typo.small,
                color = if (message.startsWith("Grille")) Ink.Teal else Ink.Soft,
                align = TextAlign.Center,
                maxLines = 2,
                softWrap = true,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (digit in 1..5) {
                Box(
                    Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(11.dp)).background(Ink.Card)
                        .border(1.dp, Ink.Line, RoundedCornerShape(11.dp))
                        .clickable(enabled = selected >= 0 && !reported) { put(digit) },
                    contentAlignment = Alignment.Center,
                ) { AppText("$digit", style = Typo.heading.copy(fontSize = 17.sp), color = Ink.Text) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (digit in 6..9) {
                Box(
                    Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(11.dp)).background(Ink.Card)
                        .border(1.dp, Ink.Line, RoundedCornerShape(11.dp))
                        .clickable(enabled = selected >= 0 && !reported) { put(digit) },
                    contentAlignment = Alignment.Center,
                ) { AppText("$digit", style = Typo.heading.copy(fontSize = 17.sp), color = Ink.Text) }
            }
            Box(
                Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(11.dp)).background(Ink.CardAlt)
                    .border(1.dp, Ink.Line, RoundedCornerShape(11.dp))
                    .clickable {
                        val index = selected
                        if (index >= 0 && puzzle.given[index] == null) cells = cells.toMutableList().also { it[index] = null }
                    },
                contentAlignment = Alignment.Center,
            ) { AppText("←", style = Typo.heading, color = Ink.Soft) }
        }
        Spacer(Modifier.height(8.dp))
        PrimaryButton("VALIDER", { validate() }, Modifier.fillMaxWidth(), color = Ink.Mustard, textColor = Ink.Text)
        Spacer(Modifier.weight(1f))
        HintBar("Les chiffres d'une ligne ou d'une colonne ne se répètent jamais. Les sommes sont en haut et à gauche.")
    }
}
