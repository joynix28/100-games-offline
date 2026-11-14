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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.botillon.games.ui.AppText
import com.botillon.games.ui.Family
import com.botillon.games.ui.GameEntry
import com.botillon.games.ui.HintBar
import com.botillon.games.ui.Ink
import com.botillon.games.ui.LocalHaptics
import com.botillon.games.ui.LocalSound
import com.botillon.games.ui.Pill
import com.botillon.games.ui.PrimaryButton
import com.botillon.games.ui.Sound
import com.botillon.games.ui.Typo
import kotlin.random.Random

/**
 * Water Sort : verser les couleurs d'une fiole à l'autre jusqu'à ce que chaque
 * fiole ne contienne qu'une seule couleur.
 *
 * Le tirage est mélangé, puis vérifié par une recherche de versements légaux :
 * on ne garde que les tirages dont une solution existe réellement.
 */
object WaterSortGame {
    val entry = GameEntry(
        id = "watersort",
        title = "Water Sort",
        tagline = "Trier les couleurs",
        family = Family.LOGIC,
        accent = Ink.Teal,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        WaterSort(variant, finish)
    }
}

private const val CAPACITY = 4

private typealias Tubes = List<List<Int>>

private val TUBE_COLORS = listOf(
    Ink.Coral,
    Ink.Teal,
    Ink.Blue,
    Ink.Violet,
    Ink.Mustard,
    Ink.Green,
    Ink.Pink,
    Color(0xFF8A6A4F),
)

private fun tubeColor(value: Int): Color = TUBE_COLORS[value % TUBE_COLORS.size]

private fun topRun(tube: List<Int>): Int {
    if (tube.isEmpty()) return 0
    val color = tube[tube.lastIndex]
    var count = 0
    var index = tube.lastIndex
    while (index >= 0 && tube[index] == color) {
        count++
        index--
    }
    return count
}

private fun canPour(from: List<Int>, to: List<Int>): Boolean =
    from.isNotEmpty() &&
        to.size < CAPACITY &&
        (to.isEmpty() || to.last() == from.last())

private fun pourAmount(from: List<Int>, to: List<Int>): Int =
    minOf(topRun(from), CAPACITY - to.size)

private fun applyPour(tubes: Tubes, from: Int, to: Int): Tubes {
    val amount = pourAmount(tubes[from], tubes[to])
    val next = tubes.map { it.toMutableList() }
    repeat(amount) {
        next[to].add(next[from].removeAt(next[from].lastIndex))
    }
    return next.map { it.toList() }
}

private fun isSolved(tubes: Tubes): Boolean =
    tubes.all { tube -> tube.distinct().size <= 1 }

private fun stateKey(tubes: Tubes): String =
    tubes.map { it.joinToString("") }.sorted().joinToString("|")

/** Une fiole est terminée quand elle est pleine et d'une seule couleur. */
private fun completesTube(tube: List<Int>): Boolean =
    tube.size == CAPACITY && tube.distinct().size == 1

/** Petit score pour tenter d'abord les versements les plus utiles. */
private fun moveScore(tubes: Tubes, from: Int, to: Int): Int {
    val amount = pourAmount(tubes[from], tubes[to])
    val target = tubes[to] + List(amount) { tubes[from].last() }
    var score = 0
    if (completesTube(target)) score += 5
    if (amount == tubes[from].size) score += 2
    if (tubes[to].isNotEmpty()) score += 1
    return score
}

/**
 * Recherche bornée : existe-t-il une suite de versements légaux qui trie le
 * tirage ? Les fioles étant interchangeables, les états sont normalisés pour
 * éviter de réexplorer les mêmes positions.
 */
private fun hasSolution(start: Tubes): Boolean {
    val seen = HashSet<String>()
    var budget = 12_000

    fun search(state: Tubes, depth: Int): Boolean {
        if (isSolved(state)) return true
        if (depth <= 0 || budget <= 0) return false
        if (!seen.add(stateKey(state))) return false
        budget--

        // On note chaque coup une seule fois, puis on tente les plus utiles.
        val scored = mutableListOf<Triple<Int, Int, Int>>()
        for (source in state.indices) {
            if (state[source].isEmpty()) continue
            val uniform = state[source].distinct().size == 1
            for (target in state.indices) {
                if (source == target) continue
                if (!canPour(state[source], state[target])) continue
                // Déplacer une fiole déjà triée vers une fiole vide ne change rien.
                if (uniform && state[target].isEmpty()) continue
                scored += Triple(moveScore(state, source, target), source, target)
            }
        }
        scored.sortByDescending { it.first }

        for ((_, source, target) in scored) {
            if (search(applyPour(state, source, target), depth - 1)) return true
        }
        return false
    }

    return search(start, 100)
}

/** Répartit les pastilles au hasard : deux fioles restent vides. */
private fun shuffledTubes(tubeCount: Int, colorCount: Int): Tubes {
    val chips = MutableList(colorCount * CAPACITY) { it / CAPACITY }
    chips.shuffle(Random.Default)
    val tubes = MutableList(tubeCount) { mutableListOf<Int>() }
    for (index in 0 until colorCount) {
        val start = index * CAPACITY
        for (offset in 0 until CAPACITY) {
            tubes[index].add(chips[start + offset])
        }
    }
    return tubes.map { it.toList() }
}

/**
 * Tirage de secours, toujours soluble : deux fioles se répondent, les autres
 * sont déjà triées. Il ne sert que si la recherche n'a rien validé.
 */
