package com.botillon.games.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
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

/** Puzzle : reconstituer un tableau peint au Canvas en glissant les tuiles dans le trou. */
object PuzzleGame {
    val entry = GameEntry(
        id = "puzzle-image",
        title = "Puzzle",
        tagline = "Reconstituer le tableau",
        family = Family.LOGIC,
        accent = Ink.Pink,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        PuzzleScreen(variant, finish)
    }
}

private fun puzzleOrder(n: Int, steps: Int): List<Int> {
    val order = MutableList(n * n) { it }
    var empty = n * n - 1
    var previous = -1
    repeat(steps) {
        val r = empty / n
        val c = empty % n
        val options = mutableListOf<Int>()
        if (r > 0) options += empty - n
        if (r < n - 1) options += empty + n
        if (c > 0) options += empty - 1
        if (c < n - 1) options += empty + 1
        val usable = options.filter { it != previous }
        val pick = (if (usable.isEmpty()) options else usable).random()
        val swap = order[empty]
        order[empty] = order[pick]
        order[pick] = swap
        previous = empty
        empty = pick
    }
    if (order.indices.all { order[it] == it }) {
        order[n * n - 1] = order[n * n - 2]
        order[n * n - 2] = n * n - 1
    }
    return order
}

@Composable
private fun PuzzleScreen(variant: Int, finish: (Long, Boolean) -> Unit) {
    val n = if (variant == 0) 3 else 4
    val steps = listOf(45, 90, 160)[variant]
    var order by remember(variant) { mutableStateOf(puzzleOrder(n, steps)) }
    var moves by remember(variant) { mutableIntStateOf(0) }
    var reported by remember(variant) { mutableStateOf(false) }
    val sound = LocalSound.current
    val haptics = LocalHaptics.current
    val tile = if (n == 3) 98.dp else 74.dp
    val blank = n * n - 1

    fun tapSlot(index: Int) {
        if (reported) return
        val hole = order.indexOf(blank)
        val dr = kotlin.math.abs(index / n - hole / n)
        val dc = kotlin.math.abs(index % n - hole % n)
        if (dr + dc != 1) {
            sound.play(Sound.UI)
            return
        }
        val next = order.toMutableList()
        val swap = next[index]
        next[index] = next[hole]
        next[hole] = swap
        order = next
        moves++
        sound.play(Sound.TAP)
        haptics.tap()
        if (next.indices.all { next[it] == it }) {
            reported = true
            sound.play(Sound.WIN)
            haptics.success()
            val bonus = (400L - moves * 3L).coerceAtLeast(0L)
            finish(600L + variant * 200L + bonus, true)
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Pill(
                "TUILES ${n * n - 1}",
                color = Ink.Pink,
                background = Ink.Pink.copy(alpha = 0.12f),
            )
            Spacer(Modifier.weight(1f))
            AppText("Coups $moves", style = Typo.small, color = Ink.Muted)
        }
        Spacer(Modifier.height(12.dp))

        Column(Modifier.padding(top = 4.dp)) {
            for (r in 0 until n) {
                Row {
                    for (c in 0 until n) {
                        val index = r * n + c
                        val piece = order[index]
                        Box(
                            Modifier
                                .size(tile)
                                .padding(1.5.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (piece == blank) Ink.CardAlt else Ink.Card)
                                .clickable { tapSlot(index) },
                        ) {
                            if (piece != blank) {
                                Canvas(Modifier.fillMaxSize()) {
                                    val tw = size.width
                                    val th = size.height
                                    val fullW = tw * n
                                    val fullH = th * n
                                    val col = piece % n
                                    val row = piece / n
                                    clipRect(0f, 0f, tw, th) {
                                        translate(left = -col * tw, top = -row * th) {
                                            drawPuzzleArt(fullW, fullH)
                                        }
                                    }
                                }
                            } else {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    AppText("·", style = Typo.title, color = Ink.Muted)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        HintBar("Touchez une tuile voisine du trou pour la faire glisser.", accent = Ink.Pink)
    }
}

/** Petit paysage peint : ciel, soleil, collines, maison et arbre. */
private fun DrawScope.drawPuzzleArt(w: Float, h: Float) {
    drawRect(Color(0xFFA9D3E6), size = Size(w, h))
    drawCircle(Color(0xFFF4C95D), radius = h * 0.13f, center = Offset(w * 0.76f, h * 0.18f))
    drawCircle(Color(0xFFFFF0BE), radius = h * 0.055f, center = Offset(w * 0.76f, h * 0.18f))
    drawCircle(Color.White.copy(alpha = 0.88f), radius = h * 0.048f, center = Offset(w * 0.17f, h * 0.17f))
    drawCircle(Color.White.copy(alpha = 0.88f), radius = h * 0.066f, center = Offset(w * 0.24f, h * 0.14f))
    drawCircle(Color.White.copy(alpha = 0.88f), radius = h * 0.042f, center = Offset(w * 0.31f, h * 0.17f))

    val backHill = Path().apply {
        moveTo(0f, h * 0.66f)
        quadraticBezierTo(w * 0.22f, h * 0.44f, w * 0.48f, h * 0.63f)
        quadraticBezierTo(w * 0.72f, h * 0.78f, w, h * 0.56f)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    drawPath(backHill, Color(0xFF8DBB79))
    val frontHill = Path().apply {
        moveTo(0f, h * 0.80f)
        quadraticBezierTo(w * 0.30f, h * 0.68f, w * 0.55f, h * 0.82f)
        quadraticBezierTo(w * 0.80f, h * 0.92f, w, h * 0.76f)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    drawPath(frontHill, Color(0xFF5F9457))

    drawRect(Color(0xFFF6EBD6), topLeft = Offset(w * 0.16f, h * 0.52f), size = Size(w * 0.24f, h * 0.24f))
    val roof = Path().apply {
        moveTo(w * 0.12f, h * 0.54f)
        lineTo(w * 0.28f, h * 0.40f)
        lineTo(w * 0.44f, h * 0.54f)
        close()
    }
    drawPath(roof, Color(0xFFC1503D))
    drawRect(Color(0xFF7B5236), topLeft = Offset(w * 0.21f, h * 0.64f), size = Size(w * 0.06f, h * 0.12f))
    drawRect(Color(0xFF8BC1D8), topLeft = Offset(w * 0.31f, h * 0.58f), size = Size(w * 0.06f, h * 0.06f))

    drawRect(Color(0xFF7B5236), topLeft = Offset(w * 0.72f, h * 0.66f), size = Size(w * 0.035f, h * 0.16f))
    drawCircle(Color(0xFF3F7A4A), radius = w * 0.085f, center = Offset(w * 0.737f, h * 0.62f))
    drawCircle(Color(0xFF4E8F52), radius = w * 0.060f, center = Offset(w * 0.70f, h * 0.58f))
    drawCircle(Color(0xFF4E8F52), radius = w * 0.055f, center = Offset(w * 0.78f, h * 0.59f))

    drawCircle(Color(0xFFE4572E), radius = w * 0.012f, center = Offset(w * 0.12f, h * 0.88f))
    drawCircle(Color(0xFFF4C95D), radius = w * 0.012f, center = Offset(w * 0.16f, h * 0.91f))
    drawCircle(Color(0xFFD96C8A), radius = w * 0.012f, center = Offset(w * 0.20f, h * 0.87f))
}
