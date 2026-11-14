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
import androidx.compose.ui.unit.dp
import com.botillon.games.ui.AppText
import com.botillon.games.ui.Family
import com.botillon.games.ui.GameEntry
import com.botillon.games.ui.HintBar
import com.botillon.games.ui.Ink
import com.botillon.games.ui.Panel
import com.botillon.games.ui.Pill
import com.botillon.games.ui.PrimaryButton
import com.botillon.games.ui.Typo

/**
 * Cryptogramme : une phrase est chiffrée par substitution alphabétique. On
 * touche une lettre chiffrée puis la lettre claire correspondante, et on
 * vérifie. Trois essais, quelques lettres offertes au départ.
 */
object CryptogrammeGame {
    val entry = GameEntry(
        id = "cryptogramme",
        title = "Cryptogramme",
        tagline = "Décoder la phrase secrète",
        family = Family.WORDS,
        accent = Ink.Coral,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        Cryptogramme(variant, finish)
    }
}

private data class Crypto(val phrase: String, val hints: Int)

private val CRYPTOS = listOf(
    Crypto("LA NUIT PORTE CONSEIL", 3),
    Crypto("PETIT A PETIT L OISEAU FAIT SON NID", 2),
    Crypto("QUI VA LENTEMENT VA SUREMENT", 1),
)

private val CRYPTO_KEYS = listOf("ABCDEFG", "HIJKLMN", "OPQRSTU", "VWXYZ")

@Composable
private fun Cryptogramme(variant: Int, finish: (Long, Boolean) -> Unit) {
    val crypto = remember(variant) { CRYPTOS[variant.coerceIn(0, 2)] }
    val phrase = crypto.phrase
    val cipher = remember {
        val alphabet = ('A'..'Z').toList()
        var shuffled: List<Char>
        do {
            shuffled = alphabet.shuffled()
        } while (shuffled.indices.any { alphabet[it] == shuffled[it] })
        alphabet.zip(shuffled).toMap()
    }
    val initial = remember {
        val shown = phrase.filter { it != ' ' }.toSet().shuffled().take(crypto.hints)
        shown.associate { cipher[it]!! to it }
    }
    val lines = remember {
        val packed = mutableListOf<MutableList<String>>()
        var width = 0
        phrase.split(" ").forEach { word ->
            if (packed.isEmpty() || width + word.length + 1 > 15) {
                packed.add(mutableListOf(word))
                width = word.length
            } else {
                packed.last().add(word)
                width += word.length + 1
            }
        }
        packed.map { it.toList() }
    }
    var mapping by remember { mutableStateOf(initial) }
    var selected by remember { mutableStateOf<Char?>(null) }
    var lives by remember { mutableIntStateOf(3) }
    var message by remember { mutableStateOf("Touchez une lettre, puis sa correspondance.") }
    var reported by remember { mutableStateOf(false) }

    fun assign(letter: Char) {
        val target = selected ?: return
        if (reported) return
        val kept = mapping.filterValues { it != letter }
        mapping = kept + (target to letter)
        val cipherLetters = phrase.filter { it != ' ' }.map { cipher[it]!! }.distinct()
        selected = cipherLetters.firstOrNull { it !in mapping }
        message = "Lettre $letter placée."
    }

    fun verify() {
        if (reported) return
        val good = phrase.all { it == ' ' || mapping[cipher[it]] == it }
        if (good) {
            reported = true
            finish(120L + lives * 25L + variant * 10L, true)
        } else {
            lives -= 1
            if (lives <= 0) {
                reported = true
                val right = phrase.count { it != ' ' && mapping[cipher[it]] == it }
                finish(right * 3L, false)
            } else {
                message = "Pas encore : il reste $lives essai${if (lives > 1) "s" else ""}."
            }
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pill("${phrase.count { it != ' ' }} LETTRES", color = Ink.Coral, background = Ink.Coral.copy(alpha = 0.10f))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) { index ->
                    Box(
                        Modifier.size(11.dp).clip(RoundedCornerShape(50))
                            .background(if (index < lives) Ink.Coral else Ink.Line),
                    )
                }
            }
            Pill("${crypto.hints} INDICE${if (crypto.hints > 1) "S" else ""}", color = Ink.Mustard)
        }

        Spacer(Modifier.height(10.dp))
        Panel(Modifier.fillMaxWidth().height(172.dp)) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                lines.forEach { line ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        line.forEach { word ->
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                word.forEach { letter ->
                                    val crypted = cipher[letter]!!
                                    val guess = mapping[crypted]
                                    val isSelected = selected == crypted
                                    Box(
                                        Modifier.size(width = 21.dp, height = 40.dp)
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(if (isSelected) Ink.Coral.copy(alpha = 0.16f) else Ink.CardAlt)
                                            .border(
                                                if (isSelected) 2.dp else 1.dp,
                                                if (isSelected) Ink.Coral else Ink.Line,
                                                RoundedCornerShape(5.dp),
                                            )
                                            .clickable { selected = crypted },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            AppText("$crypted", style = Typo.label, color = Ink.Muted)
                                            AppText(
                                                guess?.toString() ?: "·",
                                                style = Typo.body,
                                                color = if (guess == letter) Ink.Teal else Ink.Text,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        HintBar(message, accent = Ink.Coral)
        Spacer(Modifier.height(8.dp))

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            CRYPTO_KEYS.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    row.forEach { letter ->
                        Box(
                            Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(8.dp))
                                .background(Ink.Card).border(1.dp, Ink.Line, RoundedCornerShape(8.dp))
                                .clickable { assign(letter) },
                            contentAlignment = Alignment.Center,
                        ) { AppText("$letter", style = Typo.small, color = Ink.Text) }
                    }
                    if (row == CRYPTO_KEYS.last()) {
                        Box(
                            Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(8.dp))
                                .background(Ink.CardAlt).clickable {
                                    selected?.let { mapping = mapping - it }
                                },
                            contentAlignment = Alignment.Center,
                        ) { AppText("⌫", style = Typo.small, color = Ink.Soft) }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        PrimaryButton("VÉRIFIER LA PHRASE", { verify() }, Modifier.fillMaxWidth(), color = Ink.Coral)
    }
}
