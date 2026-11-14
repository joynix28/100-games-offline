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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
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
import com.botillon.games.ui.PrimaryButton
import com.botillon.games.ui.Sound
import com.botillon.games.ui.Typo
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Sudoku classique 9 × 9, trois difficultés (40, 50 ou 58 cases retirées).
 *
 * La grille pleine est tirée par backtracking aléatoire. Les cases sont ensuite
 * retirées une par une et l'on ne conserve le retrait que si la grille admet
 * encore exactement une solution (comptage arrêté à deux). Plusieurs grilles
 * sont tentées pour atteindre le nombre de cases demandé ; si aucune n'y arrive,
 * la meilleure est gardée : elle reste valide et admet au moins une solution.
 */
object SudokuGame {
    val entry = GameEntry(
        id = "sudoku",
        title = "Sudoku",
        tagline = "Remplir la grille",
        family = Family.LOGIC,
        accent = Ink.Blue,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        Sudoku(variant, finish)
    }
}

private const val SUDOKU_CELLS = 81
private const val SUDOKU_FULL_MASK = 0x1FF

/** Grille de départ (0 = case vide) et sa solution complète. */
private data class SudokuPuzzle(val puzzle: IntArray, val solution: IntArray)

/** Nombre de bits à 1 dans [mask] (pas d'appel à une API Java). */
private fun sudokuBitCount(mask: Int): Int {
    var value = mask
    var count = 0
    while (value != 0) {
        value = value and (value - 1)
        count += 1
    }
    return count
}

/** Position du seul bit à 1 de [bit] (0 pour le chiffre 1, 8 pour le 9). */
private fun sudokuBitIndex(bit: Int): Int {
    var value = bit
    var index = 0
    while ((value and 1) == 0) {
        value = value shr 1
        index += 1
    }
    return index
}

/** Vrai si [value] peut déjà se placer en [index] sans conflit. */
private fun sudokuAllows(board: IntArray, index: Int, value: Int): Boolean {
    val row = index / 9
    val col = index % 9
    for (i in 0 until 9) {
        if (board[row * 9 + i] == value) return false
        if (board[i * 9 + col] == value) return false
    }
    val top = (row / 3) * 3
    val left = (col / 3) * 3
    for (r in 0 until 3) {
        for (c in 0 until 3) {
            if (board[(top + r) * 9 + left + c] == value) return false
        }
    }
    return true
}

/**
 * Remplit une grille vide par backtracking, en essayant les chiffres dans un
 * ordre aléatoire. Le résultat est toujours un sudoku complet et valide.
 */
private fun sudokuSolvedGrid(rng: Random): IntArray {
    val board = IntArray(SUDOKU_CELLS)

    fun fill(index: Int): Boolean {
        if (index == SUDOKU_CELLS) return true
        for (value in (1..9).shuffled(rng)) {
            if (!sudokuAllows(board, index, value)) continue
            board[index] = value
            if (fill(index + 1)) return true
            board[index] = 0
        }
        return false
    }

    fill(0)
    return board
}

/**
 * Compte les solutions de [board], en s'arrêtant dès que [limit] est atteint.
 * Choix de la case la plus contrainte (MRV) et masques de bits pour aller vite.
 * La grille passée en argument est laissée intacte.
 */
private fun sudokuCountSolutions(board: IntArray, limit: Int): Int {
    val rows = IntArray(9)
    val cols = IntArray(9)
    val boxes = IntArray(9)

    for (index in 0 until SUDOKU_CELLS) {
        val value = board[index]
        if (value == 0) continue
        val bit = 1 shl (value - 1)
        val row = index / 9
        val col = index % 9
        val box = (row / 3) * 3 + col / 3
        rows[row] = rows[row] or bit
        cols[col] = cols[col] or bit
        boxes[box] = boxes[box] or bit
    }

    var found = 0

    fun search(): Boolean {
        var target = -1
        var targetMask = 0
        var fewest = 10

        for (index in 0 until SUDOKU_CELLS) {
            if (board[index] != 0) continue
            val row = index / 9
            val col = index % 9
            val box = (row / 3) * 3 + col / 3
            val mask = SUDOKU_FULL_MASK and (rows[row] or cols[col] or boxes[box]).inv()
            val choices = sudokuBitCount(mask)
            if (choices == 0) return false
            if (choices < fewest) {
                fewest = choices
                target = index
                targetMask = mask
                if (choices == 1) break
            }
        }

        if (target < 0) {
            found += 1
            return found >= limit
        }

        val row = target / 9
        val col = target % 9
        val box = (row / 3) * 3 + col / 3
        var mask = targetMask
        while (mask != 0) {
            val bit = mask and (-mask)
            mask = mask xor bit
            val value = sudokuBitIndex(bit) + 1
            board[target] = value
            rows[row] = rows[row] or bit
            cols[col] = cols[col] or bit
            boxes[box] = boxes[box] or bit

            val reached = search()

            board[target] = 0
            rows[row] = rows[row] xor bit
            cols[col] = cols[col] xor bit
            boxes[box] = boxes[box] xor bit

            if (reached || found >= limit) return true
        }
        return false
    }

    search()
    return found
}

