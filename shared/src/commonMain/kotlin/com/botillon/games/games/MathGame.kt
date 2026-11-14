package com.botillon.games.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.botillon.games.ui.AppText
import com.botillon.games.ui.Family
import com.botillon.games.ui.GameEntry
import com.botillon.games.ui.HintBar
import com.botillon.games.ui.Ink
import com.botillon.games.ui.Panel
import com.botillon.games.ui.Pill
import com.botillon.games.ui.SecondaryButton
import com.botillon.games.ui.Typo
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Calcul mental : trente secondes pour enchaîner un maximum d'opérations.
 * Une bonne réponse rapporte 10 points. Une mauvaise réponse ne retire rien :
 * on passe simplement à l'opération suivante, sans pénalité.
 * Le bouton « Passer » est lui aussi gratuit.
 */
object MathGame {
    val entry = GameEntry(
        id = "calcul",
        title = "Calcul mental",
        tagline = "Trente secondes, un maximum de calculs",
        family = Family.WORDS,
        accent = Ink.Mustard,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        MathBlitz(variant, finish)
    }
}

private data class Operation(
    val text: String,
    val answer: Int,
    val choices: List<Int>,
)

/**
 * Facile : additions et soustractions avec des nombres de 1 à 10.
 * Moyen : nombres de 1 à 20, avec quelques multiplications.
 * Difficile : multiplications et divisions exactes avec des nombres de 2 à 12.
 */
private fun buildOperation(variant: Int): Operation {
    val random = Random.Default
    fun op(text: String, answer: Int) = Operation(text, answer, choicesFor(answer, variant))

    return when (variant) {
        0 -> {
            val a = random.nextInt(1, 11)
            val b = random.nextInt(1, 11)
            if (random.nextBoolean()) {
                op("$a + $b", a + b)
            } else {
                val high = maxOf(a, b)
                val low = minOf(a, b)
                op("$high − $low", high - low)
            }
        }

        1 -> when (random.nextInt(3)) {
            0 -> {
                val a = random.nextInt(1, 21)
                val b = random.nextInt(1, 21)
                op("$a + $b", a + b)
            }

            1 -> {
                val a = random.nextInt(1, 21)
                val b = random.nextInt(1, 21)
                val high = maxOf(a, b)
                val low = minOf(a, b)
                op("$high − $low", high - low)
            }

            else -> {
                val a = random.nextInt(2, 13)
                val b = random.nextInt(2, 10)
                op("$a × $b", a * b)
            }
        }

        else -> if (random.nextBoolean()) {
            val a = random.nextInt(2, 13)
            val b = random.nextInt(2, 13)
            op("$a × $b", a * b)
        } else {
            val divisor = random.nextInt(2, 13)
            val quotient = random.nextInt(2, 13)
            op("${divisor * quotient} ÷ $divisor", quotient)
        }
    }
}

/** Trois leurres distincts, proches de la bonne réponse pour rester crédibles. */
private fun choicesFor(answer: Int, variant: Int): List<Int> {
    val random = Random.Default
    val spread = if (variant == 2) 12 else 9
    val values = mutableSetOf(answer)

    var guard = 0
    while (values.size < 4 && guard < 160) {
        guard++
        val delta = random.nextInt(-spread, spread + 1)
        if (delta == 0) continue
        val candidate = answer + delta
        if (candidate >= 0) values += candidate
    }

    var extra = 1
    while (values.size < 4) {
        values += answer + extra
        extra++
    }

    return values.toList().shuffled(random)
}

@Composable
private fun MathBlitz(variant: Int, finish: (Long, Boolean) -> Unit) {
    var remaining by remember { mutableIntStateOf(30) }
    var score by remember { mutableStateOf(0L) }
    var operation by remember { mutableStateOf(buildOperation(variant)) }
    var chosen by remember { mutableStateOf<Int?>(null) }
    var reported by remember { mutableStateOf(false) }

    // Compte à rebours : une seconde par point de temps.
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1000)
            remaining -= 1
        }
        if (!reported) {
            reported = true
            finish(score, score > 0)
        }
    }

    // Petit temps de respiration pour lire le retour vert ou rouge.
    LaunchedEffect(chosen) {
        if (chosen == null) return@LaunchedEffect
        delay(430)
        if (!reported) {
            operation = buildOperation(variant)
            chosen = null
        }
    }

    fun choose(value: Int) {
        if (reported || chosen != null) return
        chosen = value
        if (value == operation.answer) score += 10L
    }

    fun skip() {
        if (reported || chosen != null) return
        operation = buildOperation(variant)
    }

    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                AppText("SCORE", style = Typo.label, color = Ink.Muted)
                AppText("$score", style = Typo.heading, color = Ink.Text)
            }
            Pill(
                text = "10 PTS / BONNE",
                color = Ink.Soft,
                background = Ink.Mustard.copy(alpha = 0.18f),
            )
            Column(horizontalAlignment = Alignment.End) {
                AppText("TEMPS", style = Typo.label, color = Ink.Muted)
                AppText(
                    text = "$remaining s",
                    style = Typo.heading,
                    color = if (remaining <= 10) Ink.Coral else Ink.Text,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Panel(Modifier.fillMaxWidth().height(138.dp)) {
            Column(
                Modifier.fillMaxSize().padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                AppText("TROUVEZ LE RÉSULTAT", style = Typo.label, color = Ink.Muted)
                Spacer(Modifier.height(8.dp))
                AppText(
                    text = operation.text,
                    style = Typo.display.copy(fontSize = 38.sp),
                    color = Ink.Text,
                    align = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.Center) {
            AppText(
                text = when {
                    chosen == null -> ""
                    chosen == operation.answer -> "Bien joué !"
                    else -> "Raté, on continue."
                },
                style = Typo.small,
                color = if (chosen == operation.answer) Ink.Teal else Ink.Coral,
            )
        }

        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            repeat(2) { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    repeat(2) { col ->
                        val value = operation.choices[row * 2 + col]
                        AnswerButton(
                            value = value,
                            correct = value == operation.answer,
                            chosen = chosen,
                            enabled = !reported,
                            modifier = Modifier.weight(1f),
                            onClick = { choose(value) },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SecondaryButton(
            label = "PASSER CETTE OPÉRATION",
            onClick = { skip() },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.weight(1f))
        HintBar("30 secondes. Répondez vite, chaque bonne réponse rapporte 10 points.")
    }
}

@Composable
private fun AnswerButton(
    value: Int,
    correct: Boolean,
    chosen: Int?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val good = chosen != null && correct
    val bad = chosen == value && !correct
    val fill = when {
        good -> Ink.Teal
        bad -> Ink.Coral
        else -> Ink.Card
    }

    Box(
        modifier
            .height(86.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(fill)
            .border(
                width = if (good || bad) 2.dp else 1.dp,
                color = if (good || bad) fill else Ink.Line,
                shape = RoundedCornerShape(18.dp),
            )
            .clickable(enabled = enabled && chosen == null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AppText(
            text = "$value",
            style = Typo.display.copy(fontSize = 30.sp),
            color = if (good || bad) Color.White else Ink.Text,
        )
    }
}
