package com.botillon.games.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.botillon.games.ui.AppText
import com.botillon.games.ui.Family
import com.botillon.games.ui.GameEntry
import com.botillon.games.ui.HintBar
import com.botillon.games.ui.Ink
import com.botillon.games.ui.LocalHaptics
import com.botillon.games.ui.LocalSound
import com.botillon.games.ui.Pill
import com.botillon.games.ui.PrimaryButton
import com.botillon.games.ui.SecondaryButton
import com.botillon.games.ui.Sound
import com.botillon.games.ui.Typo
import kotlin.random.Random

/**
 * Solitaire : le Klondike classique.
 *
 * Sept colonnes, une pioche, quatre fondations. On touche une carte pour la
 * sélectionner, puis une colonne (ou une fondation) pour l'y déposer.
 * Variante « 3 cartes » pour les joueurs pressés, « Débutant » pour une
 * pioche d'une carte et un recyclage sans limite.
 */
object SolitaireGame {
    val entry = GameEntry(
        id = "solitaire",
        title = "Solitaire",
        tagline = "Klondike classique",
        family = Family.CARD,
        accent = Ink.Green,
        variants = listOf("1 carte", "3 cartes", "Débutant"),
    ) { variant, _, finish, _ ->
        Klondike(variant, finish)
    }
}

