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

/** Nombre manquant : une suite logique, une case vide, un clavier numérique. */
object MissingNumberGame {
    val entry = GameEntry(
        id = "manquant", title = "Nombre manquant", tagline = "Complétez la suite logique",
        family = Family.LOGIC, accent = Ink.Violet, variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ -> MissingNumber(variant, finish) }
}

private data class Suite(val terms: List<Int>, val blank: Int, val answer: Int, val rule: String)

private fun newSuite(variant: Int): Suite {
    val terms = mutableListOf<Int>()
    val rule: String
    when {
        variant == 2 && Random.nextBoolean() -> {
            var a = Random.nextInt(1, 6)
            var b = Random.nextInt(1, 7)
            repeat(5) {
                terms += a
                val next = a + b
                a = b
                b = next
            }
            rule = "Chaque terme est la somme des deux précédents."
        }

        variant > 0 && Random.nextBoolean() -> {
            val ratio = if (variant == 2) Random.nextInt(2, 4) else 2
            var value = Random.nextInt(1, 5)
            repeat(5) {
                terms += value
                value *= ratio
            }
            rule = "On multiplie par $ratio à chaque étape."
        }

        else -> {
            val start = Random.nextInt(1, 12)
            val step = when (variant) {
                0 -> Random.nextInt(1, 6)
                1 -> Random.nextInt(2, 13)
                else -> Random.nextInt(3, 20)
            }
            repeat(5) { terms += start + it * step }
            rule = "On ajoute $step à chaque étape."
        }
    }
    val blank = Random.nextInt(1, 5)
    return Suite(terms, blank, terms[blank], rule)
}

@Composable
private fun MissingNumber(variant: Int, finish: (Long, Boolean) -> Unit) {
    var round by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(3) }
    var score by remember { mutableStateOf(0L) }
    var streak by remember { mutableIntStateOf(0) }
    var correct by remember { mutableIntStateOf(0) }
    var suite by remember { mutableStateOf(newSuite(variant)) }
    var input by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf<Boolean?>(null) }
    var reported by remember { mutableStateOf(false) }

    fun report(won: Boolean) {
        if (reported) return
        reported = true
        finish(score, won)
    }

    fun submit() {
        if (reported || feedback != null || input.isEmpty()) return
        val value = input.toIntOrNull() ?: return
        if (value == suite.answer) {
            score += (15 + streak * 2).toLong()
            streak++
            correct++
            feedback = true
        } else {
            streak = 0
            lives--
            feedback = false
        }
    }

    LaunchedEffect(feedback) {
        if (feedback == null) return@LaunchedEffect
        delay(900)
        if (lives <= 0) report(false)
        else if (round == 7) report(correct >= 6)
        else {
            round += 1
            suite = newSuite(variant)
            input = ""
            feedback = null
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            AppText("SUITE ${round + 1} / 8", style = Typo.label, color = Ink.Muted)
            Pill("VIES $lives", color = Ink.Soft, background = Ink.Violet.copy(alpha = 0.14f))
            AppText("$score PTS", style = Typo.heading, color = Ink.Text)
        }
        Spacer(Modifier.height(10.dp))
        Panel(Modifier.fillMaxWidth().height(84.dp)) {
            Row(
                Modifier.fillMaxSize().padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                suite.terms.forEachIndexed { index, value ->
                    val hidden = index == suite.blank
                    Box(
                        Modifier.weight(1f).height(58.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (hidden) Ink.Violet else Ink.CardAlt)
                            .border(1.dp, if (hidden) Ink.Violet else Ink.Line, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppText(
                            if (hidden) "?" else "$value",
                            style = Typo.heading.copy(fontSize = 17.sp),
                            color = if (hidden) Color.White else Ink.Text,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Panel(Modifier.fillMaxWidth().height(56.dp), color = Ink.CardAlt) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AppText(
                    input.ifEmpty { "Votre réponse" },
                    style = Typo.display.copy(fontSize = 26.sp),
                    color = if (input.isEmpty()) Ink.Muted else Ink.Text,
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(38.dp), contentAlignment = Alignment.Center) {
            AppText(
                text = when (feedback) {
                    null -> suite.rule
                    true -> "Exact ! +${15 + (streak - 1) * 2} points"
                    false -> "La réponse était ${suite.answer}."
                },
                style = Typo.small,
                color = when (feedback) { null -> Ink.Muted; true -> Ink.Teal; else -> Ink.Coral },
                align = TextAlign.Center,
            )
        }
        for (row in 0 until 3) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (col in 0 until 3) {
                    val digit = row * 3 + col + 1
                    Box(
                        Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp)).background(Ink.Card)
                            .border(1.dp, Ink.Line, RoundedCornerShape(12.dp))
                            .clickable(enabled = feedback == null && !reported) { input = (input + digit).take(4) },
                        contentAlignment = Alignment.Center,
                    ) { AppText("$digit", style = Typo.heading.copy(fontSize = 18.sp), color = Ink.Text) }
                }
            }
            Spacer(Modifier.height(7.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("EFFACER", { input = "" }, Modifier.weight(1f))
            PrimaryButton(
                "VALIDER",
                { submit() },
                Modifier.weight(1.4f),
                color = Ink.Violet,
                enabled = input.isNotEmpty() && feedback == null && !reported,
            )
        }
        Spacer(Modifier.weight(1f))
        HintBar("Huit suites, trois vies. Observez l'écart ou le rapport entre les termes.")
    }
}
