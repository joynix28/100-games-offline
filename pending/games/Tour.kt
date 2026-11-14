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
import kotlin.math.max
import kotlin.math.min

private data class TowerBlock(val x: Float, val w: Float)

/** Tour empilée : le bloc oscille, il faut le poser au bon moment. */
object TourGame {
    val entry = GameEntry(
        id = "tour",
        title = "Tour empilée",
        tagline = "Poser droit, monter haut",
        family = Family.ARCADE,
        accent = Ink.Blue,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        Tour(variant, finish)
    }
}

@Composable
private fun Tour(variant: Int, finish: (Long, Boolean) -> Unit) {
    val sound = LocalSound.current
    val haptics = LocalHaptics.current
    val baseWidth = listOf(0.46f, 0.40f, 0.34f)[variant]
    val speed = listOf(0.46f, 0.58f, 0.72f)[variant]

    var blocks by remember { mutableStateOf(listOf(TowerBlock(0.5f - baseWidth / 2f, baseWidth))) }
    var movingX by remember { mutableFloatStateOf(0f) }
    var movingW by remember { mutableFloatStateOf(baseWidth) }
    var direction by remember { mutableIntStateOf(1) }
    var over by remember { mutableStateOf(false) }

    fun place() {
        if (over) return
        val top = blocks.last()
        val left = max(movingX, top.x)
        val right = min(movingX + movingW, top.x + top.w)
        val overlap = right - left
        if (overlap < 0.02f) {
            over = true
            sound.play(Sound.LOSE)
            haptics.error()
            finish(((blocks.size - 1) * 10).toLong(), false)
            return
        }
        blocks = blocks + TowerBlock(left, overlap)
        movingW = overlap
        movingX = 0f
        direction = 1
        sound.play(Sound.MATCH)
        haptics.tap()
        if (blocks.size >= 15) {
            over = true
            sound.play(Sound.WIN)
            finish(((blocks.size - 1) * 10).toLong(), true)
        }
    }

    LaunchedEffect(Unit) {
        var last = 0L
        while (!over) {
            withFrameNanos { now ->
                if (last == 0L) {
                    last = now
                    return@withFrameNanos
                }
                val dt = min(0.033f, (now - last) / 1_000_000_000f)
                last = now
                val current = speed + blocks.size * 0.025f
                movingX += direction * current * dt
                if (movingX < 0f) {
                    movingX = 0f
                    direction = 1
                }
                if (movingX > 1f - movingW) {
                    movingX = 1f - movingW
                    direction = -1
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
            AppText("Étage ${blocks.size - 1}", style = Typo.heading, color = Ink.Text)
            Pill("${(blocks.size - 1) * 10} pts", color = Ink.Blue)
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(over) {
                    detectTapGestures { place() }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = Ink.Card,
                    size = Size(size.width, size.height),
                    cornerRadius = CornerRadius(24f),
                )
                val visible = blocks.takeLast(11)
                val blockH = size.height * 0.048f
                val step = size.height * 0.056f
                val baseTop = size.height * 0.90f - blockH
                for ((index, block) in visible.withIndex()) {
                    val top = baseTop - index * step
                    drawRoundRect(
                        color = Ink.Accents[index % Ink.Accents.size].copy(alpha = 0.9f),
                        topLeft = Offset(block.x * size.width, top),
                        size = Size(block.w * size.width, blockH),
                        cornerRadius = CornerRadius(blockH * 0.22f),
                    )
                }
                val movingTop = baseTop - visible.size * step
                val topBlock = visible.lastOrNull()
                if (topBlock != null) {
                    val guideX = (topBlock.x + topBlock.w / 2f) * size.width
                    drawLine(
                        color = Ink.Line,
                        start = Offset(guideX, movingTop),
                        end = Offset(guideX, baseTop),
                        strokeWidth = 2f,
                    )
                }
                drawRoundRect(
                    color = Ink.Blue,
                    topLeft = Offset(movingX * size.width, movingTop),
                    size = Size(movingW * size.width, blockH),
                    cornerRadius = CornerRadius(blockH * 0.22f),
                )
                drawRoundRect(
                    color = Ink.Card.copy(alpha = 0.35f),
                    topLeft = Offset(movingX * size.width + blockH * 0.18f, movingTop + blockH * 0.18f),
                    size = Size(movingW * size.width - blockH * 0.36f, blockH * 0.16f),
                    cornerRadius = CornerRadius(blockH * 0.08f),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        HintBar("Tapez pour poser le bloc. La tour devient plus fine à chaque étage.")
    }
}