private val SOL_SUITS = listOf("♥", "♦", "♣", "♠")
private val SOL_RANKS = listOf("A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "V", "D", "R")

/** Une carte à jouer ; l'identité est donnée par le couple couleur / valeur. */
private data class SolCard(val suit: Int, val rank: Int) {
    val red: Boolean get() = suit == 0 || suit == 1
    val label: String get() = SOL_RANKS[rank - 1]
    val symbol: String get() = SOL_SUITS[suit]
}

private data class TableauCard(val card: SolCard, val faceUp: Boolean)

/** Origine d'une sélection : une pile du tableau ou la défausse. */
private sealed interface SolPick {
    data class Tableau(val column: Int, val index: Int) : SolPick
    data object Waste : SolPick
}

private data class SolState(
    val tableau: List<List<TableauCard>>,
    val stock: List<SolCard>,
    val waste: List<SolCard>,
    val foundations: List<List<SolCard>>,
)

/** Distribution complète : 7 colonnes, le reste face cachée dans la pioche. */
private fun freshSolitaire(): SolState {
    val deck = mutableListOf<SolCard>()
    for (suit in 0 until 4) for (rank in 1..13) deck.add(SolCard(suit, rank))
    val shuffled = deck.shuffled(Random.Default)
    var index = 0
    val tableau = List(7) { column ->
        List(column + 1) {
            val card = shuffled[index++]
            TableauCard(card, it == column)
        }
    }
    return SolState(
        tableau = tableau,
        stock = shuffled.drop(index),
        waste = emptyList(),
        foundations = List(4) { emptyList<SolCard>() },
    )
}

private fun solCards(state: SolState, pick: SolPick): List<SolCard> {
    when (pick) {
        is SolPick.Tableau -> {
            val column = state.tableau.getOrNull(pick.column) ?: return emptyList()
            if (pick.index !in column.indices) return emptyList()
            return column.drop(pick.index).map { it.card }
        }

        SolPick.Waste -> {
            val top = state.waste.lastOrNull() ?: return emptyList()
            return listOf(top)
        }
    }
}

/** Une suite descendante de couleurs alternées, par exemple 10♥ 9♠ 8♦. */
private fun solRunValid(cards: List<SolCard>): Boolean {
    if (cards.isEmpty()) return false
    return cards.zipWithNext().all { (a, b) ->
        a.rank == b.rank + 1 && a.red != b.red
    }
}

private fun solCanDrop(state: SolState, cards: List<SolCard>, column: Int): Boolean {
    if (cards.isEmpty()) return false
    val dest = state.tableau.getOrNull(column) ?: return false
    if (dest.isEmpty()) return cards.first().rank == 13
    val top = dest.last()
    return top.faceUp &&
        top.card.rank == cards.first().rank + 1 &&
        top.card.red != cards.first().red
}

private fun solMoveToTableau(state: SolState, pick: SolPick, dest: Int): SolState? {
    if (pick is SolPick.Tableau && pick.column == dest) return null
    val cards = solCards(state, pick)
    if (!solRunValid(cards)) return null
    if (!solCanDrop(state, cards, dest)) return null

    val tableau = state.tableau.toMutableList()
    var waste = state.waste

    when (pick) {
        is SolPick.Tableau -> {
            val source = state.tableau[pick.column]
            val picked = source.getOrNull(pick.index) ?: return null
            if (!picked.faceUp) return null
            if (source.drop(pick.index).any { !it.faceUp }) return null
            val remaining = source.take(pick.index)
            val exposed = if (remaining.isNotEmpty() && !remaining.last().faceUp) {
                remaining.dropLast(1) + remaining.last().copy(faceUp = true)
            } else {
                remaining
            }
            tableau[pick.column] = exposed
        }

        SolPick.Waste -> {
            if (state.waste.isEmpty()) return null
            waste = state.waste.dropLast(1)
        }
    }

    tableau[dest] = tableau[dest] + cards.map { TableauCard(it, true) }
    return state.copy(tableau = tableau, waste = waste)
}

private fun solMoveToFoundation(state: SolState, pick: SolPick, index: Int): SolState? {
    val cards = solCards(state, pick)
    if (cards.size != 1) return null
    val card = cards.first()

    if (pick is SolPick.Tableau) {
        val source = state.tableau.getOrNull(pick.column) ?: return null
        if (pick.index != source.lastIndex) return null
        if (!source.last().faceUp) return null
    }

    val foundation = state.foundations[index]
    val accepted = if (foundation.isEmpty()) {
        card.rank == 1
    } else {
        foundation.last().suit == card.suit && foundation.last().rank == card.rank - 1
    }
    if (!accepted) return null

    val foundations = state.foundations.toMutableList()
    foundations[index] = foundation + card

    var tableau = state.tableau
    var waste = state.waste
    when (pick) {
        is SolPick.Tableau -> {
            val remaining = state.tableau[pick.column].dropLast(1)
            val exposed = if (remaining.isNotEmpty() && !remaining.last().faceUp) {
                remaining.dropLast(1) + remaining.last().copy(faceUp = true)
            } else {
                remaining
            }
            tableau = tableau.toMutableList().also { it[pick.column] = exposed }
        }

        SolPick.Waste -> waste = waste.dropLast(1)
    }

    return state.copy(tableau = tableau, waste = waste, foundations = foundations)
}

@Composable
private fun Klondike(variant: Int, finish: (Long, Boolean) -> Unit) {
    val sound = LocalSound.current
    val haptics = LocalHaptics.current
    val drawCount = if (variant == 1) 3 else 1

    var state by remember { mutableStateOf(freshSolitaire()) }
    var selection by remember { mutableStateOf<SolPick?>(null) }
    var moves by remember { mutableIntStateOf(0) }
    var reported by remember { mutableStateOf(false) }

    fun reportWin(next: SolState) {
        if (reported) return
        if (next.foundations.all { it.size == 13 }) {
            reported = true
            sound.play(Sound.WIN)
            haptics.success()
            finish(1000L, true)
        }
    }

    fun commit(next: SolState) {
        state = next
        selection = null
        moves++
        sound.play(Sound.MATCH)
        haptics.tap()
        reportWin(next)
    }

    fun selectTableau(column: Int, index: Int) {
        val columnCards = state.tableau[column]
        val picked = columnCards.getOrNull(index) ?: return
        if (!picked.faceUp) return
        val run = columnCards.drop(index)
        if (run.any { !it.faceUp }) return
        if (!solRunValid(run.map { it.card })) return
        selection = SolPick.Tableau(column, index)
        sound.play(Sound.TAP)
        haptics.tap()
    }

    fun onTableauCard(column: Int, index: Int) {
        val pick = selection
        if (pick != null) {
            if (pick is SolPick.Tableau && pick.column == column && pick.index == index) {
                selection = null
                return
            }
            val moved = solMoveToTableau(state, pick, column)
            if (moved != null) {
                commit(moved)
                return
            }
        }
        selectTableau(column, index)
    }

    fun onColumn(column: Int) {
        val pick = selection ?: return
        val moved = solMoveToTableau(state, pick, column) ?: return
        commit(moved)
    }

    fun onFoundation(index: Int) {
        val pick = selection ?: return
        val moved = solMoveToFoundation(state, pick, index) ?: return
        commit(moved)
    }

    fun onWaste() {
        if (state.waste.isEmpty()) return
        selection = if (selection == SolPick.Waste) null else SolPick.Waste
        sound.play(Sound.TAP)
        haptics.tap()
    }

    fun drawStock() {
        val current = state
        selection = null
        val next = if (current.stock.isEmpty()) {
            if (current.waste.isEmpty()) current
            else current.copy(stock = current.waste.reversed(), waste = emptyList())
        } else {
            val count = minOf(drawCount, current.stock.size)
            current.copy(
                stock = current.stock.dropLast(count),
                waste = current.waste + current.stock.takeLast(count),
            )
        }
        if (next == current) return
        state = next
        moves++
        sound.play(Sound.FLIP)
        haptics.tap()
    }

    fun restart() {
        state = freshSolitaire()
        selection = null
        moves = 0
        sound.play(Sound.UI)
        haptics.tap()
    }

    val placed = state.foundations.sumOf { it.size }
    val instruction = if (selection == null) "Touchez une carte" else "Puis une destination"

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(
                    text = "Fondations $placed/52",
                    color = Ink.Green,
                    background = Ink.Green.copy(alpha = 0.10f),
                )
                Pill(text = "Coups $moves")
            }
            AppText(
                text = instruction,
                style = Typo.small,
                color = if (selection == null) Ink.Muted else Ink.Mustard,
            )
        }

        Spacer(Modifier.height(6.dp))

        Row(
            Modifier.fillMaxWidth().height(74.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SolitaireCardView(
                card = if (state.stock.isEmpty()) null else state.stock.last(),
                faceUp = false,
                placeholder = "VIDE",
                onClick = { drawStock() },
            )

            Box(Modifier.width(64.dp).height(74.dp)) {
                if (state.waste.isEmpty()) {
                    SolitaireCardView(
                        card = null,
                        faceUp = false,
                        placeholder = "VIDE",
                        onClick = { onWaste() },
                    )
                } else {
                    val shown = state.waste.takeLast(3)
                    shown.forEachIndexed { index, card ->
                        val isTop = index == shown.lastIndex
                        SolitaireCardView(
                            card = card,
                            faceUp = true,
                            selected = isTop && selection == SolPick.Waste,
                            modifier = Modifier
                                .offset(x = (index * 5).dp)
                                .zIndex(index.toFloat()),
                            onClick = { onWaste() },
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            repeat(4) { foundationIndex ->
                val foundation = state.foundations[foundationIndex]
                if (foundation.isEmpty()) {
                    SolitaireCardView(
                        card = null,
                        faceUp = false,
                        placeholder = SOL_SUITS[foundationIndex],
                        onClick = { onFoundation(foundationIndex) },
                    )
                } else {
                    SolitaireCardView(
                        card = foundation.last(),
                        faceUp = true,
                        onClick = { onFoundation(foundationIndex) },
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PrimaryButton(
                label = "PIOCHE",
                onClick = { drawStock() },
                modifier = Modifier.weight(1f),
                color = Ink.Green,
            )
            Spacer(Modifier.width(8.dp))
            Pill(
                text = "${state.stock.size} carte(s)",
                color = Ink.Green,
                background = Ink.Green.copy(alpha = 0.10f),
            )
        }

        Spacer(Modifier.height(8.dp))

        val selected = selection
        Row(
            Modifier.fillMaxWidth().height(440.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(7) { columnIndex ->
                val column = state.tableau[columnIndex]
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onColumn(columnIndex) },
                ) {
                    if (column.isEmpty()) {
                        SolitaireCardView(
                            card = null,
                            faceUp = false,
                            placeholder = "R",
                            onClick = { onColumn(columnIndex) },
                        )
                    } else {
                        val step = if (column.size <= 1) {
                            0f
                        } else {
                            ((440f - 74f) / (column.size - 1)).coerceAtMost(26f)
                        }
                        column.forEachIndexed { index, item ->
                            val highlighted = selected is SolPick.Tableau &&
                                selected.column == columnIndex &&
                                index >= selected.index
                            SolitaireCardView(
                                card = item.card,
                                faceUp = item.faceUp,
                                selected = highlighted,
                                modifier = Modifier
                                    .offset(y = (step * index).dp)
                                    .zIndex(index.toFloat()),
                                onClick = { onTableauCard(columnIndex, index) },
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        HintBar(
            text = when (variant) {
                1 -> "Touchez une carte puis sa destination. La pioche retourne 3 cartes."
                2 -> "Mode débutant : une carte à la fois, recyclage sans limite."
                else -> "Touchez une carte puis sa destination. La pioche retourne 1 carte."
            },
        )

        Spacer(Modifier.height(6.dp))

        SecondaryButton(
            label = "NOUVELLE PARTIE",
            onClick = { restart() },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Une carte, une case vide ou le dos de la pioche. Le même composant sert
 * partout pour garder des dimensions et des zones tactiles identiques.
 */
@Composable
private fun SolitaireCardView(
    card: SolCard?,
    faceUp: Boolean,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    selected: Boolean = false,
    onClick: () -> Unit = {},
) {
    val shape = RoundedCornerShape(7.dp)
    val borderColor = when {
        selected -> Ink.Mustard
        else -> Ink.Line
    }

    Box(
        modifier = modifier
            .width(54.dp)
            .height(74.dp)
            .clip(shape)
            .background(
                when {
                    card == null -> Ink.BgDeep.copy(alpha = 0.55f)
                    !faceUp -> Ink.Blue
                    else -> Ink.Card
                },
            )
            .border(if (selected) 2.dp else 1.dp, borderColor, shape)
            .clickable(onClick = onClick),
    ) {
        when {
            card == null -> {
                AppText(
                    text = placeholder,
                    modifier = Modifier.align(Alignment.Center),
                    style = Typo.label,
                    color = Ink.Muted,
                )
            }

            !faceUp -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(5.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.32f), RoundedCornerShape(5.dp)),
                ) {
                    AppText(
                        text = "♠",
                        modifier = Modifier.align(Alignment.Center),
                        style = Typo.heading.copy(fontSize = 20.sp),
                        color = Color.White.copy(alpha = 0.45f),
                    )
                }
            }

            else -> {
                val color = if (card.red) Ink.Coral else Ink.Text
                Column(Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 3.dp)) {
                    AppText(
                        text = card.label,
                        style = Typo.label.copy(fontSize = 12.sp, letterSpacing = 0.sp),
                        color = color,
                    )
                    AppText(
                        text = card.symbol,
                        style = Typo.small.copy(fontSize = 10.sp),
                        color = color,
                    )
                    Spacer(Modifier.weight(1f))
                    AppText(
                        text = card.symbol,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        style = Typo.heading.copy(fontSize = 22.sp),
                        color = color,
                    )
                }
            }
        }
    }
}
