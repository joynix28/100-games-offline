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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.botillon.games.ui.AppText
import com.botillon.games.ui.Family
import com.botillon.games.ui.GameEntry
import com.botillon.games.ui.HintBar
import com.botillon.games.ui.Ink
import com.botillon.games.ui.LocalHaptics
import com.botillon.games.ui.LocalSound
import com.botillon.games.ui.Sound
import com.botillon.games.ui.Typo
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Dames : captures optionnelles, dames couronnées sur la dernière rangée. */
object CheckersGame {
    val entry = GameEntry(
        id = "dames",
        title = "Dames",
        tagline = "Prendre toutes les pièces",
        family = Family.BOARD,
        accent = Ink.Blue,
        variants = listOf("Rapide", "Classique", "Longue"),
    ) { variant, _, finish, _ ->
        Checkers(variant, finish)
    }
}

private data class Piece(val x: Int, val y: Int, val player: Int, val king: Boolean = false)
private data class Move(val piece: Piece, val toX: Int, val toY: Int, val capture: Pair<Int, Int>? = null)

private val PLAYER = 1
private val AI = 2

private fun inside(x: Int, y: Int) = x in 0..7 && y in 0..7

private fun pieceAt(pieces: List<Piece>, x: Int, y: Int): Piece? =
    pieces.firstOrNull { it.x == x && it.y == y }

private fun movesFor(pieces: List<Piece>, piece: Piece): List<Move> {
    val dirs = if (piece.king) listOf(-1, 1) else if (piece.player == PLAYER) listOf(-1) else listOf(1)
    val result = mutableListOf<Move>()
    for (dy in dirs) for (dx in listOf(-1, 1)) {
        val nx = piece.x + dx
        val ny = piece.y + dy
        if (!inside(nx, ny)) continue
        val occupant = pieceAt(pieces, nx, ny)
        if (occupant == null) {
            result += Move(piece, nx, ny)
        } else if (occupant.player != piece.player) {
            val lx = piece.x + dx * 2
            val ly = piece.y + dy * 2
            if (inside(lx, ly) && pieceAt(pieces, lx, ly) == null) {
                result += Move(piece, lx, ly, occupant.x to occupant.y)
            }
        }
    }
    return result
}

private fun allMoves(pieces: List<Piece>, player: Int): List<Move> =
    pieces.filter { it.player == player }.flatMap { movesFor(pieces, it) }

