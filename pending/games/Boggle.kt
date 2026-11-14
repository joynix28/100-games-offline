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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.botillon.games.ui.SecondaryButton
import com.botillon.games.ui.Typo
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Boggle : une grille de 4 × 4, des lettres adjacentes à relier (diagonales
 * comprises) pour former des mots français. Soixante secondes, dictionnaire
 * embarqué, pas de répétition.
 */
object BoggleGame {
    val entry = GameEntry(
        id = "boggle",
        title = "Boggle",
        tagline = "Relier les lettres, trouver les mots",
        family = Family.WORDS,
        accent = Ink.Pink,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        Boggle(variant, finish)
    }
}

private val BOGGLE_GRIDS = listOf(
    listOf("TABL", "ERIE", "MONS", "CARE"),
    listOf("MERE", "SONT", "LAPI", "CARE"),
    listOf("LAMI", "SORE", "TABE", "NIVE"),
)

private val BOGGLE_WORDS = """
    AIDE AILE AIRE AMIE ANGE ANSE ARME AUBE AUNE AVIS BAIE BANC BASE BATEAU BEAU
    BILE BLEU BOIS BOIT BOND BORD BOUT BRAS BRUN BRUT CAGE CAMP CAPE CART CASE
    CAVE CENT CERF CHAT CHER CHIC CHOU CIEL CINQ CLAN CLOS COIN COUP COUR CRAN
    DAME DANS DATE DENT DIEU DIRE DOIT DOME DONC DORE DOUX DRAP ECHO ECRU ETAT
    ETRE ETUI FACE FAIM FAIT FARD FETE FILS FLAN FOIN FOND FONT FORT FOUR FUIR
    FUME GANT GARE GENE GENS GOUT GRAS GRIS GROS HAUT HIER HOTE IDEE JOLI JOUR
    JUGE LAIT LAME LARD LENT LIEU LION LIRE LONG LOUP MAIS MARC MARI MARS MERE
    MERLE MIEL MIEN MIRE MOIS MORT NAIF NAIN NAGE NOIR NOIX NORD NOTE NOUS NUIT
    OBUS ORME OURS PAGE PAIN PAIR PAIX PAPE PARC PARI PART PEAU PERE PERLE PEUR
    PIED PIEU PILE PION PIRE PLAT PONT PORT POUR PRIX PUIS QUAI RANG RATE REIN
    REVE RICHE RIDE RIRE RIVE ROBE ROSE ROTI RUSE SAIN SANG SANS SAUT SEIN SEME
    SENS SEUL SITE SOIF SOIN SOIR SONT SORT SUIS TANT TARD TIGE TOIT TORT TOUR
    TOUT TROU UNIR VAIN VASE VEAU VELO VEND VENT VERT VIDE VITE VOEU VOIE VOIR
    VOIT VOTE VOUS YEUX ZERO ZONE ESPOIR JARDIN MAISON SOLEIL PLAGE FLEUR ARBRE
    TABLE CHAISE PORTE FENETRE MUSIQUE ECHELLE AVENTURE TELEPHONE CHOCOLAT
    ESCALIER PARAPLUIE ORANGE CITRON POMME POIRE PRUNE RAISIN CERISE BANANE
    SALADE TOMATE CAROTTE FROMAGE BEURRE GATEAU BRIOCHE CROISSANT BAGUETTE
""".trimIndent().split(Regex("\\s+")).toSet()

/** Tous les mots du dictionnaire formables sur la grille, sans réutiliser de case. */
private fun boggleSolutions(grid: List<Char>): Set<String> {
    val found = mutableSetOf<String>()
    val used = BooleanArray(16)
    fun walk(index: Int, word: String) {
        if (word.length >= 3 && word in BOGGLE_WORDS) found += word
        if (word.length >= 8) return
        val row = index / 4
        val col = index % 4
        for (dr in -1..1) {
            for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val nr = row + dr
                val nc = col + dc
                if (nr !in 0..3 || nc !in 0..3) continue
                val next = nr * 4 + nc
                if (used[next]) continue
                used[next] = true
                walk(next, word + grid[next])
                used[next] = false
            }
        }
    }
    for (index in 0 until 16) {
        used[index] = true
        walk(index, grid[index].toString())
        used[index] = false
    }
    return found
}

