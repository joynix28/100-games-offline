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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.botillon.games.ui.AppText
import com.botillon.games.ui.Family
import com.botillon.games.ui.GameEntry
import com.botillon.games.ui.HintBar
import com.botillon.games.ui.Ink
import com.botillon.games.ui.Panel
import com.botillon.games.ui.Pill
import com.botillon.games.ui.Typo
import kotlin.random.Random

/**
 * Pendu : retrouver un mot français, lettre après lettre, avant six erreurs.
 * Trois listes de difficulté croissante, sans accents pour rester lisible
 * sur le clavier AZERTY.
 */
object HangmanGame {
    val entry = GameEntry(
        id = "pendu",
        title = "Pendu",
        tagline = "Deviner le mot avant la corde",
        family = Family.WORDS,
        accent = Ink.Teal,
        variants = listOf("Courts", "Moyens", "Longs"),
    ) { variant, _, finish, _ ->
        Hangman(variant, finish)
    }
}

private val WORD_LISTS = listOf(
    listOf("CAFE", "LIT", "CHAT", "PAIN", "ROSE", "MER", "TOIT", "MAIN", "LUNE", "TRAIN"),
    listOf("MAISON", "JARDIN", "METRO", "BATEAU", "ETOILE", "ORANGE", "LIVRE", "PLAGE", "PORTE", "SOLEIL"),
    listOf(
        "MONTAGNE", "FENETRE", "CUISINE", "CHAMBRE", "ESCALIER",
        "CHOCOLAT", "TELEPHONE", "PARAPLUIE", "BIBLIOTHEQUE", "AVENTURE",
    ),
)

/** Ordre AZERTY en cinq rangées : quatre de six touches, la dernière de deux. */
private val KEY_ROWS = listOf("AZERTY", "UIOPQS", "DFGHJK", "LMWXCV", "BN")

