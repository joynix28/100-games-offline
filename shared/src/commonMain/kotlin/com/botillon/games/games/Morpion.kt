package com.botillon.games.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.botillon.games.ui.AppText
import com.botillon.games.ui.Family
import com.botillon.games.ui.GameEntry
import com.botillon.games.ui.HintBar
import com.botillon.games.ui.Ink
import com.botillon.games.ui.Sound
import com.botillon.games.ui.LocalHaptics
import com.botillon.games.ui.LocalSound
import com.botillon.games.ui.Typo
import kotlin.random.Random

/**
 * Morpion / Gomoku : trois tailles de plateau, l'ordinateur joue les O.
 * L'IA gagne si elle peut, bloque sinon, et privilégie le centre.
 */
object MorpionGame {
    val entry = GameEntry(
        id = "morpion",
        title = "Morpion",
        tagline = "Aligner avant l'ordinateur",
        family = Family.BOARD,
        accent = Ink.Coral,
        variants = listOf("3 × 3", "4 × 4", "5 × 5"),
    ) { variant, _, finish, _ ->
        Morpion(variant, finish)
    }
}

private fun lineLength(size: Int) = if (size == 3) 3 else 4

private fun winningLine(board: IntArray, size: Int, player: Int): List<Int> {
    val need = lineLength(size)
    val directions = listOf(1 to 0, 0 to 1, 1 to 1, 1 to -1)
    for (y in 0 until size) for (x in 0 until size) {
        if (board[y * size + x] != player) continue
        for ((dx, dy) in directions) {
            val cells = mutableListOf<Int>()
            for (step in 0 until need) {
                val nx = x + dx * step
                val ny = y + dy * step
                if (nx !in 0 until size || ny !in 0 until size) break
                if (board[ny * size + nx] != player) break
                cells += ny * size + nx
            }
            if (cells.size == need) return cells
        }
    }
    return emptyList()
}

private fun bestMove(board: IntArray, size: Int, me: Int, opponent: Int): Int {
    val empty = board.indices.filter { board[it] == 0 }
    if (empty.isEmpty()) return -1

    // Gagner si possible.
    for (cell in empty) {
        board[cell] = me
        val win = winningLine(board, size, me).isNotEmpty()
        board[cell] = 0
        if (win) return cell
    }
    // Bloquer l'adversaire.
    for (cell in empty) {
        board[cell] = opponent
        val win = winningLine(board, size, opponent).isNotEmpty()
        board[cell] = 0
        if (win) return cell
    }
    // Sinon, le plus proche du centre.
    val center = (size - 1) / 2f
    return empty.minByOrNull { cell ->
        val x = cell % size
        val y = cell / size
        (x - center) * (x - center) + (y - center) * (y - center)
    } ?: empty.random()
}

