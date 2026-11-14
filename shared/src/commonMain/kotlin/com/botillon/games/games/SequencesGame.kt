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
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Suites : deviner le prochain terme d'une suite arithmétique, géométrique ou de Fibonacci. */
object SequencesGame {
    val entry = GameEntry(
        id = "suites", title = "Suites", tagline = "Devinez le prochain terme",
        family = Family.WORDS, accent = Ink.Pink, variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ -> Sequences(variant, finish) }
}

private data class Series(val shown: List<Int>, val answer: Int, val rule: String)

private fun newSeries(variant: Int): Series {
    val kind = when (variant) {
        0 -> 0
        1 -> Random.nextInt(0, 3)
        else -> Random.nextInt(1, 3)
    }
    val terms = mutableListOf<Int>()
    val rule: String
    when (kind) {
        0 -> {
            val start = Random.nextInt(1, 12) + variant * 2
            val step = when (variant) {
                0 -> Random.nextInt(1, 6)
                1 -> Random.nextInt(2, 13)
                else -> Random.nextInt(4, 25)
            }
            repeat(5) { terms += start + it * step }
            rule = "Suite arithmétique : + $step à chaque terme."
        }

        1 -> {
            val ratio = if (variant == 2) Random.nextInt(2, 5) else 2
            var value = Random.nextInt(1, 5)
            repeat(5) {
                terms += value
                value *= ratio
            }
            rule = "Suite géométrique : × $ratio à chaque terme."
        }

        else -> {
            var a = Random.nextInt(1, 7)
            var b = Random.nextInt(1, 8)
            repeat(5) {
                terms += a
                val next = a + b
                a = b
                b = next
            }
            rule = "Type Fibonacci : chaque terme est la somme des deux précédents."
        }
    }
    return Series(terms.take(4), terms[4], rule)
}

private fun choices(answer: Int): List<Int> {
    val values = mutableSetOf(answer)
    while (values.size < 4) {
        val delta = Random.nextInt(1, 16)
        val candidate = if (Random.nextBoolean()) answer + delta else answer - delta
        if (candidate > 0) values += candidate
    }
    return values.toList().shuffled()
}

@Composable
private fun Sequences(variant: Int, finish: (Long, Boolean) -> Unit) {
    var round by remember { mutableIntStateOf(0) }
    var series by remember { mutableStateOf(newSeries(variant)) }
    var settings by remember { mutableStateOf(choices(series.answer)) }
    var score by remember { mutableStateOf(0L) }
    var streak by remember { mutableIntStateOf(0) }
    var correct by remember { mutableIntStateOf(0) }
    var chosen by remember { mutableStateOf<Int?>(null) }
    var reported by remember { mutableStateOf(false) }

    fun report(won: Boolean) {
        if (reported) return
        reported = true
        finish(score, won)
    }

    fun pick(value: Int) {
        if (reported || chosen != null) return
        chosen = value
        if (value == series.answer) { score += (12 + streak * 2).toLong(); streak++; correct++ } else streak = 0
    }

    LaunchedEffect(chosen) {
        if (chosen == null) return@LaunchedEffect
        delay(850)
        if (round == 7) report(correct >= 6)
        else {
            round += 1
            series = newSeries(variant)
            settings = choices(series.answer)
            chosen = null
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            AppText("SUITE ${round + 1} / 8", style = Typo.label, color = Ink.Muted)
            Pill("SÉRIE $streak", color = Ink.Soft, background = Ink.Pink.copy(alpha = 0.14f))
            AppText("$score PTS", style = Typo.heading, color = Ink.Text)
        }
        Spacer(Modifier.height(12.dp))
        Panel(Modifier.fillMaxWidth().height(92.dp)) {
            Row(
                Modifier.fillMaxSize().padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                series.shown.forEach { value ->
                    Box(
                        Modifier.weight(1f).height(62.dp).clip(RoundedCornerShape(12.dp)).background(Ink.CardAlt)
                            .border(1.dp, Ink.Line, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) { AppText("$value", style = Typo.heading.copy(fontSize = 16.sp), color = Ink.Text) }
                }
                Box(
                    Modifier.weight(1f).height(62.dp).clip(RoundedCornerShape(12.dp)).background(Ink.Pink),
                    contentAlignment = Alignment.Center,
                ) { AppText("?", style = Typo.heading.copy(fontSize = 20.sp), color = Color.White) }
            }
        }
        Box(Modifier.fillMaxWidth().height(46.dp), contentAlignment = Alignment.Center) {
            AppText(
                text = if (chosen == null) "Devinez le terme suivant." else "Réponse ${series.answer} · ${series.rule}",
                style = Typo.small,
                color = if (chosen == null) Ink.Muted else Ink.Teal,
                align = TextAlign.Center,
                maxLines = 2,
                softWrap = true,
            )
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(2) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(2) { col ->
                        val value = settings[row * 2 + col]
                        val good = chosen != null && value == series.answer
                        val bad = chosen == value && value != series.answer
                        val fill = when { good -> Ink.Teal; bad -> Ink.Coral; else -> Ink.Card }
                        Box(
                            Modifier.weight(1f).height(72.dp).clip(RoundedCornerShape(16.dp)).background(fill)
                                .border(1.dp, if (good || bad) fill else Ink.Line, RoundedCornerShape(16.dp))
                                .clickable(enabled = chosen == null && !reported) { pick(value) },
                            contentAlignment = Alignment.Center,
                        ) {
                            AppText("$value", style = Typo.display.copy(fontSize = 25.sp), color = if (good || bad) Color.White else Ink.Text)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        HintBar("Huit suites. Cherchez l'écart constant, le rapport constant ou la somme des deux termes.")
    }
}
