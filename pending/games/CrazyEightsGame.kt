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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
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
import com.botillon.games.ui.SecondaryButton
import com.botillon.games.ui.Sound
import com.botillon.games.ui.Typo
import kotlinx.coroutines.delay

/** Crazy Eights : défausser par couleur ou par chiffre, le 8 est un joker. */
object CrazyEightsGame {
    val entry = GameEntry(
        id = "crazy-eights",
        title = "Crazy Eights",
        tagline = "Couleur, chiffre ou huit joker",
        family = Family.CARD,
        accent = Ink.Violet,
        variants = listOf("Facile", "Moyen", "Difficile"),
    ) { variant, _, finish, _ ->
        CrazyEights(variant, finish)
    }
}

private val RANGS_CE = listOf("A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "V", "D", "R")
private val SUITS_CE = listOf("♠", "♥", "♦", "♣")
private fun rangCE(c: Int) = c % 13 + 1
private fun couleurCE(c: Int) = c / 13
private fun estHuitCE(c: Int) = rangCE(c) == 8
private fun nomCE(c: Int) = "${RANGS_CE[rangCE(c) - 1]}${SUITS_CE[couleurCE(c)]}"
private fun teinteCE(c: Int) = if (couleurCE(c) == 1 || couleurCE(c) == 2) Ink.Coral else Ink.Text

@Composable
private fun CarteCE(
    carte: Int?,
    hidden: Boolean,
    modifier: Modifier = Modifier,
    actif: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (hidden) Ink.Violet else Ink.Card)
            .border(
                1.dp,
                when {
                    actif -> Ink.Mustard
                    hidden -> Ink.Violet
                    else -> Ink.Line
                },
                RoundedCornerShape(10.dp),
            )
            .then(if (onClick != null) Modifier.clickable { onClick?.invoke() } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        when {
            hidden -> AppText("8", style = Typo.title, color = Color.White.copy(alpha = 0.5f))
            carte != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AppText(RANGS_CE[carte % 13], style = Typo.heading, color = teinteCE(carte))
                AppText(SUITS_CE[carte / 13], style = Typo.body, color = teinteCE(carte))
            }
        }
    }
}