@Composable
private fun Morpion(variant: Int, finish: (Long, Boolean) -> Unit) {
    val size = listOf(3, 4, 5)[variant]
    val sound = LocalSound.current
    val haptics = LocalHaptics.current

    var board by remember { mutableStateOf(IntArray(size * size)) }
    var turn by remember { mutableIntStateOf(1) }
    var outcome by remember { mutableIntStateOf(0) } // 0 en cours, 1 gagné, 2 perdu, 3 nul
    var winCells by remember { mutableStateOf<List<Int>>(emptyList()) }
    var reported by remember { mutableStateOf(false) }

    fun report(result: Int) {
        if (reported) return
        reported = true
        outcome = result
        val score = when (result) {
            1 -> 100L + board.count { it == 0 } * 5L
            3 -> 40L
            else -> 0L
        }
        finish(score, result == 1)
    }

    fun afterMove(next: IntArray, playerJustPlayed: Int) {
        val line = winningLine(next, size, playerJustPlayed)
        if (line.isNotEmpty()) {
            winCells = line
            if (playerJustPlayed == 1) {
                sound.play(Sound.WIN)
                haptics.success()
                report(1)
            } else {
                sound.play(Sound.LOSE)
                haptics.error()
                report(2)
            }
            return
        }
        if (next.none { it == 0 }) {
            sound.play(Sound.LOSE)
            report(3)
            return
        }
        if (playerJustPlayed == 1) {
            turn = 2
            val move = bestMove(next, size, 2, 1)
            if (move >= 0) {
                next[move] = 2
                sound.play(Sound.TAP)
                afterMove(next, 2)
            }
        } else {
            turn = 1
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(6.dp))
        AppText(
            text = when (outcome) {
                1 -> "Gagné !"
                2 -> "Perdu…"
                3 -> "Match nul"
                else -> if (turn == 1) "À vous (X)" else "L'ordinateur réfléchit…"
            },
            style = Typo.heading,
            color = when (outcome) {
                1 -> Ink.Teal
                2 -> Ink.Coral
                else -> Ink.Soft
            },
        )
        Spacer(Modifier.height(12.dp))

        val boardColor = Ink.Card
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(size, outcome) {
                        if (outcome != 0) return@pointerInput
                        detectTapGestures { offset ->
                            val cellSize = this.size.width / size.toFloat()
                            val x = (offset.x / cellSize).toInt().coerceIn(0, size - 1)
                            val y = (offset.y / cellSize).toInt().coerceIn(0, size - 1)
                            val index = y * size + x
                            if (board[index] != 0) return@detectTapGestures
                            val next = board.copyOf()
                            next[index] = 1
                            board = next
                            sound.play(Sound.TAP)
                            haptics.tap()
                            afterMove(next, 1)
                        }
                    },
            ) {
                val cell = this.size.width / size
                drawRoundRect(
                    color = boardColor,
                    size = Size(this.size.width, this.size.width),
                    cornerRadius = CornerRadius(cell * 0.12f),
                )
                for (i in 0..size) {
                    drawLine(
                        color = Ink.Line,
                        start = Offset(i * cell, 0f),
                        end = Offset(i * cell, size * cell),
                        strokeWidth = 2f,
                    )
                    drawLine(
                        color = Ink.Line,
                        start = Offset(0f, i * cell),
                        end = Offset(size * cell, i * cell),
                        strokeWidth = 2f,
                    )
                }
                for (index in board.indices) {
                    val player = board[index]
                    if (player == 0) continue
                    val cx = (index % size) * cell + cell / 2f
                    val cy = (index / size) * cell + cell / 2f
                    if (index in winCells) {
                        drawCircle(
                            color = Ink.Mustard.copy(alpha = 0.22f),
                            radius = cell * 0.46f,
                            center = Offset(cx, cy),
                        )
                    }
                    if (player == 1) {
                        drawX(cx, cy, cell * 0.30f, Ink.Coral)
                    } else {
                        drawO(cx, cy, cell * 0.30f, Ink.Blue)
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        HintBar("Vous êtes les X. L'ordinateur bloque et attaque ; il ne triche pas.")
        Spacer(Modifier.height(10.dp))
        AppText(
            "Variante : ${size} × ${size}, aligner ${lineLength(size)}",
            style = Typo.small,
            color = Ink.Muted,
        )
    }
}

private fun DrawScope.drawX(cx: Float, cy: Float, radius: Float, color: Color) {
    val w = (radius * 0.34f).coerceAtLeast(2f)
    drawLine(color, Offset(cx - radius, cy - radius), Offset(cx + radius, cy + radius), strokeWidth = w)
    drawLine(color, Offset(cx + radius, cy - radius), Offset(cx - radius, cy + radius), strokeWidth = w)
}

private fun DrawScope.drawO(cx: Float, cy: Float, radius: Float, color: Color) {
    drawCircle(
        color = color,
        radius = radius,
        center = Offset(cx, cy),
        style = Stroke(width = (radius * 0.34f).coerceAtLeast(2f)),
    )
}
