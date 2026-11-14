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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlin.math.min
import kotlin.random.Random

/**
 * Tuyaux : relier le départ vert à l'arrivée rouge.
 *
 * La grille est d'abord construite sur un chemin valide, puis complétée par
 * des tuyaux décoratifs symétriques. Toutes les rotations sont ensuite
 * mélangées : le joueur les remet dans le bon sens, un quart de tour à la fois.
 * Une ouverture qui ne tombe sur rien est une fuite et empêche la victoire.
 */
object PipeGame {
    val entry = GameEntry(
        id = "tuyaux",
        title = "Tuyaux",
        tagline = "Relier le départ à l'arrivée",
        family = Family.LOGIC,
        accent = Ink.Teal,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        Pipe(variant, finish)
    }
}

/** Bits d'ouverture : nord, est, sud, ouest (dans cet ordre). */
private const val PIPE_N = 1
private const val PIPE_E = 2
private const val PIPE_S = 4
private const val PIPE_W = 8

private fun pipeOpposite(bit: Int): Int = when (bit) {
    PIPE_N -> PIPE_S
    PIPE_E -> PIPE_W
    PIPE_S -> PIPE_N
    else -> PIPE_E
}

/** Voisin dans la direction `dir` (0 nord, 1 est, 2 sud, 3 ouest), ou -1. */
private fun pipeNeighbor(cell: Int, n: Int, dir: Int): Int = when (dir) {
    0 -> if (cell / n > 0) cell - n else -1
    1 -> if (cell % n < n - 1) cell + 1 else -1
    2 -> if (cell / n < n - 1) cell + n else -1
    else -> if (cell % n > 0) cell - 1 else -1
}

/** Quart de tour horaire d'un masque d'ouvertures. */
private fun pipeRotate(mask: Int, turns: Int): Int {
    var m = mask
    repeat(turns) {
        m = ((m shl 1) and 0b1111) or (if (m and PIPE_W != 0) PIPE_N else 0)
    }
    return m
}

private class PipeLevel(
    val masks: List<Int>,
    val scramble: List<Int>,
    val start: Int,
    val end: Int,
)

/** Réseau atteint depuis le départ, et présence de fuites. */
private class PipeFlow(val cells: Set<Int>, val reached: Boolean)

private fun pipeFlow(masks: List<Int>, n: Int, start: Int, end: Int): PipeFlow {
    val seen = BooleanArray(masks.size)
    val cells = mutableSetOf<Int>()
    val queue = ArrayDeque<Int>()
    seen[start] = true
    queue.addLast(start)
    var leak = false
    while (queue.isNotEmpty()) {
        val cell = queue.removeFirst()
        cells += cell
        for (dir in 0..3) {
            val bit = 1 shl dir
            if (masks[cell] and bit == 0) continue
            val nb = pipeNeighbor(cell, n, dir)
            if (nb < 0 || masks[nb] and pipeOpposite(bit) == 0) {
                leak = true
                continue
            }
            if (!seen[nb]) {
                seen[nb] = true
                queue.addLast(nb)
            }
        }
    }
    return PipeFlow(cells, seen[end] && !leak)
}

