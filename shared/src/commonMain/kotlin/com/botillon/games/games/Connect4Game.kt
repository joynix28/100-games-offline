package com.botillon.games.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.botillon.games.ui.Panel
import com.botillon.games.ui.Pill
import com.botillon.games.ui.Sound
import com.botillon.games.ui.Typo
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

/**
 * Puissance 4 : quatre jetons alignés, dans n'importe quelle direction.
 * Vous jouez les corail, l'ordinateur les bleus ; la gravité fait le reste.
 */
object Connect4Game {
    val entry = GameEntry(
        id = "puissance4",
        title = "Puissance 4",
        tagline = "Aligner quatre jetons",
        family = Family.BOARD,
        accent = Ink.Coral,
        variants = listOf("6 × 7", "7 × 8", "8 × 9"),
    ) { variant, _, finish, _ ->
        Connect4(variant, finish)
    }
}

private const val PLAYER = 1
private const val MACHINE = 2

/** Ligne la plus basse encore libre d'une colonne, ou -1 si elle est pleine. */
private fun dropRow(board: IntArray, rows: Int, cols: Int, col: Int): Int {
    for (row in rows - 1 downTo 0) {
        if (board[row * cols + col] == 0) return row
    }
    return -1
}

/** Les quatre cases d'un alignement, ou une liste vide. */
private fun fourInARow(board: IntArray, rows: Int, cols: Int, player: Int): List<Int> {
    val directions = listOf(1 to 0, 0 to 1, 1 to 1, 1 to -1)
    for (row in 0 until rows) for (col in 0 until cols) {
        if (board[row * cols + col] != player) continue
        for ((dx, dy) in directions) {
            val cells = mutableListOf<Int>()
            for (step in 0 until 4) {
                val nextCol = col + dx * step
                val nextRow = row + dy * step
                if (nextCol !in 0 until cols || nextRow !in 0 until rows) break
                if (board[nextRow * cols + nextCol] != player) break
                cells += nextRow * cols + nextCol
            }
            if (cells.size == 4) return cells
        }
    }
    return emptyList()
}

/**
 * IA raisonnable : gagner, bloquer, sinon viser le centre avec un grain de hasard.
 * Le plateau passé est modifié puis remis en l'état, il n'est jamais corrompu.
 */
private fun machineColumn(board: IntArray, rows: Int, cols: Int): Int {
    val playable = (0 until cols).filter { dropRow(board, rows, cols, it) >= 0 }
    if (playable.isEmpty()) return -1

    for (col in playable) {
        val row = dropRow(board, rows, cols, col)
        board[row * cols + col] = MACHINE
        val wins = fourInARow(board, rows, cols, MACHINE).isNotEmpty()
        board[row * cols + col] = 0
        if (wins) return col
    }

    for (col in playable) {
        val row = dropRow(board, rows, cols, col)
        board[row * cols + col] = PLAYER
        val stops = fourInARow(board, rows, cols, PLAYER).isNotEmpty()
        board[row * cols + col] = 0
        if (stops) return col
    }

    val center = (cols - 1) / 2f
    return playable.minByOrNull { abs(it - center) + Random.nextFloat() * 1.4f } ?: playable.first()
}

