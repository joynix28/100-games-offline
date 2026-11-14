package com.botillon.games.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
 * Petit Bac : une lettre imposée, quatre catégories, quatre réponses libres.
 * Chaque réponse correcte rapporte, et le sans-faute offre un bonus.
 */
object PetitBacGame {
    val entry = GameEntry(
        id = "petitbac",
        title = "Petit Bac",
        tagline = "Une lettre, quatre catégories",
        family = Family.WORDS,
        accent = Ink.Green,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        PetitBac(variant, finish)
    }
}

private val PETIT_BAC_LETTERS = listOf(
    listOf("B", "C", "D", "F", "L", "M", "P", "R", "S", "T", "V"),
    listOf("A", "E", "G", "H", "I", "J", "N", "O", "U"),
    "ABCDEFGHIJKLMNOPQRSTUVWXYZ".map { it.toString() },
)

@Composable
private fun PetitBac(variant: Int, finish: (Long, Boolean) -> Unit) {
    val letter = remember(variant) { PETIT_BAC_LETTERS[variant.coerceIn(0, 2)].random() }
    var animal by remember { mutableStateOf("") }
    var ville by remember { mutableStateOf("") }
    var fruit by remember { mutableStateOf("") }
    var metier by remember { mutableStateOf("") }
    var reported by remember { mutableStateOf(false) }
    val answers = listOf(animal, ville, fruit, metier)

    fun keep(value: String): String = value.uppercase().filter { it in 'A'..'Z' }.take(14)

    fun validate() {
        if (reported) return
        val valid = answers.count { answer ->
            val word = answer.trim()
            word.length >= 2 && word.startsWith(letter) && word.all { it in 'A'..'Z' }
        }
        reported = true
        finish((valid * 20L + if (valid == 4) 20L else 0L), valid >= 3)
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Panel(Modifier.width(92.dp).height(88.dp)) {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    AppText("LETTRE", style = Typo.label, color = Ink.Muted)
                    AppText(letter, style = Typo.big, color = Ink.Green)
                }
            }
            Column(Modifier.weight(1f)) {
                Pill("4 CATÉGORIES", color = Ink.Green, background = Ink.Green.copy(alpha = 0.12f))
                Spacer(Modifier.height(8.dp))
                AppText(
                    "Trouvez un mot par catégorie. Tout doit commencer par $letter.",
                    style = Typo.small,
                    color = Ink.Soft,
                    maxLines = 3,
                    softWrap = true,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BacField("ANIMAL", animal, "Un animal en $letter", Ink.Green) { animal = keep(it) }
            BacField("VILLE", ville, "Une ville en $letter", Ink.Soft) { ville = keep(it) }
            BacField("FRUIT", fruit, "Un fruit en $letter", Ink.Green) { fruit = keep(it) }
            BacField("METIER", metier, "Un métier en $letter", Ink.Soft) { metier = keep(it) }
        }

        Spacer(Modifier.height(16.dp))
        HintBar("Au moins 2 lettres, sans accents ni espaces. Le sans-faute vaut 100.", accent = Ink.Green)

        Spacer(Modifier.weight(1f))
        PrimaryButton("VALIDER MES RÉPONSES", { validate() }, Modifier.fillMaxWidth(), color = Ink.Green)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun BacField(
    label: String,
    value: String,
    hint: String,
    accent: Color,
    onChange: (String) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AppText(label, style = Typo.label, color = accent, modifier = Modifier.width(62.dp))
        Box(
            Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(12.dp))
                .background(Ink.CardAlt).border(1.dp, Ink.Line, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = Typo.body.copy(color = Ink.Text),
                singleLine = true,
                cursorBrush = SolidColor(Ink.Green),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            AppText("$hint…", style = Typo.small, color = Ink.Muted)
                        }
                        inner()
                    }
                },
            )
        }
    }
}
