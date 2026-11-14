package com.botillon.games.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.botillon.games.ui.AppText
import com.botillon.games.ui.Family
import com.botillon.games.ui.GameEntry
import com.botillon.games.ui.HintBar
import com.botillon.games.ui.Ink
import com.botillon.games.ui.LocalHaptics
import com.botillon.games.ui.LocalSound
import com.botillon.games.ui.Pill
import com.botillon.games.ui.PrimaryButton
import com.botillon.games.ui.SecondaryButton
import com.botillon.games.ui.Sound
import com.botillon.games.ui.Typo
import kotlin.random.Random

/** Poker Dice : cinq dés, deux relances, une combinaison à valider. */
object PokerDiceGame {
    val entry = GameEntry(
        id = "poker-dice",
        title = "Poker Dice",
        tagline = "Cinq dés, trois lancers, la meilleure main",
        family = Family.CARD,
        accent = Ink.Pink,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        PokerDice(variant, finish)
    }
}

private val PIPS_POKER = listOf(
    listOf(4),
    listOf(0, 8),
    listOf(0, 4, 8),
    listOf(0, 2, 6, 8),
    listOf(0, 2, 4, 6, 8),
    listOf(0, 2, 3, 5, 6, 8),
)

private fun evaluerPoker(des: List<Int>): Pair<String, Int> {
    val comptes = des.groupingBy { it }.eachCount().values.sortedDescending()
    val distinctes = des.toSortedSet().toList()
    val quinte = distinctes.size == 5 && distinctes.last() - distinctes.first() == 4
    return when {
        quinte -> "Quinte" to 40
        comptes.first() >= 4 -> "Carré" to 35
        comptes.first() == 3 && comptes.getOrElse(1) { 0 } == 2 -> "Full" to 30
        comptes.first() == 3 -> "Brelan" to 25
        comptes.count { it >= 2 } >= 2 -> "Deux paires" to 20
        comptes.first() == 2 -> "Paire" to 10
        else -> "Rien" to 0
    }
}

@Composable
private fun DePoker(valeur: Int, garde: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val rayon = size.minDimension * 0.16f
        drawRoundRect(color = Ink.Card, cornerRadius = CornerRadius(rayon))
        drawRoundRect(
            color = if (garde) Ink.Pink else Ink.Line,
            cornerRadius = CornerRadius(rayon),
            style = Stroke(width = if (garde) 3f else 2f),
        )
        val d = size.minDimension
        val ox = (size.width - d) / 2f
        val oy = (size.height - d) / 2f
        val pas = d / 4f
        PIPS_POKER[valeur - 1].forEach { index ->
            val centre = Offset(
                ox + pas * (1 + (index % 3)),
                oy + pas * (1 + (index / 3)),
            )
            drawCircle(Ink.Text, radius = d * 0.065f, center = centre)
        }
    }
}

@Composable
private fun PokerDice(variant: Int, finish: (Long, Boolean) -> Unit) {
    val totalMains = listOf(3, 5, 7)[variant]
    val seuil = listOf(50, 100, 150)[variant].toLong()
    val sound = LocalSound.current
    val haptics = LocalHaptics.current
    var des by remember { mutableStateOf(List(5) { Random.nextInt(1, 7) }) }
    var gardes by remember { mutableStateOf(emptySet<Int>()) }
    var lancers by remember { mutableIntStateOf(1) }
    var main by remember { mutableIntStateOf(1) }
    var total by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("Touchez les dés à garder, puis relancez.") }
    var fini by remember { mutableStateOf(false) }
    var reported by remember { mutableStateOf(false) }
    val (nomMain, pointsMain) = evaluerPoker(des)

    fun relancer() {
        if (fini || lancers >= 3) return
        des = des.mapIndexed { index, valeur ->
            if (index in gardes) valeur else Random.nextInt(1, 7)
        }
        lancers++
        sound.play(Sound.FLIP)
        haptics.tap()
    }

    fun valider() {
        if (reported) return
        val (nom, points) = evaluerPoker(des)
        total += points
        message = "$nom : +$points points."
        if (points > 0) {
            sound.play(Sound.WIN)
            haptics.success()
        } else {
            sound.play(Sound.LOSE)
            haptics.error()
        }
        if (main >= totalMains) {
            reported = true
            fini = true
            finish(total.toLong(), total >= seuil)
        } else {
            main++
            des = List(5) { Random.nextInt(1, 7) }
            gardes = emptySet()
            lancers = 1
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Main $main / $totalMains")
            Pill("Total $total", color = Ink.Pink, background = Ink.Pink.copy(alpha = 0.12f))
            Pill("Lancers $lancers / 3")
        }
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            des.forEachIndexed { index, valeur ->
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    DePoker(
                        valeur = valeur,
                        garde = index in gardes,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(62.dp)
                            .clickable(enabled = !fini && lancers < 3) {
                                gardes = if (index in gardes) gardes - index else gardes + index
                            },
                    )
                    Spacer(Modifier.height(5.dp))
                    AppText(
                        if (index in gardes) "gardé" else "libre",
                        style = Typo.label.copy(fontSize = 8.sp, letterSpacing = 0.4.sp),
                        color = if (index in gardes) Ink.Pink else Ink.Muted,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        AppText("$nomMain · $pointsMain pts", style = Typo.heading, color = Ink.Text)
        Spacer(Modifier.height(6.dp))
        AppText(
            message,
            style = Typo.body,
            color = Ink.Soft,
            align = TextAlign.Center,
            maxLines = 2,
            softWrap = true,
        )
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PrimaryButton(
                label = if (lancers < 3) "Relancer (${3 - lancers})" else "Trois lancers",
                onClick = { relancer() },
                modifier = Modifier.weight(1f),
                color = Ink.Pink,
                enabled = lancers < 3 && !fini,
            )
            SecondaryButton(
                label = "Valider",
                onClick = { valider() },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        HintBar("Quinte 40, carré 35, full 30, brelan 25, deux paires 20, paire 10.")
    }
}