@Composable
private fun Connect4(variant: Int, finish: (Long, Boolean) -> Unit) {
    val rows = listOf(6, 7, 8)[variant]
    val cols = listOf(7, 8, 9)[variant]

    val sound = LocalSound.current
    val haptics = LocalHaptics.current

    var board by remember { mutableStateOf(IntArray(rows * cols)) }
    var turn by remember { mutableIntStateOf(PLAYER) }
    var outcome by remember { mutableIntStateOf(0) } // 0 en cours, 1 gagné, 2 perdu, 3 nul
    var winCells by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var lastColumn by remember { mutableIntStateOf(-1) }
    var ended by remember { mutableStateOf(false) }
    var reported by remember { mutableStateOf(false) }

    fun conclude(result: Int, next: IntArray) {
        if (reported) return
        reported = true
        ended = true
        outcome = result
        val empty = next.count { it == 0 }
        when (result) {
            1 -> {
                sound.play(Sound.WIN)
                haptics.success()
                finish(100L + empty * 5L, true)
            }

            2 -> {
                sound.play(Sound.LOSE)
                haptics.error()
                finish(0L, false)
            }

            else -> {
                sound.play(Sound.LOSE)
                finish(40L, false)
            }
        }
    }

    fun play(column: Int) {
        if (ended || turn != PLAYER) return
        val row = dropRow(board, rows, cols, column)
        if (row < 0) return

        val next = board.copyOf()
        next[row * cols + column] = PLAYER
        board = next
        lastColumn = column
        sound.play(Sound.TAP)
        haptics.tap()

        val line = fourInARow(next, rows, cols, PLAYER)
        if (line.isNotEmpty()) {
            winCells = line.toSet()
            conclude(1, next)
            return
        }
        if (next.none { it == 0 }) {
            conclude(3, next)
            return
        }
        turn = MACHINE
    }

    // L'ordinateur laisse passer un court instant, puis joue une colonne.
    LaunchedEffect(turn, ended) {
        if (ended || turn != MACHINE) return@LaunchedEffect
        delay(340)
        if (ended) return@LaunchedEffect

        val column = machineColumn(board.copyOf(), rows, cols)
        if (column < 0) return@LaunchedEffect
        val row = dropRow(board, rows, cols, column)
        if (row < 0) return@LaunchedEffect

        val next = board.copyOf()
        next[row * cols + column] = MACHINE
        board = next
        lastColumn = column
        sound.play(Sound.TAP)

        val line = fourInARow(next, rows, cols, MACHINE)
        when {
            line.isNotEmpty() -> {
                winCells = line.toSet()
                conclude(2, next)
            }

            next.none { it == 0 } -> conclude(3, next)

            else -> turn = PLAYER
        }
    }

    val status = when (outcome) {
        1 -> "Gagné !"
        2 -> "Perdu…"
        3 -> "Match nul"
        else -> if (turn == PLAYER) "À vous de jouer" else "L'ordinateur réfléchit…"
    }
    val statusColor = when (outcome) {
        1 -> Ink.Teal
        2 -> Ink.Coral
        3 -> Ink.Mustard
        else -> Ink.Text
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Panel(Modifier.fillMaxWidth(), color = Ink.Card) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    AppText(status, style = Typo.heading, color = statusColor)
                    AppText(
                        text = if (lastColumn >= 0) {
                            "Dernier coup : colonne ${lastColumn + 1}"
                        } else {
                            "Vous : corail · Ordinateur : bleu"
                        },
                        style = Typo.small,
                        color = Ink.Muted,
                    )
                }
                Pill(
                    text = if (lastColumn >= 0) "COL. ${lastColumn + 1}" else "COL. —",
                    color = if (turn == PLAYER) Ink.Coral else Ink.Blue,
                    background = (if (turn == PLAYER) Ink.Coral else Ink.Blue).copy(alpha = 0.10f),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(cols.toFloat() / rows.toFloat())
                .pointerInput(rows, cols, ended) {
                    detectTapGestures { offset ->
                        if (ended || turn != PLAYER) return@detectTapGestures
                        val width = size.width.toFloat() / cols
                        val column = (offset.x / width).toInt().coerceIn(0, cols - 1)
                        play(column)
                    }
                },
        ) {
            val cellWidth = size.width / cols
            val cellHeight = size.height / rows
            val discRadius = min(cellWidth, cellHeight) * 0.36f
            val corner = CornerRadius(min(cellWidth, cellHeight) * 0.10f)

            drawRoundRect(color = Ink.Card, cornerRadius = corner)
            drawRoundRect(
                color = Ink.Line,
                topLeft = Offset(1f, 1f),
                size = Size(size.width - 2f, size.height - 2f),
                cornerRadius = corner,
                style = Stroke(width = 2f),
            )

            // Un voile discret sur la colonne du dernier coup.
            if (lastColumn >= 0) {
                drawRoundRect(
                    color = Ink.Mustard.copy(alpha = 0.12f),
                    topLeft = Offset(lastColumn * cellWidth, 0f),
                    size = Size(cellWidth, size.height),
                    cornerRadius = corner,
                )
            }

            for (row in 0 until rows) for (col in 0 until cols) {
                val index = row * cols + col
                val value = board[index]
                val cx = (col + 0.5f) * cellWidth
                val cy = (row + 0.5f) * cellHeight
                val cellCenter = Offset(cx, cy)

                when (value) {
                    0 -> drawCircle(Ink.BgDeep, discRadius, cellCenter)
                    PLAYER -> drawCircle(Ink.Coral, discRadius, cellCenter)
                    else -> drawCircle(Ink.Blue, discRadius, cellCenter)
                }
                if (value != 0) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.26f),
                        radius = discRadius * 0.32f,
                        center = Offset(cx - discRadius * 0.26f, cy - discRadius * 0.26f),
                    )
                }
                if (index in winCells) {
                    drawCircle(
                        color = Ink.Mustard,
                        radius = discRadius * 1.06f,
                        center = cellCenter,
                        style = Stroke(width = (discRadius * 0.13f).coerceAtLeast(2f)),
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        HintBar("Touchez une colonne. L'ordinateur joue les bleus.")
    }
}
