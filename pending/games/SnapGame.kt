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
import com.botillon.games.ui.Pill
import com.botillon.games.ui.PrimaryButton
import com.botillon.games.ui.Sound
import com.botillon.games.ui.Typo
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Snap : les cartes défilent, il faut taper sur les valeurs identiques. */
object SnapGame {
    val entry = GameEntry(
        id = "snap",
        title = "Snap",
        tagline = "Tapez vite quand deux valeurs se suivent",
        family = Family.CARD,
        accent = Ink.Coral,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        Snap(variant, finish)
    }
}

private val RANGS_SNAP = listOf("A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "V", "D", "R")
private val SUITS_SNAP = listOf("♠", "♥", "♦", "♣")

@Composable
private fun CarteSnap(carte: Int, modifier: Modifier = Modifier) {
    val rouge = carte / 13 == 1 || carte / 13 == 2
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Ink.Card)
            .border(1.dp, Ink.Line, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AppText(
                RANGS_SNAP[carte % 13],
                style = Typo.display.copy(fontSize = 38.sp),
                color = if (rouge) Ink.Coral else Ink.Text,
            )
            AppText(
                SUITS_SNAP[carte / 13],
                style = Typo.title,
                color = if (rouge) Ink.Coral else Ink.Text,
            )
        }
    }
}

@Composable
private fun Snap(variant: Int, finish: (Long, Boolean) -> Unit) {
    val intervalle = listOf(1200L, 900L, 680L)[variant]
    val nbCartes = listOf(12, 18, 26)[variant]
    val sound = LocalSound.current
    val haptics = LocalHaptics.current
    val cartes = remember {
        val liste = mutableListOf<Int>()
        var precedent = 0
        repeat(nbCartes) { index ->
            val rang = if (index > 0 && index % 3 == 0) precedent else Random.nextInt(13)
            liste += Random.nextInt(4) * 13 + rang
            precedent = rang
        }
        liste
    }
    var revelees by remember { mutableIntStateOf(2) }
    var score by remember { mutableIntStateOf(0) }
    var dernierSnap by remember { mutableIntStateOf(-1) }
    var message by remember { mutableStateOf("Touchez SNAP dès que deux valeurs se suivent.") }
    var enCours by remember { mutableStateOf(true) }
    var fini by remember { mutableStateOf(false) }
    var reported by remember { mutableStateOf(false) }

    fun terminer() {
        if (reported) return
        reported = true
        fini = true
        finish(score.toLong(), score >= 30)
    }

    LaunchedEffect(Unit) {
        while (enCours) {
            delay(intervalle)
            if (revelees < cartes.size) revelees++ else enCours = false
        }
        terminer()
    }

    fun taper() {
        if (fini || !enCours) return
        val avant = cartes[revelees - 2]
        val apres = cartes[revelees - 1]
        if (avant % 13 == apres % 13) {
            if (dernierSnap == revelees - 1) {
                message = "Déjà compté, attendez la suite."
                return
            }
            score += 10
            dernierSnap = revelees - 1
            message = "Snap ! +10 points."
            sound.play(Sound.MATCH)
            haptics.success()
        } else {
            score = (score - 5).coerceAtLeast(0)
            message = "Raté : les valeurs sont différentes."
            sound.play(Sound.LOSE)
            haptics.error()
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Carte $revelees / ${cartes.size}")
            Pill("Score $score", color = Ink.Teal, background = Ink.Teal.copy(alpha = 0.12f))
        }
        Spacer(Modifier.height(18.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CarteSnap(cartes[revelees - 2], Modifier.size(width = 118.dp, height = 156.dp))
            CarteSnap(cartes[revelees - 1], Modifier.size(width = 118.dp, height = 156.dp))
        }
        Spacer(Modifier.height(12.dp))
        AppText("Même valeur ?", style = Typo.small, color = Ink.Muted)
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
            label = if (fini) "Partie terminée" else "SNAP !",
            onClick = { taper() },
            modifier = Modifier.fillMaxWidth(),
            color = Ink.Coral,
            enabled = !fini,
        )
        Spacer(Modifier.height(10.dp))
        HintBar("Un bon snap rapporte 10 points, une erreur en coûte 5.")
    }
}