/** Tire une grille complète puis retire des cases en gardant une solution unique. */
private fun generateSudoku(removed: Int, rng: Random): SudokuPuzzle {
    var best = SudokuPuzzle(IntArray(SUDOKU_CELLS), IntArray(SUDOKU_CELLS))
    var bestRemoved = -1

    repeat(20) {
        val solution = sudokuSolvedGrid(rng)
        val puzzle = solution.copyOf()
        var taken = 0

        for (index in (0 until SUDOKU_CELLS).shuffled(rng)) {
            if (taken >= removed) break
            val backup = puzzle[index]
            puzzle[index] = 0
            if (sudokuCountSolutions(puzzle, 2) == 1) {
                taken += 1
            } else {
                puzzle[index] = backup
            }
        }

        if (taken > bestRemoved) {
            bestRemoved = taken
            best = SudokuPuzzle(puzzle, solution)
        }
        if (taken >= removed) return best
    }

    return best
}

/** Chronomètre au format mm:ss. */
private fun sudokuClock(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}

@Composable
private fun SudokuLoading() {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Panel {
            Column(
                Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppText("Préparation de la grille…", style = Typo.heading, color = Ink.Text)
                Spacer(Modifier.height(6.dp))
                AppText("Une solution unique, vérifiée.", style = Typo.small, color = Ink.Muted)
            }
        }
    }
}

