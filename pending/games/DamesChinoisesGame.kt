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
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/** Dames chinoises : traverser l'étoile, pas et sauts, camp adverse. */
object DamesChinoisesGame {
    val entry = GameEntry(
        id = "dames-chinoises",
        title = "Dames Chinoises",
        tagline = "Gagner le camp opposé de l'étoile",
        family = Family.BOARD,
        accent = Ink.Pink,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        DamesChinoises(variant, finish)
    }
}

private val STAR_COORDS: List<Pair<Float, Float>> = List(21) { idx ->
    if (idx == 20) {
        0f to 0f
    } else {
        val arm = idx / 4
        val r = (idx % 4) + 1
        val a = (arm * 72.0 - 90.0) * PI / 180.0
        (cos(a) * r).toFloat() to (sin(a) * r).toFloat()
    }
}

private val STAR_ADJ: List<List<Int>> = run {
    val adj = MutableList(21) { mutableListOf<Int>() }
    fun link(a: Int, b: Int) {
        if (b !in adj[a]) adj[a].add(b)
        if (a !in adj[b]) adj[b].add(a)
    }
    for (arm in 0 until 5) {
        val base = arm * 4
        for (r in 0 until 3) link(base + r, base + r + 1)
        link(20, base)
        link(base + 3, ((arm + 1) % 5) * 4 + 3)
    }
    adj.map { it.toList() }
}

private fun starCollinear(a: Int, b: Int, c: Int): Boolean {
    val p = STAR_COORDS[a]
    val q = STAR_COORDS[b]
    val r = STAR_COORDS[c]
    return abs((q.first - p.first) * (r.second - p.second) - (q.second - p.second) * (r.first - p.first)) < 0.02f
}

private fun starMoves(b: IntArray, from: Int): List<Int> {
    val out = mutableListOf<Int>()
    for (mid in STAR_ADJ[from]) {
        if (b[mid] == 0) {
            out += mid
        } else {
            for (to in STAR_ADJ[mid]) {
                if (to != from && b[to] == 0 && starCollinear(from, mid, to)) out += to
            }
        }
    }
    return out.distinct()
}

private fun starDistance(idx: Int, camp: IntRange): Float = camp.minOf { g ->
    hypot(STAR_COORDS[idx].first - STAR_COORDS[g].first, STAR_COORDS[idx].second - STAR_COORDS[g].second)
}

private fun starStart(): IntArray {
    val b = IntArray(21)
    for (i in 0..3) b[i] = 1
    for (i in 8..11) b[i] = 2
    return b
}

