package com.botillon.games.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.random.Random

/** Serpent : manger les pommes sans se mordre la queue. */
object SnakeGame {
    val entry = GameEntry(
        id = "snake",
        title = "Serpent",
        tagline = "Manger sans se mordre",
        family = Family.ARCADE,
        accent = Ink.Green,
        variants = listOf("Tranquille", "Rapide", "Expert"),
    ) { variant, _, finish, _ ->
        Snake(variant, finish)
    }
}

private data class Cell(val x: Int, val y: Int)

@Composable
private fun Snake(variant: Int, finish: (Long, Boolean) -> Unit) {
    val grid = 14 + variant * 2
    val tick = when (variant) {
        0 -> 200L
        1 -> 145L
        else -> 105L
    }
    val sound = LocalSound.current
    val haptics = LocalHaptics.current

    var snake by remember { mutableStateOf(listOf(Cell(grid / 2, grid / 2), Cell(grid / 2 - 1, grid / 2))) }
    var direction by remember { mutableStateOf(Cell(1, 0)) }
    var pending by remember { mutableStateOf(Cell(1, 0)) }
    var food by remember { mutableStateOf(Cell(grid / 2 + 4, grid / 2)) }
    var score by remember { mutableStateOf(0L) }
    var over by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }

    fun placeFood() {
        val free = buildList {
            for (y in 0 until grid) for (x in 0 until grid) {
                if (snake.none { it.x == x && it.y == y }) add(Cell(x, y))
            }
        }
        food = if (free.isEmpty()) Cell(0, 0) else free.random(Random.Default)
    }

    fun step() {
        if (over || paused) return
        direction = pending
        val head = snake.first()
        val next = Cell(head.x + direction.x, head.y + direction.y)
        if (next.x !in 0 until grid || next.y !in 0 until grid) {
            over = true
            sound.play(Sound.LOSE)
            haptics.error()
            finish(score, false)
            return
        }
        if (snake.drop(1).any { it == next } || snake.any { it == next }) {
            over = true
            sound.play(Sound.LOSE)
            haptics.error()
            finish(score, false)
            return
        }
        val eats = next == food
        snake = if (eats) listOf(next) + snake else listOf(next) + snake.dropLast(1)
        if (eats) {
            score += 10
            sound.play(Sound.POINT)
            haptics.tap()
            placeFood()
        }
    }

    LaunchedEffect(grid, tick, over, paused) {
        while (isActive && !over) {
            delay(tick)
            step()
        }
    }

    fun turn(dx: Int, dy: Int) {
        if (dx == -direction.x && dy == -direction.y) return
        pending = Cell(dx, dy)
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AppText("Score $score", style = Typo.heading, color = Ink.Text)
            AppText("Longueur ${snake.size}", style = Typo.small, color = Ink.Muted)
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
                            if (abs(total.x) > abs(total.y)) turn(if (total.x > 0) 1 else -1, 0)
                            else if (abs(total.y) > 30f) turn(0, if (total.y > 0) 1 else -1)
                        },
                    )
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val cell = size.width / grid
                drawRoundRect(
                    color = Ink.Card,
                    size = Size(size.width, size.width),
                    cornerRadius = CornerRadius(cell * 0.4f),
                )
                for (y in 0 until grid) for (x in 0 until grid) {
                    if ((x + y) % 2 == 0) {
                        drawRect(
                            color = Ink.CardAlt.copy(alpha = 0.5f),
                            topLeft = Offset(x * cell, y * cell),
                            size = Size(cell, cell),
                        )
                    }
                }
                // Pomme.
                drawCircle(
                    color = Ink.Coral,
                    radius = cell * 0.32f,
                    center = Offset(food.x * cell + cell / 2f, food.y * cell + cell / 2f),
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.45f),
                    radius = cell * 0.10f,
                    center = Offset(food.x * cell + cell * 0.40f, food.y * cell + cell * 0.36f),
                )
                // Serpent.
                snake.forEachIndexed { index, segment ->
                    val inset = cell * 0.08f
                    drawRoundRect(
                        color = if (index == 0) Ink.Green else Ink.Teal.copy(alpha = 0.82f - index * 0.01f),
                        topLeft = Offset(segment.x * cell + inset, segment.y * cell + inset),
                        size = Size(cell - inset * 2, cell - inset * 2),
                        cornerRadius = CornerRadius(cell * 0.28f),
                    )
                }
                // Yeux de la tête.
                val head = snake.first()
                val eye = cell * 0.09f
                for (side in listOf(-1, 1)) {
                    drawCircle(
                        color = Color.White,
                        radius = eye,
                        center = Offset(
                            head.x * cell + cell / 2f + side * cell * 0.16f,
                            head.y * cell + cell / 2f - cell * 0.10f,
                        ),
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        HintBar("Glissez pour tourner. Le serpent accélère avec les variantes.")
        Spacer(Modifier.height(12.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ArrowButton("↑") { turn(0, -1) }
            Row(horizontalArrangement = Arrangement.spacedBy(46.dp)) {
                ArrowButton("←") { turn(-1, 0) }
                ArrowButton("→") { turn(1, 0) }
            }
            ArrowButton("↓") { turn(0, 1) }
        }
    }
}

@Composable
private fun ArrowButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Ink.Card)
            .border(1.dp, Ink.Line, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AppText(label, style = Typo.heading, color = Ink.Text)
    }
}
