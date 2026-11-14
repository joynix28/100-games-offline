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

/** Baccarat : joueur contre banquier, la main la plus proche de neuf gagne. */
object BaccaratGame {
    val entry = GameEntry(
        id = "baccarat",
        title = "Baccarat",
        tagline = "Joueur ou banquier, viser neuf",
        family = Family.CARD,
        accent = Ink.Teal,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        Baccarat(variant, finish)
    }
}

private val RANGS_BACC = listOf("A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "V", "D", "R")
private val SUITS_BACC = listOf("♠", "♥", "♦", "♣")

private fun valeurBacc(carte: Int): Int {
    val rang = carte % 13 + 1
    return if (rang >= 10) 0 else rang
}

private fun totalBacc(main: List<Int>): Int = main.sumOf { valeurBacc(it) } % 10

@Composable
private fun CarteBacc(carte: Int?, modifier: Modifier = Modifier) {
    val rouge = carte != null && (carte / 13 == 1 || carte / 13 == 2)
    Box(
        modifier
            .clip(RoundedCornerShape(9.dp))
            .background(Ink.Card)
            .border(1.dp, Ink.Line, RoundedCornerShape(9.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (carte == null) {
            AppText("—", style = Typo.body, color = Ink.Muted)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AppText(
                    RANGS_BACC[carte % 13],
                    style = Typo.small.copy(fontSize = 13.sp),
                    color = if (rouge) Ink.Coral else Ink.Text,
                )
                AppText(
                    SUITS_BACC[carte / 13],
                    style = Typo.body,
                    color = if (rouge) Ink.Coral else Ink.Text,
                )
            }
        }
    }
}

@Composable
private fun Baccarat(variant: Int, finish: (Long, Boolean) -> Unit) {
    val totalManches = listOf(5, 8, 12)[variant]
    val sound = LocalSound.current
    val haptics = LocalHaptics.current
    var banque by remember { mutableIntStateOf(100) }
    var manche by remember { mutableIntStateOf(0) }
    var mise by remember { mutableStateOf<Int?>(null) }
    var mainJ by remember { mutableStateOf(emptyList<Int>()) }
    var mainB by remember { mutableStateOf(emptyList<Int>()) }
    var message by remember { mutableStateOf("Choisissez votre camp, puis distribuez.") }
    var fini by remember { mutableStateOf(false) }
    var reported by remember { mutableStateOf(false) }

    fun distribuer() {
        if (reported) return
        val pari = mise
        if (pari == null) {
            message = "Choisissez d'abord joueur ou banquier."
            return
        }
        var paquet = (0 until 52).shuffled()
        fun piocher(): Int {
            val carte = paquet.first()
            paquet = paquet.drop(1)
            return carte
        }

        val joueur = mutableListOf(piocher(), piocher())
        val banquier = mutableListOf(piocher(), piocher())
        if (totalBacc(joueur) <= 5) joueur += piocher()
        if (totalBacc(banquier) <= 5) banquier += piocher()
        mainJ = joueur.toList()
        mainB = banquier.toList()
        val tj = totalBacc(joueur)
        val tb = totalBacc(banquier)
        manche++
        when {
            tj == tb -> {
                message = "Égalité $tj à $tb : votre mise est rendue."
                sound.play(Sound.TAP)
                haptics.tap()
            }

            (tj > tb && pari == 0) || (tb > tj && pari == 1) -> {
                banque += 10
                message = "Gagné ! $tj contre $tb."
                sound.play(Sound.WIN)
                haptics.success()
            }

            else -> {
                banque -= 10
                message = "Perdu : $tj contre $tb."
                sound.play(Sound.LOSE)
                haptics.error()
            }
        }
        if (manche >= totalManches) {
            reported = true
            fini = true
            finish(banque.coerceAtLeast(0).toLong(), banque > 100)
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Solde $banque", color = Ink.Teal, background = Ink.Teal.copy(alpha = 0.12f))
            Pill("Manche $manche / $totalManches")
            Pill("Mise 10")
        }
        Spacer(Modifier.height(14.dp))

        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppText("BANQUIER", style = Typo.label.copy(letterSpacing = 2.sp), color = Ink.Blue)
                    AppText("${totalBacc(mainB)}", style = Typo.heading, color = Ink.Blue)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(3) { index ->
                        CarteBacc(mainB.getOrNull(index), Modifier.size(width = 46.dp, height = 62.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppText("VOUS", style = Typo.label.copy(letterSpacing = 2.sp), color = Ink.Coral)
                    AppText("${totalBacc(mainJ)}", style = Typo.heading, color = Ink.Coral)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(3) { index ->
                        CarteBacc(mainJ.getOrNull(index), Modifier.size(width = 46.dp, height = 62.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("Joueur" to 0, "Banquier" to 1).forEach { (label, index) ->
                val actif = mise == index
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (actif) Ink.Teal else Ink.Card)
                        .border(
                            1.dp,
                            if (actif) Ink.Teal else Ink.Line,
                            RoundedCornerShape(12.dp),
                        )
                        .clickable(enabled = !fini) { mise = index }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AppText(
                        label,
                        style = Typo.button.copy(fontSize = 13.sp),
                        color = if (actif) Color.White else Ink.Soft,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
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
            label = if (fini) "Partie terminée" else "Distribuer",
            onClick = { distribuer() },
            modifier = Modifier.fillMaxWidth(),
            color = Ink.Teal,
            enabled = !fini,
        )
        Spacer(Modifier.height(10.dp))
        HintBar("Le plus proche de 9 gagne. Figures = 0, as = 1, on ignore les dizaines.")
    }
}
