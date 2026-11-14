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
 * Anagrammes : les lettres d'un mot sont mélangées, on les retape dans le bon
 * ordre avec un clavier AZERTY. Cinq mots par partie, les erreurs coûtent cher.
 */
object AnagrammesGame {
    val entry = GameEntry(
        id = "anagrammes",
        title = "Anagrammes",
        tagline = "Remettre les lettres dans l'ordre",
        family = Family.WORDS,
        accent = Ink.Violet,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        Anagrammes(variant, finish)
    }
}

private val ANA_WORDS = listOf(
    listOf("LUNE", "CHAT", "PAIN", "ROUTE", "PLAGE", "TABLE", "FLEUR", "TIGRE"),
    listOf("JARDIN", "SOLEIL", "BATEAU", "ORANGE", "MUSIQUE", "ECHELLE", "CUISINE", "CHATEAU"),
    listOf("AVENTURE", "TELEPHONE", "PARAPLUIE", "CHOCOLAT", "ESCALIER", "MONTAGNE"),
)

private val ANA_KEYS = listOf("AZERTYUIOP", "QSDFGHJKLM", "WXCVBN")

@Composable
private fun Anagrammes(variant: Int, finish: (Long, Boolean) -> Unit) {
    val pool = remember(variant) { ANA_WORDS[variant.coerceIn(0, 2)] }
    val order = remember(variant) { pool.shuffled() }
    var round by remember { mutableIntStateOf(0) }
    var typed by remember { mutableStateOf("") }
    val word = order[round]
    val letters = remember(round) { word.toList().shuffled() }
    var score by remember { mutableStateOf(0L) }
    var errors by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("Tapez les lettres dans le bon ordre.") }
    var reported by remember { mutableStateOf(false) }

    fun press(key: Char) {
        if (reported || typed.length >= word.length) return
        if (typed.count { it == key } >= letters.count { it == key }) return
        typed += key
        if (typed.length == word.length) {
            if (typed == word) {
                score += 15L + word.length * 5L
                message = "Bien joué !"
                if (round == 4) {
                    reported = true
                    finish(score, true)
                } else {
                    round += 1
                    typed = ""
                }
            } else {
                errors += 1
                message = "Presque : effacez et réessayez."
            }
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pill("MOT ${round + 1} / 5", color = Ink.Violet, background = Ink.Violet.copy(alpha = 0.10f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AppText("SCORE", style = Typo.label, color = Ink.Muted)
                AppText("$score", style = Typo.heading, color = Ink.Text)
            }
            Pill("ERREURS $errors", color = if (errors > 0) Ink.Coral else Ink.Soft)
        }

        Spacer(Modifier.height(14.dp))
        Panel(Modifier.fillMaxWidth().height(86.dp)) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                letters.forEach { letter ->
                    Box(
                        Modifier.size(30.dp).clip(RoundedCornerShape(8.dp))
                            .background(Ink.CardAlt).border(1.dp, Ink.Line, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) { AppText("$letter", style = Typo.heading, color = Ink.Soft) }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            word.indices.forEach { index ->
                val filled = index < typed.length
                Box(
                    Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                        .background(if (filled) Ink.Violet else Ink.Card)
                        .border(1.dp, if (filled) Ink.Violet else Ink.Line, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    AppText(
                        if (filled) "${typed[index]}" else "",
                        style = Typo.heading,
                        color = Ink.Card,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        HintBar(message, accent = if (message.startsWith("Presque")) Ink.Coral else Ink.Violet)

        Spacer(Modifier.height(14.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ANA_KEYS.forEachIndexed { rowIndex, row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    row.forEach { letter ->
                        Box(
                            Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(9.dp))
                                .background(Ink.Card).border(1.dp, Ink.Line, RoundedCornerShape(9.dp))
                                .clickable { press(letter) },
                            contentAlignment = Alignment.Center,
                        ) { AppText("$letter", style = Typo.body, color = Ink.Text) }
                    }
                    if (rowIndex == 2) {
                        Box(
                            Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(9.dp))
                                .background(Ink.CardAlt).clickable { typed = typed.dropLast(1) },
                            contentAlignment = Alignment.Center,
                        ) { AppText("⌫", style = Typo.body, color = Ink.Soft) }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        AppText("Le clavier ne propose que les lettres du tirage.", style = Typo.small, color = Ink.Muted)
    }
}
