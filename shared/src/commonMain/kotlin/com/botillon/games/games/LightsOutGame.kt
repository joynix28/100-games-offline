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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.botillon.games.ui.AppText
import com.botillon.games.ui.Family
import com.botillon.games.ui.GameEntry
import com.botillon.games.ui.HintBar
import com.botillon.games.ui.Ink
import com.botillon.games.ui.LocalHaptics
import com.botillon.games.ui.LocalSound
import com.botillon.games.ui.Panel
import com.botillon.games.ui.Pill
import com.botillon.games.ui.SecondaryButton
import com.botillon.games.ui.Sound
import com.botillon.games.ui.Typo
import kotlin.random.Random

/**
 * Éteindre les lumières : chaque appui inverse une case et ses quatre voisines.
 *
 * La grille de départ est obtenue en appuyant sur des cases depuis une grille
 * éteinte : la solution existe donc toujours.
 */
object LightsOutGame {
    val entry = GameEntry(
        id = "lumiere",
        title = "Éteindre les lumières",
        tagline = "Tout éteindre, case par case",
        family = Family.LOGIC,
        accent = Ink.Mustard,
        variants = listOf("3 × 3", "4 × 4", "5 × 5"),
    ) { variant, _, finish, _ ->
        LightsOut(variant, finish)
    }
}

/** Inverse la case touchée puis ses voisines orthogonales. */
private fun pressLight(board: BooleanArray, size: Int, index: Int) {
    val row = index / size
    val col = index % size

    fun flip(r: Int, c: Int) {
        if (r in 0 until size && c in 0 until size) {
            val cell = r * size + c
            board[cell] = !board[cell]
        }
    }

    flip(row, col)
    flip(row - 1, col)
    flip(row + 1, col)
    flip(row, col - 1)
    flip(row, col + 1)
}

/** Mélange soluble : on part de tout éteint et on appuie sur des cases. */
private fun scrambledBoard(size: Int): BooleanArray {
    val random = Random.Default
    val board = BooleanArray(size * size)
    val order = (0 until size * size).shuffled(random)
    val presses = (size * size + 1) / 2
    repeat(presses) { step -> pressLight(board, size, order[step]) }

    // Un motif « silencieux » peut ramener la grille à zéro : un appui de plus
    // la rend de nouveau jouable, et la solution reste garantie.
    if (board.none { it }) pressLight(board, size, order.first())
    return board
}

@Composable
private fun LightsOut(variant: Int, finish: (Long, Boolean) -> Unit) {
    val size = listOf(3, 4, 5)[variant]

    val sound = LocalSound.current
    val haptics = LocalHaptics.current

    var board by remember(variant) { mutableStateOf(scrambledBoard(size)) }
    var moves by remember(variant) { mutableIntStateOf(0) }
    var reported by remember(variant) { mutableStateOf(false) }

    val lit = board.count { it }

    fun onCell(index: Int) {
        if (reported) return

        val next = board.copyOf()
        pressLight(next, size, index)
        board = next
        moves++
        sound.play(Sound.TAP)
        haptics.tap()

        if (next.none { it }) {
            reported = true
            sound.play(Sound.WIN)
            haptics.success()
            finish((900L - moves * 10L).coerceAtLeast(100L), true)
        }
    }

    fun reshuffle() {
        if (reported) return
        board = scrambledBoard(size)
        moves = 0
        sound.play(Sound.UI)
        haptics.tap()
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pill(
                text = "COUPS $moves",
                color = Ink.Mustard,
                background = Ink.Mustard.copy(alpha = 0.16f),
            )
            Pill(text = "LUMIÈRES $lit", color = Ink.Soft)
            Spacer(Modifier.weight(1f))
            AppText("$size × $size", style = Typo.small, color = Ink.Muted)
        }

        Spacer(Modifier.weight(1f))

        Panel(Modifier.fillMaxWidth().aspectRatio(1f), color = Ink.CardAlt) {
            Column(
                Modifier.fillMaxSize().padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(size) { row ->
                    Row(
                        Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        repeat(size) { col ->
                            val index = row * size + col
                            LightCell(
                                lit = board[index],
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                onClick = { onCell(index) },
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        SecondaryButton(
            label = "NOUVELLE GRILLE",
            onClick = { reshuffle() },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(10.dp))

        HintBar(
            text = "Chaque appui inverse la case et ses voisines. Tout éteindre.",
            accent = Ink.Mustard,
        )
    }
}

@Composable
private fun LightCell(
    lit: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val glow by animateFloatAsState(
        targetValue = if (lit) 1f else 0f,
        animationSpec = tween(150),
        label = "lumiere",
    )
    val shape = RoundedCornerShape(14.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(lerp(Ink.Card, Ink.Mustard.copy(alpha = 0.16f), glow))
            .border(1.dp, Ink.Line, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.58f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(lerp(Ink.CardAlt, Ink.Mustard, glow)),
        )
    }
}
