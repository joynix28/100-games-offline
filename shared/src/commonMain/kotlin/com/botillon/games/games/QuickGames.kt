package com.botillon.games.games

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.botillon.games.ui.AppText
import com.botillon.games.ui.Family
import com.botillon.games.ui.GameEntry
import com.botillon.games.ui.HintBar
import com.botillon.games.ui.Ink
import com.botillon.games.ui.LocalHaptics
import com.botillon.games.ui.LocalSound
import com.botillon.games.ui.Panel
import com.botillon.games.ui.PrimaryButton
import com.botillon.games.ui.Sound
import com.botillon.games.ui.Typo
import kotlin.random.Random

/** Pierre-feuille-ciseaux en trois manches gagnantes. */
object RpsGame {
    val entry = GameEntry(
        id = "rps",
        title = "Pierre-feuille-ciseaux",
        tagline = "Le classique, en trois manches",
        family = Family.CARD,
        accent = Ink.Pink,
        variants = listOf("2 manches", "3 manches", "5 manches"),
    ) { variant, _, finish, _ ->
        Rps(variant, finish)
    }
}

private val MOVES = listOf("Pierre", "Feuille", "Ciseaux")
private val GLYPHS = listOf("✊", "✋", "✌")

@Composable
private fun Rps(variant: Int, finish: (Long, Boolean) -> Unit) {
    val target = listOf(2, 3, 5)[variant]
    val sound = LocalSound.current
    val haptics = LocalHaptics.current
    var player by remember { mutableIntStateOf(0) }
    var ai by remember { mutableIntStateOf(0) }
    var lastPlayer by remember { mutableStateOf<Int?>(null) }
    var lastAi by remember { mutableStateOf<Int?>(null) }
    var message by remember { mutableStateOf("À vous de jouer.") }
    var done by remember { mutableStateOf(false) }

    fun play(move: Int) {
        if (done) return
        val other = Random.nextInt(3)
        lastPlayer = move
        lastAi = other
        val outcome = when {
            move == other -> 0
            (move + 1) % 3 == other -> -1
            else -> 1
        }
        when (outcome) {
            1 -> {
                player++
                sound.play(Sound.WIN)
                message = "Gagné !"
            }

            -1 -> {
                ai++
                sound.play(Sound.LOSE)
                message = "Perdu."
            }

            else -> {
                sound.play(Sound.TAP)
                message = "Égalité."
            }
        }
        haptics.tap()
        if (player >= target || ai >= target) {
            done = true
            finish(player * 100L, player >= target)
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            AppText("Vous $player", style = Typo.heading, color = Ink.Coral)
            AppText("IA $ai", style = Typo.heading, color = Ink.Blue)
        }
        AppText("Premier à $target", style = Typo.small, color = Ink.Muted)
        Spacer(Modifier.height(16.dp))

        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AppText(lastPlayer?.let { GLYPHS[it] } ?: "?", style = Typo.display.copy(fontSize = 44.sp), color = Ink.Coral)
                        AppText("vous", style = Typo.small, color = Ink.Muted)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AppText(lastAi?.let { GLYPHS[it] } ?: "?", style = Typo.display.copy(fontSize = 44.sp), color = Ink.Blue)
                        AppText("ordinateur", style = Typo.small, color = Ink.Muted)
                    }
                }
                Spacer(Modifier.height(10.dp))
                AppText(message, style = Typo.body, color = Ink.Soft)
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MOVES.forEachIndexed { index, label ->
                PrimaryButton(
                    label = GLYPHS[index],
                    onClick = { play(index) },
                    modifier = Modifier.size(88.dp),
                    color = Ink.Accents[index],
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MOVES.forEach { label ->
                AppText(label, style = Typo.label, color = Ink.Muted)
                Spacer(Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(18.dp))
        HintBar("Pierre bat ciseaux, ciseaux bat feuille, feuille bat pierre.")
    }
}

/** Chiffre mystère : retrouver un nombre avec « plus grand / plus petit ». */
object GuessGame {
    val entry = GameEntry(
        id = "chiffre",
        title = "Chiffre mystère",
        tagline = "Plus grand ? Plus petit ?",
        family = Family.WORDS,
        accent = Ink.Violet,
        variants = listOf("1 à 50", "1 à 100", "1 à 500"),
    ) { variant, _, finish, _ ->
        Guess(variant, finish)
    }
}

@Composable
private fun Guess(variant: Int, finish: (Long, Boolean) -> Unit) {
    val max = listOf(50, 100, 500)[variant]
    val sound = LocalSound.current
    val target = remember { Random.nextInt(1, max + 1) }
    var entry by remember { mutableStateOf("") }
    var attempts by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("Devinez un nombre entre 1 et $max.") }
    var done by remember { mutableStateOf(false) }

    fun submit() {
        val value = entry.toIntOrNull() ?: return
        if (value !in 1..max) {
            message = "Entre 1 et $max."
            return
        }
        attempts++
        entry = ""
        when {
            value == target -> {
                done = true
                message = "Trouvé en $attempts essais !"
                sound.play(Sound.WIN)
                finish((max * 4 - attempts * 12).coerceAtLeast(50).toLong(), true)
            }

            value < target -> {
                message = "C'est plus grand."
                sound.play(Sound.TAP)
            }

            else -> {
                message = "C'est plus petit."
                sound.play(Sound.TAP)
            }
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        AppText("Essais : $attempts", style = Typo.heading, color = Ink.Text)
        Spacer(Modifier.height(10.dp))
        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AppText(message, style = Typo.body, color = Ink.Soft, maxLines = 2, softWrap = true)
                Spacer(Modifier.height(12.dp))
                AppText(
                    entry.ifEmpty { "—" },
                    style = Typo.display.copy(fontSize = 40.sp),
                    color = Ink.Violet,
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", "OK"))
                .forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { key ->
                            KeypadKey(key) {
                                when (key) {
                                    "⌫" -> entry = entry.dropLast(1)
                                    "OK" -> submit()
                                    else -> if (entry.length < 4 && !done) entry += key
                                }
                            }
                        }
                    }
                }
        }
        Spacer(Modifier.height(12.dp))
        HintBar("Le nombre change à chaque partie. Moins d'essais = plus de points.")
    }
}

