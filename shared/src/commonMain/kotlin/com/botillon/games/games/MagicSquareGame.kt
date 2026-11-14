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
import kotlin.random.Random

/** Carré magique : compléter un 3 × 3 dont lignes, colonnes et diagonales font 15. */
object MagicSquareGame {
    val entry = GameEntry(
        id = "carre-magique", title = "Carré magique", tagline = "Toutes les lignes font 15",
        family = Family.LOGIC, accent = Ink.Green, variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ -> MagicSquare(variant, finish) }
}

private val LO_SHU = listOf(8, 1, 6, 3, 5, 7, 4, 9, 2)

private fun magicPuzzle(variant: Int): List<Int?> {
    var grid = LO_SHU
    repeat(Random.nextInt(0, 4)) {
        grid = listOf(grid[6], grid[3], grid[0], grid[7], grid[4], grid[1], grid[8], grid[5], grid[2])
    }
    if (Random.nextBoolean()) {
        grid = listOf(grid[2], grid[1], grid[0], grid[5], grid[4], grid[3], grid[8], grid[7], grid[6])
    }
    val hidden = (0..8).shuffled().take(listOf(3, 4, 5)[variant]).toSet()
    return grid.mapIndexed { index, value -> if (index in hidden) null else value }
}

@Composable
private fun MagicSquare(variant: Int, finish: (Long, Boolean) -> Unit) {
    var puzzle by remember { mutableStateOf(magicPuzzle(variant)) }
    var cells by remember { mutableStateOf(puzzle) }
    var selected by remember { mutableIntStateOf(-1) }
    var round by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(3) }
    var score by remember { mutableStateOf(0L) }
    var message by remember { mutableStateOf("Touchez une case vide puis un chiffre.") }
    var reported by remember { mutableStateOf(false) }

    fun report(won: Boolean) {
        if (reported) return
        reported = true
        finish(score, won)
    }

    fun validate() {
        if (reported) return
        if (cells.any { it == null }) { message = "Complétez les neuf cases."; return }
        val n = cells.map { it ?: 0 }
        val sums = listOf(
            n[0] + n[1] + n[2], n[3] + n[4] + n[5], n[6] + n[7] + n[8],
            n[0] + n[3] + n[6], n[1] + n[4] + n[7], n[2] + n[5] + n[8],
            n[0] + n[4] + n[8], n[2] + n[4] + n[6],
        )
        if (sums.all { it == 15 } && n.toSet() == (1..9).toSet()) {
            score += (30 + variant * 15).toLong()
            round += 1
            if (round == 4) report(true)
            else {
                puzzle = magicPuzzle(variant)
                cells = puzzle
                selected = -1
                message = "Carré magique ! Suivant."
            }
        } else {
            lives -= 1
            message = if (lives <= 0) "Plus de vies." else "Raté : chaque ligne et diagonale doit faire 15."
            if (lives <= 0) report(false)
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            AppText("CARRÉ ${round + 1} / 4", style = Typo.label, color = Ink.Muted)
            Pill("VIES $lives", color = Ink.Soft, background = Ink.Green.copy(alpha = 0.16f))
            AppText("$score PTS", style = Typo.heading, color = Ink.Text)
        }
        Spacer(Modifier.height(6.dp))
        AppText("CHAQUE LIGNE, COLONNE ET DIAGONALE = 15", style = Typo.label, color = Ink.Muted)
        Spacer(Modifier.height(10.dp))
        Panel(Modifier.fillMaxWidth(0.86f)) {
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (row in 0 until 3) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (col in 0 until 3) {
                            val index = row * 3 + col
                            val fixed = puzzle[index] != null
                            val on = selected == index
                            Box(
                                Modifier.weight(1f).height(76.dp).clip(RoundedCornerShape(13.dp))
                                    .background(if (fixed) Ink.CardAlt else if (on) Ink.Green.copy(alpha = 0.16f) else Ink.Card)
                                    .border(if (on) 2.dp else 1.dp, if (on) Ink.Green else Ink.Line, RoundedCornerShape(13.dp))
                                    .clickable(enabled = !fixed && !reported) { selected = if (on) -1 else index },
                                contentAlignment = Alignment.Center,
                            ) {
                                AppText(
                                    cells[index]?.toString() ?: "",
                                    style = Typo.display.copy(fontSize = 27.sp),
                                    color = if (fixed) Ink.Soft else Ink.Green,
                                )
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(42.dp), contentAlignment = Alignment.Center) {
            AppText(
                message,
                style = Typo.small,
                color = if (message.startsWith("Carré")) Ink.Teal else Ink.Soft,
                align = TextAlign.Center,
                maxLines = 2,
                softWrap = true,
            )
        }
        for (row in 0 until 3) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                for (col in 0 until 3) {
                    val digit = row * 3 + col + 1
                    Box(
                        Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(11.dp)).background(Ink.Card)
                            .border(1.dp, Ink.Line, RoundedCornerShape(11.dp))
                            .clickable(enabled = selected >= 0 && !reported) {
                                val index = selected
                                if (index >= 0 && puzzle[index] == null) {
                                    cells = cells.toMutableList().also { it[index] = digit }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) { AppText("$digit", style = Typo.heading.copy(fontSize = 17.sp), color = Ink.Text) }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(
                "EFFACER",
                {
                    val index = selected
                    if (index >= 0 && puzzle[index] == null) cells = cells.toMutableList().also { it[index] = null }
                },
                Modifier.weight(1f),
            )
            PrimaryButton("VALIDER", { validate() }, Modifier.weight(1.4f), color = Ink.Green)
        }
        Spacer(Modifier.weight(1f))
        HintBar("Les chiffres de 1 à 9 s'utilisent une seule fois. La case du centre est toujours 5.")
    }
}
