package com.botillon.games.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.botillon.games.ui.AppText
import com.botillon.games.ui.Family
import com.botillon.games.ui.GameEntry
import com.botillon.games.ui.HintBar
import com.botillon.games.ui.Ink
import com.botillon.games.ui.LocalHaptics
import com.botillon.games.ui.LocalSound
import com.botillon.games.ui.Pill
import com.botillon.games.ui.Sound
import com.botillon.games.ui.Typo
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Mots mêlés : une grille de 10 × 10, six mots par thème, placés dans les huit
 * directions (y compris à l'envers). On touche la première lettre puis la
 * dernière — ou on glisse — pour valider le mot.
 */
object WordSearchGame {
    val entry = GameEntry(
        id = "motsmeles",
        title = "Mots mêlés",
        tagline = "Retrouver les mots cachés",
        family = Family.WORDS,
        accent = Ink.Green,
        variants = listOf("Animaux", "Voyage", "Cuisine"),
    ) { variant, _, finish, _ ->
        WordSearch(variant, finish)
    }
}

private data class WordTheme(val name: String, val words: List<String>)

private val WordThemes = listOf(
    WordTheme("Animaux", listOf("CHAT", "CHIEN", "LION", "TIGRE", "OURS", "RENARD")),
    WordTheme("Voyage", listOf("TRAIN", "AVION", "BATEAU", "ROUTE", "HOTEL", "VALISE")),
    WordTheme("Cuisine", listOf("PAIN", "SOUPE", "GATEAU", "RIZ", "SEL", "FOUR")),
)

private const val GridSize = 10

/** Les huit orientations : horizontale, verticale et les deux diagonales. */
private val Directions = listOf(
    1 to 0, -1 to 0,
    0 to 1, 0 to -1,
    1 to 1, 1 to -1,
    -1 to 1, -1 to -1,
)

/** Remplit une grille neuve, en mélangeant les placements jusqu'à ce que tout tienne. */
private fun buildGrid(words: List<String>, random: Random): CharArray {
    repeat(200) {
        val cells = CharArray(GridSize * GridSize) { ' ' }
        var complete = true
        for (word in words) {
            if (!placeWord(cells, word, random)) {
                complete = false
                break
            }
        }
        if (complete) {
            for (index in cells.indices) {
                if (cells[index] == ' ') cells[index] = 'A' + random.nextInt(26)
            }
            return cells
        }
    }
    // Repli très rare : les mots à l'horizontale, une ligne chacun.
    val cells = CharArray(GridSize * GridSize) { 'A' + random.nextInt(26) }
    words.forEachIndexed { row, word ->
        val line = row % GridSize
        word.forEachIndexed { column, letter ->
            if (column < GridSize) cells[line * GridSize + column] = letter
        }
    }
    return cells
}

private fun placeWord(cells: CharArray, word: String, random: Random): Boolean {
    repeat(500) {
        val direction = Directions[random.nextInt(Directions.size)]
        val dx = direction.first
        val dy = direction.second
        val startX = random.nextInt(GridSize)
        val startY = random.nextInt(GridSize)
        val endX = startX + dx * (word.length - 1)
        val endY = startY + dy * (word.length - 1)
        if (endX !in 0 until GridSize || endY !in 0 until GridSize) return@repeat
        var fits = true
        for (i in word.indices) {
            val index = (startY + dy * i) * GridSize + (startX + dx * i)
            val current = cells[index]
            if (current != ' ' && current != word[i]) {
                fits = false
                break
            }
        }
        if (!fits) return@repeat
        for (i in word.indices) {
            cells[(startY + dy * i) * GridSize + (startX + dx * i)] = word[i]
        }
        return true
    }
    return false
}

/** Cases d'une ligne droite entre deux cases, ou null si le tracé est tordu. */
private fun lineCells(from: Int, to: Int, size: Int): List<Int>? {
    val ax = from % size
    val ay = from / size
    val bx = to % size
    val by = to / size
    val dx = when {
        bx > ax -> 1
        bx < ax -> -1
        else -> 0
    }
    val dy = when {
        by > ay -> 1
        by < ay -> -1
        else -> 0
    }
    val stepsX = abs(bx - ax)
    val stepsY = abs(by - ay)
    if (stepsX != 0 && stepsY != 0 && stepsX != stepsY) return null
    val steps = max(stepsX, stepsY)
    if (steps == 0) return null
    return (0..steps).map { step -> (ay + dy * step) * size + (ax + dx * step) }
}

/** Case sous le doigt, ou null en dehors de la grille. */
private fun cellAt(position: Offset, width: Float, height: Float, size: Int): Int? {
    val side = min(width, height)
    if (side <= 0f) return null
    val cell = side / size
    val originX = (width - side) / 2f
    val originY = (height - side) / 2f
    if (position.x < originX || position.y < originY) return null
    if (position.x > originX + side || position.y > originY + side) return null
    val column = ((position.x - originX) / cell).toInt()
    val row = ((position.y - originY) / cell).toInt()
    if (column !in 0 until size || row !in 0 until size) return null
    return row * size + column
}

@Composable
private fun WordSearch(variant: Int, finish: (Long, Boolean) -> Unit) {
    val theme = WordThemes[variant.coerceIn(WordThemes.indices)]
    val sound = LocalSound.current
    val haptics = LocalHaptics.current
    val textMeasurer = rememberTextMeasurer(cacheSize = 128)

    val grid = remember(variant) { buildGrid(theme.words, Random.Default) }

    var found by remember(grid) { mutableStateOf(emptySet<String>()) }
    var foundCells by remember(grid) { mutableStateOf(emptySet<Int>()) }
    var errors by remember(grid) { mutableIntStateOf(0) }
    var anchor by remember(grid) { mutableStateOf<Int?>(null) }
    var dragStart by remember(grid) { mutableStateOf<Int?>(null) }
    var dragEnd by remember(grid) { mutableStateOf<Int?>(null) }
    var wrongCells by remember(grid) { mutableStateOf(emptySet<Int>()) }
    var wrongTick by remember(grid) { mutableIntStateOf(0) }
    var over by remember(grid) { mutableStateOf(false) }
    var reported by remember(grid) { mutableStateOf(false) }

    // Efface le clignotement rouge d'une sélection incorrecte.
    LaunchedEffect(wrongTick) {
        if (wrongCells.isEmpty()) return@LaunchedEffect
        delay(420)
        wrongCells = emptySet()
    }

    fun validate(from: Int, to: Int) {
        if (over || reported) return
        val cells = lineCells(from, to, GridSize)
        if (cells == null) {
            anchor = null
            if (from != to) {
                errors++
                wrongCells = setOf(from, to)
                wrongTick++
                sound.play(Sound.LOSE)
                haptics.error()
            }
            return
        }
        val text = cells.map { grid[it] }.joinToString("")
        val word = theme.words.firstOrNull { it == text || it == text.reversed() }
        anchor = null
        if (word == null) {
            errors++
            wrongCells = cells.toSet()
            wrongTick++
            sound.play(Sound.LOSE)
            haptics.error()
            return
        }
        if (word in found) return
        found = found + word
        foundCells = foundCells + cells
        sound.play(Sound.MATCH)
        haptics.tap()
        if (found.size == theme.words.size) {
            over = true
            reported = true
            sound.play(Sound.WIN)
            haptics.success()
            val total = theme.words.sumOf { it.length }
            finish(500L + 50L * total - 5L * errors, true)
        }
    }

    // Aperçu de la sélection en cours (glisser ou ancre posée).
    val previewStart = dragStart ?: anchor
    val previewEnd = dragEnd ?: anchor
    val preview: List<Int> = if (previewStart == null || previewEnd == null) {
        emptyList()
    } else {
        lineCells(previewStart, previewEnd, GridSize) ?: listOf(previewStart, previewEnd)
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pill(
                text = "${found.size} / ${theme.words.size} mots",
                color = Ink.Green,
                background = Ink.Green.copy(alpha = 0.14f),
            )
            AppText(theme.name, style = Typo.heading, color = Ink.Text)
            Pill(
                text = if (errors == 0) "Aucune erreur" else "Erreurs $errors",
                color = if (errors == 0) Ink.Muted else Ink.Coral,
                background = Ink.CardAlt,
            )
        }

        Spacer(Modifier.height(8.dp))

        Box(Modifier.fillMaxWidth().weight(1f)) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(grid, over) {
                        if (over) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val start = cellAt(
                                down.position,
                                size.width.toFloat(),
                                size.height.toFloat(),
                                GridSize,
                            ) ?: return@awaitEachGesture
                            down.consume()
                            // Une nouvelle pression remplace l'ancre précédente.
                            val previousAnchor = anchor
                            anchor = null
                            var end = start
                            var moved = false
                            dragStart = start
                            dragEnd = start
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val cell = cellAt(
                                    change.position,
                                    size.width.toFloat(),
                                    size.height.toFloat(),
                                    GridSize,
                                )
                                if (cell != null && cell != end) {
                                    end = cell
                                    dragEnd = cell
                                    moved = true
                                }
                                change.consume()
                            }
                            dragStart = null
                            dragEnd = null
                            if (moved && end != start) {
                                validate(start, end)
                            } else if (previousAnchor == null) {
                                // Premier appui : on garde la lettre comme ancre.
                                anchor = start
                            } else {
                                validate(previousAnchor, start)
                            }
                        }
                    },
            ) {
                val side = min(size.width, size.height)
                val cell = side / GridSize
                val originX = (size.width - side) / 2f
                val originY = (size.height - side) / 2f

                drawRoundRect(
                    color = Ink.Card,
                    topLeft = Offset(originX, originY),
                    size = Size(side, side),
                    cornerRadius = CornerRadius(cell * 0.30f),
                )

                // Surlignage des cases trouvées, fausses et sélectionnées.
                for (index in foundCells) {
                    drawCell(cell, originX, originY, index, Ink.Green.copy(alpha = 0.16f))
                }
                for (index in wrongCells) {
                    drawCell(cell, originX, originY, index, Ink.Coral.copy(alpha = 0.18f))
                }
                for (index in preview) {
                    drawCell(cell, originX, originY, index, Ink.Blue.copy(alpha = 0.13f))
                }
                // Trait discret qui relie les lettres sélectionnées.
                if (preview.size > 1) {
                    val first = preview.first()
                    val last = preview.last()
                    drawLine(
                        color = Ink.Blue.copy(alpha = 0.35f),
                        start = Offset(
                            originX + (first % GridSize) * cell + cell / 2f,
                            originY + (first / GridSize) * cell + cell / 2f,
                        ),
                        end = Offset(
                            originX + (last % GridSize) * cell + cell / 2f,
                            originY + (last / GridSize) * cell + cell / 2f,
                        ),
                        strokeWidth = cell * 0.16f,
                        cap = StrokeCap.Round,
                    )
                }

                // Grille.
                for (i in 0..GridSize) {
                    drawLine(
                        color = Ink.Line,
                        start = Offset(originX + i * cell, originY),
                        end = Offset(originX + i * cell, originY + side),
                        strokeWidth = 1.2f,
                    )
                    drawLine(
                        color = Ink.Line,
                        start = Offset(originX, originY + i * cell),
                        end = Offset(originX + side, originY + i * cell),
                        strokeWidth = 1.2f,
                    )
                }

                // Lettres.
                val letterStyle = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontFamily = Typo.family,
                    fontSize = (cell / density * 0.52f).sp,
                )
                for (index in grid.indices) {
                    val letter = grid[index].toString()
                    val measured = textMeasurer.measure(letter, letterStyle)
                    val x = originX + (index % GridSize) * cell
                    val y = originY + (index / GridSize) * cell
                    val color = when {
                        index in foundCells -> Ink.Green
                        index in wrongCells -> Ink.Coral
                        index in preview -> Ink.Blue
                        else -> Ink.Text
                    }
                    drawText(
                        textLayoutResult = measured,
                        color = color,
                        topLeft = Offset(
                            x + (cell - measured.size.width) / 2f,
                            y + (cell - measured.size.height) / 2f,
                        ),
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            theme.words.chunked(3).forEach { line ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    line.forEach { word ->
                        val done = word in found
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            AppText(
                                text = word,
                                style = Typo.body.copy(
                                    textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
                                ),
                                color = if (done) Ink.Muted else Ink.Text,
                            )
                        }
                    }
                    repeat(3 - line.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        HintBar(
            text = "Glissez d'une lettre à l'autre pour former un mot.",
            accent = Ink.Green,
        )
    }
}

private fun DrawScope.drawCell(
    cell: Float,
    originX: Float,
    originY: Float,
    index: Int,
    color: Color,
) {
    drawRoundRect(
        color = color,
        topLeft = Offset(
            originX + (index % GridSize) * cell + cell * 0.06f,
            originY + (index / GridSize) * cell + cell * 0.06f,
        ),
        size = Size(cell * 0.88f, cell * 0.88f),
        cornerRadius = CornerRadius(cell * 0.22f),
    )
}
