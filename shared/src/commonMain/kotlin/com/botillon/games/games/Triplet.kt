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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
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
import com.botillon.games.ui.Sound
import com.botillon.games.ui.Typo

/** Triplet : piocher des tuiles et réunir trois motifs identiques dans une file de sept places. */
object TripletGame {
    val entry = GameEntry(
        id = "triplet",
        title = "Triplet",
        tagline = "Trois motifs identiques, pas un de plus",
        family = Family.LOGIC,
        accent = Ink.Mustard,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        TripletScreen(variant, finish)
    }
}

private val TRIPLET_INKS = listOf(Ink.Coral, Ink.Teal, Ink.Blue, Ink.Violet, Ink.Mustard, Ink.Green)

private fun tripletBoard(types: Int): List<Int> {
    val tiles = MutableList(types * 3) { it / 3 }
    tiles.shuffle()
    return tiles
}

@Composable
private fun TripletScreen(variant: Int, finish: (Long, Boolean) -> Unit) {
    val types = listOf(6, 8, 12)[variant]
    val columns = 6
    var board by remember(variant) { mutableStateOf(tripletBoard(types)) }
    var tray by remember(variant) { mutableStateOf(emptyList<Int>()) }
    var triples by remember(variant) { mutableIntStateOf(0) }
    var reported by remember(variant) { mutableStateOf(false) }
    val sound = LocalSound.current
    val haptics = LocalHaptics.current

    fun pick(index: Int) {
        if (reported || board[index] < 0) return
        val type = board[index]
        val nextBoard = board.toMutableList()
        nextBoard[index] = -1
        board = nextBoard
        val nextTray = tray + type
        if (nextTray.count { it == type } >= 3) {
            var removed = 0
            tray = nextTray.filter {
                if (it == type && removed < 3) {
                    removed++
                    false
                } else {
                    true
                }
            }
            triples++
            sound.play(Sound.MATCH)
        } else {
            tray = nextTray
            sound.play(Sound.TAP)
        }
        haptics.tap()
        if (board.all { it < 0 }) {
            reported = true
            sound.play(Sound.WIN)
            haptics.success()
            finish(triples * 100L + 400L, true)
        } else if (tray.size >= 7) {
            reported = true
            sound.play(Sound.LOSE)
            haptics.error()
            finish(triples * 100L, false)
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Pill(
                "TRIPLETS $triples",
                color = Ink.Mustard,
                background = Ink.Mustard.copy(alpha = 0.18f),
            )
            Spacer(Modifier.weight(1f))
            AppText("File ${tray.size} / 7", style = Typo.small, color = if (tray.size >= 6) Ink.Coral else Ink.Muted)
        }
        Spacer(Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            for (r in 0 until (board.size + columns - 1) / columns) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    for (c in 0 until columns) {
                        val index = r * columns + c
                        if (index < board.size && board[index] >= 0) {
                            TripletTile(board[index]) { pick(index) }
                        } else {
                            Spacer(Modifier.size(54.dp))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Panel(Modifier.fillMaxWidth(), color = Ink.CardAlt) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (slot in 0 until 7) {
                    val type = tray.getOrNull(slot)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Ink.Card)
                            .border(1.dp, Ink.Line, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (type != null) TripletToken(type, 34.dp)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        HintBar("Les triplés disparaissent. La file pleine à 7 sans triplé perd la partie.", accent = Ink.Mustard)
    }
}

@Composable
private fun TripletTile(type: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .size(54.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(Ink.Card)
            .border(1.dp, Ink.Line, RoundedCornerShape(11.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawTripletShape(type, this.size.minDimension * 0.27f)
        }
    }
}

@Composable
private fun TripletToken(type: Int, tileSize: Dp) {
    Canvas(Modifier.size(tileSize)) {
        drawTripletShape(type, this.size.minDimension * 0.30f)
    }
}

private fun DrawScope.drawTripletShape(type: Int, radius: Float) {
    val color = TRIPLET_INKS[type % TRIPLET_INKS.size]
    val shape = type / TRIPLET_INKS.size
    val middle = center
    when (shape) {
        0 -> drawCircle(color, radius = radius, center = middle)
        1 -> drawRoundRect(
            color,
            topLeft = Offset(middle.x - radius, middle.y - radius),
            size = Size(radius * 2f, radius * 2f),
            cornerRadius = CornerRadius(radius * 0.26f),
        )
        else -> {
            val triangle = Path().apply {
                moveTo(middle.x, middle.y - radius)
                lineTo(middle.x + radius, middle.y + radius)
                lineTo(middle.x - radius, middle.y + radius)
                close()
            }
            drawPath(triangle, color)
        }
    }
}
