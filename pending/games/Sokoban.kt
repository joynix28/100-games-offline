package com.botillon.games.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.botillon.games.ui.AppText
import com.botillon.games.ui.Family
import com.botillon.games.ui.GameEntry
import com.botillon.games.ui.HintBar
import com.botillon.games.ui.Ink
import com.botillon.games.ui.LocalHaptics
import com.botillon.games.ui.LocalSound
import com.botillon.games.ui.Pill
import com.botillon.games.ui.PrimaryButton
import com.botillon.games.ui.Sound
import com.botillon.games.ui.Typo

/** Sokoban : pousser les caisses sur les cibles sans les coincer dans un mur. */
object SokobanGame {
    val entry = GameEntry(
        id = "sokoban",
        title = "Sokoban",
        tagline = "Pousser les caisses sur les cibles",
        family = Family.LOGIC,
        accent = Ink.Mustard,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        SokobanScreen(variant, finish)
    }
}

private val SOKOBAN_LEVELS = listOf(
    listOf(
        "#######",
        "#     #",
        "# @$ .#",
        "#     #",
        "#######",
    ),
    listOf(
        "########",
        "#      #",
        "#  $   #",
        "#  .   #",
        "#  @   #",
        "########",
    ),
    listOf(
        "#########",
        "#       #",
        "#  $ $  #",
        "#  . .  #",
        "#   @   #",
        "#########",
    ),
)

private class SokobanMap(
    val walls: Set<Int>,
    val targets: Set<Int>,
    val boxes: Set<Int>,
    val start: Int,
)

private fun parseSokoban(rows: List<String>): SokobanMap {
    val width = rows.maxOf { it.length }
    val walls = mutableSetOf<Int>()
    val targets = mutableSetOf<Int>()
    val boxes = mutableSetOf<Int>()
    var start = 0
    rows.forEachIndexed { y, line ->
        line.forEachIndexed { x, ch ->
            val index = y * width + x
            when (ch) {
                '#' -> walls += index
                '.' -> targets += index
                '$' -> boxes += index
                '*' -> {
                    boxes += index
                    targets += index
                }
                '@' -> start = index
                '+' -> {
                    start = index
                    targets += index
                }
            }
        }
    }
    return SokobanMap(walls, targets, boxes, start)
}

@Composable
private fun SokobanScreen(variant: Int, finish: (Long, Boolean) -> Unit) {
    val rows = SOKOBAN_LEVELS[variant]
    val height = rows.size
    val width = rows.maxOf { it.length }
    val map = remember(variant) { parseSokoban(rows) }
    var player by remember(variant) { mutableIntStateOf(map.start) }
    var boxes by remember(variant) { mutableStateOf(map.boxes) }
    var moves by remember(variant) { mutableIntStateOf(0) }
    var reported by remember(variant) { mutableStateOf(false) }
    val sound = LocalSound.current
    val haptics = LocalHaptics.current

    fun move(dx: Int, dy: Int) {
        if (reported) return
        val x = player % width
        val y = player / width
        val nx = x + dx
        val ny = y + dy
        if (nx !in 0 until width || ny !in 0 until height) return
        val next = ny * width + nx
        if (next in map.walls) {
            sound.play(Sound.UI)
            return
        }
        if (next in boxes) {
            val bx = nx + dx
            val by = ny + dy
            if (bx !in 0 until width || by !in 0 until height) return
            val behind = by * width + bx
            if (behind in map.walls || behind in boxes) {
                sound.play(Sound.UI)
                return
            }
            boxes = boxes - next + behind
        }
        player = next
        moves++
        sound.play(Sound.TAP)
        haptics.tap()
        if (boxes.all { it in map.targets }) {
            reported = true
            sound.play(Sound.WIN)
            haptics.success()
            finish((500L + variant * 300L - moves * 5L).coerceAtLeast(80L), true)
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Pill("COUPS $moves", color = Ink.Mustard, background = Ink.Mustard.copy(alpha = 0.16f))
            Spacer(Modifier.weight(1f))
            Pill("NIVEAU ${variant + 1} / 3", color = Ink.Soft)
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxWidth().aspectRatio(width.toFloat() / height)) {
                val cell = minOf(size.width / width, size.height / height)
                val ox = (size.width - cell * width) / 2f
                val oy = (size.height - cell * height) / 2f
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        val index = y * width + x
                        val left = ox + x * cell
                        val top = oy + y * cell
                        if (index in map.walls) {
                            drawRoundRect(
                                Ink.Soft,
                                topLeft = Offset(left + 1f, top + 1f),
                                size = Size(cell - 2f, cell - 2f),
                                cornerRadius = CornerRadius(cell * 0.12f),
                            )
                        } else {
                            drawRoundRect(
                                Ink.Card,
                                topLeft = Offset(left + 1f, top + 1f),
                                size = Size(cell - 2f, cell - 2f),
                                cornerRadius = CornerRadius(cell * 0.08f),
                            )
                            if (index in map.targets) {
                                drawCircle(
                                    Ink.Coral,
                                    radius = cell * 0.17f,
                                    center = Offset(left + cell / 2f, top + cell / 2f),
                                    style = Stroke(width = cell * 0.07f),
                                )
                            }
                        }
                    }
                }
                for (box in boxes) {
                    val left = ox + (box % width) * cell
                    val top = oy + (box / width) * cell
                    val color = if (box in map.targets) Ink.Teal else Ink.Mustard
                    drawRoundRect(
                        color,
                        topLeft = Offset(left + cell * 0.17f, top + cell * 0.17f),
                        size = Size(cell * 0.66f, cell * 0.66f),
                        cornerRadius = CornerRadius(cell * 0.10f),
                    )
                    drawLine(
                        Ink.Card.copy(alpha = 0.7f),
                        Offset(left + cell * 0.28f, top + cell * 0.5f),
                        Offset(left + cell * 0.72f, top + cell * 0.5f),
                        cell * 0.05f,
                    )
                }
                val px = ox + (player % width) * cell + cell / 2f
                val py = oy + (player / width) * cell + cell / 2f
                drawCircle(Ink.Blue, radius = cell * 0.30f, center = Offset(px, py))
                drawCircle(Ink.Card, radius = cell * 0.09f, center = Offset(px - cell * 0.10f, py - cell * 0.07f))
                drawCircle(Ink.Card, radius = cell * 0.09f, center = Offset(px + cell * 0.10f, py - cell * 0.07f))
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            PrimaryButton("←", { move(-1, 0) }, Modifier.weight(1f), Ink.CardAlt, Ink.Text)
            PrimaryButton("↑", { move(0, -1) }, Modifier.weight(1f), Ink.CardAlt, Ink.Text)
            PrimaryButton("↓", { move(0, 1) }, Modifier.weight(1f), Ink.CardAlt, Ink.Text)
            PrimaryButton("→", { move(1, 0) }, Modifier.weight(1f), Ink.CardAlt, Ink.Text)
        }
        Spacer(Modifier.height(10.dp))
        HintBar("Un pas = une case. Une caisse ne se tire jamais, elle se pousse.", accent = Ink.Mustard)
        Spacer(Modifier.height(2.dp))
        AppText("Toutes les caisses sur les cibles.", style = Typo.small, color = Ink.Muted)
    }
}
