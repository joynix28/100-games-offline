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
import androidx.compose.runtime.LaunchedEffect
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
import com.botillon.games.ui.Panel
import com.botillon.games.ui.Pill
import com.botillon.games.ui.Sound
import com.botillon.games.ui.Typo
import kotlinx.coroutines.delay

/** Go Fish : demander une valeur à l'IA et réunir des paires. */
object GoFishGame {
    val entry = GameEntry(
        id = "go-fish",
        title = "Go Fish",
        tagline = "Demandez une valeur, réunissez les paires",
        family = Family.CARD,
        accent = Ink.Blue,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        GoFish(variant, finish)
    }
}

private val RANGS_GF = listOf("A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "V", "D", "R")
private fun nomRangGF(rang: Int) = RANGS_GF[rang - 1]

private fun retirerPairesGF(main: List<Int>): Pair<List<Int>, Int> {
    val reste = mutableListOf<Int>()
    var paires = 0
    main.groupBy { it }.forEach { (_, cartes) ->
        paires += cartes.size / 2
        if (cartes.size % 2 == 1) reste += cartes.first()
    }
    return reste.sorted() to paires
}

private data class DonneGF(
    val mainJ: List<Int>,
    val mainIa: List<Int>,
    val pioche: List<Int>,
    val pairesJ: Int,
    val pairesIa: Int,
)

@Composable
private fun GoFish(variant: Int, finish: (Long, Boolean) -> Unit) {
    val donne = remember {
        val paquet = (1..13).flatMap { rang -> List(4) { rang } }.shuffled()
        val (j, pj) = retirerPairesGF(paquet.take(7))
        val (i, pi) = retirerPairesGF(paquet.drop(7).take(7))
        DonneGF(j, i, paquet.drop(14), pj, pi)
    }
    val sound = LocalSound.current
    val haptics = LocalHaptics.current
    var mainJ by remember { mutableStateOf(donne.mainJ) }
    var mainIa by remember { mutableStateOf(donne.mainIa) }
    var pioche by remember { mutableStateOf(donne.pioche) }
    var pairesJ by remember { mutableIntStateOf(donne.pairesJ) }
    var pairesIa by remember { mutableIntStateOf(donne.pairesIa) }
    var tour by remember { mutableIntStateOf(0) }
    var coups by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("Touchez une carte de votre main pour la demander.") }
    var fini by remember { mutableStateOf(false) }
    var reported by remember { mutableStateOf(false) }
    val patience = if (variant == 2) 500L else 850L

    fun terminer() {
        if (reported) return
        reported = true
        fini = true
        val gagne = pairesJ > pairesIa
        finish(pairesJ * 100L + if (gagne) 50L else 0L, gagne)
    }

    fun verifierFin() {
        if (fini) return
        if (coups >= 40 || pioche.isEmpty() || mainJ.isEmpty() || mainIa.isEmpty()) terminer()
    }

    fun demander(rang: Int) {
        if (fini || tour != 0 || rang !in mainJ) return
        coups++
        val chezIa = mainIa.count { it == rang }
        if (chezIa > 0) {
            mainIa = mainIa.filter { it != rang }
            mainJ = mainJ + List(chezIa) { rang }
            val (reste, gagnees) = retirerPairesGF(mainJ)
            mainJ = reste
            pairesJ += gagnees
            message = if (gagnees > 0) {
                "L'IA avait $chezIa ${nomRangGF(rang)} : paire complétée !"
            } else {
                "L'IA avait $chezIa ${nomRangGF(rang)}."
            }
            sound.play(Sound.MATCH)
            haptics.success()
        } else {
            message = "Pêche ! L'IA n'a pas de ${nomRangGF(rang)}."
            if (pioche.isNotEmpty()) {
                val prise = pioche.first()
                pioche = pioche.drop(1)
                mainJ = mainJ + prise
                val (reste, gagnees) = retirerPairesGF(mainJ)
                mainJ = reste
                pairesJ += gagnees
                if (gagnees > 0) {
                    message = "Pêche miraculeuse : paire de ${nomRangGF(prise)} !"
                    sound.play(Sound.MATCH)
                    haptics.success()
                } else {
                    sound.play(Sound.FLIP)
                    haptics.tap()
                }
            } else {
                sound.play(Sound.TAP)
                haptics.tap()
            }
        }
        verifierFin()
        if (!fini) tour = 1
    }

    fun iaDemande() {
        if (fini) return
        val rang = mainIa.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        if (rang == null) {
            verifierFin()
            return
        }
        coups++
        val chezJ = mainJ.count { it == rang }
        if (chezJ > 0) {
            mainJ = mainJ.filter { it != rang }
            mainIa = mainIa + List(chezJ) { rang }
            val (reste, gagnees) = retirerPairesGF(mainIa)
            mainIa = reste
            pairesIa += gagnees
            message = "L'IA demande des ${nomRangGF(rang)} : elle en prend $chezJ."
            sound.play(Sound.LOSE)
            haptics.error()
        } else {
            message = "L'IA demande des ${nomRangGF(rang)}. Pêche !"
            if (pioche.isNotEmpty()) {
                val prise = pioche.first()
                pioche = pioche.drop(1)
                mainIa = mainIa + prise
                val (reste, gagnees) = retirerPairesGF(mainIa)
                mainIa = reste
                pairesIa += gagnees
            }
        }
        verifierFin()
        if (!fini) tour = 0
    }

    LaunchedEffect(tour, fini) {
        if (tour == 1 && !fini) {
            delay(patience)
            iaDemande()
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Pioche ${pioche.size}")
            Pill("Vos paires $pairesJ", color = Ink.Teal, background = Ink.Teal.copy(alpha = 0.12f))
            Pill("IA $pairesIa", color = Ink.Coral, background = Ink.Coral.copy(alpha = 0.12f))
        }
        Spacer(Modifier.height(12.dp))

        Panel(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    AppText("L'IA", style = Typo.label.copy(letterSpacing = 2.sp), color = Ink.Blue)
                    Spacer(Modifier.height(4.dp))
                    AppText("${mainIa.size} cartes en main", style = Typo.small, color = Ink.Muted)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(minOf(mainIa.size, 8)) {
                        Box(
                            Modifier
                                .size(width = 18.dp, height = 26.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Ink.Blue),
                        )
                    }
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
        Spacer(Modifier.height(10.dp))

        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                AppText("VOTRE MAIN", style = Typo.label.copy(letterSpacing = 2.sp), color = Ink.Teal)
                Spacer(Modifier.height(8.dp))
                mainJ.chunked(5).forEach { rangee ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        rangee.forEach { rang ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(Ink.CardAlt)
                                    .border(1.dp, Ink.Line, RoundedCornerShape(9.dp))
                                    .clickable(enabled = tour == 0 && !fini) { demander(rang) },
                                contentAlignment = Alignment.Center,
                            ) {
                                AppText(
                                    nomRangGF(rang),
                                    style = Typo.heading,
                                    color = if (rang == 1 || rang == 11 || rang == 12 || rang == 13) Ink.Coral else Ink.Text,
                                )
                            }
                        }
                        repeat(5 - rangee.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(5.dp))
                }
            }
        }
        Spacer(Modifier.weight(1f))
        AppText("Vous demandez la valeur de votre choix à l'IA.", style = Typo.small, color = Ink.Muted)
        Spacer(Modifier.height(8.dp))
        HintBar("Une paire se retire automatiquement et rapporte un point.")
    }
}
