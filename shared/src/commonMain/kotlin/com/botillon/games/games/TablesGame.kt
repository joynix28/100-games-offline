package com.botillon.games.games

import androidx.compose.foundation.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.botillon.games.ui.*
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Tables de multiplication : soixante secondes, quatre réponses possibles. */
object TablesGame {
    val entry = GameEntry(
        id = "tables", title = "Tables de multiplication", tagline = "Soixante secondes, quatre réponses",
        family = Family.WORDS, accent = Ink.Teal, variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ -> Tables(variant, finish) }
}

private data class TableQuestion(val a: Int, val b: Int, val choices: List<Int>) {
    val answer: Int get() = a * b
}

private fun tableRange(variant: Int): IntRange = when (variant) {
    0 -> 2..5
    1 -> 2..9
    else -> 6..12
}

private fun newTable(variant: Int): TableQuestion {
    val range = tableRange(variant)
    val a = Random.nextInt(range.first, range.last + 1)
    val b = if (variant == 2) Random.nextInt(4, 13) else Random.nextInt(2, 11)
    val answer = a * b
    val choices = mutableSetOf(answer)
    while (choices.size < 4) {
        val candidate = answer + Random.nextInt(-10, 11)
        if (candidate > 0) choices += candidate
    }
    return TableQuestion(a, b, choices.toList().shuffled())
}

@Composable
private fun Tables(variant: Int, finish: (Long, Boolean) -> Unit) {
    var remaining by remember { mutableIntStateOf(60) }
    var score by remember { mutableStateOf(0L) }
    var streak by remember { mutableIntStateOf(0) }
    var question by remember { mutableStateOf(newTable(variant)) }
    var chosen by remember { mutableStateOf<Int?>(null) }
    var reported by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1000)
            remaining -= 1
        }
        if (!reported) { reported = true; finish(score, score >= 100) }
    }
    LaunchedEffect(chosen) {
        if (chosen == null) return@LaunchedEffect
        delay(330)
        if (!reported) { question = newTable(variant); chosen = null }
    }

    fun pick(value: Int) {
        if (reported || chosen != null) return
        chosen = value
        if (value == question.answer) { score += (10 + streak * 2).toLong(); streak++ } else streak = 0
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                AppText("SCORE", style = Typo.label, color = Ink.Muted)
                AppText("$score", style = Typo.heading, color = Ink.Text)
            }
            Pill("SÉRIE × ${streak + 1}", color = Ink.Soft, background = Ink.Teal.copy(alpha = 0.14f))
            Column(horizontalAlignment = Alignment.End) {
                AppText("TEMPS", style = Typo.label, color = Ink.Muted)
                AppText("$remaining s", style = Typo.heading, color = if (remaining <= 10) Ink.Coral else Ink.Text)
            }
        }
        Spacer(Modifier.height(12.dp))
        Panel(Modifier.fillMaxWidth().height(150.dp)) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                AppText("TABLES ${tableRange(variant).first} À ${tableRange(variant).last}", style = Typo.label, color = Ink.Muted)
                Spacer(Modifier.height(8.dp))
                AppText("${question.a} × ${question.b}", style = Typo.display.copy(fontSize = 42.sp), color = Ink.Text)
            }
        }
        Box(Modifier.fillMaxWidth().height(30.dp), contentAlignment = Alignment.Center) {
            AppText(
                text = when {
                    chosen == null -> "Choisissez le produit exact."
                    chosen == question.answer -> "Bien joué, +${10 + (streak - 1) * 2} points"
                    else -> "Raté : ${question.a} × ${question.b} = ${question.answer}"
                },
                style = Typo.small,
                color = when {
                    chosen == null -> Ink.Muted
                    chosen == question.answer -> Ink.Teal
                    else -> Ink.Coral
                },
            )
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(2) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(2) { col ->
                        val value = question.choices[row * 2 + col]
                        val good = chosen != null && value == question.answer
                        val bad = chosen == value && value != question.answer
                        val fill = when { good -> Ink.Teal; bad -> Ink.Coral; else -> Ink.Card }
                        Box(
                            Modifier.weight(1f).height(74.dp).clip(RoundedCornerShape(17.dp)).background(fill)
                                .border(1.dp, if (good || bad) fill else Ink.Line, RoundedCornerShape(17.dp))
                                .clickable(enabled = chosen == null && !reported) { pick(value) },
                            contentAlignment = Alignment.Center,
                        ) {
                            AppText("$value", style = Typo.display.copy(fontSize = 28.sp), color = if (good || bad) Color.White else Ink.Text)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        HintBar("Une série de bonnes réponses rapporte plus : 10 points, puis +2 par bonne réponse d'affilée.")
    }
}
