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

/**
 * Course de frappe : les mots défilent, il faut les taper au clavier AZERTY
 * le plus vite possible. Trente secondes, un mot validé rapporte selon sa
 * longueur.
 */
object FrappeGame {
    val entry = GameEntry(
        id = "frappe",
        title = "Course de frappe",
        tagline = "Taper les mots avant la fin du temps",
        family = Family.WORDS,
        accent = Ink.Blue,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        Frappe(variant, finish)
    }
}

private val FRAPPE_WORDS = listOf(
    listOf("CHAT", "LUNE", "PAIN", "ROSE", "TABLE", "FLEUR", "TRAIN", "PLAGE", "PORTE", "VACHE", "ECOLE", "LIVRE"),
    listOf("JARDIN", "SOLEIL", "BATEAU", "ORANGE", "CUISINE", "CHATEAU", "FROMAGE", "MONTAGNE", "MUSIQUE", "FENETRE", "ECHELLE", "CITRON"),
    listOf("AVENTURE", "TELEPHONE", "PARAPLUIE", "CHOCOLAT", "ESCALIER", "BOULEVARD", "ORDINATEUR", "RESTAURANT", "APPARTEMENT", "BIBLIOTHEQUE"),
)

private val FRAPPE_KEYS = listOf("AZERTYUIOP", "QSDFGHJKLM", "WXCVBN")

@Composable
private fun Frappe(variant: Int, finish: (Long, Boolean) -> Unit) {
    val words = remember(variant) { FRAPPE_WORDS[variant.coerceIn(0, 2)].shuffled() }
    var index by remember { mutableIntStateOf(0) }
    var typed by remember { mutableStateOf("") }
    var score by remember { mutableStateOf(0L) }
    var done by remember { mutableIntStateOf(0) }
    var left by remember { mutableIntStateOf(30) }
    var message by remember { mutableStateOf("Tapez le mot affiché, lettre après lettre.") }
    var reported by remember { mutableStateOf(false) }
    val word = words[index % words.size]

    LaunchedEffect(Unit) {
        while (left > 0) {
            delay(1000)
            left -= 1
        }
        if (!reported) {
            reported = true
            finish(score, done > 0)
        }
    }

    fun press(key: Char) {
        if (reported || typed.length >= word.length) return
        val expected = word[typed.length]
        if (key != expected) {
            message = "Raté : il fallait $expected."
            return
        }
        val next = typed + key
        typed = next
        if (next == word) {
            val points = word.length * 5L
            score += points
            done += 1
            message = "+$points points · ${words[(index + 1) % words.size]} arrive"
            index += 1
            typed = ""
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pill(
                "TEMPS $left s",
                color = if (left <= 8) Ink.Coral else Ink.Blue,
                background = Ink.Blue.copy(alpha = 0.10f),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AppText("SCORE", style = Typo.label, color = Ink.Muted)
                AppText("$score", style = Typo.heading, color = Ink.Text)
            }
            Pill("$done MOT${if (done > 1) "S" else ""}", color = Ink.Soft)
        }

        Spacer(Modifier.height(16.dp))
        Panel(Modifier.fillMaxWidth().height(108.dp)) {
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                word.forEachIndexed { letterIndex, letter ->
                    val typedAlready = letterIndex < typed.length
                    val current = letterIndex == typed.length
                    Box(
                        Modifier.size(width = if (word.length > 9) 27.dp else 31.dp, height = 42.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(
                                when {
                                    typedAlready -> Ink.Blue.copy(alpha = 0.14f)
                                    current -> Ink.Blue.copy(alpha = 0.08f)
                                    else -> Ink.CardAlt
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppText(
                            "$letter",
                            style = Typo.heading,
                            color = when {
                                typedAlready -> Ink.Blue
                                current -> Ink.Coral
                                else -> Ink.Muted
                            },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        AppText(
            "Lettre ${typed.length + 1} sur ${word.length}",
            style = Typo.small,
            color = Ink.Muted,
        )

        Spacer(Modifier.height(12.dp))
        HintBar(message, accent = Ink.Blue)
        Spacer(Modifier.height(16.dp))

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FRAPPE_KEYS.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    row.forEach { letter ->
                        Box(
                            Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(9.dp))
                                .background(Ink.Card).border(1.dp, Ink.Line, RoundedCornerShape(9.dp))
                                .clickable { press(letter) },
                            contentAlignment = Alignment.Center,
                        ) { AppText("$letter", style = Typo.body, color = Ink.Text) }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        AppText("Seules les bonnes lettres sont acceptées.", style = Typo.small, color = Ink.Muted)
        Spacer(Modifier.height(8.dp))
    }
}