@Composable
private fun DamesChinoises(variant: Int, finish: (Long, Boolean) -> Unit) {
    val sound = LocalSound.current
    val haptics = LocalHaptics.current
    var board by remember { mutableStateOf(starStart()) }
    var selected by remember { mutableIntStateOf(-1) }
    var turn by remember { mutableIntStateOf(0) }
    var moves by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf("Touchez un pion, puis une case libre.") }
    var outcome by remember { mutableIntStateOf(0) }
    var reported by remember { mutableStateOf(false) }

    fun winPlayer() {
        if (reported) return
        reported = true
        outcome = 1
        msg = "Votre camp est complet !"
        sound.play(Sound.WIN)
        haptics.success()
        finish((500L - moves * 6L).coerceAtLeast(100L), true)
    }

    fun tap(idx: Int) {
        if (turn != 0 || reported) return
        if (selected >= 0 && idx in starMoves(board, selected)) {
            val from = selected
            val nb = board.copyOf()
            nb[from] = 0
            nb[idx] = 1
            board = nb
            selected = -1
            moves++
            sound.play(Sound.TAP)
            haptics.tap()
            if ((8..11).all { nb[it] == 1 }) {
                winPlayer()
            } else {
                turn = 1
                msg = "À l'IA."
            }
        } else {
            selected = if (board[idx] == 1) idx else -1
        }
    }

    LaunchedEffect(turn, board) {
        if (turn != 1 || reported) return@LaunchedEffect
        delay(620)
        val options = mutableListOf<Pair<Int, Int>>()
        for (i in 0..20) if (board[i] == 2) for (to in starMoves(board, i)) options += i to to
        if (options.isEmpty()) {
            turn = 0
            msg = "L'IA est bloquée : rejouez."
            return@LaunchedEffect
        }
        val best = if (variant == 0) {
            options.random()
        } else {
            options.maxByOrNull { (from, to) -> starDistance(from, 0..3) - starDistance(to, 0..3) }!!
        }
        val nb = board.copyOf()
        nb[best.first] = 0
        nb[best.second] = 2
        board = nb
        sound.play(Sound.TAP)
        if ((0..3).all { nb[it] == 2 }) {
            if (!reported) {
                reported = true
                outcome = 2
                msg = "L'IA a gagné votre camp."
                sound.play(Sound.LOSE)
                haptics.error()
                finish(0L, false)
            }
        } else {
            turn = 0
            msg = "À vous."
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            AppText("Vous : ${board.count { it == 1 }}", style = Typo.heading, color = Ink.Coral)
            Pill("Coups : $moves", color = Ink.Pink)
            AppText("IA : ${board.count { it == 2 }}", style = Typo.heading, color = Ink.Blue)
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            Canvas(
                Modifier.fillMaxSize().pointerInput(board, turn, selected) {
                    if (outcome != 0) return@pointerInput
                    detectTapGestures { offset ->
                        val scale = minOf(size.width, size.height) * 0.105f
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        var best = -1
                        var bestD = Float.MAX_VALUE
                        for (i in 0..20) {
                            val dx = offset.x - (cx + STAR_COORDS[i].first * scale)
                            val dy = offset.y - (cy + STAR_COORDS[i].second * scale)
                            val d = dx * dx + dy * dy
                            if (d < bestD) { bestD = d; best = i }
                        }
                        if (bestD <= scale * scale * 0.35f) tap(best)
                    }
                },
            ) {
                val scale = size.minDimension * 0.105f
                val cx = size.width / 2f
                val cy = size.height / 2f
                for (i in 0..3) {
                    drawCircle(Ink.Blue.copy(alpha = 0.10f), scale * 1.15f, Offset(cx + STAR_COORDS[i].first * scale, cy + STAR_COORDS[i].second * scale))
                }
                for (i in 8..11) {
                    drawCircle(Ink.Coral.copy(alpha = 0.10f), scale * 1.15f, Offset(cx + STAR_COORDS[i].first * scale, cy + STAR_COORDS[i].second * scale))
                }
                for (i in 0..20) for (j in STAR_ADJ[i]) if (j > i) {
                    drawLine(
                        Ink.Line,
                        Offset(cx + STAR_COORDS[i].first * scale, cy + STAR_COORDS[i].second * scale),
                        Offset(cx + STAR_COORDS[j].first * scale, cy + STAR_COORDS[j].second * scale),
                        strokeWidth = 2.5f,
                    )
                }
                if (selected >= 0 && turn == 0) {
                    for (target in starMoves(board, selected)) {
                        drawCircle(Ink.Teal.copy(alpha = 0.50f), scale * 0.22f, Offset(cx + STAR_COORDS[target].first * scale, cy + STAR_COORDS[target].second * scale))
                    }
                }
                for (i in 0..20) {
                    val p = Offset(cx + STAR_COORDS[i].first * scale, cy + STAR_COORDS[i].second * scale)
                    drawCircle(Ink.Card, scale * 0.34f, p)
                    drawCircle(Ink.Line, scale * 0.34f, p, style = Stroke(2f))
                    if (board[i] != 0) {
                        if (i == selected) drawCircle(Ink.Mustard, scale * 0.42f, p)
                        drawCircle(if (board[i] == 1) Ink.Coral else Ink.Blue, scale * 0.31f, p)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        AppText(msg, style = Typo.small, color = Ink.Soft, maxLines = 2, softWrap = true)
        Spacer(Modifier.height(8.dp))
        HintBar("Un pas vers un point voisin, ou un saut par-dessus un pion. Gagnez le camp d'en face.", accent = Ink.Pink)
    }
}
