package com.botillon.games.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.geometry.CornerRadius
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
import com.botillon.games.ui.Pill
import com.botillon.games.ui.Sound
import com.botillon.games.ui.Typo

/** Tours de Hanoï : déplacer la pile, comparer au nombre optimal de coups. */
object HanoiGame {
    val entry = GameEntry(
        id = "tours-hanoi",
        title = "Tours de Hanoï",
        tagline = "Déplacer la pile en un minimum de coups",
        family = Family.BOARD,
        accent = Ink.Violet,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        Hanoi(variant, finish)
    }
}

@Composable
private fun Hanoi(variant: Int, finish: (Long, Boolean) -> Unit) {
    val sound = LocalSound.current
    val haptics = LocalHaptics.current
    val n = listOf(5, 6, 7, 8)[variant]
    val optimal = (1 shl n) - 1
    var pegs by remember { mutableStateOf(listOf((n downTo 1).toList(), emptyList<Int>(), emptyList<Int>())) }
    var selected by remember { mutableIntStateOf(-1) }
    var moves by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf("Touchez une tige, puis une autre.") }
    var outcome by remember { mutableIntStateOf(0) }
    var reported by remember { mutableStateOf(false) }

    fun tapPeg(idx: Int) {
        if (reported) return
        if (selected < 0) {
            if (pegs[idx].isNotEmpty()) selected = idx
            return
        }
        if (selected == idx) {
            selected = -1
            return
        }
        val disk = pegs[selected].last()
        val top = pegs[idx].lastOrNull()
        if (top == null || disk < top) {
            val next = pegs.toMutableList()
            next[selected] = pegs[selected].dropLast(1)
            next[idx] = pegs[idx] + disk
            pegs = next
            moves++
            selected = -1
            sound.play(Sound.TAP)
            haptics.tap()
            msg = "Disque $disk déplacé."
            if (next[2].size == n) {
                reported = true
                outcome = 1
                val score = if (moves == optimal) 1000L else (1000L - (moves - optimal) * 12L).coerceAtLeast(100L)
                msg = "Résolu en $moves coups (optimal : $optimal)."
                sound.play(Sound.WIN)
                haptics.success()
                finish(score, true)
            }
        } else {
            msg = "Un disque plus grand ne va pas sur un plus petit."
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            AppText("Coups : $moves", style = Typo.heading, color = Ink.Violet)
            Pill("Optimal : $optimal", color = Ink.Violet)
            AppText("$n disques", style = Typo.small, color = Ink.Muted)
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(330.dp)) {
            Canvas(
                Modifier.fillMaxSize().pointerInput(pegs, selected) {
                    if (outcome != 0) return@pointerInput
                    detectTapGestures { offset -> tapPeg((offset.x / (size.width / 3f)).toInt().coerceIn(0, 2)) }
                },
            ) {
                val baseY = size.height * 0.90f
                val diskH = size.height * 0.072f
                val maxW = size.width / 3f * 0.88f
                val centers = listOf(size.width / 6f, size.width / 2f, size.width * 5f / 6f)
                for (i in 0..2) {
                    if (i == selected) {
                        drawRoundRect(
                            color = Ink.Mustard.copy(alpha = 0.16f),
                            topLeft = Offset(centers[i] - maxW / 2f, size.height * 0.08f),
                            size = Size(maxW, baseY - size.height * 0.08f),
                            cornerRadius = CornerRadius(14f),
                        )
                    }
                    drawLine(Ink.Soft, Offset(centers[i], baseY), Offset(centers[i], size.height * 0.12f), strokeWidth = 7f)
                    drawLine(Ink.Soft, Offset(centers[i] - maxW / 2f, baseY), Offset(centers[i] + maxW / 2f, baseY), strokeWidth = 10f)
                }
                for (i in 0..2) {
                    for ((index, disk) in pegs[i].withIndex()) {
                        val w = maxW * disk / n
                        val y = baseY - (index + 1) * diskH
                        val color = when (disk % 3) {
                            0 -> Ink.Violet
                            1 -> Ink.Teal
                            else -> Ink.Mustard
                        }
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(centers[i] - w / 2f, y),
                            size = Size(w, diskH * 0.86f),
                            cornerRadius = CornerRadius(diskH * 0.35f),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        AppText(msg, style = Typo.small, color = if (outcome == 0) Ink.Soft else Ink.Muted, maxLines = 2, softWrap = true)
        Spacer(Modifier.height(8.dp))
        HintBar("Tige 1 vers tige 3. Jamais un grand disque sur un petit. Optimal = 2^n − 1.", accent = Ink.Violet)
    }
}
