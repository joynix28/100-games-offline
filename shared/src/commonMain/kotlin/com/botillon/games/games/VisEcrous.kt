package com.botillon.games.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
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

/** Vis et Écrous : dévisser les plaques une à une, en commençant par celles du dessus. */
object VisEcrousGame {
    val entry = GameEntry(
        id = "vis-ecrous",
        title = "Vis & Écrous",
        tagline = "Démonter la pile dans l'ordre",
        family = Family.LOGIC,
        accent = Ink.Violet,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        VisEcrousScreen(variant, finish)
    }
}

private val PIN_INKS = listOf(Ink.Violet, Ink.Teal, Ink.Mustard, Ink.Blue, Ink.Pink)
private val PIN_OFFSETS = listOf(0.00f, 0.13f, 0.00f, 0.13f, 0.00f)
private val PIN_X = listOf(0.16f, 0.84f, 0.16f, 0.84f)
private val PIN_Y = listOf(0.24f, 0.24f, 0.76f, 0.76f)

private class PinRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
}

@Composable
private fun VisEcrousScreen(variant: Int, finish: (Long, Boolean) -> Unit) {
    val count = listOf(3, 4, 5)[variant]
    val step = when (count) {
        3 -> 0.17f
        4 -> 0.15f
        else -> 0.125f
    }
    val plateHeight = 0.20f
    var present by remember(variant) { mutableStateOf(List(count) { true }) }
    var screws by remember(variant) { mutableStateOf(List(count) { setOf(0, 1, 2, 3) }) }
    var taps by remember(variant) { mutableIntStateOf(0) }
    var reported by remember(variant) { mutableStateOf(false) }
    val sound = LocalSound.current
    val haptics = LocalHaptics.current

    fun pinRect(index: Int, width: Float, height: Float): PinRect {
        val left = width * (0.17f + PIN_OFFSETS[index])
        val top = height * (0.06f + index * step)
        return PinRect(left, top, left + width * 0.66f, top + height * plateHeight)
    }

    fun covered(index: Int, x: Float, y: Float, rects: List<PinRect>): Boolean =
        (0 until index).any { present[it] && rects[it].contains(x, y) }

    fun tapAt(x: Float, y: Float, width: Float, height: Float) {
        if (reported) return
        val rects = List(count) { pinRect(it, width, height) }
        var bestPlate = -1
        var bestScrew = -1
        var bestDistance = (width * 0.07f) * (width * 0.07f)
        for (index in 0 until count) {
            if (!present[index]) continue
            for (screw in screws[index]) {
                val px = rects[index].left + rects[index].width * PIN_X[screw]
                val py = rects[index].top + rects[index].height * PIN_Y[screw]
                if (covered(index, px, py, rects)) continue
                val distance = (px - x) * (px - x) + (py - y) * (py - y)
                if (distance < bestDistance) {
                    bestDistance = distance
                    bestPlate = index
                    bestScrew = screw
                }
            }
        }
        if (bestPlate < 0) return
        val nextScrews = screws.toMutableList()
        nextScrews[bestPlate] = nextScrews[bestPlate] - bestScrew
        screws = nextScrews
        taps++
        sound.play(Sound.TAP)
        haptics.tap()
        var nextPresent = present
        if (nextScrews[bestPlate].isEmpty()) {
            val removed = present.toMutableList()
            removed[bestPlate] = false
            present = removed
            nextPresent = removed
            sound.play(Sound.MATCH)
        }
        if (nextPresent.none { it }) {
            reported = true
            sound.play(Sound.WIN)
            haptics.success()
            finish((600L + variant * 150L - taps * 4L).coerceAtLeast(100L), true)
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Pill(
                "PLAQUES ${present.count { it }} / $count",
                color = Ink.Violet,
                background = Ink.Violet.copy(alpha = 0.12f),
            )
            Spacer(Modifier.weight(1f))
            AppText("Vis $taps", style = Typo.small, color = Ink.Muted)
        }
        Spacer(Modifier.height(8.dp))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(variant) {
                        val boardWidth = size.width.toFloat()
                        val boardHeight = size.height.toFloat()
                        detectTapGestures { offset ->
                            tapAt(offset.x, offset.y, boardWidth, boardHeight)
                        }
                    },
            ) {
                val rects = List(count) { pinRect(it, size.width, size.height) }
                for (index in count - 1 downTo 0) {
                    if (!present[index]) continue
                    val rect = rects[index]
                    drawRoundRect(
                        Ink.Line.copy(alpha = 0.55f),
                        topLeft = Offset(rect.left + 3f, rect.top + 5f),
                        size = Size(rect.width, rect.height),
                        cornerRadius = CornerRadius(16f),
                    )
                    drawRoundRect(
                        PIN_INKS[index % PIN_INKS.size],
                        topLeft = Offset(rect.left, rect.top),
                        size = Size(rect.width, rect.height),
                        cornerRadius = CornerRadius(16f),
                    )
                    drawRoundRect(
                        Color.White.copy(alpha = 0.16f),
                        topLeft = Offset(rect.left + 8f, rect.top + 7f),
                        size = Size(rect.width - 16f, rect.height * 0.22f),
                        cornerRadius = CornerRadius(10f),
                    )
                }
                for (index in 0 until count) {
                    if (!present[index]) continue
                    val rect = rects[index]
                    for (screw in screws[index]) {
                        val px = rect.left + rect.width * PIN_X[screw]
                        val py = rect.top + rect.height * PIN_Y[screw]
                        if (covered(index, px, py, rects)) continue
                        drawPin(px, py, size.width * 0.034f, PIN_INKS[index % PIN_INKS.size])
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        HintBar("Touchez les vis visibles. Une plaque tombe quand ses quatre vis sont parties.", accent = Ink.Violet)
    }
}

private fun DrawScope.drawPin(x: Float, y: Float, radius: Float, tint: Color) {
    drawCircle(Ink.Card, radius + 3f, Offset(x, y))
    drawCircle(tint, radius, Offset(x, y))
    drawCircle(Color(0xFFD8D1C4), radius * 0.78f, Offset(x, y))
    drawCircle(Color(0xFFB4AA9A), radius * 0.14f, Offset(x, y))
    drawLine(Ink.Soft, Offset(x - radius * 0.62f, y), Offset(x + radius * 0.62f, y), radius * 0.20f)
    drawLine(Ink.Soft, Offset(x, y - radius * 0.62f), Offset(x, y + radius * 0.62f), radius * 0.20f)
}
