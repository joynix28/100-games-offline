package com.botillon.games.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.input.pointer.pointerInput
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

/** Backgammon simplifié : deux pions chacun, deux dés, arrivée exacte. */
object BackgammonGame {
    val entry = GameEntry(
        id = "backgammon",
        title = "Backgammon",
        tagline = "Deux pions, deux dés, course exacte",
        family = Family.BOARD,
        accent = Ink.Blue,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        Backgammon(variant, finish)
    }
}

@Composable
private fun Backgammon(variant: Int, finish: (Long, Boolean) -> Unit) {
    val sound = LocalSound.current
    val haptics = LocalHaptics.current
    var pos by remember { mutableStateOf(listOf(0, 0, 0, 0)) }
    var dice by remember { mutableStateOf(listOf(0, 0)) }
    var used by remember { mutableStateOf(booleanArrayOf(false, false)) }
    var selectedDie by remember { mutableIntStateOf(0) }
    var turn by remember { mutableIntStateOf(0) }
    var rolled by remember { mutableStateOf(false) }
    var turns by remember { mutableIntStateOf(1) }
    var msg by remember { mutableStateOf("Lancez les dés pour commencer.") }
    var outcome by remember { mutableIntStateOf(0) }
    var reported by remember { mutableStateOf(false) }

    fun endPlayerTurn() {
        turn = 1
        rolled = false
        dice = listOf(0, 0)
        used = booleanArrayOf(false, false)
        selectedDie = 0
        msg = "L'IA lance les dés."
    }

    fun movePlayer(pawn: Int) {
        if (turn != 0 || !rolled || reported || used[selectedDie]) return
        val d = dice[selectedDie]
        if (d == 0 || pos[pawn] + d > 12) return
        val np = pos.toMutableList()
        np[pawn] += d
        pos = np
        sound.play(Sound.TAP)
        haptics.tap()
        if (np[0] == 12 && np[1] == 12) {
            reported = true
            outcome = 1
            sound.play(Sound.WIN)
            haptics.success()
            finish((900L - turns * 25L).coerceAtLeast(100L), true)
            return
        }
        val nu = used.copyOf()
        nu[selectedDie] = true
        used = nu
        val next = nu.indexOfFirst { !it }
        if (next == -1) {
            endPlayerTurn()
        } else {
            selectedDie = next
            val can = (0..1).any { p -> pos[p] + dice[next] <= 12 }
            if (!can) endPlayerTurn() else msg = "Encore un dé à jouer."
        }
    }

    fun tapBoard(cell: Int) {
        if (turn != 0 || !rolled) return
        val candidates = (0..1).filter { pos[it] == cell }
        if (candidates.isEmpty()) return
        val pawn = candidates.firstOrNull { pos[it] + dice[selectedDie] <= 12 } ?: candidates.first()
        movePlayer(pawn)
    }

    LaunchedEffect(turn, rolled, dice, used, pos) {
        if (turn == 0 && rolled && !reported) {
            val any = (0..1).any { i -> !used[i] && (0..1).any { p -> pos[p] + dice[i] <= 12 } }
            if (!any) {
                delay(700)
                msg = "Aucun coup possible avec ces dés."
                endPlayerTurn()
            }
        }
    }

    LaunchedEffect(turn) {
        if (turn != 1 || reported) return@LaunchedEffect
        delay(600)
        val d1 = Random.nextInt(1, 7)
        val d2 = Random.nextInt(1, 7)
        dice = listOf(d1, d2)
        used = booleanArrayOf(false, false)
        rolled = true
        msg = "L'IA a fait $d1 et $d2."
        delay(480)
        var p = pos.toMutableList()
        var u = used.copyOf()
        for (i in 0..1) {
            val d = if (i == 0) d1 else d2
            val cands = listOf(2, 3).filter { p[it] + d <= 12 }
            if (cands.isNotEmpty()) {
                val pawn = if (variant == 0) cands.random() else cands.maxByOrNull { p[it] }!!
                p[pawn] += d
                u[i] = true
                pos = p.toList()
                used = u.copyOf()
                sound.play(Sound.TAP)
                delay(360)
            }
        }
        if (p[2] == 12 && p[3] == 12) {
            if (!reported) {
                reported = true
                outcome = 2
                sound.play(Sound.LOSE)
                haptics.error()
                finish(0L, false)
            }
        } else {
            turns++
            turn = 0
            rolled = false
            dice = listOf(0, 0)
            used = booleanArrayOf(false, false)
            selectedDie = 0
            msg = "À vous de lancer."
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            AppText("Vous : ${pos[0] + pos[1]}/24", style = Typo.heading, color = Ink.Coral)
            Pill("Tour $turns", color = Ink.Blue)
            AppText("IA : ${pos[2] + pos[3]}/24", style = Typo.heading, color = Ink.Blue)
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(290.dp)) {
            Canvas(
                Modifier.fillMaxSize().pointerInput(turn, rolled, pos, selectedDie, dice) {
                    if (turn != 0 || !rolled) return@pointerInput
                    detectTapGestures { offset ->
                        if (offset.y < size.height / 2f) return@detectTapGestures
                        val cw = size.width / 12f
                        tapBoard((offset.x / cw).toInt().coerceIn(0, 11))
                    }
                },
            ) {
                val cw = size.width / 12f
                for (i in 0 until 12) {
                    drawRect(
                        color = if (i % 2 == 0) Ink.Card else Ink.CardAlt,
                        topLeft = Offset(i * cw, 0f),
                        size = Size(cw, size.height),
                    )
                    drawLine(Ink.Line, Offset(i * cw, 0f), Offset(i * cw, size.height), strokeWidth = 1.5f)
                }
                drawLine(Ink.Line, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), strokeWidth = 2f)
                for (p in 0..1) {
                    val prog = pos[p]
                    val cx = if (prog == 12) size.width - cw * 0.30f else prog * cw + cw / 2f
                    val cy = size.height * (0.62f + p * 0.12f)
                    drawCircle(Ink.Card, cw * 0.33f, Offset(cx, cy))
                    drawCircle(Ink.Coral, cw * 0.28f, Offset(cx, cy))
                }
                for (p in 2..3) {
                    val prog = pos[p]
                    val cx = if (prog == 12) cw * 0.30f else (11 - prog) * cw + cw / 2f
                    val cy = size.height * (0.30f - (p - 2) * 0.12f)
                    drawCircle(Ink.Card, cw * 0.33f, Offset(cx, cy))
                    drawCircle(Ink.Blue, cw * 0.28f, Offset(cx, cy))
                }
                for (i in 0..11) {
                    drawCircle(Ink.Muted.copy(alpha = 0.35f), 2.2f, Offset(i * cw + cw / 2f, size.height / 2f))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (i in 0..1) {
                val active = turn == 0 && rolled && !used[i] && i == selectedDie
                Panel(
                    Modifier.weight(1f).height(54.dp).clickable(enabled = turn == 0 && rolled && !used[i]) { selectedDie = i },
                    color = if (active) Ink.Mustard.copy(alpha = 0.22f) else Ink.Card,
                ) {
                    Box(Modifier.fillMaxSize().padding(4.dp), contentAlignment = Alignment.Center) {
                        AppText(
                            if (rolled) "dé ${dice[i]}" else "dé —",
                            style = Typo.heading,
                            color = if (active) Ink.Mustard else if (used[i]) Ink.Muted else Ink.Soft,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        PrimaryButton(
            label = if (turn == 0) "Lancer les dés" else "L'IA joue…",
            onClick = {
                if (turn == 0 && !rolled && !reported) {
                    dice = listOf(Random.nextInt(1, 7), Random.nextInt(1, 7))
                    used = booleanArrayOf(false, false)
                    selectedDie = 0
                    rolled = true
                    msg = "Choisissez un dé, puis un pion."
                    sound.play(Sound.TAP)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            color = Ink.Blue,
            enabled = turn == 0 && !rolled && !reported,
        )
        Spacer(Modifier.height(8.dp))
        AppText(msg, style = Typo.small, color = Ink.Soft, maxLines = 2, softWrap = true)
        Spacer(Modifier.height(6.dp))
        HintBar("Touchez un dé, puis un pion dans la moitié basse. Arrivée exacte sur 12.", accent = Ink.Blue)
    }
}
