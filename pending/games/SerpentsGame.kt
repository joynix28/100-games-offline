package com.botillon.games.games

import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Serpents et échelles : course à la case 100 contre l'ordinateur. */
object SerpentsGame {
    val entry = GameEntry(
        id = "serpents-echelles",
        title = "Serpents & Échelles",
        tagline = "Le premier à la case 100",
        family = Family.BOARD,
        accent = Ink.Green,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        Serpents(variant, finish)
    }
}

private val SNAKES = mapOf(16 to 6, 47 to 26, 49 to 11, 56 to 53, 62 to 19, 64 to 60, 87 to 24, 93 to 73, 95 to 75, 98 to 78)
private val LADDERS = mapOf(1 to 38, 4 to 14, 9 to 31, 21 to 42, 28 to 84, 36 to 44, 51 to 67, 71 to 91, 80 to 100)

private fun boardCell(n: Int, cell: Float): Offset {
    val row = (n - 1) / 10
    val col = (n - 1) % 10
    val x = if (row % 2 == 0) col else 9 - col
    return Offset(x * cell + cell / 2f, (9 - row) * cell + cell / 2f)
}

@Composable
private fun Serpents(variant: Int, finish: (Long, Boolean) -> Unit) {
    val sound = LocalSound.current
    val haptics = LocalHaptics.current
    var pos by remember { mutableStateOf(listOf(0, 0)) }
    var turn by remember { mutableIntStateOf(0) }
    var die by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf("À vous de lancer le dé.") }
    var outcome by remember { mutableIntStateOf(0) }
    var reported by remember { mutableStateOf(false) }

    fun roll() {
        if (outcome != 0 || reported) return
        var d = Random.nextInt(1, 7)
        if (turn == 1 && variant == 2) d = (d + 1).coerceAtMost(6)
        die = d
        val start = pos[turn]
        var next = (start + d).coerceAtMost(100)
        LADDERS[next]?.let { next = it }
        SNAKES[next]?.let { next = it }
        val list = pos.toMutableList()
        list[turn] = next
        pos = list
        sound.play(Sound.TAP)
        haptics.tap()
        if (next >= 100) {
            outcome = if (turn == 0) 1 else 2
            reported = true
            msg = if (turn == 0) "Vous atteignez 100 !" else "L'IA atteint 100."
            if (turn == 0) sound.play(Sound.WIN) else sound.play(Sound.LOSE)
            finish(if (turn == 0) 1000L else 0L, turn == 0)
            return
        }
        if (next < start) msg = "$next : serpent avalé !" else if (next != start + d) msg = "$next : échelle gravie !"
        turn = 1 - turn
    }

    LaunchedEffect(turn) {
        if (turn == 1 && outcome == 0) {
            delay(750)
            roll()
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            AppText("Vous : ${pos[0]}", style = Typo.heading, color = Ink.Coral)
            Pill(if (die == 0) "dé —" else "dé $die")
            AppText("IA : ${pos[1]}", style = Typo.heading, color = Ink.Blue)
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            Canvas(Modifier.fillMaxSize()) {
                val cell = size.width / 10f
                for (n in 1..100) {
                    val c = boardCell(n, cell)
                    drawRect(
                        color = if ((n / 10 + n % 10) % 2 == 0) Ink.Card else Ink.CardAlt,
                        topLeft = Offset(c.x - cell / 2f, c.y - cell / 2f),
                        size = androidx.compose.ui.geometry.Size(cell, cell),
                    )
                }
                for ((from, to) in LADDERS) {
                    val a = boardCell(from, cell)
                    val b = boardCell(to, cell)
                    drawLine(Ink.Green.copy(alpha = 0.30f), a, b, strokeWidth = 4f)
                }
                for ((from, to) in SNAKES) {
                    val a = boardCell(from, cell)
                    val b = boardCell(to, cell)
                    drawLine(Ink.Coral.copy(alpha = 0.28f), a, b, strokeWidth = 3f)
                }
                for (p in 0..1) {
                    val n = pos[p]
                    if (n <= 0) continue
                    val c = boardCell(n, cell)
                    val ox = if (p == 0) -cell * 0.16f else cell * 0.16f
                    drawCircle(Ink.Card, cell * 0.22f, Offset(c.x + ox, c.y))
                    drawCircle(if (p == 0) Ink.Coral else Ink.Blue, cell * 0.18f, Offset(c.x + ox, c.y))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        PrimaryButton(
            label = if (outcome != 0) "Course terminée" else if (turn == 0) "Lancer le dé" else "L'IA lance…",
            onClick = { roll() },
            modifier = Modifier.fillMaxWidth(),
            color = Ink.Green,
            enabled = turn == 0 && outcome == 0,
        )
        Spacer(Modifier.height(10.dp))
        AppText(msg, style = Typo.small, color = if (outcome == 0) Ink.Soft else Ink.Muted)
        Spacer(Modifier.height(8.dp))
        HintBar(
            if (variant == 2) "Difficile : l'IA ajoute 1 à ses dés. Échelles vertes, serpents rouges."
            else "Échelles vertes, serpents rouges. Le premier à 100 gagne.",
            accent = Ink.Green,
        )
    }
}
