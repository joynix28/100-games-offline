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

/** Moulin : placer neuf pièces, puis aligner trois et capturer. */
object MoulinGame {
    val entry = GameEntry(
        id = "moulin",
        title = "Moulin",
        tagline = "Aligner trois pièces, bloquer l'adversaire",
        family = Family.BOARD,
        accent = Ink.Violet,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        Moulin(variant, finish)
    }
}

private val MOULIN_ADJ: List<List<Int>> = listOf(
    listOf(1, 9), listOf(0, 2, 4), listOf(1, 14),
    listOf(4, 10), listOf(1, 3, 5, 7), listOf(4, 13),
    listOf(7, 11), listOf(4, 6, 8), listOf(7, 12),
    listOf(0, 10, 21), listOf(3, 9, 11, 18), listOf(6, 10, 15),
    listOf(8, 13, 17), listOf(5, 12, 14, 20), listOf(2, 13, 23),
    listOf(11, 16), listOf(15, 17, 19), listOf(12, 16),
    listOf(10, 19), listOf(16, 18, 20, 22), listOf(13, 19),
    listOf(9, 22), listOf(19, 21, 23), listOf(14, 22),
)

private val MOULIN_MILLS: List<List<Int>> = listOf(
    listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8), listOf(9, 10, 11),
    listOf(12, 13, 14), listOf(15, 16, 17), listOf(18, 19, 20), listOf(21, 22, 23),
    listOf(0, 9, 21), listOf(3, 10, 18), listOf(6, 11, 15), listOf(1, 4, 7),
    listOf(16, 19, 22), listOf(8, 12, 17), listOf(5, 13, 20), listOf(2, 14, 23),
)

private val MOULIN_COORDS: List<Pair<Int, Int>> = listOf(
    0 to 0, 3 to 0, 6 to 0, 1 to 1, 3 to 1, 5 to 1, 2 to 2, 3 to 2, 4 to 2,
    0 to 3, 1 to 3, 2 to 3, 4 to 3, 5 to 3, 6 to 3, 2 to 4, 3 to 4, 4 to 4,
    1 to 5, 3 to 5, 5 to 5, 0 to 6, 3 to 6, 6 to 6,
)

private fun isInMill(b: IntArray, idx: Int, player: Int): Boolean =
    MOULIN_MILLS.any { idx in it && it.all { q -> b[q] == player } }

private fun removable(b: IntArray, player: Int): List<Int> {
    val opp = 3 - player
    val all = b.indices.filter { b[it] == opp }
    val free = all.filter { !isInMill(b, it, opp) }
    return if (free.isNotEmpty()) free else all
}

private fun moulinMoves(b: IntArray, player: Int): List<Pair<Int, Int>> =
    b.indices.filter { b[it] == player }.flatMap { from ->
        MOULIN_ADJ[from].filter { b[it] == 0 }.map { from to it }
    }