private fun fallbackPuzzle(colorCount: Int): Tubes {
    val tubes = MutableList(colorCount + 2) { emptyList<Int>() }
    tubes[0] = listOf(0, 1, 1, 1)
    tubes[1] = listOf(1, 0, 0, 0)
    for (color in 2 until colorCount) {
        tubes[color] = List(CAPACITY) { color }
    }
    return tubes
}

private fun buildPuzzle(tubeCount: Int, colorCount: Int): Tubes {
    repeat(6) {
        val candidate = shuffledTubes(tubeCount, colorCount)
        if (!isSolved(candidate) && hasSolution(candidate)) return candidate
    }
    return fallbackPuzzle(colorCount)
}

@Composable
private fun WaterSort(variant: Int, finish: (Long, Boolean) -> Unit) {
    val tubeCount = listOf(6, 8, 10)[variant]
    val columns = tubeCount / 2
    val colorCount = tubeCount - 2
    val tubeHeight = when (variant) {
        0 -> 288.dp
        1 -> 240.dp
        else -> 210.dp
    }

    val sound = LocalSound.current
    val haptics = LocalHaptics.current

    var tubes by remember(variant) { mutableStateOf(buildPuzzle(tubeCount, colorCount)) }
    var moves by remember(variant) { mutableIntStateOf(0) }
    var selected by remember(variant) { mutableIntStateOf(-1) }
    var history by remember(variant) { mutableStateOf<List<Tubes>>(emptyList()) }
    var reported by remember(variant) { mutableStateOf(false) }

    fun onTube(index: Int) {
        if (reported) return

        if (selected < 0) {
            if (tubes[index].isEmpty()) return
            selected = index
            sound.play(Sound.TAP)
            return
        }

        if (selected == index) {
            selected = -1
            return
        }

        val from = selected
        if (canPour(tubes[from], tubes[index])) {
            val before = tubes
            val next = applyPour(before, from, index)
            history = (history + listOf(before)).takeLast(10)
            tubes = next
            moves++
            selected = -1
            sound.play(Sound.TAP)
            haptics.tap()

            if (isSolved(next)) {
                reported = true
                sound.play(Sound.WIN)
                haptics.success()
                finish((1200L - moves * 8L).coerceAtLeast(100L), true)
            }
        } else {
            // La fiole visée n'accepte pas ce versement : elle devient la source.
            selected = if (tubes[index].isEmpty()) -1 else index
            sound.play(Sound.UI)
        }
    }

    fun undo() {
        if (reported) return
        val previous = history.lastOrNull() ?: return
        history = history.dropLast(1)
        tubes = previous
        moves = (moves - 1).coerceAtLeast(0)
        selected = -1
        sound.play(Sound.UI)
        haptics.tap()
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pill(
                text = "COUPS $moves",
                color = Ink.Teal,
                background = Ink.Teal.copy(alpha = 0.12f),
            )
            Spacer(Modifier.weight(1f))
            AppText("$colorCount couleurs", style = Typo.small, color = Ink.Muted)
        }

        Spacer(Modifier.height(10.dp))

        Box(
            Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                repeat(tubeCount / columns) { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        repeat(columns) { col ->
                            val index = row * columns + col
                            val isSelected = selected == index
                            val reachable =
                                selected >= 0 &&
                                    selected != index &&
                                    canPour(tubes[selected], tubes[index])
                            TubeView(
                                colors = tubes[index],
                                selected = isSelected,
                                reachable = reachable,
                                height = tubeHeight,
                                modifier = Modifier.weight(1f),
                                onClick = { onTube(index) },
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        PrimaryButton(
            label = "ANNULER",
            onClick = { undo() },
            modifier = Modifier.fillMaxWidth(),
            color = Ink.CardAlt,
            textColor = Ink.Soft,
            enabled = history.isNotEmpty() && !reported,
        )

        Spacer(Modifier.height(10.dp))

        HintBar(
            text = "Touchez une fiole, puis sa destination.",
            accent = Ink.Teal,
        )
    }
}

@Composable
private fun TubeView(
    colors: List<Int>,
    selected: Boolean,
    reachable: Boolean,
    height: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(
        topStart = 10.dp,
        topEnd = 10.dp,
        bottomStart = 24.dp,
        bottomEnd = 24.dp,
    )
    val edge = when {
        selected -> Ink.Teal
        reachable -> Ink.Teal.copy(alpha = 0.45f)
        else -> Ink.Line
    }

    Box(
        modifier = modifier
            .height(height)
            .offset(y = if (selected) (-7).dp else 0.dp)
            .clip(shape)
            .background(if (selected) Ink.Teal.copy(alpha = 0.08f) else Ink.Card)
            .border(
                width = if (selected || reachable) 2.dp else 1.dp,
                color = edge,
                shape = shape,
            )
            .clickable(onClick = onClick),
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 5.dp, vertical = 6.dp),
        ) {
            // Les couches sont stockées du bas vers le haut : on les dessine
            // de haut en bas pour que la dernière couleur repose au fond.
            for (slot in CAPACITY - 1 downTo 0) {
                val filled = slot < colors.size
                Box(
                    Modifier.fillMaxWidth().weight(1f).padding(vertical = 1.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (filled) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(6.dp))
                                .background(tubeColor(colors[slot])),
                        )
                    } else {
                        // Petite pastille : la place encore libre dans la fiole.
                        Box(
                            Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(Ink.Line),
                        )
                    }
                }
            }
        }
    }
}
