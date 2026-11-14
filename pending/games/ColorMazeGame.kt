package com.botillon.games.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
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
import com.botillon.games.ui.SecondaryButton
import com.botillon.games.ui.Sound
import com.botillon.games.ui.Typo
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

/**
 * Labyrinthe de couleurs : trouver la sortie en bas à droite.
 *
 * Le labyrinthe est parfait (une seule route entre deux cases), obtenu par
 * backtracking. Le pion se déplace au doigt, en glissant ou en touchant une
 * case voisine ; les murs arrêtent net. La traînée du chemin parcouru change
 * progressivement de couleur.
 */
object ColorMazeGame {
    val entry = GameEntry(
        id = "labyrinthe",
        title = "Labyrinthe de couleurs",
        tagline = "Rejoindre la sortie",
        family = Family.LOGIC,
        accent = Ink.Violet,
        variants = listOf("Petit", "Moyen", "Grand"),
    ) { variant, _, finish, _ ->
        ColorMaze(variant, finish)
    }
}

/** Bits de mur : nord, est, sud, ouest (dans cet ordre). */
private const val MAZE_N = 1
private const val MAZE_E = 2
private const val MAZE_S = 4
private const val MAZE_W = 8

private fun mazeNeighbor(cell: Int, n: Int, dir: Int): Int = when (dir) {
    0 -> if (cell / n > 0) cell - n else -1
    1 -> if (cell % n < n - 1) cell + 1 else -1
    2 -> if (cell / n < n - 1) cell + n else -1
    else -> if (cell % n > 0) cell - 1 else -1
}

private fun mazeOpposite(bit: Int): Int = when (bit) {
    MAZE_N -> MAZE_S
    MAZE_E -> MAZE_W
    MAZE_S -> MAZE_N
    else -> MAZE_E
}

private class MazeBoard(val walls: IntArray, val exit: Int)

/** Labyrinthe parfait par backtracking : chaque case est atteignable. */
private fun buildMaze(n: Int, rng: Random): MazeBoard {
    val total = n * n
    val walls = IntArray(total) { MAZE_N or MAZE_E or MAZE_S or MAZE_W }
    val visited = BooleanArray(total)
    val stack = ArrayDeque<Int>()
    visited[0] = true
    stack.addLast(0)
    while (stack.isNotEmpty()) {
        val current = stack.last()
        val options = (0..3)
            .mapNotNull { dir -> mazeNeighbor(current, n, dir).takeIf { it >= 0 && !visited[it] } }
        if (options.isEmpty()) {
            stack.removeLast()
            continue
        }
        val next = options[rng.nextInt(options.size)]
        val dir = (0..3).first { mazeNeighbor(current, n, it) == next }
        walls[current] = walls[current] and (1 shl dir).inv()
        walls[next] = walls[next] and mazeOpposite(1 shl dir).inv()
        visited[next] = true
        stack.addLast(next)
    }
    return MazeBoard(walls, total - 1)
}

