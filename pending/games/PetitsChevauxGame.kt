package com.botillon.games.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.botillon.games.ui.PrimaryButton
import com.botillon.games.ui.Sound
import com.botillon.games.ui.Typo
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Petits chevaux : quatre pions, sortie sur un 6, vingt cases puis l'écurie. */
object PetitsChevauxGame {
    val entry = GameEntry(
        id = "petits-chevaux",
        title = "Petits Chevaux",
        tagline = "Sortir avec un 6, arriver au bout",
        family = Family.BOARD,
        accent = Ink.Pink,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        PetitsChevaux(variant, finish)
    }
}

@Composable
private fun PetitsChevaux(variant: Int, finish: (Long, Boolean) -> Unit) {
    val sound = LocalSound.current
    val haptics = LocalHaptics.current
    var pawns by remember { mutableStateOf(IntArray(8)) }
    var turn by remember { mutableIntStateOf(0) }
    var die by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf("Lancez le dé. Un 6 sort un pion.") }
    var outcome by remember { mutableIntStateOf(0) }
    var reported by remember { mutableStateOf(false) }

    fun movable(player: Int, d: Int): List<Int> = (0..3).filter { p ->
        val prog = pawns[player * 4 + p]
        (prog == 0 && d == 6) || (prog in 1..19 && prog + d <= 20)
    }

    fun movePawn(player: Int, pawn: Int, d: Int) {
        if (reported) return
        val index = player * 4 + pawn
        val prog = pawns[index]
        val next = if (prog == 0) 1 else prog + d
        val copy = pawns.copyOf()
        copy[index] = next
        pawns = copy
        sound.play(Sound.TAP)
        haptics.tap()
        if (next >= 20) {
            reported = true
            outcome = if (player == 0) 1 else 2
            if (player == 0) { sound.play(Sound.WIN); haptics.success() } else sound.play(Sound.LOSE)
            finish(if (player == 0) 500L else 0L, player == 0)
            return
        }
        msg = if (player == 0) "Votre pion ${pawn + 1} avance à $next." else "L'IA avance son pion ${pawn + 1} à $next."
        turn = 1 - player
        die = 0
    }

    LaunchedEffect(turn) {
        if (turn != 1 || reported) return@LaunchedEffect
        delay(650)
        val d = Random.nextInt(1, 7)
        die = d
        msg = "L'IA a fait $d."
        delay(450)
        val options = movable(1, d)
        if (options.isEmpty()) {
            msg = "L'IA ne peut pas jouer."
            turn = 0
            die = 0
        } else {
            val pawn = when (variant) {
                0 -> options.random()
                1 -> options.maxByOrNull { pawns[4 + it] } ?: options.first()
                else -> options.maxByOrNull { p -> if (pawns[4 + p] == 0) 100 else pawns[4 + p] } ?: options.first()
            }
            movePawn(1, pawn, d)
        }
    }

    LaunchedEffect(turn, die) {
        if (turn == 0 && die > 0 && !reported && movable(0, die).isEmpty()) {
            delay(800)
            msg = "Aucun pion jouable, au tour de l'IA."
            turn = 1
            die = 0
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            AppText("Vous", style = Typo.heading, color = Ink.Coral)
            Pill(if (die == 0) "dé —" else "dé $die", color = Ink.Pink)
            AppText("IA", style = Typo.heading, color = Ink.Blue)
        }
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            Canvas(Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val radius = size.minDimension * 0.39f
                val dot = size.minDimension * 0.042f
                drawCircle(Ink.Card, radius = radius, center = Offset(cx, cy), style = Stroke(dot * 0.55f))
                for (i in 0 until 20) {
                    val a = (90.0 + i * 18.0) * PI / 180.0
                    val p = Offset(cx + cos(a).toFloat() * radius, cy + sin(a).toFloat() * radius)
                    drawCircle(Ink.Card, dot, p)
                    drawCircle(if (i == 0) Ink.Coral else if (i == 10) Ink.Blue else Ink.Line, dot, p, style = Stroke(dot * 0.28f))
                }
                for (player in 0..1) for (p in 0..3) {
                    val prog = pawns[player * 4 + p]
                    val start = if (player == 0) 0 else 10
                    val idx = if (prog <= 0) start else (start + prog - 1) % 20
                    val angle = (90.0 + idx * 18.0 + (p - 1.5f) * 1.4f) * PI / 180.0
                    val rr = radius + (p - 1.5f) * dot * 0.45f
                    val c = Offset(cx + cos(angle).toFloat() * rr, cy + sin(angle).toFloat() * rr)
                    drawCircle(Ink.Card, dot * 0.62f, c)
                    drawCircle(if (player == 0) Ink.Coral else Ink.Blue, dot * 0.50f, c)
                }
                drawCircle(Ink.CardAlt, size.minDimension * 0.09f, Offset(cx, cy))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            for (p in 0..3) {
                val prog = pawns[p]
                val can = turn == 0 && die > 0 && (prog == 0 && die == 6 || prog in 1..19 && prog + die <= 20)
                Panel(Modifier.weight(1f).height(58.dp)) {
                    Box(
                        Modifier.fillMaxSize().clickable { if (can) movePawn(0, p, die) }.padding(4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AppText("P${p + 1}", style = Typo.label, color = Ink.Muted)
                            AppText(
                                if (prog == 0) "départ" else if (prog >= 20) "but" else "$prog",
                                style = Typo.heading,
                                color = if (can) Ink.Coral else Ink.Soft,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        PrimaryButton(
            label = if (turn == 0) "Lancer le dé" else "L'IA joue…",
            onClick = {
                if (turn == 0 && die == 0 && !reported) {
                    die = Random.nextInt(1, 7)
                    msg = "Vous avez fait $die."
                    sound.play(Sound.TAP)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            color = Ink.Pink,
            enabled = turn == 0 && die == 0 && !reported,
        )
        Spacer(Modifier.height(8.dp))
        AppText(msg, style = Typo.small, color = Ink.Soft, maxLines = 2, softWrap = true)
        Spacer(Modifier.height(6.dp))
        HintBar("Touchez un pion sous le plateau pour le déplacer du montant du dé.", accent = Ink.Pink)
    }
}
