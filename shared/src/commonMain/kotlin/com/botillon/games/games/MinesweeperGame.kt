package com.botillon.games.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * Démineur : trois grilles, dix à trente-six mines.
 * La première case touchée est toujours sûre ; un bouton bascule le mode drapeau.
 */
object MinesweeperGame {
    val entry = GameEntry(
        id = "demineur",
        title = "Démineur",
        tagline = "Déminer sans exploser",
        family = Family.LOGIC,
        accent = Ink.Blue,
        variants = listOf("Débutant", "Intermédiaire", "Expert"),
    ) { variant, _, finish, _ ->
        Minesweeper(variant, finish)
    }
}

/** Une grille figée : les mines ne bougent qu'au tout premier appui. */
private data class Minefield(
    val rows: Int,
    val cols: Int,
    val mines: BooleanArray,
    val revealed: BooleanArray,
    val flags: BooleanArray,
) {
    val size: Int get() = rows * cols
    val mineCount: Int get() = mines.count { it }
    val flagCount: Int get() = flags.count { it }

    /** Les huit voisines d'une case, bords compris. */
    fun neighbours(index: Int): List<Int> {
        val x = index % cols
        val y = index / cols
        val out = mutableListOf<Int>()
        for (dy in -1..1) for (dx in -1..1) {
            if (dx == 0 && dy == 0) continue
            val nx = x + dx
            val ny = y + dy
            if (nx in 0 until cols && ny in 0 until rows) out += ny * cols + nx
        }
        return out
    }

    fun adjacent(index: Int): Int = neighbours(index).count { mines[it] }
}

private fun newField(rows: Int, cols: Int, mineTotal: Int): Minefield {
    val total = rows * cols
    val mines = BooleanArray(total)
    var placed = 0
    while (placed < mineTotal) {
        val index = Random.nextInt(total)
        if (mines[index]) continue
        mines[index] = true
        placed++
    }
    return Minefield(rows, cols, mines, BooleanArray(total), BooleanArray(total))
}

/** Déplace la mine touchée pour que le premier appui ouvre toujours la grille. */
private fun moveMine(field: Minefield, away: Int): Minefield {
    if (!field.mines[away]) return field
    val mines = field.mines.copyOf()
    mines[away] = false
    val unflagged = mines.indices.filter { it != away && !mines[it] && !field.flags[it] }
    val candidates = if (unflagged.isNotEmpty()) {
        unflagged
    } else {
        mines.indices.filter { it != away && !mines[it] }
    }
    val around = field.neighbours(away)
    val far = candidates.filter { it !in around }
    val pool = if (far.isNotEmpty()) far else candidates
    if (pool.isEmpty()) {
        // Plateau minuscule : on remet la mine, la partie reste jouable.
        mines[away] = true
        return field
    }
    mines[pool.random(Random.Default)] = true
    return field.copy(mines = mines)
}

/** Révèle une case et, si elle est vide, toute la zone vide autour. */
private fun openCell(field: Minefield, start: Int): Minefield {
    if (field.revealed[start] || field.flags[start] || field.mines[start]) return field
    val revealed = field.revealed.copyOf()
    val queue = mutableListOf(start)
    while (queue.isNotEmpty()) {
        val index = queue.removeAt(queue.lastIndex)
        if (revealed[index] || field.flags[index]) continue
        revealed[index] = true
        if (field.adjacent(index) == 0) {
            for (next in field.neighbours(index)) {
                if (!revealed[next] && !field.flags[next] && !field.mines[next]) queue += next
            }
        }
    }
    return field.copy(revealed = revealed)
}

private fun numberColor(value: Int): Color = when (value) {
    1 -> Ink.Blue
    2 -> Ink.Green
    3 -> Ink.Coral
    4 -> Ink.Violet
    5 -> Ink.Mustard
    6 -> Ink.Teal
    7 -> Ink.Text
    else -> Ink.Soft
}

private fun clockText(seconds: Int): String {
    val minutes = seconds / 60
    val rest = seconds % 60
    return "${minutes.toString().padStart(2, '0')}:${rest.toString().padStart(2, '0')}"
}

