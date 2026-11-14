package com.botillon.games.games

import androidx.compose.foundation.*
import androidx.compose.foundation.shape.RoundedCornerShape
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

/** Jeu du 24 : combiner quatre nombres avec +, −, ×, ÷ pour arriver à 24. */
object TwentyFourGame {
    val entry = GameEntry(
        id = "jeu24", title = "Jeu du 24", tagline = "Quatre nombres, quatre opérations",
        family = Family.LOGIC, accent = Ink.Coral, variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ -> TwentyFour(variant, finish) }
}

private fun results(a: Int, b: Int): List<Int> = buildList {
    for (value in listOf(a + b, a * b, a - b, b - a)) if (value > 0) add(value)
    if (b != 0 && a % b == 0 && a / b > 0) add(a / b)
    if (a != 0 && b % a == 0 && b / a > 0) add(b / a)
}

private fun solvable(values: List<Int>): Boolean {
    if (values.size == 1) return values[0] == 24
    for (i in values.indices) for (j in i + 1 until values.size) {
        val rest = values.filterIndexed { k, _ -> k != i && k != j }
        for (value in results(values[i], values[j])) if (solvable(rest + value)) return true
    }
    return false
}

private fun puzzle(variant: Int): List<Int> {
    val max = listOf(6, 9, 12)[variant]
    repeat(800) {
        val hand = List(4) { Random.nextInt(1, max + 1) }
        if (solvable(hand)) return hand
    }
    return listOf(6, 6, 6, 6)
}

@Composable
private fun TwentyFour(variant: Int, finish: (Long, Boolean) -> Unit) {
    var values by remember { mutableStateOf(puzzle(variant)) }
    var history by remember { mutableStateOf(emptyList<List<Int>>()) }
    var selected by remember { mutableStateOf(emptyList<Int>()) }
    var message by remember { mutableStateOf("Touchez deux nombres, puis une opération.") }
    var wrong by remember { mutableStateOf(0) }
    var reported by remember { mutableStateOf(false) }

    fun report(won: Boolean) {
        if (reported) return
        reported = true
        finish(if (won) (120 + variant * 60 - wrong * 10).coerceAtLeast(30).toLong() else 0L, won)
    }

    fun useOp(op: Char) {
        if (reported) return
        if (selected.size != 2) { message = "Sélectionnez deux nombres."; return }
        val a = values[selected[0]]
        val b = values[selected[1]]
        val value = when (op) {
            '+' -> a + b
            '−' -> a - b
            '×' -> a * b
            else -> if (b != 0 && a % b == 0) a / b else null
        }
        if (value == null || value <= 0) { wrong++; message = "Ce coup ne donne pas un entier positif."; return }
        history = history + listOf(values)
        values = values.filterIndexed { index, _ -> index !in selected } + value
        selected = emptyList()
        message = if (values.size == 1) "Un nombre reste : validez." else "Encore ${values.size - 1} nombre(s)."
    }

    fun undo() {
        if (history.isEmpty() || reported) { message = "Rien à annuler."; return }
        values = history.last()
        history = history.dropLast(1)
        selected = emptyList()
        message = "Coup annulé."
    }

    fun validate() {
        if (reported) return
        if (values.size > 1) { message = "Il reste ${values.size} nombres."; return }
        if (values[0] == 24) report(true) else message = "Pas 24 : annulez un coup ou passez."
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        AppText("OBJECTIF : 24", style = Typo.label, color = Ink.Muted)
        Spacer(Modifier.height(8.dp))
        Panel(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                values.forEachIndexed { index, value ->
                    val on = index in selected
                    Box(
                        Modifier.weight(1f).height(82.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (on) Ink.Coral else Ink.CardAlt)
                            .border(2.dp, if (on) Ink.Coral else Ink.Line, RoundedCornerShape(16.dp))
                            .clickable {
                                selected = when {
                                    on -> selected - index
                                    selected.size == 2 -> listOf(index)
                                    else -> (selected + index).sorted()
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        AppText("$value", style = Typo.display.copy(fontSize = 30.sp), color = if (on) Color.White else Ink.Text)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
            AppText(
                message,
                style = Typo.small,
                color = if (values.size == 1) Ink.Teal else Ink.Soft,
                align = TextAlign.Center,
                maxLines = 2,
                softWrap = true,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            for (op in "+−×÷") {
                Box(
                    Modifier.weight(1f).height(58.dp).clip(RoundedCornerShape(15.dp))
                        .background(Ink.Card).border(1.dp, Ink.Line, RoundedCornerShape(15.dp))
                        .clickable { useOp(op) },
                    contentAlignment = Alignment.Center,
                ) { AppText("$op", style = Typo.display.copy(fontSize = 25.sp), color = Ink.Blue) }
            }
        }
        Spacer(Modifier.height(10.dp))
        PrimaryButton("VALIDER 24", { validate() }, Modifier.fillMaxWidth(), color = Ink.Coral)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            SecondaryButton("ANNULER", { undo() }, Modifier.weight(1f))
            SecondaryButton("PASSER", { report(false) }, Modifier.weight(1f))
        }
        Spacer(Modifier.weight(1f))
        HintBar("Deux nombres puis une opération : le résultat remplace les deux. Atteignez 24.")
    }
}
