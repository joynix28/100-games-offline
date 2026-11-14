package com.botillon.games.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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

/** Nonogramme : retrouver le dessin caché à partir des indices de lignes et de colonnes. */
object NonogrammeGame {
    val entry = GameEntry(
        id = "nonogramme",
        title = "Nonogramme",
        tagline = "Révéler le dessin caché",
        family = Family.LOGIC,
        accent = Ink.Violet,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        NonogrammeScreen(variant, finish)
    }
}

private val NONOGRAMMES = listOf(
    listOf("..#..", ".###.", "#####", ".###.", "..#.."),
    listOf(
        "...##...", "..####..", ".######.", "##.##.##",
        "...##...", "...##...", "...##...", "........",
    ),
    listOf(
        ".##..##.", "#..##..#", "#..##..#", ".######.",
        "..#..#..", ".##..##.", "#......#", "........",
    ),
)

private fun nonogramClues(line: List<Boolean>): List<Int> {
    val clues = mutableListOf<Int>()
    var run = 0
    for (filled in line) {
        if (filled) {
            run++
        } else if (run > 0) {
            clues += run
            run = 0
        }
    }
    if (run > 0) clues += run
    return if (clues.isEmpty()) listOf(0) else clues
}

@Composable
private fun NonogrammeScreen(variant: Int, finish: (Long, Boolean) -> Unit) {
    val n = if (variant == 0) 5 else 8
    val pattern = NONOGRAMMES[variant]
    val solution = remember(variant) { pattern.flatMap { row -> row.map { it == '#' } } }
    val rowClues = remember(variant) {
        (0 until n).map { r -> nonogramClues((0 until n).map { c -> solution[r * n + c] }) }
    }
    val colClues = remember(variant) {
        (0 until n).map { c -> nonogramClues((0 until n).map { r -> solution[r * n + c] }) }
    }
    val total = solution.count { it }

    var cells by remember(variant) { mutableStateOf(List(n * n) { 0 }) }
    var taps by remember(variant) { mutableIntStateOf(0) }
    var reported by remember(variant) { mutableStateOf(false) }
    val sound = LocalSound.current
    val haptics = LocalHaptics.current

    val cell = if (n == 5) 46.dp else 36.dp
    val clueWidth = 46.dp
    val clueHeight = if (n == 5) 46.dp else 52.dp

    fun tap(index: Int) {
        if (reported) return
        val next = cells.toMutableList()
        next[index] = (next[index] + 1) % 3
        cells = next
        taps++
        sound.play(Sound.TAP)
        haptics.tap()
        if (next.indices.all { (next[it] == 1) == solution[it] }) {
            reported = true
            sound.play(Sound.WIN)
            haptics.success()
            finish(((variant + 1) * 400L - taps * 3L).coerceAtLeast(60L), true)
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Pill(
                "NOIRES ${cells.count { it == 1 }} / $total",
                color = Ink.Violet,
                background = Ink.Violet.copy(alpha = 0.12f),
            )
            Spacer(Modifier.weight(1f))
            Pill("TOUCHERS $taps", color = Ink.Soft)
        }
        Spacer(Modifier.height(14.dp))

        Column {
            Row {
                Spacer(Modifier.width(clueWidth).height(clueHeight))
                for (c in 0 until n) {
                    Column(
                        Modifier.width(cell).height(clueHeight),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        for (value in colClues[c]) {
                            AppText(
                                "$value",
                                style = Typo.small.copy(fontSize = 9.sp),
                                color = Ink.Muted,
                            )
                        }
                    }
                }
            }
            Row {
                Column(Modifier.width(clueWidth)) {
                    for (r in 0 until n) {
                        Box(
                            Modifier.width(clueWidth).height(cell).padding(end = 6.dp),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            AppText(
                                rowClues[r].joinToString(" "),
                                style = Typo.small.copy(fontSize = 10.sp),
                                color = Ink.Soft,
                            )
                        }
                    }
                }
                Column {
                    for (r in 0 until n) {
                        Row {
                            for (c in 0 until n) {
                                val index = r * n + c
                                val state = cells[index]
                                val background = when (state) {
                                    1 -> Ink.Text
                                    2 -> Ink.CardAlt
                                    else -> Ink.Card
                                }
                                Box(
                                    Modifier
                                        .size(cell)
                                        .padding(1.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(background)
                                        .clickable { tap(index) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (state == 2) {
                                        Canvas(Modifier.fillMaxSize()) {
                                            val m = size.minDimension * 0.26f
                                            val color = Ink.Muted
                                            drawLine(
                                                color,
                                                Offset(center.x - m, center.y - m),
                                                Offset(center.x + m, center.y + m),
                                                2.2f,
                                            )
                                            drawLine(
                                                color,
                                                Offset(center.x + m, center.y - m),
                                                Offset(center.x - m, center.y + m),
                                                2.2f,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        HintBar(
            "Touchez : noire, puis croix, puis vide. Les croix aident à compter.",
            accent = Ink.Violet,
        )
    }
}
