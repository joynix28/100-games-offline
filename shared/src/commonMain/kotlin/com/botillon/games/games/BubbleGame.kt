package com.botillon.games.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Bulles : un tireur, une grille hexagonale et trois bulles de même couleur à
 * faire éclater. On vise en glissant le doigt, on relâche pour tirer.
 */
object BubbleGame {
    val entry = GameEntry(
        id = "bulles",
        title = "Bulles",
        tagline = "Aligner trois bulles",
        family = Family.ARCADE,
        accent = Ink.Pink,
        variants = listOf("Facile", "Normal", "Difficile"),
    ) { variant, _, finish, _ ->
        Bubble(variant, finish)
    }
}

private const val COLS = 9
private const val MAX_ROWS = 16
private const val ROW_STEP = 1.7320508f
private const val SHOOTER_X = 9.5f

/** Les cinq couleurs de bulles. */
private val BUBBLE_COLORS = listOf(Ink.Coral, Ink.Teal, Ink.Blue, Ink.Mustard, Ink.Violet)

/** Une bulle en vol, exprimée en unités de rayon. */
private data class Shot(val x: Float, val y: Float, val vx: Float, val vy: Float, val color: Int)

private fun cellX(row: Int, col: Int): Float = (if (row % 2 == 0) 1f else 2f) + 2f * col

private fun cellY(row: Int): Float = 1f + ROW_STEP * row

/** Les six voisines d'une alvéole, dans une grille à rangées décalées. */
private fun neighbours(index: Int): List<Int> {
    val row = index / COLS
    val col = index % COLS
    val out = mutableListOf<Int>()
    fun add(r: Int, c: Int) {
        if (r in 0 until MAX_ROWS && c in 0 until COLS) out.add(r * COLS + c)
    }
    add(row, col - 1)
    add(row, col + 1)
    if (row % 2 == 0) {
        add(row - 1, col - 1)
        add(row - 1, col)
        add(row + 1, col - 1)
        add(row + 1, col)
    } else {
        add(row - 1, col)
        add(row - 1, col + 1)
        add(row + 1, col)
        add(row + 1, col + 1)
    }
    return out
}

