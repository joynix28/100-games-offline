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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.botillon.games.ui.SecondaryButton
import com.botillon.games.ui.Sound
import com.botillon.games.ui.Typo
import kotlin.math.abs
import kotlin.random.Random

/**
 * Taquin : faire glisser les tuiles pour remettre les nombres dans l'ordre,
 * la case vide terminant en bas à droite.
 *
 * Le mélange part de la solution et n'applique que des déplacements légaux :
 * la position obtenue est donc toujours résoluble.
 */
object SlidingGame {
    val entry = GameEntry(
        id = "taquin",
        title = "Taquin",
        tagline = "Remettre les nombres dans l'ordre",
        family = Family.LOGIC,
        accent = Ink.Teal,
        variants = listOf("3 × 3", "4 × 4", "5 × 5"),
    ) { variant, _, finish, _ ->
        Sliding(variant, finish)
    }
}

/** Solution : 1, 2, 3… puis la case vide (0) en bas à droite. */
private fun solvedBoard(n: Int): IntArray =
    IntArray(n * n) { index -> if (index == n * n - 1) 0 else index + 1 }

/** Indices des cases orthogonalement voisines d'une case. */
private fun neighbours(index: Int, n: Int): List<Int> {
    val row = index / n
    val col = index % n
    val result = mutableListOf<Int>()
    if (row > 0) result += index - n
    if (row < n - 1) result += index + n
    if (col > 0) result += index - 1
    if (col < n - 1) result += index + 1
    return result
}

/**
 * Mélange soluble : on promène la case vide depuis la solution, sans revenir
 * immédiatement en arrière pour bien brasser le plateau.
 */
private fun shuffledBoard(n: Int, steps: Int): Pair<IntArray, Int> {
    val board = solvedBoard(n)
    var blank = n * n - 1
    var previous = -1

    repeat(steps) {
        val options = neighbours(blank, n).filter { it != previous }
        val next = options.random(Random.Default)
        board[blank] = board[next]
        board[next] = 0
        previous = blank
        blank = next
    }

    // Cas rarissime : le hasard ramène la solution. Un petit coup de plus suffit.
    if (board.contentEquals(solvedBoard(n))) {
        val next = neighbours(blank, n).random(Random.Default)
        board[blank] = board[next]
        board[next] = 0
        blank = next
    }

    return board to blank
}

@Composable
private fun Sliding(variant: Int, finish: (Long, Boolean) -> Unit) {
    val n = when (variant) {
        0 -> 3
        1 -> 4
        else -> 5
    }
    val sound = LocalSound.current
    val haptics = LocalHaptics.current

    val opening = remember(n) { shuffledBoard(n, steps = 200) }
    var board by remember(n) { mutableStateOf(opening.first) }
    var blank by remember(n) { mutableIntStateOf(opening.second) }
    var moves by remember(n) { mutableIntStateOf(0) }
    var reported by remember(n) { mutableStateOf(false) }

    val numberStyle = when (n) {
        3 -> Typo.heading.copy(fontSize = 26.sp)
        4 -> Typo.heading.copy(fontSize = 21.sp)
        else -> Typo.heading.copy(fontSize = 17.sp)
    }

    fun move(index: Int) {
        if (reported) return
        val row = index / n
        val col = index % n
        val blankRow = blank / n
        val blankCol = blank % n
        // Seule une tuile collée à la case vide peut glisser.
        if (abs(row - blankRow) + abs(col - blankCol) != 1) return

        val next = board.copyOf()
        next[blank] = next[index]
        next[index] = 0
        board = next
        blank = index
        moves++
        sound.play(Sound.TAP)
        haptics.tap()

        if (next.contentEquals(solvedBoard(n))) {
            reported = true
            val score = (2000L - moves * 8L).coerceAtLeast(100L)
            sound.play(Sound.WIN)
            haptics.success()
            finish(score, true)
        }
    }

    fun reshuffle() {
        if (reported) return
        val (fresh, freshBlank) = shuffledBoard(n, steps = 200)
        board = fresh
        blank = freshBlank
        moves = 0
        sound.play(Sound.UI)
        haptics.tap()
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText("Coups : $moves", style = Typo.heading, color = Ink.Text)
            Pill("Ordre croissant")
        }
        Spacer(Modifier.height(10.dp))

        Panel(Modifier.fillMaxWidth().aspectRatio(1f), color = Ink.CardAlt) {
            Column(
                Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(n) { row ->
                    Row(
                        Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        repeat(n) { col ->
                            val index = row * n + col
                            val value = board[index]
                            if (value == 0) {
                                // La case vide laisse voir le fond du plateau.
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Ink.CardAlt),
                                )
                            } else {
                                val adjacent =
                                    abs(row - blank / n) + abs(col - blank % n) == 1
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Ink.Card)
                                        .border(1.dp, Ink.Line, RoundedCornerShape(12.dp))
                                        .clickable(enabled = adjacent) { move(index) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    AppText("$value", style = numberStyle, color = Ink.Text)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SecondaryButton(
            label = "MÉLANGER",
            onClick = { reshuffle() },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        HintBar("Touchez une tuile voisine de la case vide.")
    }
}
