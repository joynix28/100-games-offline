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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
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
import com.botillon.games.ui.SecondaryButton
import com.botillon.games.ui.Sound
import com.botillon.games.ui.Typo
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Blackjack : approcher 21 sans dépasser, face au croupier.
 *
 * Mise fixe de 10 jetons. Le joueur tire ou reste, le croupier révèle sa carte
 * cachée puis tire jusqu'à 17. Un blackjack naturel est payé 3:2. La partie
 * s'arrête à 0 jeton (ou moins de 10) et se gagne à 100 jetons.
 */
object BlackjackGame {
    val entry = GameEntry(
        id = "blackjack",
        title = "Blackjack",
        tagline = "Approcher 21 sans dépasser",
        family = Family.CARD,
        accent = Ink.Mustard,
        variants = listOf("10 jetons", "25 jetons", "50 jetons"),
    ) { variant, _, finish, _ ->
        Blackjack(variant, finish)
    }
}

private const val BJO_READY = 0
private const val BJO_PLAYER = 1
private const val BJO_DEALER = 2
private const val BJO_DONE = 3
private const val BJO_BET = 10

private val BJO_SUITS = listOf("♥", "♦", "♣", "♠")
private val BJO_RANKS = listOf("A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "V", "D", "R")

private data class BjoCard(val rank: Int, val suit: Int) {
    val red: Boolean get() = suit == 0 || suit == 1
    val label: String get() = BJO_RANKS[rank - 1]
    val symbol: String get() = BJO_SUITS[suit]
}

private fun bjoDeck(): List<BjoCard> {
    val cards = mutableListOf<BjoCard>()
    for (suit in 0 until 4) for (rank in 1..13) cards.add(BjoCard(rank, suit))
    return cards.shuffled(Random.Default)
}

/** Total d'une main ; les As passent de 11 à 1 si la main dépasse 21. */
private fun bjoValue(cards: List<BjoCard>): Int {
    var total = 0
    var aces = 0
    for (card in cards) {
        when {
            card.rank == 1 -> {
                total += 11
                aces++
            }

            card.rank >= 10 -> total += 10
            else -> total += card.rank
        }
    }
    while (total > 21 && aces > 0) {
        total -= 10
        aces--
    }
    return total
}

@Composable
private fun Blackjack(variant: Int, finish: (Long, Boolean) -> Unit) {
    val sound = LocalSound.current
    val haptics = LocalHaptics.current
    val startChips = listOf(10, 25, 50)[variant]

    var chips by remember { mutableIntStateOf(startChips) }
    var deck by remember { mutableStateOf(bjoDeck()) }
    var player by remember { mutableStateOf<List<BjoCard>>(emptyList()) }
    var dealer by remember { mutableStateOf<List<BjoCard>>(emptyList()) }
    var phase by remember { mutableIntStateOf(BJO_READY) }
    var message by remember { mutableStateOf("Mise fixe : 10 jetons. Touchez DISTRIBUER.") }
    var round by remember { mutableIntStateOf(0) }
    var reported by remember { mutableStateOf(false) }

    fun report(score: Long, won: Boolean) {
        if (reported) return
        reported = true
        if (won) {
            sound.play(Sound.WIN)
            haptics.success()
        } else {
            sound.play(Sound.LOSE)
            haptics.error()
        }
        finish(score, won)
    }

    fun drawCard(): BjoCard {
        if (deck.isEmpty()) deck = bjoDeck()
        val card = deck.last()
        deck = deck.dropLast(1)
        return card
    }

    fun settle() {
        val playerTotal = bjoValue(player)
        val dealerTotal = bjoValue(dealer)
        val natural = player.size == 2 && playerTotal == 21
        val dealerNatural = dealer.size == 2 && dealerTotal == 21

        val delta: Int
        val text: String
        when {
            natural && dealerNatural -> {
                delta = BJO_BET
                text = "Blackjack des deux côtés : égalité."
            }

            natural -> {
                delta = BJO_BET + BJO_BET * 3 / 2
                text = "Blackjack naturel ! +15 jetons."
            }

            playerTotal > 21 -> {
                delta = 0
                text = "Vous dépassez 21 : main perdue."
            }

            dealerNatural -> {
                delta = 0
                text = "Blackjack du croupier : main perdue."
            }

            dealerTotal > 21 -> {
                delta = BJO_BET * 2
                text = "Le croupier saute : +10 jetons."
            }

            playerTotal > dealerTotal -> {
                delta = BJO_BET * 2
                text = "Vous gagnez $playerTotal contre $dealerTotal."
            }

            playerTotal == dealerTotal -> {
                delta = BJO_BET
                text = "Égalité à $playerTotal : mise rendue."
            }

            else -> {
                delta = 0
                text = "Le croupier gagne $dealerTotal contre $playerTotal."
            }
        }

        chips += delta
        message = text
        phase = BJO_DONE

        when {
            chips >= 100 -> report(chips.toLong(), true)
            chips <= 0 -> report(0L, false)
            chips < BJO_BET -> report(chips.toLong(), false)
        }
    }

    fun deal() {
        if (phase == BJO_PLAYER || phase == BJO_DEALER) return
        if (chips < BJO_BET) {
            report(chips.toLong(), false)
            return
        }
        deck = bjoDeck()
        chips -= BJO_BET
        player = listOf(drawCard(), drawCard())
        dealer = listOf(drawCard(), drawCard())
        round++
        phase = BJO_PLAYER
        message = "À vous : tirez une carte ou restez."
        sound.play(Sound.TAP)
        haptics.tap()

        if (bjoValue(player) == 21) {
            phase = BJO_DONE
            settle()
        }
    }

    fun hit() {
        if (phase != BJO_PLAYER) return
        val card = drawCard()
        val next = player + card
        player = next
        sound.play(Sound.FLIP)
        haptics.tap()
        if (bjoValue(next) > 21) {
            message = "Vous dépassez 21…"
            phase = BJO_DONE
            settle()
        } else {
            message = "Vous avez ${bjoValue(next)}. Tirez encore ?"
        }
    }

    fun stand() {
        if (phase != BJO_PLAYER) return
        phase = BJO_DEALER
        message = "Le croupier révèle sa carte…"
        sound.play(Sound.TAP)
        haptics.tap()
    }

    // Le croupier joue automatiquement, avec une petite pause pour la lisibilité.
    LaunchedEffect(phase) {
        if (phase != BJO_DEALER) return@LaunchedEffect
        delay(430)
        while (bjoValue(dealer) < 17) {
            dealer = dealer + drawCard()
            sound.play(Sound.FLIP)
            delay(430)
        }
        settle()
    }

    val dealerTotal = when {
        dealer.isEmpty() -> "—"
        phase == BJO_PLAYER -> "${bjoValue(listOf(dealer.first()))} + ?"
        else -> "${bjoValue(dealer)}"
    }
    val playerTotal = if (player.isEmpty()) "—" else "${bjoValue(player)}"
    val playerTotalColor = when {
        player.isEmpty() -> Ink.Muted
        bjoValue(player) > 21 -> Ink.Coral
        bjoValue(player) == 21 -> Ink.Teal
        else -> Ink.Text
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Panel(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    AppText("JETONS", style = Typo.label, color = Ink.Muted)
                    AppText(
                        text = "$chips",
                        style = Typo.display,
                        color = if (chips >= 100) Ink.Teal else Ink.Text,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Pill(
                        text = "MISE $BJO_BET",
                        color = Ink.Mustard,
                        background = Ink.Mustard.copy(alpha = 0.14f),
                    )
                    Spacer(Modifier.height(4.dp))
                    AppText("Manche $round", style = Typo.small, color = Ink.Muted)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Panel(Modifier.fillMaxWidth().weight(1f), color = Ink.Card) {
            Column(
                Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                BjoHand(
                    label = "CROUPIER",
                    cards = dealer,
                    hideSecond = phase == BJO_PLAYER,
                    total = dealerTotal,
                    totalColor = if (phase == BJO_PLAYER) Ink.Muted else Ink.Text,
                )

                Panel(Modifier.fillMaxWidth(), color = Ink.BgDeep) {
                    Box(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppText(
                            text = message,
                            style = Typo.body,
                            color = Ink.Soft,
                            align = TextAlign.Center,
                            maxLines = 2,
                            softWrap = true,
                        )
                    }
                }

                BjoHand(
                    label = "VOUS",
                    cards = player,
                    hideSecond = false,
                    total = playerTotal,
                    totalColor = playerTotalColor,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        when (phase) {
            BJO_PLAYER -> {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PrimaryButton(
                        label = "TIRER",
                        onClick = { hit() },
                        modifier = Modifier.weight(1f),
                        color = Ink.Mustard,
                    )
                    SecondaryButton(
                        label = "RESTER",
                        onClick = { stand() },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            BJO_DEALER -> {
                PrimaryButton(
                    label = "LE CROUPIER JOUE…",
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    color = Ink.Muted,
                    enabled = false,
                )
            }

            else -> {
                PrimaryButton(
                    label = if (phase == BJO_READY) "DISTRIBUER" else "NOUVELLE DONNE",
                    onClick = { deal() },
                    modifier = Modifier.fillMaxWidth(),
                    color = Ink.Mustard,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        HintBar("Le croupier tire jusqu'à 17. Blackjack naturel payé 3:2, mise fixe de 10 jetons.")
    }
}

@Composable
private fun BjoHand(
    label: String,
    cards: List<BjoCard>,
    hideSecond: Boolean,
    total: String,
    totalColor: Color,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(label, style = Typo.label, color = Ink.Muted)
            AppText(total, style = Typo.heading, color = totalColor)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(100.dp)) {
            if (cards.isEmpty()) {
                AppText(
                    text = "—",
                    modifier = Modifier.align(Alignment.Center),
                    style = Typo.small,
                    color = Ink.Muted,
                )
            } else {
                val step = if (cards.size <= 1) {
                    72f
                } else {
                    ((380f - 66f) / (cards.size - 1)).coerceAtMost(72f)
                }
                val totalWidth = 66f + step * (cards.size - 1)
                val start = ((380f - totalWidth) / 2f).coerceAtLeast(0f)
                cards.forEachIndexed { index, card ->
                    BjoCardView(
                        card = card,
                        faceDown = hideSecond && index == 1,
                        modifier = Modifier
                            .offset(x = (start + step * index).dp)
                            .zIndex(index.toFloat()),
                    )
                }
            }
        }
    }
}

@Composable
private fun BjoCardView(card: BjoCard, faceDown: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .width(66.dp)
            .height(94.dp)
            .clip(shape)
            .background(if (faceDown) Ink.Blue else Ink.Card)
            .border(1.dp, if (faceDown) Ink.Blue else Ink.Line, shape),
    ) {
        if (faceDown) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(6.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.32f), RoundedCornerShape(5.dp)),
            ) {
                AppText(
                    text = "♠",
                    modifier = Modifier.align(Alignment.Center),
                    style = Typo.heading.copy(fontSize = 22.sp),
                    color = Color.White.copy(alpha = 0.45f),
                )
            }
        } else {
            val color = if (card.red) Ink.Coral else Ink.Text
            Column(Modifier.fillMaxSize().padding(horizontal = 5.dp, vertical = 4.dp)) {
                AppText(
                    text = card.label,
                    style = Typo.heading.copy(fontSize = 15.sp),
                    color = color,
                )
                AppText(
                    text = card.symbol,
                    style = Typo.small.copy(fontSize = 11.sp),
                    color = color,
                )
                Spacer(Modifier.weight(1f))
                AppText(
                    text = card.symbol,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    style = Typo.heading.copy(fontSize = 26.sp),
                    color = color,
                )
            }
        }
    }
}