@Composable
private fun Bubble(variant: Int, finish: (Long, Boolean) -> Unit) {
    val initialRows = when (variant) {
        0 -> 6
        1 -> 7
        else -> 9
    }
    val colorCount = if (variant == 0) 4 else 5
    val speed = when (variant) {
        0 -> 26f
        1 -> 34f
        else -> 44f
    }
    val sound = LocalSound.current
    val haptics = LocalHaptics.current

    var board by remember {
        mutableStateOf(
            IntArray(MAX_ROWS * COLS) { i ->
                if (i / COLS < initialRows) Random.nextInt(colorCount) else -1
            },
        )
    }
    var score by remember { mutableStateOf(0L) }
    var currentColor by remember { mutableIntStateOf(Random.nextInt(colorCount)) }
    var nextColor by remember { mutableIntStateOf(Random.nextInt(colorCount)) }
    var angle by remember { mutableStateOf(-(PI / 2).toFloat()) }
    var aiming by remember { mutableStateOf(false) }
    var shot by remember { mutableStateOf<Shot?>(null) }
    var over by remember { mutableStateOf(false) }
    var reported by remember { mutableStateOf(false) }

    /** Toutes les bulles de la même couleur reliées à [start]. */
    fun groupAt(start: Int, grid: IntArray): List<Int> {
        val color = grid[start]
        if (color < 0) return emptyList()
        val seen = mutableSetOf(start)
        val stack = mutableListOf(start)
        while (stack.isNotEmpty()) {
            val cur = stack.removeAt(stack.size - 1)
            for (n in neighbours(cur)) {
                if (n !in seen && grid[n] == color) {
                    seen.add(n)
                    stack.add(n)
                }
            }
        }
        return seen.toList()
    }

    /** Supprime les bulles qui ne tiennent plus au plafond. */
    fun dropFloating(grid: IntArray): Int {
        val linked = BooleanArray(grid.size)
        val stack = mutableListOf<Int>()
        for (c in 0 until COLS) {
            if (grid[c] >= 0) {
                linked[c] = true
                stack.add(c)
            }
        }
        while (stack.isNotEmpty()) {
            val cur = stack.removeAt(stack.size - 1)
            for (n in neighbours(cur)) {
                if (grid[n] >= 0 && !linked[n]) {
                    linked[n] = true
                    stack.add(n)
                }
            }
        }
        var dropped = 0
        for (i in grid.indices) {
            if (grid[i] >= 0 && !linked[i]) {
                grid[i] = -1
                dropped++
            }
        }
        return dropped
    }

    fun finishGame(won: Boolean) {
        if (reported) return
        reported = true
        over = true
        aiming = false
        if (won) {
            sound.play(Sound.WIN)
            haptics.success()
        } else {
            sound.play(Sound.LOSE)
            haptics.error()
        }
        finish(if (won) score + 200L else score, won)
    }

    /** Colle la bulle au premier emplacement libre proche du point d'impact. */
    fun land(x: Float, y: Float, hit: Int, color: Int, lossRow: Int) {
        if (over) return
        val candidates = mutableListOf<Int>()
        if (hit < 0) {
            for (c in 0 until COLS) if (board[c] < 0) candidates.add(c)
        } else {
            for (n in neighbours(hit)) if (board[n] < 0) candidates.add(n)
        }
        if (candidates.isEmpty()) {
            // Aucune place collée : on prend le trou le plus proche, quitte à ce
            // qu'il tombe ensuite (la chute des blocs flottants s'en charge).
            for (i in board.indices) if (board[i] < 0) candidates.add(i)
        }
        val target = candidates.minByOrNull { i ->
            val dx = cellX(i / COLS, i % COLS) - x
            val dy = cellY(i / COLS) - y
            dx * dx + dy * dy
        }
        if (target == null) {
            finishGame(false)
            return
        }
        val grid = board.copyOf()
        grid[target] = color
        val group = groupAt(target, grid)
        if (group.size >= 3) {
            for (i in group) grid[i] = -1
            dropFloating(grid)
            score += group.size * 10L
            sound.play(Sound.POINT)
            haptics.tap()
        } else {
            sound.play(Sound.TAP)
            haptics.tap()
        }
        board = grid
        if (grid.none { it >= 0 }) {
            finishGame(true)
            return
        }
        if (grid.indices.any { grid[it] >= 0 && it / COLS >= lossRow }) finishGame(false)
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pill("$score pts", color = Ink.Pink, background = Ink.Pink.copy(alpha = 0.12f))
            Pill(
                text = if (shot == null) "Prêt à tirer" else "Tir en vol",
                color = Ink.Soft,
                background = Ink.CardAlt,
            )
            Pill("${board.count { it >= 0 }} bulles", color = Ink.Soft, background = Ink.CardAlt)
        }
        Spacer(Modifier.height(6.dp))

        Panel(Modifier.fillMaxWidth().weight(1f)) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val widthPx = with(density) { maxWidth.toPx() }
                val heightPx = with(density) { maxHeight.toPx() }
                val unit = widthPx / 19f
                val heightUnits = heightPx / unit
                val shooterY = heightUnits - 3.4f
                val lossRow = (((shooterY - 4.5f) / ROW_STEP).toInt()).coerceIn(initialRows + 1, 13)
                val halfPi = (PI / 2).toFloat()
                val limit = (PI - 0.22).toFloat()

                fun aimAt(position: Offset) {
                    val dx = position.x / unit - SHOOTER_X
                    val dy = position.y / unit - shooterY
                    if (dx == 0f && dy == 0f) return
                    var a = atan2(dy, dx)
                    if (a > 0f) a = if (a < halfPi) -0.22f else -limit
                    angle = a.coerceIn(-limit, -0.22f)
                }

                fun fire() {
                    if (over || shot != null) return
                    val a = angle
                    shot = Shot(
                        x = SHOOTER_X + cos(a) * 1.2f,
                        y = shooterY + sin(a) * 1.2f,
                        vx = cos(a) * speed,
                        vy = sin(a) * speed,
                        color = currentColor,
                    )
                    currentColor = nextColor
                    nextColor = Random.nextInt(colorCount)
                    sound.play(Sound.TAP)
                    haptics.tap()
                }

                LaunchedEffect(lossRow, over) {
                    if (over) return@LaunchedEffect
                    while (true) {
                        delay(16)
                        val s = shot ?: continue
                        var x = s.x
                        var y = s.y
                        var vx = s.vx
                        var vy = s.vy
                        var hit = -1
                        var ceiling = false
                        // Plusieurs sous-pas pour ne pas traverser une bulle.
                        for (step in 0 until 4) {
                            x += vx * 0.016f / 4f
                            y += vy * 0.016f / 4f
                            if (x < 1f) {
                                x = 1f
                                vx = -vx
                            }
                            if (x > 18f) {
                                x = 18f
                                vx = -vx
                            }
                            if (y <= 1f) {
                                ceiling = true
                                break
                            }
                            var found = -1
                            for (i in board.indices) {
                                if (board[i] < 0) continue
                                val dx = x - cellX(i / COLS, i % COLS)
                                val dy = y - cellY(i / COLS)
                                if (dx * dx + dy * dy < 3.61f) {
                                    found = i
                                    break
                                }
                            }
                            if (found >= 0) {
                                hit = found
                                break
                            }
                        }
                        when {
                            ceiling -> {
                                shot = null
                                land(x, y, -1, s.color, lossRow)
                            }
                            hit >= 0 -> {
                                shot = null
                                land(x, y, hit, s.color, lossRow)
                            }
                            else -> shot = Shot(x, y, vx, vy, s.color)
                        }
                    }
                }

                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(over, shooterY, unit) {
                            if (over) return@pointerInput
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                aiming = true
                                aimAt(down.position)
                                down.consume()
                                var pressed = true
                                while (pressed) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (change.pressed) {
                                        aimAt(change.position)
                                        change.consume()
                                    } else {
                                        pressed = false
                                    }
                                }
                                aiming = false
                                if (!over) fire()
                            }
                        },
                ) {
                    // Ligne de défaite, en pointillés.
                    val lossY = (cellY(lossRow) - 1f) * unit
                    var dashX = 0f
                    while (dashX < size.width) {
                        drawLine(
                            color = Ink.Coral.copy(alpha = 0.30f),
                            start = Offset(dashX, lossY),
                            end = Offset(min(dashX + 12f, size.width), lossY),
                            strokeWidth = 2f,
                        )
                        dashX += 20f
                    }

                    // Les bulles posées.
                    for (i in board.indices) {
                        val v = board[i]
                        if (v < 0) continue
                        val cx = cellX(i / COLS, i % COLS) * unit
                        val cy = cellY(i / COLS) * unit
                        drawBubble(cx, cy, unit * 0.95f, BUBBLE_COLORS[v % BUBBLE_COLORS.size])
                    }

                    // Guide de visée.
                    if (aiming && shot == null && !over) {
                        var t = 1.6f
                        while (t < 10f) {
                            drawLine(
                                color = Ink.Pink.copy(alpha = 0.45f),
                                start = Offset(
                                    (SHOOTER_X + cos(angle) * t) * unit,
                                    (shooterY + sin(angle) * t) * unit,
                                ),
                                end = Offset(
                                    (SHOOTER_X + cos(angle) * (t + 0.35f)) * unit,
                                    (shooterY + sin(angle) * (t + 0.35f)) * unit,
                                ),
                                strokeWidth = unit * 0.12f,
                            )
                            t += 0.7f
                        }
                    }

                    val sx = SHOOTER_X * unit
                    val sy = shooterY * unit
                    drawCircle(Ink.CardAlt, radius = unit * 1.55f, center = Offset(sx, sy))
                    drawCircle(
                        color = Ink.Line,
                        radius = unit * 1.55f,
                        center = Offset(sx, sy),
                        style = Stroke(width = unit * 0.08f),
                    )
                    drawLine(
                        color = Ink.Soft,
                        start = Offset(sx, sy),
                        end = Offset(sx + cos(angle) * unit * 1.4f, sy + sin(angle) * unit * 1.4f),
                        strokeWidth = unit * 0.16f,
                    )
                    drawBubble(sx, sy, unit * 0.9f, BUBBLE_COLORS[currentColor % BUBBLE_COLORS.size])

                    // Aperçu de la bulle suivante.
                    val nx = sx + unit * 3.1f
                    drawBubble(nx, sy, unit * 0.62f, BUBBLE_COLORS[nextColor % BUBBLE_COLORS.size])
                    drawCircle(
                        color = Ink.Line,
                        radius = unit * 0.82f,
                        center = Offset(nx, sy),
                        style = Stroke(width = unit * 0.07f),
                    )

                    // La bulle en vol.
                    shot?.let { s ->
                        drawBubble(
                            s.x * unit,
                            s.y * unit,
                            unit * 0.9f,
                            BUBBLE_COLORS[s.color % BUBBLE_COLORS.size],
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        HintBar(
            text = "Visez en glissant, relâchez pour tirer. Trois bulles identiques éclatent.",
            accent = Ink.Pink,
        )
        Spacer(Modifier.height(2.dp))
        AppText(
            text = if (over) "Partie terminée" else "Rangées : $initialRows · $colorCount couleurs",
            style = Typo.small,
            color = Ink.Muted,
        )
    }
}

/** Bulle avec reflet et liseré, pour un rendu un peu moins plat. */
private fun DrawScope.drawBubble(
    cx: Float,
    cy: Float,
    radius: Float,
    color: Color,
) {
    drawCircle(color, radius = radius, center = Offset(cx, cy))
    drawCircle(
        color = Color.White.copy(alpha = 0.32f),
        radius = radius * 0.24f,
        center = Offset(cx - radius * 0.30f, cy - radius * 0.32f),
    )
    drawCircle(
        color = Color.Black.copy(alpha = 0.07f),
        radius = radius * 0.94f,
        center = Offset(cx, cy),
        style = Stroke(width = radius * 0.09f),
    )
}
