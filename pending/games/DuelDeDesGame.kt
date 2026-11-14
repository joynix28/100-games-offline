package com.botillon.games.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlin.random.Random

/** Duel de dés : deux dés chacun, le plus haut total enlève la manche. */
object DuelDeDesGame {
    val entry = GameEntry(
        id = "duel-de-des",
        title = "Duel de Dés",
        tagline = "Deux dés, premier à cinq manches",
        family = Family.CARD,
        accent = Ink.Blue,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        DuelDeDes(variant, finish)
    }
}

private val PIPS_DUEL = listOf(
    listOf(4),
    listOf(0, 8),
    listOf(0, 4, 8),
    listOf(0, 2, 6, 8),
    listOf(0, 2, 4, 6, 8),
    listOf(0, 2, 3, 5, 6, 8),
)

@Composable
private fun DeDuel(valeur: Int, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val rayon = size.minDimension * 0.16f
        drawRoundRect(color = Ink.Card, cornerRadius = CornerRadius(rayon))
        drawRoundRect(
            color = Ink.Line,
            cornerRadius = CornerRadius(rayon),
            style = Stroke(width = 2f),
        )
        val pas = size.minDimension / 4f
        PIPS_DUEL[valeur - 1].forEach { index ->
            val centre = Offset(
                pas * (1 + (index % 3)),
                pas * (1 + (index / 3)),
            )
            drawCircle(Ink.Text, radius = size.minDimension * 0.065f, center = centre)
        }
    }
}

@Composable
private fun DuelDeDes(variant: Int, finish: (Long, Boolean) -> Unit) {
    val sound = LocalSound.current
    val haptics = LocalHaptics.current
    var desJ by remember { mutableStateOf(listOf(1, 1)) }
    var desIa by remember { mutableStateOf(listOf(1, 1)) }
    var manchesJ by remember { mutableIntStateOf(0) }
    var manchesIa by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("Lancez vos deux dés pour ouvrir le duel.") }
    var fini by remember { mutableStateOf(false) }
    var reported by remember { mutableStateOf(false) }

    fun terminer() {
        if (reported) return
        reported = true
        fini = true
        finish(manchesJ * 100L + if (manchesJ > manchesIa) 50L else 0L, manchesJ > manchesIa)
    }

    fun lancer() {
        if (fini) return
        val j = List(2) { Random.nextInt(1, 7) }
        val ia = List(2) { Random.nextInt(1, 7) }
        desJ = j
        desIa = ia
        val totalJ = j.sum()
        val totalIa = ia.sum()
        when {
            totalJ > totalIa -> {
                manchesJ++
                message = "Vous emportez la manche $totalJ à $totalIa."
                sound.play(Sound.WIN)
                haptics.success()
            }

            totalJ < totalIa -> {
                manchesIa++
                message = "L'IA emporte la manche $totalIa à $totalJ."
                sound.play(Sound.LOSE)
                haptics.error()
            }

            variant == 0 -> {
                manchesJ++
                message = "Égalité $totalJ partout : le joueur est avantagé."
                sound.play(Sound.TAP)
                haptics.tap()
            }

            variant == 2 -> {
                manchesIa++
                message = "Égalité $totalJ partout : l'IA est avantagée."
                sound.play(Sound.LOSE)
                haptics.error()
            }

            else -> {
                message = "Égalité $totalJ partout : personne ne marque."
                sound.play(Sound.TAP)
                haptics.tap()
            }
        }
        if (manchesJ >= 5 || manchesIa >= 5) terminer()
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill("Manches $manchesJ", color = Ink.Blue, background = Ink.Blue.copy(alpha = 0.12f))
            Pill("IA $manchesIa", color = Ink.Coral, background = Ink.Coral.copy(alpha = 0.12f))
            Pill("Objectif 5")
        }
        Spacer(Modifier.height(20.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            listOf("VOUS" to desJ, "IA" to desIa).forEachIndexed { index, (titre, des) ->
                Panel(Modifier.weight(1f)) {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AppText(
                            titre,
                            style = Typo.label.copy(letterSpacing = 2.sp),
                            color = if (index == 0) Ink.Blue else Ink.Coral,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            des.forEach { valeur ->
                                DeDuel(valeur, Modifier.size(46.dp))
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        AppText("${des.sum()}", style = Typo.display, color = Ink.Text)
                    }
                }
            }
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
        PrimaryButton(
            label = if (fini) "Duel terminé" else "Lancer les dés",
            onClick = { lancer() },
            modifier = Modifier.fillMaxWidth(),
            color = Ink.Blue,
            enabled = !fini,
        )
        Spacer(Modifier.height(12.dp))
        HintBar(
            when (variant) {
                0 -> "En cas d'égalité, la manche vous revient."
                2 -> "En cas d'égalité, la manche revient à l'IA."
                else -> "En cas d'égalité, personne ne marque : on relance."
            },
        )
    }
}