@Composable
private fun Boggle(variant: Int, finish: (Long, Boolean) -> Unit) {
    val grid = remember(variant) { BOGGLE_GRIDS[variant.coerceIn(0, 2)].flatMap { it.toList() } }
    val possible = remember(variant) { boggleSolutions(grid) }
    var path by remember { mutableStateOf<List<Int>>(emptyList()) }
    var found by remember { mutableStateOf<Set<String>>(emptySet()) }
    var score by remember { mutableStateOf(0L) }
    var left by remember { mutableIntStateOf(60) }
    var message by remember { mutableStateOf("Reliez des lettres voisines, puis validez.") }
    var reported by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (left > 0) {
            delay(1000)
            left -= 1
        }
        if (!reported) {
            reported = true
            finish(score, score > 0)
        }
    }

    fun tap(index: Int) {
        if (reported) return
        if (index in path) {
            path = emptyList()
            return
        }
        val last = path.lastOrNull()
        if (last != null) {
            val distance = maxOf(abs(index / 4 - last / 4), abs(index % 4 - last % 4))
            if (distance != 1) {
                message = "Cette lettre ne touche pas la précédente."
                return
            }
        }
        path = path + index
    }

    fun submit() {
        if (reported || path.isEmpty()) return
        val word = path.joinToString("") { grid[it].toString() }
        path = emptyList()
        when {
            word.length < 4 -> message = "Quatre lettres minimum."
            word in found -> message = "Déjà trouvé : $word."
            word !in BOGGLE_WORDS -> message = "$word n'est pas dans le dictionnaire."
            else -> {
                found = found + word
                score += word.length * 5L
                message = "+${word.length * 5} points · $word"
                if (found.size == possible.size) {
                    reported = true
                    finish(score + 50L, true)
                }
            }
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pill(
                "TEMPS $left s",
                color = if (left <= 15) Ink.Coral else Ink.Pink,
                background = Ink.Pink.copy(alpha = 0.10f),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AppText("SCORE", style = Typo.label, color = Ink.Muted)
                AppText("$score", style = Typo.heading, color = Ink.Text)
            }
            Pill("MOTS ${found.size} / ${possible.size}", color = Ink.Soft)
        }

        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (row in 0 until 4) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (col in 0 until 4) {
                        val index = row * 4 + col
                        val inPath = index in path
                        Box(
                            Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (inPath) Ink.Pink else Ink.Card)
                                .border(
                                    1.dp,
                                    if (inPath) Ink.Pink else Ink.Line,
                                    RoundedCornerShape(12.dp),
                                )
                                .clickable { tap(index) },
                            contentAlignment = Alignment.Center,
                        ) {
                            AppText(
                                "${grid[index]}",
                                style = Typo.title,
                                color = if (inPath) Ink.Card else Ink.Text,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Panel(Modifier.fillMaxWidth().height(46.dp)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AppText(
                    if (path.isEmpty()) "…" else path.joinToString("") { grid[it].toString() },
                    style = Typo.heading,
                    color = if (path.isEmpty()) Ink.Muted else Ink.Pink,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("EFFACER", { path = emptyList() }, Modifier.weight(1f))
            PrimaryButton("VALIDER", { submit() }, Modifier.weight(1f), color = Ink.Pink)
        }

        Spacer(Modifier.height(8.dp))
        HintBar(message, accent = Ink.Pink)
        Spacer(Modifier.height(6.dp))
        AppText(
            "${possible.size} mots possibles sur cette grille",
            style = Typo.small,
            color = Ink.Muted,
        )
    }
}
