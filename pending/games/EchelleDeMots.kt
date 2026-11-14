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
import com.botillon.games.ui.Typo

/**
 * Échelle de mots : on change une seule lettre à chaque marche pour rejoindre
 * le mot cible. Chaque mot intermédiaire doit exister dans le dictionnaire.
 */
object EchelleGame {
    val entry = GameEntry(
        id = "echelle",
        title = "Échelle de mots",
        tagline = "Une lettre par marche",
        family = Family.WORDS,
        accent = Ink.Blue,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        Echelle(variant, finish)
    }
}

private data class Ladder(val from: String, val to: String, val par: Int)

private val LADDERS = listOf(
    listOf(Ladder("MAIN", "NAIF", 2), Ladder("CAFE", "PAGE", 2), Ladder("LIRE", "PERE", 2)),
    listOf(Ladder("RANG", "SENS", 3), Ladder("PORT", "TOUR", 3), Ladder("JOIE", "NOIR", 3)),
    listOf(Ladder("PONT", "TOUR", 4), Ladder("NOIR", "PORT", 5), Ladder("JOIE", "TOUR", 5)),
)

private val LADDER_WORDS = """
    AIDE AILE AIRE AMIE ANGE ARME AUBE AUNE AVIS BAIE BANC BASE BEAU BILE BLEU BOIS
    BOIT BOND BORD BOUT BRAS BRUN BRUT CAGE CAFE CAMP CAPE CART CASE CAVE CENT CHAT CHER
    CHIC CHOU CIEL CINQ CIRE CLAN CLOS COIN COUP COUR CRAN DAME DANS DATE DENT DIRE DOIT
    DOME DONC DORE DOUX DRAP ETAT ETRE FACE FAIM FAIT FETE FILS FLAN FOIN FOND FONT
    FORT FOUR FUIR FUME GANT GARE GENE GENS GOUT GRAS GRIS GROS HAUT HIER HOTE IDEE
    JOIE JOLI JOUR JUGE LAIT LAME LARD LENT LIEU LION LIRE LONG LOUP MAIN MAIS MARC
    MARI MARS MERE MERLE MIEL MIEN MIRE MOIS MORT NAIF NAIN NAGE NOIR NOIX NORD NOTE
    NOUS NUIT OBUS ORME OURS PAGE PAIN PAIR PAIX PAPE PARC PARI PART PEAU PERE PERLE
    PEUR PIED PIEU PILE PION PIRE PLAT PONT PORT POUR PRIX PUIS QUAI RANG RATE REIN
    REVE RIDE RIRE RIVE ROBE ROSE ROTI RUSE SAIN SANG SANS SAUT SEIN SEME SENS SEUL
    SITE SOIF SOIN SOIR SONT SORT TANT TARD TIGE TOIT TORT TOUR TOUT TROU UNIR VAIN
    VASE VEAU VELO VEND VENT VERT VIDE VITE VOEU VOIE VOIR VOIT VOTE VOUS ZERO ZONE
""".trimIndent().split(Regex("\\s+")).toSet()

private val LADDER_KEYS = listOf("ABCDEFG", "HIJKLMN", "OPQRSTU", "VWXYZ")

@Composable
private fun Echelle(variant: Int, finish: (Long, Boolean) -> Unit) {
    val ladder = remember(variant) { LADDERS[variant.coerceIn(0, 2)].random() }
    var chain by remember { mutableStateOf(listOf(ladder.from)) }
    var selected by remember { mutableIntStateOf(0) }
    var errors by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("Touchez une lettre, puis choisissez sa remplaçante.") }
    var reported by remember { mutableStateOf(false) }
    val current = chain.last()

    fun replace(letter: Char) {
        if (reported) return
        val candidate = current.toCharArray().also { it[selected] = letter }.concatToString()
        if (candidate == current) return
        val previous = chain.indexOf(candidate)
        if (previous >= 0) {
            chain = chain.take(previous + 1)
            selected = 0
            message = "Retour à $candidate."
            return
        }
        if (candidate !in LADDER_WORDS) {
            errors += 1
            message = "Ce mot n'est pas dans le dictionnaire."
            return
        }
        val next = chain + candidate
        chain = next
        selected = 0
        val moves = next.size - 1
        if (candidate == ladder.to) {
            val score = (200 - (moves - ladder.par) * 15 - errors * 5 + variant * 10).coerceAtLeast(30).toLong()
            reported = true
            finish(score, true)
        } else if (moves > ladder.par + 2) {
            message = "Trop de marches…"
            reported = true
            finish(0L, false)
        } else {
            message = "${ladder.to} en ${ladder.par} marches idéalement."
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pill("${ladder.from} → ${ladder.to}", color = Ink.Blue, background = Ink.Blue.copy(alpha = 0.10f))
            Pill("${chain.size - 1} / ${ladder.par} MARCHES", color = Ink.Soft)
            Pill("ERREURS $errors", color = if (errors > 0) Ink.Coral else Ink.Muted)
        }

        Spacer(Modifier.height(10.dp))
        Panel(Modifier.fillMaxWidth().height(228.dp)) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                chain.forEachIndexed { index, word ->
                    Row(
                        Modifier.fillMaxWidth().height(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppText(
                            "${index + 1}.",
                            style = Typo.label,
                            color = Ink.Muted,
                            modifier = Modifier.size(width = 24.dp, height = 20.dp),
                        )
                        AppText(
                            word,
                            style = Typo.body,
                            color = when {
                                word == ladder.to -> Ink.Mustard
                                index == chain.lastIndex -> Ink.Blue
                                else -> Ink.Soft
                            },
                        )
                        Spacer(Modifier.weight(1f))
                        if (word == ladder.from) AppText("DÉPART", style = Typo.label, color = Ink.Muted)
                        if (word == ladder.to) AppText("ARRIVÉE", style = Typo.label, color = Ink.Mustard)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            current.forEachIndexed { index, letter ->
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (selected == index) Ink.Blue.copy(alpha = 0.12f) else Ink.Card)
                        .border(2.dp, if (selected == index) Ink.Blue else Ink.Line, RoundedCornerShape(10.dp))
                        .clickable { selected = index },
                    contentAlignment = Alignment.Center,
                ) { AppText("$letter", style = Typo.title, color = Ink.Text) }
            }
        }

        Spacer(Modifier.height(8.dp))
        HintBar(message, accent = Ink.Blue)
        Spacer(Modifier.height(8.dp))

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            LADDER_KEYS.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    row.forEach { letter ->
                        Box(
                            Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(8.dp))
                                .background(Ink.Card).border(1.dp, Ink.Line, RoundedCornerShape(8.dp))
                                .clickable { replace(letter) },
                            contentAlignment = Alignment.Center,
                        ) { AppText("$letter", style = Typo.small, color = Ink.Text) }
                    }
                }
            }
        }
    }
}
