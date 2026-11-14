package com.botillon.games.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.botillon.games.ui.AppText
import com.botillon.games.ui.Family
import com.botillon.games.ui.GameEntry
import com.botillon.games.ui.HintBar
import com.botillon.games.ui.Ink
import com.botillon.games.ui.LocalHaptics
import com.botillon.games.ui.LocalSound
import com.botillon.games.ui.Pill
import com.botillon.games.ui.PrimaryButton
import com.botillon.games.ui.Sound
import com.botillon.games.ui.Typo

/** Plus ou moins : une carte s'affiche, il faut annoncer la suivante. */
object PlusOuMoinsGame {
    val entry = GameEntry(
        id = "plus-ou-moins",
        title = "Plus ou Moins",
        tagline = "Devinez si la carte suivante monte ou descend",
        family = Family.CARD,
        accent = Ink.Green,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        PlusOuMoins(variant, finish)
    }
}

private val RANGS_PM = listOf("A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "V", "D", "R")
private val SUITS_PM = listOf("♠", "♥", "♦", "♣")

private fun rougePM(carte: Int) = carte / 13 == 1 || carte / 13 == 2

@Composable
private fun CartePM(carte: Int, modifier: Modifier = Modifier) {
    val teinte = if (rougePM(carte)) Ink.Coral else Ink.Text
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Ink.Card)
            .border(1.dp, Ink.Line, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AppText(RANGS_PM[carte % 13], style = Typo.heading, color = teinte)
            AppText(SUITS_PM[carte / 13], style = Typo.display.copy(fontSize = 34.sp), color = teinte)
        }
    }
}

@Composable
private fun PlusOuMoins(variant: Int, finish: (Long, Boolean) -> Unit) {
    val tours = listOf(5, 10, 15)[variant]
    val sound = LocalSound.current
    val haptics = LocalHaptics.current
    val paquet = remember { (0 until 52).shuffled() }
    var courante by remember { mutableStateOf(paquet.first()) }
    var pioche by remember { mutableStateOf(paquet.drop(1)) }
    var essais by remember { mutableIntStateOf(0) }
    var bons by remember { mutableIntStateOf(0) }
    var serie by remember { mutableIntStateOf(0) }
    var meilleure by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("La suivante sera-t-elle plus haute ou plus basse ?") }
    var fini by remember { mutableStateOf(false) }
    var reported by remember { mutableStateOf(false) }

    fun terminer() {
        if (reported) return
        reported = true
        fini = true
        finish(bons * 10L + meilleure * 5L, bons * 2 >= tours)
    }

    fun deviner(plusHaute: Boolean) {
        if (fini) return
        val suite = if (pioche.isEmpty()) (0 until 52).shuffled() else pioche
        val suivante = suite.first()
        pioche = suite.drop(1)
        val bonne = if (plusHaute) {
            suivante % 13 > courante % 13
        } else {
            suivante % 13 < courante % 13
        }
        if (bonne) {
            bons++
            serie++
            meilleure = maxOf(meilleure, serie)
            message = "Bien joué : série de $serie."
            sound.play(Sound.WIN)
            haptics.success()
        } else {
            serie = 0
            message = if (suivante % 13 == courante % 13) {
                "Égalité : la manche est perdue."
            } else {
                "Raté, elle ${if (plusHaute) "descendait" else "montait"}."
            }
            sound.play(Sound.LOSE)
            haptics.error()
        }
        essais++
        courante = suivante
        if (essais >= tours) terminer()
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Carte ${essais + 1} / $tours")
            Pill("Bons $bons", color = Ink.Teal, background = Ink.Teal.copy(alpha = 0.12f))
            Pill("Série $serie", color = Ink.Coral, background = Ink.Coral.copy(alpha = 0.12f))
        }
        Spacer(Modifier.height(18.dp))
        CartePM(courante, Modifier.size(width = 108.dp, height = 142.dp))
        Spacer(Modifier.height(16.dp))
        AppText("La carte suivante sera…", style = Typo.body, color = Ink.Soft)
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PrimaryButton(
                label = "Plus haute",
                onClick = { deviner(true) },
                modifier = Modifier.weight(1f),
                color = Ink.Green,
                enabled = !fini,
            )
            PrimaryButton(
                label = "Plus basse",
                onClick = { deviner(false) },
                modifier = Modifier.weight(1f),
                color = Ink.Coral,
                enabled = !fini,
            )
        }
        Spacer(Modifier.height(14.dp))
        AppText(
            message,
            style = Typo.body,
            color = Ink.Soft,
            align = TextAlign.Center,
            maxLines = 2,
            softWrap = true,
        )
        Spacer(Modifier.weight(1f))
        HintBar("A vaut 1, R vaut 13. Une carte égale fait perdre la manche.")
    }
}