private fun buildPipeLevel(n: Int, rng: Random): PipeLevel {
    val total = n * n

    // 1. Labyrinthe parfait : arêtes tirées au hasard, sans boucle.
    val links = mutableListOf<Pair<Int, Int>>()
    val visited = BooleanArray(total)
    val stack = ArrayDeque<Int>()
    visited[0] = true
    stack.addLast(0)
    while (stack.isNotEmpty()) {
        val current = stack.last()
        val options = (0..3)
            .mapNotNull { dir -> pipeNeighbor(current, n, dir).takeIf { it >= 0 && !visited[it] } }
        if (options.isEmpty()) {
            stack.removeLast()
            continue
        }
        val next = options[rng.nextInt(options.size)]
        visited[next] = true
        links += current to next
        stack.addLast(next)
    }

    // 2. Chemin unique du départ à l'arrivée dans ce labyrinthe.
    val adjacency = Array(total) { mutableListOf<Int>() }
    links.forEach { (a, b) ->
        adjacency[a] += b
        adjacency[b] += a
    }
    val parent = IntArray(total) { -1 }
    val seen = BooleanArray(total)
    val queue = ArrayDeque<Int>()
    seen[0] = true
    queue.addLast(0)
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        for (nb in adjacency[current]) {
            if (!seen[nb]) {
                seen[nb] = true
                parent[nb] = current
                queue.addLast(nb)
            }
        }
    }
    val path = mutableListOf<Int>()
    var walk = total - 1
    while (walk >= 0) {
        path += walk
        if (walk == 0) break
        walk = parent[walk]
    }
    path.reverse()
    val onPath = BooleanArray(total)
    path.forEach { onPath[it] = true }

    // 3. Tuyaux du chemin, puis décorations symétriques hors du chemin.
    val masks = IntArray(total)
    fun connect(a: Int, b: Int) {
        val dir = (0..3).first { pipeNeighbor(a, n, it) == b }
        masks[a] = masks[a] or (1 shl dir)
        masks[b] = masks[b] or pipeOpposite(1 shl dir)
    }
    path.zipWithNext().forEach { (a, b) -> connect(a, b) }
    for (cell in 0 until total) {
        if (onPath[cell]) continue
        for (dir in 0..3) {
            val nb = pipeNeighbor(cell, n, dir)
            if (nb < 0 || onPath[nb] || nb < cell) continue
            if (rng.nextFloat() < 0.46f) connect(cell, nb)
        }
    }

    // 4. Rotations mélangées, jamais déjà gagnantes.
    val start = 0
    val end = total - 1
    val scramble = MutableList(total) { 0 }
    var attempt = 0
    do {
        for (i in 0 until total) {
            scramble[i] = if (i == start || i == end) 0 else rng.nextInt(4)
        }
        attempt++
    } while (attempt < 40 && pipeFlow(
            masks.mapIndexed { i, mask -> pipeRotate(mask, scramble[i]) },
            n,
            start,
            end,
        ).reached
    )

    return PipeLevel(masks.toList(), scramble.toList(), start, end)
}

