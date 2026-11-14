package com.botillon.games.games

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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

/** Pile ou face : une prédiction, un lancer, une série de bons choix. */
object PileOuFaceGame {
    val entry = GameEntry(
        id = "pile-ou-face",
        title = "Pile ou Face",
        tagline = "Devinez la pièce, enchaînez les bons choix",
        family = Family.CARD,
        accent = Ink.Mustard,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        PileOuFace(variant, finish)
    }
}

@Composable
private fun PileOuFace(variant: Int, finish: (Long, Boolean) -> Unit) {
    val tours = listOf(5, 10, 15)[variant]
    val sound = LocalSound.current
    val haptics = LocalHaptics.current
    var choix by remember { mutableStateOf<Int?>(null) }
    var resultat by remember { mutableStateOf<Int?>(null) }
    var lancers by remember { mutableIntStateOf(0) }
    var bons by remember { mutableIntStateOf(0) }
    var serie by remember { mutableIntStateOf(0) }
    var meilleure by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("Choisissez pile ou face, puis lancez.") }
    var enVol by remember { mutableStateOf(false) }
    var angle by remember { mutableFloatStateOf(0f) }
    var reported by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(angle, tween(520), label = "piece")

    fun terminer() {
        if (reported) return
        reported = true
        finish(bons * 10L + meilleure * 5L, bons * 2 >= tours)
    }

    fun lancer() {
        if (enVol || lancers >= tours || choix == null) return
        enVol = true
        resultat = null
        angle += 720f
        sound.play(Sound.FLIP)
        haptics.tap()
    }

    LaunchedEffect(enVol) {
        if (!enVol) return@LaunchedEffect
        delay(540)
        val tirage = Random.nextInt(2)
        resultat = tirage
        if (tirage == choix) {
            bons++
            serie++
            meilleure = maxOf(meilleure, serie)
            message = if (serie > 1) "Bien vu ! Série de $serie." else "Bien vu !"
            sound.play(Sound.WIN)
            haptics.success()
        } else {
            serie = 0
            message = "Raté, c'était ${if (tirage == 0) "pile" else "face"}."
            sound.play(Sound.LOSE)
            haptics.error()
        }
        lancers++
        enVol = false
        if (lancers >= tours) terminer()
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("$lancers / $tours")
            Pill("Bons $bons", color = Ink.Teal, background = Ink.Teal.copy(alpha = 0.12f))
            Pill("Série $serie", color = Ink.Coral, background = Ink.Coral.copy(alpha = 0.12f))
        }
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier
                .size(164.dp)
                .graphicsLayer {
                    rotationY = rotation
                    cameraDistance = 12f * density
                }
                .clip(CircleShape)
                .background(
                    when (resultat) {
                        0 -> Ink.Mustard
                        1 -> Ink.CardAlt
                        else -> Ink.Card
                    },
                )
                .border(1.dp, Ink.Line, CircleShape)
                .clickable(enabled = !enVol && lancers < tours) { lancer() },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AppText(
                    text = if (enVol) "…" else when (resultat) {
                        0 -> "PILE"
                        1 -> "FACE"
                        else -> "?"
                    },
                    style = Typo.display.copy(fontSize = 30.sp),
                    color = Ink.Text,
                )
                AppText(
                    text = if (enVol) "la pièce tourne" else "touchez pour lancer",
                    style = Typo.small,
                    color = Ink.Muted,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        AppText(
            message,
            style = Typo.body,
            color = Ink.Soft,
            align = TextAlign.Center,
            maxLines = 2,
            softWrap = true,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Pile", "Face").forEachIndexed { index, label ->
                val actif = choix == index
                Box(
                    Modifier
                        .width(118.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (actif) Ink.Mustard else Ink.Card)
                        .border(
                            1.dp,
                            if (actif) Ink.Mustard else Ink.Line,
                            RoundedCornerShape(14.dp),
                        )
                        .clickable(enabled = !enVol && lancers < tours) {
                            choix = index
                            message = "Prêt pour le lancer."
                        }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AppText(label, style = Typo.button, color = if (actif) Color.White else Ink.Soft)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            label = when {
                enVol -> "La pièce tourne…"
                lancers >= tours -> "Partie terminée"
                choix == null -> "Choisissez un côté"
                else -> "Lancer la pièce"
            },
            onClick = { lancer() },
            modifier = Modifier.fillMaxWidth(),
            color = Ink.Mustard,
            enabled = !enVol && choix != null && lancers < tours,
        )
        Spacer(Modifier.height(12.dp))
        HintBar("Le score récompense les bonnes prédictions et la meilleure série.")
    }
}
