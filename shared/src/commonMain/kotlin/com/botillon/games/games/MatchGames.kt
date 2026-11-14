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

// ---------------------------------------------------------------------------
// Trois alignés (match 3)
// ---------------------------------------------------------------------------

object Match3Game {
    val entry = GameEntry(
        id = "aligner",
        title = "Trois alignés",
        tagline = "Échanger deux fruits, aligner trois",
        family = Family.ARCADE,
        accent = Ink.Coral,
        variants = listOf("Détente", "Classique", "Rapide"),
    ) { variant, _, finish, _ ->
        Match3(variant, finish)
    }
}

private const val M3 = 8
private const val M3_COLORS = 5

private fun m3Index(x: Int, y: Int) = y * M3 + x
private fun m3Inside(x: Int, y: Int) = x in 0 until M3 && y in 0 until M3

private fun m3At(board: IntArray, x: Int, y: Int): Int = if (m3Inside(x, y)) board[m3Index(x, y)] else -1

private fun m3Matches(board: IntArray): Set<Int> {
    val result = mutableSetOf<Int>()
    for (y in 0 until M3) {
        var runStart = 0
        for (x in 1..M3) {
            val same = x < M3 && m3At(board, x, y) >= 0 && m3At(board, x, y) == m3At(board, runStart, y)
            if (!same) {
                if (x - runStart >= 3 && m3At(board, runStart, y) >= 0) {
                    for (k in runStart until x) result += m3Index(k, y)
                }
                runStart = x
            }
        }
    }
    for (x in 0 until M3) {
        var runStart = 0
        for (y in 1..M3) {
            val same = y < M3 && m3At(board, x, y) >= 0 && m3At(board, x, y) == m3At(board, x, runStart)
            if (!same) {
                if (y - runStart >= 3 && m3At(board, x, runStart) >= 0) {
                    for (k in runStart until y) result += m3Index(x, k)
                }
                runStart = y
            }
        }
    }
    return result
}

private fun m3Collapse(board: IntArray, random: Random): IntArray {
    val next = board.copyOf()
    for (x in 0 until M3) {
        var write = M3 - 1
        for (y in M3 - 1 downTo 0) {
            val value = next[m3Index(x, y)]
            if (value >= 0) {
                next[m3Index(x, write)] = value
                if (write != y) next[m3Index(x, y)] = -1
                write--
            }
        }
        for (y in write downTo 0) next[m3Index(x, y)] = random.nextInt(M3_COLORS)
    }
    return next
}

private fun m3HasMove(board: IntArray): Boolean {
    for (y in 0 until M3) for (x in 0 until M3) {
        for ((dx, dy) in listOf(1 to 0, 0 to 1)) {
            val nx = x + dx
            val ny = y + dy
            if (!m3Inside(nx, ny)) continue
            val copy = board.copyOf()
            val a = m3Index(x, y)
            val b = m3Index(nx, ny)
            val tmp = copy[a]
            copy[a] = copy[b]
            copy[b] = tmp
            if (m3Matches(copy).isNotEmpty()) return true
        }
    }
    return false
}

private fun m3Fresh(random: Random): IntArray {
    var board = IntArray(M3 * M3) { random.nextInt(M3_COLORS) }
    var guard = 0
    while (m3Matches(board).isNotEmpty() && guard++ < 50) {
        board = IntArray(M3 * M3) { random.nextInt(M3_COLORS) }
    }
    return board
}

