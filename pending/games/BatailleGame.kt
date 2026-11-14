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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.botillon.games.ui.Panel
import com.botillon.games.ui.Pill
import com.botillon.games.ui.PrimaryButton
import com.botillon.games.ui.Sound
import com.botillon.games.ui.Typo

/** Bataille : chacun retourne une carte, la plus forte rafle la plie. */
object BatailleGame {
    val entry = GameEntry(
        id = "bataille-cartes",
        title = "Bataille",
        tagline = "Le plus fort ramasse, jusqu'à épuisement",
        family = Family.CARD,
        accent = Ink.Coral,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        Bataille(variant, finish)
    }
}

private val RANGS_BAT = listOf("A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "V", "D", "R")
private val SUITS_BAT = listOf("♠", "♥", "♦", "♣")

@Composable
private fun CarteBat(carte: Int?, hidden: Boolean, modifier: Modifier = Modifier) {
    val teinte = when {
        hidden -> Color.White
        carte != null && (carte / 13 == 1 || carte / 13 == 2) -> Ink.Coral
        else -> Ink.Text
    }
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (hidden) Ink.Blue else Ink.Card)
            .border(1.dp, if (hidden) Ink.Blue else Ink.Line, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (hidden) {
            AppText("B", style = Typo.title, color = Color.White.copy(alpha = 0.55f))
        } else if (carte == null) {
            AppText("—", style = Typo.title, color = Ink.Muted)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AppText(RANGS_BAT[carte % 13], style = Typo.heading, color = teinte)
                AppText(SUITS_BAT[carte / 13], style = Typo.title, color = teinte)
            }
        }
    }
}

@Composable
private fun Bataille(variant: Int, finish: (Long, Boolean) -> Unit) {
    val sound = LocalSound.current
    val haptics = LocalHaptics.current
    val depart = remember { (0 until 52).shuffled() }
    val partJoueur = listOf(30, 26, 22)[variant]
    var joueur by remember { mutableStateOf(depart.take(partJoueur)) }
    var ia by remember { mutableStateOf(depart.drop(partJoueur)) }
    var carteJ by remember { mutableStateOf<Int?>(null) }
    var carteIa by remember { mutableStateOf<Int?>(null) }
    var manches by remember { mutableIntStateOf(0) }
    var gagnees by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("Tirez une carte : la plus haute remporte le pli.") }
    var fini by remember { mutableStateOf(false) }
    var reported by remember { mutableStateOf(false) }
    val limite = listOf(60, 90, 120)[variant]

    fun terminer(gagne: Boolean) {
        if (reported) return
        reported = true
        fini = true
        val score = if (gagne) 200L + joueur.size * 5L else gagnees * 10L
        finish(score, gagne)
    }

    fun tirer() {
        if (fini) return
        if (joueur.isEmpty() || ia.isEmpty()) {
            terminer(joueur.isNotEmpty())
            return
        }
        val j = joueur.first()
        val i = ia.first()
        joueur = joueur.drop(1)
        ia = ia.drop(1)
        carteJ = j
        carteIa = i
        manches++
        when {
            j % 13 > i % 13 -> {
                joueur = joueur + listOf(j, i)
                gagnees++
                message = "Votre ${RANGS_BAT[j % 13]} bat ${RANGS_BAT[i % 13]} : pli gagné."
                sound.play(Sound.WIN)
                haptics.success()
            }

            j % 13 < i % 13 -> {
                ia = ia + listOf(i, j)
                message = "L'IA rafle avec ${RANGS_BAT[i % 13]} contre ${RANGS_BAT[j % 13]}."
                sound.play(Sound.LOSE)
                haptics.error()
            }

            else -> {
                message = "Égalité : les deux cartes sont écartées."
                sound.play(Sound.TAP)
                haptics.tap()
            }
        }
        when {
            joueur.isEmpty() -> terminer(false)
            ia.isEmpty() -> terminer(true)
            manches >= limite -> terminer(joueur.size > ia.size)
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Vous ${joueur.size}", color = Ink.Coral, background = Ink.Coral.copy(alpha = 0.12f))
            Pill("IA ${ia.size}", color = Ink.Blue, background = Ink.Blue.copy(alpha = 0.12f))
            Pill("Pli $manches")
        }
        Spacer(Modifier.height(18.dp))
        Panel(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AppText("VOUS", style = Typo.label.copy(letterSpacing = 2.sp), color = Ink.Coral)
                    Spacer(Modifier.height(8.dp))
                    CarteBat(carteJ, false, Modifier.size(width = 86.dp, height = 118.dp))
                }
                AppText("VS", style = Typo.label, color = Ink.Muted)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AppText("IA", style = Typo.label.copy(letterSpacing = 2.sp), color = Ink.Blue)
                    Spacer(Modifier.height(8.dp))
                    CarteBat(carteIa, false, Modifier.size(width = 86.dp, height = 118.dp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) {
                CarteBat(null, true, Modifier.size(width = 26.dp, height = 36.dp))
            }
            Spacer(Modifier.width(4.dp))
            AppText("pioche de l'IA", style = Typo.small, color = Ink.Muted)
        }
        Spacer(Modifier.height(12.dp))
        AppText(
            message,
            style = Typo.body,
            color = Ink.Soft,
            align = TextAlign.Center,
            maxLines = 2,
            softWrap = true,
        )
        Spacer(Modifier.weight(1f))
        PrimaryButton(
            label = if (fini) "Bataille terminée" else "Tirer une carte",
            onClick = { tirer() },
            modifier = Modifier.fillMaxWidth(),
            color = Ink.Coral,
            enabled = !fini,
        )
        Spacer(Modifier.height(12.dp))
        HintBar("La carte la plus haute remporte le pli. Égalité : cartes perdues.")
    }
}
