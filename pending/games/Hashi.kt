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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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

/** Hashi : relier les îles par un ou deux ponts, sans croisement, jusqu'à satisfaire chaque chiffre. */
object HashiGame {
    val entry = GameEntry(
        id = "hashi",
        title = "Hashi",
        tagline = "Relier les îles sans croiser",
        family = Family.LOGIC,
        accent = Ink.Blue,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        HashiScreen(variant, finish)
    }
}

private class HashiIsland(val x: Int, val y: Int, val need: Int)

private val HASHI_ISLANDS = listOf(
    HashiIsland(0, 0, 3), HashiIsland(2, 0, 3), HashiIsland(4, 0, 3),
    HashiIsland(0, 2, 3), HashiIsland(2, 2, 3), HashiIsland(4, 2, 3),
    HashiIsland(0, 4, 2), HashiIsland(2, 4, 4), HashiIsland(4, 4, 2),
)

/** Quelques ponts de départ pour adoucir les deux premières variantes. */
private fun hashiStart(variant: Int): Map<Pair<Int, Int>, Int> = when (variant) {
    0 -> mapOf((0 to 3) to 2, (3 to 6) to 1, (2 to 5) to 2)
    1 -> mapOf((1 to 4) to 1)
    else -> emptyMap()
}

@Composable
private fun HashiScreen(variant: Int, finish: (Long, Boolean) -> Unit) {
    var bridges by remember(variant) { mutableStateOf(hashiStart(variant)) }
    var selected by remember(variant) { mutableIntStateOf(-1) }
    var moves by remember(variant) { mutableIntStateOf(0) }
    var reported by remember(variant) { mutableStateOf(false) }
    val sound = LocalSound.current
    val haptics = LocalHaptics.current

    fun degree(index: Int): Int = bridges.entries.sumOf { (pair, count) ->
        if (pair.first == index || pair.second == index) count else 0
    }

    fun canLink(a: Int, b: Int): Boolean {
        val ia = HASHI_ISLANDS[a]
        val ib = HASHI_ISLANDS[b]
        if (ia.x != ib.x && ia.y != ib.y) return false
        return HASHI_ISLANDS.indices.none { k ->
            if (k == a || k == b) return@none false
            val other = HASHI_ISLANDS[k]
            if (ia.x == ib.x) {
                other.x == ia.x && other.y > minOf(ia.y, ib.y) && other.y < maxOf(ia.y, ib.y)
            } else {
                other.y == ia.y && other.x > minOf(ia.x, ib.x) && other.x < maxOf(ia.x, ib.x)
            }
        }
    }

    fun crosses(a: Int, b: Int): Boolean {
        val ia = HASHI_ISLANDS[a]
        val ib = HASHI_ISLANDS[b]
        val newHorizontal = ia.y == ib.y
        return bridges.keys.any { (p, q) ->
            val ic = HASHI_ISLANDS[p]
            val id = HASHI_ISLANDS[q]
            if ((ic.y == id.y) == newHorizontal) return@any false
            val h1 = if (newHorizontal) ia else ic
            val h2 = if (newHorizontal) ib else id
            val v1 = if (newHorizontal) ic else ia
            val v2 = if (newHorizontal) id else ib
            v1.x > minOf(h1.x, h2.x) && v1.x < maxOf(h1.x, h2.x) &&
                h1.y > minOf(v1.y, v2.y) && h1.y < maxOf(v1.y, v2.y)
        }
    }

    fun connected(): Boolean {
        val parent = IntArray(HASHI_ISLANDS.size) { it }
        fun root(x: Int): Int {
            var node = x
            while (parent[node] != node) node = parent[node]
            return node
        }
        bridges.keys.forEach { (p, q) ->
            val a = root(p)
            val b = root(q)
            if (a != b) parent[a] = b
        }
        return HASHI_ISLANDS.indices.all { root(it) == root(0) }
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
        if (!canLink(a, b)) {
            selected = b
            sound.play(Sound.UI)
            return
        }
        val key = minOf(a, b) to maxOf(a, b)
        val current = bridges[key] ?: 0
        val next = (current + 1) % 3
        if (next > current) {
            val overA = degree(a) + 1 > HASHI_ISLANDS[a].need
            val overB = degree(b) + 1 > HASHI_ISLANDS[b].need
            if (overA || overB || crosses(a, b)) {
                selected = -1
                sound.play(Sound.UI)
                return
            }
        }
        val updated = bridges.toMutableMap()
        if (next == 0) updated.remove(key) else updated[key] = next
        bridges = updated
        moves++
        selected = -1
        sound.play(if (next == 2) Sound.MATCH else Sound.TAP)
        haptics.tap()
        if (HASHI_ISLANDS.indices.all { degree(it) == HASHI_ISLANDS[it].need } && connected()) {
            reported = true
            sound.play(Sound.WIN)
            haptics.success()
            finish((900L - moves * 8L).coerceAtLeast(120L), true)
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Pill(
                "PONTS ${bridges.values.sum()}",
                color = Ink.Blue,
                background = Ink.Blue.copy(alpha = 0.12f),
            )
            Spacer(Modifier.weight(1f))
            AppText("Touchers $moves", style = Typo.small, color = Ink.Muted)
        }
        Spacer(Modifier.height(8.dp))

        BoxWithConstraints(
            Modifier.fillMaxWidth().aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            val pad = 34.dp
            val span = maxWidth - pad * 2
            fun posX(x: Int): Dp = pad + span * (x / 4f)
            fun posY(y: Int): Dp = pad + span * (y / 4f)

            Canvas(Modifier.fillMaxSize()) {
                val left = pad.toPx()
                val usable = size.width - left * 2
                fun xOf(v: Int) = left + usable * (v / 4f)
                fun yOf(v: Int) = left + usable * (v / 4f)
                bridges.forEach { (pair, count) ->
                    val a = HASHI_ISLANDS[pair.first]
                    val b = HASHI_ISLANDS[pair.second]
                    val start = Offset(xOf(a.x), yOf(a.y))
                    val end = Offset(xOf(b.x), yOf(b.y))
                    if (count == 1) {
                        drawLine(Ink.Soft, start, end, 5f, cap = StrokeCap.Round)
                    } else {
                        val dx = end.x - start.x
                        val dy = end.y - start.y
                        val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                        val offX = -dy / len * 5f
                        val offY = dx / len * 5f
                        drawLine(Ink.Soft, Offset(start.x + offX, start.y + offY), Offset(end.x + offX, end.y + offY), 4f, cap = StrokeCap.Round)
                        drawLine(Ink.Soft, Offset(start.x - offX, start.y - offY), Offset(end.x - offX, end.y - offY), 4f, cap = StrokeCap.Round)
                    }
                }
            }

            HASHI_ISLANDS.forEachIndexed { index, island ->
                val done = degree(index) == island.need
                Box(
                    Modifier
                        .offset(x = posX(island.x) - 21.dp, y = posY(island.y) - 21.dp)
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (selected == index) Ink.Blue else Ink.Card)
                        .border(2.dp, if (done) Ink.Teal else Ink.Line, CircleShape)
                        .clickable { tapIsland(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    AppText(
                        "${island.need}",
                        style = Typo.heading,
                        color = if (selected == index) Color.White else if (done) Ink.Teal else Ink.Text,
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))
        HintBar("Touchez deux îles alignées : 1 pont, 2 ponts, puis 0. Aucun croisement.", accent = Ink.Blue)
    }
}
