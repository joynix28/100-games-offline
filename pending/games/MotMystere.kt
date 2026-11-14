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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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

/**
 * Mot mystère : le pendu sans dessin. Six erreurs maximum, un indice pour
 * chaque mot, et un joker qui révèle une lettre en consommant une erreur.
 */
object MotMystereGame {
    val entry = GameEntry(
        id = "motmystere",
        title = "Mot mystère",
        tagline = "Six erreurs, un seul mot",
        family = Family.WORDS,
        accent = Ink.Teal,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        MotMystere(variant, finish)
    }
}

private data class Mystery(val word: String, val clue: String)

private val MYSTERIES = listOf(
    listOf(
        Mystery("CHAT", "Petit félin domestique"),
        Mystery("PAIN", "Il sort du four"),
        Mystery("LUNE", "Elle brille la nuit"),
        Mystery("ROSE", "Fleur à épines"),
        Mystery("MER", "Grande étendue salée"),
        Mystery("TRAIN", "Il roule sur des rails"),
    ),
    listOf(
        Mystery("JARDIN", "Espace vert derrière la maison"),
        Mystery("BATEAU", "Il flotte sur l'eau"),
        Mystery("ORANGE", "Fruit et couleur"),
        Mystery("MUSIQUE", "L'art des sons"),
        Mystery("MONTAGNE", "Sommet élevé"),
        Mystery("FROMAGE", "Produit du lait"),
    ),
    listOf(
        Mystery("PARAPLUIE", "Il s'ouvre sous la pluie"),
        Mystery("TELEPHONE", "Il sert à appeler"),
        Mystery("AVENTURE", "Histoire pleine de péripéties"),
        Mystery("CHOCOLAT", "Douceur des goûters"),
        Mystery("ESCALIER", "Il relie deux étages"),
        Mystery("HORLOGER", "Il répare les montres"),
    ),
)

private val MYST_KEYS = listOf("AZERTYUIOP", "QSDFGHJKLM", "WXCVBN")

@Composable
private fun MotMystere(variant: Int, finish: (Long, Boolean) -> Unit) {
    val pool = remember(variant) { MYSTERIES[variant.coerceIn(0, 2)] }
    val mystery = remember(variant) { pool.random() }
    var guessed by remember { mutableStateOf<Set<Char>>(emptySet()) }
    var errors by remember { mutableIntStateOf(0) }
    var score by remember { mutableStateOf(0L) }
    var message by remember { mutableStateOf("Devinez le mot, lettre par lettre.") }
    var reported by remember { mutableStateOf(false) }

    val word = mystery.word
    val won = word.all { it in guessed }
    val wrong = guessed.filter { it !in word }.sorted()

    fun play(letter: Char, free: Boolean = false) {
        if (reported || letter in guessed || won || errors >= 6) return
        val next = guessed + letter
        guessed = next
        if (letter !in word && !free) errors += 1
        if (word.all { it in next }) {
            score = 40L + ((6 - errors) * 15L) + variant * 10L
            reported = true
            finish(score, true)
        } else if (errors >= 6) {
            score = word.count { it in next } * 5L
            reported = true
            finish(score, false)
        }
    }

    fun hint() {
        if (reported || won) return
        val hidden = word.filter { it !in guessed }
        if (hidden.isEmpty() || errors >= 6) return
        play(hidden.random(), free = true)
        message = "Joker utilisé : une erreur en plus."
        errors += 1
        if (!reported && errors >= 6) {
            score = word.count { it in guessed } * 5L
            reported = true
            finish(score, false)
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                AppText("ERREURS", style = Typo.label, color = Ink.Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(6) { index ->
                        Box(
                            Modifier.size(11.dp).clip(CircleShape)
                                .background(if (index < errors) Ink.Coral else Ink.Line),
                        )
                    }
                }
            }
            Pill("${word.length} LETTRES", color = Ink.Teal, background = Ink.Teal.copy(alpha = 0.10f))
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(Ink.CardAlt)
                    .clickable { hint() }.padding(horizontal = 10.dp, vertical = 6.dp),
            ) { AppText("JOKER −1", style = Typo.label, color = Ink.Mustard) }
        }

        Spacer(Modifier.height(10.dp))
        HintBar("Indice : ${mystery.clue}", accent = Ink.Teal)

        Spacer(Modifier.height(14.dp))
        Panel(Modifier.fillMaxWidth().height(92.dp)) {
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                word.forEach { letter ->
                    val visible = letter in guessed
                    Box(
                        Modifier.size(if (word.length > 9) 27.dp else 32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (visible) Ink.Teal.copy(alpha = 0.12f) else Ink.CardAlt),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppText(
                            if (visible) "$letter" else "_",
                            style = Typo.heading,
                            color = if (visible) Ink.Teal else Ink.Muted,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().height(26.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            AppText(
                if (wrong.isEmpty()) "Aucune lettre écartée" else "Écartées : ${wrong.joinToString(" ")}",
                style = Typo.small,
                color = if (wrong.isEmpty()) Ink.Muted else Ink.Coral,
            )
        }

        Spacer(Modifier.height(4.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MYST_KEYS.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    row.forEach { letter ->
                        val used = letter in guessed
                        Box(
                            Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(9.dp))
                                .background(
                                    when {
                                        used && letter in word -> Ink.Teal
                                        used -> Ink.Line
                                        else -> Ink.Card
                                    },
                                )
                                .border(1.dp, Ink.Line, RoundedCornerShape(9.dp))
                                .clickable(enabled = !used) { play(letter) },
                            contentAlignment = Alignment.Center,
                        ) {
                            AppText(
                                "$letter",
                                style = Typo.body,
                                color = when {
                                    used && letter in word -> Ink.Card
                                    used -> Ink.Muted
                                    else -> Ink.Text
                                },
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        AppText(message, style = Typo.small, color = Ink.Muted)
    }
}
