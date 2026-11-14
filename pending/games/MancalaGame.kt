package com.botillon.games.games

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import com.botillon.games.ui.Sound
import com.botillon.games.ui.Typo
import kotlinx.coroutines.delay

/** Mancala : semer des graines, capturer et remplir son grenier. */
object MancalaGame {
    val entry = GameEntry(
        id = "mancala",
        title = "Mancala",
        tagline = "Semer, capturer, remplir son grenier",
        family = Family.BOARD,
        accent = Ink.Mustard,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        Mancala(variant, finish)
    }
}

private val PLAYER_PATH = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
private val AI_PATH = listOf(12, 11, 10, 9, 8, 7, 13, 0, 1, 2, 3, 4, 5)

private fun sow(b: IntArray, pit: Int, player: Int): Int {
    var stones = b[pit]
    b[pit] = 0
    val path = if (player == 0) PLAYER_PATH else AI_PATH
    var i = path.indexOf(pit)
    while (stones > 0) {
        i = (i + 1) % path.size
        b[path[i]]++
        stones--
    }
    return path[i]
}

private fun mancalaScore(b: IntArray, pit: Int, player: Int): Int {
    val c = b.copyOf()
    val last = sow(c, pit, player)
    var score = if (player == 1) c[13] - b[13] else c[6] - b[6]
    val store = if (player == 1) 13 else 6
    if (last == store) score += 5
    if (player == 1 && last in 7..12 && c[last] == 1) score += c[12 - last] + 1
    if (player == 0 && last in 0..5 && c[last] == 1) score += c[12 - last] + 1
    return score
}

@Composable
private fun Mancala(variant: Int, finish: (Long, Boolean) -> Unit) {
    val sound = LocalSound.current
    val haptics = LocalHaptics.current
    var board by remember {
        mutableStateOf(IntArray(14) { i -> if (i == 6 || i == 13) 0 else 4 })
    }
    var turn by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf("À vous : choisissez un trou.") }
    var outcome by remember { mutableIntStateOf(0) }
    var reported by remember { mutableStateOf(false) }

    fun resolveEnd(b: IntArray): Boolean {
        val yours = (0..5).all { b[it] == 0 }
        val theirs = (7..12).all { b[it] == 0 }
        if (!yours && !theirs) return false
        val nb = b.copyOf()
        for (i in 0..5) { nb[6] += nb[i]; nb[i] = 0 }
        for (i in 7..12) { nb[13] += nb[i]; nb[i] = 0 }
        board = nb
        if (!reported) {
            reported = true
            val won = nb[6] > nb[13]
            outcome = if (won) 1 else 2
            msg = "Greniers : vous ${nb[6]} — IA ${nb[13]}"
            if (won) { sound.play(Sound.WIN); haptics.success() } else { sound.play(Sound.LOSE); haptics.error() }
            finish(if (won) nb[6] * 10L else 0L, won)
        }
        return true
    }

    fun playPlayer(pit: Int) {
        if (turn != 0 || reported || board[pit] == 0) return
        val nb = board.copyOf()
        val last = sow(nb, pit, 0)
        var extra = false
        if (last == 6) {
            extra = true
        } else if (last in 0..5 && nb[last] == 1) {
            val opp = 12 - last
            if (nb[opp] > 0) {
                nb[6] += nb[opp] + 1
                nb[opp] = 0
                nb[last] = 0
                sound.play(Sound.MATCH)
            }
        }
        board = nb
        sound.play(if (extra) Sound.POINT else Sound.TAP)
        haptics.tap()
        if (resolveEnd(nb)) return
        if (extra) {
            msg = "Dernière graine chez vous : rejouez !"
        } else {
            turn = 1
            msg = "À l'IA."
        }
    }

    LaunchedEffect(turn, board) {
        if (turn != 1 || reported) return@LaunchedEffect
        delay(620)
        val pits = (7..12).filter { board[it] > 0 }
        if (pits.isEmpty()) { turn = 0; return@LaunchedEffect }
        val best = if (variant == 0) pits.random() else pits.maxByOrNull { mancalaScore(board, it, 1) } ?: pits.first()
        val nb = board.copyOf()
        val last = sow(nb, best, 1)
        if (last != 13 && last in 7..12 && nb[last] == 1) {
            val opp = 12 - last
            if (nb[opp] > 0) {
                nb[13] += nb[opp] + 1
                nb[opp] = 0
                nb[last] = 0
            }
        }
        board = nb
        sound.play(Sound.TAP)
        if (resolveEnd(nb)) return@LaunchedEffect
        if (last == 13) {
            msg = "L'IA a fini dans son grenier : elle rejoue."
        } else {
            turn = 0
            msg = "À vous."
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            AppText("Vous : ${board[6]}", style = Typo.heading, color = Ink.Coral)
            Pill(if (turn == 0) "À vous" else "IA", color = Ink.Mustard)
            AppText("IA : ${board[13]}", style = Typo.heading, color = Ink.Blue)
        }
        Spacer(Modifier.height(10.dp))
        Panel(Modifier.fillMaxWidth().height(270.dp)) {
            Row(Modifier.fillMaxSize().padding(8.dp)) {
                Store(board[13], "IA", Ink.Blue, Modifier.width(56.dp).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 6.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        for (i in 12 downTo 7) {
                            Hole(board[i], false, false) { }
                        }
                    }
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        for (i in 0..5) {
                            Hole(board[i], true, turn == 0 && !reported && board[i] > 0) { playPlayer(i) }
                        }
                    }
                }
                Store(board[6], "Vous", Ink.Coral, Modifier.width(56.dp).fillMaxHeight())
            }
        }
        Spacer(Modifier.height(12.dp))
        AppText(msg, style = Typo.small, color = if (outcome == 0) Ink.Soft else Ink.Muted, maxLines = 2, softWrap = true)
        Spacer(Modifier.height(8.dp))
        HintBar(
            "Dernière graine dans votre grenier = rejouez. Un trou vide en face capture.",
            accent = Ink.Mustard,
        )
    }
}

@Composable
private fun Hole(count: Int, mine: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (mine) Ink.Coral.copy(alpha = if (enabled) 0.18f else 0.08f) else Ink.CardAlt)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AppText("$count", style = Typo.heading, color = if (mine) Ink.Coral else Ink.Soft)
            AppText("•".repeat(count.coerceAtMost(4)), style = Typo.small, color = Ink.Muted)
        }
    }
}

@Composable
private fun Store(count: Int, label: String, color: Color, modifier: Modifier = Modifier) {
    Panel(modifier.padding(vertical = 6.dp), color = color.copy(alpha = 0.08f)) {
        Column(
            Modifier.fillMaxSize().padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AppText(label, style = Typo.label, color = color)
            Spacer(Modifier.height(6.dp))
            AppText("$count", style = Typo.heading, color = color)
        }
    }
}
