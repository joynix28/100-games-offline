package com.botillon.games.games

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import kotlin.math.roundToInt

/** Drop It : lâcher des formes dans le bocal et empiler sans dépasser le bord. */
object DropItGame {
    val entry = GameEntry(
        id = "drop-it",
        title = "Drop It",
        tagline = "Empiler dans le bocal",
        family = Family.LOGIC,
        accent = Ink.Coral,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        DropItScreen(variant, finish)
    }
}

private const val DROP_COLS = 7

private class DropPiece(val type: Int, val width: Int)

private val DROP_INKS = listOf(Ink.Coral, Ink.Teal, Ink.Blue, Ink.Mustard)

private fun dropPieces(target: Int, seed: Int): List<DropPiece> {
    val random = kotlin.random.Random(seed)
    return List(target) {
        if (random.nextInt(100) < 26) DropPiece(3, 2) else DropPiece(random.nextInt(3), 1)
    }
}

private class DroppedBlock(val col: Int, val width: Int, val level: Int, val type: Int)

@Composable
private fun DropItScreen(variant: Int, finish: (Long, Boolean) -> Unit) {
    val target = listOf(8, 10, 12)[variant]
    val capacity = listOf(7, 6, 5)[variant]
    val pieces = remember(variant) { dropPieces(target, variant * 31 + 5) }
    var cursor by remember(variant) { mutableFloatStateOf(0f) }
    var direction by remember(variant) { mutableIntStateOf(1) }
    var heights by remember(variant) { mutableStateOf(IntArray(DROP_COLS)) }
    var blocks by remember(variant) { mutableStateOf(emptyList<DroppedBlock>()) }
    var dropped by remember(variant) { mutableIntStateOf(0) }
    var reported by remember(variant) { mutableStateOf(false) }
    val sound = LocalSound.current
    val haptics = LocalHaptics.current
    val speed = 0.016f + variant * 0.006f

    LaunchedEffect(variant) {
        while (true) {
            delay(20)
            if (!reported) {
                cursor += direction * speed
                if (cursor >= DROP_COLS - 1f) {
                    cursor = DROP_COLS - 1f
                    direction = -1
                }
                if (cursor <= 0f) {
                    cursor = 0f
                    direction = 1
                }
            }
        }
    }

    fun drop() {
        if (reported) return
        val piece = pieces[dropped.coerceAtMost(pieces.lastIndex)]
        val col = cursor.roundToInt().coerceIn(0, DROP_COLS - piece.width)
        val base = (col until col + piece.width).maxOf { heights[it] }
        val top = base + 1
        if (top > capacity) {
            reported = true
            sound.play(Sound.LOSE)
            haptics.error()
            finish(blocks.size * 30L, false)
            return
        }
        val next = heights.copyOf()
        for (c in col until col + piece.width) next[c] = top
        heights = next
        blocks = blocks + DroppedBlock(col, piece.width, base, piece.type)
        dropped++
        sound.play(Sound.TAP)
        haptics.tap()
        if (dropped == pieces.size) {
            reported = true
            sound.play(Sound.WIN)
            haptics.success()
            finish(500L + variant * 200L + (capacity * DROP_COLS - heights.sum()) * 4L, true)
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Pill(
                "PIÈCES $dropped / $target",
                color = Ink.Coral,
                background = Ink.Coral.copy(alpha = 0.12f),
            )
            Spacer(Modifier.weight(1f))
            AppText("Hauteur max $capacity", style = Typo.small, color = Ink.Muted)
        }
        Spacer(Modifier.height(8.dp))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            Canvas(Modifier.fillMaxSize()) {
                val jarLeft = size.width * 0.06f
                val jarRight = size.width * 0.94f
                val jarTop = size.height * 0.18f
                val jarBottom = size.height * 0.96f
                val cellW = (jarRight - jarLeft) / DROP_COLS
                val cellH = (jarBottom - jarTop) / capacity

                drawRoundRect(
                    Color(0xFFEAF4F2),
                    topLeft = Offset(jarLeft, jarTop),
                    size = Size(jarRight - jarLeft, jarBottom - jarTop),
                    cornerRadius = CornerRadius(22f),
                )
                for (level in 1 until capacity) {
                    drawLine(
                        Ink.Line,
                        Offset(jarLeft + 4f, jarBottom - level * cellH),
                        Offset(jarRight - 4f, jarBottom - level * cellH),
                        1.5f,
                    )
                }
                for (block in blocks) {
                    val x = jarLeft + block.col * cellW + 2f
                    val y = jarBottom - (block.level + 1) * cellH + 2f
                    val w = block.width * cellW - 4f
                    val h = cellH - 4f
                    drawRoundRect(
                        DROP_INKS[block.type],
                        topLeft = Offset(x, y),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(minOf(w, h) * 0.20f),
                    )
                    drawRoundRect(
                        Color.White.copy(alpha = 0.25f),
                        topLeft = Offset(x + w * 0.14f, y + h * 0.14f),
                        size = Size(w * 0.26f, h * 0.16f),
                        cornerRadius = CornerRadius(h * 0.08f),
                    )
                }
                drawLine(
                    Ink.Soft,
                    Offset(jarLeft - 8f, jarTop),
                    Offset(jarRight + 8f, jarTop),
                    6f,
                    cap = StrokeCap.Round,
                )

                val piece = pieces[dropped.coerceAtMost(pieces.lastIndex)]
                val col = cursor.roundToInt().coerceIn(0, DROP_COLS - piece.width)
                val w = piece.width * cellW - 4f
                val h = cellH * 0.72f
                val x = jarLeft + col * cellW + 2f
                val y = jarTop - h - 14f
                drawRoundRect(
                    DROP_INKS[piece.type],
                    topLeft = Offset(x, y),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(minOf(w, h) * 0.20f),
                )
                drawLine(
                    DROP_INKS[piece.type].copy(alpha = 0.35f),
                    Offset(x + w / 2f, y + h + 3f),
                    Offset(x + w / 2f, jarTop - 2f),
                    3f,
                    cap = StrokeCap.Round,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        PrimaryButton("DÉPOSER", { drop() }, Modifier.fillMaxWidth(), Ink.Coral)
        Spacer(Modifier.height(8.dp))
        HintBar("La forme glisse toute seule : déposez-la au bon endroit, sans dépasser le bocal.", accent = Ink.Coral)
    }
}