@Composable
private fun Checkers(variant: Int, finish: (Long, Boolean) -> Unit) {
    val sound = LocalSound.current
    val haptics = LocalHaptics.current
    val rows = when (variant) {
        0 -> 5
        1 -> 6
        else -> 8
    }

    var pieces by remember { mutableStateOf(initialPieces(rows)) }
    var selected by remember { mutableStateOf<Piece?>(null) }
    var turn by remember { mutableStateOf(PLAYER) }
    var message by remember { mutableStateOf("À vous. Sélectionnez une pièce.") }
    var outcome by remember { mutableStateOf(0) }
    var reported by remember { mutableStateOf(false) }

    fun finishOnce(won: Boolean) {
        if (reported) return
        reported = true
        outcome = if (won) 1 else 2
        val mine = pieces.count { it.player == PLAYER }
        finish(if (won) 100L + mine * 10 else 0L, won)
    }

    fun afterMove() {
        val aiMoves = allMoves(pieces, AI)
        val playerMoves = allMoves(pieces, PLAYER)
        when {
            aiMoves.isEmpty() -> {
                message = "Plus de coup pour l'ordinateur."
                sound.play(Sound.WIN)
                haptics.success()
                finishOnce(true)
            }

            playerMoves.isEmpty() -> {
                message = "Vous êtes bloqué."
                sound.play(Sound.LOSE)
                haptics.error()
                finishOnce(false)
            }
        }
    }

    fun play(move: Move) {
        val mutable = pieces.toMutableList()
        mutable.remove(move.piece)
        move.capture?.let { (cx, cy) ->
            mutable.removeAll { it.x == cx && it.y == cy }
        }
        var landed = move.piece.copy(x = move.toX, y = move.toY)
        if ((landed.player == PLAYER && landed.y == 0) || (landed.player == AI && landed.y == 7)) {
            landed = landed.copy(king = true)
        }
        mutable += landed
        pieces = mutable
        selected = null
        sound.play(Sound.TAP)
        afterMove()
        if (turn == PLAYER && outcome == 0) {
            turn = AI
        }
    }

    LaunchedEffect(turn, outcome) {
        if (turn != AI || outcome != 0) return@LaunchedEffect
        delay(420)
        val candidates = allMoves(pieces, AI)
        if (candidates.isEmpty()) return@LaunchedEffect
        val captures = candidates.filter { it.capture != null }
        val move = if (captures.isNotEmpty()) captures.random() else candidates.random()
        play(move)
        turn = PLAYER
        message = "À vous."
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AppText("Vous : ${pieces.count { it.player == PLAYER }}", style = Typo.heading, color = Ink.Coral)
            AppText("IA : ${pieces.count { it.player == AI }}", style = Typo.heading, color = Ink.Blue)
        }
        Spacer(Modifier.height(8.dp))
        AppText(message, style = Typo.small, color = Ink.Muted)

        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(pieces, turn, outcome) {
                        if (outcome != 0 || turn != PLAYER) return@pointerInput
                        detectTapGestures { offset ->
                            val cell = size.width / 8f
                            val x = (offset.x / cell).toInt().coerceIn(0, 7)
                            val y = (offset.y / cell).toInt().coerceIn(0, 7)
                            val current = selected
                            val target = current?.let { movesFor(pieces, it).firstOrNull { m -> m.toX == x && m.toY == y } }
                            if (target != null) {
                                play(target)
                            } else {
                                val tapped = pieceAt(pieces, x, y)
                                selected = if (tapped != null && tapped.player == PLAYER) tapped else null
                                if (selected != null) {
                                    sound.play(Sound.TAP)
                                    haptics.tap()
                                }
                            }
                        }
                    },
            ) {
                val cell = size.width / 8f
                for (y in 0 until 8) for (x in 0 until 8) {
                    val dark = (x + y) % 2 == 1
                    drawRect(
                        color = if (dark) Ink.CardAlt else Ink.Card,
                        topLeft = Offset(x * cell, y * cell),
                        size = Size(cell, cell),
                    )
                }
                // Coups possibles du pion sélectionné.
                selected?.let { piece ->
                    for (move in movesFor(pieces, piece)) {
                        drawCircle(
                            color = Ink.Teal.copy(alpha = 0.35f),
                            radius = cell * (if (move.capture != null) 0.16f else 0.10f),
                            center = Offset(move.toX * cell + cell / 2f, move.toY * cell + cell / 2f),
                        )
                    }
                }
                for (piece in pieces) {
                    val cx = piece.x * cell + cell / 2f
                    val cy = piece.y * cell + cell / 2f
                    val color = if (piece.player == PLAYER) Ink.Coral else Ink.Blue
                    if (piece == selected) {
                        drawCircle(Ink.Mustard.copy(alpha = 0.35f), radius = cell * 0.46f, center = Offset(cx, cy))
                    }
                    drawCircle(color, radius = cell * 0.36f, center = Offset(cx, cy))
                    drawCircle(Color.Black.copy(alpha = 0.18f), radius = cell * 0.36f, center = Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(cell * 0.03f))
                    drawCircle(Color.White.copy(alpha = 0.35f), radius = cell * 0.12f, center = Offset(cx - cell * 0.10f, cy - cell * 0.10f))
                    if (piece.king) {
                        drawCircle(Ink.Mustard, radius = cell * 0.12f, center = Offset(cx, cy))
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        HintBar("Touchez une pièce, puis une case mise en évidence. Captures optionnelles.")
        Spacer(Modifier.height(8.dp))
        AppText(
            if (outcome == 1) "Gagné !" else if (outcome == 2) "Perdu…" else "Les dames se couronnent au fond.",
            style = Typo.small,
            color = if (outcome == 1) Ink.Teal else Ink.Muted,
        )
    }
}

private fun initialPieces(rows: Int): List<Piece> {
    val pieces = mutableListOf<Piece>()
    for (y in 0 until rows) {
        for (x in 0 until 8) {
            if ((x + y) % 2 == 1) pieces += Piece(x, y, AI)
        }
    }
    for (y in 8 - rows until 8) {
        for (x in 0 until 8) {
            if ((x + y) % 2 == 1) pieces += Piece(x, y, PLAYER)
        }
    }
    return pieces
}
