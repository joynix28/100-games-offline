package com.botillon.games.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import com.botillon.games.ui.Sound
import com.botillon.games.ui.Typo

/** Bridges : tracer un chemin unique qui traverse toutes les îles, deux ponts par île au maximum. */
object BridgesGame {
    val entry = GameEntry(
        id = "bridges",
        title = "Bridges",
        tagline = "Un seul chemin, toutes les îles",
        family = Family.LOGIC,
        accent = Ink.Teal,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        BridgesScreen(variant, finish)
    }
}

private val BRIDGE_GRIDS = listOf(3 to 2, 3 to 3, 4 to 3)

@Composable
private fun BridgesScreen(variant: Int, finish: (Long, Boolean) -> Unit) {
    val (cols, rows) = BRIDGE_GRIDS[variant]
    val count = cols * rows
    var edges by remember(variant) { mutableStateOf(emptySet<Pair<Int, Int>>()) }
    var selected by remember(variant) { mutableIntStateOf(-1) }
    var moves by remember(variant) { mutableIntStateOf(0) }
    var reported by remember(variant) { mutableStateOf(false) }
    val sound = LocalSound.current
    val haptics = LocalHaptics.current

    fun rowOf(index: Int) = index / cols
    fun colOf(index: Int) = index % cols
    fun degree(index: Int) = edges.count { it.first == index || it.second == index }

    fun aligned(a: Int, b: Int): Boolean {
        if (a == b) return false
        val ra = rowOf(a)
        val rb = rowOf(b)
        val ca = colOf(a)
        val cb = colOf(b)
        if (ra == rb) return kotlin.math.abs(ca - cb) == 1
        if (ca == cb) return kotlin.math.abs(ra - rb) == 1
        return false
    }

    fun connected(): Boolean {
        val parent = IntArray(count) { it }
        fun root(x: Int): Int {
            var node = x
            while (parent[node] != node) node = parent[node]
            return node
        }
        edges.forEach { (a, b) ->
            val ra = root(a)
            val rb = root(b)
            if (ra != rb) parent[ra] = rb
        }
        return (0 until count).all { root(it) == root(0) }
    }

    fun tapIsland(index: Int) {
        if (reported) return
        if (selected < 0) {
            selected = index
            sound.play(Sound.TAP)
            return
        }
        if (selected == index) {
            selected = -1
            return
        }
        val a = selected
        val b = index
        if (!aligned(a, b)) {
            selected = b
            sound.play(Sound.UI)
            return
        }
        val key = minOf(a, b) to maxOf(a, b)
        if (key in edges) {
            edges = edges - key
            moves++
            selected = -1
            sound.play(Sound.UI)
            return
        }
        if (degree(a) >= 2 || degree(b) >= 2) {
            selected = -1
            sound.play(Sound.UI)
            return
        }
        edges = edges + key
        moves++
        selected = -1
        sound.play(Sound.TAP)
        haptics.tap()
        if (edges.size == count - 1 && connected()) {
            reported = true
            sound.play(Sound.WIN)
            haptics.success()
            finish((1000L - moves * 6L).coerceAtLeast(100L), true)
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Pill(
                "PONTS ${edges.size} / ${count - 1}",
                color = Ink.Teal,
                background = Ink.Teal.copy(alpha = 0.12f),
            )
            Spacer(Modifier.weight(1f))
            AppText("Touchers $moves", style = Typo.small, color = Ink.Muted)
        }
        Spacer(Modifier.height(8.dp))

        BoxWithConstraints(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            val boardHeight = (maxWidth * (rows - 1).toFloat() / (cols - 1).coerceAtLeast(1))
                .coerceAtLeast(230.dp)
            val pad = 34.dp
            fun posX(c: Int): Dp = pad + (maxWidth - pad * 2) * (c / (cols - 1).toFloat())
            fun posY(r: Int): Dp = pad + (boardHeight - pad * 2) * (r / (rows - 1).toFloat())

            Box(Modifier.fillMaxWidth().height(boardHeight)) {
                Canvas(Modifier.fillMaxSize()) {
                    val sx = size.width - pad.toPx() * 2
                    val sy = size.height - pad.toPx() * 2
                    fun xOf(c: Int) = pad.toPx() + sx * (c / (cols - 1).toFloat())
                    fun yOf(r: Int) = pad.toPx() + sy * (r / (rows - 1).toFloat())
                    edges.forEach { (a, b) ->
                        drawLine(
                            Ink.Teal,
                            Offset(xOf(colOf(a)), yOf(rowOf(a))),
                            Offset(xOf(colOf(b)), yOf(rowOf(b))),
                            6f,
                            cap = StrokeCap.Round,
                        )
                    }
                }
                for (index in 0 until count) {
                    val deg = degree(index)
                    Box(
                        Modifier
                            .offset(x = posX(colOf(index)) - 21.dp, y = posY(rowOf(index)) - 21.dp)
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                when {
                                    selected == index -> Ink.Coral
                                    deg > 0 -> Ink.Teal.copy(alpha = 0.16f)
                                    else -> Ink.Card
                                },
                            )
                            .border(
                                2.dp,
                                when {
                                    selected == index -> Ink.Coral
                                    deg == 2 -> Ink.Teal
                                    else -> Ink.Line
                                },
                                RoundedCornerShape(12.dp),
                            )
                            .clickable { tapIsland(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        AppText(
                            "$deg",
                            style = Typo.small,
                            color = if (selected == index) Color.White else if (deg == 2) Ink.Teal else Ink.Muted,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        HintBar(
            "Reliez toutes les îles en un chemin unique. Deux ponts maximum par île.",
            accent = Ink.Teal,
        )
    }
}