@Composable
private fun CrazyEights(variant: Int, finish: (Long, Boolean) -> Unit) {
    val sound = LocalSound.current
    val haptics = LocalHaptics.current
    val paquet = remember { (0 until 52).shuffled() }
    val premiere = remember { paquet.drop(10).first { !estHuitCE(it) } }
    var mainJ by remember { mutableStateOf(paquet.take(5)) }
    var mainIa by remember { mutableStateOf(paquet.drop(5).take(5)) }
    var pioche by remember { mutableStateOf(paquet.drop(10).filter { it != premiere }) }
    var defausse by remember { mutableStateOf(listOf(premiere)) }
    var couleur by remember { mutableStateOf(couleurCE(premiere)) }
    var tour by remember { mutableIntStateOf(0) }
    var pendingWild by remember { mutableStateOf(false) }
    var coups by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("À vous : posez une carte ou piochez.") }
    var fini by remember { mutableStateOf(false) }
    var reported by remember { mutableStateOf(false) }

    fun jouable(c: Int) = estHuitCE(c) || rangCE(c) == rangCE(defausse.last()) || couleurCE(c) == couleur

    fun terminer(gagne: Boolean) {
        if (reported) return
        reported = true
        fini = true
        finish(if (gagne) 100L + mainJ.size * 5L else mainJ.size * 5L, gagne)
    }

    fun apresCoup() {
        if (fini) return
        when {
            mainJ.isEmpty() -> terminer(true)
            mainIa.isEmpty() -> terminer(false)
            coups >= 60 -> terminer(mainJ.size < mainIa.size)
            else -> tour = 1
        }
    }

    fun jouerJoueur(carte: Int) {
        if (fini || tour != 0 || pendingWild) return
        if (!jouable(carte)) {
            message = "Cette carte ne correspond ni à la couleur ni au chiffre."
            sound.play(Sound.LOSE)
            haptics.error()
            return
        }
        mainJ = mainJ - carte
        defausse = defausse + carte
        coups++
        if (estHuitCE(carte)) {
            pendingWild = true
            message = "Huit posé : choisissez la couleur demandée."
            sound.play(Sound.MATCH)
            haptics.success()
            return
        }
        couleur = couleurCE(carte)
        message = "Vous posez ${nomCE(carte)}."
        sound.play(Sound.TAP)
        haptics.tap()
        apresCoup()
    }

    fun choisirCouleur(s: Int) {
        if (!pendingWild || fini) return
        pendingWild = false
        couleur = s
        message = "Couleur demandée : ${SUITS_CE[s]}."
        sound.play(Sound.TAP)
        apresCoup()
    }

    fun piocherJoueur() {
        if (fini || tour != 0 || pendingWild) return
        var tas = pioche
        var defausseLoc = defausse
        if (tas.isEmpty()) {
            val recycle = defausseLoc.dropLast(1).shuffled()
            if (recycle.isEmpty()) {
                message = "Blocage : plus rien à piocher."
                terminer(mainJ.size <= mainIa.size)
                return
            }
            tas = recycle
            defausseLoc = listOf(defausseLoc.last())
        }
        val carte = tas.first()
        pioche = tas.drop(1)
        defausse = defausseLoc
        mainJ = mainJ + carte
        coups++
        message = "Vous piochez ${nomCE(carte)}."
        sound.play(Sound.FLIP)
        haptics.tap()
        apresCoup()
    }

    fun iaJouer() {
        if (fini) return
        val sommet = defausse.last()
        val ordinaire = mainIa.firstOrNull {
            !estHuitCE(it) && (rangCE(it) == rangCE(sommet) || couleurCE(it) == couleur)
        }
        val choix = ordinaire ?: mainIa.firstOrNull { estHuitCE(it) }
        if (choix != null) {
            mainIa = mainIa - choix
            defausse = defausse + choix
            coups++
            if (estHuitCE(choix)) {
                val nouvelle = (0..3).maxByOrNull { s -> mainIa.count { couleurCE(it) == s } } ?: 0
                couleur = nouvelle
                message = "L'IA pose un 8 et demande ${SUITS_CE[nouvelle]}."
            } else {
                couleur = couleurCE(choix)
                message = "L'IA pose ${nomCE(choix)}."
            }
            sound.play(Sound.TAP)
        } else {
            val tas = if (pioche.isNotEmpty()) pioche else defausse.dropLast(1).shuffled()
            if (tas.isEmpty()) {
                message = "L'IA passe."
                coups++
            } else {
                if (pioche.isEmpty()) defausse = listOf(defausse.last())
                pioche = tas.drop(1)
                mainIa = mainIa + tas.first()
                coups++
                message = "L'IA pioche."
            }
        }
        when {
            mainIa.isEmpty() -> terminer(false)
            mainJ.isEmpty() -> terminer(true)
            coups >= 60 -> terminer(mainJ.size < mainIa.size)
            else -> tour = 0
        }
    }

    LaunchedEffect(tour, fini, pendingWild) {
        if (tour == 1 && !fini && !pendingWild) {
            delay(if (variant == 2) 450L else 700L)
            iaJouer()
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Vous ${mainJ.size}", color = Ink.Violet, background = Ink.Violet.copy(alpha = 0.12f))
            Pill("IA ${mainIa.size}", color = Ink.Blue, background = Ink.Blue.copy(alpha = 0.12f))
            Pill("Pioche ${pioche.size}")
        }
        Spacer(Modifier.height(12.dp))
        Panel(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AppText("IA", style = Typo.label, color = Ink.Blue)
                    Spacer(Modifier.height(6.dp))
                    CarteCE(null, true, Modifier.size(width = 56.dp, height = 78.dp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AppText("DÉFAUSSE", style = Typo.label, color = Ink.Muted)
                    Spacer(Modifier.height(6.dp))
                    CarteCE(defausse.last(), false, Modifier.size(width = 74.dp, height = 100.dp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AppText("PIOCHE", style = Typo.label, color = Ink.Muted)
                    Spacer(Modifier.height(6.dp))
                    CarteCE(null, true, Modifier.size(width = 56.dp, height = 78.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (pendingWild) {
            AppText("Choisissez une couleur :", style = Typo.small, color = Ink.Soft)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SUITS_CE.forEachIndexed { s, symbole ->
                    Box(
                        Modifier
                            .size(width = 58.dp, height = 38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Ink.CardAlt)
                            .border(1.dp, Ink.Line, RoundedCornerShape(10.dp))
                            .clickable { choisirCouleur(s) },
                        contentAlignment = Alignment.Center,
                    ) {
                        AppText(symbole, style = Typo.heading, color = if (s == 1 || s == 2) Ink.Coral else Ink.Text)
                    }
                }
            }
        } else {
            AppText("Couleur en jeu : ${SUITS_CE[couleur]}", style = Typo.small, color = Ink.Soft)
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            mainJ.forEach { carte ->
                CarteCE(
                    carte = carte,
                    hidden = false,
                    modifier = Modifier.weight(1f).height(74.dp),
                    actif = tour == 0 && !pendingWild && jouable(carte),
                    onClick = { jouerJoueur(carte) },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        AppText(
            message,
            style = Typo.body,
            color = Ink.Soft,
            align = TextAlign.Center,
            maxLines = 2,
            softWrap = true,
        )
        Spacer(Modifier.weight(1f))
        SecondaryButton(
            label = "Piocher une carte",
            onClick = { piocherJoueur() },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        HintBar("Le 8 est un joker : il autorise n'importe quelle couleur.")
    }
}
