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
import androidx.compose.foundation.layout.width
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
import com.botillon.games.ui.Pill
import com.botillon.games.ui.SecondaryButton
import com.botillon.games.ui.Sound
import com.botillon.games.ui.Typo
import kotlinx.coroutines.delay

/** Fan Tan : poser les 7, puis étendre les suites dans les deux sens. */
object FanTanGame {
    val entry = GameEntry(
        id = "fan-tan",
        title = "Fan Tan",
        tagline = "Étendez les suites autour des sept",
        family = Family.CARD,
        accent = Ink.Mustard,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        FanTan(variant, finish)
    }
}

private val RANGS_FAN = listOf("A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "V", "D", "R")
private val SUITS_FAN = listOf("♠", "♥", "♦", "♣")
private fun rangFan(id: Int) = id % 13 + 1
private fun couleurFan(id: Int) = id / 13
private fun nomFan(id: Int) = "${RANGS_FAN[rangFan(id) - 1]}${SUITS_FAN[couleurFan(id)]}"

@Composable
private fun FanTan(variant: Int, finish: (Long, Boolean) -> Unit) {
    val sound = LocalSound.current
    val haptics = LocalHaptics.current
    val depart = remember {
        val plateau = IntArray(52) { -1 }
        for (s in 0..3) plateau[s * 13 + 6] = 0
        val dispo = (0 until 52).filter { plateau[it] == -1 }.shuffled()
        Triple(plateau, dispo.take(8), dispo.drop(8).take(8))
    }
    var plateau by remember { mutableStateOf(depart.first) }
    var mainJ by remember { mutableStateOf(depart.second) }
    var mainIa by remember { mutableStateOf(depart.third) }
    var pointsJ by remember { mutableIntStateOf(0) }
    var pointsIa by remember { mutableIntStateOf(0) }
    var tour by remember { mutableIntStateOf(0) }
    var passes by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("Posez une carte voisine d'une carte déjà sur la table.") }
    var fini by remember { mutableStateOf(false) }
    var reported by remember { mutableStateOf(false) }

    fun posable(id: Int, table: IntArray): Boolean {
        if (table[id] != -1) return false
        val rang = rangFan(id)
        val base = couleurFan(id) * 13
        if (rang > 1 && table[base + rang - 2] != -1) return true
        if (rang < 13 && table[base + rang] != -1) return true
        return false
    }

    fun terminer() {
        if (reported) return
        reported = true
        fini = true
        val gagne = pointsJ > pointsIa
        finish(pointsJ * 20L + if (gagne) 30L else 0L, gagne)
    }

    fun poser(id: Int, joueur: Int) {
        val table = plateau.copyOf()
        table[id] = joueur
        plateau = table
        if (joueur == 1) {
            mainJ = mainJ - id
            pointsJ++
        } else {
            mainIa = mainIa - id
            pointsIa++
        }
        passes = 0
    }

    fun jouerJoueur(id: Int) {
        if (fini || tour != 0) return
        if (!posable(id, plateau)) {
            message = "Cette carte ne touche aucune carte posée."
            sound.play(Sound.LOSE)
            haptics.error()
            return
        }
        poser(id, 1)
        message = "Vous posez ${nomFan(id)}."
        sound.play(Sound.TAP)
        haptics.tap()
        if (mainJ.isEmpty() || mainIa.isEmpty()) terminer() else tour = 1
    }

    fun passer() {
        if (fini || tour != 0) return
        if (mainJ.any { posable(it, plateau) }) {
            message = "Vous avez encore une carte jouable."
            return
        }
        passes++
        message = "Vous passez."
        if (passes >= 2) terminer() else tour = 1
    }

    fun iaJouer() {
        if (fini) return
        val jouables = mainIa.filter { posable(it, plateau) }
        val choix = when (variant) {
            0 -> jouables.randomOrNull()
            1 -> jouables.minByOrNull { rangFan(it) }
            else -> jouables.maxByOrNull { rangFan(it) }
        }
        if (choix != null) {
            poser(choix, 2)
            message = "L'IA pose ${nomFan(choix)}."
            sound.play(Sound.TAP)
            if (mainJ.isEmpty() || mainIa.isEmpty()) {
                terminer()
                return
            }
            tour = 0
        } else {
            passes++
            message = "L'IA passe."
            if (passes >= 2) terminer() else tour = 0
        }
    }

    LaunchedEffect(tour, fini) {
        if (tour == 1 && !fini) {
            delay(if (variant == 2) 450L else 700L)
            iaJouer()
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Vous $pointsJ", color = Ink.Coral, background = Ink.Coral.copy(alpha = 0.12f))
            Pill("IA $pointsIa", color = Ink.Blue, background = Ink.Blue.copy(alpha = 0.12f))
            Pill("Passes $passes")
        }
        Spacer(Modifier.height(10.dp))

        for (couleur in 0..3) {
            Row(
                Modifier.fillMaxWidth().height(25.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText(
                    SUITS_FAN[couleur],
                    style = Typo.small,
                    color = if (couleur == 1 || couleur == 2) Ink.Coral else Ink.Text,
                    modifier = Modifier.width(18.dp),
                )
                for (rang in 1..13) {
                    val id = couleur * 13 + (rang - 1)
                    val proprietaire = plateau[id]
                    Box(
                        Modifier
                            .weight(1f)
                            .padding(1.dp)
                            .height(23.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                when (proprietaire) {
                                    0 -> Ink.Mustard.copy(alpha = 0.35f)
                                    1 -> Ink.Coral
                                    2 -> Ink.Blue
                                    else -> Ink.BgDeep
                                },
                            )
                            .border(1.dp, Ink.Line, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppText(
                            RANGS_FAN[rang - 1],
                            style = Typo.small.copy(fontSize = 8.sp, letterSpacing = 0.sp),
                            color = when (proprietaire) {
                                1, 2 -> Color.White
                                else -> Ink.Muted
                            },
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
        Spacer(Modifier.height(8.dp))

        mainJ.chunked(4).forEach { rangee ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                rangee.forEach { id ->
                    val jouable = posable(id, plateau)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(Ink.CardAlt)
                            .border(1.dp, if (jouable && tour == 0) Ink.Teal else Ink.Line, RoundedCornerShape(9.dp))
                            .clickable(enabled = tour == 0 && !fini) { jouerJoueur(id) },
                        contentAlignment = Alignment.Center,
                    ) {
                        AppText(
                            nomFan(id),
                            style = Typo.body,
                            color = if (rangFan(id) == 1) Ink.Coral else Ink.Text,
                        )
                    }
                }
                repeat(4 - rangee.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(5.dp))
        }

        Spacer(Modifier.weight(1f))
        SecondaryButton(
            label = if (tour == 0 && !fini) "Passer" else "L'IA réfléchit…",
            onClick = { passer() },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        HintBar("Les 7 sont posés. Une carte doit toucher une voisine de sa couleur.")
    }
}