@Composable
private fun Hangman(variant: Int, finish: (Long, Boolean) -> Unit) {
    val words = WORD_LISTS[variant.coerceIn(0, WORD_LISTS.lastIndex)]
    var word by remember { mutableStateOf(words.random(Random.Default)) }
    var guessed by remember { mutableStateOf<Set<Char>>(emptySet()) }
    var errors by remember { mutableIntStateOf(0) }
    var reported by remember { mutableStateOf(false) }

    val won = word.all { it in guessed }
    val wrongLetters = guessed.filter { it !in word }.sorted()
    val foundLetters = word.toSet().count { it in guessed }
    val uniqueLetters = word.toSet().size

    fun play(letter: Char) {
        if (reported || letter in guessed) return
        val next = guessed + letter
        guessed = next
        if (letter !in word) errors += 1

        if (word.all { it in next }) {
            // Victoire : 50 points, plus 15 par erreur encore disponible.
            reported = true
            finish(50L + (6 - errors) * 15L, true)
        } else if (errors >= 6) {
            // Défaite : 10 points par lettre effectivement révélée dans le mot.
            reported = true
            finish(word.count { it in next } * 10L, false)
        }
    }

    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                AppText("ERREURS", style = Typo.label, color = Ink.Muted)
                AppText(
                    text = "$errors / 6",
                    style = Typo.heading,
                    color = if (errors >= 4) Ink.Coral else Ink.Text,
                )
            }
            Pill(
                text = "$foundLetters / $uniqueLetters LETTRES",
                color = Ink.Teal,
                background = Ink.Teal.copy(alpha = 0.10f),
            )
            Column(horizontalAlignment = Alignment.End) {
                AppText("MOT", style = Typo.label, color = Ink.Muted)
                AppText("${word.length} lettres", style = Typo.heading, color = Ink.Text)
            }
        }

        Spacer(Modifier.height(8.dp))

        Panel(Modifier.fillMaxWidth().height(206.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val gallows = Ink.Soft.copy(alpha = 0.55f)
                val figure = if (won) Ink.Teal else Ink.Coral
                val poleX = w * 0.27f
                val beamY = h * 0.12f
                val hookX = w * 0.57f
                val baseY = h * 0.9f
                val thin = (h * 0.014f).coerceAtLeast(2.5f)
                val thick = (h * 0.024f).coerceAtLeast(4f)

                // Potence.
                drawLine(
                    color = gallows,
                    start = Offset(w * 0.14f, baseY),
                    end = Offset(w * 0.42f, baseY),
                    strokeWidth = thick,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = gallows,
                    start = Offset(poleX, baseY),
                    end = Offset(poleX, beamY),
                    strokeWidth = thick,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = gallows,
                    start = Offset(poleX, beamY),
                    end = Offset(hookX, beamY),
                    strokeWidth = thick,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = gallows,
                    start = Offset(hookX, beamY),
                    end = Offset(hookX, h * 0.2f),
                    strokeWidth = thin,
                    cap = StrokeCap.Round,
                )

                // Bonhomme, une partie par erreur.
                val headRadius = h * 0.075f
                val headY = h * 0.275f
                val bodyTop = headY + headRadius
                val bodyBottom = h * 0.62f

                if (errors >= 1) {
                    drawCircle(
                        color = figure,
                        radius = headRadius,
                        center = Offset(hookX, headY),
                        style = Stroke(width = thin),
                    )
                }
                if (errors >= 2) {
                    drawLine(
                        color = figure,
                        start = Offset(hookX, bodyTop),
                        end = Offset(hookX, bodyBottom),
                        strokeWidth = thin,
                        cap = StrokeCap.Round,
                    )
                }
                if (errors >= 3) {
                    drawLine(
                        color = figure,
                        start = Offset(hookX, h * 0.44f),
                        end = Offset(w * 0.45f, h * 0.55f),
                        strokeWidth = thin,
                        cap = StrokeCap.Round,
                    )
                }
                if (errors >= 4) {
                    drawLine(
                        color = figure,
                        start = Offset(hookX, h * 0.44f),
                        end = Offset(w * 0.69f, h * 0.55f),
                        strokeWidth = thin,
                        cap = StrokeCap.Round,
                    )
                }
                if (errors >= 5) {
                    drawLine(
                        color = figure,
                        start = Offset(hookX, bodyBottom),
                        end = Offset(w * 0.46f, h * 0.8f),
                        strokeWidth = thin,
                        cap = StrokeCap.Round,
                    )
                }
                if (errors >= 6) {
                    drawLine(
                        color = figure,
                        start = Offset(hookX, bodyBottom),
                        end = Offset(w * 0.68f, h * 0.8f),
                        strokeWidth = thin,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            word.forEach { letter ->
                val visible = letter in guessed
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AppText(
                        text = if (visible) letter.toString() else "–",
                        style = Typo.heading.copy(fontSize = if (word.length > 9) 16.sp else 20.sp),
                        color = if (visible) Ink.Text else Ink.Muted,
                        align = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(3.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(
                                color = if (visible) Ink.Teal else Ink.Line,
                                shape = RoundedCornerShape(50),
                            ),
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        AppText(
            text = if (wrongLetters.isEmpty()) {
                "Aucune lettre ratée pour l'instant."
            } else {
                "Ratées : ${wrongLetters.joinToString(" ")}"
            },
            style = Typo.small,
            color = if (wrongLetters.isEmpty()) Ink.Muted else Ink.Coral,
            align = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(10.dp))

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            KEY_ROWS.forEachIndexed { index, row ->
                if (index == KEY_ROWS.lastIndex) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { letter ->
                            LetterKey(
                                letter = letter,
                                state = keyState(letter, word, guessed),
                                enabled = !reported && letter !in guessed,
                                modifier = Modifier.width(62.dp),
                                onClick = { play(letter) },
                            )
                        }
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        row.forEach { letter ->
                            LetterKey(
                                letter = letter,
                                state = keyState(letter, word, guessed),
                                enabled = !reported && letter !in guessed,
                                modifier = Modifier.weight(1f),
                                onClick = { play(letter) },
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        HintBar("Six erreurs maximum.")
    }
}

/** 0 : pas encore jouée, 1 : trouvée, 2 : ratée. */
private fun keyState(letter: Char, word: String, guessed: Set<Char>): Int = when {
    letter !in guessed -> 0
    letter in word -> 1
    else -> 2
}

@Composable
private fun LetterKey(
    letter: Char,
    state: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val fill = when (state) {
        1 -> Ink.Teal.copy(alpha = 0.14f)
        2 -> Ink.Coral.copy(alpha = 0.10f)
        else -> Ink.Card
    }
    val outline = when (state) {
        1 -> Ink.Teal
        2 -> Ink.Coral.copy(alpha = 0.35f)
        else -> Ink.Line
    }
    val textColor = when (state) {
        1 -> Ink.Teal
        2 -> Ink.Muted
        else -> Ink.Text
    }

    Box(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(fill)
            .border(1.dp, outline, RoundedCornerShape(11.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AppText(
            text = "$letter",
            style = Typo.heading.copy(fontSize = 16.sp),
            color = textColor,
        )
    }
}
