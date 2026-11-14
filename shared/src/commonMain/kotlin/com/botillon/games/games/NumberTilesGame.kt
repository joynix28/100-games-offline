package com.botillon.games.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import kotlin.random.Random

/**
 * Chiffres en ordre : une grille de nombres mélangés, à toucher de 1 jusqu'au
 * dernier. Le chronomètre défile au dixième et chaque erreur coûte 2 secondes.
 * Retoucher un nombre déjà validé est sans effet, pour éviter les fausses
 * pénalités sur des cases volontairement estompées.
 */
object NumberTilesGame {
    val entry = GameEntry(
        id = "chiffres",
        title = "Chiffres en ordre",
        tagline = "Toucher les nombres dans l'ordre",
        family = Family.WORDS,
        accent = Ink.Violet,
        variants = listOf("3 × 3", "4 × 4", "5 × 5"),
    ) { variant, _, finish, _ ->
        NumberTiles(variant, finish)
    }
}

/** Chronomètre au dixième : « 12,3 s » ou « 1:05,7 s ». */
private fun numberTilesClock(milliseconds: Long): String {
    val totalTenths = (milliseconds / 100L).coerceAtLeast(0L)
    val minutes = totalTenths / 600L
    val seconds = (totalTenths / 10L) % 60L
    val tenth = totalTenths % 10L
    return if (minutes > 0L) {
        "$minutes:${seconds.toString().padStart(2, '0')},$tenth s"
    } else {
        "$seconds,$tenth s"
    }
}

@Composable
private fun NumberTiles(variant: Int, finish: (Long, Boolean) -> Unit) {
    val size = listOf(3, 4, 5)[variant]
    val total = size * size
    val sound = LocalSound.current
    val haptics = LocalHaptics.current

    // Ordre mélangé, stable pendant toute la partie.
    val tiles = remember(size) { (1..total).shuffled(Random.Default) }
    var next by remember { mutableIntStateOf(1) }
    var elapsedMs by remember { mutableStateOf(0L) }
    var penaltyMs by remember { mutableStateOf(0L) }
    var finished by remember { mutableStateOf(false) }
    var reported by remember { mutableStateOf(false) }

    // Le temps vient de l'horloge des frames : pas de dérive cumulative.
    LaunchedEffect(tiles) {
        var start = 0L
        while (!finished) {
            withFrameNanos { now ->
                if (start == 0L) start = now
                val ms = (now - start) / 1_000_000L
                // On ne recompose que lorsque le dixième change.
                if (ms / 100L != elapsedMs / 100L) elapsedMs = ms
            }
        }
    }

    fun tap(value: Int) {
        if (finished) return
        if (value < next) return // nombre déjà validé : aucune pénalité
        if (value == next) {
            sound.play(Sound.TAP)
            haptics.tap()
            if (next == total) {
                finished = true
                if (!reported) {
                    reported = true
                    val totalMs = elapsedMs + penaltyMs
                    sound.play(Sound.WIN)
                    haptics.success()
                    finish(maxOf(100L, 3000L - totalMs / 10L), true)
                }
            } else {
                next += 1
            }
        } else {
            penaltyMs += 2000L
            sound.play(Sound.LOSE)
            haptics.error()
        }
    }

    val totalMs = elapsedMs + penaltyMs

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Panel(Modifier.weight(1f).height(96.dp)) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AppText("À TOUCHER", style = Typo.label, color = Ink.Muted)
                    Spacer(Modifier.height(2.dp))
                    AppText("$next", style = Typo.display, color = Ink.Violet)
                }
            }
            Panel(Modifier.weight(1f).height(96.dp)) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AppText("TEMPS", style = Typo.label, color = Ink.Muted)
                    Spacer(Modifier.height(2.dp))
                    AppText(numberTilesClock(totalMs), style = Typo.heading, color = Ink.Text)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pill(
                text = "TROUVÉS ${next - 1} / $total",
                color = Ink.Violet,
                background = Ink.Violet.copy(alpha = 0.10f),
            )
            Spacer(Modifier.weight(1f))
            if (penaltyMs > 0L) {
                Pill(
                    text = "PÉNALITÉ +${penaltyMs / 1000L} s",
                    color = Ink.Coral,
                    background = Ink.Coral.copy(alpha = 0.12f),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Box(
            Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Panel(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                color = Ink.CardAlt,
            ) {
                Column(Modifier.fillMaxSize().padding(5.dp)) {
                    for (row in 0 until size) {
                        Row(Modifier.weight(1f).fillMaxWidth()) {
                            for (col in 0 until size) {
                                val value = tiles[row * size + col]
                                NumberTile(
                                    value = value,
                                    done = value < next,
                                    enabled = !finished,
                                    big = size <= 4,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .padding(3.dp),
                                    onClick = { tap(value) },
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        HintBar(
            "Touchez les nombres dans l'ordre, le plus vite possible.",
            accent = Ink.Violet,
        )
    }
}

@Composable
private fun NumberTile(
    value: Int,
    done: Boolean,
    enabled: Boolean,
    big: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .clip(shape)
            .background(if (done) Ink.CardAlt else Ink.Card)
            .border(1.dp, if (done) Color.Transparent else Ink.Line, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AppText(
            text = "$value",
            style = Typo.heading.copy(fontSize = if (big) 26.sp else 20.sp),
            color = if (done) Ink.Muted.copy(alpha = 0.45f) else Ink.Text,
        )
    }
}
