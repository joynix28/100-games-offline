package com.botillon.games.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.botillon.games.ui.AppText
import com.botillon.games.ui.Family
import com.botillon.games.ui.GameEntry
import com.botillon.games.ui.HintBar
import com.botillon.games.ui.Ink
import com.botillon.games.ui.LocalHaptics
import com.botillon.games.ui.LocalSound
import com.botillon.games.ui.Pill
import com.botillon.games.ui.Sound
import com.botillon.games.ui.Typo
import kotlinx.coroutines.delay

/** Reversi : l'IA choisit le coup qui retourne le plus de pièces. */
object ReversiGame {
    val entry = GameEntry(
        id = "reversi",
        title = "Reversi",
        tagline = "Retourner un maximum de pièces",
        family = Family.BOARD,
        accent = Ink.Green,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        Reversi(variant, finish)
    }
}

private val REVERSI_DIRS = listOf(-1 to -1, -1 to 0, -1 to 1, 0 to -1, 0 to 1, 1 to -1, 1 to 0, 1 to 1)

private fun reversiFlips(b: IntArray, idx: Int, player: Int): List<Int> {
    if (b[idx] != 0) return emptyList()
    val opp = 3 - player
    val x0 = idx % 8
    val y0 = idx / 8
    val out = mutableListOf<Int>()
    for ((dx, dy) in REVERSI_DIRS) {
        val line = mutableListOf<Int>()
        var x = x0 + dx
        var y = y0 + dy
        while (x in 0..7 && y in 0..7 && b[y * 8 + x] == opp) {
            line += y * 8 + x
            x += dx
            y += dy
        }
        if (line.isNotEmpty() && x in 0..7 && y in 0..7 && b[y * 8 + x] == player) out += line
    }
    return out
}

private fun reversiMoves(b: IntArray, player: Int): List<Int> =
    b.indices.filter { reversiFlips(b, it, player).isNotEmpty() }

private fun reversiPlay(b: IntArray, idx: Int, player: Int): IntArray {
    val nb = b.copyOf()
    nb[idx] = player
    for (i in reversiFlips(b, idx, player)) nb[i] = player
    return nb
}

private fun reversiStart(): IntArray {
    val b = IntArray(64)
    b[3 * 8 + 3] = 1
    b[4 * 8 + 4] = 1
    b[3 * 8 + 4] = 2
    b[4 * 8 + 3] = 2
    return b
}

@Composable
private fun Reversi(variant: Int, finish: (Long, Boolean) -> Unit) {
    val sound = LocalSound.current
    val haptics = LocalHaptics.current
    var board by remember { mutableStateOf(reversiStart()) }
    var turn by remember { mutableIntStateOf(1) }
    var msg by remember { mutableStateOf("À vous. Les points indiquent vos coups.") }
    var outcome by remember { mutableIntStateOf(0) }
    var reported by remember { mutableStateOf(false) }

    fun endGame(b: IntArray) {
        if (reported) return
        reported = true
        val mine = b.count { it == 1 }
        val ai = b.count { it == 2 }
        outcome = if (mine > ai) 1 else 2
        msg = "Vous $mine — IA $ai"
        if (mine > ai) { sound.play(Sound.WIN); haptics.success() } else { sound.play(Sound.LOSE); haptics.error() }
        finish(if (mine > ai) mine * 10L else 0L, mine > ai)
    }

    LaunchedEffect(turn, board) {
        if (turn != 2 || reported) return@LaunchedEffect
        delay(520)
        val moves = reversiMoves(board, 2)
        if (moves.isEmpty()) {
            if (reversiMoves(board, 1).isEmpty()) endGame(board) else { turn = 1; msg = "L'IA passe son tour." }
            return@LaunchedEffect
        }
        val corners = setOf(0, 7, 56, 63)
        val best = moves.maxByOrNull { m -> reversiFlips(board, m, 2).size * 2 + if (m in corners) 8 else 0 } ?: moves.first()
        val nb = reversiPlay(board, best, 2)
        board = nb
        sound.play(Sound.TAP)
        if (reversiMoves(nb, 1).isEmpty()) {
            if (reversiMoves(nb, 2).isEmpty()) endGame(nb) else { turn = 2; msg = "Vous passez votre tour." }
        } else {
            turn = 1
            msg = "À vous."
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            AppText("Vous : ${board.count { it == 1 }}", style = Typo.heading, color = Ink.Coral)
            Pill("Reversi", color = Ink.Green)
            AppText("IA : ${board.count { it == 2 }}", style = Typo.heading, color = Ink.Blue)
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val hints = if (turn == 1 && outcome == 0) reversiMoves(board, 1) else emptyList()
            Canvas(
                Modifier.fillMaxSize().pointerInput(board, turn, outcome) {
                    if (outcome != 0 || turn != 1) return@pointerInput
                    detectTapGestures { offset ->
                        val cell = size.width / 8f
                        val x = (offset.x / cell).toInt().coerceIn(0, 7)
                        val y = (offset.y / cell).toInt().coerceIn(0, 7)
                        val idx = y * 8 + x
                        if (reversiFlips(board, idx, 1).isEmpty()) return@detectTapGestures
                        val nb = reversiPlay(board, idx, 1)
                        board = nb
                        sound.play(Sound.TAP)
                        haptics.tap()
                        if (reversiMoves(nb, 2).isEmpty()) {
                            if (reversiMoves(nb, 1).isEmpty()) endGame(nb) else { turn = 1; msg = "L'IA passe son tour." }
                        } else turn = 2
                    }
                },
            ) {
                val cell = size.width / 8f
                for (i in 0 until 64) {
                    val x = (i % 8) * cell
                    val y = (i / 8) * cell
                    drawRect(
                        color = if ((i / 8 + i % 8) % 2 == 0) Ink.Green.copy(alpha = 0.10f) else Ink.Green.copy(alpha = 0.19f),
                        topLeft = Offset(x, y),
                        size = Size(cell, cell),
                    )
                }
                for (idx in hints) {
                    drawCircle(Ink.Teal.copy(alpha = 0.35f), cell * 0.10f, Offset((idx % 8) * cell + cell / 2f, (idx / 8) * cell + cell / 2f))
                }
                for (i in 0 until 64) {
                    if (board[i] == 0) continue
                    val c = Offset((i % 8) * cell + cell / 2f, (i / 8) * cell + cell / 2f)
                    val color = if (board[i] == 1) Ink.Coral else Ink.Blue
                    drawCircle(color, cell * 0.37f, c)
                    drawCircle(Color.White.copy(alpha = 0.40f), cell * 0.10f, Offset(c.x - cell * 0.10f, c.y - cell * 0.10f))
                    drawCircle(Ink.Text.copy(alpha = 0.18f), cell * 0.37f, c, style = Stroke(cell * 0.025f))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        AppText(msg, style = Typo.small, color = Ink.Soft)
        Spacer(Modifier.height(8.dp))
        HintBar(
            if (variant == 2) "Difficile : l'IA privilégie les coins et les grands retournements."
            else "Touchez un point : toutes les pièces alignées changent de couleur.",
            accent = Ink.Green,
        )
    }
}