@Composable
private fun Minesweeper(variant: Int, finish: (Long, Boolean) -> Unit) {
    val rows = listOf(9, 12, 14)[variant]
    val mineTotal = listOf(10, 22, 36)[variant]

    val sound = LocalSound.current
    val haptics = LocalHaptics.current

    var field by remember { mutableStateOf(newField(rows, rows, mineTotal)) }
    var flagMode by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }
    var seconds by remember { mutableIntStateOf(0) }
    var ended by remember { mutableStateOf(false) }
    var lost by remember { mutableStateOf(false) }
    var explodedIndex by remember { mutableIntStateOf(-1) }
    var reported by remember { mutableStateOf(false) }

    val remaining = (field.mineCount - field.flagCount).coerceAtLeast(0)

    fun conclude(score: Long, won: Boolean) {
        if (reported) return
        reported = true
        ended = true
        if (won) {
            sound.play(Sound.WIN)
            haptics.success()
        } else {
            sound.play(Sound.LOSE)
            haptics.error()
        }
        finish(score, won)
    }

    fun onCell(index: Int) {
        if (ended) return

        if (flagMode) {
            if (field.revealed[index]) return
            val flags = field.flags.copyOf()
            flags[index] = !flags[index]
            field = field.copy(flags = flags)
            sound.play(Sound.TAP)
            haptics.tap()
            return
        }

        if (field.revealed[index] || field.flags[index]) return

        var current = field
        if (!started) {
            started = true
            current = moveMine(current, index)
        }

        if (current.mines[index]) {
            // Mine touchée : on dévoile le plateau et la partie s'arrête là.
            lost = true
            explodedIndex = index
            val opened = current.revealed.count { it }
            field = current
            conclude(opened * 5L, false)
            return
        }

        val opened = openCell(current, index)
        field = opened
        sound.play(Sound.TAP)
        haptics.tap()

        val safeTotal = opened.size - opened.mineCount
        if (opened.revealed.count { it } == safeTotal) {
            conclude(100L + safeTotal * 5L, true)
        }
    }

    // Le chronomètre démarre au premier appui et s'arrête avec la partie.
    LaunchedEffect(started, ended) {
        if (!started || ended) return@LaunchedEffect
        while (true) {
            delay(1000)
            seconds += 1
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pill(
                text = "MINES $remaining",
                color = Ink.Coral,
                background = Ink.Coral.copy(alpha = 0.12f),
            )
            Pill(text = "TEMPS ${clockText(seconds)}", color = Ink.Soft)
            Spacer(Modifier.weight(1f))
            PrimaryButton(
                label = if (flagMode) "DRAPEAU ✓" else "DRAPEAU",
                onClick = { flagMode = !flagMode },
                modifier = Modifier.width(124.dp),
                color = if (flagMode) Ink.Mustard else Ink.CardAlt,
                textColor = if (flagMode) Color.White else Ink.Soft,
                enabled = !ended,
            )
        }

        Spacer(Modifier.height(12.dp))

        Panel(Modifier.fillMaxWidth(), color = Ink.CardAlt) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(5.dp),
            ) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    for (row in 0 until field.rows) {
                        Row(
                            Modifier.weight(1f).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            for (col in 0 until field.cols) {
                                val index = row * field.cols + col
                                MineCell(
                                    field = field,
                                    index = index,
                                    lost = lost,
                                    exploded = index == explodedIndex,
                                    compact = field.cols > 12,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    onClick = { onCell(index) },
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        HintBar(
            when {
                lost -> "Une mine a explosé. Rejouez pour une nouvelle grille."
                ended -> "Grille déminée, beau travail."
                flagMode -> "Mode drapeau : touchez une case pour poser ou retirer un drapeau."
                else -> "Touchez une case pour la déminer. « Drapeau » marque les mines."
            },
        )
    }
}

@Composable
private fun MineCell(
    field: Minefield,
    index: Int,
    lost: Boolean,
    exploded: Boolean,
    compact: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val revealed = field.revealed[index]
    val isMine = field.mines[index]
    val flagged = field.flags[index]
    val showMine = isMine && (revealed || lost)
    val number = if (revealed && !isMine) field.adjacent(index) else 0

    val background = when {
        showMine && exploded -> Ink.Coral
        showMine -> Ink.CardAlt
        revealed -> Ink.BgDeep
        else -> Ink.Card
    }
    val edge = when {
        flagged && !revealed -> Ink.Mustard
        revealed -> Ink.BgDeep
        else -> Ink.Line
    }

    Box(
        modifier
            .clip(RoundedCornerShape(5.dp))
            .background(background)
            .border(
                width = if (flagged && !revealed) 2.dp else 1.dp,
                color = edge,
                shape = RoundedCornerShape(5.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            showMine -> Canvas(Modifier.fillMaxWidth(0.62f).aspectRatio(1f)) {
                drawMine(
                    center = center,
                    side = size.minDimension,
                    color = if (exploded) Color.White else Ink.Soft,
                )
            }

            flagged -> Canvas(Modifier.fillMaxWidth(0.56f).aspectRatio(1f)) {
                drawFlag(center, size.minDimension)
            }

            number > 0 -> AppText(
                text = "$number",
                style = Typo.heading.copy(fontSize = if (compact) 11.sp else 15.sp),
                color = numberColor(number),
            )
        }
    }
}

private fun DrawScope.drawFlag(center: Offset, side: Float) {
    val poleX = center.x + side * 0.24f
    val top = center.y - side * 0.42f
    val bottom = center.y + side * 0.44f
    drawLine(
        color = Ink.Text,
        start = Offset(poleX, top),
        end = Offset(poleX, bottom),
        strokeWidth = (side * 0.10f).coerceAtLeast(1.6f),
        cap = StrokeCap.Round,
    )
    val cloth = Path().apply {
        moveTo(poleX, top)
        lineTo(poleX - side * 0.52f, top + side * 0.19f)
        lineTo(poleX, top + side * 0.38f)
        close()
    }
    drawPath(cloth, Ink.Coral)
}

private fun DrawScope.drawMine(center: Offset, side: Float, color: Color) {
    val radius = side * 0.19f
    val spike = radius * 1.65f
    val arm = (radius * 0.36f).coerceAtLeast(1.4f)
    drawLine(color, Offset(center.x - spike, center.y), Offset(center.x + spike, center.y), arm)
    drawLine(color, Offset(center.x, center.y - spike), Offset(center.x, center.y + spike), arm)
    val diagonal = spike * 0.70f
    drawLine(
        color,
        Offset(center.x - diagonal, center.y - diagonal),
        Offset(center.x + diagonal, center.y + diagonal),
        arm,
    )
    drawLine(
        color,
        Offset(center.x + diagonal, center.y - diagonal),
        Offset(center.x - diagonal, center.y + diagonal),
        arm,
    )
    drawCircle(color, radius, center)
    drawCircle(
        color = Color.White.copy(alpha = 0.55f),
        radius = radius * 0.26f,
        center = Offset(center.x - radius * 0.32f, center.y - radius * 0.32f),
    )
}
