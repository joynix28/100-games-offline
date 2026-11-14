package com.botillon.games.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
import com.botillon.games.ui.Pill
import com.botillon.games.ui.PrimaryButton
import com.botillon.games.ui.SecondaryButton
import com.botillon.games.ui.Sound
import com.botillon.games.ui.Typo
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * 4 Couleurs : un UNO simplifié à deux joueurs.
 *
 * On pose une carte de la même couleur ou du même symbole que la défausse.
 * Le joker choisit la couleur active, le +2 fait piocher l'adversaire et
 * l'inversion (à deux) fait rejouer celui qui la pose. Le premier à vider
 * sa main gagne la manche.
 */
object UnoGame {
    val entry = GameEntry(
        id = "uno",
        title = "4 Couleurs",
        tagline = "Se débarrasser de ses cartes",
        family = Family.CARD,
        accent = Ink.Coral,
        variants = listOf("Rapide", "Classique", "Longue"),
    ) { variant, _, finish, _ ->
        Uno(variant, finish)
    }
}

private const val UNO_DRAW_TWO = 10
private const val UNO_REVERSE = 11
private const val UNO_JOKER = 12

private val unoColorNames = listOf("Rouge", "Bleu", "Vert", "Jaune")

/** Une carte : couleur 0 à 3, ou -1 pour le joker. */
private data class UnoCard(val color: Int, val value: Int)

private data class UnoDeal(
    val player: List<UnoCard>,
    val machine: List<UnoCard>,
    val deck: List<UnoCard>,
    val top: UnoCard,
)

private fun unoColorOf(index: Int): Color = when (index) {
    0 -> Ink.Coral
    1 -> Ink.Blue
    2 -> Ink.Green
    else -> Ink.Mustard
}

/** Le jaune réclame une encre sombre pour rester lisible. */
private fun unoInkOn(color: Int): Color = if (color == 3) Ink.Text else Color.White

/** Jeu complet : 0 à 9, deux +2 et deux inversions par couleur, quatre jokers. */
private fun unoDeck(): List<UnoCard> {
    val cards = mutableListOf<UnoCard>()
    for (color in 0..3) {
        for (value in 0..9) cards += UnoCard(color, value)
        repeat(2) {
            cards += UnoCard(color, UNO_DRAW_TWO)
            cards += UnoCard(color, UNO_REVERSE)
        }
    }
    repeat(4) { cards += UnoCard(-1, UNO_JOKER) }
    return cards
}

private fun unoRemoveOne(list: List<UnoCard>, card: UnoCard): List<UnoCard> {
    val index = list.indexOf(card)
    if (index < 0) return list
    return list.filterIndexed { i, _ -> i != index }
}

private fun unoPlayable(card: UnoCard, top: UnoCard?, activeColor: Int): Boolean {
    if (card.value == UNO_JOKER) return true
    if (top == null) return true
    return card.color == activeColor || card.value == top.value
}

private fun unoName(card: UnoCard): String = when (card.value) {
    UNO_JOKER -> "un joker"
    UNO_DRAW_TWO -> "un +2 ${unoColorNames[card.color].lowercase()}"
    UNO_REVERSE -> "une inversion ${unoColorNames[card.color].lowercase()}"
    else -> "un ${card.value} ${unoColorNames[card.color].lowercase()}"
}

/** Couleur la plus présente en main, pour le joker de l'ordinateur. */
private fun unoBestColor(hand: List<UnoCard>): Int {
    val counts = IntArray(4)
    hand.forEach { if (it.color in 0..3) counts[it.color]++ }
    var best = 0
    for (color in 1..3) if (counts[color] > counts[best]) best = color
    return best
}

