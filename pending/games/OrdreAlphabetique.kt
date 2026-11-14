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
import com.botillon.games.ui.Pill
import com.botillon.games.ui.PrimaryButton
import com.botillon.games.ui.Typo

/**
 * Ordre alphabétique : six mots sont mélangés, on les échange deux par deux
 * pour reconstituer l'ordre du dictionnaire. Cinq listes, trois erreurs max.
 */
object OrdreGame {
    val entry = GameEntry(
        id = "ordre",
        title = "Ordre alphabétique",
        tagline = "Ranger les mots de A à Z",
        family = Family.WORDS,
        accent = Ink.Mustard,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        Ordre(variant, finish)
    }
}

private val ORDRE_WORDS = listOf(
    "ARBRE", "AVION", "BATEAU", "BONBON", "CABANE", "CAFE", "CHAT", "CHIEN",
    "CITRON", "COLLINE", "COURSE", "DESSIN", "ECOLE", "ETOILE", "FENETRE", "FLEUR",
    "FROMAGE", "GATEAU", "GUITARE", "HERBE", "HISTOIRE", "JARDIN", "LAMPE", "LAPIN",
    "LIVRE", "MAISON", "MONTAGNE", "MUSIQUE", "NAVIRE", "OISEAU", "ORANGE", "PAPILLON",
    "PARAPLUIE", "POISSON", "POMME", "PORTE", "RAISIN", "RIVIERE", "ROUTE", "SALADE",
    "SOLEIL", "TABLE", "TOMATE", "TRAIN", "VACHE", "VELO", "VERRE", "VOITURE",
)

@Composable
private fun Ordre(variant: Int, finish: (Long, Boolean) -> Unit) {
    val count = 5 + variant
    var round by remember { mutableIntStateOf(0) }
    val start = remember(round) {
        val list = ORDRE_WORDS.shuffled().take(count)
        if (list == list.sorted()) list.reversed() else list
    }
    var order by remember(round) { mutableStateOf(start) }
    var selected by remember(round) { mutableStateOf<Int?>(null) }
    var errors by remember { mutableIntStateOf(0) }
    var score by remember { mutableStateOf(0L) }
    var message by remember(round) { mutableStateOf("Touchez deux mots pour les échanger.") }
    var reported by remember { mutableStateOf(false) }

    fun move(index: Int) {
        if (reported) return
        val first = selected
        if (first == null) {
            selected = index
        } else if (first == index) {
            selected = null
        } else {
            val list = order.toMutableList()
            val swapped = list[first]
            list[first] = list[index]
            list[index] = swapped
            order = list
            selected = null
        }
    }

    fun check() {
        if (reported) return
        if (order == order.sorted()) {
            score += 30L
            if (round == 4) {
                reported = true
                finish(score + 50L, true)
            } else {
                round += 1
            }
        } else {
            errors += 1
            message = "Pas encore : comparez les premières lettres."
            if (errors >= 3) {
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
            Pill("LISTE ${round + 1} / 5", color = Ink.Mustard, background = Ink.Mustard.copy(alpha = 0.16f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AppText("SCORE", style = Typo.label, color = Ink.Muted)
                AppText("$score", style = Typo.heading, color = Ink.Text)
            }
            Pill("ERREURS $errors / 3", color = if (errors > 0) Ink.Coral else Ink.Muted)
        }

        Spacer(Modifier.height(10.dp))
        HintBar(message, accent = if (errors >= 2) Ink.Coral else Ink.Mustard)

        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            order.forEachIndexed { index, word ->
                val isSelected = selected == index
                Box(
                    Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(11.dp))
                        .background(if (isSelected) Ink.Mustard.copy(alpha = 0.18f) else Ink.Card)
                        .border(
                            if (isSelected) 2.dp else 1.dp,
                            if (isSelected) Ink.Mustard else Ink.Line,
                            RoundedCornerShape(11.dp),
                        )
                        .clickable { move(index) }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppText(
                            "${index + 1}.",
                            style = Typo.label,
                            color = if (isSelected) Ink.Mustard else Ink.Muted,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                        AppText(word, style = Typo.body, color = Ink.Text)
                        Spacer(Modifier.weight(1f))
                        if (isSelected) AppText("SÉLECTIONNÉ", style = Typo.label, color = Ink.Mustard)
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        PrimaryButton("VÉRIFIER L'ORDRE", { check() }, Modifier.fillMaxWidth(), color = Ink.Mustard)
        Spacer(Modifier.height(8.dp))
        AppText(
            "Ordre alphabétique strict, sans tenir compte des accents.",
            style = Typo.small,
            color = Ink.Muted,
        )
    }
}
