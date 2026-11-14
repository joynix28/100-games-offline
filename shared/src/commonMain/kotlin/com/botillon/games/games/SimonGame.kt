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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlin.random.Random

/**
 * Simon : une suite de couleurs s'allume, puis le joueur la reproduit.
 * Chaque réussite allonge la suite ; une erreur met fin à la partie.
 * La partie n'a pas de fin heureuse : elle s'arrête seulement sur une faute.
 */
object SimonGame {
    val entry = GameEntry(
        id = "simon",
        title = "Simon",
        tagline = "Reproduire la suite de couleurs",
        family = Family.ARCADE,
        accent = Ink.Violet,
        variants = listOf("Lent", "Moyen", "Rapide"),
    ) { variant, best, finish, _ ->
        Simon(variant, best, finish)
    }
}

/** Ordre des pads : corail, bleu, jaune, vert. */
private val PAD_COLORS = listOf(Ink.Coral, Ink.Blue, Ink.Mustard, Ink.Green)
private val PAD_SYMBOLS = listOf("●", "◆", "▲", "★")

@Composable
private fun Simon(variant: Int, best: Long, finish: (Long, Boolean) -> Unit) {
    val stepMs = when (variant) {
        0 -> 700L
        1 -> 480L
        else -> 300L
    }
    val litMs = (stepMs * 6L) / 10L
    val darkMs = stepMs - litMs

    val sound = LocalSound.current
    val haptics = LocalHaptics.current

    var sequence by remember { mutableStateOf(listOf(Random.nextInt(PAD_COLORS.size))) }
    var round by remember { mutableIntStateOf(0) }
    var inputIndex by remember { mutableIntStateOf(0) }
    var showingSequence by remember { mutableStateOf(true) }
    var litPad by remember { mutableIntStateOf(-1) }
    var reported by remember { mutableStateOf(false) }

    // Une seule démonstration par séquence : la clé (round, sequence) ne change
    // qu'après une réussite ou un redémarrage volontaire.
    LaunchedEffect(round, sequence) {
        val toShow = sequence
        showingSequence = true
        inputIndex = 0
        litPad = -1
        delay(350)
        for (pad in toShow) {
            litPad = pad
            sound.play(Sound.POINT)
            delay(litMs)
            litPad = -1
            delay(darkMs)
        }
        delay(150)
        showingSequence = false
    }

    fun restart() {
        if (reported) return
        sequence = listOf(Random.nextInt(PAD_COLORS.size))
        round++
        inputIndex = 0
        litPad = -1
        showingSequence = true
        sound.play(Sound.UI)
        haptics.tap()
    }

    fun tapPad(pad: Int) {
        // Pendant la démonstration, les taps sont ignorés : pas de double saisie.
        if (reported || showingSequence) return
        if (inputIndex >= sequence.size) return

        if (pad != sequence[inputIndex]) {
            reported = true
            showingSequence = true
            litPad = -1
            val reached = (sequence.size - 1).coerceAtLeast(0)
            sound.play(Sound.LOSE)
            haptics.error()
            finish(reached * 10L, false)
            return
        }

        sound.play(Sound.TAP)
        haptics.tap()
        inputIndex++
        if (inputIndex == sequence.size) {
            // Série complète : on allonge la suite et on repasse la main à la démo.
            showingSequence = true
            sound.play(Sound.MATCH)
            haptics.success()
            sequence = sequence + Random.nextInt(PAD_COLORS.size)
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText("Séquence : ${sequence.size}", style = Typo.heading, color = Ink.Text)
            AppText("Meilleur : $best", style = Typo.small, color = Ink.Muted)
        }
        Spacer(Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Pill(
                text = if (showingSequence) "DÉMO" else "À VOUS",
                color = if (showingSequence) Ink.Violet else Ink.Teal,
                background = if (showingSequence) {
                    Ink.Violet.copy(alpha = 0.12f)
                } else {
                    Ink.Teal.copy(alpha = 0.12f)
                },
            )
            AppText(
                text = if (showingSequence) "Regardez bien…" else "Reproduisez la suite.",
                style = Typo.small,
                color = Ink.Soft,
            )
        }
        Spacer(Modifier.height(10.dp))

        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                repeat(2) { row ->
                    Row(
                        Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        repeat(2) { col ->
                            val pad = row * 2 + col
                            SimonPad(
                                color = PAD_COLORS[pad],
                                symbol = PAD_SYMBOLS[pad],
                                lit = litPad == pad,
                                enabled = !showingSequence && !reported,
                                onClick = { tapPad(pad) },
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        HintBar("Regardez la séquence, puis reproduisez-la.")
        Spacer(Modifier.height(10.dp))
        SecondaryButton(
            label = "RECOMMENCER",
            onClick = { restart() },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RowScope.SimonPad(
    color: Color,
    symbol: String,
    lit: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val glow by animateFloatAsState(
        targetValue = if (lit) 1f else 0f,
        animationSpec = tween(90),
        label = "pad",
    )
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .graphicsLayer {
                scaleX = 0.98f + glow * 0.02f
                scaleY = 0.98f + glow * 0.02f
            }
            .clip(RoundedCornerShape(24.dp))
            .background(color.copy(alpha = 0.18f + 0.82f * glow))
            .border(
                width = 2.dp,
                color = color.copy(alpha = 0.45f + 0.55f * glow),
                shape = RoundedCornerShape(24.dp),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AppText(
            text = symbol,
            style = Typo.big.copy(fontSize = 26.sp),
            color = when {
                lit && color == Ink.Mustard -> Ink.Text
                lit -> Color.White
                else -> color.copy(alpha = 0.8f)
            },
        )
    }
}