@Composable
private fun Moulin(variant: Int, finish: (Long, Boolean) -> Unit) {
    val sound = LocalSound.current
    val haptics = LocalHaptics.current
    var board by remember { mutableStateOf(IntArray(24)) }
    var hand by remember { mutableStateOf(intArrayOf(9, 9)) }
    var turn by remember { mutableIntStateOf(0) }
    var selected by remember { mutableIntStateOf(-1) }
    var removing by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("Placez une pièce sur un point libre.") }
    var outcome by remember { mutableIntStateOf(0) }
    var reported by remember { mutableStateOf(false) }

    fun finishGame(won: Boolean, b: IntArray) {
        if (reported) return
        reported = true
        outcome = if (won) 1 else 2
        msg = if (won) "L'adversaire n'a plus de jeu." else "Vous êtes bloqué."
        if (won) { sound.play(Sound.WIN); haptics.success() } else { sound.play(Sound.LOSE); haptics.error() }
        finish(if (won) 100L + b.count { it == 1 } * 20L else 0L, won)
    }

    fun checkLose(b: IntArray): Boolean {
        if (reported) return true
        val bothPlaced = hand[0] == 0 && hand[1] == 0
        if (hand[0] == 0 && b.count { it == 1 } < 3) { finishGame(false, b); return true }
        if (hand[1] == 0 && b.count { it == 2 } < 3) { finishGame(true, b); return true }
        if (bothPlaced && moulinMoves(b, 1).isEmpty()) { finishGame(false, b); return true }
        if (bothPlaced && moulinMoves(b, 2).isEmpty()) { finishGame(true, b); return true }
        return false
    }

    fun playerTap(idx: Int) {
        if (reported || turn != 0) return
        if (removing) {
            if (idx !in removable(board, 0)) return
            val nb = board.copyOf()
            nb[idx] = 0
            board = nb
            removing = false
            sound.play(Sound.POINT)
            haptics.tap()
            if (checkLose(nb)) return
            turn = 1
            msg = "À l'ordinateur."
            return
        }
        if (hand[0] > 0) {
            if (board[idx] != 0) return
            val nb = board.copyOf()
            nb[idx] = 1
            board = nb
            val nh = hand.copyOf()
            nh[0]--
            hand = nh
            sound.play(Sound.TAP)
            haptics.tap()
            if (isInMill(nb, idx, 1)) {
                removing = true
                msg = "Moulin ! Retirez une pièce adverse."
            } else {
                turn = 1
                msg = "À l'ordinateur."
            }
            return
        }
        if (selected < 0) {
            if (board[idx] == 1) selected = idx
            return
        }
        if (idx == selected) {
            selected = -1
            return
        }
        if (board[idx] == 0 && idx in MOULIN_ADJ[selected]) {
            val from = selected
            val nb = board.copyOf()
            nb[from] = 0
            nb[idx] = 1
            board = nb
            selected = -1
            sound.play(Sound.TAP)
            haptics.tap()
            if (isInMill(nb, idx, 1)) {
                removing = true
                msg = "Moulin ! Retirez une pièce adverse."
            } else {
                turn = 1
                msg = "À l'ordinateur."
                checkLose(nb)
            }
        } else if (board[idx] == 1) {
            selected = idx
        }
    }

    LaunchedEffect(turn, board, removing, hand) {
        if (turn != 1 || reported) return@LaunchedEffect
        delay(560)
        if (removing) {
            val targets = removable(board, 1)
            if (targets.isEmpty()) { removing = false; turn = 0; return@LaunchedEffect }
            val nb = board.copyOf()
            nb[targets.random()] = 0
            board = nb
            removing = false
            sound.play(Sound.POINT)
            if (checkLose(nb)) return@LaunchedEffect
            turn = 0
            msg = "L'IA a retiré une pièce."
            return@LaunchedEffect
        }
        if (hand[1] > 0) {
            val empties = board.indices.filter { board[it] == 0 }
            if (empties.isEmpty()) return@LaunchedEffect
            val mills = empties.filter { e -> val t = board.copyOf(); t[e] = 2; isInMill(t, e, 2) }
            val pick = (if (variant == 0) empties else mills.ifEmpty { empties }).random()
            val nb = board.copyOf()
            nb[pick] = 2
            board = nb
            val nh = hand.copyOf()
            nh[1]--
            hand = nh
            sound.play(Sound.TAP)
            if (isInMill(nb, pick, 2)) {
                removing = true
                msg = "L'IA forme un moulin."
            } else {
                turn = 0
                msg = "À vous."
                checkLose(nb)
            }
        } else {
            val moves = moulinMoves(board, 2)
            if (moves.isEmpty()) { finishGame(true, board); return@LaunchedEffect }
            val mills = moves.filter { (f, t) -> val b = board.copyOf(); b[f] = 0; b[t] = 2; isInMill(b, t, 2) }
            val (from, to) = (if (variant == 2) mills.ifEmpty { moves } else moves).random()
            val nb = board.copyOf()
            nb[from] = 0
            nb[to] = 2
            board = nb
            sound.play(Sound.TAP)
            if (isInMill(nb, to, 2)) {
                removing = true
                msg = "Moulin de l'IA."
            } else {
                turn = 0
                msg = "À vous."
                checkLose(nb)
            }
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            AppText("Vous : ${board.count { it == 1 }} (+${hand[0]})", style = Typo.heading, color = Ink.Coral)
            Pill(if (removing) "capture" else if (turn == 0) "À vous" else "IA", color = Ink.Violet)
            AppText("IA : ${board.count { it == 2 }} (+${hand[1]})", style = Typo.heading, color = Ink.Blue)
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            Canvas(Modifier.fillMaxSize().pointerInput(board, turn, removing, selected) {
                if (outcome != 0) return@pointerInput
                detectTapGestures { offset ->
                    val cell = size.width / 7f
                    var best = -1
                    var bestD = Float.MAX_VALUE
                    for (i in 0 until 24) {
                        val (gx, gy) = MOULIN_COORDS[i]
                        val dx = offset.x - (gx + 0.5f) * cell
                        val dy = offset.y - (gy + 0.5f) * cell
                        val d = dx * dx + dy * dy
                        if (d < bestD) { bestD = d; best = i }
                    }
                    if (bestD <= cell * cell * 0.20f) playerTap(best)
                }
            }) {
                val cell = size.width / 7f
                for (i in 0 until 24) for (j in MOULIN_ADJ[i]) if (j > i) {
                    val a = MOULIN_COORDS[i]
                    val b = MOULIN_COORDS[j]
                    drawLine(
                        Ink.Line,
                        Offset((a.first + 0.5f) * cell, (a.second + 0.5f) * cell),
                        Offset((b.first + 0.5f) * cell, (b.second + 0.5f) * cell),
                        strokeWidth = 3f,
                    )
                }
                for (i in 0 until 24) {
                    val (gx, gy) = MOULIN_COORDS[i]
                    val c = Offset((gx + 0.5f) * cell, (gy + 0.5f) * cell)
                    drawCircle(Ink.Card, cell * 0.20f, c)
                    drawCircle(Ink.Line, cell * 0.20f, c, style = Stroke(2f))
                }
                if (turn == 0 && !removing && selected >= 0) {
                    for (target in MOULIN_ADJ[selected]) if (board[target] == 0) {
                        val (gx, gy) = MOULIN_COORDS[target]
                        drawCircle(Ink.Teal.copy(alpha = 0.45f), cell * 0.10f, Offset((gx + 0.5f) * cell, (gy + 0.5f) * cell))
                    }
                }
                if (turn == 0 && removing) {
                    for (target in removable(board, 0)) {
                        val (gx, gy) = MOULIN_COORDS[target]
                        drawCircle(Ink.Pink.copy(alpha = 0.45f), cell * 0.30f, Offset((gx + 0.5f) * cell, (gy + 0.5f) * cell), style = Stroke(4f))
                    }
                }
                for (i in 0 until 24) {
                    if (board[i] == 0) continue
                    val (gx, gy) = MOULIN_COORDS[i]
                    val c = Offset((gx + 0.5f) * cell, (gy + 0.5f) * cell)
                    val color = if (board[i] == 1) Ink.Coral else Ink.Blue
                    if (i == selected) drawCircle(Ink.Mustard.copy(alpha = 0.45f), cell * 0.30f, c)
                    drawCircle(color, cell * 0.23f, c)
                    drawCircle(Ink.Text.copy(alpha = 0.18f), cell * 0.23f, c, style = Stroke(2f))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        AppText(msg, style = Typo.small, color = Ink.Soft, maxLines = 2, softWrap = true)
        Spacer(Modifier.height(8.dp))
        HintBar(
            if (hand[0] > 0) "Placez vos 9 pièces. Un alignement de 3 permet de capturer."
            else "Touchez une pièce puis un point voisin libre. Alignez 3 pour capturer.",
            accent = Ink.Violet,
        )
    }
}