@Composable
private fun Uno(variant: Int, finish: (Long, Boolean) -> Unit) {
    val accent = Ink.Coral
    val sound = LocalSound.current
    val haptics = LocalHaptics.current

    // La distribution est tirée une fois par manche.
    val deal = remember(variant) {
        val pile = unoDeck().shuffled(Random.Default)
        val starter = pile.drop(14).first { it.value < 10 }
        UnoDeal(
            player = pile.take(7),
            machine = pile.drop(7).take(7),
            deck = unoRemoveOne(pile.drop(14), starter),
            top = starter,
        )
    }

    var myHand by remember { mutableStateOf(deal.player) }
    var aiHand by remember { mutableStateOf(deal.machine) }
    var deck by remember { mutableStateOf(deal.deck) }
    var discard by remember { mutableStateOf(listOf(deal.top)) }
    var activeColor by remember { mutableIntStateOf(deal.top.color) }
    var turn by remember { mutableIntStateOf(0) }
    var hasDrawn by remember { mutableStateOf(false) }
    var pendingJoker by remember { mutableStateOf<UnoCard?>(null) }
    var notice by remember { mutableStateOf("À vous : même couleur ou même chiffre.") }
    var over by remember { mutableStateOf(false) }
    var reported by remember { mutableStateOf(false) }
    var aiToken by remember { mutableIntStateOf(0) }

    val topCard = discard.lastOrNull()

    /** Pioche une carte ; la défausse est recyclée quand la pioche se vide. */
    fun drawOne(): UnoCard? {
        if (deck.isEmpty()) {
            if (discard.size <= 1) return null
            val top = discard.last()
            deck = discard.dropLast(1).shuffled(Random.Default)
            discard = listOf(top)
        }
        val card = deck.firstOrNull() ?: return null
        deck = deck.drop(1)
        return card
    }

    fun forceDraw(targetPlayer: Boolean, count: Int) {
        repeat(count) {
            val card = drawOne() ?: return
            if (targetPlayer) myHand = myHand + card else aiHand = aiHand + card
        }
    }

    /** Fin de manche, appelée au plus une fois. */
    fun endGame(won: Boolean) {
        if (reported) return
        reported = true
        over = true
        pendingJoker = null
        if (won) {
            sound.play(Sound.WIN)
            haptics.success()
            notice = "Vous n'avez plus de cartes : gagné !"
        } else {
            sound.play(Sound.LOSE)
            haptics.error()
            notice = "L'IA n'a plus de cartes : perdu."
        }
        finish(if (won) 200L else 0L, won)
    }

    fun nextTurn(player: Int) {
        turn = player
        hasDrawn = false
        pendingJoker = null
        if (player == 1) aiToken++
    }

    fun playCard(card: UnoCard, chosenColor: Int, byPlayer: Boolean) {
        if (over) return
        if (byPlayer && !unoPlayable(card, discard.lastOrNull(), activeColor)) {
            notice = "Cette carte ne correspond ni à la couleur ni au chiffre."
            sound.play(Sound.UI)
            haptics.error()
            return
        }
        if (byPlayer) myHand = unoRemoveOne(myHand, card) else aiHand = unoRemoveOne(aiHand, card)
        discard = discard + card
        activeColor = if (card.value == UNO_JOKER) chosenColor else card.color
        pendingJoker = null
        hasDrawn = false
        sound.play(Sound.TAP)
        haptics.tap()

        val who = if (byPlayer) "Vous posez" else "L'IA pose"
        notice = "$who ${unoName(card)}."
        if (byPlayer && myHand.isEmpty()) {
            endGame(true)
            return
        }
        if (!byPlayer && aiHand.isEmpty()) {
            endGame(false)
            return
        }

        // Plus de cartes ? On vérifie d'abord la main, puis les effets.
        val again = card.value == UNO_DRAW_TWO || card.value == UNO_REVERSE
        if (card.value == UNO_DRAW_TWO) {
            forceDraw(!byPlayer, 2)
            notice = if (byPlayer) {
                "Vous posez un +2 : l'IA pioche 2 cartes et vous rejouez."
            } else {
                "L'IA pose un +2 : vous piochez 2 cartes et elle rejoue."
            }
        } else if (card.value == UNO_REVERSE) {
            notice = if (byPlayer) {
                "Inversion : à deux joueurs, vous rejouez."
            } else {
                "Inversion : à deux joueurs, l'IA rejoue."
            }
        }
        val next = if (again) (if (byPlayer) 0 else 1) else (if (byPlayer) 1 else 0)
        nextTurn(next)
    }

    fun playerDraw() {
        if (over || turn != 0 || hasDrawn || pendingJoker != null) return
        val card = drawOne()
        if (card == null) {
            hasDrawn = true
            notice = "La pioche est vide : passez votre tour."
            sound.play(Sound.UI)
            return
        }
        myHand = myHand + card
        hasDrawn = true
        sound.play(Sound.FLIP)
        haptics.tap()
        notice = if (unoPlayable(card, discard.lastOrNull(), activeColor)) {
            "Vous piochez ${unoName(card)}. Posez-la ou passez."
        } else {
            "Vous piochez ${unoName(card)}. Rien à poser, passez."
        }
    }

    fun playerPass() {
        if (over || turn != 0 || !hasDrawn) return
        notice = "Vous passez votre tour."
        sound.play(Sound.UI)
        nextTurn(1)
    }

    fun onCardTap(card: UnoCard) {
        if (over || turn != 0) return
        if (pendingJoker != null) {
            // Taper un joker alors qu'une couleur est demandée annule le choix.
            if (card.value == UNO_JOKER) {
                pendingJoker = null
                notice = "Joker gardé en main."
                return
            }
            pendingJoker = null
        }
        if (!unoPlayable(card, discard.lastOrNull(), activeColor)) {
            val label = unoName(card).replaceFirstChar { it.uppercase() }
            notice = "$label ne peut pas être posé."
            sound.play(Sound.UI)
            haptics.error()
            return
        }
        if (card.value == UNO_JOKER) {
            pendingJoker = card
            notice = "Joker : choisissez la couleur active."
            sound.play(Sound.TAP)
            haptics.tap()
            return
        }
        playCard(card, card.color, true)
    }

    // Tour de l'ordinateur : il réfléchit 600 ms, pioche au besoin, puis joue.
    LaunchedEffect(aiToken, over) {
        if (aiToken == 0 || over) return@LaunchedEffect
        delay(600)
        if (over || turn != 1) return@LaunchedEffect
        var pick = aiHand
            .filter { unoPlayable(it, discard.lastOrNull(), activeColor) }
            .minByOrNull { (if (it.value == UNO_JOKER) 100 else 0) + (if (it.color == activeColor) 0 else 10) }
        if (pick == null) {
            val drawn = drawOne()
            if (drawn != null) {
                aiHand = aiHand + drawn
                sound.play(Sound.FLIP)
                if (unoPlayable(drawn, discard.lastOrNull(), activeColor)) pick = drawn
            }
        }
        val card = pick
        if (card == null) {
            notice = "L'IA pioche et passe son tour."
            nextTurn(0)
            return@LaunchedEffect
        }
        val chosen = if (card.value == UNO_JOKER) unoBestColor(aiHand) else card.color
        playCard(card, chosen, false)
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pill("Vous ${myHand.size}", color = Ink.Teal, background = Ink.Teal.copy(alpha = 0.10f))
            Pill("IA ${aiHand.size}", color = Ink.Blue, background = Ink.Blue.copy(alpha = 0.10f))
            Pill("Pioche ${deck.size}", color = Ink.Soft, background = Ink.CardAlt)
            Spacer(Modifier.weight(1f))
            Pill(
                text = "Couleur ${unoColorNames[activeColor]}",
                color = Ink.Text,
                background = unoColorOf(activeColor).copy(alpha = 0.20f),
            )
        }

        Spacer(Modifier.height(6.dp))
        AppText(
            text = notice,
            style = Typo.small,
            color = if (over) Ink.Muted else Ink.Soft,
            align = TextAlign.Center,
            maxLines = 2,
            softWrap = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))

        Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AppText("IA", style = Typo.label, color = Ink.Muted)
                Spacer(Modifier.width(6.dp))
                aiHand.take(12).forEach {
                    UnoBack(16.dp, 24.dp)
                    Spacer(Modifier.width(3.dp))
                }
                if (aiHand.size > 12) {
                    AppText("+${aiHand.size - 12}", style = Typo.small, color = Ink.Muted)
                }
                Spacer(Modifier.weight(1f))
                Pill(
                    text = if (turn == 0) "À vous" else "À l'IA",
                    color = if (turn == 0) Ink.Teal else Ink.Blue,
                    background = if (turn == 0) Ink.Teal.copy(alpha = 0.10f) else Ink.Blue.copy(alpha = 0.10f),
                )
            }
            Spacer(Modifier.height(6.dp))
            Panel(Modifier.fillMaxWidth().weight(1f), color = Ink.CardAlt) {
                Column(
                    Modifier.fillMaxSize().padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    AppText("DÉFAUSSE", style = Typo.label, color = Ink.Muted)
                    Spacer(Modifier.height(8.dp))
                    topCard?.let { UnoFace(it, 88.dp, 122.dp, false, false, null) }
                    Spacer(Modifier.height(8.dp))
                    AppText("Couleur : ${unoColorNames[activeColor]}", style = Typo.small, color = Ink.Soft)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        UnoHand(
            hand = myHand,
            top = topCard,
            activeColor = activeColor,
            pendingJoker = pendingJoker,
            onCard = { onCardTap(it) },
        )
        Spacer(Modifier.height(8.dp))

        Box(Modifier.fillMaxWidth().height(54.dp), contentAlignment = Alignment.Center) {
            if (pendingJoker != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (color in 0..3) {
                        PrimaryButton(
                            label = unoColorNames[color],
                            onClick = { pendingJoker?.let { playCard(it, color, true) } },
                            modifier = Modifier.weight(1f),
                            color = unoColorOf(color),
                            textColor = unoInkOn(color),
                        )
                    }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PrimaryButton(
                        label = "PIOCHER",
                        onClick = { playerDraw() },
                        modifier = Modifier.weight(1f),
                        color = accent,
                        enabled = turn == 0 && !hasDrawn && !over,
                    )
                    SecondaryButton(
                        label = "PASSER",
                        onClick = { playerPass() },
                        modifier = Modifier
                            .weight(1f)
                            .alpha(if (turn == 0 && hasDrawn && !over) 1f else 0.45f),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        HintBar("Même couleur ou même chiffre. Joker pour changer de couleur, +2 et inversion pour rejouer.")
    }
}

/** Main du joueur : une à trois rangées selon le nombre de cartes. */
@Composable
private fun UnoHand(
    hand: List<UnoCard>,
    top: UnoCard?,
    activeColor: Int,
    pendingJoker: UnoCard?,
    onCard: (UnoCard) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().height(172.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (hand.isEmpty()) {
            AppText("Votre main est vide.", style = Typo.small, color = Ink.Muted)
        } else {
            val rows = when {
                hand.size <= 8 -> 1
                hand.size <= 16 -> 2
                else -> 3
            }
            val perRow = (hand.size + rows - 1) / rows
            val gap = 4.dp
            val slot = (maxWidth - gap * (perRow - 1)) / perRow
            val cardWidth = slot.coerceAtMost(56.dp)
            val available = maxHeight - gap * (rows - 1)
            val cardHeight = minOf(cardWidth * 1.42f, available / rows)
            val finalWidth = cardHeight / 1.42f

            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                hand.chunked(perRow).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        row.forEachIndexed { index, card ->
                            if (index > 0) Spacer(Modifier.width(gap))
                            UnoFace(
                                card = card,
                                width = finalWidth,
                                height = cardHeight,
                                playable = unoPlayable(card, top, activeColor),
                                selected = pendingJoker == card,
                                onClick = { onCard(card) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Dos de carte de l'ordinateur. */
@Composable
private fun UnoBack(width: Dp, height: Dp) {
    Box(
        Modifier
            .size(width, height)
            .clip(RoundedCornerShape(5.dp))
            .background(Ink.Blue)
            .border(1.dp, Ink.Card, RoundedCornerShape(5.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width * 0.55f, height * 0.55f)
                .clip(CircleShape)
                .background(Ink.Card.copy(alpha = 0.30f)),
        )
    }
}

@Composable
private fun UnoFace(
    card: UnoCard,
    width: Dp,
    height: Dp,
    playable: Boolean,
    selected: Boolean,
    onClick: (() -> Unit)?,
) {
    val background = if (card.color < 0) Ink.Text else unoColorOf(card.color)
    val foreground = if (card.color < 0) Color.White else unoInkOn(card.color)
    val shape = RoundedCornerShape(10.dp)
    val borderColor = when {
        selected -> Ink.Mustard
        playable -> Ink.Mustard.copy(alpha = 0.85f)
        else -> Ink.Line
    }
    Box(
        Modifier
            .size(width, height)
            .alpha(if (playable || onClick == null) 1f else 0.62f)
            .clip(shape)
            .background(background)
            .border(if (playable || selected) 2.dp else 1.dp, borderColor, shape)
            .then(onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier),
        contentAlignment = Alignment.Center,
    ) {
        when {
            card.value == UNO_JOKER -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AppText("JOKER", style = Typo.label.copy(fontSize = 8.sp), color = Color.White)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (color in 0..3) {
                        Box(
                            Modifier
                                .size(width * 0.13f)
                                .clip(CircleShape)
                                .background(unoColorOf(color)),
                        )
                    }
                }
            }

            card.value == UNO_DRAW_TWO -> AppText(
                text = "+2",
                style = Typo.heading.copy(fontSize = (height.value * 0.30f).sp),
                color = foreground,
            )

            card.value == UNO_REVERSE -> AppText(
                text = "↻",
                style = Typo.title.copy(fontSize = (height.value * 0.36f).sp),
                color = foreground,
            )

            else -> AppText(
                text = "${card.value}",
                style = Typo.title.copy(fontSize = (height.value * 0.34f).sp),
                color = foreground,
            )
        }
    }
}
