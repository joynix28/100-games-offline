package com.botillon.games.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
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
import kotlin.math.abs
import kotlin.random.Random
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle

/**
 * 2048 : on fait glisser les tuiles, deux tuiles identiques fusionnent.
 * Le plateau s'agrandit avec les variantes.
 */
object Game2048Entry {
    val entry = GameEntry(
        id = "2048",
        title = "2048",
        tagline = "Fusionner jusqu'à 2048",
        family = Family.LOGIC,
        accent = Ink.Mustard,
        variants = listOf("4 × 4", "5 × 5", "6 × 6"),
    ) { variant, _, finish, _ ->
        Game2048(variant, finish)
    }
}

private fun spawn(board: IntArray, random: Random = Random.Default): IntArray {
    val empty = board.indices.filter { board[it] == 0 }
    if (empty.isEmpty()) return board
    val next = board.copyOf()
    next[empty.random(random)] = if (random.nextFloat() < 0.9f) 2 else 4
    return next
}

private fun freshBoard(n: Int): IntArray {
    var board = IntArray(n * n)
    repeat(2) { board = spawn(board) }
    return board
}

private fun slideLine(line: List<Int>): Pair<List<Int>, Int> {
    val values = line.filter { it != 0 }
    val out = mutableListOf<Int>()
    var gained = 0
    var i = 0
    while (i < values.size) {
        if (i + 1 < values.size && values[i] == values[i + 1]) {
            val merged = values[i] * 2
            out += merged
            gained += merged
            i += 2
        } else {
            out += values[i]
            i++
        }
    }
    while (out.size < line.size) out += 0
    return out to gained
}

/** dir : 0 gauche, 1 droite, 2 haut, 3 bas. */
private fun slide(board: IntArray, n: Int, dir: Int): Triple<IntArray, Int, Boolean> {
    val next = board.copyOf()
    var gained = 0
    var moved = false

    for (index in 0 until n) {
        val original = when (dir) {
            0, 1 -> (0 until n).map { x -> board[index * n + x] }
            else -> (0 until n).map { y -> board[y * n + index] }
        }
        val oriented = if (dir == 1 || dir == 3) original.reversed() else original
        val (merged, points) = slideLine(oriented)
        val result = if (dir == 1 || dir == 3) merged.reversed() else merged
        gained += points
        if (result != original) moved = true
        result.forEachIndexed { position, value ->
            when (dir) {
                0, 1 -> next[index * n + position] = value
                else -> next[position * n + index] = value
            }
        }
    }
    return Triple(next, gained, moved)
}

private fun hasMoves(board: IntArray, n: Int): Boolean {
    if (board.any { it == 0 }) return true
    for (y in 0 until n) for (x in 0 until n) {
        val value = board[y * n + x]
        if (x + 1 < n && board[y * n + x + 1] == value) return true
        if (y + 1 < n && board[(y + 1) * n + x] == value) return true
    }
    return false
}

private fun tileColor(value: Int): Color = when (value) {
    2 -> Color(0xFFF7E2BC)
    4 -> Color(0xFFF3D49B)
    8 -> Color(0xFFF2B179)
    16 -> Color(0xFFF59563)
    32 -> Color(0xFFF67C5F)
    64 -> Color(0xFFF65E3B)
    128 -> Color(0xFFEDCF72)
    256 -> Color(0xFFEDCC61)
    512 -> Color(0xFFEDC850)
    1024 -> Color(0xFFEDC53F)
    else -> Color(0xFFEDC22E)
}

@Composable
private fun Game2048(variant: Int, finish: (Long, Boolean) -> Unit) {
    val n = 4 + variant
    val sound = LocalSound.current
    val haptics = LocalHaptics.current
    val textMeasurer = rememberTextMeasurer()

    var board by remember { mutableStateOf(freshBoard(n)) }
    var score by remember { mutableStateOf(0L) }
    var over by remember { mutableStateOf(false) }

    fun doMove(dir: Int) {
        if (over) return
        val (next, gained, changed) = slide(board, n, dir)
        if (!changed) return
        val spawned = spawn(next)
        board = spawned
        score += gained
        if (gained > 0) {
            sound.play(Sound.MATCH)
            haptics.tap()
        }
        if (!hasMoves(spawned, n)) {
            over = true
            sound.play(Sound.LOSE)
            finish(score, false)
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText("Score $score", style = Typo.heading, color = Ink.Text)
            AppText("Objectif 2048", style = Typo.small, color = Ink.Muted)
        }
        Spacer(Modifier.height(10.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .pointerInput(over) {
                    if (over) return@pointerInput
                    var total = Offset.Zero
                    detectDragGestures(
                        onDragStart = { total = Offset.Zero },
                        onDrag = { change, amount ->
                            change.consume()
                            total += amount
                        },
                        onDragEnd = {
                            if (abs(total.x) > abs(total.y)) {
                                if (abs(total.x) > 30f) doMove(if (total.x > 0) 1 else 0)
                            } else if (abs(total.y) > 30f) {
                                doMove(if (total.y > 0) 3 else 2)
                            }
                        },
                    )
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val cell = size.width / n
                drawRoundRect(
                    color = Ink.CardAlt,
                    size = Size(size.width, size.width),
                    cornerRadius = CornerRadius(cell * 0.12f),
                )
                for (y in 0 until n) for (x in 0 until n) {
                    val value = board[y * n + x]
                    val inset = cell * 0.055f
                    val topLeft = Offset(x * cell + inset, y * cell + inset)
                    val side = cell - inset * 2
                    drawRoundRect(
                        color = if (value == 0) Color(0xFFFBF7EF) else tileColor(value),
                        topLeft = topLeft,
                        size = Size(side, side),
                        cornerRadius = CornerRadius(side * 0.16f),
                    )
                    if (value != 0) {
                        val text = "$value"
                        val fontSize = (side * if (value < 100) 0.36f else if (value < 1000) 0.28f else 0.22f)
                        val style = TextStyle(
                            color = if (value <= 4) Ink.Soft else Color.White,
                            fontSize = androidx.compose.ui.unit.TextUnit(fontSize / density, androidx.compose.ui.unit.TextUnitType.Sp),
                            fontWeight = FontWeight.Black,
                        )
                        val measured = textMeasurer.measure(text, style)
                        drawText(
                            textMeasurer = textMeasurer,
                            text = text,
                            topLeft = Offset(
                                topLeft.x + (side - measured.size.width) / 2f,
                                topLeft.y + (side - measured.size.height) / 2f,
                            ),
                            style = style,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        HintBar("Glissez dans une direction (ou utilisez les flèches). Deux tuiles égales fusionnent.")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryArrow("←") { doMove(0) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PrimaryArrow("↑") { doMove(2) }
                Spacer(Modifier.height(8.dp))
                PrimaryArrow("↓") { doMove(3) }
            }
            PrimaryArrow("→") { doMove(1) }
        }
    }
}

@Composable
private fun PrimaryArrow(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Ink.Card)
            .border(1.dp, Ink.Line, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AppText(label, style = Typo.heading, color = Ink.Text)
    }
}
