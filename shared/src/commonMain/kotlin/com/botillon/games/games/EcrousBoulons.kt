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
import androidx.compose.foundation.shape.CircleShape
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Écrous et Boulons : choisir un écrou et le visser sur le boulon qui réclame sa couleur. */
object EcrousBoulonsGame {
    val entry = GameEntry(
        id = "ecrous-boulons",
        title = "Écrous & Boulons",
        tagline = "Visser les écrous au bon endroit",
        family = Family.LOGIC,
        accent = Ink.Blue,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        EcrousBoulonsScreen(variant, finish)
    }
}

private val NUT_INKS = listOf(Ink.Coral, Ink.Teal, Ink.Blue, Ink.Violet)

private fun boltPlans(variant: Int): List<List<Int>> {
    val bolts = listOf(3, 4, 5)[variant]
    val depth = if (variant == 0) 2 else 3
    val random = kotlin.random.Random(variant * 41 + 9)
    return List(bolts) { List(depth) { random.nextInt(NUT_INKS.size) } }
}

@Composable
private fun EcrousBoulonsScreen(variant: Int, finish: (Long, Boolean) -> Unit) {
    val plans = remember(variant) { boltPlans(variant) }
    var filled by remember(variant) { mutableStateOf(List(plans.size) { 0 }) }
    var nuts by remember(variant) { mutableStateOf(plans.flatten().shuffled()) }
    var selected by remember(variant) { mutableIntStateOf(-1) }
    var mistakes by remember(variant) { mutableIntStateOf(0) }
    var reported by remember(variant) { mutableStateOf(false) }
    val sound = LocalSound.current
    val haptics = LocalHaptics.current

    fun tapNut(index: Int) {
        if (reported) return
        selected = if (selected == index) -1 else index
        sound.play(Sound.TAP)
        haptics.tap()
    }

    fun tapBolt(index: Int) {
        if (reported) return
        if (selected !in nuts.indices) {
            sound.play(Sound.UI)
            return
        }
        val plan = plans[index]
        val color = nuts[selected]
        if (filled[index] >= plan.size || plan[filled[index]] != color) {
            mistakes++
            sound.play(Sound.LOSE)
            haptics.error()
            return
        }
        val nextFilled = filled.toMutableList()
        nextFilled[index]++
        filled = nextFilled
        val nextNuts = nuts.toMutableList()
        nextNuts.removeAt(selected)
        nuts = nextNuts
        selected = -1
        sound.play(if (nextFilled[index] == plan.size) Sound.MATCH else Sound.TAP)
        haptics.tap()
        if (filled.indices.all { nextFilled[it] == plans[it].size }) {
            reported = true
            sound.play(Sound.WIN)
            haptics.success()
            finish((700L + variant * 150L - mistakes * 30L).coerceAtLeast(80L), true)
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Pill(
                "ÉCROUS ${nuts.size}",
                color = Ink.Blue,
                background = Ink.Blue.copy(alpha = 0.12f),
            )
            Spacer(Modifier.weight(1f))
            Pill("ERREURS $mistakes", color = if (mistakes > 0) Ink.Coral else Ink.Muted)
        }
        Spacer(Modifier.height(10.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Top,
        ) {
            plans.forEachIndexed { index, plan ->
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected in nuts.indices) Ink.Blue.copy(alpha = 0.06f) else Color.Transparent)
                        .clickable { tapBolt(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.fillMaxWidth().height(210.dp)) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawBolt(plan, filled[index])
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        plan.forEachIndexed { slot, color ->
                            Box(
                                Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (slot < filled[index]) NUT_INKS[color].copy(alpha = 0.30f)
                                        else NUT_INKS[color],
                                    ),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    AppText(
                        "${filled[index]} / ${plan.size}",
                        style = Typo.label,
                        color = if (filled[index] == plan.size) Ink.Teal else Ink.Muted,
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Panel(Modifier.fillMaxWidth(), color = Ink.CardAlt) {
            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                AppText(
                    if (selected in nuts.indices) "Écrou choisi : touchez un boulon" else "Touchez un écrou dans la boîte",
                    style = Typo.small,
                    color = if (selected in nuts.indices) Ink.Blue else Ink.Muted,
                )
                Spacer(Modifier.height(8.dp))
                for (row in nuts.chunked(6).indices) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        nuts.chunked(6)[row].forEachIndexed { column, color ->
                            val index = row * 6 + column
                            Box(
                                Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(if (selected == index) Ink.Card else Color.Transparent)
                                    .border(
                                        2.dp,
                                        if (selected == index) Ink.Blue else Color.Transparent,
                                        CircleShape,
                                    )
                                    .clickable { tapNut(index) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Canvas(Modifier.size(36.dp)) {
                                    drawHexNut(center.x, center.y, size.minDimension * 0.44f, NUT_INKS[color])
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        HintBar("Chaque boulon réclame ses couleurs dans l'ordre des pastilles, du bas vers le haut.", accent = Ink.Blue)
    }
}

private fun DrawScope.drawBolt(plan: List<Int>, placed: Int) {
    val rodWidth = size.width * 0.26f
    val left = (size.width - rodWidth) / 2f
    val top = size.height * 0.06f
    val bottom = size.height * 0.97f
    drawRoundRect(
        Color(0xFFC9C1B4),
        topLeft = Offset(left, top),
        size = Size(rodWidth, bottom - top),
        cornerRadius = CornerRadius(rodWidth * 0.3f),
    )
    var y = bottom - 8f
    while (y > top + 30f) {
        drawLine(Color(0xFFADA395), Offset(left + 2f, y), Offset(left + rodWidth - 2f, y), 2f)
        y -= 9f
    }
    drawRoundRect(
        Color(0xFF8C8376),
        topLeft = Offset(left - rodWidth * 0.22f, top),
        size = Size(rodWidth * 1.44f, rodWidth * 0.5f),
        cornerRadius = CornerRadius(rodWidth * 0.22f),
    )
    val nutRadius = minOf(size.width * 0.34f, (bottom - top - 10f) / (plan.size * 2f + 0.8f))
    val step = nutRadius * 1.9f
    val baseY = bottom - nutRadius
    for (slot in 0 until placed) {
        drawHexNut(size.width / 2f, baseY - slot * step, nutRadius, NUT_INKS[plan[slot]])
    }
}

private fun DrawScope.drawHexNut(cx: Float, cy: Float, radius: Float, color: Color) {
    val path = Path()
    for (corner in 0 until 6) {
        val angle = PI / 3.0 * corner - PI / 6.0
        val x = cx + radius * cos(angle).toFloat()
        val y = cy + radius * sin(angle).toFloat()
        if (corner == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
    drawCircle(Color.Black.copy(alpha = 0.16f), radius = radius * 0.44f, center = Offset(cx, cy))
    drawCircle(Ink.Card, radius = radius * 0.32f, center = Offset(cx, cy))
}