@Composable
private fun Sudoku(variant: Int, finish: (Long, Boolean) -> Unit) {
    val removed = listOf(40, 50, 58)[variant]
    val hardMode = variant == 2
    val sound = LocalSound.current
    val haptics = LocalHaptics.current
    val textMeasurer = rememberTextMeasurer()

    var puzzle by remember { mutableStateOf<SudokuPuzzle?>(null) }

    // La génération est un peu coûteuse : on affiche d'abord la carte d'attente.
    LaunchedEffect(variant) {
        delay(20)
        puzzle = generateSudoku(removed, Random.Default)
    }

    val start = puzzle
    if (start == null) {
        SudokuLoading()
        return
    }

    var entries by remember(start) { mutableStateOf(start.puzzle.copyOf()) }
    var marked by remember(start) { mutableStateOf(IntArray(SUDOKU_CELLS)) }
    var selected by remember(start) { mutableIntStateOf(-1) }
    var errors by remember(start) { mutableIntStateOf(0) }
    var seconds by remember(start) { mutableIntStateOf(0) }
    var finished by remember(start) { mutableStateOf(false) }
    var reported by remember(start) { mutableStateOf(false) }

    // Le chrono démarre dès que la grille est prête.
    LaunchedEffect(start, finished) {
        if (finished) return@LaunchedEffect
        while (true) {
            delay(1000)
            seconds += 1
        }
    }

    fun enter(value: Int) {
        if (finished) return
        val index = selected
        if (index < 0 || start.puzzle[index] != 0) return
        // Rejouer le même chiffre déjà marqué faux ne recompte pas d'erreur.
        if (marked[index] == 1 && entries[index] == value) return

        if (value == start.solution[index]) {
            val next = entries.copyOf()
            next[index] = value
            entries = next
            if (marked[index] == 1) {
                marked = marked.copyOf().also { it[index] = 0 }
            }
            sound.play(Sound.TAP)
            haptics.tap()
            if (next.indices.all { next[it] == start.solution[it] }) {
                finished = true
                if (!reported) {
                    reported = true
                    sound.play(Sound.WIN)
                    haptics.success()
                    finish(maxOf(0L, 1000L - errors * 80L), true)
                }
            }
        } else {
            entries = entries.copyOf().also { it[index] = value }
            marked = marked.copyOf().also { it[index] = 1 }
            errors += 1
            sound.play(Sound.LOSE)
            haptics.error()
            if (hardMode && errors >= 3 && !reported) {
                reported = true
                finished = true
                finish(0L, false)
            }
        }
    }

    fun erase() {
        if (finished) return
        val index = selected
        if (index < 0 || start.puzzle[index] != 0) return
        if (entries[index] == 0) return
        entries = entries.copyOf().also { it[index] = 0 }
        marked = marked.copyOf().also { it[index] = 0 }
        sound.play(Sound.TAP)
        haptics.tap()
    }

    val selectedRow = if (selected >= 0) selected / 9 else -1
    val selectedCol = if (selected >= 0) selected % 9 else -1
    val selectedBox = if (selected >= 0) (selectedRow / 3) * 3 + selectedCol / 3 else -1
    val emptyCount = start.puzzle.count { it == 0 }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pill(
                text = if (hardMode) "ERREURS $errors / 3" else "ERREURS $errors",
                color = if (errors > 0) Ink.Coral else Ink.Soft,
                background = if (errors > 0) Ink.Coral.copy(alpha = 0.12f) else Ink.CardAlt,
            )
            Pill(text = "TEMPS ${sudokuClock(seconds)}", color = Ink.Soft)
            Spacer(Modifier.weight(1f))
            AppText("$emptyCount cases vides", style = Typo.small, color = Ink.Muted)
        }

        Spacer(Modifier.height(10.dp))

        Panel(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            color = Ink.Card,
        ) {
            Box(Modifier.fillMaxSize().padding(6.dp)) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(start, finished) {
                            if (finished) return@pointerInput
                            detectTapGestures { offset ->
                                val inset = 2.dp.toPx()
                                val cell = (size.width - inset * 2f) / 9f
                                if (offset.x < inset || offset.y < inset) return@detectTapGestures
                                val col = ((offset.x - inset) / cell).toInt().coerceIn(0, 8)
                                val row = ((offset.y - inset) / cell).toInt().coerceIn(0, 8)
                                selected = row * 9 + col
                                sound.play(Sound.TAP)
                                haptics.tap()
                            }
                        },
                ) {
                    val inset = 2.dp.toPx()
                    val boardSize = size.width - inset * 2f
                    val cell = boardSize / 9f

                    for (index in 0 until SUDOKU_CELLS) {
                        val row = index / 9
                        val col = index % 9
                        val box = (row / 3) * 3 + col / 3
                        val peer = selected >= 0 && index != selected &&
                            (row == selectedRow || col == selectedCol || box == selectedBox)
                        val background = when {
                            marked[index] == 1 -> Ink.Coral.copy(alpha = 0.22f)
                            index == selected -> Ink.Blue.copy(alpha = 0.20f)
                            peer -> Ink.Blue.copy(alpha = 0.06f)
                            start.puzzle[index] != 0 -> Ink.BgDeep.copy(alpha = 0.55f)
                            else -> Color.Transparent
                        }
                        if (background != Color.Transparent) {
                            drawRect(
                                color = background,
                                topLeft = Offset(inset + col * cell, inset + row * cell),
                                size = Size(cell, cell),
                            )
                        }
                    }

                    // Séparateurs fins, puis traits épais tous les trois carreaux.
                    for (i in 0..9) {
                        val thick = i % 3 == 0
                        val color = if (thick) Ink.Text.copy(alpha = 0.55f) else Ink.Line
                        val width = if (thick) 2.2.dp.toPx() else 0.8.dp.toPx()
                        val position = inset + i * cell
                        drawLine(
                            color = color,
                            start = Offset(position, inset),
                            end = Offset(position, inset + boardSize),
                            strokeWidth = width,
                        )
                        drawLine(
                            color = color,
                            start = Offset(inset, position),
                            end = Offset(inset + boardSize, position),
                            strokeWidth = width,
                        )
                    }

                    if (selected >= 0) {
                        val row = selected / 9
                        val col = selected % 9
                        drawRect(
                            color = Ink.Blue.copy(alpha = 0.70f),
                            topLeft = Offset(inset + col * cell, inset + row * cell),
                            size = Size(cell, cell),
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }

                    val digitSize = TextUnit(cell * 0.52f / density, TextUnitType.Sp)
                    for (index in 0 until SUDOKU_CELLS) {
                        val value = entries[index]
                        if (value == 0) continue
                        val fixed = start.puzzle[index] != 0
                        val style = TextStyle(
                            color = when {
                                marked[index] == 1 -> Ink.Coral
                                fixed -> Ink.Text
                                else -> Ink.Blue
                            },
                            fontSize = digitSize,
                            fontWeight = FontWeight.Bold,
                        )
                        val text = "$value"
                        val measured = textMeasurer.measure(text, style)
                        val cx = inset + (index % 9) * cell + cell / 2f
                        val cy = inset + (index / 9) * cell + cell / 2f
                        drawText(
                            textMeasurer = textMeasurer,
                            text = text,
                            topLeft = Offset(
                                cx - measured.size.width / 2f,
                                cy - measured.size.height / 2f,
                            ),
                            style = style,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (digit in 1..5) {
                    PrimaryButton(
                        label = "$digit",
                        onClick = { enter(digit) },
                        modifier = Modifier.weight(1f),
                        color = Ink.Card,
                        textColor = Ink.Blue,
                        enabled = !finished,
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (digit in 6..9) {
                    PrimaryButton(
                        label = "$digit",
                        onClick = { enter(digit) },
                        modifier = Modifier.weight(1f),
                        color = Ink.Card,
                        textColor = Ink.Blue,
                        enabled = !finished,
                    )
                }
                PrimaryButton(
                    label = "EFFACER",
                    onClick = { erase() },
                    modifier = Modifier.weight(1f),
                    color = Ink.Card,
                    textColor = Ink.Coral,
                    enabled = !finished,
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        HintBar("Sélectionnez une case puis un chiffre.", accent = Ink.Blue)
    }
}
