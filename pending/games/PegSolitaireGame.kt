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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
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

/** Solitaire à clous : sauter par-dessus un pion pour le retirer. */
object PegSolitaireGame {
    val entry = GameEntry(
        id = "solitaire-clous",
        title = "Solitaire à Clous",
        tagline = "Laisser un seul pion sur la croix",
        family = Family.BOARD,
        accent = Ink.Coral,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        PegSolitaire(variant, finish)
    }
}

private fun pegValid(r: Int, c: Int): Boolean = r in 2..4 || c in 2..4

private fun pegInitial(variant: Int): BooleanArray {
    val b = BooleanArray(49)
    for (r in 0..6) for (c in 0..6) if (pegValid(r, c)) b[r * 7 + c] = true
    b[3 * 7 + 3] = false
    if (variant == 0) {
        b[2 * 7 + 2] = false
        b[4 * 7 + 4] = false
        b[2 * 7 + 4] = false
        b[4 * 7 + 2] = false
    }
    if (variant == 1) {
        b[2 * 7 + 2] = false
        b[4 * 7 + 4] = false
    }
    return b
}

private val PEG_DIRS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)

private fun pegMoves(b: BooleanArray, from: Int): List<Int> {
    val r = from / 7
    val c = from % 7
    val out = mutableListOf<Int>()
    for ((dr, dc) in PEG_DIRS) {
        val mr = r + dr
        val mc = c + dc
        val tr = r + 2 * dr
        val tc = c + 2 * dc
        if (tr in 0..6 && tc in 0..6 && pegValid(tr, tc) && b[mr * 7 + mc] && !b[tr * 7 + tc]) out += tr * 7 + tc
    }
    return out
}

private fun pegAnyMove(b: BooleanArray): Boolean = b.indices.any { b[it] && pegMoves(b, it).isNotEmpty() }

@Composable
private fun PegSolitaire(variant: Int, finish: (Long, Boolean) -> Unit) {
    val sound = LocalSound.current
    val haptics = LocalHaptics.current
    val startTotal = remember(variant) { pegInitial(variant).count { it } }
    var pegs by remember { mutableStateOf(pegInitial(variant)) }
    var selected by remember { mutableIntStateOf(-1) }
    var msg by remember { mutableStateOf("Sautez un pion pour l'éliminer.") }
    var outcome by remember { mutableIntStateOf(0) }
    var reported by remember { mutableStateOf(false) }

    fun report(b: BooleanArray) {
        if (reported) return
        val left = b.count { it }
        if (left == 1 || !pegAnyMove(b)) {
            reported = true
            outcome = if (left == 1) 1 else 2
            msg = if (left == 1) "Un seul pion : réussi !" else "$left pions restants, plus aucun saut."
            if (left == 1) { sound.play(Sound.WIN); haptics.success() } else { sound.play(Sound.LOSE); haptics.error() }
            finish(((startTotal - left) * 10).toLong(), left == 1)
        }
    }

    fun tap(idx: Int) {
        if (reported) return
        val targets = if (selected >= 0) pegMoves(pegs, selected) else emptyList()
        if (selected >= 0 && idx in targets) {
            val nb = pegs.copyOf()
            val from = selected
            nb[from] = false
            nb[((from / 7 + idx / 7) / 2) * 7 + ((from % 7 + idx % 7) / 2)] = false
            nb[idx] = true
            pegs = nb
            selected = -1
            sound.play(Sound.TAP)
            haptics.tap()
            report(nb)
        } else {
            selected = if (pegs[idx]) idx else -1
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            AppText("Pions : ${pegs.count { it }}", style = Typo.heading, color = Ink.Coral)
            Pill("Objectif : 1", color = Ink.Coral)
            AppText("Départs : $startTotal", style = Typo.small, color = Ink.Muted)
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            Canvas(
                Modifier.fillMaxSize().pointerInput(pegs, selected) {
                    if (outcome != 0) return@pointerInput
                    detectTapGestures { offset ->
                        val cell = size.width / 7f
                        val c = (offset.x / cell).toInt().coerceIn(0, 6)
                        val r = (offset.y / cell).toInt().coerceIn(0, 6)
                        if (pegValid(r, c)) tap(r * 7 + c)
                    }
                },
            ) {
                val cell = size.width / 7f
                for (r in 0..6) for (c in 0..6) {
                    if (!pegValid(r, c)) continue
                    val center = Offset(c * cell + cell / 2f, r * cell + cell / 2f)
                    drawCircle(Ink.CardAlt, cell * 0.40f, center)
                    drawCircle(Ink.Line, cell * 0.40f, center, style = Stroke(cell * 0.03f))
                }
                if (selected >= 0) {
                    for (target in pegMoves(pegs, selected)) {
                        val center = Offset((target % 7) * cell + cell / 2f, (target / 7) * cell + cell / 2f)
                        drawCircle(Ink.Teal.copy(alpha = 0.55f), cell * 0.12f, center)
                    }
                }
                for (i in 0 until 49) {
                    if (!pegs[i]) continue
                    val center = Offset((i % 7) * cell + cell / 2f, (i / 7) * cell + cell / 2f)
                    if (i == selected) drawCircle(Ink.Mustard, cell * 0.37f, center)
                    drawCircle(Ink.Coral, cell * 0.31f, center)
                    drawCircle(Ink.Card.copy(alpha = 0.45f), cell * 0.09f, Offset(center.x - cell * 0.09f, center.y - cell * 0.09f))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        AppText(msg, style = Typo.small, color = if (outcome == 0) Ink.Soft else Ink.Muted)
        Spacer(Modifier.height(8.dp))
        HintBar("Touchez un pion, puis un trou vide à deux cases. Le pion sauté disparaît.", accent = Ink.Coral)
    }
}
