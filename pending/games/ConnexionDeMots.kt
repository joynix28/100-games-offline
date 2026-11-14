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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.botillon.games.ui.AppText
import com.botillon.games.ui.Family
import com.botillon.games.ui.GameEntry
import com.botillon.games.ui.HintBar
import com.botillon.games.ui.Ink
import com.botillon.games.ui.Pill
import com.botillon.games.ui.PrimaryButton
import com.botillon.games.ui.Typo

/**
 * Connexion de mots : seize mots, quatre familles de quatre. On sélectionne
 * un groupe puis on le valide ; quatre erreurs mettent fin à la partie.
 */
object ConnexionGame {
    val entry = GameEntry(
        id = "connexion",
        title = "Connexion de mots",
        tagline = "Quatre familles à retrouver",
        family = Family.WORDS,
        accent = Ink.Green,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        Connexion(variant, finish)
    }
}

private data class WordGroup(val name: String, val words: List<String>)

private val CONNEXIONS = listOf(
    listOf(
        WordGroup("ANIMAUX", listOf("CHAT", "CHIEN", "LAPIN", "TIGRE")),
        WordGroup("COULEURS", listOf("ROUGE", "VERT", "BLEU", "JAUNE")),
        WordGroup("MEUBLES", listOf("TABLE", "CHAISE", "LIT", "ARMOIRE")),
        WordGroup("FRUITS", listOf("POMME", "POIRE", "KIWI", "CITRON")),
    ),
    listOf(
        WordGroup("VILLES", listOf("PARIS", "LYON", "NICE", "LILLE")),
        WordGroup("METIERS", listOf("MEDECIN", "BOULANGER", "PLOMBIER", "AVOCAT")),
        WordGroup("SPORTS", listOf("TENNIS", "JUDO", "RUGBY", "BOXE")),
        WordGroup("INSTRUMENTS", listOf("PIANO", "VIOLON", "FLUTE", "TAMBOUR")),
    ),
    listOf(
        WordGroup("ARBRES", listOf("CHENE", "HETRE", "BOULEAU", "SAPIN")),
        WordGroup("PAYS", listOf("FRANCE", "ITALIE", "JAPON", "BRESIL")),
        WordGroup("METEO", listOf("PLUIE", "NEIGE", "VENT", "ORAGE")),
        WordGroup("CUISINE", listOf("FOUR", "COUTEAU", "CASSEROLE", "LOUCHE")),
    ),
)

private val GROUP_COLORS = listOf(Ink.Coral, Ink.Teal, Ink.Blue, Ink.Violet)

@Composable
private fun Connexion(variant: Int, finish: (Long, Boolean) -> Unit) {
    val puzzle = remember(variant) { CONNEXIONS[variant.coerceIn(0, 2)] }
    val words = remember(variant) { puzzle.flatMap { it.words }.shuffled() }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var solved by remember { mutableStateOf<List<WordGroup>>(emptyList()) }
    var mistakes by remember { mutableIntStateOf(0) }
    var score by remember { mutableStateOf(0L) }
    var message by remember { mutableStateOf("Reliez quatre mots d'une même famille.") }
    var reported by remember { mutableStateOf(false) }

    fun toggle(word: String) {
        if (reported || solved.any { word in it.words }) return
        selected = when {
            word in selected -> selected - word
            selected.size < 4 -> selected + word
            else -> selected
        }
    }

    fun check() {
        if (reported || selected.size != 4) return
        val group = puzzle.firstOrNull { it.words.toSet() == selected }
        if (group != null) {
            solved = solved + group
            selected = emptySet()
            score += 50L
            message = "Bien vu : ${group.name}."
            if (solved.size == 4) {
                reported = true
                finish(score + 60L, true)
            }
        } else {
            mistakes += 1
            selected = emptySet()
            message = "Ces quatre mots ne vont pas ensemble."
            if (mistakes >= 4) {
                reported = true
                finish(score, false)
            }
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pill("${solved.size} / 4 FAMILLES", color = Ink.Green, background = Ink.Green.copy(alpha = 0.12f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AppText("SCORE", style = Typo.label, color = Ink.Muted)
                AppText("$score", style = Typo.heading, color = Ink.Text)
            }
            Pill("ERREURS $mistakes / 4", color = if (mistakes > 0) Ink.Coral else Ink.Muted)
        }

        Spacer(Modifier.height(10.dp))
        HintBar(message, accent = if (mistakes >= 3) Ink.Coral else Ink.Green)

        Spacer(Modifier.height(10.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            words.chunked(4).forEach { line ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    line.forEach { word ->
                        val owner = solved.firstOrNull { word in it.words }
                        val color: Color? = owner?.let { GROUP_COLORS[puzzle.indexOf(it) % GROUP_COLORS.size] }
                        val isSelected = word in selected
                        Box(
                            Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(11.dp))
                                .background(
                                    when {
                                        color != null -> color.copy(alpha = 0.14f)
                                        isSelected -> Ink.Green.copy(alpha = 0.14f)
                                        else -> Ink.Card
                                    },
                                )
                                .border(
                                    if (isSelected) 2.dp else 1.dp,
                                    when {
                                        isSelected -> Ink.Green
                                        color != null -> color
                                        else -> Ink.Line
                                    },
                                    RoundedCornerShape(11.dp),
                                )
                                .clickable { toggle(word) },
                            contentAlignment = Alignment.Center,
                        ) {
                            AppText(
                                word,
                                style = Typo.small,
                                color = when {
                                    color != null -> color
                                    isSelected -> Ink.Green
                                    else -> Ink.Soft
                                },
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            "VALIDER LA SÉLECTION",
            { check() },
            Modifier.fillMaxWidth(),
            color = Ink.Green,
            enabled = selected.size == 4,
        )

        Spacer(Modifier.height(10.dp))
        AppText(
            if (solved.isEmpty()) "Aucune famille trouvée" else "Trouvées : " + solved.joinToString(" · ") { it.name },
            style = Typo.small,
            color = Ink.Muted,
            maxLines = 2,
            softWrap = true,
        )
        Spacer(Modifier.height(4.dp))
        AppText(
            "Sélectionnez quatre mots, puis validez.",
            style = Typo.small,
            color = Ink.Muted,
        )
    }
}
