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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import kotlin.math.min
import kotlin.random.Random

/** Taupe : une grille 3 × 3, une seule taupe à la fois, trente secondes. */
object TaupeGame {
    val entry = GameEntry(
        id = "taupe",
        title = "Taupe",
        tagline = "Tapez la taupe, pas le trou",
        family = Family.ARCADE,
        accent = Ink.Green,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        Taupe(variant, finish)
    }
}

@Composable
private fun Taupe(variant: Int, finish: (Long, Boolean) -> Unit) {
    val sound = LocalSound.current
    val haptics = LocalHaptics.current
    val life = listOf(1.15f, 0.85f, 0.62f)[variant]
    val gapMax = listOf(0.70f, 0.52f, 0.38f)[variant]

    var mole by remember { mutableIntStateOf(-1) }
    var age by remember { mutableFloatStateOf(0f) }
    var wait by remember { mutableFloatStateOf(0.35f) }
    var score by remember { mutableIntStateOf(0) }
    var left by remember { mutableFloatStateOf(30f) }
    var over by remember { mutableStateOf(false) }

    fun report() {
        if (over) return
        over = true
        finish(score * 10L, score >= 8)
    }

    LaunchedEffect(Unit) {
        var last = 0L
        while (!over) {
            withFrameNanos { now ->
                if (last == 0L) {
                    last = now
                    return@withFrameNanos
                }
                val dt = min(0.05f, (now - last) / 1_000_000_000f)
                last = now
                left -= dt
                if (left <= 0f) {
                    report()
                    return@withFrameNanos
                }
                if (mole < 0) {
                    wait -= dt
                    if (wait <= 0f) {
                        mole = Random.nextInt(9)
                        age = 0f
                        wait = gapMax + Random.nextFloat() * 0.22f
                    }
                } else {
                    age += dt
                    if (age >= life) {
                        mole = -1
                        wait = gapMax * 0.35f
                    }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText("Points ${score * 10}", style = Typo.heading, color = Ink.Text)
            Pill(
                text = "${left.toInt().coerceAtLeast(0)} s",
                color = if (left < 8f) Ink.Coral else Ink.Soft,
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(over) {
                    detectTapGestures { offset ->
                        if (over || mole < 0) return@detectTapGestures
                        val side = min(size.width, size.height).toFloat()
                        val ox = (size.width - side) / 2f
                        val oy = (size.height - side) / 2f
                        val px = offset.x - ox
                        val py = offset.y - oy
                        if (px < 0f || py < 0f || px >= side || py >= side) return@detectTapGestures
                        val cell = side / 3f
                        val index = (py / cell).toInt() * 3 + (px / cell).toInt()
                        if (index == mole) {
                            score++
                            mole = -1
                            wait = gapMax * 0.35f
                            sound.play(Sound.POINT)
                            haptics.tap()
                        }
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val side = min(size.width, size.height)
                val ox = (size.width - side) / 2f
                val oy = (size.height - side) / 2f
                val cell = side / 3f
                drawRoundRect(
                    color = Ink.Card,
                    topLeft = Offset(ox, oy),
                    size = Size(side, side),
                    cornerRadius = CornerRadius(side * 0.06f),
                )
                for (i in 0..3) {
                    drawLine(Ink.Line, Offset(ox + i * cell, oy), Offset(ox + i * cell, oy + side), 2f)
                    drawLine(Ink.Line, Offset(ox, oy + i * cell), Offset(ox + side, oy + i * cell), 2f)
                }
                for (i in 0 until 9) {
                    val cx = ox + (i % 3) * cell + cell / 2f
                    val cy = oy + (i / 3) * cell + cell / 2f
                    drawOval(
                        color = Ink.BgDeep,
                        topLeft = Offset(cx - cell * 0.30f, cy + cell * 0.04f),
                        size = Size(cell * 0.60f, cell * 0.32f),
                    )
                    if (i == mole) {
                        val rise = (age / 0.16f).coerceIn(0f, 1f)
                        val bodyY = cy + cell * 0.02f + (1f - rise) * cell * 0.26f
                        drawCircle(Ink.Green.copy(alpha = 0.35f), cell * 0.27f, Offset(cx, bodyY))
                        drawCircle(Ink.Green, cell * 0.23f, Offset(cx, bodyY))
                        drawCircle(Ink.Green, cell * 0.09f, Offset(cx - cell * 0.17f, bodyY - cell * 0.12f))
                        drawCircle(Ink.Green, cell * 0.09f, Offset(cx + cell * 0.17f, bodyY - cell * 0.12f))
                        drawCircle(Ink.Text, cell * 0.035f, Offset(cx - cell * 0.08f, bodyY - cell * 0.03f))
                        drawCircle(Ink.Text, cell * 0.035f, Offset(cx + cell * 0.08f, bodyY - cell * 0.03f))
                        drawCircle(Ink.Pink, cell * 0.055f, Offset(cx, bodyY + cell * 0.08f))
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        HintBar("Tapez la taupe dès qu'elle sort du trou. Trente secondes.")
    }
}
