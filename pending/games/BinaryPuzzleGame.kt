package com.botillon.games.games

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.botillon.games.ui.*
import kotlin.random.Random

/** Puzzle binaire (Takuzu) : 0 et 1, jamais trois identiques, lignes et colonnes uniques. */
object BinaryPuzzleGame {
    val entry = GameEntry(
        id = "binaire", title = "Puzzle binaire", tagline = "Ni trois 0, ni trois 1",
        family = Family.LOGIC, accent = Ink.Blue, variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ -> BinaryPuzzle(variant, finish) }
}

private val BASE4 = listOf(0, 0, 1, 1, 0, 1, 1, 0, 1, 1, 0, 0, 1, 0, 0, 1)
private val BASE6 = listOf(
    0, 0, 1, 0, 1, 1,
    1, 0, 1, 1, 0, 0,
    1, 1, 0, 1, 0, 0,
    0, 1, 0, 0, 1, 1,
    1, 0, 1, 0, 0, 1,
    0, 1, 0, 1, 1, 0,
)

private fun binaryPuzzle(variant: Int): List<Int?> {
    val n = if (variant == 0) 4 else 6
    val base = if (n == 4) BASE4 else BASE6
    val rows = (0 until n).shuffled()
    val cols = (0 until n).shuffled()
    val flip = Random.nextBoolean()
    val solution = List(n * n) { index ->
        val value = base[rows[index / n] * n + cols[index % n]]
        if (flip) 1 - value else value
    }
    val hidden = (0 until n * n).shuffled().take(listOf(8, 16, 22)[variant]).toSet()
    return solution.mapIndexed { index, value -> if (index in hidden) null else value }
}

private fun binaryValid(cells: List<Int?>, n: Int): Boolean {
    if (cells.any { it == null }) return false
    val values = cells.map { it ?: 0 }
    fun lines(get: (Int, Int) -> Int): Boolean {
        val all = (0 until n).map { i -> (0 until n).map { j -> get(i, j) } }
        if (all.toSet().size != n) return false
        return all.all { line ->
            line.count { it == 0 } == n / 2 &&
                (0 until n - 2).none { k -> line[k] == line[k + 1] && line[k] == line[k + 2] }
        }
    }
    return lines { i, j -> values[i * n + j] } && lines { i, j -> values[j * n + i] }
}

@Composable
private fun BinaryPuzzle(variant: Int, finish: (Long, Boolean) -> Unit) {
    val n = if (variant == 0) 4 else 6
    var cells by remember { mutableStateOf(binaryPuzzle(variant)) }
    var given by remember { mutableStateOf(cells.map { it != null }) }
    var round by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(3) }
    var score by remember { mutableStateOf(0L) }
    var message by remember { mutableStateOf("Touchez une case : vide, 0, puis 1.") }
    var reported by remember { mutableStateOf(false) }

    fun report(won: Boolean) {
        if (reported) return
        reported = true
        finish(score, won)
    }

    fun validate() {
        if (reported) return
        if (cells.any { it == null }) { message = "Remplissez toutes les cases avant de valider."; return }
        if (binaryValid(cells, n)) {
            score += (50 + variant * 20).toLong()
            round += 1
            if (round == 3) report(true)
            else {
                cells = binaryPuzzle(variant)
                given = cells.map { it != null }
                message = "Grille valide ! Suivante."
            }
        } else {
            lives -= 1
            message = if (lives <= 0) "Plus de vies." else "Une règle est enfreinte : triples ou doublons."
            if (lives <= 0) report(false)
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            AppText("GRILLE ${round + 1} / 3", style = Typo.label, color = Ink.Muted)
            Pill("VIES $lives", color = Ink.Soft, background = Ink.Blue.copy(alpha = 0.14f))
            AppText("$score PTS", style = Typo.heading, color = Ink.Text)
        }
        Spacer(Modifier.height(6.dp))
        AppText(
            "Pas trois 0 ou 1 alignés · autant de 0 que de 1 · lignes et colonnes toutes différentes",
            style = Typo.small.copy(fontSize = 11.sp),
            color = Ink.Muted,
            align = TextAlign.Center,
            maxLines = 2,
            softWrap = true,
        )
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (r in 0 until n) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (c in 0 until n) {
                        val index = r * n + c
                        val value = cells[index]
                        val locked = given[index]
                        Box(
                            Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(9.dp))
                                .background(if (value == 1) Ink.Blue else if (value == 0) Ink.CardAlt else Ink.Card)
                                .border(
                                    if (locked) 2.dp else 1.dp,
                                    if (locked) Ink.Soft.copy(alpha = 0.55f) else Ink.Line,
                                    RoundedCornerShape(9.dp),
                                )
                                .clickable(enabled = !locked && !reported) {
                                    cells = cells.toMutableList().also {
                                        it[index] = when (value) { null -> 0; 0 -> 1; else -> null }
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            AppText(
                                value?.toString() ?: "·",
                                style = Typo.heading.copy(fontSize = 20.sp),
                                color = if (value == 1) Color.White else if (value == 0) Ink.Text else Ink.Muted,
                            )
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
            AppText(
                message,
                style = Typo.small,
                color = if (message.startsWith("Grille")) Ink.Teal else Ink.Soft,
                align = TextAlign.Center,
                maxLines = 2,
                softWrap = true,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(
                "EFFACER",
                { cells = cells.mapIndexed { index, value -> if (given[index]) value else null } },
                Modifier.weight(1f),
            )
            PrimaryButton("VALIDER", { validate() }, Modifier.weight(1.4f), color = Ink.Blue)
        }
        Spacer(Modifier.weight(1f))
        HintBar("Chaque ligne et chaque colonne contient autant de 0 que de 1, sans trois symboles identiques.")
    }
}