@Composable
private fun Pipe(variant: Int, finish: (Long, Boolean) -> Unit) {
    val accent = Ink.Teal
    val sound = LocalSound.current
    val haptics = LocalHaptics.current
    val n = listOf(5, 7, 9)[variant.coerceIn(0, 2)]

    val level = remember(variant) { buildPipeLevel(n, Random.Default) }
    var rots by remember { mutableStateOf(level.scramble) }
    var moves by remember { mutableIntStateOf(0) }
    var over by remember { mutableStateOf(false) }
    var reported by remember { mutableStateOf(false) }

    val display = level.masks.mapIndexed { index, mask -> pipeRotate(mask, rots[index]) }
    val flow = pipeFlow(display, n, level.start, level.end)

    fun checkWin(masks: List<Int>) {
        if (over || reported) return
        if (!pipeFlow(masks, n, level.start, level.end).reached) return
        reported = true
        over = true
        sound.play(Sound.WIN)
        haptics.success()
        finish(maxOf(100L, 1200L - moves * 10L), true)
    }

    fun rotateCell(cell: Int) {
        if (over || cell == level.start || cell == level.end) return
        // Une case sans tuyau ne tourne pas : inutile de compter un coup.
        if (level.masks[cell] == 0) return
        val next = rots.toMutableList()
        next[cell] = (next[cell] + 1) % 4
        rots = next
        moves++
        sound.play(Sound.TAP)
        haptics.tap()
        checkWin(level.masks.mapIndexed { index, mask -> pipeRotate(mask, next[index]) })
    }

    fun shuffle() {
        if (over) return
        rots = level.masks.indices.map { index ->
            if (index == level.start || index == level.end) 0 else Random.nextInt(4)
        }
        moves = 0
        sound.play(Sound.FLIP)
        haptics.tap()
        checkWin(level.masks.mapIndexed { index, mask -> pipeRotate(mask, rots[index]) })
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pill("Coups $moves", color = Ink.Teal, background = Ink.Teal.copy(alpha = 0.10f))
            Pill("$n × $n", color = Ink.Soft, background = Ink.CardAlt)
            Spacer(Modifier.weight(1f))
            Pill(
                text = if (over) "Relié !" else "À relier",
                color = if (over) Ink.Teal else Ink.Muted,
                background = if (over) Ink.Teal.copy(alpha = 0.12f) else Ink.CardAlt,
            )
        }
        Spacer(Modifier.height(6.dp))
        AppText(
            text = "Réseau alimenté : ${flow.cells.size} case(s)",
            style = Typo.small,
            color = Ink.Muted,
            align = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Panel(Modifier.fillMaxWidth().aspectRatio(1f), color = Ink.Card) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                        .pointerInput(level, over) {
                            if (over) return@pointerInput
                            detectTapGestures { offset ->
                                val side = min(size.width, size.height).toFloat()
                                val cell = side / n
                                val originX = (size.width - side) / 2f
                                val originY = (size.height - side) / 2f
                                val x = ((offset.x - originX) / cell).toInt()
                                val y = ((offset.y - originY) / cell).toInt()
                                if (x in 0 until n && y in 0 until n) rotateCell(y * n + x)
                            }
                        },
                ) {
                    val side = min(size.width, size.height)
                    val cell = side / n
                    val originX = (size.width - side) / 2f
                    val originY = (size.height - side) / 2f

                    // Damier très discret pour lire la grille.
                    for (index in 0 until n * n) {
                        val x = originX + (index % n) * cell
                        val y = originY + (index / n) * cell
                        if ((index / n + index % n) % 2 == 0) {
                            drawRect(
                                color = Ink.CardAlt.copy(alpha = 0.55f),
                                topLeft = Offset(x, y),
                                size = Size(cell, cell),
                            )
                        }
                    }

                    // Teintes des deux extrémités.
                    for (index in intArrayOf(level.start, level.end)) {
                        val isStart = index == level.start
                        val x = originX + (index % n) * cell
                        val y = originY + (index / n) * cell
                        drawRoundRect(
                            color = (if (isStart) Ink.Green else Ink.Coral).copy(alpha = 0.14f),
                            topLeft = Offset(x + 1f, y + 1f),
                            size = Size(cell - 2f, cell - 2f),
                            cornerRadius = CornerRadius(cell * 0.14f),
                        )
                    }

                    // Tuyaux : halo des connexions actives, puis trait principal.
                    for (index in 0 until n * n) {
                        val mask = display[index]
                        val x = originX + (index % n) * cell
                        val y = originY + (index / n) * cell
                        if (mask == 0) {
                            drawCircle(
                                color = Ink.Line,
                                radius = cell * 0.09f,
                                center = Offset(x + cell / 2f, y + cell / 2f),
                            )
                            continue
                        }
                        val active = index in flow.cells
                        if (active) {
                            drawPipeCell(
                                x = x,
                                y = y,
                                cellSize = cell,
                                mask = mask,
                                color = accent.copy(alpha = 0.20f),
                                width = cell * 0.36f,
                            )
                        }
                        drawPipeCell(
                            x = x,
                            y = y,
                            cellSize = cell,
                            mask = mask,
                            color = if (active) accent else Ink.Soft.copy(alpha = 0.55f),
                            width = cell * 0.19f,
                        )
                    }

                    // Départ vert et arrivée rouge, posés par-dessus les tuyaux.
                    for (index in intArrayOf(level.start, level.end)) {
                        val isStart = index == level.start
                        val color = if (isStart) Ink.Green else Ink.Coral
                        val center = Offset(
                            originX + (index % n) * cell + cell / 2f,
                            originY + (index / n) * cell + cell / 2f,
                        )
                        drawCircle(color.copy(alpha = 0.18f), radius = cell * 0.40f, center = center)
                        drawCircle(
                            color = color,
                            radius = cell * 0.30f,
                            center = center,
                            style = Stroke(width = cell * 0.09f),
                        )
                        drawCircle(color, radius = cell * 0.10f, center = center)
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        SecondaryButton(
            label = "MÉLANGER LES TUYAUX",
            onClick = { shuffle() },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        HintBar("Touchez un tuyau pour le faire tourner. Une ouverture dans le vide fuit : tout doit être relié.")
    }
}

private fun DrawScope.drawPipeCell(
    x: Float,
    y: Float,
    cellSize: Float,
    mask: Int,
    color: Color,
    width: Float,
) {
    val cx = x + cellSize / 2f
    val cy = y + cellSize / 2f
    for (dir in 0..3) {
        if (mask and (1 shl dir) == 0) continue
        val ex = when (dir) {
            0 -> cx
            1 -> x + cellSize
            2 -> cx
            else -> x
        }
        val ey = when (dir) {
            0 -> y
            1 -> cy
            2 -> y + cellSize
            else -> cy
        }
        drawLine(color, Offset(cx, cy), Offset(ex, ey), strokeWidth = width, cap = StrokeCap.Round)
    }
    drawCircle(color, radius = width / 2f, center = Offset(cx, cy))
}
