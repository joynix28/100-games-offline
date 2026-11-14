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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import com.botillon.games.ui.AppText
import com.botillon.games.ui.Family
import com.botillon.games.ui.GameEntry
import com.botillon.games.ui.HintBar
import com.botillon.games.ui.Ink
import com.botillon.games.ui.Panel
import com.botillon.games.ui.Pill
import com.botillon.games.ui.Typo
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Lettre manquante : un mot est affiché avec une case vide. Quatre lettres
 * sont proposées, une seule complète le mot. Douze mots par partie.
 */
object LettreManquanteGame {
    val entry = GameEntry(
        id = "lettremanquante",
        title = "Lettre manquante",
        tagline = "Compléter le mot d'une lettre",
        family = Family.WORDS,
        accent = Ink.Mustard,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        LettreManquante(variant, finish)
    }
}

private val MISSING_WORDS = listOf(
    listOf(
        "LUNE", "CHAT", "PAIN", "ROUTE", "PLAGE", "TABLE", "FLEUR", "TIGRE",
        "ARBRE", "PORTE", "VACHE", "ECOLE",
    ),
    listOf(
        "JARDIN", "SOLEIL", "BATEAU", "ORANGE", "MUSIQUE", "ECHELLE", "CUISINE", "CHATEAU",
        "BONBON", "FROMAGE", "CITRON", "GATEAU",
    ),
    listOf(
        "AVENTURE", "TELEPHONE", "PARAPLUIE", "CHOCOLAT", "ESCALIER", "MONTAGNE",
        "APPARTEMENT", "ORDINATEUR", "RESTAURANT", "BOULEVARD", "TRAVERSER", "BIBLIOTHEQUE",
    ),
)

private const val MISSING_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"

@Composable
private fun LettreManquante(variant: Int, finish: (Long, Boolean) -> Unit) {
    val order = remember(variant) { MISSING_WORDS[variant.coerceIn(0, 2)].shuffled().take(12) }
    var round by remember { mutableIntStateOf(0) }
    var chosen by remember { mutableStateOf<Char?>(null) }
    var score by remember { mutableStateOf(0L) }
    var correct by remember { mutableIntStateOf(0) }
    var errors by remember { mutableIntStateOf(0) }
    var reported by remember { mutableStateOf(false) }

    val word = order[round]
    val blank = remember(round) { Random.nextInt(word.length) }
    val answer = word[blank]
    val options = remember(round) {
        (listOf(answer) + MISSING_ALPHABET.filter { it != answer }.toList().shuffled().take(3)).shuffled()
    }

    LaunchedEffect(chosen) {
        if (chosen == null) return@LaunchedEffect
        delay(520)
        if (round >= 11) {
            reported = true
            finish(score, correct >= 9)
        } else {
            round += 1
            chosen = null
        }
    }

    fun pick(letter: Char) {
        if (reported || chosen != null) return
        chosen = letter
        if (letter == answer) {
            score += 10L + variant * 5L
            correct += 1
        } else {
            errors += 1
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pill("MOT ${round + 1} / 12", color = Ink.Mustard, background = Ink.Mustard.copy(alpha = 0.16f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AppText("SCORE", style = Typo.label, color = Ink.Muted)
                AppText("$score", style = Typo.heading, color = Ink.Text)
            }
            Pill("ERREURS $errors", color = if (errors > 0) Ink.Coral else Ink.Muted)
        }

        Spacer(Modifier.height(22.dp))
        Panel(Modifier.fillMaxWidth().height(122.dp)) {
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                word.forEachIndexed { index, letter ->
                    val isBlank = index == blank
                    val width = if (word.length > 9) 26.dp else 32.dp
                    Box(
                        Modifier.size(width = width, height = 42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    !isBlank -> Ink.CardAlt
                                    chosen == null -> Ink.Mustard.copy(alpha = 0.20f)
                                    chosen == answer -> Ink.Teal.copy(alpha = 0.16f)
                                    else -> Ink.Coral.copy(alpha = 0.14f)
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppText(
                            if (isBlank) (chosen?.toString() ?: "_") else "$letter",
                            style = if (word.length > 9) Typo.body else Typo.heading,
                            color = when {
                                !isBlank -> Ink.Soft
                                chosen == null -> Ink.Mustard
                                chosen == answer -> Ink.Teal
                                else -> Ink.Coral
                            },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        AppText("Quelle lettre se cache dans la case ?", style = Typo.small, color = Ink.Muted)

        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            options.forEach { letter ->
                val state = when {
                    chosen == null -> 0
                    letter == answer -> 1
                    letter == chosen -> 2
                    else -> 3
                }
                Box(
                    Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(14.dp))
                        .background(
                            when (state) {
                                1 -> Ink.Teal
                                2 -> Ink.Coral
                                else -> Ink.Card
                            },
                        )
                        .border(1.dp, Ink.Line, RoundedCornerShape(14.dp))
                        .clickable(enabled = chosen == null) { pick(letter) },
                    contentAlignment = Alignment.Center,
                ) {
                    AppText(
                        "$letter",
                        style = Typo.title,
                        color = when (state) {
                            1, 2 -> Ink.Card
                            3 -> Ink.Muted
                            else -> Ink.Text
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Pill(
            "BONNES RÉPONSES : $correct",
            color = Ink.Teal,
            background = Ink.Teal.copy(alpha = 0.10f),
        )

        Spacer(Modifier.weight(1f))
        HintBar(
            when {
                chosen == null -> "Choisissez la lettre qui complète le mot."
                chosen == answer -> "Exact ! Le mot est complet."
                else -> "Non : la bonne lettre était $answer."
            },
            accent = when {
                chosen == null -> Ink.Mustard
                chosen == answer -> Ink.Teal
                else -> Ink.Coral
            },
        )
        Spacer(Modifier.height(8.dp))
    }
}
