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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import com.botillon.games.ui.Pill
import com.botillon.games.ui.Sound
import com.botillon.games.ui.Typo

/** Tri de Pièces : déplacer les pièces pour que chaque tube n'ait plus qu'une seule valeur. */
object TriDePiecesGame {
    val entry = GameEntry(
        id = "tri-pieces",
        title = "Tri de Pièces",
        tagline = "Une valeur par tube",
        family = Family.LOGIC,
        accent = Ink.Mustard,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        TriDePiecesScreen(variant, finish)
    }
}

private const val COIN_CAP = 4
private val COIN_VALUES = listOf(1, 2, 5, 10, 20, 50)
private val COIN_INKS = listOf(Ink.Mustard, Ink.Coral, Ink.Teal, Ink.Blue, Ink.Violet, Ink.Green)

private fun coinTopRun(tube: List<Int>): Int {
    if (tube.isEmpty()) return 0
    val value = tube.last()
    var count = 0
    var index = tube.lastIndex
    while (index >= 0 && tube[index] == value) {
        count++
        index--
    }
    return count
}

private fun coinCanPour(from: List<Int>, to: List<Int>): Boolean =
    from.isNotEmpty() && to.size < COIN_CAP && (to.isEmpty() || to.last() == from.last())

private fun coinPour(tubes: List<List<Int>>, from: Int, to: Int): List<List<Int>> {
    val amount = minOf(coinTopRun(tubes[from]), COIN_CAP - tubes[to].size)
    val next = tubes.map { it.toMutableList() }
    repeat(amount) { next[to].add(next[from].removeAt(next[from].lastIndex)) }
    return next.map { it.toList() }
}

private fun coinSolved(tubes: List<List<Int>>): Boolean =
    tubes.all { it.distinct().size <= 1 }

private fun coinSolvable(start: List<List<Int>>): Boolean {
    val seen = HashSet<String>()
    var budget = 2500

    fun key(state: List<List<Int>>): String =
        state.map { it.joinToString(",") }.sorted().joinToString("|")

    fun search(state: List<List<Int>>, depth: Int): Boolean {
        if (coinSolved(state)) return true
        if (depth <= 0 || budget <= 0) return false
        if (!seen.add(key(state))) return false
        budget--
        for (from in state.indices) {
            if (state[from].isEmpty()) continue
            for (to in state.indices) {
                if (from == to || !coinCanPour(state[from], state[to])) continue
                if (search(coinPour(state, from, to), depth - 1)) return true
            }
        }
        return false
    }
    return search(start, 34)
}

private fun coinDeal(values: Int, empties: Int, seed: Int): List<List<Int>> {
    val random = kotlin.random.Random(seed)
    repeat(6) {
        val chips = MutableList(values * COIN_CAP) { it / COIN_CAP }
        chips.shuffle(random)
        val tubes = MutableList(values + empties) { mutableListOf<Int>() }
        chips.forEachIndexed { index, value -> tubes[index / COIN_CAP].add(value) }
        val deal = tubes.map { it.toList() }
        if (deal.any { it.distinct().size > 1 } && coinSolvable(deal)) return deal
    }
    val tubes = MutableList(values + empties) { mutableListOf<Int>() }
    for (value in 0 until values) tubes[value] = MutableList(COIN_CAP) { value }
    if (values >= 2) {
        tubes[0] = mutableListOf(0, 0, 1, 1)
        tubes[1] = mutableListOf(1, 1, 0, 0)
    }
    return tubes.map { it.toList() }
}

@Composable
private fun TriDePiecesScreen(variant: Int, finish: (Long, Boolean) -> Unit) {
    val values = listOf(4, 5, 6)[variant]
    val empties = 2
    val tubeCount = values + empties
    val columns = if (tubeCount <= 6) 3 else 4
    var tubes by remember(variant) { mutableStateOf(coinDeal(values, empties, variant * 77 + 3)) }
    var selected by remember(variant) { mutableIntStateOf(-1) }
    var moves by remember(variant) { mutableIntStateOf(0) }
    var reported by remember(variant) { mutableStateOf(false) }
    val sound = LocalSound.current
    val haptics = LocalHaptics.current

    fun tapTube(index: Int) {
        if (reported) return
        if (selected < 0) {
            if (tubes[index].isEmpty()) {
                sound.play(Sound.UI)
                return
            }
            selected = index
            sound.play(Sound.TAP)
            return
        }
        if (selected == index) {
            selected = -1
            return
        }
        if (coinCanPour(tubes[selected], tubes[index])) {
            tubes = coinPour(tubes, selected, index)
            moves++
            selected = -1
            sound.play(Sound.TAP)
            haptics.tap()
            if (coinSolved(tubes)) {
                reported = true
                sound.play(Sound.WIN)
                haptics.success()
                finish((1400L - moves * 10L).coerceAtLeast(100L), true)
            }
        } else {
            selected = if (tubes[index].isEmpty()) -1 else index
            sound.play(Sound.UI)
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Pill(
                "COUPS $moves",
                color = Ink.Mustard,
                background = Ink.Mustard.copy(alpha = 0.18f),
            )
            Spacer(Modifier.weight(1f))
            AppText("$values valeurs", style = Typo.small, color = Ink.Muted)
        }
        Spacer(Modifier.height(10.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (row in 0 until (tubeCount + columns - 1) / columns) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (column in 0 until columns) {
                        val index = row * columns + column
                        if (index < tubeCount) {
                            CoinTube(
                                coins = tubes[index],
                                selected = selected == index,
                                modifier = Modifier.weight(1f),
                                onClick = { tapTube(index) },
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        HintBar("Touchez un tube pour prendre sa pile, puis la destination. Une seule valeur par tube.", accent = Ink.Mustard)
    }
}

@Composable
private fun CoinTube(
    coins: List<Int>,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 22.dp, bottomEnd = 22.dp)
    Box(
        modifier
            .height(206.dp)
            .clip(shape)
            .background(if (selected) Ink.Mustard.copy(alpha = 0.12f) else Ink.Card)
            .border(2.dp, if (selected) Ink.Mustard else Ink.Line, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Bottom),
        ) {
            for (value in coins.reversed()) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(COIN_INKS[value % COIN_INKS.size])
                        .border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    AppText(
                        "${COIN_VALUES[value % COIN_VALUES.size]}",
                        style = Typo.small.copy(fontSize = 10.sp),
                        color = Color.White,
                    )
                }
            }
        }
    }
}