@Composable
private fun ColorMaze(variant: Int, finish: (Long, Boolean) -> Unit) {
    val accent = Ink.Violet
    val sound = LocalSound.current
    val haptics = LocalHaptics.current
    val n = listOf(7, 9, 11)[variant.coerceIn(0, 2)]

    val maze = remember(variant) { buildMaze(n, Random.Default) }
    var pos by remember { mutableIntStateOf(0) }
    var trail by remember { mutableStateOf(listOf(0)) }
    var seconds by remember { mutableIntStateOf(0) }
    var over by remember { mutableStateOf(false) }
    var reported by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("La sortie est en bas à droite.") }

    // Chrono : une seconde par tour de boucle, arrêté à la victoire.
    LaunchedEffect(over) {
        while (!over) {
            delay(1000)
            if (!over) seconds++
        }
    }

    /** Victoire : le score fond avec le temps écoulé. */
    fun win() {
        if (reported) return
        reported = true
        over = true
        sound.play(Sound.WIN)
        haptics.success()
        finish(maxOf(100L, 1500L - seconds * 20L), true)
    }

    fun moveStep(dir: Int) {
        if (over) return
        val bit = 1 shl dir
        if (maze.walls[pos] and bit != 0) {
            notice = "Un mur bloque le passage."
            sound.play(Sound.UI)
            haptics.error()
            return
        }
        val next = mazeNeighbor(pos, n, dir)
        if (next < 0) return
        pos = next
        val previous = trail.indexOf(next)
        trail = if (previous >= 0) trail.take(previous + 1) else trail + next
        sound.play(Sound.TAP)
        haptics.tap()
        notice = if (next == maze.exit) "Sortie atteinte !" else "Cherchez la sortie en bas à droite."
        if (next == maze.exit) win()
    }

    fun tapCell(cell: Int) {
        if (over || cell == pos) return
        val dx = cell % n - pos % n
        val dy = cell / n - pos / n
        if (abs(dx) + abs(dy) != 1) {
            notice = "Touchez une case voisine du pion, ou glissez le doigt."
            sound.play(Sound.UI)
            return
        }
        val dir = when {
            dx == 1 -> 1
            dx == -1 -> 3
            dy == 1 -> 2
            else -> 0
        }
        moveStep(dir)
    }

    fun reset() {
        if (over) return
        pos = 0
        trail = listOf(0)
        notice = "Retour au départ, le chrono continue."
        sound.play(Sound.UI)
        haptics.tap()
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pill("Temps ${seconds} s", color = Ink.Violet, background = Ink.Violet.copy(alpha = 0.10f))
            Pill("$n × $n", color = Ink.Soft, background = Ink.CardAlt)
            Pill("Chemin ${trail.size - 1}", color = Ink.Soft, background = Ink.CardAlt)
            Spacer(Modifier.weight(1f))
            Pill("Sortie", color = Ink.Green, background = Ink.Green.copy(alpha = 0.12f))
        }
        Spacer(Modifier.height(8.dp))

        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Panel(Modifier.fillMaxWidth().aspectRatio(1f), color = Ink.Card) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .pointerInput(maze, over) {
                            if (over) return@pointerInput
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val side = min(size.width, size.height).toFloat()
                                val margin = side * 0.02f
                                val board = side - margin * 2f
                                val cell = board / n
                                val originX = (size.width - board) / 2f
                                val originY = (size.height - board) / 2f
                                var last = down.position
                                var accX = 0f
                                var accY = 0f
                                var dragged = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) {
                                        if (!dragged) {
                                            val x = ((down.position.x - originX) / cell).toInt()
                                            val y = ((down.position.y - originY) / cell).toInt()
                                            if (x in 0 until n && y in 0 until n) tapCell(y * n + x)
                                        }
                                        break
                                    }
                                    val dx = change.position.x - last.x
                                    val dy = change.position.y - last.y
                                    accX += dx
                                    accY += dy
                                    last = change.position
                                    if (!dragged) {
                                        val travel = abs(change.position.x - down.position.x) +
                                            abs(change.position.y - down.position.y)
                                        if (travel > viewConfiguration.touchSlop) dragged = true
                                    }
                                    if (dragged) {
                                        change.consume()
                                        val step = cell * 0.62f
                                        if (abs(accX) > step || abs(accY) > step) {
                                            if (abs(accX) > abs(accY)) {
                                                moveStep(if (accX > 0) 1 else 3)
                                            } else {
                                                moveStep(if (accY > 0) 2 else 0)
                                            }
                                            accX = 0f
                                            accY = 0f
                                        }
                                    }
                                }
                            }
                        },
                ) {
                    val side = min(size.width, size.height)
                    val margin = side * 0.02f
                    val board = side - margin * 2f
                    val cell = board / n
                    val originX = (size.width - board) / 2f
                    val originY = (size.height - board) / 2f

                    fun centerX(cellIndex: Int) = originX + (cellIndex % n) * cell + cell / 2f
                    fun centerY(cellIndex: Int) = originY + (cellIndex / n) * cell + cell / 2f

                    drawRoundRect(
                        color = Ink.CardAlt.copy(alpha = 0.45f),
                        topLeft = Offset(originX, originY),
                        size = Size(board, board),
                        cornerRadius = CornerRadius(cell * 0.16f),
                    )

                    // Cases de départ et de sortie.
                    drawRoundRect(
                        color = accent.copy(alpha = 0.12f),
                        topLeft = Offset(originX + 1f, originY + 1f),
                        size = Size(cell - 2f, cell - 2f),
                        cornerRadius = CornerRadius(cell * 0.12f),
                    )
                    drawRoundRect(
                        color = Ink.Green.copy(alpha = 0.20f),
                        topLeft = Offset(originX + (n - 1) * cell + 1f, originY + (n - 1) * cell + 1f),
                        size = Size(cell - 2f, cell - 2f),
                        cornerRadius = CornerRadius(cell * 0.12f),
                    )

                    // Traînée colorée du chemin parcouru.
                    for (index in 0 until trail.size - 1) {
                        val a = trail[index]
                        val b = trail[index + 1]
                        val t = if (trail.size <= 2) 0f else index / (trail.size - 2).toFloat()
                        val color = lerp(accent, Ink.Pink, t)
                        drawLine(
                            color = color.copy(alpha = 0.55f),
                            start = Offset(centerX(a), centerY(a)),
                            end = Offset(centerX(b), centerY(b)),
                            strokeWidth = cell * 0.20f,
                            cap = StrokeCap.Round,
                        )
                    }

                    // Murs : chaque case trace son nord et son ouest, plus les
                    // bords est et sud du pourtour.
                    val wallColor = Ink.Text.copy(alpha = 0.82f)
                    val wallWidth = cell * 0.12f
                    for (index in 0 until n * n) {
                        val x = originX + (index % n) * cell
                        val y = originY + (index / n) * cell
                        val walls = maze.walls[index]
                        if (walls and MAZE_N != 0) {
                            drawLine(
                                wallColor,
                                Offset(x, y),
                                Offset(x + cell, y),
                                strokeWidth = wallWidth,
                                cap = StrokeCap.Round,
                            )
                        }
                        if (walls and MAZE_W != 0) {
                            drawLine(
                                wallColor,
                                Offset(x, y),
                                Offset(x, y + cell),
                                strokeWidth = wallWidth,
                                cap = StrokeCap.Round,
                            )
                        }
                        if (index % n == n - 1 && walls and MAZE_E != 0) {
                            drawLine(
                                wallColor,
                                Offset(x + cell, y),
                                Offset(x + cell, y + cell),
                                strokeWidth = wallWidth,
                                cap = StrokeCap.Round,
                            )
                        }
                        if (index / n == n - 1 && walls and MAZE_S != 0) {
                            drawLine(
                                wallColor,
                                Offset(x, y + cell),
                                Offset(x + cell, y + cell),
                                strokeWidth = wallWidth,
                                cap = StrokeCap.Round,
                            )
                        }
                    }

                    // Repère de départ, puis pion.
                    if (pos != 0) {
                        drawCircle(
                            color = accent.copy(alpha = 0.55f),
                            radius = cell * 0.12f,
                            center = Offset(centerX(0), centerY(0)),
                            style = Stroke(width = cell * 0.05f),
                        )
                    }
                    drawCircle(
                        color = Ink.Green,
                        radius = cell * 0.15f,
                        center = Offset(centerX(maze.exit), centerY(maze.exit)),
                    )
                    drawCircle(
                        color = Ink.Card,
                        radius = cell * 0.06f,
                        center = Offset(centerX(maze.exit), centerY(maze.exit)),
                    )

                    val px = centerX(pos)
                    val py = centerY(pos)
                    drawCircle(accent.copy(alpha = 0.20f), radius = cell * 0.38f, center = Offset(px, py))
                    drawCircle(accent, radius = cell * 0.24f, center = Offset(px, py))
                    drawCircle(Ink.Card, radius = cell * 0.09f, center = Offset(px, py))
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        AppText(
            text = notice,
            style = Typo.small,
            color = if (over) Ink.Muted else Ink.Soft,
            align = TextAlign.Center,
            maxLines = 2,
            softWrap = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        SecondaryButton(
            label = "REVENIR AU DÉPART",
            onClick = { reset() },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        HintBar("Glissez le doigt ou touchez une case voisine. Les murs arrêtent le pion.")
    }
}