@Composable
private fun KeypadKey(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 78.dp, height = 54.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Ink.Card)
            .border(1.dp, Ink.Line, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AppText(label, style = Typo.heading, color = Ink.Text)
    }
}

/** Allumettes : retirer 1 à 3 allumettes, celui qui prend la dernière perd. */
object NimGame {
    val entry = GameEntry(
        id = "allumettes",
        title = "Allumettes",
        tagline = "Ne prenez pas la dernière",
        family = Family.LOGIC,
        accent = Ink.Coral,
        variants = listOf("11 allumettes", "15 allumettes", "21 allumettes"),
    ) { variant, _, finish, _ ->
        Nim(variant, finish)
    }
}

@Composable
private fun Nim(variant: Int, finish: (Long, Boolean) -> Unit) {
    val start = listOf(11, 15, 21)[variant]
    val sound = LocalSound.current
    var left by remember { mutableIntStateOf(start) }
    var message by remember { mutableStateOf("À vous. Retirez 1 à 3 allumettes.") }
    var done by remember { mutableStateOf(false) }

    fun aiTurn(remaining: Int): Int {
        // Stratégie gagnante : laisser un multiple de 4 (jeu de la misère).
        val take = remaining % 4
        return if (take in 1..3) take else Random.nextInt(1, 4)
    }

    fun take(amount: Int) {
        if (done || amount > left) return
        left -= amount
        sound.play(Sound.TAP)
        if (left == 0) {
            done = true
            message = "Vous avez pris la dernière…"
            sound.play(Sound.LOSE)
            finish(0, false)
            return
        }
        val ai = aiTurn(left)
        left -= ai
        message = "L'ordinateur prend $ai allumette${if (ai > 1) "s" else ""}."
        if (left == 0) {
            done = true
            message = "L'ordinateur a pris la dernière. Gagné !"
            sound.play(Sound.WIN)
            finish(100, true)
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        AppText("$left allumettes", style = Typo.heading, color = Ink.Text)
        Spacer(Modifier.height(14.dp))
        Panel(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Allumettes dessinées en petites barres.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat((left + 6) / 7) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            repeat(7) { col ->
                                val index = row * 7 + col
                                if (index < left) {
                                    Box(
                                        Modifier
                                            .size(width = 9.dp, height = 42.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Ink.Mustard),
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                AppText(message, style = Typo.small, color = Ink.Soft, maxLines = 2, softWrap = true)
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            (1..3).forEach { amount ->
                PrimaryButton(
                    label = "$amount",
                    onClick = { take(amount) },
                    modifier = Modifier.size(72.dp),
                    color = Ink.Coral,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        HintBar("Celui qui prend la dernière allumette perd. L'ordinateur connaît la stratégie.")
    }
}
