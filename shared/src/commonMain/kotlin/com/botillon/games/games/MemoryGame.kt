package com.botillon.games.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/** Memory : retrouver les paires de symboles. */
object MemoryGame {
    val entry = GameEntry(
        id = "memory",
        title = "Memory",
        tagline = "Retrouver les paires",
        family = Family.CARD,
        accent = Ink.Violet,
        variants = listOf("4 × 3", "4 × 4", "5 × 4"),
    ) { variant, _, finish, _ ->
        Memory(variant, finish)
    }
}

private val SYMBOLS = listOf("★", "●", "▲", "■", "◆", "♥", "♠", "♣", "✦", "☾", "✚", "◐")

@Composable
private fun Memory(variant: Int, finish: (Long, Boolean) -> Unit) {
    val rows = when (variant) {
        0 -> 3
        1 -> 4
        else -> 4
    }
    val cols = if (variant == 0) 4 else if (variant == 1) 4 else 5
    val pairs = rows * cols / 2
    val sound = LocalSound.current
    val haptics = LocalHaptics.current

    var cards by remember { mutableStateOf(emptyList<Int>()) }
    var opened by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var matched by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var moves by remember { mutableStateOf(0) }
    var lock by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val deck = (0 until pairs).flatMap { listOf(it, it) }.shuffled(Random.Default)
        cards = deck
    }

    LaunchedEffect(opened) {
        if (opened.size == 2) {
            lock = true
            delay(650)
            val list = opened.toList()
            if (cards[list[0]] == cards[list[1]]) {
                matched = matched + opened
                sound.play(Sound.MATCH)
                haptics.success()
            } else {
                sound.play(Sound.TAP)
            }
            opened = emptySet()
            lock = false
            if (matched.size == cards.size && cards.isNotEmpty()) {
                done = true
                val score = (pairs * 160 - moves * 12).coerceAtLeast(80).toLong()
                sound.play(Sound.WIN)
                finish(score, true)
            }
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AppText("Coups : $moves", style = Typo.heading, color = Ink.Text)
            AppText("Paires : ${matched.size / 2} / $pairs", style = Typo.small, color = Ink.Muted)
        }
        Spacer(Modifier.height(12.dp))

        if (cards.isEmpty()) {
            AppText("Mélange…", style = Typo.body, color = Ink.Muted)
            return@Column
        }

        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(rows) { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    repeat(cols) { col ->
                        val index = row * cols + col
                        if (index >= cards.size) {
                            Spacer(Modifier.weight(1f))
                        } else {
                            val visible = index in opened || index in matched
                            Card(
                                symbol = cards[index],
                                visible = visible,
                                matched = index in matched,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (lock || done || visible || opened.size >= 2) return@Card
                                    opened = opened + index
                                    moves++
                                    sound.play(Sound.FLIP)
                                    haptics.tap()
                                },
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        HintBar("Retournez deux cartes. Une paire trouvée reste visible.")
    }
}

@Composable
private fun Card(
    symbol: Int,
    visible: Boolean,
    matched: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val accent = Ink.Accents[symbol % Ink.Accents.size]
    Box(
        modifier
            .height(74.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (visible) Ink.Card else Ink.CardAlt)
            .border(
                width = if (matched) 2.dp else 1.dp,
                color = if (matched) accent else Ink.Line,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (visible) {
            AppText(
                text = SYMBOLS[symbol % SYMBOLS.size],
                style = Typo.big.copy(fontSize = 30.sp),
                color = accent,
            )
        } else {
            Box(
                Modifier
                    .fillMaxWidth(0.28f)
                    .height(4.dp)
                    .background(Ink.Line, RoundedCornerShape(50)),
            )
        }
    }
}
