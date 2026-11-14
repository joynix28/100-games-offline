package com.botillon.games.games

import androidx.compose.foundation.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.botillon.games.ui.*
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Intrus : quatre propositions, une seule ne respecte pas la règle commune. */
object IntruderGame {
    val entry = GameEntry(
        id = "intrus", title = "L'Intrus", tagline = "Trouvez ce qui détonne",
        family = Family.LOGIC, accent = Ink.Mustard, variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ -> Intruder(variant, finish) }
}

private data class Riddle(val items: List<String>, val odd: Int, val rule: String)

private val WORDS = listOf(
    Riddle(listOf("Chien", "Chat", "Renard", "Table"), 3, "Table n'est pas un animal."),
    Riddle(listOf("Paris", "Lyon", "Nice", "Genève"), 3, "Genève n'est pas en France."),
    Riddle(listOf("Rouge", "Bleu", "Vert", "Rond"), 3, "Rond n'est pas une couleur."),
    Riddle(listOf("Pomme", "Poire", "Carotte", "Cerise"), 2, "La carotte n'est pas un fruit."),
    Riddle(listOf("Lundi", "Mardi", "Juillet", "Jeudi"), 2, "Juillet n'est pas un jour de la semaine."),
    Riddle(listOf("Piano", "Guitare", "Violon", "Peinture"), 3, "La peinture n'est pas un instrument."),
    Riddle(listOf("Deux", "Quatre", "Six", "Neuf"), 3, "Neuf n'est pas un nombre pair."),
    Riddle(listOf("Été", "Hiver", "Automne", "Nord"), 3, "Le nord n'est pas une saison."),
    Riddle(listOf("Paris", "Londres", "Tokyo", "France"), 3, "La France n'est pas une ville."),
    Riddle(listOf("Manteau", "Écharpe", "Bonnet", "Sandale"), 3, "La sandale n'est pas un vêtement d'hiver."),
    Riddle(listOf("Cuillère", "Fourchette", "Couteau", "Assiette"), 3, "L'assiette n'est pas un couvert."),
)

private fun numericRiddle(variant: Int): Riddle {
    val kind = Random.nextInt(0, if (variant == 2) 4 else if (variant == 1) 3 else 2)
    val good = mutableSetOf<Int>()
    val rule: String
    var bad = 0
    when (kind) {
        0 -> {
            val step = Random.nextInt(3, 10)
            while (good.size < 3) good += step * Random.nextInt(2, 13)
            do { bad = Random.nextInt(5, 90) } while (bad % step == 0 || bad in good)
            rule = "Tous les autres sont des multiples de $step."
        }

        1 -> {
            while (good.size < 3) {
                val root = Random.nextInt(2, 10)
                good += root * root
            }
            do { bad = Random.nextInt(3, 90) } while (bad in good || (2..10).any { it * it == bad })
            rule = "Tous les autres sont des carrés parfaits."
        }

        2 -> {
            while (good.size < 3) good += Random.nextInt(2, 41) * 2
            do { bad = Random.nextInt(3, 80) } while (bad % 2 == 0)
            rule = "Tous les autres sont des nombres pairs."
        }

        else -> {
            val primes = listOf(2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37)
            while (good.size < 3) good += primes.random()
            do { bad = Random.nextInt(4, 40) } while (bad in primes)
            rule = "Tous les autres sont des nombres premiers."
        }
    }
    val items = (good + bad).shuffled()
    return Riddle(items.map { "$it" }, items.indexOf(bad), rule)
}

@Composable
private fun Intruder(variant: Int, finish: (Long, Boolean) -> Unit) {
    val riddles = remember { (WORDS.shuffled().take(4) + List(4) { numericRiddle(variant) }).shuffled() }
    var round by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(3) }
    var score by remember { mutableStateOf(0L) }
    var correct by remember { mutableIntStateOf(0) }
    var chosen by remember { mutableStateOf<Int?>(null) }
    var reported by remember { mutableStateOf(false) }
    val riddle = riddles[round]

    fun report(won: Boolean) {
        if (reported) return
        reported = true
        finish(score, won)
    }

    fun answer(index: Int) {
        if (reported || chosen != null) return
        chosen = index
        if (index == riddle.odd) { score += (15 + variant * 5).toLong(); correct++ } else lives--
    }

    LaunchedEffect(chosen) {
        if (chosen == null) return@LaunchedEffect
        delay(1000)
        if (lives <= 0) report(false)
        else if (round == riddles.lastIndex) report(correct >= 6)
        else { round += 1; chosen = null }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            AppText("ÉNIGME ${round + 1} / 8", style = Typo.label, color = Ink.Muted)
            Pill("VIES $lives", color = Ink.Soft, background = Ink.Mustard.copy(alpha = 0.20f))
            AppText("$score PTS", style = Typo.heading, color = Ink.Text)
        }
        Spacer(Modifier.height(12.dp))
        AppText("LEQUEL NE SUIT PAS LA RÈGLE ?", style = Typo.label, color = Ink.Soft)
        Spacer(Modifier.height(10.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(2) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(2) { col ->
                        val index = row * 2 + col
                        val isOdd = index == riddle.odd
                        val revealed = chosen != null && isOdd
                        val missed = chosen == index && !isOdd
                        Box(
                            Modifier.weight(1f).height(82.dp).clip(RoundedCornerShape(17.dp))
                                .background(if (revealed) Ink.Teal else if (missed) Ink.Coral else Ink.Card)
                                .border(
                                    1.dp,
                                    if (revealed) Ink.Teal else if (missed) Ink.Coral else Ink.Line,
                                    RoundedCornerShape(17.dp),
                                )
                                .clickable(enabled = chosen == null && !reported) { answer(index) }
                                .padding(8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            AppText(
                                riddle.items[index],
                                style = Typo.heading.copy(fontSize = 15.sp),
                                color = if (revealed || missed) Color.White else Ink.Text,
                                align = TextAlign.Center,
                                maxLines = 2,
                                softWrap = true,
                            )
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(54.dp), contentAlignment = Alignment.Center) {
            AppText(
                text = if (chosen == null) "Une proposition détonne : à vous de la repérer." else riddle.rule,
                style = Typo.small,
                color = if (chosen == null) Ink.Muted else if (chosen == riddle.odd) Ink.Teal else Ink.Coral,
                align = TextAlign.Center,
                maxLines = 2,
                softWrap = true,
            )
        }
        Spacer(Modifier.weight(1f))
        HintBar("Huit énigmes, trois vies. La règle peut porter sur le sens, la parité ou les maths.")
    }
}
