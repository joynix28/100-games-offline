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
import com.botillon.games.ui.Pill
import com.botillon.games.ui.Sound
import com.botillon.games.ui.Typo
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/** Points et carrés : tracer des traits, fermer des carrés, garder la main. */
object PointsCarresGame {
    val entry = GameEntry(
        id = "points-carres",
        title = "Points et Carrés",
        tagline = "Fermer le plus de carrés",
        family = Family.BOARD,
        accent = Ink.Mustard,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        PointsCarres(variant, finish)
    }
}

@Composable
private fun PointsCarres(variant: Int, finish: (Long, Boolean) -> Unit) {
    val sound = LocalSound.current
    val haptics = LocalHaptics.current
    var hLines by remember { mutableStateOf(BooleanArray(20)) }
    var vLines by remember { mutableStateOf(BooleanArray(20)) }
    var boxes by remember { mutableStateOf(IntArray(16)) }
    var turn by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf("Tracez un trait entre deux points.") }
    var outcome by remember { mutableIntStateOf(0) }
    var reported by remember { mutableStateOf(false) }

    fun boxDone(h: BooleanArray, v: BooleanArray, r: Int, c: Int): Boolean =
        h[r * 4 + c] && h[(r + 1) * 4 + c] && v[r * 5 + c] && v[r * 5 + c + 1]

    fun playLine(horizontal: Boolean, x: Int, y: Int, player: Int): Int {
        val nh = hLines.copyOf()
        val nv = vLines.copyOf()
        if (horizontal) nh[y * 4 + x] = true else nv[y * 5 + x] = true
        val nb = boxes.copyOf()
        val cells = mutableListOf<Pair<Int, Int>>()
        if (horizontal) {
            if (y > 0) cells += (y - 1) to x
            if (y < 4) cells += y to x
        } else {
            if (x > 0) cells += y to (x - 1)
            if (x < 4) cells += y to x
        }
        var gained = 0
        for ((r, c) in cells) {
            if (nb[r * 4 + c] == 0 && boxDone(nh, nv, r, c)) {
                nb[r * 4 + c] = player
                gained++
            }
        }
        hLines = nh
        vLines = nv
        boxes = nb
        return gained
    }

    fun finishGame() {
        if (reported) return
        reported = true
        val mine = boxes.count { it == 1 }
        val ai = boxes.count { it == 2 }
        val won = mine > ai
        outcome = if (won) 1 else 2
        msg = "Carrés : vous $mine — IA $ai"
        if (won) { sound.play(Sound.WIN); haptics.success() } else { sound.play(Sound.LOSE); haptics.error() }
        finish(if (won) mine * 10L else 0L, won)
    }

    fun completeTurn(horizontal: Boolean, x: Int, y: Int) {
        val gained = playLine(horizontal, x, y, 1)
        sound.play(if (gained > 0) Sound.MATCH else Sound.TAP)
        haptics.tap()
        if (boxes.all { it != 0 }) { finishGame(); return }
        if (gained > 0) msg = "Carré fermé : rejouez !" else { turn = 1; msg = "À l'IA." }
    }

    fun playerTap(px: Float, py: Float, width: Float) {
        if (turn != 0 || reported) return
        val margin = width * 0.07f
        val s = (width - 2f * margin) / 4f
        val gx = (px - margin) / s
        val gy = (py - margin) / s
        val hy = gy.roundToInt()
        val hx = gx.toInt()
        val vx = gx.roundToInt()
        val vy = gy.toInt()
        val dh = abs(gy - hy) * s
        val dv = abs(gx - vx) * s
        if (hx in 0..3 && hy in 0..4 && !hLines[hy * 4 + hx] && dh <= dv && dh < s * 0.45f) {
            completeTurn(true, hx, hy)
        } else if (vx in 0..4 && vy in 0..3 && !vLines[vy * 5 + vx] && dv < s * 0.45f) {
            completeTurn(false, vx, vy)
        }
    }

    LaunchedEffect(turn, hLines, vLines) {
        if (turn != 1 || reported) return@LaunchedEffect
        delay(if (variant == 0) 620 else 420)
        val options = mutableListOf<Triple<Boolean, Int, Int>>()
        for (y in 0..4) for (x in 0..3) if (!hLines[y * 4 + x]) options += Triple(true, x, y)
        for (y in 0..3) for (x in 0..4) if (!vLines[y * 5 + x]) options += Triple(false, x, y)
        if (options.isEmpty()) return@LaunchedEffect

        fun preview(o: Triple<Boolean, Int, Int>): Int {
            val nh = hLines.copyOf()
            val nv = vLines.copyOf()
            if (o.first) nh[o.third * 4 + o.second] = true else nv[o.third * 5 + o.second] = true
            var done = 0
            var risk = 0
            for (r in 0 until 4) for (c in 0 until 4) if (boxes[r * 4 + c] == 0) {
                val sides = listOf(
                    nh[r * 4 + c], nh[(r + 1) * 4 + c],
                    nv[r * 5 + c], nv[r * 5 + c + 1],
                ).count { it }
                if (sides == 4) done++ else if (sides == 3) risk++
            }
            return done * 10 - risk
        }

        val best = options.maxOf { preview(it) }
        val pick = if (variant == 0) options.random() else options.filter { preview(it) == best }.random()
        val gained = playLine(pick.first, pick.second, pick.third, 2)
        sound.play(if (gained > 0) Sound.POINT else Sound.TAP)
        if (boxes.all { it != 0 }) { finishGame(); return@LaunchedEffect }
        if (gained > 0) {
            msg = "L'IA ferme un carré et rejoue."
        } else {
            turn = 0
            msg = "À vous."
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            AppText("Vous : ${boxes.count { it == 1 }}", style = Typo.heading, color = Ink.Coral)
            Pill(if (turn == 0) "À vous" else "IA", color = Ink.Mustard)
            AppText("IA : ${boxes.count { it == 2 }}", style = Typo.heading, color = Ink.Blue)
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            Canvas(
                Modifier.fillMaxSize().pointerInput(turn, hLines, vLines, boxes) {
                    if (turn != 0) return@pointerInput
                    detectTapGestures { offset -> playerTap(offset.x, offset.y, size.width.toFloat()) }
                },
            ) {
                val margin = size.width * 0.07f
                val s = (size.width - 2f * margin) / 4f
                for (r in 0 until 4) for (c in 0 until 4) {
                    val owner = boxes[r * 4 + c]
                    if (owner != 0) {
                        drawRect(
                            color = (if (owner == 1) Ink.Coral else Ink.Blue).copy(alpha = 0.16f),
                            topLeft = Offset(margin + c * s, margin + r * s),
                            size = Size(s, s),
                        )
                    }
                }
                for (y in 0..4) for (x in 0..3) if (hLines[y * 4 + x]) {
                    drawLine(
                        Ink.Text,
                        Offset(margin + x * s, margin + y * s),
                        Offset(margin + (x + 1) * s, margin + y * s),
                        strokeWidth = s * 0.07f,
                    )
                }
                for (y in 0..3) for (x in 0..4) if (vLines[y * 5 + x]) {
                    drawLine(
                        Ink.Text,
                        Offset(margin + x * s, margin + y * s),
                        Offset(margin + x * s, margin + (y + 1) * s),
                        strokeWidth = s * 0.07f,
                    )
                }
                for (y in 0..4) for (x in 0..4) {
                    drawCircle(Ink.Text, s * 0.065f, Offset(margin + x * s, margin + y * s))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        AppText(msg, style = Typo.small, color = Ink.Soft)
        Spacer(Modifier.height(8.dp))
        HintBar(
            if (variant == 0) "Facile : l'IA trace parfois un trait au hasard."
            else "Fermer un carré donne un tour supplémentaire. Un trait sur 3 côtés offre un carré.",
            accent = Ink.Mustard,
        )
    }
}