@Composable
private fun Match3(variant: Int, finish: (Long, Boolean) -> Unit) {
    val sound = LocalSound.current
    val haptics = LocalHaptics.current
    val seconds = listOf(90, 60, 45)[variant]

    var board by remember { mutableStateOf(m3Fresh(Random.Default)) }
    var score by remember { mutableStateOf(0L) }
    var selected by remember { mutableStateOf(-1) }
    var timeLeft by remember { mutableIntStateOf(seconds) }
    var done by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (!done && timeLeft > 0) {
            delay(1000)
            timeLeft--
        }
        if (!done) {
            done = true
            finish(score, score > 0)
        }
    }

    fun resolveFrom(current: IntArray): Pair<IntArray, Long> {
        var boardNow = current
        var gained = 0L
        var chain = 0
        while (true) {
            val matches = m3Matches(boardNow)
            if (matches.isEmpty()) break
            chain++
            gained += matches.size * 10L * chain
            for (index in matches) boardNow[index] = -1
            boardNow = m3Collapse(boardNow, Random.Default)
        }
        return boardNow to gained
    }

    fun tap(index: Int) {
        if (done) return
        if (selected < 0) {
            selected = index
            sound.play(Sound.TAP)
            return
        }
        if (selected == index) {
            selected = -1
            return
        }
        val sx = selected % M3
        val sy = selected / M3
        val x = index % M3
        val y = index / M3
        val adjacent = (kotlin.math.abs(sx - x) + kotlin.math.abs(sy - y)) == 1
        if (adjacent) {
            val copy = board.copyOf()
            val tmp = copy[selected]
            copy[selected] = copy[index]
            copy[index] = tmp
            if (m3Matches(copy).isNotEmpty()) {
                val (next, gained) = resolveFrom(copy)
                board = next
                score += gained
                sound.play(Sound.MATCH)
                haptics.success()
                if (!m3HasMove(next)) {
                    val (shuffled, extra) = resolveFrom(m3Fresh(Random.Default))
                    board = shuffled
                    score += extra
                }
            } else {
                sound.play(Sound.LOSE)
            }
        }
        selected = -1
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AppText("Score $score", style = Typo.heading, color = Ink.Text)
            AppText("${timeLeft}s", style = Typo.heading, color = if (timeLeft < 15) Ink.Coral else Ink.Soft)
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            Canvas(
                Modifier.fillMaxSize().pointerInput(done) {
                    if (done) return@pointerInput
                    detectTapGestures { offset ->
                        val cell = size.width / M3
                        val x = (offset.x / cell).toInt().coerceIn(0, M3 - 1)
                        val y = (offset.y / cell).toInt().coerceIn(0, M3 - 1)
                        tap(m3Index(x, y))
                    }
                },
            ) {
                val cell = size.width / M3
                drawRoundRect(
                    color = Ink.CardAlt,
                    size = Size(size.width, size.width),
                    cornerRadius = CornerRadius(cell * 0.14f),
                )
                for (index in board.indices) {
                    val value = board[index]
                    if (value < 0) continue
                    val x = index % M3
                    val y = index / M3
                    val inset = cell * 0.06f
                    drawRoundRect(
                        color = Ink.Accents[value % Ink.Accents.size],
                        topLeft = Offset(x * cell + inset, y * cell + inset),
                        size = Size(cell - inset * 2, cell - inset * 2),
                        cornerRadius = CornerRadius(cell * 0.22f),
                    )
                    if (index == selected) {
                        drawRoundRect(
                            color = Color.White,
                            topLeft = Offset(x * cell + inset, y * cell + inset),
                            size = Size(cell - inset * 2, cell - inset * 2),
                            cornerRadius = CornerRadius(cell * 0.22f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(cell * 0.05f),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        HintBar("Touchez deux cases voisines pour les échanger. Trois identiques ou plus disparaissent.")
    }
}

// ---------------------------------------------------------------------------
// Color Match (SameGame)
// ---------------------------------------------------------------------------

object ColorMatchGame {
    val entry = GameEntry(
        id = "couleurs",
        title = "Couleurs",
        tagline = "Faire disparaître les groupes",
        family = Family.LOGIC,
        accent = Ink.Violet,
        variants = listOf("8 × 10", "10 × 12", "12 × 14"),
    ) { variant, _, finish, _ ->
        ColorMatch(variant, finish)
    }
}

@Composable
private fun ColorMatch(variant: Int, finish: (Long, Boolean) -> Unit) {
    val cols = listOf(8, 10, 12)[variant]
    val rows = listOf(10, 12, 14)[variant]
    val colors = if (variant == 0) 4 else 5
    val sound = LocalSound.current
    val haptics = LocalHaptics.current

    var board by remember { mutableStateOf(IntArray(cols * rows) { Random.nextInt(colors) }) }
    var score by remember { mutableStateOf(0L) }
    var moves by remember { mutableIntStateOf(0) }
    var done by remember { mutableStateOf(false) }
    val maxMoves = 30

    fun neighbors(index: Int): List<Int> {
        val x = index % cols
        val y = index / cols
        return buildList {
            if (x > 0) add(index - 1)
            if (x < cols - 1) add(index + 1)
            if (y > 0) add(index - cols)
            if (y < rows - 1) add(index + cols)
        }
    }

    fun groupOf(index: Int): Set<Int> {
        val color = board[index]
        if (color < 0) return emptySet()
        val seen = mutableSetOf(index)
        val queue = ArrayDeque<Int>().apply { add(index) }
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (n in neighbors(current)) {
                if (n !in seen && board[n] == color) {
                    seen += n
                    queue += n
                }
            }
        }
        return seen
    }

    fun applyGravity(source: IntArray): IntArray {
        val next = source.copyOf()
        for (x in 0 until cols) {
            var write = rows - 1
            for (y in rows - 1 downTo 0) {
                val value = next[y * cols + x]
                if (value >= 0) {
                    next[write * cols + x] = value
                    if (write != y) next[y * cols + x] = -1
                    write--
                }
            }
        }
        // Colonnes vides : on tasse vers la gauche.
        var writeCol = 0
        for (x in 0 until cols) {
            val empty = (0 until rows).all { next[it * cols + x] < 0 }
            if (!empty) {
                if (writeCol != x) {
                    for (y in 0 until rows) {
                        next[y * cols + writeCol] = next[y * cols + x]
                        next[y * cols + x] = -1
                    }
                }
                writeCol++
            }
        }
        return next
    }

    fun anyGroup(): Boolean = board.indices.any { i -> board[i] >= 0 && groupOf(i).size >= 2 }

    fun tap(index: Int) {
        if (done) return
        val group = groupOf(index)
        if (group.size < 2) {
            sound.play(Sound.TAP)
            return
        }
        val gain = group.size.toLong() * (group.size - 1) * 5
        val next = board.copyOf()
        for (i in group) next[i] = -1
        board = applyGravity(next)
        score += gain
        moves++
        sound.play(Sound.MATCH)
        haptics.success()
        if (moves >= maxMoves || !anyGroup()) {
            done = true
            finish(score, score > 0)
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AppText("Score $score", style = Typo.heading, color = Ink.Text)
            AppText("Coups $moves/$maxMoves", style = Typo.small, color = Ink.Muted)
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().aspectRatio(cols / rows.toFloat())) {
            Canvas(
                Modifier.fillMaxSize().pointerInput(done) {
                    if (done) return@pointerInput
                    detectTapGestures { offset ->
                        val cell = size.width / cols
                        val x = (offset.x / cell).toInt().coerceIn(0, cols - 1)
                        val y = (offset.y / (size.height / rows)).toInt().coerceIn(0, rows - 1)
                        tap(y * cols + x)
                    }
                },
            ) {
                val cellW = size.width / cols
                val cellH = size.height / rows
                for (index in board.indices) {
                    val value = board[index]
                    if (value < 0) continue
                    val x = index % cols
                    val y = index / cols
                    val side = minOf(cellW, cellH)
                    drawRoundRect(
                        color = Ink.Accents[value % Ink.Accents.size],
                        topLeft = Offset(x * cellW + side * 0.06f, y * cellH + side * 0.06f),
                        size = Size(side * 0.88f, side * 0.88f),
                        cornerRadius = CornerRadius(side * 0.22f),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        HintBar("Touchez un groupe de deux cases ou plus de la même couleur pour le faire disparaître.")
    }
}
